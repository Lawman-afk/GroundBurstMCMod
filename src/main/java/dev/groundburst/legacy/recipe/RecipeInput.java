package dev.groundburst.legacy.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft 1.21's recipe input: the stacks a recipe is tried against. On 1.20.1 a recipe takes a Container, so this is
 * one too (read only).
 */
public interface RecipeInput extends Container {
    ItemStack getItem(int index);

    int size();

    @Override
    default boolean isEmpty() {
        for (int i = 0; i < size(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    default int getContainerSize() {
        return size();
    }

    @Override
    default ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
    }

    @Override
    default void setChanged() {
    }

    @Override
    default boolean stillValid(Player player) {
        return true;
    }

    @Override
    default void clearContent() {
    }
}
