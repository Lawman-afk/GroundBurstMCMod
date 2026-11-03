package dev.groundburst.port.recipe;

import java.util.List;

/** A shapeless crafting recipe as shown: its ingredients and its result. */
public record ShapelessCraftingRecipeDisplay(List<SlotDisplay> ingredients, SlotDisplay result) implements RecipeDisplay {
}
