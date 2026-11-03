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
import net.minecraft.world.item.crafting.RecipeHolder;
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
        return recipes == null ? List.of() : List.copyOf(recipes.getAllRecipesFor(GBRecipes.type(kind)));
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
            holders.addAll(recipes.getAllRecipesFor(RecipeType.SMELTING));
            holders.addAll(recipes.getAllRecipesFor(RecipeType.BLASTING));
        } else {
            holders.addAll(recipes.getAllRecipesFor(RecipeType.CRAFTING));
        }
        List<RecipeDisplay> out = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder.value().getResultItem(level.registryAccess()).is(item)) {
                Displays.of(holder.value(), level.registryAccess()).ifPresent(out::add);
            }
        }
        return out;
    }
}
