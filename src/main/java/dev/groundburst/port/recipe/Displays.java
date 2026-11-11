package dev.groundburst.port.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** How a 1.21.1 crafting or furnace recipe shows, as 26.x's recipe displays. */
public final class Displays {
    private Displays() {}

    public static Optional<RecipeDisplay> of(Recipe<?> recipe, HolderLookup.Provider registries) {
        SlotDisplay result = SlotDisplay.of(recipe.getResultItem(dev.groundburst.legacy.Regs.access()));
        if (((Object) recipe) instanceof ShapedRecipe shaped) {
            return Optional.of(new ShapedCraftingRecipeDisplay(shaped.getWidth(), shaped.getHeight(), slots(shaped.getIngredients()), result));
        }
        if (((Object) recipe) instanceof ShapelessRecipe shapeless) {
            return Optional.of(new ShapelessCraftingRecipeDisplay(slots(shapeless.getIngredients()), result));
        }
        if (((Object) recipe) instanceof AbstractCookingRecipe cooking) {
            Ingredient in = cooking.getIngredients().isEmpty() ? Ingredient.EMPTY : dev.groundburst.legacy.J21.first(cooking.getIngredients());
            return Optional.of(new FurnaceRecipeDisplay(SlotDisplay.of(in), result, cooking.getCookingTime()));
        }
        return Optional.empty();
    }

    private static List<SlotDisplay> slots(List<Ingredient> ingredients) {
        List<SlotDisplay> out = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            out.add(SlotDisplay.of(ingredient));
        }
        return out;
    }
}
