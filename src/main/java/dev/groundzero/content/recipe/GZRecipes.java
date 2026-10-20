package dev.groundzero.content.recipe;

import java.util.EnumMap;
import java.util.Map;

import dev.groundzero.GroundZero;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe types and serializers: one {@link ProcessingKind} each, all sharing {@link ProcessingRecipe}. */
public final class GZRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, GroundZero.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, GroundZero.MODID);
    public static final DeferredRegister<RecipeBookCategory> BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, GroundZero.MODID);

    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> MACHINE_CATEGORY =
            BOOK_CATEGORIES.register("machine", RecipeBookCategory::new);

    private static final Map<ProcessingKind, DeferredHolder<RecipeType<?>, RecipeType<ProcessingRecipe>>> TYPE_BY_KIND = new EnumMap<>(ProcessingKind.class);
    private static final Map<ProcessingKind, DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ProcessingRecipe>>> SERIALIZER_BY_KIND = new EnumMap<>(ProcessingKind.class);

    static {
        for (ProcessingKind kind : ProcessingKind.values()) {
            TYPE_BY_KIND.put(kind, TYPES.register(kind.id(), () -> RecipeType.simple(GroundZero.id(kind.id()))));
            SERIALIZER_BY_KIND.put(kind, SERIALIZERS.register(kind.id(),
                    () -> new RecipeSerializer<>(ProcessingRecipe.codec(kind), ProcessingRecipe.streamCodec(kind))));
        }
    }

    private GZRecipes() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        BOOK_CATEGORIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(GZRecipes::syncToClients);
    }

    /**
     * Since 1.21.2 the server keeps recipes to itself. Machine recipes are sent to every client so recipe viewers
     * (JEI) can show them, and crafting and furnace recipes for the Field Manual; the client keeps them in
     * {@code ClientRecipes}.
     */
    private static void syncToClients(OnDatapackSyncEvent event) {
        for (ProcessingKind kind : ProcessingKind.values()) {
            event.sendRecipes(type(kind));
        }
        // The Field Manual shows crafting and furnace recipes too.
        event.sendRecipes(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.RecipeType.SMELTING,
                net.minecraft.world.item.crafting.RecipeType.BLASTING);
    }

    public static RecipeType<ProcessingRecipe> type(ProcessingKind kind) {
        return TYPE_BY_KIND.get(kind).get();
    }

    public static RecipeSerializer<ProcessingRecipe> serializer(ProcessingKind kind) {
        return SERIALIZER_BY_KIND.get(kind).get();
    }
}
