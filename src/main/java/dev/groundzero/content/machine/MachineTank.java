package dev.groundzero.content.machine;

import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's single input fluid tank (e.g. water for the Electrolytic Separator). Pipes may fill it through faces
 * whose fluid switch is on; nothing may drain it from outside (it only feeds the machine's recipes).
 */
public class MachineTank extends FluidStacksResourceHandler {
    /** Tank size, which fluids it takes, and which item slot empties fluid containers (buckets) into it (-1: none). */
    public record Spec(int capacity, Predicate<FluidResource> filter, int containerSlot) {
        public static Spec water(int capacity, int containerSlot) {
            return new Spec(capacity, r -> r.getFluid().isSame(Fluids.WATER), containerSlot);
        }
    }

    private final Spec spec;
    private final Runnable onChanged;

    public MachineTank(Spec spec, Runnable onChanged) {
        super(1, spec.capacity());
        this.spec = spec;
        this.onChanged = onChanged;
    }

    public Spec spec() {
        return spec;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return this.spec.filter().test(resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        this.onChanged.run();
    }

    public FluidStack fluid() {
        return this.stacks.get(0);
    }

    public int amount() {
        return this.stacks.get(0).getAmount();
    }

    public int capacity() {
        return this.capacity;
    }

    /** Removes fluid for a finished recipe (machine-internal, no transaction needed). */
    public void drainInternal(int amount) {
        FluidStack current = this.stacks.get(0);
        int left = Math.max(0, current.getAmount() - amount);
        set(0, left == 0 ? FluidResource.EMPTY : FluidResource.of(current), left);
    }

    /** Fluid registry id of the contents, for menu sync (0 = empty). */
    public int fluidId() {
        Fluid fluid = this.stacks.get(0).getFluid();
        return this.stacks.get(0).isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid);
    }

    /** What pipes see: fill only. */
    public ResourceHandler<FluidResource> fillOnly() {
        MachineTank tank = this;
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public FluidResource getResource(int index) {
                return tank.getResource(0);
            }

            @Override
            public long getAmountAsLong(int index) {
                return tank.getAmountAsLong(0);
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return tank.getCapacityAsLong(0, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return tank.isValid(0, resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return tank.insert(0, resource, amount, transaction);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }
}
