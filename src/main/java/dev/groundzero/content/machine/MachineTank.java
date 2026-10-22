package dev.groundzero.content.machine;

import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * One of a machine's fluid tanks. Input tanks feed recipes and may be filled from outside (pipes, buckets, canisters);
 * output tanks collect products and may be drained from outside. The machine itself moves fluid in and out with
 * {@link #drainInternal} and {@link #fillInternal}.
 */
public class MachineTank extends FluidStacksResourceHandler {
    public enum Role { INPUT, OUTPUT }

    /**
     * @param filter   which fluids the tank takes (the machine adds its own recipe check for input tanks)
     * @param dumpable whether the GUI offers a dump button (the water tank never does: it only ever holds water)
     */
    public record Spec(int capacity, Role role, Predicate<FluidResource> filter, boolean dumpable) {
        /** An input tank that only ever holds water. */
        public static Spec water(int capacity) {
            return new Spec(capacity, Role.INPUT, r -> r.getFluid().isSame(Fluids.WATER), false);
        }

        /** An input tank for whatever fluid a recipe needs (never water: machines that use water have a water tank). */
        public static Spec input(int capacity) {
            return new Spec(capacity, Role.INPUT, r -> !r.getFluid().isSame(Fluids.WATER), true);
        }

        /** A product tank. */
        public static Spec output(int capacity) {
            return new Spec(capacity, Role.OUTPUT, r -> true, true);
        }
    }

    private final Spec spec;
    private final Predicate<FluidResource> accepts;
    private final Runnable onChanged;

    /** {@code accepts}: the machine's extra check (e.g. "some recipe uses this fluid"), on top of the spec's filter. */
    public MachineTank(Spec spec, Predicate<FluidResource> accepts, Runnable onChanged) {
        super(1, spec.capacity());
        this.spec = spec;
        this.accepts = accepts;
        this.onChanged = onChanged;
    }

    public Spec spec() {
        return spec;
    }

    public boolean isInput() {
        return this.spec.role() == Role.INPUT;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return this.spec.filter().test(resource) && (this.spec.role() == Role.OUTPUT || this.accepts.test(resource));
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

    /** Resizes the tank (a structure that changed size); fluid above the new capacity is lost. */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        if (amount() > this.capacity) {
            set(0, FluidResource.of(this.stacks.get(0)), this.capacity);
        } else {
            this.onChanged.run();
        }
    }

    public boolean isEmpty() {
        return this.stacks.get(0).isEmpty();
    }

    /** Room left for this fluid (0 if the tank holds something else). */
    public int space(Fluid fluid) {
        FluidStack current = this.stacks.get(0);
        if (!current.isEmpty() && !current.getFluid().isSame(fluid)) {
            return 0;
        }
        return this.capacity - current.getAmount();
    }

    /** Removes fluid for a finished recipe (machine-internal, no transaction needed). */
    public void drainInternal(int amount) {
        FluidStack current = this.stacks.get(0);
        int left = Math.max(0, current.getAmount() - amount);
        set(0, left == 0 ? FluidResource.EMPTY : FluidResource.of(current), left);
    }

    /** Adds a recipe's fluid product (machine-internal; the caller checked {@link #space}). */
    public void fillInternal(FluidStack stack) {
        int total = Math.min(this.capacity, amount() + stack.getAmount());
        set(0, FluidResource.of(stack), total);
    }

    /** Empties the tank (the GUI's dump button). */
    public void dump() {
        set(0, FluidResource.EMPTY, 0);
    }

    /** Fluid registry id of the contents, for menu sync (0 = empty). */
    public int fluidId() {
        Fluid fluid = this.stacks.get(0).getFluid();
        return this.stacks.get(0).isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid);
    }
}
