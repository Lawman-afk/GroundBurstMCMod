package dev.groundzero.content.nuclear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.gear.GZArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * A neutron warhead's burst (DESIGN.md §15, Phase 8 slice 8b): a small blast, but a flood of neutrons deadly to every
 * living thing out to {@link #LETHAL_RADIUS}, weakened by what lies between: each block on the line from the burst to a
 * body takes its share (thick walls of water, lead, concrete or obsidian stop most of it, so a deep enough bunker
 * survives; a hazmat suit counts for only {@link #SUIT_SHARE} of its protection, as neutrons go through cloth and thin
 * lead). Plants out to {@link #WITHER_RADIUS} die as in a strong radiation field (slice 12b): leaves die on the branch,
 * crops wither, grass and flowers go, grass blocks die. Buildings are barely touched. The withering runs over a few ticks.
 */
public final class NeutronBurst {
    public static final float LETHAL_RADIUS = 180, WITHER_RADIUS = 120;
    /** A dose (0 to 1) at or over this kills; under it, down to {@link #SICK_DOSE}, makes radiation-sick. */
    public static final double LETHAL_DOSE = 0.3, SICK_DOSE = 0.03;
    /** Rad taken for a dose of 1 (so {@link #SICK_DOSE} is 120 rad, the start of Radiation Sickness I). */
    public static final double RAD_PER_DOSE = 4_000;
    /** How much one block on the line takes out (as e^-sum): ordinary solid, liquid, and dense shielding. */
    private static final double SOLID = 0.25, LIQUID = 0.4, DENSE = 1.0;
    /** How much of a suit's radiation protection counts against neutrons. */
    public static final double SUIT_SHARE = 0.5;
    /** Columns withered a tick. */
    private static final int COLUMNS_PER_TICK = 1500;
    private static final int WRITE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Withering(BlockPos centre, int radius, int[] next) {}

    private static final Map<ServerLevel, List<Withering>> WITHERING = new WeakHashMap<>();

    private NeutronBurst() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
    }

    /** The burst at {@code at}, its reach times {@code scale} (GameTests run it small). */
    public static void burst(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        float reach = Math.max(4, LETHAL_RADIUS * scale);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach))) {
            if (e.is(EntityTypeTags.UNDEAD) || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            Vec3 body = e.getBoundingBox().getCenter();
            double d = body.distanceTo(at);
            if (d > reach) {
                continue;
            }
            double dose = (1 - Math.pow(d / reach, 2)) * shielding(level, at, body) * (1 - SUIT_SHARE * GZArmor.radiationProtection(e));
            if (dose >= LETHAL_DOSE) {
                e.hurtServer(level, level.damageSources().source(GZEffects.RADIATION_DAMAGE), 1000);
            } else if (dose > 0.001) {
                // Taken at once, as rad (DESIGN.md §21.8): from SICK_DOSE (120 rad) it brings Radiation Sickness I and up.
                dev.groundzero.content.radiation.Radiation.addDose(e, (float) (dose * RAD_PER_DOSE));
            }
        }
        WITHERING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Withering(BlockPos.containing(at),
                (int) Math.max(3, WITHER_RADIUS * scale), new int[] {0}));
    }

    /** The share of the neutrons that gets through the blocks between {@code from} and {@code to} (1: nothing in the way). */
    public static double shielding(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        double length = step.length();
        int n = (int) Math.ceil(length * 2);
        double sum = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        for (int i = 1; i < n; i++) {
            Vec3 p = from.add(step.scale(i / (double) n));
            at.set(Math.floor(p.x), Math.floor(p.y), Math.floor(p.z));
            long key = at.asLong();
            if (key == last || !level.isLoaded(at)) {
                continue;
            }
            last = key;
            sum += attenuation(level.getBlockState(at), level, at);
        }
        return Math.exp(-sum);
    }

    private static double attenuation(BlockState s, ServerLevel level, BlockPos at) {
        if (s.isAir()) {
            return 0;
        }
        if (s.getBlock() instanceof LiquidBlock) {
            return LIQUID;
        }
        if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN) || s.is(Blocks.REINFORCED_DEEPSLATE) || s.is(Tags.BlockItems.CONCRETES.block())
                || s.is(dev.groundzero.content.resource.GZResources.LEAD_BLOCK.block().get())) {
            return DENSE;
        }
        return s.isCollisionShapeFullBlock(level, at) ? SOLID : 0.02;
    }

    private static void tick(ServerLevel level) {
        List<Withering> jobs = WITHERING.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        int budget = COLUMNS_PER_TICK;
        while (budget > 0 && !jobs.isEmpty()) {
            Withering w = jobs.getFirst();
            int side = 2 * w.radius() + 1, total = side * side;
            while (budget > 0 && w.next()[0] < total) {
                int i = w.next()[0]++;
                int dx = i % side - w.radius(), dz = i / side - w.radius();
                if (dx * dx + dz * dz > w.radius() * w.radius()) {
                    continue;
                }
                budget--;
                wither(level, w.centre().getX() + dx, w.centre().getZ() + dz);
            }
            if (w.next()[0] >= total) {
                jobs.removeFirst();
            }
        }
    }

    /** One column, from the top down, as a strong radiation field withers it (dead leaves, withered crops, dead grass). */
    private static void wither(ServerLevel level, int x, int z) {
        dev.groundzero.content.fallout.Withering.wither(level, x, z, WRITE);
    }

    /** For GameTests: whether withering is still going on in this level. */
    public static boolean witheringFor(ServerLevel level) {
        List<Withering> jobs = WITHERING.get(level);
        return jobs != null && !jobs.isEmpty();
    }
}
