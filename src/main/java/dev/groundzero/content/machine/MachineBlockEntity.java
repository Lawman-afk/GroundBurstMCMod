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

/**
 * Shared base of every powered machine: FE buffer, item slots, fluid tanks, redstone control, the EMP hook, ACTIVE
 * block state upkeep, client sync of GUI values (and of tank levels for models that show them), and save/load.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    // Sync slots shared by every machine menu. Ints are sent as shorts by vanilla, so 32-bit values use two.
    public static final int DATA_ENERGY_LO = 0, DATA_ENERGY_HI = 1, DATA_CAPACITY_LO = 2, DATA_CAPACITY_HI = 3,
            DATA_REDSTONE = 4, DATA_STATUS = 5, DATA_SIDE_ITEMS = 6, DATA_SIDE_FLAGS = 7,
            DATA_TANKS = 8, DATA_SIDE2_ITEMS = 16, DATA_SIDE2_FLAGS = 17, DATA_BASE_COUNT = 18;
    /** Tanks a menu can show (two sync slots each: amount, fluid id). Amounts stay below 32768 mB to fit a short. */
    public static final int MAX_TANKS = 4;
    private static final int EJECT_INTERVAL = 8, EJECT_BATCH = 16, FLUID_EJECT_BATCH = 2000;
    /** Ticks a machine stays shown as working after it stops (see {@link #serverTick}). */
    public static final int ACTIVE_LINGER = 10;
    private int idleTicks = ACTIVE_LINGER;

    protected final MachineEnergy energy;
    protected final MachineInventory inventory;
    /** Fluid tanks in GUI order (empty for machines without any). */
    protected final List<MachineTank> tanks;
    private final MachineFluidHandler fluidHandler;
    /** Automation views by item mode (NONE has none). */
    private final AutomationView[] automation = new AutomationView[SideConfig.ItemMode.values().length];
    private final int[] inputSlots;
    private final int[] outputSlots;
    protected final SideConfig sides = new SideConfig();
    /**
     * The second half of a two-block machine has its own faces (DESIGN.md §10): a wide machine's side half, a tall
     * machine's upper half. This is their configuration.
     */
    protected final SideConfig sideHalf = new SideConfig();
    protected RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    /** Why the machine is (not) running; shown in the GUI. */
    protected MachineStatus status = MachineStatus.IDLE;
    /** Knocked out by an EMP field (it lost half its energy as the field hit; it reboots when the field ends). */
    private boolean emped;

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
                case DATA_SIDE2_ITEMS -> sideHalf.packItems();
                case DATA_SIDE2_FLAGS -> sideHalf.packFlags();
                default -> {
                    if (index >= DATA_TANKS && index < DATA_TANKS + MAX_TANKS * 2) {
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
        this(type, pos, state, energyCapacity, maxInsert, maxExtract, slotCount, inputSlots, outputSlots, List.of());
    }

    /** With tanks (filled and drained by pipes; buckets only as a click shortcut, see {@link MachineFluidInteraction}). */
    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxInsert,
            int maxExtract, int slotCount, int[] inputSlots, int[] outputSlots, List<MachineTank.Spec> tankSpecs) {
        super(type, pos, state);
        if (tankSpecs.size() > MAX_TANKS) {
            throw new IllegalArgumentException("at most " + MAX_TANKS + " tanks");
        }
        this.energy = new MachineEnergy(energyCapacity, maxInsert, maxExtract, this::setChanged);
        this.inventory = new MachineInventory(slotCount, this::isItemValid, this::onSlotChanged, this::slotLimit);
        List<MachineTank> list = new ArrayList<>();
        for (int i = 0; i < tankSpecs.size(); i++) {
            int index = i;
            list.add(new MachineTank(tankSpecs.get(i), r -> acceptsFluid(index, r), this::onTankChanged));
        }
        this.tanks = List.copyOf(list);
        this.fluidHandler = new MachineFluidHandler(this.tanks);
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.sides.restrict(allowedItemModes());
        this.sideHalf.restrict(allowedItemModes());
        for (SideConfig.ItemMode mode : SideConfig.ItemMode.values()) {
            if (mode != SideConfig.ItemMode.NONE) {
                this.automation[mode.ordinal()] = new AutomationView(this.inventory, inputSlots, outputSlots, mode.canInsert(), mode.canExtract(),
                        this::slotAutomated);
            }
        }
    }

    // --- subclass contract ----------------------------------------------------------------------------------------
    /** Whether pipes, hoppers and auto-eject may use this slot now (the Assembly Bench: only its current page's). */
    protected boolean slotAutomated(int slot) {
        return true;
    }

    /**
     * Whether the screen shows this slot and shift-clicks may land in it ({@code menu}'s synced data, on either side): a
     * machine with pages (the Assembly Bench) shows only the page it is on.
     */
    public boolean slotShown(int slot, MachineMenu menu) {
        return true;
    }

    /** Whether shift-clicks may fill this slot while the screen hides it (the reactor's other core layers). */
    public boolean slotTakesHidden(int slot) {
        return false;
    }

    /**
     * Whether the screen holds this slot's item fast ({@code menu}'s synced data, on either side): nothing taken out or
     * put in, a launcher's missiles while it counts down and fires.
     */
    public boolean slotLocked(int slot, MachineMenu menu) {
        return false;
    }

    /** One server tick of work. Returns whether the machine is actively working (drives ACTIVE, sound, animation). */
    protected abstract boolean tickMachine(ServerLevel level);

    protected abstract boolean isItemValid(int slot, ItemStack stack);

    /** Most items slot {@code slot} holds, whatever the item (the reactor's fuel channels: one rod each). */
    protected int slotLimit(int slot) {
        return net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE;
    }

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

    /** A machine control button from the GUI (layout controls of kind "button"); returns whether it did anything. */
    public boolean handleButton(int button) {
        return false;
    }

    /** Lines for the GUI's CRT readout (layouts that have one), built from the menu's synced values. */
    public List<Component> readout(MachineMenu menu) {
        return List.of();
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
            if (!this.emped) {
                // The pulse: half its stored energy gone in a shower of sparks.
                this.emped = true;
                this.energy.consume(this.energy.stored() / 2);
                setChanged();
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.3);
            } else if (level.getRandom().nextInt(30) == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.2);
            }
            onEmp(level);
        } else if (redstoneHalted()) {
            status = MachineStatus.REDSTONE;
            active = false;
            onRedstoneHalt(level);
        } else {
            if (this.emped) {
                this.emped = false;                            // the field has passed: it reboots
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.6f);
            }
            active = tickMachine(level);
        }
        if (this.sides.autoEject() && (level.getGameTime() + worldPosition.hashCode()) % EJECT_INTERVAL == 0) {
            if (this.outputSlots.length > 0) {
                autoEject(level);
            }
            pushFluids(level);
        }
        // ACTIVE drives the model, light and sound. It turns on at once but only off after a short idle spell, so a machine
        // that stutters (short of power, a slow feed) does not rewrite its block state every few ticks: each change makes
        // clients rebuild the chunk's mesh. Neighbours are not told (their connections do not depend on it).
        this.idleTicks = active ? 0 : this.idleTicks + 1;
        boolean shown = active || this.idleTicks < ACTIVE_LINGER;
        BlockState state = getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != shown) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, shown), Block.UPDATE_CLIENTS);
        }
    }

    /** Whether an EMP field is over it (DESIGN.md §21.4): then it does nothing at all. */
    protected boolean isEmpDisabled() {
        return this.level != null && dev.groundzero.content.effect.EmpFields.inField(this.level, this.worldPosition);
    }

    /** Whether its redstone setting holds it stopped right now. */
    protected boolean redstoneHalted() {
        return this.level != null && !this.redstoneMode.allows(this.level.hasNeighborSignal(this.worldPosition));
    }

    /** Each tick redstone holds it stopped (instead of working). */
    protected void onRedstoneHalt(ServerLevel level) {}

    /** Whether it is sitting dead in an EMP field. */
    public boolean emped() {
        return this.emped;
    }

    /** Each tick it sits in an EMP field (instead of working). */
    protected void onEmp(ServerLevel level) {}

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

    /** The grey hint line under a tank's tooltip in the GUI. */
    public String tankHint(int tank) {
        return this.tanks.get(tank).isInput() ? MachineText.TANK_INPUT : MachineText.TANK_OUTPUT;
    }

    /** Whether the screen shows the side-configuration tab (off for structures that connect only through ports). */
    public boolean hasSideConfig() {
        return true;
    }

    /** Tooltip over the GUI flame: by default the burn time left of a solid-fuel generator. */
    public Component flameTooltip(MachineMenu menu) {
        return Component.translatable(MachineText.BURN, menu.extra(0) / 20);
    }

    /** Pours output tanks into the fluid handlers beside every face whose fluid switch is on. */
    protected void pushFluids(ServerLevel level) {
        if (this.tanks.stream().allMatch(t -> t.isInput() || t.isEmpty())) {
            return;
        }
        pushFluids(level, false);
        if (twoBlock()) {
            pushFluids(level, true);
        }
    }

    private void pushFluids(ServerLevel level, boolean half) {
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        BlockPos from = half ? sideHalfPos() : this.worldPosition;
        for (SideConfig.Face face : SideConfig.Face.values()) {
            if (!sides(half).fluid(face) || !faceOpen(face, half) || !fluidFaceAllowed(face)) {
                continue;
            }
            Direction dir = face.toDirection(facing);
            BlockPos target = from.relative(dir);
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

    /** Whether fluids can pass through a face at all (a pump only has outlets on two sides). */
    public boolean fluidFaceAllowed(SideConfig.Face face) {
        return true;
    }

    public @Nullable EnergyHandler energyFor(@Nullable Direction side) {
        return energyFor(side, false);
    }

    /** {@code half}: asked through a wide machine's side half (its own faces). */
    public @Nullable EnergyHandler energyFor(@Nullable Direction side, boolean half) {
        if (side != null && !faceOpen(face(side), half)) {
            return null;
        }
        return side == null || sides(half).energy(face(side)) ? this.energy : null;
    }

    // --- two-block-wide machines ----------------------------------------------------------------------------------------
    /** Whether this is a two-block-wide machine (its side half has faces of its own). */
    public boolean wide() {
        return getBlockState().getBlock() instanceof WideMachineBlock;
    }

    /** Whether this is a two-block-tall machine (its upper half has faces of its own). */
    public boolean tall() {
        return getBlockState().getBlock() instanceof TallMachineBlock;
    }

    /** Whether this machine is two blocks (wide or tall): each half has its own faces. */
    public boolean twoBlock() {
        return wide() || tall();
    }

    /** The face configuration of the main half, or of the second half (a wide machine's side half, a tall one's top half). */
    public SideConfig sides(boolean half) {
        return half ? this.sideHalf : this.sides;
    }

    /** Where the second half is: a wide machine's side half, a tall machine's upper half. */
    public BlockPos sideHalfPos() {
        if (tall()) {
            return this.worldPosition.above();
        }
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        return this.worldPosition.relative(WideMachineBlock.sideward(facing));
    }

    /** A face of either half: the faces where a two-block machine's halves meet are inside it. */
    public boolean faceOpen(SideConfig.Face face, boolean half) {
        if (wide() && face == (half ? SideConfig.Face.LEFT : SideConfig.Face.RIGHT)
                || tall() && face == (half ? SideConfig.Face.BOTTOM : SideConfig.Face.TOP)) {
            return false;
        }
        return faceOpen(face);
    }

    public @Nullable ResourceHandler<FluidResource> fluidsFor(@Nullable Direction side) {
        return fluidsFor(side, false);
    }

    public @Nullable ResourceHandler<FluidResource> fluidsFor(@Nullable Direction side, boolean half) {
        if (this.tanks.isEmpty() || side != null && (!faceOpen(face(side), half) || !fluidFaceAllowed(face(side)))) {
            return null;
        }
        return side == null || sides(half).fluid(face(side)) ? this.fluidHandler : null;
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
        return itemsFor(side, false);
    }

    public @Nullable ResourceHandler<ItemResource> itemsFor(@Nullable Direction side, boolean half) {
        if (side == null) {
            return this.inventory;
        }
        return faceOpen(face(side), half) ? this.automation[sides(half).items(face(side)).ordinal()] : null;
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
        changeSides(false, change);
    }

    /** As {@link #changeSides(java.util.function.Consumer)}, for the main half's faces or a wide machine's side half's. */
    public void changeSides(boolean half, java.util.function.Consumer<SideConfig> change) {
        change.accept(sides(half));
        setChanged();
        if (this.level != null && twoBlock()) {
            this.level.invalidateCapabilities(sideHalfPos());
            this.level.updateNeighborsAt(sideHalfPos(), getBlockState().getBlock());
        }
        if (this.level != null) {
            this.level.invalidateCapabilities(this.worldPosition);
            this.level.updateNeighborsAt(this.worldPosition, getBlockState().getBlock());
            if (getBlockState().getBlock() instanceof TallMachineBlock) {
                this.level.invalidateCapabilities(this.worldPosition.above());
                this.level.updateNeighborsAt(this.worldPosition.above(), getBlockState().getBlock());
            }
        }
    }

    /** Pushes output slots into the item handlers beside every output face (both halves of a wide machine). */
    private void autoEject(ServerLevel level) {
        autoEject(level, false);
        if (twoBlock()) {
            autoEject(level, true);
        }
    }

    private void autoEject(ServerLevel level, boolean half) {
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        AutomationView out = new AutomationView(this.inventory, new int[0], this.outputSlots, false, true, this::slotAutomated);
        BlockPos from = half ? sideHalfPos() : this.worldPosition;
        for (SideConfig.Face face : SideConfig.Face.values()) {
            if (!sides(half).items(face).canExtract() || !faceOpen(face, half)) {
                continue;
            }
            Direction dir = face.toDirection(facing);
            BlockPos target = from.relative(dir);
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

    /** Why the machine is (not) running, as of its last tick. */
    public MachineStatus status() {
        return status;
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
        this.sideHalf.save(output.child("sides_side_half"));
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
        this.sideHalf.load(input.childOrEmpty("sides_side_half"));
        this.sideHalf.restrict(allowedItemModes());
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
