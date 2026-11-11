package dev.groundburst.content.recipe;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import dev.groundburst.GroundBurst;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import dev.groundburst.legacy.DeferredHolder;
import dev.groundburst.legacy.DeferredRegister;

/**
 * Recipe types and serializers: one {@link ProcessingKind} each, all sharing {@link ProcessingRecipe}. (1.21.1 sends
 * every recipe to the client itself, so nothing more is synced here.)
 */
public final class GBRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, GroundBurst.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, GroundBurst.MODID);

    private static final Map<ProcessingKind, DeferredHolder<RecipeType<?>, RecipeType<ProcessingRecipe>>> TYPE_BY_KIND = new EnumMap<>(ProcessingKind.class);
    private static final Map<ProcessingKind, DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ProcessingRecipe>>> SERIALIZER_BY_KIND = new EnumMap<>(ProcessingKind.class);

    static {
        for (ProcessingKind kind : ProcessingKind.values()) {
            TYPE_BY_KIND.put(kind, TYPES.register(kind.id(), () -> RecipeType.simple(GroundBurst.id(kind.id()))));
            SERIALIZER_BY_KIND.put(kind, SERIALIZERS.register(kind.id(),
                    () -> serializer(ProcessingRecipe.codec(kind), ProcessingRecipe.streamCodec(kind))));
        }
    }

    private GBRecipes() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }

    private static RecipeSerializer<ProcessingRecipe> serializer(MapCodec<ProcessingRecipe> codec,
            StreamCodec<FriendlyByteBuf, ProcessingRecipe> streamCodec) {
        return dev.groundburst.legacy.recipe.Recipes.serializer(codec, streamCodec);
    }

    public static RecipeType<ProcessingRecipe> type(ProcessingKind kind) {
        return TYPE_BY_KIND.get(kind).get();
    }

    public static RecipeSerializer<ProcessingRecipe> serializer(ProcessingKind kind) {
        return SERIALIZER_BY_KIND.get(kind).get();
    }
}
