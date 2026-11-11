package dev.groundburst.port.transfer.fluid;

import dev.groundburst.port.transfer.StacksResourceHandler;
import net.minecraft.core.NonNullList;
import net.minecraftforge.fluids.FluidStack;

/** Fluid stacks in tanks of one capacity each. */
public class FluidStacksResourceHandler extends StacksResourceHandler<FluidStack, FluidResource> {
    protected int capacity;

    public FluidStacksResourceHandler(int size, int capacity) {
        super(size, FluidStack.EMPTY, dev.groundburst.legacy.net.Codecs.OPTIONAL_FLUID_STACK_CODEC);
        this.capacity = capacity;
    }

    public FluidStacksResourceHandler(NonNullList<FluidStack> stacks, int capacity) {
        super(stacks, FluidStack.EMPTY, dev.groundburst.legacy.net.Codecs.OPTIONAL_FLUID_STACK_CODEC);
        this.capacity = capacity;
    }

    @Override
    public FluidResource getResourceFrom(FluidStack stack) {
        return FluidResource.of(stack);
    }

    @Override
    public int getAmountFrom(FluidStack stack) {
        return stack.getAmount();
    }

    @Override
    protected FluidStack getStackFrom(FluidResource resource, int amount) {
        return resource.toStack(amount);
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return this.capacity;
    }

    @Override
    protected FluidStack copyOf(FluidStack stack) {
        return stack.copy();
    }

    @Override
    public boolean matches(FluidStack stack, FluidResource resource) {
        return resource.matches(stack);
    }
}
