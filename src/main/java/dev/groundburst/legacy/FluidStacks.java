package dev.groundburst.legacy;

import net.minecraft.core.Holder;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** NeoForge's fluid stack calls that Forge 1.20.1's has none of. */
public final class FluidStacks {
    private FluidStacks() {}

    /** A stack's fluid as a holder (NeoForge's {@code FluidStack.getFluidHolder()}). */
    @SuppressWarnings("deprecation")
    public static Holder<Fluid> holder(FluidStack stack) {
        return stack.getFluid().builtInRegistryHolder();
    }
}
