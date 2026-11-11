package dev.groundburst.legacy.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/** Minecraft 1.21's recipe with its id (1.20.1's recipes carry their own id). */
public record RecipeHolder<T extends Recipe<?>>(ResourceLocation id, T value) {
    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof RecipeHolder<?> h && h.id.equals(this.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return this.id.toString();
    }
}
