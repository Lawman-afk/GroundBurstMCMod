package dev.groundzero.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.groundzero.GroundZero;
import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.machine.ArcFurnaceBlockEntity;
import dev.groundzero.content.machine.CryogenicCondenserBlockEntity;
import dev.groundzero.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.IndustrialPressBlockEntity;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.content.recipe.ProcessingRecipe;
import dev.groundzero.registry.GZTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
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
        // Every material input is a common (c:) tag, so other mods' uranium, lead, dusts etc. work here too.
        cook("raw_materials/uranium", GZResources.RAW_URANIUM, GZResources.URANIUM_INGOT, 0.7f);
        cook("raw_materials/lead", GZResources.RAW_LEAD, GZResources.LEAD_INGOT, 0.5f);

        storage("ingots/uranium", "storage_blocks/uranium", GZResources.URANIUM_INGOT, GZResources.URANIUM_BLOCK.item());
        storage("ingots/lead", "storage_blocks/lead", GZResources.LEAD_INGOT, GZResources.LEAD_BLOCK.item());
        storage("ingots/tungsten", "storage_blocks/tungsten", GZResources.TUNGSTEN_INGOT, GZResources.TUNGSTEN_BLOCK.item());
        storage("raw_materials/uranium", "storage_blocks/raw_uranium", GZResources.RAW_URANIUM, GZResources.RAW_URANIUM_BLOCK.item());
        storage("raw_materials/lithium", "storage_blocks/raw_lithium", GZResources.RAW_LITHIUM, GZResources.RAW_LITHIUM_BLOCK.item());
        storage("raw_materials/tungsten", "storage_blocks/raw_tungsten", GZResources.RAW_TUNGSTEN, GZResources.RAW_TUNGSTEN_BLOCK.item());
        storage("raw_materials/lead", "storage_blocks/raw_lead", GZResources.RAW_LEAD, GZResources.RAW_LEAD_BLOCK.item());
        storage("raw_materials/rare_earth", "storage_blocks/raw_rare_earth", GZResources.RAW_RARE_EARTH, GZResources.RAW_RARE_EARTH_BLOCK.item());

        // Black powder: a renewable gunpowder source from mined saltpeter and sulfur.
        shapeless(RecipeCategory.MISC, Items.GUNPOWDER, 4)
                .requires(GZTags.commonItem("dusts/saltpeter")).requires(GZTags.commonItem("dusts/saltpeter"))
                .requires(GZTags.commonItem("dusts/sulfur"))
                .requires(Items.CHARCOAL)
                .unlockedBy(getHasName(GZResources.SALTPETER_DUST), has(GZResources.SALTPETER_DUST))
                .save(output, id("gunpowder_from_saltpeter"));

        // Dusts smelt back to ingots (tungsten dust waits for the Arc Furnace).
        cook("dusts/uranium", GZResources.URANIUM_DUST, GZResources.URANIUM_INGOT, 0.3f);
        cook("dusts/lead", GZResources.LEAD_DUST, GZResources.LEAD_INGOT, 0.3f);
        cook("dusts/iron", GZResources.IRON_DUST, Items.IRON_INGOT, 0.3f);
        cook("dusts/gold", GZResources.GOLD_DUST, Items.GOLD_INGOT, 0.3f);
        cook("dusts/copper", GZResources.COPPER_DUST, Items.COPPER_INGOT, 0.3f);

        bootstrap();
        crushing();
        arcSmelting();
        pressing();
        parts();
        gases();
    }

    /** DESIGN.md §26.1: vanilla-only recipes that get the player into the power + machine loop. */
    private void bootstrap() {
        shaped(RecipeCategory.REDSTONE, GZMachines.COMBUSTION_GENERATOR.item(), 1)
                .define('I', Tags.Items.INGOTS_IRON).define('F', Items.FURNACE).define('C', Tags.Items.INGOTS_COPPER).define('R', Tags.Items.DUSTS_REDSTONE)
                .pattern("III").pattern("CFC").pattern("IRI")
                .unlockedBy(getHasName(Items.FURNACE), has(Items.FURNACE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.ORE_CRUSHER.item(), 1)
                .define('I', Tags.Items.INGOTS_IRON).define('S', Items.STONECUTTER).define('P', Items.PISTON).define('G', Items.GRINDSTONE)
                .pattern("ISI").pattern("PGP").pattern("I I")
                .unlockedBy(getHasName(Items.GRINDSTONE), has(Items.GRINDSTONE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.CABLES.get(0).item(), 6)
                .define('C', Tags.Items.INGOTS_COPPER).define('K', Items.DRIED_KELP)
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
        // Monazite carries thorium and a little uranium; cobalt comes with copper (Copperbelt-style) and some iron ores.
        crushOre("rare_earth", GZResources.RARE_EARTH_DUST, GZResources.URANIUM_DUST, 0.05f);
        crushOre("iron", GZResources.IRON_DUST, GZResources.COBALT_DUST, 0.10f);
        crushOre("gold", GZResources.GOLD_DUST, null, 0);
        crushOre("copper", GZResources.COPPER_DUST, GZResources.COBALT_DUST, 0.15f);
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
        outputs.add(out(result, count));
        if (byproduct != null && chance > 0) {
            outputs.add(new ProcessingRecipe.Output(new ItemStackTemplate(byproduct.asItem(), 1), chance));
        }
        machine(ProcessingKind.CRUSHING, name, List.of(new SizedIngredient(input, inCount)), null, outputs, time, OreCrusherBlockEntity.ENERGY_PER_TICK);
    }

    private static ProcessingRecipe.Output out(ItemLike item, int count) {
        return new ProcessingRecipe.Output(new ItemStackTemplate(item.asItem(), count), 1f);
    }

    private static SizedIngredient in(Ingredient ingredient, int count) {
        return new SizedIngredient(ingredient, count);
    }

    private void machine(ProcessingKind kind, String name, List<SizedIngredient> inputs, Ingredient catalyst,
            List<ProcessingRecipe.Output> outputs, int time, int energyPerTick) {
        machine(kind, name, inputs, catalyst, null, outputs, time, energyPerTick);
    }

    private void machine(ProcessingKind kind, String name, List<SizedIngredient> inputs, Ingredient catalyst, SizedFluidIngredient fluid,
            List<ProcessingRecipe.Output> outputs, int time, int energyPerTick) {
        ProcessingRecipe recipe = new ProcessingRecipe(kind, inputs, Optional.ofNullable(catalyst), Optional.ofNullable(fluid), outputs, time, energyPerTick);
        output.accept(ResourceKey.create(Registries.RECIPE, GroundZero.id(name)), recipe, null);
    }

    /** Electrolytic Separator and Cryogenic Condenser (DESIGN.md §6): deuterium, liquid oxygen, liquid nitrogen. */
    private void gases() {
        machine(ProcessingKind.ELECTROLYSIS, "electrolysis/deuterium_canister", List.of(in(Ingredient.of(GZChemicals.EMPTY_GAS_CANISTER), 1)), null,
                SizedFluidIngredient.of(Fluids.WATER, 8_000), List.of(out(GZChemicals.DEUTERIUM_CANISTER, 1)), 400,
                ElectrolyticSeparatorBlockEntity.ENERGY_PER_TICK);
        int cryo = CryogenicCondenserBlockEntity.ENERGY_PER_TICK;
        machine(ProcessingKind.CRYO_CONDENSING, "cryo_condensing/liquid_oxygen_canister", List.of(in(Ingredient.of(GZParts.EMPTY_CANISTER), 1)),
                Ingredient.of(GZParts.OXYGEN_MEMBRANE), List.of(out(GZChemicals.LIQUID_OXYGEN_CANISTER, 1)), 300, cryo);
        machine(ProcessingKind.CRYO_CONDENSING, "cryo_condensing/liquid_nitrogen_canister", List.of(in(Ingredient.of(GZParts.EMPTY_CANISTER), 1)),
                Ingredient.of(GZParts.NITROGEN_MEMBRANE), List.of(out(GZChemicals.LIQUID_NITROGEN_CANISTER, 1)), 300, cryo);

        shapeless(RecipeCategory.MISC, GZChemicals.EMPTY_GAS_CANISTER, 1)
                .requires(GZTags.commonItem("plates/steel")).requires(GZTags.commonItem("plates/steel")).requires(Items.GLASS)
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE))
                .save(output);
        membrane(GZParts.OXYGEN_MEMBRANE, Items.LAPIS_LAZULI);
        membrane(GZParts.NITROGEN_MEMBRANE, Items.QUARTZ);
        // DESIGN.md §26.4. The condenser calls for an advanced circuit; until the Circuit Fabricator exists
        // (Phase 4, slice 5) it takes any basic circuit.
        shaped(RecipeCategory.REDSTONE, GZMachines.ELECTROLYTIC_SEPARATOR.item(), 1)
                .define('W', GZTags.commonItem("wires/gold")).define('G', Items.GLASS).define('P', GZTags.commonItem("plates/copper"))
                .define('F', GZParts.MACHINE_FRAME).define('B', GZTags.BASIC_CIRCUITS).define('K', Items.CAULDRON)
                .pattern("WGW").pattern("PFP").pattern("BK ")
                .unlockedBy(getHasName(GZParts.MACHINE_FRAME), has(GZParts.MACHINE_FRAME))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.CRYOGENIC_CONDENSER.item(), 1)
                .define('P', GZTags.commonItem("plates/copper")).define('M', GZParts.ELECTRIC_MOTOR).define('F', GZParts.MACHINE_FRAME)
                .define('V', GZParts.PRESSURE_VESSEL).define('B', GZTags.BASIC_CIRCUITS)
                .pattern("PMP").pattern("PFP").pattern("BV ")
                .unlockedBy(getHasName(GZParts.PRESSURE_VESSEL), has(GZParts.PRESSURE_VESSEL))
                .save(output);
    }

    private void membrane(ItemLike membrane, ItemLike sieve) {
        shaped(RecipeCategory.TOOLS, membrane, 1)
                .define('R', GZTags.commonItem("rods/steel")).define('C', Items.CLAY_BALL).define('L', sieve)
                .pattern("RCR").pattern("CLC").pattern("RCR")
                .unlockedBy(getHasName(Items.CLAY_BALL), has(Items.CLAY_BALL))
                .save(output);
    }

    /** Arc Furnace (DESIGN.md §26.2-26.3): steel, refractory metals, carbide, magnets, cobalt. */
    private void arcSmelting() {
        int fe = ArcFurnaceBlockEntity.ENERGY_PER_TICK;
        Ingredient coal = tag(ItemTags.COALS);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/steel_ingot", List.of(in(tag(Tags.Items.INGOTS_IRON), 1), in(coal, 1)), null,
                List.of(out(GZParts.STEEL_INGOT, 1)), 200, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/steel_ingot_from_dust", List.of(in(tag(GZTags.commonItem("dusts/iron")), 1), in(coal, 1)), null,
                List.of(out(GZParts.STEEL_INGOT, 1)), 160, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/tungsten_ingot_from_dust", List.of(in(tag(GZTags.commonItem("dusts/tungsten")), 1)), null,
                List.of(out(GZResources.TUNGSTEN_INGOT, 1)), 240, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/tungsten_ingot_from_raw", List.of(in(tag(GZTags.commonItem("raw_materials/tungsten")), 1)), null,
                List.of(out(GZResources.TUNGSTEN_INGOT, 1)), 300, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/tungsten_carbide_ingot", List.of(in(tag(GZTags.commonItem("ingots/tungsten")), 1), in(coal, 2)), null,
                List.of(out(GZParts.TUNGSTEN_CARBIDE_INGOT, 1)), 300, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/neodymium_magnet",
                List.of(in(tag(GZTags.commonItem("dusts/rare_earth")), 2), in(tag(GZTags.commonItem("dusts/iron")), 1)), null,
                List.of(out(GZParts.NEODYMIUM_MAGNET, 1)), 240, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/cobalt_ingot", List.of(in(tag(GZTags.commonItem("dusts/cobalt")), 8)), null,
                List.of(out(GZParts.COBALT_INGOT, 1)), 300, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/lead_ingot_from_raw", List.of(in(tag(GZTags.commonItem("raw_materials/lead")), 1)), null,
                List.of(out(GZResources.LEAD_INGOT, 1), new ProcessingRecipe.Output(new ItemStackTemplate(GZResources.LEAD_DUST.get(), 1), 0.25f)), 120, fe);
    }

    /** Industrial Press (DESIGN.md §26.2): the die in the catalyst slot picks the product; dies are not consumed. */
    private void pressing() {
        int fe = IndustrialPressBlockEntity.ENERGY_PER_TICK;
        Ingredient plate = Ingredient.of(GZParts.PLATE_DIE), rod = Ingredient.of(GZParts.ROD_DIE),
                wire = Ingredient.of(GZParts.WIRE_DIE), canister = Ingredient.of(GZParts.CANISTER_DIE);
        press("steel_plate", tag(GZTags.commonItem("ingots/steel")), 1, plate, GZParts.STEEL_PLATE, 1, 100, fe);
        press("copper_plate", tag(Tags.Items.INGOTS_COPPER), 1, plate, GZParts.COPPER_PLATE, 1, 100, fe);
        press("lead_plate", tag(GZTags.commonItem("ingots/lead")), 1, plate, GZParts.LEAD_PLATE, 1, 100, fe);
        press("tungsten_plate", tag(GZTags.commonItem("ingots/tungsten")), 1, plate, GZParts.TUNGSTEN_PLATE, 1, 160, fe);
        press("dense_steel_plate", tag(GZTags.commonItem("plates/steel")), 4, plate, GZParts.DENSE_STEEL_PLATE, 1, 200, fe);
        press("steel_rod", tag(GZTags.commonItem("ingots/steel")), 1, rod, GZParts.STEEL_ROD, 2, 100, fe);
        press("tungsten_rod", tag(GZTags.commonItem("ingots/tungsten")), 1, rod, GZParts.TUNGSTEN_ROD, 1, 160, fe);
        press("copper_wire", tag(Tags.Items.INGOTS_COPPER), 1, wire, GZParts.COPPER_WIRE, 4, 100, fe);
        press("gold_wire", tag(Tags.Items.INGOTS_GOLD), 1, wire, GZParts.GOLD_WIRE, 4, 100, fe);
        press("tungsten_filament", tag(GZTags.commonItem("ingots/tungsten")), 1, wire, GZParts.TUNGSTEN_FILAMENT, 4, 160, fe);
        press("empty_canister", tag(GZTags.commonItem("plates/steel")), 2, canister, GZParts.EMPTY_CANISTER, 2, 120, fe);
    }

    private void press(String name, Ingredient input, int count, Ingredient die, ItemLike result, int resultCount, int time, int fe) {
        machine(ProcessingKind.PRESSING, "pressing/" + name, List.of(in(input, count)), die, List.of(out(result, resultCount)), time, fe);
    }

    /** Crafting-table parts, machines and dies (DESIGN.md §26.1-26.2, §26.4). */
    private void parts() {
        shaped(RecipeCategory.REDSTONE, GZMachines.ARC_FURNACE.item(), 1)
                .define('B', Tags.Items.STORAGE_BLOCKS_IRON).define('K', Items.CAULDRON).define('C', Tags.Items.INGOTS_COPPER)
                .define('F', Items.BLAST_FURNACE).define('R', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                .pattern("BKB").pattern("CFC").pattern("BRB")
                .unlockedBy(getHasName(Items.BLAST_FURNACE), has(Items.BLAST_FURNACE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.INDUSTRIAL_PRESS.item(), 1)
                .define('I', Tags.Items.INGOTS_IRON).define('A', Items.ANVIL).define('P', Items.PISTON).define('R', Tags.Items.DUSTS_REDSTONE)
                .pattern("IAI").pattern("PRP").pattern("I I")
                .unlockedBy(getHasName(Items.ANVIL), has(Items.ANVIL))
                .save(output);
        die(GZParts.PLATE_DIE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
        die(GZParts.ROD_DIE, Items.IRON_BARS);
        die(GZParts.WIRE_DIE, Items.IRON_CHAIN);
        die(GZParts.CANISTER_DIE, Items.BUCKET);

        shaped(RecipeCategory.MISC, GZParts.HANDMADE_CIRCUIT_BOARD, 1)
                .define('C', Tags.Items.INGOTS_COPPER).define('R', Tags.Items.DUSTS_REDSTONE).define('P', Items.PAPER).define('G', Tags.Items.NUGGETS_GOLD)
                .pattern("CRC").pattern("RPG")
                .unlockedBy(getHasName(Items.REDSTONE), has(Items.REDSTONE))
                .save(output);
        TagKey<Item> circuit = GZTags.BASIC_CIRCUITS;
        shaped(RecipeCategory.MISC, GZParts.ELECTRIC_MOTOR, 1)
                .define('W', GZTags.commonItem("wires/copper")).define('P', GZTags.commonItem("plates/steel"))
                .define('M', GZParts.NEODYMIUM_MAGNET).define('R', GZTags.commonItem("rods/steel"))
                .pattern("WPW").pattern("MRM").pattern("WPW")
                .unlockedBy(getHasName(GZParts.NEODYMIUM_MAGNET), has(GZParts.NEODYMIUM_MAGNET))
                .save(output);
        shaped(RecipeCategory.MISC, GZParts.HEATING_ELEMENT, 1)
                .define('F', GZParts.TUNGSTEN_FILAMENT).define('P', GZTags.commonItem("plates/steel")).define('C', GZTags.commonItem("wires/copper"))
                .pattern("FFF").pattern("PCP")
                .unlockedBy(getHasName(GZParts.TUNGSTEN_FILAMENT), has(GZParts.TUNGSTEN_FILAMENT))
                .save(output);
        shaped(RecipeCategory.MISC, GZParts.PRESSURE_VESSEL, 1)
                .define('D', GZParts.DENSE_STEEL_PLATE).define('R', GZTags.commonItem("rods/steel"))
                .pattern(" D ").pattern("DRD").pattern(" D ")
                .unlockedBy(getHasName(GZParts.DENSE_STEEL_PLATE), has(GZParts.DENSE_STEEL_PLATE))
                .save(output);
        shaped(RecipeCategory.MISC, GZParts.MACHINE_FRAME, 1)
                .define('R', GZTags.commonItem("rods/steel")).define('P', GZTags.commonItem("plates/steel")).define('C', circuit)
                .pattern("RPR").pattern("PCP").pattern("RPR")
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE))
                .save(output);
        shapeless(RecipeCategory.MISC, GZParts.SERVO, 1)
                .requires(GZParts.ELECTRIC_MOTOR).requires(GZTags.commonItem("rods/steel")).requires(circuit)
                .unlockedBy(getHasName(GZParts.ELECTRIC_MOTOR), has(GZParts.ELECTRIC_MOTOR))
                .save(output);
    }

    private void die(ItemLike die, ItemLike shape) {
        shaped(RecipeCategory.TOOLS, die, 1)
                .define('I', Tags.Items.INGOTS_IRON).define('X', shape)
                .pattern(" I ").pattern("IXI").pattern(" I ")
                .unlockedBy(getHasName(GZMachines.INDUSTRIAL_PRESS.item()), has(GZMachines.INDUSTRIAL_PRESS.item()))
                .save(output);
    }

    /** Smelting + blasting from a c: tag (`named` only names the recipe and unlocks it). */
    private void cook(String inputTag, ItemLike named, ItemLike result, float xp) {
        Ingredient input = tag(GZTags.commonItem(inputTag));
        SimpleCookingRecipeBuilder.generic(input, RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 200, SmeltingRecipe::new)
                .unlockedBy("has_" + inputTag.replace('/', '_'), has(GZTags.commonItem(inputTag)))
                .save(output, id(getItemName(result) + "_from_smelting_" + getItemName(named)));
        SimpleCookingRecipeBuilder.generic(input, RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 100, BlastingRecipe::new)
                .unlockedBy("has_" + inputTag.replace('/', '_'), has(GZTags.commonItem(inputTag)))
                .save(output, id(getItemName(result) + "_from_blasting_" + getItemName(named)));
    }

    /** 9 units <-> 1 block, both directions taking any item in the c: tags. */
    private void storage(String unitTag, String blockTag, ItemLike unit, ItemLike block) {
        shaped(RecipeCategory.BUILDING_BLOCKS, block, 1)
                .define('#', GZTags.commonItem(unitTag))
                .pattern("###").pattern("###").pattern("###")
                .unlockedBy("has_" + unitTag.replace('/', '_'), has(GZTags.commonItem(unitTag)))
                .save(output);   // default id: the result's own id (groundzero:<block>)
        shapeless(RecipeCategory.MISC, unit, 9)
                .requires(GZTags.commonItem(blockTag))
                .unlockedBy("has_" + blockTag.replace('/', '_'), has(GZTags.commonItem(blockTag)))
                .save(output, id(getItemName(unit) + "_from_" + getItemName(block)));
    }
}
