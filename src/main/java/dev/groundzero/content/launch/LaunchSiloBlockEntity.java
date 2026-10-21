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
 * controller at the front of the pad. Slots: 0 the missile, 1 its warhead (any bomb). Launching, ordered from a Launch
 * Computer, costs 100 000 FE (through the Energy Port at the back of the pad) and runs a 10 s countdown with a siren,
 * then the missile lights and lifts off the pad.
 */
public class LaunchSiloBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int ENERGY_CAPACITY = 200_000, LAUNCH_COST = 100_000, COUNTDOWN = 200;
    public static final int SLOT_MISSILE = 0, SLOT_WARHEAD = 1;
    private static final int RECHECK_INTERVAL = 40;
    /** What the silo can do right now (shown on its screen and the computer's). */
    public enum Readiness { NOT_FORMED, NO_MISSILE, NO_WARHEAD, NO_POWER, READY, COUNTING }

    private static final String[] T_STATE = {
            GZLang.add(GroundZero.MODID + ".silo.state.not_formed", "Structure incomplete"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_missile", "No missile loaded"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_warhead", "No warhead fitted"),
            GZLang.add(GroundZero.MODID + ".silo.state.no_power", "Charging: needs 100k FE"),
            GZLang.add(GroundZero.MODID + ".silo.state.ready", "READY"),
            GZLang.add(GroundZero.MODID + ".silo.state.counting", "LAUNCH IN %s")};
    private static final String T_HINT = GZLang.add(GroundZero.MODID + ".silo.hint", "Launch from a Launch Computer");
    private static final String T_WARHEAD = GZLang.add(GroundZero.MODID + ".silo.warhead", "Warhead: %s");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int countdown = -1;
    private @Nullable BlockPos target;
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

    /** Starts the countdown to launch at {@code target}; false if the silo is not ready. */
    public boolean launch(ServerLevel level, BlockPos target, @Nullable Entity by) {
        if (readiness() != Readiness.READY) {
            return false;
        }
        this.target = target.immutable();
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
            this.status = readiness() == Readiness.READY ? MachineStatus.IDLE : MachineStatus.NO_RECIPE;
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
    private static final int DATA_READINESS = 0, DATA_COUNTDOWN = 1;

    @Override
    protected int extraDataCount() {
        return 2;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_READINESS ? readiness().ordinal() : Math.max(0, this.countdown);
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        Readiness r = Readiness.values()[Math.clamp(menu.extra(DATA_READINESS), 0, Readiness.values().length - 1)];
        Component state = r == Readiness.COUNTING ? Component.translatable(T_STATE[r.ordinal()], (menu.extra(DATA_COUNTDOWN) + 19) / 20)
                : Component.translatable(T_STATE[r.ordinal()]);
        ItemStack warhead = this.inventory.getItem(SLOT_WARHEAD);
        return List.of(state, warhead.isEmpty() ? Component.translatable(T_HINT) : Component.translatable(T_WARHEAD, warhead.getHoverName()));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(dev.groundzero.registry.GZMenus.LAUNCH_SILO.get(), containerId, inventory, this, dev.groundzero.content.machine.MachineLayouts.LAUNCH_SILO, this.data);
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    /** Unformed, the controller is a plain block; formed, the missile shows on the pad while one is loaded. */
    /** The model shows the missile while one is loaded, so the client hears about that slot. */
    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        if ((slot == SLOT_MISSILE || slot < 0) && this.level != null && !this.level.isClientSide()) {
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
        return this.inventory.getItem(SLOT_MISSILE).isEmpty() ? Set.of("missile") : Set.of();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("countdown", this.countdown);
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
        this.target = input.getLong("target").map(BlockPos::of).orElse(null);
        this.launchedBy = input.getString("launched_by").map(UUID::fromString).orElse(null);
    }
}
