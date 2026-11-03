package dev.groundburst.port;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Minecraft 26.x's ingredient contents (the items or fluids an ingredient takes), from 1.21.1's stacks. */
public final class Ingredients {
    private Ingredients() {}

    public static Stream<Holder<Item>> items(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).map(ItemStack::getItemHolder).distinct();
    }

    public static List<Holder<Fluid>> fluids(FluidIngredient ingredient) {
        return Arrays.stream(ingredient.getStacks()).map(FluidStack::getFluidHolder).distinct().toList();
    }
}
