package dev.groundburst.content.superweapon;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchSiloBlockEntity.Readiness;
import dev.groundburst.content.launch.LaunchSite;
import dev.groundburst.content.launch.Launches;
import dev.groundburst.content.launch.MissileCarrier;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A C&C Generals superweapon (DESIGN.md §19.5): a multiblock that takes only its own ammunition, charges while it is
 * powered (the game's countdown timer), and is aimed, armed and fired from a Launch Computer like any launcher (it is a
 * {@link LaunchSite}). Fired, it warns everyone in the dimension, makes its launchers ready during a countdown (the SCUD
 * Storm raises its nine missiles; the Nuclear Missile opens its roof and raises its missile), fires, stands down and
 * starts charging again. An EMP, a redstone halt or a broken structure scrubs a strike: what was not fired stays loaded.
 */
public abstract class SuperweaponBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, MissileCarrier, LaunchSite {
    public static final int SLOT_AMMO = 0;
    /** How far a superweapon reaches, blocks. */
    public static final int RANGE = 4096;
    /** From LAUNCH to the first missile lighting, ticks (its launchers are made ready within it). */
    public static final int COUNTDOWN = 200;
    /** How long its launchers stay up after the last missile has gone, before they are lowered. */
    public static final int LINGER = 40;
    private static final int ENERGY_CAPACITY = 400_000, MAX_INSERT = 8_000, RECHECK_INTERVAL = 40;

    /** Where it is in a strike. */
    public enum Phase { IDLE, COUNTDOWN, FIRING, STANDING_DOWN }

    /** What the dimension hears about a superweapon. */
    public enum Announcement { DETECTED, READY, LAUNCHED }

    private static final String T_CHARGING = GBLang.add(GroundBurst.MODID + ".superweapon.charging", "Charging: %s");
    private static final String T_NO_POWER = GBLang.add(GroundBurst.MODID + ".superweapon.no_power", "Charging paused: needs %s FE/t");
    private static final String T_READY = GBLang.add(GroundBurst.MODID + ".superweapon.ready", "READY");
    private static final String T_ARMED = GBLang.add(GroundBurst.MODID + ".superweapon.armed", "ARMED");
    private static final String T_EMPTY = GBLang.add(GroundBurst.MODID + ".superweapon.empty", "Charged: load its ammunition");
    private static final String T_COUNTING = GBLang.add(GroundBurst.MODID + ".superweapon.counting", "LAUNCH IN %s");
    private static final String T_FIRING = GBLang.add(GroundBurst.MODID + ".superweapon.firing", "FIRING");
    private static final String T_STANDING_DOWN = GBLang.add(GroundBurst.MODID + ".superweapon.standing_down", "Standing down");
    private static final String T_INCOMPLETE = GBLang.add(GroundBurst.MODID + ".superweapon.incomplete", "Structure incomplete");
    private static final String T_AMMO = GBLang.add(GroundBurst.MODID + ".superweapon.ammo", "%s: %s / %s");
    private static final String T_TARGET = GBLang.add(GroundBurst.MODID + ".superweapon.target", "Target: %s, %s");
    private static final String T_NO_TARGET = GBLang.add(GroundBurst.MODID + ".superweapon.no_target", "Aim it from a Launch Computer");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int charge;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private @Nullable BlockPos target;
    private boolean armed;
    private @Nullable UUID launchedBy;
    private boolean announcedFormed, announcedReady;
    /** Whether its launchers are up (synced: the model raises or lowers them). */
    private boolean raised;
    /** Client: whether it has seen its launchers up since it loaded (so it lowers them after, rather than snapping). */
    private boolean clientSawRaised;

