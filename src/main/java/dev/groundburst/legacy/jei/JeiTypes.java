package dev.groundburst.legacy.jei;

import dev.groundburst.legacy.recipe.RecipeHolder;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/**
 * JEI 19's recipe types made from the game's (JEI 15 on 1.20.1 has no such factory: its recipe types are an id and a
 * class). A JEI recipe type of the mod's recipes holds them as the 1.21.1 build does, with their ids
 * ({@link RecipeHolder}). Only the JEI plugin calls this, so the class loads only with JEI.
 */
public final class JeiTypes {
    private JeiTypes() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <R extends Recipe<?>> RecipeType<RecipeHolder<R>> fromVanilla(net.minecraft.world.item.crafting.RecipeType<R> type) {
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (id == null) {
            throw new IllegalArgumentException("A recipe type not registered: " + type);
        }
        Class<RecipeHolder<R>> holders = (Class) RecipeHolder.class;
        return new RecipeType<>(id, holders);
    }
}
