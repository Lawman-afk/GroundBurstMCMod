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
 * A structure's item port: once its structure forms it remembers the controller and hands pipes and hoppers the
 * controller's inventory (inputs in, products out) and its tank (fill only).
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

    public @Nullable ResourceHandler<ItemResource> items(@Nullable Direction side) {
        MachineBlockEntity m = machine();
        return m == null ? null : m.portItems();
    }

    public @Nullable ResourceHandler<FluidResource> fluids(@Nullable Direction side) {
        MachineBlockEntity m = machine();
        return m == null || m.tanks().isEmpty() ? null : m.fluidHandler();
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
