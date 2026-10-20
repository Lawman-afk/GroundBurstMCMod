package dev.groundzero.content.multiblock;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.registry.GZBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A structure's port: once its structure forms it remembers the controller and hands automation one channel of it.
 * Item Port: the inventory (inputs in, products out). Fluid Port: the tanks (fill input tanks, drain product tanks).
 * Energy Port: the energy buffer.
 */
public class PortBlockEntity extends BlockEntity {
    private @Nullable BlockPos controller;

    public PortBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.ITEM_PORT.get(), pos, state);
    }

    public void setController(@Nullable BlockPos controller) {
        if (!java.util.Objects.equals(this.controller, controller)) {
            this.controller = controller;
            setChanged();
            if (this.level != null) {
                this.level.invalidateCapabilities(this.worldPosition);
            }
        }
    }

    private @Nullable MachineBlockEntity machine() {
        if (this.controller == null || this.level == null) {
            return null;
        }
        return this.level.getBlockEntity(this.controller) instanceof MachineBlockEntity m ? m : null;
    }

    private boolean isEnergyPort() {
        return getBlockState().getBlock() instanceof EnergyPortBlock;
    }

    /** The structure this port belongs to, once formed (null otherwise). */
    public @Nullable BlockPos controller() {
        return this.controller;
    }

    private boolean isFluidPort() {
        return getBlockState().getBlock() instanceof FluidPortBlock;
    }

    private boolean isItemPort() {
        return !isEnergyPort() && !isFluidPort();
    }

    public @Nullable ResourceHandler<ItemResource> items(@Nullable Direction side) {
        MachineBlockEntity m = machine();
        return m == null || !isItemPort() ? null : m.portItems();
    }

    public @Nullable ResourceHandler<FluidResource> fluids(@Nullable Direction side) {
        MachineBlockEntity m = machine();
        return m == null || !isFluidPort() || m.tanks().isEmpty() ? null : m.fluidHandler();
    }

    public net.neoforged.neoforge.transfer.energy.@Nullable EnergyHandler energy(@Nullable Direction side) {
        MachineBlockEntity m = machine();
        return m == null || !isEnergyPort() ? null : m.energyFor(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.controller != null) {
            output.putLong("controller", this.controller.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.controller = input.getLong("controller").map(BlockPos::of).orElse(null);
    }
}
