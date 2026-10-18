package dev.groundzero.datagen;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.content.recipe.ProcessingRecipe;
import dev.groundzero.registry.GZTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
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
public class GZRecipeProvider extends RecipeProvider {
    public GZRecipeProvider(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
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

        // Dusts smelt back to ingots (tungsten dust waits for the Arc Furnace).
        cook(GZResources.URANIUM_DUST, GZResources.URANIUM_INGOT, 0.3f);
        cook(GZResources.LEAD_DUST, GZResources.LEAD_INGOT, 0.3f);
        cook(GZResources.IRON_DUST, Items.IRON_INGOT, 0.3f);
        cook(GZResources.GOLD_DUST, Items.GOLD_INGOT, 0.3f);
        cook(GZResources.COPPER_DUST, Items.COPPER_INGOT, 0.3f);

        bootstrap();
        crushing();
    }

    /** DESIGN.md §26.1: vanilla-only recipes that get the player into the power + machine loop. */
    private void bootstrap() {
        shaped(RecipeCategory.REDSTONE, GZMachines.COMBUSTION_GENERATOR.item(), 1)
                .define('I', Items.IRON_INGOT).define('F', Items.FURNACE).define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE)
                .pattern("III").pattern("CFC").pattern("IRI")
                .unlockedBy(getHasName(Items.FURNACE), has(Items.FURNACE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.ORE_CRUSHER.item(), 1)
                .define('I', Items.IRON_INGOT).define('S', Items.STONECUTTER).define('P', Items.PISTON).define('G', Items.GRINDSTONE)
                .pattern("ISI").pattern("PGP").pattern("I I")
                .unlockedBy(getHasName(Items.GRINDSTONE), has(Items.GRINDSTONE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.CABLES.get(0).item(), 6)
                .define('C', Items.COPPER_INGOT).define('K', Items.DRIED_KELP)
                .pattern("KKK").pattern("CCC").pattern("KKK")
                .unlockedBy(getHasName(Items.COPPER_INGOT), has(Items.COPPER_INGOT))
                .save(output);
    }

    /** Ore Crusher (DESIGN.md §6, §8): ores/raw/ingots to dust, with byproduct chances. */
    private void crushing() {
        crushOre("uranium", GZResources.URANIUM_DUST, null, 0);
        crushOre("lithium", GZResources.LITHIUM_DUST, null, 0);
        crushOre("tungsten", GZResources.TUNGSTEN_DUST, null, 0);
        crushOre("lead", GZResources.LEAD_DUST, null, 0);
        crushOre("rare_earth", GZResources.RARE_EARTH_DUST, GZResources.COBALT_DUST, 0.10f);
        crushOre("iron", GZResources.IRON_DUST, GZResources.COBALT_DUST, 0.15f);
        crushOre("gold", GZResources.GOLD_DUST, null, 0);
        crushOre("copper", GZResources.COPPER_DUST, null, 0);
        for (String metal : new String[] {"uranium", "lead", "tungsten", "iron", "gold", "copper"}) {
            ItemLike dust = switch (metal) {
                case "uranium" -> GZResources.URANIUM_DUST;
                case "lead" -> GZResources.LEAD_DUST;
                case "tungsten" -> GZResources.TUNGSTEN_DUST;
                case "iron" -> GZResources.IRON_DUST;
                case "gold" -> GZResources.GOLD_DUST;
                default -> GZResources.COPPER_DUST;
            };
            crush("crushing/" + metal + "_dust_from_ingot", tag(GZTags.commonItem("ingots/" + metal)), 1, dust, 1, null, 0, 100);
        }
        crush("crushing/sulfur_dust_from_ore", tag(GZTags.commonItem("ores/sulfur")), 1, GZResources.SULFUR_DUST, 5, null, 0, 160);
        crush("crushing/saltpeter_dust_from_ore", tag(GZTags.commonItem("ores/saltpeter")), 1, GZResources.SALTPETER_DUST, 4, null, 0, 160);
        // Vanilla Sulfur Caves blocks: a renewable-feeling sulfur and mercury source.
        crush("crushing/sulfur_dust_from_sulfur", Ingredient.of(Items.SULFUR), 1, GZResources.SULFUR_DUST, 4, null, 0, 120);
        crush("crushing/sulfur_dust_from_potent_sulfur", Ingredient.of(Items.POTENT_SULFUR), 1, GZResources.SULFUR_DUST, 6, null, 0, 120);
        crush("crushing/cinnabar_dust", Ingredient.of(Items.CINNABAR), 1, GZResources.CINNABAR_DUST, 4, null, 0, 160);
        crush("crushing/gravel_from_cobblestone", Ingredient.of(Items.COBBLESTONE), 1, Items.GRAVEL, 1, Items.FLINT, 0.10f, 100);
        crush("crushing/sand_from_gravel", Ingredient.of(Items.GRAVEL), 1, Items.SAND, 1, null, 0, 100);
    }

    /** Raw material -> 2 dust; ore block -> 2 dust with a 50% chance of a third. */
    private void crushOre(String metal, ItemLike dust, ItemLike byproduct, float byChance) {
        crush("crushing/" + metal + "_dust_from_raw", tag(GZTags.commonItem("raw_materials/" + metal)), 1, dust, 2, byproduct, byChance, 200);
        crush("crushing/" + metal + "_dust_from_ore", tag(GZTags.commonItem("ores/" + metal)), 1, dust, 2,
                byproduct != null ? byproduct : dust, byproduct != null ? byChance : 0.5f, 240);
    }

    private void crush(String name, Ingredient input, int inCount, ItemLike result, int count, ItemLike byproduct, float chance, int time) {
        List<ProcessingRecipe.Output> outputs = new ArrayList<>();
        outputs.add(new ProcessingRecipe.Output(new ItemStackTemplate(result.asItem(), count), 1f));
        if (byproduct != null && chance > 0) {
            outputs.add(new ProcessingRecipe.Output(new ItemStackTemplate(byproduct.asItem(), 1), chance));
        }
        ProcessingRecipe recipe = new ProcessingRecipe(ProcessingKind.CRUSHING, List.of(new SizedIngredient(input, inCount)), outputs,
                time, OreCrusherBlockEntity.ENERGY_PER_TICK);
        output.accept(ResourceKey.create(Registries.RECIPE, GroundZero.id(name)), recipe, null);
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
