package dev.groundburst.content.recipe;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A machine's input slots as a recipe input, plus its catalyst slot and the contents of its input tanks (in tank
 * order; empty list when the machine has none).
 */
public record MachineRecipeInput(List<ItemStack> stacks, ItemStack catalyst, List<FluidStack> fluids) implements RecipeInput {
    public MachineRecipeInput(List<ItemStack> stacks) {
        this(stacks, ItemStack.EMPTY, List.of());
    }

    public MachineRecipeInput(List<ItemStack> stacks, ItemStack catalyst) {
        this(stacks, catalyst, List.of());
    }

    @Override
    public ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public int size() {
        return this.stacks.size();
    }

    /** Nothing at all to work with: no items, no catalyst, no fluid (some recipes need only a catalyst or a fluid). */
    @Override
    public boolean isEmpty() {
        return this.stacks.stream().allMatch(ItemStack::isEmpty) && this.catalyst.isEmpty() && this.fluids.stream().allMatch(FluidStack::isEmpty);
    }
}
