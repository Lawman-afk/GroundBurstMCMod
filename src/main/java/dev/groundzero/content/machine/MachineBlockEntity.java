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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Shared base of every powered machine: FE buffer, item slots, redstone control, the EMP hook, ACTIVE block
 * state upkeep, client sync of GUI values, and save/load.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    // Sync slots shared by every machine menu. Ints are sent as shorts by vanilla, so 32-bit values use two.
    public static final int DATA_ENERGY_LO = 0, DATA_ENERGY_HI = 1, DATA_CAPACITY_LO = 2, DATA_CAPACITY_HI = 3,
            DATA_REDSTONE = 4, DATA_STATUS = 5, DATA_BASE_COUNT = 6;

    protected final MachineEnergy energy;
    protected final MachineInventory inventory;
    protected final AutomationView automation;
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
        super(type, pos, state);
        this.energy = new MachineEnergy(energyCapacity, maxInsert, maxExtract, this::setChanged);
        this.inventory = new MachineInventory(slotCount, this::isItemValid, this::setChanged);
        this.automation = new AutomationView(this.inventory, inputSlots, outputSlots);
    }

    // --- subclass contract ----------------------------------------------------------------------------------------
    /** One server tick of work. Returns whether the machine is actively working (drives ACTIVE, sound, animation). */
    protected abstract boolean tickMachine(ServerLevel level);

    protected abstract boolean isItemValid(int slot, ItemStack stack);

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
        BlockState state = getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, active), 3);
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

    // --- capabilities ------------------------------------------------------------------------------------------------------
    public @Nullable EnergyHandler energyFor(@Nullable Direction side) {
        return this.energy;
    }

    public @Nullable ResourceHandler<ItemResource> itemsFor(@Nullable Direction side) {
        return side == null ? this.inventory : this.automation;
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy.deserialize(input.childOrEmpty("energy"));
        this.inventory.deserialize(input.childOrEmpty("items"));
        this.redstoneMode = RedstoneMode.byId(input.getIntOr("redstone", 0));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.inventory);
        }
    }
}
