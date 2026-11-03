package dev.groundburst.port;

import java.util.Arrays;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What a JEI slot shows, as JEI 19 takes it (a list of stacks): 26.x's JEI slots add an item, a stack, an ingredient,
 * a recipe's product or a fluid and its amount with one method.
 */
public final class Jei {
    private Jei() {}

    public static List<?> of(ItemStack stack) {
        return List.of(stack);
    }

    public static List<?> of(ItemLike item) {
        return List.of(new ItemStack(item));
    }

    public static List<?> of(Ingredient ingredient) {
        return Arrays.asList(ingredient.getItems());
    }

    public static List<?> of(ItemStackTemplate template) {
        return List.of(template.create());
    }

    public static List<?> of(List<ItemStack> stacks) {
        return stacks;
    }

    public static List<?> of(Fluid fluid, int amount) {
        return List.of(new FluidStack(fluid, amount));
    }

    public static List<?> of(Fluid fluid, long amount) {
        return List.of(new FluidStack(fluid, (int) amount));
    }
}
