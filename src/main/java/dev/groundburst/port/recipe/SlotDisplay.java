package dev.groundburst.port.recipe;

import java.util.Arrays;
import java.util.List;

import dev.groundburst.port.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Minecraft 26.x's slot display (what one slot of a shown recipe holds), made from 1.21.1's ingredients and stacks. */
public interface SlotDisplay {
    List<ItemStack> resolveForStacks(ContextMap context);

    default ItemStack resolveForFirstStack(ContextMap context) {
        List<ItemStack> stacks = resolveForStacks(context);
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst();
    }

    static SlotDisplay of(Ingredient ingredient) {
        return ingredient.isEmpty() ? Empty.INSTANCE : new Stacks(Arrays.asList(ingredient.getItems()));
    }

    static SlotDisplay of(ItemStack stack) {
        return stack.isEmpty() ? Empty.INSTANCE : new Stacks(List.of(stack));
    }

    final class Empty implements SlotDisplay {
        public static final Empty INSTANCE = new Empty();

        private Empty() {}

        @Override
        public List<ItemStack> resolveForStacks(ContextMap context) {
            return List.of();
        }
    }

    record Stacks(List<ItemStack> stacks) implements SlotDisplay {
        @Override
        public List<ItemStack> resolveForStacks(ContextMap context) {
            return this.stacks;
        }
    }
}
