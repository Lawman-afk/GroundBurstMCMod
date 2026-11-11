package dev.groundburst.legacy.recipe;

import net.minecraft.world.item.ItemStack;

/** Minecraft 1.21's one-stack recipe input (a furnace's, a stonecutter's). */
public record SingleRecipeInput(ItemStack item) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("No item for index " + index);
        }
        return this.item;
    }

    @Override
    public int size() {
        return 1;
    }
}
