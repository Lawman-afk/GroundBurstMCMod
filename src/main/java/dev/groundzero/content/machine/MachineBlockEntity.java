package dev.groundzero.content.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
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

/**
 * Shared base of every powered machine: FE buffer, item slots, redstone control, the EMP hook, ACTIVE block
 * state upkeep, client sync of GUI values, and save/load.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    // Sync slots shared by every machine menu. Ints are sent as shorts by vanilla, so 32-bit values use two.
    public static final int DATA_ENERGY_LO = 0, DATA_ENERGY_HI = 1, DATA_CAPACITY_LO = 2, DATA_CAPACITY_HI = 3,
            DATA_REDSTONE = 4, DATA_STATUS = 5, DATA_SIDE_ITEMS = 6, DATA_SIDE_FLAGS = 7, DATA_TANK_AMOUNT = 8,
            DATA_TANK_FLUID = 9, DATA_BASE_COUNT = 10;
    private static final int EJECT_INTERVAL = 8, EJECT_BATCH = 16;

    protected final MachineEnergy energy;
    protected final MachineInventory inventory;
    /** Input fluid tank, or null for machines without one. */
    protected final @Nullable MachineTank tank;
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
                case DATA_TANK_AMOUNT -> tank == null ? 0 : tank.amount();
                case DATA_TANK_FLUID -> tank == null ? 0 : tank.fluidId();
                default -> getExtraData(index - DATA_BASE_COUNT);
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
        this(type, pos, state, energyCapacity, maxInsert, maxExtract, slotCount, inputSlots, outputSlots, null);
    }

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxInsert,
            int maxExtract, int slotCount, int[] inputSlots, int[] outputSlots, MachineTank.@Nullable Spec tankSpec) {
        super(type, pos, state);
        this.energy = new MachineEnergy(energyCapacity, maxInsert, maxExtract, this::setChanged);
        this.inventory = new MachineInventory(slotCount, this::isItemValid, this::onSlotChanged);
        this.tank = tankSpec == null ? null : new MachineTank(tankSpec, this::setChanged);
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
        drainContainerSlot();
        if (this.sides.autoEject() && this.outputSlots.length > 0 && (level.getGameTime() + worldPosition.hashCode()) % EJECT_INTERVAL == 0) {
            autoEject(level);
        }
        BlockState state = getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, active), 3);
        }
    }

    /** Empties a fluid container (bucket, tank item) sitting in the tank's container slot into the tank. */
    private void drainContainerSlot() {
        if (this.tank == null || this.tank.spec().containerSlot() < 0) {
            return;
        }
        int slot = this.tank.spec().containerSlot();
        if (this.inventory.getItem(slot).isEmpty() || this.tank.amount() >= this.tank.capacity()) {
            return;
        }
        ResourceHandler<FluidResource> item = ItemAccess.forHandlerIndex(this.inventory, slot).getCapability(Capabilities.Fluid.ITEM);
        if (item != null) {
            ResourceHandlerUtil.move(item, this.tank, r -> this.tank.isValid(0, r), this.tank.capacity() - this.tank.amount(), null);
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

    public @Nullable EnergyHandler energyFor(@Nullable Direction side) {
        return side == null || this.sides.energy(face(side)) ? this.energy : null;
    }

    public @Nullable ResourceHandler<FluidResource> fluidsFor(@Nullable Direction side) {
        if (this.tank == null) {
            return null;
        }
        return side == null || this.sides.fluid(face(side)) ? this.tank.fillOnly() : null;
    }

    public @Nullable MachineTank tank() {
        return tank;
    }

    public @Nullable ResourceHandler<ItemResource> itemsFor(@Nullable Direction side) {
        return side == null ? this.inventory : this.automation[this.sides.items(face(side)).ordinal()];
    }

    public SideConfig sides() {
        return sides;
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
            if (!this.sides.items(face).canExtract()) {
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
        if (this.tank != null) {
            this.tank.serialize(output.child("tank"));
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
        if (this.tank != null) {
            this.tank.deserialize(input.childOrEmpty("tank"));
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.inventory);
        }
    }
}
