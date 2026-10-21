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
    public static final int SLOT_MISSILE = 0, SLOT_WARHEAD = 1;
    private static final int RECHECK_INTERVAL = 40;
    /** What the silo can do right now (shown on its screen and the computer's). */
    public enum Readiness { NOT_FORMED, EMP, HALTED, NO_MISSILE, NO_WARHEAD, NO_POWER, READY, COUNTING }

    private static final String[] T_STATE = {
            GZLang.add(GroundZero.MODID + ".silo.state.not_formed", "Structure incomplete"),
            GZLang.add(GroundZero.MODID + ".silo.state.emp", "Disabled by EMP"),
            GZLang.add(GroundZero.MODID + ".silo.state.halted", "Halted by redstone"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_missile", "No missile loaded"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_warhead", "No warhead fitted"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_power", "No power: needs 100k FE"),
            GZLang.add(GroundZero.MODID + ".silo.state.ready", "READY"),
            GZLang.add(GroundZero.MODID + ".silo.state.counting", "LAUNCH IN %s")};
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
        super(GZLaunch.SILO_ENTITY.get(), pos, state, ENERGY_CAPACITY, 4_000, 0, 2, new int[] {SLOT_MISSILE, SLOT_WARHEAD}, new int[0]);
    }

    /** Whether a stack can be a warhead: any bomb. */
    public static boolean isWarhead(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof BombBlock;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == SLOT_MISSILE ? stack.is(GZLaunch.BASIC_MISSILE.get()) : slot == SLOT_WARHEAD && isWarhead(stack);
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
        if (this.inventory.getItem(SLOT_WARHEAD).isEmpty()) {
            return Readiness.NO_WARHEAD;
        }
        return this.energy.stored() < LAUNCH_COST ? Readiness.NO_POWER : Readiness.READY;
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
                <= MissileEntity.MAX_RANGE;
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
        this.countdown = COUNTDOWN;
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
        this.countdown--;
        BlockPos pad = padCentre();
        if (this.countdown > 0 && this.countdown % 20 == 0) {
            level.playSound(null, pad, dev.groundzero.content.ordnance.GZOrdnance.BOMB_BEEP.get(), SoundSource.BLOCKS, 2f, this.countdown <= 60 ? 1.4f : 1f);
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
        ItemStack warhead = this.inventory.getItem(SLOT_WARHEAD).copyWithCount(1);
        if (this.target == null || readinessIgnoringCountdown() != Readiness.READY) {
            return;
        }
        this.inventory.removeItem(SLOT_MISSILE, 1);
        this.inventory.removeItem(SLOT_WARHEAD, 1);
        this.energy.consume(LAUNCH_COST);
        Entity by = this.launchedBy == null ? null : level.getEntity(this.launchedBy);
        MissileEntity.launch(level, Vec3.atBottomCenterOf(pad), Vec3.atBottomCenterOf(this.target), warhead, by);
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
                : r == Readiness.READY && menu.extra(DATA_ARMED) != 0 ? Component.translatable(T_ARMED)
                : Component.translatable(T_STATE[r.ordinal()]);
        ItemStack warhead = this.inventory.getItem(SLOT_WARHEAD);
        return List.of(state, warhead.isEmpty() ? Component.translatable(T_HINT) : Component.translatable(T_WARHEAD, warhead.getHoverName()));
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
        String id = MissileEntity.warheadId(this.inventory.getItem(SLOT_WARHEAD));
        String shown = "nose_" + (id.isEmpty() ? "none" : id);
        Set<String> hidden = new java.util.HashSet<>();
        for (String nose : NOSES) {
            if (!nose.equals(shown)) {
                hidden.add(nose);
            }
        }
        return hidden;
    }

    /** The silo model's nose bones (tools/assets/launch.py), one per warhead. */
    private static final List<String> NOSES = java.util.stream.Stream.concat(java.util.stream.Stream.of("nose_none"),
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
