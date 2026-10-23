package dev.groundzero.content.superweapon;

import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.launch.LaunchSite;
import dev.groundzero.content.launch.MissileCarrier;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import dev.groundzero.registry.GZLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * China's Nuclear Missile (DESIGN.md §19.5, C&C Generals): not a true silo but an above-ground "coffin", the missile
 * lying in a long hall (fieldstone below, grey walls between green pilasters, a brown roof in four sections with red
 * ridges and gold finials, a red star on its gable) beside an octagonal control house, a green lattice tower at its
 * launch end. Charged (6:00) and fired, the roof folds open, the erector raises the silver missile upright beside the
 * tower, vapour boiling off it, and it climbs straight out on a column of fire: a nuclear blast and the game's
 * radiation field where it comes down.
 *
 * <p>The figures here are the model's (tools/assets/nuclear_silo.py): where the raised missile's tail stands.
 */
public class NuclearMissileBlockEntity extends SuperweaponBlockEntity {
    public static final int CAPACITY = 1, CHARGE_TICKS = 7200, CHARGE_POWER = 800;
    public static final float SPREAD = 3;
    /** The raised missile's tail: pixels back from the middle of the controller's cell, and up from the bottom of it. */
    public static final float RAISED_TAIL_BACK = 192, RAISED_TAIL_UP = 20;
    /** How the missile lies on its erector: turned so its red star faces out of the hall's side, its raceway down. */
    public static final float TURN = 90;

    private static final String[] T_ANNOUNCE = {
            GZLang.add(GroundZero.MODID + ".superweapon.nuclear_missile.detected", "Warning: a Nuclear Missile silo has been detected"),
            GZLang.add(GroundZero.MODID + ".superweapon.nuclear_missile.ready", "Nuclear Missile ready"),
            GZLang.add(GroundZero.MODID + ".superweapon.nuclear_missile.launched", "Warning: Nuclear Missile launch detected!")};

    public NuclearMissileBlockEntity(BlockPos pos, BlockState state) {
        super(GZSuperweapons.NUCLEAR_MISSILE_ENTITY.get(), pos, state);
    }

    @Override
    public LaunchSite.Kind siteKind() {
        return LaunchSite.Kind.NUCLEAR_MISSILE;
    }

    @Override
    public MultiblockDefinition definition() {
        return GZSuperweapons.NUCLEAR_MISSILE;
    }

    @Override
    public Item ammo() {
        return GZSuperweapons.NUCLEAR_MISSILE_ROUND.get();
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public int chargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public int chargePower() {
        return CHARGE_POWER;
    }

    @Override
    protected ItemStack warhead() {
        return new ItemStack(GZOrdnance.NUCLEAR_MISSILE_WARHEAD.item().get());
    }

    @Override
    protected SizeClass body() {
        return SizeClass.XL;
    }

    @Override
    protected float spread() {
        return SPREAD;
    }

    @Override
    protected int fireInterval() {
        return 1;
    }

    @Override
    protected Vec3 launchPoint(int index) {
        return modelPoint(0, RAISED_TAIL_UP, RAISED_TAIL_BACK);
    }

    @Override
    protected String announcementKey(Announcement which) {
        return T_ANNOUNCE[which.ordinal()];
    }

    @Override
    protected ChatFormatting announcementColour() {
        return ChatFormatting.RED;
    }

    /** The missile, lying in the hall on its erector until the erector raises it. */
    @Override
    public List<MissileCarrier.Shown> shownMissiles() {
        if (!isFormed() || loaded() == 0) {
            return List.of();
        }
        String warhead = ChargeKind.NUCLEAR_MISSILE_WARHEAD.id;
        return List.of(new MissileCarrier.Shown(warhead, List.of(MissileParts.look(warhead)), "missile", true, TURN));
    }

    // --- effects ----------------------------------------------------------------------------------------------------------
    /** The roof folding open and the erector raising the missile; then vapour boiling off its tanks as it waits. */
    @Override
    protected void onCountdown(ServerLevel level, int ticks) {
        if (ticks == 1) {
            level.playSound(null, getBlockPos(), dev.groundzero.registry.GZSounds.NUCLEAR_ERECT.get(), SoundSource.BLOCKS, 5f, 1f);
        }
        if (ticks > 80 && ticks % 3 == 0) {
            Vec3 tail = launchPoint(0);
            var random = level.getRandom();
            double y = tail.y + 2 + random.nextDouble() * 5;
            double a = random.nextDouble() * Math.PI * 2;
            level.sendParticles(ParticleTypes.CLOUD, tail.x + Math.cos(a) * 0.55, y, tail.z + Math.sin(a) * 0.55, 1,
                    Math.cos(a) * 0.1, -0.02, Math.sin(a) * 0.1, 0.03);
        }
    }

    /** The missile lights: fire and smoke pour out of the hall and roll out along the ground. */
    @Override
    protected void onFire(ServerLevel level, int index, Vec3 at) {
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 80, 0.6, 0.3, 0.6, 0.12);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 120, 1.5, 0.6, 1.5, 0.08);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 0.5, at.z, 30, 1.2, 0.3, 1.2, 0.02);
        for (double back : new double[] {40, 100, 160}) {
            Vec3 p = modelPoint(0, 26, back);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 30, 0.8, 0.4, 0.8, 0.12);
        }
    }

    /** Charging: a wisp from the control house's vent now and then. */
    @Override
    protected void onCharging(ServerLevel level) {
        if (level.getGameTime() % 40 == 0) {
            Vec3 p = modelPoint(-64, 50, 16);
            level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
    }
}
