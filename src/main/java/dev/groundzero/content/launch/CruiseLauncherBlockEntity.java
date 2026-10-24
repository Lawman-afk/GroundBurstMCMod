package dev.groundzero.content.launch;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.MissileItem;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.launch.LaunchSiloBlockEntity.Readiness;
import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.SideConfig;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Cruise Missile Launcher (Phase 9 slice 9c, DESIGN.md §17.11): two armored boxes side by side on a cradle hinged at
 * the back of a 2x6 deck, a cruise missile in each, after the Navy's Armored Box Launchers. A Launch Computer aims, arms
 * and fires it like any launcher (it is a {@link LaunchSite}): a 10 s countdown while its rams raise the boxes to 45
 * degrees, then each box's missile goes, 3 s apart, at the same target (each with its own guidance's scatter), out along
 * the launcher's front, bursting its box's frangible front cover as it lights; the boxes come down again after (with new
 * covers). It takes cruise missiles only,
 * and no other launcher fires them. An EMP, a redstone halt or a broken structure scrubs a launch: what has not gone
 * stays loaded.
 */
public class CruiseLauncherBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, LaunchSite {
    public static final int SLOT_A = 0, SLOT_B = 1;
    public static final int ENERGY_CAPACITY = 150_000, LAUNCH_COST = 60_000;
    /** From LAUNCH to the first missile (its boxes rise in the first 3 s), between the two, and after the last until they come down. */
    public static final int COUNTDOWN = 200, BETWEEN = 60, LINGER = 60;
    private static final int RECHECK_INTERVAL = 40;

    /**
     * The model's figures (tools/assets/cruise_launcher.py), pixels for a controller facing north: the boxes' hinge (up from
     * the deck's foot, back from the controller's cell's middle), each box's axis (right of that middle, and above the
     * hinge line square to the box), where a missile's tail sits in its box (along it from the hinge), and how far the
     * boxes rise (degrees).
     */
    public static final double HINGE_UP = 20, HINGE_BACK = 76, AXIS_OFF = 8, TAIL_ALONG = 3, BOX_LENGTH = 84, ELEVATION = 45;
    public static final double[] BOX_RIGHT = {0, 16};

    public enum Phase { IDLE, COUNTDOWN, FIRING, LOWERING }

