package dev.groundburst.port.recipe;

import java.util.List;

/** A shaped crafting recipe as shown: its grid, row by row, and its result. */
public record ShapedCraftingRecipeDisplay(int width, int height, List<SlotDisplay> ingredients, SlotDisplay result) implements RecipeDisplay {
}
