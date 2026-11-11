package dev.groundburst.client.recipe;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.content.recipe.GBRecipes;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import dev.groundburst.port.recipe.Displays;
import dev.groundburst.port.recipe.RecipeDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import dev.groundburst.legacy.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Machine recipes on the client, for recipe viewers, and crafting and furnace recipes for the Field Manual. 1.21.1's
 * server sends the client every recipe, so they are read from the connection's recipe manager.
 */
public final class ClientRecipes {
    private ClientRecipes() {}

    public static void register() {}

    private static RecipeManager recipes() {
        var connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getRecipeManager();
    }

    public static List<RecipeHolder<ProcessingRecipe>> get(ProcessingKind kind) {
        RecipeManager recipes = recipes();
        return recipes == null ? List.of() : List.copyOf(dev.groundburst.legacy.recipe.Recipes.all(recipes, GBRecipes.type(kind)));
    }

    /**
     * How every crafting-table (or, with {@code smelting}, furnace and blast furnace) recipe that makes {@code item}
     * displays, for the Field Manual.
     */
    public static List<RecipeDisplay> crafting(Item item, boolean smelting) {
        var level = Minecraft.getInstance().level;
        RecipeManager recipes = recipes();
        if (level == null || recipes == null) {
            return List.of();
        }
        List<RecipeHolder<?>> holders = new ArrayList<>();
        if (smelting) {
            holders.addAll(dev.groundburst.legacy.recipe.Recipes.all(recipes, RecipeType.SMELTING));
            holders.addAll(dev.groundburst.legacy.recipe.Recipes.all(recipes, RecipeType.BLASTING));
        } else {
            holders.addAll(dev.groundburst.legacy.recipe.Recipes.all(recipes, RecipeType.CRAFTING));
        }
        List<RecipeDisplay> out = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder.value().getResultItem(dev.groundburst.legacy.Regs.access()).is(item)) {
                Displays.of(holder.value(), level.registryAccess()).ifPresent(out::add);
            }
        }
        return out;
    }
}
