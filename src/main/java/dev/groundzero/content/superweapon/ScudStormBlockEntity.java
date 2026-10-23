package dev.groundzero.content.superweapon;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.launch.LaunchSite;
import dev.groundzero.content.launch.MissileCarrier;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.ordnance.GZOrdnance;
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
 * The GLA's SCUD Storm (DESIGN.md §19.5, C&C Generals): a square sand pad, nine Toxin SCUDs lying like the spokes of a
 * wheel on their erectors round a tank of toxin, behind a low adobe wall with two watchtowers. Charged (5:00) and fired,
 * the erectors raise the SCUDs upright and they go one after another (0.6 s apart), each coming down anywhere within 16
 * blocks of the target with a heavy blast, a toxin cloud and a spreading front of anthrax residue.
 *
 * <p>The figures here are the model's (tools/assets/scud_storm.py): the pad's middle, each erector's bearing and where a
 * raised SCUD's tail stands.
 */
public class ScudStormBlockEntity extends SuperweaponBlockEntity {
    public static final int CAPACITY = 9, CHARGE_TICKS = 6000, CHARGE_POWER = 400, FIRE_INTERVAL = 12;
    public static final float SPREAD = 16;
    /** The pad's middle: pixels back from the middle of the controller's cell (seven cells). */
    public static final float CENTER_BACK = 112;
    /** Erector k's bearing, degrees clockwise from the front: FIRST_BEARING + k * BEARING_STEP. */
    public static final float FIRST_BEARING = 20, BEARING_STEP = 40;
    /** A raised SCUD's tail: pixels out from the pad's middle (past the scrap round the tank) and up from the deck's bottom. */
    public static final float RAISED_TAIL_OUT = 35, RAISED_TAIL_UP = 28;
    /** How a SCUD lies on its erector: turned a quarter of a right angle about its axis (its fins clear of the boom). */
    public static final float TURN = 45;

    private static final String[] T_ANNOUNCE = {
            GZLang.add(GroundZero.MODID + ".superweapon.scud_storm.detected", "Warning: a SCUD Storm has been detected"),
            GZLang.add(GroundZero.MODID + ".superweapon.scud_storm.ready", "SCUD Storm ready"),
            GZLang.add(GroundZero.MODID + ".superweapon.scud_storm.launched", "Warning: SCUD Storm launched!")};

    public ScudStormBlockEntity(BlockPos pos, BlockState state) {
        super(GZSuperweapons.SCUD_STORM_ENTITY.get(), pos, state);
    }

    @Override
    public LaunchSite.Kind siteKind() {
        return LaunchSite.Kind.SCUD_STORM;
    }

    @Override
    public MultiblockDefinition definition() {
        return GZSuperweapons.SCUD_STORM;
    }

    @Override
    public Item ammo() {
        return GZSuperweapons.TOXIN_SCUD.get();
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
        return new ItemStack(GZOrdnance.SCUD_STORM_WARHEAD.item().get());
    }

    @Override
    protected SizeClass body() {
        return SizeClass.M;
    }

    @Override
    protected float spread() {
        return SPREAD;
    }

    @Override
    protected int fireInterval() {
        return FIRE_INTERVAL;
    }

    /** Erector {@code index}'s bearing, radians clockwise from the front. */
    public static double bearing(int index) {
        return Math.toRadians(FIRST_BEARING + index * BEARING_STEP);
    }

    @Override
    protected Vec3 launchPoint(int index) {
        double a = bearing(index);
        return modelPoint(RAISED_TAIL_OUT * Math.sin(a), RAISED_TAIL_UP, CENTER_BACK - RAISED_TAIL_OUT * Math.cos(a));
    }

    @Override
    protected String announcementKey(Announcement which) {
        return T_ANNOUNCE[which.ordinal()];
    }

    @Override
    protected ChatFormatting announcementColour() {
        return ChatFormatting.GREEN;
    }

    /** The SCUDs loaded, each on its erector (the first loaded on the first), lying until the erectors raise them. */
    @Override
    public List<MissileCarrier.Shown> shownMissiles() {
        if (!isFormed()) {
            return List.of();
        }
        String warhead = dev.groundzero.content.ordnance.charge.ChargeKind.SCUD_STORM_WARHEAD.id;
        List<String> looks = List.of(MissileParts.look(warhead));
        List<MissileCarrier.Shown> out = new ArrayList<>(CAPACITY);
        for (int k = 0; k < Math.min(CAPACITY, loaded()); k++) {
            out.add(new MissileCarrier.Shown(warhead, looks, "scud" + k, true, TURN));
        }
        return out;
    }

    // --- effects ----------------------------------------------------------------------------------------------------------
    /** The erectors' hydraulics as they raise the SCUDs; vapour boiling off them as they wait. */
    @Override
    protected void onCountdown(ServerLevel level, int ticks) {
        if (ticks == 1) {
            level.playSound(null, getBlockPos(), dev.groundzero.registry.GZSounds.SCUD_ERECT.get(), SoundSource.BLOCKS, 4f, 1f);
        }
        if (ticks % 4 == 0) {
            for (int k = 0; k < loaded(); k++) {
                Vec3 tail = launchPoint(k);
                level.sendParticles(ParticleTypes.CLOUD, tail.x, tail.y + 0.3, tail.z, 1, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    /** A SCUD lights: flame and smoke burst out over its erector's foot and roll across the pad. */
    @Override
    protected void onFire(ServerLevel level, int index, Vec3 at) {
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 30, 0.4, 0.2, 0.4, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 40, 0.8, 0.3, 0.8, 0.04);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.2, at.z, 12, 1.2, 0.2, 1.2, 0.02);
    }

    /** Charging: vapour off the vents in the pad, now and then. */
    @Override
    protected void onCharging(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        for (double[] v : VENTS) {
            if (level.getRandom().nextInt(3) == 0) {
                Vec3 p = modelPoint(v[0], 14.5, v[1]);
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.08, 0.02, 0.08, 0.015);
            }
        }
    }

    /** The steam vents in the pad (the model's grates): right, back in pixels. */
    public static final double[][] VENTS = {{-100, 28}, {100, 176}, {-92, 204}, {96, 40}};
}
