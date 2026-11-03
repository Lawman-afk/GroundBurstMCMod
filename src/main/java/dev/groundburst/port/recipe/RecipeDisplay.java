package dev.groundburst.port.recipe;

/** Minecraft 26.x's recipe display (how a recipe shows: its slots and result), made from a 1.21.1 recipe. */
public interface RecipeDisplay {
    SlotDisplay result();
}
