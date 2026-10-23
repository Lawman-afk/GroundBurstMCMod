package dev.groundzero.content.ordnance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.groundzero.content.effect.GasAgent;
import dev.groundzero.content.effect.GasGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * A front of toxin spreading out over the ground from where a toxin SCUD came down (DESIGN.md §19.5): once the blast has
 * dug its crater, a heavy cloud of toxin wells up out of it and a ring runs out over a second or two, leaving glowing
 * anthrax residue behind it (solid near the middle, spattered toward the edge, down into the crater), splashing green as
 * it goes and dosing whatever it washes over.
 */
public final class ToxinSpreads {
    /** How fast the front runs out, blocks a tick, and how long after the blast it sets off (the crater dug first). */
    public static final float SPEED = 0.6f;
    public static final int DELAY = 8;
    /** How far down from the burst it looks for the floor (the bottom of the crater). */
    private static final int FLOOR_REACH = 14;
    private static final DustParticleOptions SPLASH = new DustParticleOptions(0x6CF038, 1.8f);
    private static final DustParticleOptions MIST = new DustParticleOptions(0xA6F870, 1.2f);
    /** How thick its cloud is as it wells up (the grid's full strength is 0.3). */
    public static final float CLOUD_STRENGTH = 1.6f;

    private record Spread(Vec3 at, float radius, float cloud, long start, long seed) {}

    private static final Map<ResourceKey<Level>, List<Spread>> ACTIVE = new HashMap<>();

    private ToxinSpreads() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> ACTIVE.clear());
    }

    /** A front setting off from {@code at} (a moment from now), out to {@code radius}, under a cloud {@code cloud} blocks round. */
    public static void start(ServerLevel level, Vec3 at, float radius, float cloud) {
        ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>())
                .add(new Spread(at, radius, cloud, level.getGameTime() + DELAY, level.getRandom().nextLong()));
    }

    /** For GameTests: how many fronts are still spreading in a level. */
    public static int active(ServerLevel level) {
        List<Spread> list = ACTIVE.get(level.dimension());
        return list == null ? 0 : list.size();
    }

    private static void tick(ServerLevel level) {
        List<Spread> list = ACTIVE.get(level.dimension());
        if (list == null || list.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        list.removeIf(s -> now >= s.start() && step(level, s, now - s.start()));
    }

    /** One tick of a front {@code t} ticks after it set off; true once it has reached its full radius. */
    private static boolean step(ServerLevel level, Spread s, long t) {
        if (t == 0) {
            // The cloud wells up out of the crater, and a gout of toxin is thrown up out of it.
            GasGrid.of(level).fill(level, s.at().add(0, 1, 0), s.cloud(), GasAgent.SCUD_TOXIN, CLOUD_STRENGTH);
            level.sendParticles(SPLASH, s.at().x, s.at().y + 1, s.at().z, 90, 1.6, 1.2, 1.6, 0.35);
            level.sendParticles(MIST, s.at().x, s.at().y + 2, s.at().z, 60, 3, 1.5, 3, 0.05);
            level.playSound(null, s.at().x, s.at().y, s.at().z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.BLOCKS, 4f, 0.45f);
        }
        float inner = Math.min(s.radius(), SPEED * t), outer = Math.min(s.radius(), SPEED * (t + 1));
        RandomSource random = RandomSource.create(s.seed() + t);
        BlockState residue = GZOrdnance.ANTHRAX_RESIDUE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = Mth.ceil(outer);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d < inner - 0.01f || d >= outer) {
                    continue;
                }
                // Solid near the middle, spattered toward the edge (like a splash).
                float edge = d / s.radius();
                if (random.nextFloat() > 1.0f - 0.55f * edge * edge) {
                    continue;
                }
                if (floor(level, s.at(), dx, dz, pos)) {
                    BlockState here = level.getBlockState(pos);
                    if ((here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty() && !here.is(GZOrdnance.ANTHRAX_RESIDUE.get()))
                            && residue.canSurvive(level, pos)) {
                        level.setBlockAndUpdate(pos, residue);
                    }
                }
            }
        }
        // The front splashing up as it runs out: green spray round the ring, mist rolling over it.
        int points = Math.max(6, (int) (outer * 5));
        for (int i = 0; i < points; i++) {
            double a = (i + random.nextDouble()) / points * Math.PI * 2;
            double x = s.at().x + Math.cos(a) * outer, z = s.at().z + Math.sin(a) * outer;
            double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.2;
            level.sendParticles(SPLASH, x, y, z, 2, 0.25, 0.15, 0.25, 0.08);
            if (random.nextInt(3) == 0) {
                level.sendParticles(MIST, x, y + 0.4, z, 1, 0.4, 0.2, 0.4, 0.01);
            }
            if (random.nextInt(4) == 0) {
                level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 1, 0.2, 0.1, 0.2, 0.15);
            }
        }
        if (t % 6 == 0) {
            level.playSound(null, s.at().x, s.at().y, s.at().z, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.5f, 0.5f + 0.1f * random.nextFloat());
        }
        // Whatever the front washes over takes a heavy dose.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s.at(), s.at()).inflate(outer + 1, 4, outer + 1))) {
            double d = Math.hypot(e.getX() - s.at().x, e.getZ() - s.at().z);
            if (d >= inner - 1 && d < outer + 1) {
                Toxins.expose(level, e, 4f, 200, Toxins.TOXIN, Toxins.TOXIN_NAUSEA);
            }
        }
        return outer >= s.radius();
    }

    /** The open block on the floor at an offset from the burst (down into its crater), or false if there is none. */
    private static boolean floor(ServerLevel level, Vec3 at, int dx, int dz, BlockPos.MutableBlockPos pos) {
        pos.set(Mth.floor(at.x) + dx, Mth.floor(at.y) + 3, Mth.floor(at.z) + dz);
        // Up out of anything solid first (a slope), then down to the floor.
        for (int i = 0; i < 4 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
            pos.move(Direction.UP);
        }
        for (int i = 0; i < FLOOR_REACH; i++) {
            if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return true;
            }
            pos.move(Direction.DOWN);
        }
        return false;
    }
}
