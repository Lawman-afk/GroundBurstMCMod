package dev.groundzero.client.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.groundzero.content.recipe.GZRecipes;
import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.content.recipe.ProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Machine recipes as received from the server (see {@code GZRecipes#syncToClients}), for recipe viewers. Filled at
 * the highest priority so JEI, which reloads on the same event, already sees them.
 */
public final class ClientRecipes {
    private static final Map<ProcessingKind, List<RecipeHolder<ProcessingRecipe>>> RECIPES = new EnumMap<>(ProcessingKind.class);

    private ClientRecipes() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientRecipes::received);
        NeoForge.EVENT_BUS.addListener(ClientRecipes::loggedOut);
    }

    public static List<RecipeHolder<ProcessingRecipe>> get(ProcessingKind kind) {
        return Collections.unmodifiableList(RECIPES.getOrDefault(kind, List.of()));
    }

    private static void received(RecipesReceivedEvent event) {
        for (ProcessingKind kind : ProcessingKind.values()) {
            if (event.getRecipeTypes().contains(GZRecipes.type(kind))) {
                RECIPES.put(kind, new ArrayList<>(event.getRecipeMap().byType(GZRecipes.type(kind))));
            }
        }
    }

    private static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RECIPES.clear();
    }
}
