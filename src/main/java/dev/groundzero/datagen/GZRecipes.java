package dev.groundzero.datagen;

import dev.groundzero.GroundZero;
import dev.groundzero.content.resource.GZResources;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ItemLike;

/**
 * Crafting/cooking recipes (DESIGN.md §26). Vanilla's helpers name recipes in the minecraft namespace, so every
 * recipe here is saved explicitly under groundzero:.
 */
public class GZRecipes extends RecipeProvider {
    public GZRecipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
        super(recipes, advancements);
    }

    private static String id(String name) {
        return GroundZero.id(name).toString();
    }

    @Override
    protected void buildRecipes() {
        // Furnace-grade metals. Tungsten is too refractory for a furnace: Arc Furnace only (Phase 4).
        cook(GZResources.RAW_URANIUM, GZResources.URANIUM_INGOT, 0.7f);
        cook(GZResources.RAW_LEAD, GZResources.LEAD_INGOT, 0.5f);

        storage(GZResources.URANIUM_INGOT, GZResources.URANIUM_BLOCK.item());
        storage(GZResources.LEAD_INGOT, GZResources.LEAD_BLOCK.item());
        storage(GZResources.TUNGSTEN_INGOT, GZResources.TUNGSTEN_BLOCK.item());
        storage(GZResources.RAW_URANIUM, GZResources.RAW_URANIUM_BLOCK.item());
        storage(GZResources.RAW_LITHIUM, GZResources.RAW_LITHIUM_BLOCK.item());
        storage(GZResources.RAW_TUNGSTEN, GZResources.RAW_TUNGSTEN_BLOCK.item());
        storage(GZResources.RAW_LEAD, GZResources.RAW_LEAD_BLOCK.item());
        storage(GZResources.RAW_RARE_EARTH, GZResources.RAW_RARE_EARTH_BLOCK.item());

        // Black powder: a renewable gunpowder source from mined saltpeter and sulfur.
        shapeless(RecipeCategory.MISC, Items.GUNPOWDER, 4)
                .requires(GZResources.SALTPETER_DUST, 2)
                .requires(GZResources.SULFUR_DUST)
                .requires(Items.CHARCOAL)
                .unlockedBy(getHasName(GZResources.SALTPETER_DUST), has(GZResources.SALTPETER_DUST))
                .save(output, id("gunpowder_from_saltpeter"));
    }

    private void cook(ItemLike raw, ItemLike result, float xp) {
        SimpleCookingRecipeBuilder.generic(Ingredient.of(raw), RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 200, SmeltingRecipe::new)
                .unlockedBy(getHasName(raw), has(raw))
                .save(output, id(getItemName(result) + "_from_smelting_" + getItemName(raw)));
        SimpleCookingRecipeBuilder.generic(Ingredient.of(raw), RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 100, BlastingRecipe::new)
                .unlockedBy(getHasName(raw), has(raw))
                .save(output, id(getItemName(result) + "_from_blasting_" + getItemName(raw)));
    }

    private void storage(ItemLike unit, ItemLike block) {
        shaped(RecipeCategory.BUILDING_BLOCKS, block, 1)
                .define('#', unit)
                .pattern("###").pattern("###").pattern("###")
                .unlockedBy(getHasName(unit), has(unit))
                .save(output);   // default id: the result's own id (groundzero:<block>)
        shapeless(RecipeCategory.MISC, unit, 9)
                .requires(block)
                .unlockedBy(getHasName(block), has(block))
                .save(output, id(getItemName(unit) + "_from_" + getItemName(block)));
    }
}
