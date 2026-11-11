package dev.groundburst.legacy.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Minecraft 1.21's recipe lookups, over 1.20.1's recipe manager (whose recipes carry their own ids, and which hands back
 * recipes rather than holders): the port writes {@code manager.getAllRecipesFor(type)} as {@code Recipes.all(manager,
 * type)}. The mod's recipes are records made by their codecs: their ids are kept here as the serializer reads them
 * ({@link #serializer}).
 */
public final class Recipes {
    private Recipes() {}

    /** By the recipe object itself (records equal in content are still two recipes). */
    private static final Map<Object, ResourceLocation> IDS = new com.google.common.collect.MapMaker().weakKeys().makeMap();

    /** A recipe's id (a record of the mod's: as its serializer read it). */
    public static ResourceLocation idOf(Object recipe) {
        ResourceLocation id = IDS.get(recipe);
        return id != null ? id : new ResourceLocation("groundburst", "unnamed");
    }

    public static <T> T named(ResourceLocation id, T recipe) {
        IDS.put(recipe, id);
        return recipe;
    }

    public static <C extends Container, T extends Recipe<C>> List<RecipeHolder<T>> all(RecipeManager manager, RecipeType<T> type) {
        List<RecipeHolder<T>> out = new ArrayList<>();
        for (T recipe : manager.getAllRecipesFor(type)) {
            out.add(new RecipeHolder<>(recipe.getId(), recipe));
        }
        return out;
    }

    public static <C extends Container, T extends Recipe<C>> Optional<RecipeHolder<T>> find(RecipeManager manager, RecipeType<T> type, C input,
            Level level) {
        return manager.getRecipeFor(type, input, level).map(r -> new RecipeHolder<>(r.getId(), r));
    }

    /** Every recipe, with its id (1.21's RecipeManager.getRecipes). */
    public static java.util.Collection<RecipeHolder<?>> everything(RecipeManager manager) {
        List<RecipeHolder<?>> out = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            out.add(new RecipeHolder<>(recipe.getId(), recipe));
        }
        return out;
    }

    public static Optional<RecipeHolder<?>> byKey(RecipeManager manager, ResourceLocation id) {
        return manager.byKey(id).map(r -> new RecipeHolder<>(id, r));
    }

    /** A serializer of a record recipe of the mod's, by its 1.21 codecs: JSON through the map codec, packets through the stream codec. */
    public static <T extends Recipe<?>> RecipeSerializer<T> serializer(MapCodec<T> codec, StreamCodec<? super FriendlyByteBuf, T> stream) {
        return new RecipeSerializer<>() {
            @Override
            public T fromJson(ResourceLocation id, JsonObject json) {
                T recipe = codec.codec().parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> {
                    throw new IllegalStateException("Recipe " + id + ": " + error);
                });
                return named(id, recipe);
            }

            @Override
            public T fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
                return named(id, stream.decode(buf));
            }

            @Override
            public void toNetwork(FriendlyByteBuf buf, T recipe) {
                stream.encode(buf, recipe);
            }
        };
    }
}
