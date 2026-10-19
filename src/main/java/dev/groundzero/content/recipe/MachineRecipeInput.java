package dev.groundzero.content.recipe;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A machine's input slots as a recipe input, plus its catalyst slot and its input tank (empty when the machine has
 * none).
 */
public record MachineRecipeInput(List<ItemStack> stacks, ItemStack catalyst, FluidStack fluid) implements RecipeInput {
    public MachineRecipeInput(List<ItemStack> stacks) {
        this(stacks, ItemStack.EMPTY, FluidStack.EMPTY);
    }

    public MachineRecipeInput(List<ItemStack> stacks, ItemStack catalyst) {
        this(stacks, catalyst, FluidStack.EMPTY);
    }

    @Override
    public ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public int size() {
        return this.stacks.size();
    }
}
