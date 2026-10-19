package dev.groundzero.content.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Shared base of every powered machine: FE buffer, item slots, fluid tanks, redstone control, the EMP hook, ACTIVE
 * block state upkeep, client sync of GUI values (and of tank levels for models that show them), and save/load.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    // Sync slots shared by every machine menu. Ints are sent as shorts by vanilla, so 32-bit values use two.
    public static final int DATA_ENERGY_LO = 0, DATA_ENERGY_HI = 1, DATA_CAPACITY_LO = 2, DATA_CAPACITY_HI = 3,
            DATA_REDSTONE = 4, DATA_STATUS = 5, DATA_SIDE_ITEMS = 6, DATA_SIDE_FLAGS = 7,
            DATA_TANKS = 8, DATA_BASE_COUNT = 16;
    /** Tanks a menu can show (two sync slots each: amount, fluid id). Amounts stay below 32768 mB to fit a short. */
    public static final int MAX_TANKS = 4;
    private static final int EJECT_INTERVAL = 8, EJECT_BATCH = 16, FLUID_EJECT_BATCH = 2000;

    protected final MachineEnergy energy;
    protected final MachineInventory inventory;
    /** Fluid tanks in GUI order (empty for machines without any). */
    protected final List<MachineTank> tanks;
    private final MachineFluidHandler fluidHandler;
    /** Slots that fill or empty fluid containers (buckets, canisters, other mods' tanks), or -1. */
    protected final int containerIn, containerOut;
    /** Automation views by item mode (NONE has none). */
    private final AutomationView[] automation = new AutomationView[SideConfig.ItemMode.values().length];
    private final int[] inputSlots;
    private final int[] outputSlots;
    protected final SideConfig sides = new SideConfig();
    protected RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    /** Why the machine is (not) running; shown in the GUI. */
    protected MachineStatus status = MachineStatus.IDLE;

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LO -> energy.stored() & 0xFFFF;
                case DATA_ENERGY_HI -> energy.stored() >>> 16;
                case DATA_CAPACITY_LO -> energy.capacity() & 0xFFFF;
                case DATA_CAPACITY_HI -> energy.capacity() >>> 16;
                case DATA_REDSTONE -> redstoneMode.ordinal();
                case DATA_STATUS -> status.ordinal();
                case DATA_SIDE_ITEMS -> sides.packItems();
                case DATA_SIDE_FLAGS -> sides.packFlags();
                default -> {
                    if (index >= DATA_TANKS && index < DATA_BASE_COUNT) {
                        int t = (index - DATA_TANKS) / 2;
                        if (t >= tanks.size()) {
                            yield 0;
                        }
                        yield (index - DATA_TANKS) % 2 == 0 ? tanks.get(t).amount() : tanks.get(t).fluidId();
                    }
                    yield getExtraData(index - DATA_BASE_COUNT);
                }
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; the client copy is a plain array (see MachineMenu).
        }

        @Override
        public int getCount() {
            return DATA_BASE_COUNT + extraDataCount();
        }
    };

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxInsert,
            int maxExtract, int slotCount, int[] inputSlots, int[] outputSlots) {
        this(type, pos, state, energyCapacity, maxInsert, maxExtract, slotCount, inputSlots, outputSlots, List.of(), -1, -1);
    }

    /** With tanks: {@code containerIn}/{@code containerOut} are the slot pair that fills and empties fluid containers. */
    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxInsert,
            int maxExtract, int slotCount, int[] inputSlots, int[] outputSlots, List<MachineTank.Spec> tankSpecs,
            int containerIn, int containerOut) {
        super(type, pos, state);
        if (tankSpecs.size() > MAX_TANKS) {
            throw new IllegalArgumentException("at most " + MAX_TANKS + " tanks");
        }
        this.energy = new MachineEnergy(energyCapacity, maxInsert, maxExtract, this::setChanged);
        this.inventory = new MachineInventory(slotCount, this::isItemValid, this::onSlotChanged);
        List<MachineTank> list = new ArrayList<>();
        for (int i = 0; i < tankSpecs.size(); i++) {
            int index = i;
            list.add(new MachineTank(tankSpecs.get(i), r -> acceptsFluid(index, r), this::onTankChanged));
        }
        this.tanks = List.copyOf(list);
        this.fluidHandler = new MachineFluidHandler(this.tanks);
        this.containerIn = containerIn;
        this.containerOut = containerOut;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.sides.restrict(allowedItemModes());
        for (SideConfig.ItemMode mode : SideConfig.ItemMode.values()) {
            if (mode != SideConfig.ItemMode.NONE) {
                this.automation[mode.ordinal()] = new AutomationView(this.inventory, inputSlots, outputSlots, mode.canInsert(), mode.canExtract());
            }
        }
    }

    // --- subclass contract ----------------------------------------------------------------------------------------
    /** One server tick of work. Returns whether the machine is actively working (drives ACTIVE, sound, animation). */
    protected abstract boolean tickMachine(ServerLevel level);

    protected abstract boolean isItemValid(int slot, ItemStack stack);

    /** Whether input tank {@code tank} may take this fluid (beyond its spec's filter), e.g. "a recipe uses it". */
    protected boolean acceptsFluid(int tank, FluidResource resource) {
        return true;
    }

    /** Where the model shows tank contents (model pixels, machine facing north); empty if it shows none. */
    public List<TankRender> tankRenders() {
        return List.of();
    }

    /** Whether the model currently shows its tanks (a multiblock only does once formed). */
    public boolean showsTanks() {
        return true;
    }

    /** A box in model pixels (x/z centred on the block, y from the bottom) that the renderer fills with tank {@code tank}'s fluid. */
    public record TankRender(int tank, float x0, float y0, float z0, float x1, float y1, float z1) {}

    /** Fill steps clients are told about: enough for a smooth-looking level without a packet per millibucket. */
    private static final int SYNC_STEPS = 48;
    private final int[] syncedLevel = new int[MAX_TANKS];
    {
        java.util.Arrays.fill(this.syncedLevel, -1);
    }

    /** A tank's contents changed: save, and if the model shows that tank, tell clients when its level moves a step. */
    protected void onTankChanged() {
        setChanged();
        if (this.level == null || this.level.isClientSide() || tankRenders().isEmpty()) {
            return;
        }
        boolean changed = false;
        for (TankRender r : tankRenders()) {
            MachineTank t = this.tanks.get(r.tank());
            int key = t.fluidId() * (SYNC_STEPS + 1) + (int) ((long) t.amount() * SYNC_STEPS / t.capacity());
            if (key != this.syncedLevel[r.tank()]) {
                this.syncedLevel[r.tank()] = key;
                changed = true;
            }
        }
        if (changed) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return tankRenders().isEmpty() ? super.getUpdateTag(registries) : saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return tankRenders().isEmpty() ? super.getUpdatePacket() : ClientboundBlockEntityDataPacket.create(this);
    }

    /** A slot changed (-1: any slot may have). Subclasses that show slot contents on the model sync here. */
    protected void onSlotChanged(int slot) {
        setChanged();
    }

    protected int extraDataCount() {
        return 0;
    }

    protected int getExtraData(int index) {
        return 0;
    }

    // --- ticking ----------------------------------------------------------------------------------------------------------
    public void serverTick(ServerLevel level) {
        boolean active;
        if (isEmpDisabled()) {
            status = MachineStatus.EMP;
            active = false;
        } else if (!redstoneMode.allows(level.hasNeighborSignal(worldPosition))) {
            status = MachineStatus.REDSTONE;
            active = false;
        } else {
            active = tickMachine(level);
        }
        handleContainers();
        if (this.sides.autoEject() && (level.getGameTime() + worldPosition.hashCode()) % EJECT_INTERVAL == 0) {
            if (this.outputSlots.length > 0) {
                autoEject(level);
            }
            autoEjectFluids(level);
        }
        BlockState state = getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, active), 3);
        }
    }

    /**
     * The container slot pair: a container holding fluid empties into the input tank that takes it; an empty one is
     * filled from the first output tank it can hold. One item is handled at a time and the result goes to the out
     * slot, so a stack of buckets or canisters works through one by one.
     */
    private void handleContainers() {
        if (this.containerIn < 0 || this.tanks.isEmpty()) {
            return;
        }
        ItemStack in = this.inventory.getItem(this.containerIn);
        if (in.isEmpty()) {
            return;
        }
        ItemStacksResourceHandler temp = new ItemStacksResourceHandler(1);
        temp.set(0, ItemResource.of(in), 1);
        ResourceHandler<FluidResource> item = ItemAccess.forHandlerIndexStrict(temp, 0).getCapability(Capabilities.Fluid.ITEM);
        if (item == null) {
            return;
        }
        boolean holdsFluid = false;
        for (int i = 0; i < item.size(); i++) {
            holdsFluid |= item.getAmountAsLong(i) > 0;
        }
        int moved = 0;
        try (Transaction tx = Transaction.openRoot()) {
            if (holdsFluid) {
                moved = ResourceHandlerUtil.move(item, this.fluidHandler, r -> true, Integer.MAX_VALUE, tx);
            } else {
                for (MachineTank tank : this.tanks) {
                    if (!tank.isInput() && !tank.isEmpty()) {
                        moved = ResourceHandlerUtil.move(tank, item, r -> true, Integer.MAX_VALUE, tx);
                        if (moved > 0) {
                            break;
                        }
                    }
                }
            }
            if (moved <= 0) {
                return;
            }
            ItemStack result = temp.getResource(0).toStack(temp.getAmountAsInt(0));
            ItemStack out = this.inventory.getItem(this.containerOut);
            if (!result.isEmpty()) {
                if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, result) || out.getCount() + result.getCount() > out.getMaxStackSize())) {
                    return;                                   // nowhere to put the result: try again later
                }
            }
            tx.commit();
            in.shrink(1);
            this.inventory.setItem(this.containerIn, in.isEmpty() ? ItemStack.EMPTY : in);
            if (!result.isEmpty()) {
                if (out.isEmpty()) {
                    this.inventory.setItem(this.containerOut, result);
                } else {
                    out.grow(result.getCount());
                    this.inventory.setItem(this.containerOut, out);
                }
            }
        }
    }

    /** EMP hook: Phase 12 makes EMP fields disable machines here. Until then nothing can disable a machine. */
    protected boolean isEmpDisabled() {
        return false;
    }

    /** The work loop the client plays while this machine is ACTIVE, or null for a silent machine. */
    public @Nullable SoundEvent loopSound() {
        return null;
    }

    // --- capabilities & side configuration ----------------------------------------------------------------------------------
    /** The machine face a world direction falls on. */
    public SideConfig.Face face(Direction side) {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : Direction.NORTH;
        return SideConfig.Face.of(side, facing);
    }

    /**
     * Whether anything can connect to a face. A multiblock controller's faces that lie against its own structure
     * cannot: they expose nothing and the side tab shows them blocked.
     */
    public boolean faceOpen(SideConfig.Face face) {
        return true;
    }

    /** Pours output tanks into the fluid handlers beside every face whose fluid switch is on. */
    private void autoEjectFluids(ServerLevel level) {
        if (this.tanks.stream().allMatch(t -> t.isInput() || t.isEmpty())) {
            return;
        }
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        for (SideConfig.Face face : SideConfig.Face.values()) {
            if (!this.sides.fluid(face) || !faceOpen(face)) {
                continue;
            }
            Direction dir = face.toDirection(facing);
            BlockPos target = this.worldPosition.relative(dir);
            if (getBlockState().getBlock() instanceof TallMachineBlock && dir == Direction.UP) {
                target = target.above();
            }
            ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, target, dir.getOpposite());
            if (handler == null) {
                continue;
            }
            for (MachineTank tank : this.tanks) {
                if (!tank.isInput() && !tank.isEmpty()) {
                    ResourceHandlerUtil.move(tank, handler, r -> true, FLUID_EJECT_BATCH, null);
                }
            }
        }
    }

    public @Nullable EnergyHandler energyFor(@Nullable Direction side) {
        if (side != null && !faceOpen(face(side))) {
            return null;
        }
        return side == null || this.sides.energy(face(side)) ? this.energy : null;
    }

    public @Nullable ResourceHandler<FluidResource> fluidsFor(@Nullable Direction side) {
        if (this.tanks.isEmpty() || side != null && !faceOpen(face(side))) {
            return null;
        }
        return side == null || this.sides.fluid(face(side)) ? this.fluidHandler : null;
    }

    /** Every tank, in GUI order. */
    public List<MachineTank> tanks() {
        return this.tanks;
    }

    public MachineTank tank(int index) {
        return this.tanks.get(index);
    }

    /** What item ports, buckets clicked on the block and the side-less capability see: fill inputs, drain outputs. */
    public MachineFluidHandler fluidHandler() {
        return this.fluidHandler;
    }

    /** Empties a dumpable tank (the GUI's dump button). */
    public void dumpTank(int index) {
        if (index >= 0 && index < this.tanks.size() && this.tanks.get(index).spec().dumpable()) {
            this.tanks.get(index).dump();
        }
    }

    public @Nullable ResourceHandler<ItemResource> itemsFor(@Nullable Direction side) {
        if (side == null) {
            return this.inventory;
        }
        return faceOpen(face(side)) ? this.automation[this.sides.items(face(side)).ordinal()] : null;
    }

    public SideConfig sides() {
        return sides;
    }

    /** What a multiblock's item ports expose: inputs in, products out, regardless of the side configuration. */
    public ResourceHandler<ItemResource> portItems() {
        return this.automation[SideConfig.ItemMode.BOTH.ordinal()] != null
                ? this.automation[SideConfig.ItemMode.BOTH.ordinal()] : this.inventory;
    }

    /** The item modes a face may take: only directions this machine actually has slots for. */
    public java.util.Set<SideConfig.ItemMode> allowedItemModes() {
        java.util.Set<SideConfig.ItemMode> modes = java.util.EnumSet.of(SideConfig.ItemMode.NONE);
        if (this.inputSlots.length > 0) {
            modes.add(SideConfig.ItemMode.INPUT);
        }
        if (this.outputSlots.length > 0) {
            modes.add(SideConfig.ItemMode.OUTPUT);
        }
        if (this.inputSlots.length > 0 && this.outputSlots.length > 0) {
            modes.add(SideConfig.ItemMode.BOTH);
        }
        return modes;
    }

    /** Whether this machine has output slots (auto-eject is offered only then). */
    public boolean hasOutputs() {
        return this.outputSlots.length > 0;
    }

    /** Whether the item channel is fuel (generators) rather than inputs and products; only changes GUI wording. */
    public boolean itemsAreFuel() {
        return false;
    }

    /** Applies a side-config change from the GUI and tells cables, pipes and capability caches about it. */
    public void changeSides(java.util.function.Consumer<SideConfig> change) {
        change.accept(this.sides);
        setChanged();
        if (this.level != null) {
            this.level.invalidateCapabilities(this.worldPosition);
            this.level.updateNeighborsAt(this.worldPosition, getBlockState().getBlock());
            if (getBlockState().getBlock() instanceof TallMachineBlock) {
                this.level.invalidateCapabilities(this.worldPosition.above());
                this.level.updateNeighborsAt(this.worldPosition.above(), getBlockState().getBlock());
            }
        }
    }

    /** Pushes output slots into the item handlers beside every output face. */
    private void autoEject(ServerLevel level) {
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        AutomationView out = new AutomationView(this.inventory, new int[0], this.outputSlots, false, true);
        for (SideConfig.Face face : SideConfig.Face.values()) {
            if (!this.sides.items(face).canExtract() || !faceOpen(face)) {
                continue;
            }
            Direction dir = face.toDirection(facing);
            BlockPos target = this.worldPosition.relative(dir);
            if (getBlockState().getBlock() instanceof TallMachineBlock && dir == Direction.UP) {
                target = target.above();
            }
            ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, target, dir.getOpposite());
            if (handler != null) {
                ResourceHandlerUtil.moveStacking(out, handler, r -> true, EJECT_BATCH, null);
            }
        }
    }

    // --- accessors -------------------------------------------------------------------------------------------------------
    public MachineInventory inventory() {
        return inventory;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public ContainerData data() {
        return data;
    }

    public void cycleRedstoneMode() {
        this.redstoneMode = this.redstoneMode.next();
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    // --- persistence -------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.energy.serialize(output.child("energy"));
        this.inventory.serialize(output.child("items"));
        output.putInt("redstone", this.redstoneMode.ordinal());
        this.sides.save(output.child("sides"));
        for (int i = 0; i < this.tanks.size(); i++) {
            this.tanks.get(i).serialize(output.child("tank" + i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy.deserialize(input.childOrEmpty("energy"));
        this.inventory.deserialize(input.childOrEmpty("items"));
        this.redstoneMode = RedstoneMode.byId(input.getIntOr("redstone", 0));
        this.sides.load(input.childOrEmpty("sides"));
        this.sides.restrict(allowedItemModes());
        for (int i = 0; i < this.tanks.size(); i++) {
            this.tanks.get(i).deserialize(input.childOrEmpty("tank" + i));
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.inventory);
        }
    }
}
