package dev.groundburst.client.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.groundburst.content.recipe.GBRecipes;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Machine recipes as received from the server (see {@code GBRecipes#syncToClients}), for recipe viewers. Filled at
 * the highest priority so JEI, which reloads on the same event, already sees them.
 */
public final class ClientRecipes {
    private static final Map<ProcessingKind, List<RecipeHolder<ProcessingRecipe>>> RECIPES = new EnumMap<>(ProcessingKind.class);
    private static List<RecipeHolder<?>> crafting = List.of(), smelting = List.of();

    private ClientRecipes() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientRecipes::received);
        NeoForge.EVENT_BUS.addListener(ClientRecipes::loggedOut);
    }

    public static List<RecipeHolder<ProcessingRecipe>> get(ProcessingKind kind) {
        return Collections.unmodifiableList(RECIPES.getOrDefault(kind, List.of()));
    }

    /**
     * How every crafting-table (or, with {@code smelting}, furnace and blast furnace) recipe that makes {@code item}
     * displays, for the Field Manual.
     */
    public static List<net.minecraft.world.item.crafting.display.RecipeDisplay> crafting(net.minecraft.world.item.Item item, boolean smelting) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        var context = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
        List<net.minecraft.world.item.crafting.display.RecipeDisplay> out = new ArrayList<>();
        for (RecipeHolder<?> holder : smelting ? ClientRecipes.smelting : ClientRecipes.crafting) {
            for (var display : holder.value().display()) {
                if (display.result().resolveForFirstStack(context).is(item)) {
                    out.add(display);
                }
            }
        }
        return out;
    }

    private static void received(RecipesReceivedEvent event) {
        for (ProcessingKind kind : ProcessingKind.values()) {
            if (event.getRecipeTypes().contains(GBRecipes.type(kind))) {
                RECIPES.put(kind, new ArrayList<>(event.getRecipeMap().byType(GBRecipes.type(kind))));
            }
        }
        var types = event.getRecipeTypes();
        var map = event.getRecipeMap();
        if (types.contains(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            crafting = List.copyOf(map.byType(net.minecraft.world.item.crafting.RecipeType.CRAFTING));
        }
        List<RecipeHolder<?>> furnace = new ArrayList<>();
        if (types.contains(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
            furnace.addAll(map.byType(net.minecraft.world.item.crafting.RecipeType.SMELTING));
        }
        if (types.contains(net.minecraft.world.item.crafting.RecipeType.BLASTING)) {
            furnace.addAll(map.byType(net.minecraft.world.item.crafting.RecipeType.BLASTING));
        }
        smelting = List.copyOf(furnace);
    }

    private static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RECIPES.clear();
        crafting = List.of();
        smelting = List.of();
    }
}
