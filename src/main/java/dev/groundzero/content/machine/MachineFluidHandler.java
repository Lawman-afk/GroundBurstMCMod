package dev.groundzero.content.machine;

import java.util.List;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What the outside world (pipes, other mods, buckets clicked on the block, item ports) sees of a machine's tanks:
 * every tank in order, filling only input tanks and draining only output tanks. A fluid already held by one input
 * tank only ever goes into that tank, so two input tanks never end up holding the same fluid.
 */
public final class MachineFluidHandler implements ResourceHandler<FluidResource> {
    private final List<MachineTank> tanks;

    public MachineFluidHandler(List<MachineTank> tanks) {
        this.tanks = tanks;
    }

    @Override
    public int size() {
        return this.tanks.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return this.tanks.get(index).getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return this.tanks.get(index).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return this.tanks.get(index).getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        MachineTank tank = this.tanks.get(index);
        return tank.isInput() && tank.isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        MachineTank tank = this.tanks.get(index);
        if (!tank.isInput() || heldElsewhere(index, resource)) {
            return 0;
        }
        return tank.insert(0, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        MachineTank tank = this.tanks.get(index);
        return tank.isInput() ? 0 : tank.extract(0, resource, amount, transaction);
    }

    /** Whether another input tank already holds this fluid (then only that tank takes more of it). */
    private boolean heldElsewhere(int index, FluidResource resource) {
        for (int i = 0; i < this.tanks.size(); i++) {
            MachineTank other = this.tanks.get(i);
            if (i != index && other.isInput() && !other.isEmpty() && other.getResource(0).equals(resource)) {
                return true;
            }
        }
        return false;
    }
}
