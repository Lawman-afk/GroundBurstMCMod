package dev.groundzero.content.machine;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.fluid.ChemFluid;
import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.power.PowerUtil;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * Fusion Reactor (DESIGN.md §7, §23.3): a 9x9x3 octagonal ring run from this controller. A game abstraction, not a
 * reactor design: fuel in, an ignition charge, cold magnets, power out.
 *
 * <ul>
 * <li>Fuel: deuterium and tritium gas, 1 mB of each every 4 ticks. (Lithium deuteride pellets are kept for the
 *     thermonuclear weapons, so the reactor does not burn them.)</li>
 * <li>Ignition: while the plasma is out, the Energy Port takes power into a separate ignition charge; at 2 000 000 FE,
 *     with fuel and liquid nitrogen in, the plasma lights and the charge is spent.</li>
 * <li>Lit: 6 000 FE a tick into the output buffer, pushed out of the Energy Port; liquid nitrogen keeps the magnets
 *     cold at 2 mB a tick. With the buffer full it idles (no fuel used). Out of fuel or nitrogen, unformed or halted by
 *     redstone, the plasma goes out and needs a fresh ignition charge.</li>
 * </ul>
 * No item slots. Tanks: 0 deuterium, 1 tritium, 2 liquid nitrogen.
 */