    protected SuperweaponBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, ENERGY_CAPACITY, MAX_INSERT, 0, 1, new int[] {SLOT_AMMO}, new int[0]);
    }

    // --- what each superweapon is ------------------------------------------------------------------------------------------
    /** The only ammunition it takes. */
    public abstract Item ammo();

    /** How many rounds of it it holds (the SCUD Storm's nine). */
    public abstract int capacity();

    /** How long it takes to charge from empty, ticks (the game's timer). */
    public abstract int chargeTicks();

    /** The energy charging takes, FE a tick. */
    public abstract int chargePower();

    /** The warhead each of its missiles carries. */
    protected abstract ItemStack warhead();

    /** Its missiles' airframe class (their stages). */
    protected abstract SizeClass body();

    /** How far from the target each missile may come down, blocks. */
    protected abstract float spread();

    /** Ticks between one missile lighting and the next. */
    protected abstract int fireInterval();

    /** Where missile {@code index}'s tail is when it is ready to fire (its launcher up), in the world. */
    protected abstract Vec3 launchPoint(int index);

    /** Its name in the dimension's announcements, and their colour. */
    protected abstract String announcementKey(Announcement which);

    protected abstract ChatFormatting announcementColour();

    /** Each tick of the countdown, {@code ticks} into it (smoke, steam, the roof opening). */
    protected void onCountdown(ServerLevel level, int ticks) {}

    /** Just after missile {@code index} is lit (its exhaust over the launcher). */
    protected void onFire(ServerLevel level, int index, Vec3 at) {}

    /** Each tick it charges (vents steaming, the tank bubbling). */
    protected void onCharging(ServerLevel level) {}

    // --- state ----------------------------------------------------------------------------------------------------------
    public int charge() {
        return this.charge;
    }

    public boolean charged() {
        return this.charge >= chargeTicks();
    }

    public Phase phase() {
        return this.phase;
    }

    /** Rounds loaded. */
    public int loaded() {
        return this.inventory.getItem(SLOT_AMMO).getCount();
    }

    /** Whether its launchers are up (client: as last synced). */
    public boolean raised() {
        return this.raised;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == SLOT_AMMO && stack.is(ammo());
    }

    @Override
    protected int slotLimit(int slot) {
        return capacity();
    }

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
        if (!charged()) {
            return this.energy.stored() < chargePower() ? Readiness.NO_POWER : Readiness.CHARGING;
        }
        return loaded() == 0 ? Readiness.NO_MISSILE : Readiness.READY;
    }

    /** Each of its missiles' warhead, coming down anywhere within its spread of the target. */
    @Override
    public Strike strike(ServerLevel level) {
        if (loaded() == 0) {
            return Strike.NONE;
        }
        var area = dev.groundburst.content.assembly.AssemblyStats.area(warhead(), level.registryAccess());
        return new Strike(area.lethal(), area.reach(), spread(), spread());
    }

    /** Counting down: ticks until its first missile lights; charging: until it is charged; otherwise -1. */
    @Override
    public int countdown() {
        if (this.phase == Phase.COUNTDOWN) {
            return Math.max(0, COUNTDOWN - this.phaseTicks);
        }
        if (this.phase == Phase.IDLE && !charged()) {
            return chargeTicks() - this.charge;
        }
        return this.phase == Phase.IDLE ? -1 : 0;
    }

    @Override
    public @Nullable BlockPos target() {
        return this.target;
    }

    @Override
    public boolean armed() {
        return this.armed;
    }

    @Override
    public int range() {
        return RANGE;
    }

    /** Its ammunition (as many rounds as it holds). */
    @Override
    public ItemStack shownStack() {
        return this.inventory.getItem(SLOT_AMMO).copy();
    }

    /** Its countdown, its first missile's ignition, then that missile's flight. */
    @Override
    public Timing timing() {
        return new Timing(COUNTDOWN + 1 + dev.groundburst.content.launch.MissileEntity.IGNITION, false, this.worldPosition.getX() + 0.5,
                this.worldPosition.getZ() + 0.5, 0);
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

    /** Starts a strike on {@code target}: the warning, the countdown, its launchers made ready. False if it is not ready. */
    public boolean launch(ServerLevel level, BlockPos target, @Nullable Entity by) {
        if (readiness() != Readiness.READY) {
            return false;
        }
        this.target = target.immutable();
        this.armed = false;
        this.launchedBy = by == null ? null : by.getUUID();
        enter(Phase.COUNTDOWN);
        setRaised(true);
        announce(level, Announcement.LAUNCHED);
        level.playSound(null, this.worldPosition, GBLaunch.SIREN.get(), SoundSource.BLOCKS, 6f, 1f);
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

    /** For GameTests: charge it fully at once. */
    public void chargeForTest() {
        this.charge = chargeTicks();
        setChanged();
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
        Launches.track(level, this);
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            standDown();
            return false;
        }
        if (!this.announcedFormed) {
            this.announcedFormed = true;
            announce(level, Announcement.DETECTED);
            setChanged();
        }
        return switch (this.phase) {
            case IDLE -> idle(level);
            case COUNTDOWN -> countingDown(level);
            case FIRING -> firing(level);
            case STANDING_DOWN -> standingDown();
        };
    }

    private boolean idle(ServerLevel level) {
        if (!charged()) {
            if (this.energy.stored() < chargePower()) {
                this.status = MachineStatus.NO_POWER;
                return false;
            }
            this.energy.consume(chargePower());
            this.charge++;
            this.status = MachineStatus.CHARGING;
            onCharging(level);
            if (this.charge % 20 == 0) {
                setChanged();
            }
            return true;
        }
        if (!this.announcedReady) {
            this.announcedReady = true;
            announce(level, Announcement.READY);
            setChanged();
        }
        boolean ready = readiness() == Readiness.READY;
        if (this.armed && !ready) {
            this.armed = false;                                 // unloaded: safe again
            setChanged();
        }
        this.status = ready ? MachineStatus.IDLE : MachineStatus.NO_RECIPE;
        return false;
    }

    private boolean countingDown(ServerLevel level) {
        this.status = MachineStatus.RUNNING;
        this.phaseTicks++;
        int left = COUNTDOWN - this.phaseTicks;
        if (left > 0 && left % 20 == 0) {
            level.playSound(null, this.worldPosition, dev.groundburst.content.ordnance.GBOrdnance.BOMB_BEEP.get(), SoundSource.BLOCKS, 3f,
                    left <= 60 ? 1.4f : 1f);
        }
        onCountdown(level, this.phaseTicks);
        if (left <= 0) {
            enter(Phase.FIRING);
        }
        return true;
    }

    private boolean firing(ServerLevel level) {
        this.status = MachineStatus.RUNNING;
        if (this.phaseTicks % fireInterval() == 0) {
            int index = loaded() - 1;
            if (index < 0 || this.target == null) {
                enter(Phase.STANDING_DOWN);
                return true;
            }
            fire(level, index);
        }
        this.phaseTicks++;
        return true;
    }

    /** Missile {@code index} (the last loaded) lights on its launcher and sets off for somewhere near the target. */
    private void fire(ServerLevel level, int index) {
        this.inventory.removeItem(SLOT_AMMO, 1);
        Entity by = this.launchedBy == null ? null : level.getEntity(this.launchedBy);
        Vec3 from = launchPoint(index);
        RandomSource random = level.getRandom();
        double r = spread() * Math.sqrt(random.nextDouble()), a = random.nextDouble() * Math.PI * 2;
        Vec3 aim = Vec3.atBottomCenterOf(this.target).add(Math.cos(a) * r, 0, Math.sin(a) * r);
        dev.groundburst.content.launch.LaunchNetwork.launched(level, this.worldPosition,
                Flights.launch(level, from, aim, warhead(), body(), true, by, 0));
        this.firedThisStrike = true;
        onFire(level, index, from);
        setChanged();
        sync();
    }

    private boolean standingDown() {
        this.status = MachineStatus.RUNNING;
        this.phaseTicks++;
        if (this.phaseTicks == LINGER) {
            setRaised(false);
        }
        if (this.phaseTicks >= LINGER + 60) {
            finishStrike();
        }
        return true;
    }

    /** Back to idle: a strike that fired anything charges again from empty; one scrubbed before it fired keeps its charge. */
    private void finishStrike() {
        enter(Phase.IDLE);
        setRaised(false);
        if (this.firedThisStrike) {
            this.charge = 0;
            this.announcedReady = false;
        }
        this.firedThisStrike = false;
    }

    /** Whether the strike under way has fired anything (a scrubbed one keeps its charge). */
    private boolean firedThisStrike;

    /** Knocked out, stopped or broken: a countdown is scrubbed, an armed one made safe; it lowers its launchers. */
    private void standDown() {
        if (this.armed) {
            this.armed = false;
            setChanged();
        }
        if (this.phase == Phase.COUNTDOWN || this.phase == Phase.FIRING) {
            enter(Phase.STANDING_DOWN);
            this.phaseTicks = LINGER - 1;
        }
        if (this.phase == Phase.STANDING_DOWN && !isFormed()) {
            finishStrike();
        }
    }

    @Override
    protected void onEmp(ServerLevel level) {
        standDown();
        if (this.phase == Phase.STANDING_DOWN) {
            standingDown();
        }
    }

    @Override
    protected void onRedstoneHalt(ServerLevel level) {
        onEmp(level);
    }

    // --- announcements ----------------------------------------------------------------------------------------------------
    /** Everyone in the dimension hears it, as the game's announcer has it: a line in chat and a warning tone. */
    protected void announce(ServerLevel level, Announcement which) {
        Component line = Component.translatable(announcementKey(which)).withStyle(announcementColour(), ChatFormatting.BOLD);
        Holder<SoundEvent> sound = which == Announcement.READY ? dev.groundburst.registry.GBSounds.SUPERWEAPON_READY
                : dev.groundburst.registry.GBSounds.SUPERWEAPON_WARNING;
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(line);
            player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, player.getX(), player.getEyeY(), player.getZ(),
                    1f, 1f, level.getRandom().nextLong()));
        }
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
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

    /**
     * A point on the structure in the world: {@code right}, {@code up} and {@code back} pixels from the bottom middle of
     * the controller's cell, as the model has them (x right, y up, z back from the controller, for a controller facing
     * north), turned with the structure.
     */
    protected Vec3 modelPoint(double right, double up, double back) {
        Direction facing = controllerFacing();
        Direction r = facing.getClockWise(), b = facing.getOpposite();
        Vec3 base = Vec3.atBottomCenterOf(this.worldPosition);
        return base.add(r.getStepX() * right / 16 + b.getStepX() * back / 16, up / 16, r.getStepZ() * right / 16 + b.getStepZ() * back / 16);
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

    /** Its missiles cannot be taken out (or more put in) from the strike's countdown until the last has flown. */
    @Override
    public boolean slotLocked(int slot, MachineMenu menu) {
        return menu.extra(DATA_READINESS) == Readiness.COUNTING.ordinal();
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

    /** Ticks as a clock, M:SS. */
    public static String clock(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return s / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        Readiness r = Readiness.values()[Mth.clamp(menu.extra(DATA_READINESS), 0, Readiness.values().length - 1)];
        Phase p = Phase.values()[Mth.clamp(menu.extra(DATA_PHASE), 0, Phase.values().length - 1)];
        int ticks = menu.extra(DATA_COUNTDOWN);
        Component state = switch (r) {
            case NOT_FORMED -> Component.translatable(T_INCOMPLETE);
            case CHARGING -> Component.translatable(T_CHARGING, clock(ticks));
            case NO_POWER -> Component.translatable(T_NO_POWER, chargePower());
            case NO_MISSILE -> Component.translatable(T_EMPTY);
            case READY -> Component.translatable(menu.extra(DATA_ARMED) != 0 ? T_ARMED : T_READY);
            case COUNTING -> p == Phase.COUNTDOWN ? Component.translatable(T_COUNTING, (ticks + 19) / 20)
                    : Component.translatable(p == Phase.FIRING ? T_FIRING : T_STANDING_DOWN);
            default -> Component.translatable(r == Readiness.EMP ? "groundburst.silo.state.emp" : "groundburst.silo.state.halted");
        };
        Component ammo = Component.translatable(T_AMMO, new ItemStack(ammo()).getHoverName(), menu.extra(DATA_LOADED), capacity());
        Component aim = this.target == null ? Component.translatable(T_NO_TARGET) : Component.translatable(T_TARGET, this.target.getX(), this.target.getZ());
        return List.of(state, ammo, aim);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(dev.groundburst.registry.GBMenus.SUPERWEAPON.get(), containerId, inventory, this,
                dev.groundburst.content.machine.MachineLayouts.SUPERWEAPON, this.data);
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

    @Override
    public Set<String> hiddenBones() {
        return isFormed() ? Set.of() : Set.of("frame");
    }

    /** Its missiles as the model shows them, each at its own launcher's bone. */
    @Override
    public abstract List<MissileCarrier.Shown> shownMissiles();

    @Override
    public MissileCarrier.@Nullable Shown shownMissile() {
        List<MissileCarrier.Shown> all = shownMissiles();
        return all.isEmpty() ? null : all.get(0);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    static final RawAnimation RAISE = RawAnimation.begin().thenPlayAndHold("raise");
    static final RawAnimation LOWER = RawAnimation.begin().thenPlayAndHold("lower");
    /** The launchers at rest (their own rest pose: "idle" belongs to the main controller's "working"). */
    static final RawAnimation LOWERED = RawAnimation.begin().thenPlayAndHold("lowered");
    /** Not charging: its flags still stir in the wind. */
    static final RawAnimation AMBIENT = RawAnimation.begin().thenLoop("ambient");

    @Override
    public RawAnimation idleAnimation() {
        return AMBIENT;
    }

    /**
     * The main controller ("working" while it charges) and the launchers': raised while it is ready to fire; lowered
     * again after, but only once this client has seen them up (a superweapon loaded at rest stays at rest).
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimatedMachine.super.registerControllers(controllers);
        controllers.add(new AnimationController<SuperweaponBlockEntity>("launchers", 0, test -> {
            SuperweaponBlockEntity be = test.animatable();
            if (be.raised) {
                be.clientSawRaised = true;
                return test.setAndContinue(RAISE);
            }
            return test.setAndContinue(be.clientSawRaised ? LOWER : LOWERED);
        }));
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("charge", this.charge);
        output.putString("phase", this.phase.name());
        output.putInt("phase_ticks", this.phaseTicks);
        output.putBoolean("armed", this.armed);
        if (this.target != null) {
            output.putLong("target", this.target.asLong());
        }
        if (this.launchedBy != null) {
            output.putString("launched_by", this.launchedBy.toString());
        }
        output.putBoolean("announced_formed", this.announcedFormed);
        output.putBoolean("announced_ready", this.announcedReady);
        output.putBoolean("raised", this.raised);
        output.putBoolean("fired_this_strike", this.firedThisStrike);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.charge = input.getIntOr("charge", 0);
        try {
            this.phase = Phase.valueOf(input.getStringOr("phase", Phase.IDLE.name()));
        } catch (IllegalArgumentException e) {
            this.phase = Phase.IDLE;
        }
        this.phaseTicks = input.getIntOr("phase_ticks", 0);
        this.armed = input.getBooleanOr("armed", false);
        this.target = input.getLong("target").map(BlockPos::of).orElse(null);
        this.launchedBy = input.getString("launched_by").map(UUID::fromString).orElse(null);
        this.announcedFormed = input.getBooleanOr("announced_formed", false);
        this.announcedReady = input.getBooleanOr("announced_ready", false);
        this.raised = input.getBooleanOr("raised", false);
        this.firedThisStrike = input.getBooleanOr("fired_this_strike", false);
    }
}
