package dev.groundburst.port.recipe;

/** A furnace (or blast furnace) recipe as shown: what goes in, what comes out, and how many ticks it takes. */
public record FurnaceRecipeDisplay(SlotDisplay ingredient, SlotDisplay result, int duration) implements RecipeDisplay {
}
