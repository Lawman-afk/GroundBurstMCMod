package dev.groundzero.content.launch;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.SideConfig;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.ordnance.charge.BombBlock;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

/**
 * The Launch Silo (DESIGN.md §17.2, basic gantry type): a 3x3 launch pad with a gantry tower beside it, run from this
 * controller at the front of the pad. Slots: 0 the missile, 1 its warhead (any bomb). A Launch Computer gives each silo
 * its own target and arms it; its LAUNCH fires every armed silo. Launching costs 100 000 FE (through the Energy Port at
 * the back of the pad) and runs a 10 s countdown with a siren, then the missile lights and lifts off the pad. An armed
 * silo that stops being ready (power, missile or warhead gone) disarms itself.
 */
public class LaunchSiloBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int ENERGY_CAPACITY = 200_000, LAUNCH_COST = 100_000, COUNTDOWN = 200;
    public static final int SLOT_MISSILE = 0;
    private static final int RECHECK_INTERVAL = 40;
    /** What the silo can do right now (shown on its screen and the computer's). BLOCKED: the way up is not clear. */
    public enum Readiness { NOT_FORMED, EMP, HALTED, NO_MISSILE, NO_POWER, READY, COUNTING, BLOCKED }

    private static final String[] T_STATE = {
            GZLang.add(GroundZero.MODID + ".silo.state.not_formed", "Structure incomplete"),
            GZLang.add(GroundZero.MODID + ".silo.state.emp", "Disabled by EMP"),
            GZLang.add(GroundZero.MODID + ".silo.state.halted", "Halted by redstone"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_missile", "No missile loaded"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_power", "No power: needs %sk FE"),
            GZLang.add(GroundZero.MODID + ".silo.state.ready", "READY"),
            GZLang.add(GroundZero.MODID + ".silo.state.counting", "LAUNCH IN %s"),
            GZLang.add(GroundZero.MODID + ".silo.state.blocked", "Hatch blocked")};
    private static final String T_ARMED = GZLang.add(GroundZero.MODID + ".silo.state.armed", "ARMED");
    private static final String T_HINT = GZLang.add(GroundZero.MODID + ".silo.hint", "Launch from a Launch Computer");
    private static final String T_WARHEAD = GZLang.add(GroundZero.MODID + ".silo.warhead", "Warhead: %s");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int countdown = -1;
    /** Where it is aimed (set from a Launch Computer), and whether it is armed to fire at the next LAUNCH. */
    private @Nullable BlockPos target;
    private boolean armed;
    private @Nullable UUID launchedBy;

    public LaunchSiloBlockEntity(BlockPos pos, BlockState state) {
        this(GZLaunch.SILO_ENTITY.get(), pos, state, ENERGY_CAPACITY);
    }

    /** For heavier launchers (the Launch Complex): their own type and energy buffer. */
    protected LaunchSiloBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity) {
        super(type, pos, state, capacity, 8_000, 0, 1, new int[] {SLOT_MISSILE}, new int[0]);
    }

    /** The energy one launch takes. */
    public int launchCost() {
        return LAUNCH_COST;
    }

    /** How long the countdown runs, ticks. */
    protected int countdownTicks() {
        return COUNTDOWN;
    }

    /** Each tick of the countdown, {@code left} ticks before ignition (the complex's deluge). */
    protected void onCountdown(ServerLevel level, BlockPos pad, int left) {}

    /** Just after the missile is lit (the complex's trench smoke). */
    protected void onLiftOff(ServerLevel level, BlockPos pad) {}

    /** Whether to hold the countdown at T-1 (the Missile Silo until its doors are open). */
    protected boolean holdLaunch(ServerLevel level) {
        return false;
    }

    /** Where the missile's tail is lit: on the pad (the Missile Silo's launch ring, at the bottom of its shaft). */
    protected Vec3 launchPoint(BlockPos pad) {
        return Vec3.atBottomCenterOf(pad);
    }

    /** How far the missile climbs straight up out of a shaft before it turns (0: none, it stands on a pad). */
    protected double shaftHeight() {
        return 0;
    }

    /** Whether something is in the missile's way up (a block over the Missile Silo's hatch). */
    protected boolean blocked() {
        return false;
    }

    /** Whether a stack can be a warhead: any bomb. */
    public static boolean isWarhead(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof BombBlock;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        var spec = dev.groundzero.content.assembly.MissileItem.spec(stack);
        return slot == SLOT_MISSILE && spec != null && accepts(spec);
    }

    /**
     * Whether it can fire this missile: the Launch Silo none that needs more (a nuclear warhead, a Strategic or an
     * intercontinental airframe); the Launch Complex and the Missile Silo any.
     */
    public boolean accepts(dev.groundzero.content.assembly.MissileSpec spec) {
        return !spec.needsComplex();
    }

    /** The loaded missile (null: none). */
    public dev.groundzero.content.assembly.@org.jspecify.annotations.Nullable MissileSpec missile() {
        return dev.groundzero.content.assembly.MissileItem.spec(this.inventory.getItem(SLOT_MISSILE));
    }

    /** How far the loaded missile flies (0: none loaded). */
    public int range() {
        var spec = missile();
        return spec == null ? 0 : spec.range();
    }

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
        if (this.countdown >= 0) {
            return Readiness.COUNTING;
        }
        if (this.inventory.getItem(SLOT_MISSILE).isEmpty()) {
            return Readiness.NO_MISSILE;
        }
        if (blocked()) {
            return Readiness.BLOCKED;
        }
        return this.energy.stored() < launchCost() ? Readiness.NO_POWER : Readiness.READY;
    }

    public int countdown() {
        return this.countdown;
    }

    /** For GameTests: jump to the end of the countdown. */
    public void skipCountdownForTest() {
        if (this.countdown > 1) {
            this.countdown = 1;
        }
    }

    public @Nullable BlockPos target() {
        return this.target;
    }

    public boolean armed() {
        return this.armed;
    }

    /** Aims the silo (not while it counts down). */
    public boolean setTarget(int x, int z) {
        if (this.countdown >= 0) {
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
        return Math.sqrt(Math.pow(at.getX() - this.worldPosition.getX(), 2) + Math.pow(at.getZ() - this.worldPosition.getZ(), 2))
                <= range();
    }

    /** Arms (only a ready silo with a target in range) or makes safe. */
    public boolean arm(boolean on) {
        if (on && (readiness() != Readiness.READY || this.target == null || !inRange(this.target))) {
            return false;
        }
        this.armed = on;
        setChanged();
        return true;
    }

    /** Fires if armed: the countdown to launch at its target. */
    public boolean launchArmed(ServerLevel level, @Nullable Entity by) {
        return this.armed && this.target != null && launch(level, this.target, by);
    }

    /** Starts the countdown to launch at {@code target}; false if the silo is not ready. */
    public boolean launch(ServerLevel level, BlockPos target, @Nullable Entity by) {
        if (readiness() != Readiness.READY || isEmpDisabled()) {
            return false;
        }
        this.target = target.immutable();
        this.armed = false;
        this.launchedBy = by == null ? null : by.getUUID();
        this.countdown = countdownTicks();
        level.playSound(null, padCentre(), GZLaunch.SIREN.get(), SoundSource.BLOCKS, 4f, 1f);
        setChanged();
        return true;
    }

    /** The middle of the pad's top, where the missile stands. */
    public BlockPos padCentre() {
        return definition().cellPos(this.worldPosition, controllerFacing(), 1, 1, 1);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZLaunch.SILO;
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

    // --- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        Launches.track(level, this.worldPosition);           // a freshly placed silo is not loaded until its first tick
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            this.countdown = -1;
            return false;
        }
        if (this.countdown < 0) {
            boolean ready = readiness() == Readiness.READY;
            if (this.armed && !ready) {
                this.armed = false;                          // power, missile or warhead gone: safe again
                setChanged();
            }
            this.status = ready ? MachineStatus.IDLE : MachineStatus.NO_RECIPE;
            return false;
        }
        this.status = MachineStatus.RUNNING;
        BlockPos pad = padCentre();
        if (this.countdown <= 1 && holdLaunch(level)) {
            this.countdown = 1;                              // held at T-1 (the silo's doors still opening)
            onCountdown(level, pad, 1);
            return true;
        }
        this.countdown--;
        if (this.countdown > 0 && this.countdown % 20 == 0) {
            level.playSound(null, pad, dev.groundzero.content.ordnance.GZOrdnance.BOMB_BEEP.get(), SoundSource.BLOCKS, 2f, this.countdown <= 60 ? 1.4f : 1f);
        }
        if (this.countdown > 0) {
            onCountdown(level, pad, this.countdown);
        }
        if (this.countdown == 0) {
            liftOff(level, pad);
        }
        return true;
    }

    /** Knocked out by an EMP, or stopped by redstone: an armed silo is made safe and a countdown is scrubbed. */
    private void standDown() {
        if (this.armed || this.countdown >= 0) {
            this.armed = false;
            this.countdown = -1;
            setChanged();
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

    /** T-0: the missile is lit on the pad (it climbs away after its ignition), carrying its warhead. */
    private void liftOff(ServerLevel level, BlockPos pad) {
        this.countdown = -1;
        var spec = missile();
        if (this.target == null || spec == null || readinessIgnoringCountdown() != Readiness.READY) {
            return;
        }
        this.inventory.removeItem(SLOT_MISSILE, 1);
        this.energy.consume(launchCost());
        Entity by = this.launchedBy == null ? null : level.getEntity(this.launchedBy);
        // Its guidance decides how close to the target it comes down.
        Vec3 from = launchPoint(pad);
        Vec3 aim = spec.guidance().aim(from, Vec3.atBottomCenterOf(this.target), level.getRandom());
        MissileEntity.launch(level, from, aim, spec, by, shaftHeight());
        onLiftOff(level, pad);
        setChanged();
    }

    private Readiness readinessIgnoringCountdown() {
        int saved = this.countdown;
        this.countdown = -1;
        Readiness r = readiness();
        this.countdown = saved;
        return r;
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
    private static final int DATA_READINESS = 0, DATA_COUNTDOWN = 1, DATA_ARMED = 2;

    @Override
    protected int extraDataCount() {
        return 3;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_READINESS -> readiness().ordinal();
            case DATA_COUNTDOWN -> Math.max(0, this.countdown);
            default -> this.armed ? 1 : 0;
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        Readiness r = Readiness.values()[Math.clamp(menu.extra(DATA_READINESS), 0, Readiness.values().length - 1)];
        Component state = r == Readiness.COUNTING ? Component.translatable(T_STATE[r.ordinal()], (menu.extra(DATA_COUNTDOWN) + 19) / 20)
                : r == Readiness.NO_POWER ? Component.translatable(T_STATE[r.ordinal()], launchCost() / 1000)
                : r == Readiness.READY && menu.extra(DATA_ARMED) != 0 ? Component.translatable(T_ARMED)
                : Component.translatable(T_STATE[r.ordinal()]);
        var spec = missile();
        return List.of(state, spec == null ? Component.translatable(T_HINT) : Component.translatable(T_WARHEAD, spec.warhead().getHoverName()));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(dev.groundzero.registry.GZMenus.LAUNCH_SILO.get(), containerId, inventory, this, dev.groundzero.content.machine.MachineLayouts.LAUNCH_SILO, this.data);
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    /** The model shows the missile while one is loaded, its nose painted as its warhead, so the client hears about both slots. */
    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
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
        if (!isFormed()) {
            return Set.of("frame");
        }
        if (this.inventory.getItem(SLOT_MISSILE).isEmpty()) {
            return Set.of("missile");
        }
        // Every nose but the fitted warhead's (a bare steel nose with none).
        var spec = missile();
        String id = spec == null ? "" : spec.warheadId();
        String shown = "nose_" + (id.isEmpty() ? "none" : id);
        Set<String> hidden = new java.util.HashSet<>();
        for (String nose : NOSES) {
            if (!nose.equals(shown)) {
                hidden.add(nose);
            }
        }
        return hidden;
    }

    /** The missile drawn the size of its body. */
    @Override
    public java.util.Map<String, float[]> boneScales() {
        var spec = missile();
        if (spec == null) {
            return java.util.Map.of();
        }
        float[] s = dev.groundzero.content.assembly.MissileParts.scale(spec.body());
        return java.util.Map.of("missile", new float[] {s[0], s[1], s[0]});
    }

    /** The silo model's nose bones (tools/assets/launch.py), one per warhead. */
    protected static final List<String> NOSES = java.util.stream.Stream.concat(java.util.stream.Stream.of("nose_none"),
            dev.groundzero.content.ordnance.GZOrdnance.WARHEADS.stream().map(w -> "nose_" + w.block().getId().getPath())).toList();

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("countdown", this.countdown);
        output.putBoolean("armed", this.armed);
        if (this.target != null) {
            output.putLong("target", this.target.asLong());
        }
        if (this.launchedBy != null) {
            output.putString("launched_by", this.launchedBy.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.countdown = input.getIntOr("countdown", -1);
        this.armed = input.getBooleanOr("armed", false);
        this.target = input.getLong("target").map(BlockPos::of).orElse(null);
        this.launchedBy = input.getString("launched_by").map(UUID::fromString).orElse(null);
    }
}