    private static final String T_READY = GZLang.add(GroundZero.MODID + ".cruise.ready", "READY");
    private static final String T_ARMED = GZLang.add(GroundZero.MODID + ".cruise.armed", "ARMED");
    private static final String T_COUNTING = GZLang.add(GroundZero.MODID + ".cruise.counting", "LAUNCH IN %s");
    private static final String T_FIRING = GZLang.add(GroundZero.MODID + ".cruise.firing", "FIRING");
    private static final String T_LOWERING = GZLang.add(GroundZero.MODID + ".cruise.lowering", "Lowering");
    private static final String T_EMPTY = GZLang.add(GroundZero.MODID + ".cruise.empty", "Load cruise missiles");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".cruise.no_power", "No power: needs %sk FE");
    private static final String T_INCOMPLETE = GZLang.add(GroundZero.MODID + ".cruise.incomplete", "Structure incomplete");
    private static final String T_LOADED = GZLang.add(GroundZero.MODID + ".cruise.loaded", "Missiles: %s / 2");
    private static final String T_TARGET = GZLang.add(GroundZero.MODID + ".cruise.target", "Target: %s, %s");
    private static final String T_NO_TARGET = GZLang.add(GroundZero.MODID + ".cruise.no_target", "Aim it from a Launch Computer");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private @Nullable BlockPos target;
    private boolean armed;
    private @Nullable UUID launchedBy;
    /** The boxes up (synced: the model raises or lowers them); which have fired (their front covers open). */
    private boolean raised, firedA, firedB;
    /** The boxes still to fire in this launch, in order (their slots). */
    private int[] queue = new int[0];
    /** Client: whether it has seen its boxes up since it loaded (so it lowers them after, rather than snapping). */
    private boolean clientSawRaised;

    public CruiseLauncherBlockEntity(BlockPos pos, BlockState state) {
        super(GZLaunch.CRUISE_ENTITY.get(), pos, state, ENERGY_CAPACITY, 8_000, 0, 2, new int[] {SLOT_A, SLOT_B}, new int[0]);
    }

    // --- what it holds ------------------------------------------------------------------------------------------------------
    /** Whether a stack is a cruise missile (the only thing it takes). */
    public static boolean isCruiseMissile(ItemStack stack) {
        MissileSpec spec = MissileItem.spec(stack);
        return spec != null && spec.cruise();
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return (slot == SLOT_A || slot == SLOT_B) && isCruiseMissile(stack);
    }

    @Override
    protected int slotLimit(int slot) {
        return 1;
    }

    /** The missile in box {@code slot} (null: none). */
    public @Nullable MissileSpec missile(int slot) {
        return MissileItem.spec(this.inventory.getItem(slot));
    }

    /** Missiles loaded. */
    public int loaded() {
        return (missile(SLOT_A) != null ? 1 : 0) + (missile(SLOT_B) != null ? 1 : 0);
    }

    public Phase phase() {
        return this.phase;
    }

    /** Whether its boxes are up (client: as last synced). */
    public boolean raised() {
        return this.raised;
    }

    // --- a launch site ---------------------------------------------------------------------------------------------------
    @Override
    public Readiness readiness() {
        if (!isFormed()) {
            return Readiness.NOT_FORMED;
        }
        if (isEmpDisabled()) {
            return Readiness.EMP;
        }
        if (redstoneHalted()) {
            return Readiness.HALTED;
        }
        if (this.phase != Phase.IDLE) {
            return Readiness.COUNTING;
        }
        int n = loaded();
        if (n == 0) {
            return Readiness.NO_MISSILE;
        }
        return this.energy.stored() < (long) LAUNCH_COST * n ? Readiness.NO_POWER : Readiness.READY;
    }

    @Override
    public int countdown() {
        return this.phase == Phase.COUNTDOWN ? Math.max(0, COUNTDOWN - this.phaseTicks) : this.phase == Phase.IDLE ? -1 : 0;
    }

    @Override
    public @Nullable BlockPos target() {
        return this.target;
    }

    @Override
    public boolean armed() {
        return this.armed;
    }

    /** How far its loaded missiles fly (the shorter, with two), blocks; 0 with none. */
    @Override
    public int range() {
        int r = Integer.MAX_VALUE;
        for (int slot : new int[] {SLOT_A, SLOT_B}) {
            MissileSpec s = missile(slot);
            if (s != null) {
                r = Math.min(r, s.range());
            }
        }
        return r == Integer.MAX_VALUE ? 0 : r;
    }

    @Override
    public boolean setTarget(int x, int z) {
        if (this.phase != Phase.IDLE) {
            return false;
        }
        this.target = new BlockPos(x, this.worldPosition.getY(), z);
        if (this.armed && !inRange(this.target)) {
            this.armed = false;
        }
        setChanged();
        return true;
    }

    private boolean inRange(BlockPos at) {
        return Math.hypot(at.getX() - this.worldPosition.getX(), at.getZ() - this.worldPosition.getZ()) <= range();
    }

    @Override
    public boolean arm(boolean on) {
        if (on && (readiness() != Readiness.READY || this.target == null || !inRange(this.target))) {
            return false;
        }
        this.armed = on;
        setChanged();
        return true;
    }

    @Override
    public boolean launchArmed(ServerLevel level, @Nullable Entity by) {
        return this.armed && this.target != null && launch(level, this.target, by);
    }

    /** Starts the countdown to fire its missiles at {@code target}: its boxes rise. False if it is not ready. */
    public boolean launch(ServerLevel level, BlockPos target, @Nullable Entity by) {
        if (readiness() != Readiness.READY) {
            return false;
        }
        this.target = target.immutable();
        this.armed = false;
        this.launchedBy = by == null ? null : by.getUUID();
        this.queue = missile(SLOT_A) != null && missile(SLOT_B) != null ? new int[] {SLOT_A, SLOT_B}
                : new int[] {missile(SLOT_A) != null ? SLOT_A : SLOT_B};
        enter(Phase.COUNTDOWN);
        setRaised(true);
        level.playSound(null, this.worldPosition, GZLaunch.SIREN.get(), SoundSource.BLOCKS, 4f, 1.1f);
        level.playSound(null, this.worldPosition, dev.groundzero.registry.GZSounds.CRUISE_ELEVATE.get(), SoundSource.BLOCKS, 2f, 1f);
        return true;
    }

    private void enter(Phase next) {
        this.phase = next;
        this.phaseTicks = 0;
        setChanged();
    }

    private void setRaised(boolean up) {
        if (this.raised != up) {
            this.raised = up;
            setChanged();
            sync();
        }
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** For GameTests: jump to the end of the countdown. */
    public void skipCountdownForTest() {
        if (this.phase == Phase.COUNTDOWN) {
            this.phaseTicks = COUNTDOWN - 1;
        }
    }

    // --- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        Launches.track(level, this.worldPosition);
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            standDown();
            return false;
        }
        switch (this.phase) {
            case IDLE -> {
                boolean ready = readiness() == Readiness.READY;
                if (this.armed && !ready) {
                    this.armed = false;                          // power or missiles gone: safe again
                    setChanged();
                }
                this.status = ready ? MachineStatus.IDLE : MachineStatus.NO_RECIPE;
                return false;
            }
            case COUNTDOWN -> {
                this.status = MachineStatus.RUNNING;
                this.phaseTicks++;
                int left = COUNTDOWN - this.phaseTicks;
                if (left > 0 && left % 20 == 0) {
                    level.playSound(null, this.worldPosition, dev.groundzero.content.ordnance.GZOrdnance.BOMB_BEEP.get(), SoundSource.BLOCKS, 2f,
                            left <= 60 ? 1.4f : 1f);
                }
                if (left <= 0) {
                    enter(Phase.FIRING);
                }
            }
            case FIRING -> {
                this.status = MachineStatus.RUNNING;
                int shot = this.phaseTicks / BETWEEN;
                if (this.phaseTicks % BETWEEN == 0 && shot < this.queue.length) {
                    fire(level, this.queue[shot]);
                }
                this.phaseTicks++;
                if (this.phaseTicks >= (this.queue.length - 1) * BETWEEN + LINGER) {
                    enter(Phase.LOWERING);
                    setRaised(false);
                    level.playSound(null, this.worldPosition, dev.groundzero.registry.GZSounds.CRUISE_ELEVATE.get(), SoundSource.BLOCKS, 2f, 0.85f);
                }
            }
            case LOWERING -> {
                this.status = MachineStatus.RUNNING;
                this.phaseTicks++;
                if (this.phaseTicks >= 70) {
                    finish();
                }
            }
        }
        return true;
    }

    /** Box {@code slot}'s missile goes: it lights, bursting its box's cover, and leaves along the box. */
    private void fire(ServerLevel level, int slot) {
        MissileSpec spec = missile(slot);
        if (spec == null || this.target == null || isEmpDisabled() || this.energy.stored() < LAUNCH_COST) {
            return;
        }
        this.inventory.removeItem(slot, 1);
        this.energy.consume(LAUNCH_COST);
        if (slot == SLOT_A) {
            this.firedA = true;
        } else {
            this.firedB = true;
        }
        Entity by = this.launchedBy == null ? null : level.getEntity(this.launchedBy);
        Vec3 from = tailPoint(slot);
        Vec3 aim = spec.guidance().aim(from, Vec3.atBottomCenterOf(this.target), level.getRandom());
        Flights.launchCruise(level, from, heading(), aim, spec, by);
        // The cover bursting off the mouth; the booster's blast out of the back of the box, rolling over the deck behind it.
        Vec3 mouth = boxEnd(slot, true);
        level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 14, 0.3, 0.3, 0.3, 0.08);
        level.sendParticles(ParticleTypes.CRIT, mouth.x, mouth.y, mouth.z, 10, 0.2, 0.2, 0.2, 0.4);
        Vec3 back = boxEnd(slot, false);
        Direction b = controllerFacing().getOpposite();
        level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 24, 0.3, 0.2, 0.3, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, back.x + b.getStepX() * 0.8, back.y, back.z + b.getStepZ() * 0.8, 36, 0.6, 0.3, 0.6, 0.06);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, back.x + b.getStepX() * 1.5, back.y - 0.5, back.z + b.getStepZ() * 1.5, 10, 0.8, 0.2, 0.8, 0.02);
        setChanged();
        sync();
    }

    /** Back to idle: the boxes down, their covers shut for the next load. */
    private void finish() {
        enter(Phase.IDLE);
        setRaised(false);
        this.firedA = this.firedB = false;
        this.queue = new int[0];
        sync();
    }

    /** Knocked out, stopped or broken: a countdown is scrubbed, an armed one made safe; the boxes come down. */
    private void standDown() {
        if (this.armed) {
            this.armed = false;
            setChanged();
        }
        if (this.phase == Phase.COUNTDOWN || this.phase == Phase.FIRING) {
            enter(Phase.LOWERING);
            setRaised(false);
        }
        if (this.phase == Phase.LOWERING && !isFormed()) {
            finish();
        }
    }

    @Override
    protected void onEmp(ServerLevel level) {
        standDown();
    }

    @Override
    protected void onRedstoneHalt(ServerLevel level) {
        standDown();
    }

    // --- where things are -------------------------------------------------------------------------------------------------
    /** The way its boxes point over the ground (radians, as {@link Flights.Flight#heading}): the controller's front. */
    public double heading() {
        Direction f = controllerFacing();
        return Math.atan2(f.getStepX(), f.getStepZ());
    }

    /** Where box {@code slot}'s missile's tail is with the boxes up, in the world. */
    public Vec3 tailPoint(int slot) {
        double a = Math.toRadians(ELEVATION);
        double up = HINGE_UP + Math.sin(a) * TAIL_ALONG + Math.cos(a) * AXIS_OFF;
        double back = HINGE_BACK - Math.cos(a) * TAIL_ALONG + Math.sin(a) * AXIS_OFF;
        return modelPoint(BOX_RIGHT[slot], up, back);
    }

    /** One end of box {@code slot} with the boxes up: its mouth (front) or its back, on its axis. */
    public Vec3 boxEnd(int slot, boolean front) {
        double a = Math.toRadians(ELEVATION), along = front ? BOX_LENGTH : 0;
        return modelPoint(BOX_RIGHT[slot], HINGE_UP + Math.sin(a) * along + Math.cos(a) * AXIS_OFF,
                HINGE_BACK - Math.cos(a) * along + Math.sin(a) * AXIS_OFF);
    }

    /**
     * A point on the structure in the world: {@code right}, {@code up} and {@code back} pixels from the bottom middle of
     * the controller's cell, as the model has them (for a controller facing north), turned with the structure.
     */
    protected Vec3 modelPoint(double right, double up, double back) {
        Direction facing = controllerFacing();
        Direction r = facing.getClockWise(), b = facing.getOpposite();
        Vec3 base = Vec3.atBottomCenterOf(this.worldPosition);
        return base.add(r.getStepX() * right / 16 + b.getStepX() * back / 16, up / 16, r.getStepZ() * right / 16 + b.getStepZ() * back / 16);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZLaunch.CRUISE_LAUNCHER;
    }

    @Override
    public BlockPos controllerPos() {
        return this.worldPosition;
    }

    @Override
    public Direction controllerFacing() {
        return getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;
    }

    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    public boolean isFormed() {
        return getBlockState().hasProperty(MultiblockPartBlock.FORMED) && getBlockState().getValue(MultiblockPartBlock.FORMED);
    }

    @Override
    public void revalidate() {
        if (this.level == null || this.level.isClientSide() || isRemoved()) {
            return;
        }
        boolean complete = definition().check(this.level, this.worldPosition, controllerFacing()).isEmpty();
        if (complete != isFormed()) {
            MultiblockController.setPartsFormed(this.level, this, complete);
            this.level.setBlock(this.worldPosition, getBlockState().setValue(MultiblockPartBlock.FORMED, complete), Block.UPDATE_ALL);
            setChanged();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide() && state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
            MultiblockController.setPartsFormed(this.level, this, false);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel server) {
            Launches.track(server, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level instanceof ServerLevel server) {
            Launches.untrack(server, this.worldPosition);
        }
    }

    // --- screen -----------------------------------------------------------------------------------------------------------
    private static final int DATA_READINESS = 0, DATA_COUNTDOWN = 1, DATA_ARMED = 2, DATA_LOADED = 3, DATA_PHASE = 4;

    @Override
    protected int extraDataCount() {
        return 5;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_READINESS -> readiness().ordinal();
            case DATA_COUNTDOWN -> Math.max(0, countdown());
            case DATA_ARMED -> this.armed ? 1 : 0;
            case DATA_LOADED -> loaded();
            default -> this.phase.ordinal();
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        Readiness r = Readiness.values()[Mth.clamp(menu.extra(DATA_READINESS), 0, Readiness.values().length - 1)];
        Phase p = Phase.values()[Mth.clamp(menu.extra(DATA_PHASE), 0, Phase.values().length - 1)];
        Component state = switch (r) {
            case NOT_FORMED -> Component.translatable(T_INCOMPLETE);
            case NO_MISSILE -> Component.translatable(T_EMPTY);
            case NO_POWER -> Component.translatable(T_NO_POWER, LAUNCH_COST * Math.max(1, menu.extra(DATA_LOADED)) / 1000);
            case READY -> Component.translatable(menu.extra(DATA_ARMED) != 0 ? T_ARMED : T_READY);
            case COUNTING -> p == Phase.COUNTDOWN ? Component.translatable(T_COUNTING, (menu.extra(DATA_COUNTDOWN) + 19) / 20)
                    : Component.translatable(p == Phase.FIRING ? T_FIRING : T_LOWERING);
            default -> Component.translatable(r == Readiness.EMP ? "groundzero.silo.state.emp" : "groundzero.silo.state.halted");
        };
        Component aim = this.target == null ? Component.translatable(T_NO_TARGET) : Component.translatable(T_TARGET, this.target.getX(), this.target.getZ());
        return List.of(state, Component.translatable(T_LOADED, menu.extra(DATA_LOADED)), aim);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(dev.groundzero.registry.GZMenus.CRUISE_LAUNCHER.get(), containerId, inventory, this,
                dev.groundzero.content.machine.MachineLayouts.CRUISE_LAUNCHER, this.data);
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        sync();
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Unformed, nothing (the blocks show); formed, the beacon's lit lens only while it counts down and fires (ACTIVE). */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        BlockState state = getBlockState();
        return state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) ? Set.of() : Set.of("beacon");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    static final RawAnimation RAISE = RawAnimation.begin().thenPlayAndHold("raise");
    static final RawAnimation LOWER = RawAnimation.begin().thenPlayAndHold("lower");
    static final RawAnimation LOWERED = RawAnimation.begin().thenPlayAndHold("lowered");
    static final RawAnimation OPEN_A = RawAnimation.begin().thenPlayAndHold("open_a");
    static final RawAnimation SHUT_A = RawAnimation.begin().thenPlayAndHold("shut_a");
    static final RawAnimation OPEN_B = RawAnimation.begin().thenPlayAndHold("open_b");
    static final RawAnimation SHUT_B = RawAnimation.begin().thenPlayAndHold("shut_b");

    /**
     * The main controller (its beacon turning while it counts down and fires), the boxes' (raised while it launches;
     * lowered after, but only once this client has seen them up) and each box's front cover (burst off once its missile
     * has lit, until the boxes are down: then a new one is in place).
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimatedMachine.super.registerControllers(controllers);
        controllers.add(new AnimationController<CruiseLauncherBlockEntity>("boxes", 0, test -> {
            CruiseLauncherBlockEntity be = test.animatable();
            if (be.raised) {
                be.clientSawRaised = true;
                return test.setAndContinue(RAISE);
            }
            return test.setAndContinue(be.clientSawRaised ? LOWER : LOWERED);
        }));
        controllers.add(new AnimationController<CruiseLauncherBlockEntity>("cover_a", 0,
                test -> test.setAndContinue(test.animatable().firedA ? OPEN_A : SHUT_A)));
        controllers.add(new AnimationController<CruiseLauncherBlockEntity>("cover_b", 0,
                test -> test.setAndContinue(test.animatable().firedB ? OPEN_B : SHUT_B)));
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("phase", this.phase.name());
        output.putInt("phase_ticks", this.phaseTicks);
        output.putBoolean("armed", this.armed);
        if (this.target != null) {
            output.putLong("target", this.target.asLong());
        }
        if (this.launchedBy != null) {
            output.putString("launched_by", this.launchedBy.toString());
        }
        output.putBoolean("raised", this.raised);
        output.putBoolean("fired_a", this.firedA);
        output.putBoolean("fired_b", this.firedB);
        output.putIntArray("queue", this.queue);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            this.phase = Phase.valueOf(input.getStringOr("phase", Phase.IDLE.name()));
        } catch (IllegalArgumentException e) {
            this.phase = Phase.IDLE;
        }
        this.phaseTicks = input.getIntOr("phase_ticks", 0);
        this.armed = input.getBooleanOr("armed", false);
        this.target = input.getLong("target").map(BlockPos::of).orElse(null);
        this.launchedBy = input.getString("launched_by").map(UUID::fromString).orElse(null);
        this.raised = input.getBooleanOr("raised", false);
        this.firedA = input.getBooleanOr("fired_a", false);
        this.firedB = input.getBooleanOr("fired_b", false);
        this.queue = input.getIntArray("queue").orElse(new int[0]);
    }
}
