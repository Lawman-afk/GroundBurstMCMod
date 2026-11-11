package dev.groundburst.content.superweapon;

import java.util.List;

import software.bernie.geckolib.core.animation.RawAnimation;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.ParticleBeams;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.LaunchSite;
import dev.groundburst.content.launch.MissileCarrier;
import dev.groundburst.content.machine.MachineLayout;
import dev.groundburst.content.machine.MachineLayouts;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The USA's Particle Uplink Cannon (DESIGN.md §19.6, C&C Generals): a twelve-sided drum on a pad, eight particle emitters
 * through its wall round a hexagonal pit, beside a control tower and an operations building with two satellite dishes.
 * It takes no ammunition: it charges on power alone (4:00), and charged, its focusing column rises out of the pit, its
 * core aglow and arcs leaping to it from the emitters. Fired, a beam goes up out of the pit to its satellite and two
 * seconds later comes down from orbit on the target, burning for 48 seconds ({@link ParticleBeams}): a trench melted
 * into the ground, fires along it, everything in it vaporised. Whoever fired it steers it with a Laser Designator (or a
 * Launch Computer aims it anew); it follows at three blocks a second. The beam draws {@link #BEAM_POWER} FE a tick:
 * whatever can charge it keeps it burning, and what the cannon holds lasts twenty seconds of it, so cut its power and
 * the beam dies early.
 *
 * <p>The figures here are the model's (tools/assets/particle_cannon.py): where the pit's middle is and how high its
 * core stands once the column is up.
 */
public class ParticleCannonBlockEntity extends SuperweaponBlockEntity {
    public static final int CHARGE_TICKS = 4800, CHARGE_POWER = 1200, BEAM_POWER = 1000;
    /**
     * The pit's middle: pixels to the controller's right (negative: its left) and back from the middle of its cell (the
     * drum's middle, two cells to its left and six back); its core's height once the column is up.
     */
    public static final float PIT_RIGHT = -32, PIT_BACK = 96, CORE_UP = 56;
    /** The controller's block id: the launch network's name for it (a Laser Designator arms, fires and steers it). */
    public static final String KIND = GroundBurst.MODID + ":particle_uplink_cannon";
    /** Charged and ready: the column up, its core aglow, arcs leaping to it from the emitters (looping after it rises). */
    static final RawAnimation RAISE_AND_ARC = RawAnimation.begin().thenPlay("raise").thenLoop("charged");

    private static final String[] T_ANNOUNCE = {
            GBLang.add(GroundBurst.MODID + ".superweapon.particle_cannon.detected", "Warning: a Particle Uplink Cannon has been detected"),
            GBLang.add(GroundBurst.MODID + ".superweapon.particle_cannon.ready", "Particle Uplink Cannon ready"),
            GBLang.add(GroundBurst.MODID + ".superweapon.particle_cannon.launched", "Warning: Particle Uplink Cannon firing!")};
    private static final String T_BEAM = GBLang.add(GroundBurst.MODID + ".superweapon.particle_cannon.beam",
            "Beam: %s FE/t for %s s");
    private static final String T_BURNING = GBLang.add(GroundBurst.MODID + ".superweapon.particle_cannon.burning",
            "Beam burning: %s s left");

    /** The beam under way's id (0: none). */
    private int beam;

    public ParticleCannonBlockEntity(BlockPos pos, BlockState state) {
        super(GBSuperweapons.PARTICLE_CANNON_ENTITY.get(), pos, state);
    }

    @Override
    public LaunchSite.Kind siteKind() {
        return LaunchSite.Kind.PARTICLE_CANNON;
    }

    @Override
    public MultiblockDefinition definition() {
        return GBSuperweapons.PARTICLE_CANNON;
    }

    // --- no ammunition ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean needsAmmo() {
        return false;
    }

    /** It takes none (its slot holds nothing). */
    @Override
    public Item ammo() {
        return Items.AIR;
    }

    @Override
    public int capacity() {
        return 1;
    }

    @Override
    public int chargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public int chargePower() {
        return CHARGE_POWER;
    }

    /** It fires no missiles. */
    @Override
    protected ItemStack warhead() {
        return ItemStack.EMPTY;
    }

    @Override
    protected SizeClass body() {
        return SizeClass.M;
    }

    @Override
    protected float spread() {
        return 0;
    }

    @Override
    protected int fireInterval() {
        return 1;
    }

    /** Where its beam leaves: its core, over the pit. */
    @Override
    protected Vec3 launchPoint(int index) {
        return modelPoint(PIT_RIGHT, CORE_UP, PIT_BACK);
    }

    @Override
    public List<MissileCarrier.Shown> shownMissiles() {
        return List.of();
    }

    /** Its beam: its core where it comes down, its fires round it (the Launch Computer's map). */
    @Override
    public Strike strike(ServerLevel level) {
        return new Strike(ParticleBeams.CORE, ParticleBeams.FIRE, 0, 0);
    }

    /** Its countdown, then the beam's way up and back down from orbit, wherever it is aimed. */
    @Override
    public Timing timing() {
        return Timing.direct(COUNTDOWN + 1 + ParticleBeams.UPLINK);
    }

    @Override
    protected String announcementKey(Announcement which) {
        return T_ANNOUNCE[which.ordinal()];
    }

    @Override
    protected ChatFormatting announcementColour() {
        return ChatFormatting.BLUE;
    }

    // --- aiming and firing ------------------------------------------------------------------------------------------------------
    /** Aimed any time but while it stands down: during its countdown and while its beam burns, the beam turns toward the new aim. */
    @Override
    protected boolean retargetable() {
        return phase() != Phase.STANDING_DOWN;
    }

    /** Whether it is firing beam {@code id} now (the beam burns only while it is). */
    public boolean beamLive(int id) {
        return phase() == Phase.FIRING && this.beam == id && id != 0;
    }

    /** The beam under way's id (0: none; for GameTests). */
    public int beam() {
        return this.beam;
    }

    /**
     * Its strike: the beam goes up out of the pit; it draws {@link #BEAM_POWER} a tick while it burns (none left: the beam
     * dies) and goes out after its time.
     */
    @Override
    protected void fireTick(ServerLevel level, int ticks) {
        if (ticks == 0) {
            BlockPos aim = target();
            if (aim == null) {
                endFiring();
                return;
            }
            this.beam = ParticleBeams.start(level, getBlockPos(), launchPoint(0), aim, launchedBy());
            markFired();
        } else if (!ParticleBeams.running(level, this.beam)) {
            endFiring();                                          // its beam is gone (the world was reloaded under it)
            return;
        }
        if (this.energy.stored() < BEAM_POWER) {
            endFiring();                                          // out of power: the beam dies
            return;
        }
        this.energy.consume(BEAM_POWER);
        if (ticks >= ParticleBeams.UPLINK + ParticleBeams.BURN) {
            endFiring();
        }
    }

    /** Its column is up while it is charged and ready, counting down or firing; lowered as it stands down. */
    @Override
    protected void onTick(ServerLevel level) {
        Phase p = phase();
        setRaised(p == Phase.COUNTDOWN || p == Phase.FIRING || p == Phase.IDLE && charged());
        if (p == Phase.IDLE && charged() && level.getGameTime() % 50 == 0) {
            Vec3 core = launchPoint(0);
            playSound(level, core, GBSounds.PARTICLE_CHARGED.get(), SoundSource.BLOCKS, 1.4f,
                    0.9f + level.getRandom().nextFloat() * 0.2f);
        }
    }

    /** The countdown: its core brightening, sparks crackling off it, faster as the uplink nears. */
    @Override
    protected void onCountdown(ServerLevel level, int ticks) {
        int every = ticks > COUNTDOWN - 60 ? 2 : ticks > COUNTDOWN / 2 ? 4 : 8;
        if (ticks % every == 0) {
            Vec3 core = launchPoint(0);
            particles(level, ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 6, 0.6, 0.6, 0.6, 0.25);
        }
        if (ticks % 40 == 0) {
            Vec3 core = launchPoint(0);
            playSound(level, core, GBSounds.PARTICLE_CHARGED.get(), SoundSource.BLOCKS, 2f, 1.1f + ticks / (float) COUNTDOWN * 0.4f);
        }
    }

    /** Charging: now and then a spark off one of the emitters, and the operations building's vents breathing. */
    @Override
    protected void onCharging(ServerLevel level) {
        if (level.getGameTime() % 30 == 0) {
            var random = level.getRandom();
            double a = random.nextInt(8) * Math.PI / 4;
            Vec3 tip = modelPoint(PIT_RIGHT + Math.cos(a) * 50, 26, PIT_BACK + Math.sin(a) * 50);
            particles(level, ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 3, 0.1, 0.1, 0.1, 0.1);
        }
    }

    // --- its screen -------------------------------------------------------------------------------------------------------------
    @Override
    protected MenuType<MachineMenu> menuType() {
        return GBMenus.PARTICLE_CANNON.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayouts.PARTICLE_CANNON;
    }

    /** No ammunition: what its beam draws and for how long, or (burning) how long it has left. */
    @Override
    protected Component ammoLine(MachineMenu menu) {
        if (menu.extra(DATA_PHASE) == Phase.FIRING.ordinal()) {
            return Component.translatable(T_BURNING, (menu.extra(DATA_COUNTDOWN) + 19) / 20);
        }
        return Component.translatable(T_BEAM, BEAM_POWER, ParticleBeams.BURN / 20);
    }

    /** While its beam burns: ticks until it goes out; otherwise as any superweapon. */
    @Override
    public int countdown() {
        return phase() == Phase.FIRING ? Math.max(0, ParticleBeams.UPLINK + ParticleBeams.BURN - phaseTicks()) : super.countdown();
    }

    // --- saving -------------------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag_) { net.minecraft.core.HolderLookup.Provider registries_ = dev.groundburst.legacy.Regs.access(); ValueOutput output = new ValueOutput(tag_, registries_);
        super.saveAdditional(output.tag());
        output.putInt("beam", this.beam);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag_) { net.minecraft.core.HolderLookup.Provider registries_ = dev.groundburst.legacy.Regs.access(); ValueInput input = new ValueInput(tag_, registries_);
        super.load(input.tag());
        this.beam = input.getIntOr("beam", 0);
    }

    /** Its raised animation: the column rising, then the core's glow and arcs looping. */
    @Override
    protected RawAnimation raisedAnimation() {
        return RAISE_AND_ARC;
    }
}
