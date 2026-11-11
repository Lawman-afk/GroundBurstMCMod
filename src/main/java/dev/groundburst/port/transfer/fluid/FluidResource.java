package dev.groundburst.port.transfer.fluid;

import java.util.Objects;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.transfer.Resource;
import net.minecraft.core.Holder;
import dev.groundburst.legacy.component.DataComponentMap;
import dev.groundburst.legacy.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/** A fluid with its components, without an amount (a {@link FluidStack} without its amount). */
public final class FluidResource implements Resource {
    public static final FluidResource EMPTY = new FluidResource(Fluids.EMPTY.builtInRegistryHolder(), DataComponentPatch.EMPTY);

    private final Holder<Fluid> fluid;
    private final DataComponentPatch patch;

    private FluidResource(Holder<Fluid> fluid, DataComponentPatch patch) {
        this.fluid = fluid;
        this.patch = patch;
    }

    public static FluidResource of(FluidStack stack) {
        return stack.isEmpty() ? EMPTY : new FluidResource(dev.groundburst.legacy.FluidStacks.holder(stack), dev.groundburst.legacy.component.DataComponentPatch.of(stack));
    }

    @SuppressWarnings("deprecation")
    public static FluidResource of(Fluid fluid) {
        return fluid == Fluids.EMPTY ? EMPTY : new FluidResource(fluid.builtInRegistryHolder(), DataComponentPatch.EMPTY);
    }

    @SuppressWarnings("deprecation")
    public static FluidResource of(Fluid fluid, DataComponentPatch patch) {
        return fluid == Fluids.EMPTY ? EMPTY : new FluidResource(fluid.builtInRegistryHolder(), patch);
    }

    public static FluidResource of(Holder<Fluid> fluid) {
        return fluid.value() == Fluids.EMPTY ? EMPTY : new FluidResource(fluid, DataComponentPatch.EMPTY);
    }

    public Fluid value() {
        return this.fluid.value();
    }

    public Fluid getFluid() {
        return this.fluid.value();
    }

    public Holder<Fluid> typeHolder() {
        return this.fluid;
    }

    public FluidType getFluidType() {
        return getFluid().getFluidType();
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY || this.fluid.value() == Fluids.EMPTY;
    }

    public boolean is(FluidType fluidType) {
        return getFluidType() == fluidType;
    }

    public boolean is(Fluid fluid) {
        return getFluid() == fluid;
    }

    public boolean matches(FluidStack stack) {
        return isEmpty() ? stack.isEmpty() : !stack.isEmpty() && stack.getFluid() == getFluid() && dev.groundburst.legacy.component.DataComponentPatch.of(stack).equals(this.patch);
    }

    public boolean test(Predicate<FluidStack> predicate) {
        return predicate.test(toStack(1));
    }

    public boolean isComponentsPatchEmpty() {
        return this.patch.isEmpty();
    }

    public DataComponentMap getComponents() {
        return dev.groundburst.legacy.component.DataComponentMap.of(toStack(1));
    }

    public DataComponentPatch getComponentsPatch() {
        return this.patch;
    }

    public FluidStack toStack(int amount) {
        return isEmpty() || amount <= 0 ? FluidStack.EMPTY : dev.groundburst.legacy.component.DataComponentPatch.fluid(this.fluid, amount, this.patch);
    }

    public Component getHoverName() {
        return toStack(1).getDisplayName();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return ((Object) obj) instanceof FluidResource r && r.getFluid() == getFluid() && r.patch.equals(this.patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFluid(), this.patch);
    }

    @Override
    public String toString() {
        return "FluidResource[" + dev.groundburst.legacy.Game.registeredName(this.fluid) + (this.patch.isEmpty() ? "" : " " + this.patch) + "]";
    }
}
