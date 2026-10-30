package dev.groundburst.content.exotic;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.Annihilations;
import dev.groundburst.content.gear.GBArmor;
import dev.groundburst.registry.GBLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * The antimatter warhead (DESIGN.md §16.3, Phase 11): antimatter held in a Penning trap. Delivered by a missile, the
 * trap is opened as it arrives: a white sphere grows to {@link #RADIUS} blocks in a second and a half and erases
 * everything in it ({@link Annihilations}), with a gamma flash from the first instant that kills anything in the open
 * out to {@link #GAMMA_LETHAL} and hurts it out to {@link #GAMMA_REACH}; nothing burns, nothing is left lying, and no
 * fallout lingers. Set off any other way (placed, dropped, shot, caught in a blast) the trap's field fails unevenly and
 * only a little of the antimatter meets the casing: a sphere of {@link #BREACH_RADIUS}.
 */
public final class Antimatter {
    /** The sphere it erases; its gamma flash's lethal and farthest reach (blocks, before the bomb scale). */
    public static final float RADIUS = 67, GAMMA_LETHAL = 90, GAMMA_REACH = 134;
    /** A breached trap's sphere. */
    public static final float BREACH_RADIUS = 5.5f;
    /** {@code groundburst:gamma_flash}: the flash's damage (it goes through armour, not through walls). */
    public static final ResourceKey<DamageType> GAMMA_FLASH = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("gamma_flash"));
    /** A dose (0 to 1, see {@link #gamma}) at or over this kills. */
    static final double LETHAL_DOSE = 0.5;
    /** What a block on the line takes out of the flash (as e^-sum): an ordinary solid, a liquid, dense shielding. */
    private static final double SOLID = 0.8, LIQUID = 0.35, DENSE = 1.6;
    /** How much of a suit's radiation protection counts against the flash (lead-lined suits stop a good share). */
    private static final double SUIT_SHARE = 0.6;
    /** Health a dose of 1 would take off, and rad it would give, under the lethal dose. */
    private static final float HURT_PER_DOSE = 40, RAD_PER_DOSE = 800;

    private Antimatter() {}

    public static void register() {
        GBLang.add("death.attack." + GroundBurst.MODID + ".gamma_flash", "%1$s was caught in a gamma flash");
        GBLang.add("death.attack." + GroundBurst.MODID + ".gamma_flash.player", "%1$s was caught in %2$s's gamma flash");
    }

    /** A missile's antimatter warhead arriving at {@code at}: the flash, then the sphere (its reach times {@code scale}). */
    public static void burst(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        float radius = Math.max(2, RADIUS * scale);
        gamma(level, Vec3.atCenterOf(BlockPos.containing(at)), radius, Math.max(radius + 1, GAMMA_LETHAL * scale),
                Math.max(radius + 2, GAMMA_REACH * scale), by);
        Annihilations.start(level, at, radius, by);
    }

    /** An antimatter warhead set off any other way: its trap breached, a small sphere and a weak flash. */
    public static void breach(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        float radius = Math.max(1.5f, BREACH_RADIUS * scale);
        gamma(level, Vec3.atCenterOf(BlockPos.containing(at)), radius, radius * 1.4f, radius * 2.4f, by);
        Annihilations.start(level, at, radius, by);
    }

    /**
     * The gamma flash from {@code at}: each creature between the sphere and {@code reach} takes a dose of its distance's
     * share (all of it inside {@code lethal}, falling to none at {@code reach}) times what gets through the blocks
     * between (the flash comes before the sphere has erased any) and its suit. A lethal dose kills; less hurts and makes
     * radiation-sick. Inside the sphere there is nothing left to dose.
     */
    static void gamma(ServerLevel level, Vec3 at, float sphere, float lethal, float reach, @Nullable Entity by) {
        DamageSource source = level.damageSources().source(GAMMA_FLASH, null, by);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            Vec3 body = e.getBoundingBox().getCenter();
            double d = body.distanceTo(at);
            if (d <= sphere || d > reach) {
                continue;
            }
            double share = d <= lethal ? 1 : 1 - (d - lethal) / (reach - lethal);
            double dose = share * shielding(level, at, body) * (1 - SUIT_SHARE * GBArmor.radiationProtection(e));
            if (dose >= LETHAL_DOSE) {
                e.damageCooldownTime = 0;
                e.hurtServer(level, source, 1000);
            } else if (dose > 0.03) {
                e.damageCooldownTime = 0;
                e.hurtServer(level, source, (float) dose * HURT_PER_DOSE);
                dev.groundburst.content.radiation.Radiation.addDose(e, (float) dose * RAD_PER_DOSE);
            }
        }
    }

    /** The share of the flash that gets through the blocks between {@code from} and {@code to} (1: nothing in the way). */
    static double shielding(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int n = (int) Math.ceil(step.length() * 2);
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
                || s.is(dev.groundburst.content.resource.GBResources.LEAD_BLOCK.block().get())) {
            return DENSE;
        }
        return s.isCollisionShapeFullBlock(level, at) ? SOLID : 0.05;
    }
}