public class FusionReactorBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int OUTPUT = 6_000;
    public static final int CAPACITY = 2_000_000;
    public static final int PUSH = 24_000;
    public static final int IGNITION_ENERGY = 2_000_000;
    public static final int IGNITION_INTAKE = 32_768;
    public static final int GAS_TICKS_PER_MB = 4;
    public static final int NITROGEN_PER_TICK = 2;
    public static final int TANK_DEUTERIUM = 0, TANK_TRITIUM = 1, TANK_NITROGEN = 2;
    /** Sync slots: ignition charge (%), output (FE/t), plasma lit (0/1). */
    public static final int DATA_IGNITION = 0, DATA_POWER = 1, DATA_LIT = 2;
    private static final int RECHECK_INTERVAL = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The ignition charge, fed through the Energy Port while the plasma is out. */
    private final MachineEnergy ignition = new MachineEnergy(IGNITION_ENERGY, IGNITION_INTAKE, 0, this::setChanged);
    private final ReactorEnergyView port = new ReactorEnergyView(this.energy, () -> this.lit ? null : this.ignition, () -> 0);
    private boolean lit;
    private int power;
    private float gasDebt;
    /** The last tick the reactor ran (-1: not yet since loading, so a reload never counts as a skipped tick). */
    private long lastTick = -1;

    public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.FUSION_REACTOR.get(), pos, state, CAPACITY, 0, PUSH, 0, new int[0], new int[0],
                List.of(gasTank(GZFluids.DEUTERIUM, 8_000), gasTank(GZFluids.TRITIUM, 8_000), gasTank(GZFluids.LIQUID_NITROGEN, 16_000)));
    }

    private static MachineTank.Spec gasTank(ChemFluid fluid, int capacity) {
        return new MachineTank.Spec(capacity, MachineTank.Role.INPUT, r -> r.getFluid().isSame(fluid.sourceFluid()), true);
    }

    // --- multiblock ---------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZMultiblocks.FUSION_REACTOR;
    }

    @Override
    public BlockPos controllerPos() {
        return this.worldPosition;
    }

    @Override
    public Direction controllerFacing() {
        return StructureMachines.facing(getBlockState());
    }

    @Override
    public boolean isFormed() {
        return StructureMachines.isFormed(getBlockState());
    }

    @Override
    public void revalidate() {
        StructureMachines.revalidate(this, this);
    }

    /** The controller connects to nothing: items, fluids and power all go through the structure's ports. */
    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;
    }

    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        StructureMachines.unformOnRemove(this, this, state);
    }

    /** The Energy Port: power out of the output buffer, power in to the ignition charge while the plasma is out. */
    @Override
    public @Nullable EnergyHandler energyFor(@Nullable Direction side) {
        return side == null ? this.port : null;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    // --- the plasma ----------------------------------------------------------------------------------------------------------
    public boolean lit() {
        return this.lit;
    }

    public MachineEnergy ignition() {
        return this.ignition;
    }

    private boolean hasGas() {
        return this.tanks.get(TANK_DEUTERIUM).amount() >= 1 && this.tanks.get(TANK_TRITIUM).amount() >= 1;
    }


    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        long now = level.getGameTime();
        if (this.lit && this.lastTick >= 0 && now - this.lastTick > 1) {
            setLit(false);                              // a tick was skipped (redstone halt): the plasma did not survive it
        }
        this.lastTick = now;
        this.power = 0;
        if (!isFormed()) {
            setLit(false);
            this.status = MachineStatus.INCOMPLETE;
            return false;
        }
        boolean nitrogen = this.tanks.get(TANK_NITROGEN).amount() >= NITROGEN_PER_TICK;
        boolean fuel = hasGas();
        if (!this.lit) {
            if (!fuel) {
                this.status = MachineStatus.NO_FUEL;
            } else if (!nitrogen) {
                this.status = MachineStatus.NEED_FLUID;
            } else if (this.ignition.stored() < IGNITION_ENERGY) {
                this.status = MachineStatus.CHARGING;
            } else {
                this.ignition.consume(IGNITION_ENERGY);
                setLit(true);
            }
            if (!this.lit) {
                return false;
            }
        }
        if (!fuel || !nitrogen) {
            setLit(false);
            announce(level);
            this.status = !fuel ? MachineStatus.NO_FUEL : MachineStatus.NEED_FLUID;
            return false;
        }
        // The magnets need cooling whether or not the power is wanted.
        this.tanks.get(TANK_NITROGEN).drainInternal(NITROGEN_PER_TICK);
        float lvl = Math.min(1f, (this.energy.capacity() - this.energy.stored()) / (float) OUTPUT);
        if (lvl > 0) {
            burnFuel(lvl);
            this.power = Math.round(OUTPUT * lvl);
            this.energy.generate(this.power);
        }
        pushPower(level);
        this.status = lvl < 1 ? MachineStatus.BUFFER_FULL : MachineStatus.RUNNING;
        setChanged();
        return true;
    }

    /** Burns {@code lvl} ticks' worth of deuterium and tritium. */
    private void burnFuel(float lvl) {
        this.gasDebt += lvl / GAS_TICKS_PER_MB;
        if (this.gasDebt >= 1) {
            this.gasDebt -= 1;
            this.tanks.get(TANK_DEUTERIUM).drainInternal(1);
            this.tanks.get(TANK_TRITIUM).drainInternal(1);
        }
    }

    private void setLit(boolean lit) {
        if (this.lit != lit) {
            this.lit = lit;
            setChanged();
        }
    }

    private void announce(ServerLevel level) {
        for (Player player : level.players()) {
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.worldPosition)) < 48 * 48) {
                player.sendOverlayMessage(Component.translatable(MachineText.PLASMA_LOST).withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
    }

    /** Pushes power out of the Energy Port. */
    private void pushPower(ServerLevel level) {
        for (BlockPos port : definition().positionsOf('E', this.worldPosition, controllerFacing())) {
            PowerUtil.pushToNeighbours(level, port, this.energy, PUSH);
        }
    }

    // --- GUI -----------------------------------------------------------------------------------------------------------------
    @Override
    public List<Component> readout(MachineMenu menu) {
        return List.of(Component.translatable(MachineText.OUTPUT, menu.extra(DATA_POWER)),
                menu.extra(DATA_LIT) != 0 ? Component.translatable(MachineText.PLASMA_LIT)
                        : Component.translatable(MachineText.IGNITION, menu.extra(DATA_IGNITION)));
    }

    @Override
    protected int extraDataCount() {
        return 3;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_IGNITION -> this.lit ? 100 : (int) ((long) this.ignition.stored() * 100 / IGNITION_ENERGY);
            case DATA_POWER -> this.power;
            case DATA_LIT -> this.lit ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.FUSION_REACTOR.get(), containerId, inventory, this, MachineLayouts.FUSION_REACTOR, this.data);
    }

    // --- rendering -----------------------------------------------------------------------------------------------------------
    /** Unformed, the controller is an ordinary block; the plasma only glows while the reactor runs. */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? Set.of() : Set.of("plasma", "streak");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_FUSION_REACTOR.get();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.ignition.serialize(output.child("ignition"));
        output.putBoolean("lit", this.lit);
        output.putFloat("gas_debt", this.gasDebt);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ignition.deserialize(input.childOrEmpty("ignition"));
        this.lit = input.getBooleanOr("lit", false);
        this.gasDebt = input.getFloatOr("gas_debt", 0);
    }
}
