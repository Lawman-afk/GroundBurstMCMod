package dev.groundburst.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.machine.ArcFurnaceBlockEntity;
import dev.groundburst.content.fluid.ChemFluid;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.machine.ChemicalPlantBlockEntity;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.machine.CryogenicCondenserBlockEntity;
import dev.groundburst.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.IndustrialPressBlockEntity;
import dev.groundburst.content.machine.OreCrusherBlockEntity;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import dev.groundburst.registry.GBTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import dev.groundburst.content.resource.GBResources;
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
 * recipe here is saved explicitly under groundburst:.
 */
public class GBRecipeProvider extends RecipeProvider {
    private final net.minecraft.core.HolderGetter<net.minecraft.world.level.material.Fluid> fluids;

    public GBRecipeProvider(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
        super(recipes, advancements);
        this.fluids = recipes.lookup(Registries.FLUID);
    }

    /** A fluid ingredient by common tag, so any mod's fluid of that kind works. */
    private SizedFluidIngredient fluid(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag, int amount) {
        return new SizedFluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(this.fluids.getOrThrow(tag)), amount);
    }

    private SizedFluidIngredient fluid(ChemFluid f, int amount) {
        return fluid(f.commonTag(), amount);
    }

    private SizedFluidIngredient water(int amount) {
        return fluid(Tags.Fluids.WATER, amount);
    }

    private static FluidStackTemplate fluidOut(ChemFluid f, int amount) {
        return new FluidStackTemplate(f.source().get(), amount);
    }

    private static String id(String name) {
        return GroundBurst.id(name).toString();
    }

    @Override
    protected void buildRecipes() {
        // Furnace-grade metals. Tungsten is too refractory for a furnace: Arc Furnace only (Phase 4).
        // Every material input is a common (c:) tag, so other mods' uranium, lead, dusts etc. work here too.
        cook("raw_materials/uranium", GBResources.RAW_URANIUM, GBResources.URANIUM_INGOT, 0.7f);
        cook("raw_materials/lead", GBResources.RAW_LEAD, GBResources.LEAD_INGOT, 0.5f);

        storage("ingots/uranium", "storage_blocks/uranium", GBResources.URANIUM_INGOT, GBResources.URANIUM_BLOCK.item());
        storage("ingots/lead", "storage_blocks/lead", GBResources.LEAD_INGOT, GBResources.LEAD_BLOCK.item());
        storage("ingots/tungsten", "storage_blocks/tungsten", GBResources.TUNGSTEN_INGOT, GBResources.TUNGSTEN_BLOCK.item());
        storage("raw_materials/uranium", "storage_blocks/raw_uranium", GBResources.RAW_URANIUM, GBResources.RAW_URANIUM_BLOCK.item());
        storage("raw_materials/lithium", "storage_blocks/raw_lithium", GBResources.RAW_LITHIUM, GBResources.RAW_LITHIUM_BLOCK.item());
        storage("raw_materials/tungsten", "storage_blocks/raw_tungsten", GBResources.RAW_TUNGSTEN, GBResources.RAW_TUNGSTEN_BLOCK.item());
        storage("raw_materials/lead", "storage_blocks/raw_lead", GBResources.RAW_LEAD, GBResources.RAW_LEAD_BLOCK.item());
        storage("raw_materials/rare_earth", "storage_blocks/raw_rare_earth", GBResources.RAW_RARE_EARTH, GBResources.RAW_RARE_EARTH_BLOCK.item());

        // Black powder: a renewable gunpowder source from mined saltpeter and sulfur.
        shapeless(RecipeCategory.MISC, Items.GUNPOWDER, 4)
                .requires(GBTags.commonItem("dusts/saltpeter")).requires(GBTags.commonItem("dusts/saltpeter"))
                .requires(GBTags.commonItem("dusts/sulfur"))
                .requires(Items.CHARCOAL)
                .unlockedBy(getHasName(GBResources.SALTPETER_DUST), has(GBResources.SALTPETER_DUST))
                .save(output, id("gunpowder_from_saltpeter"));

        // Dusts smelt back to ingots (tungsten dust waits for the Arc Furnace).
        cook("dusts/uranium", GBResources.URANIUM_DUST, GBResources.URANIUM_INGOT, 0.3f);
        cook("dusts/lead", GBResources.LEAD_DUST, GBResources.LEAD_INGOT, 0.3f);
        cook("dusts/iron", GBResources.IRON_DUST, Items.IRON_INGOT, 0.3f);
        cook("dusts/gold", GBResources.GOLD_DUST, Items.GOLD_INGOT, 0.3f);
        cook("dusts/copper", GBResources.COPPER_DUST, Items.COPPER_INGOT, 0.3f);

        bootstrap();
        crushing();
        arcSmelting();
        pressing();
        parts();
        gases();
        canning();
        chemistry();
        slice5();
        slice6();
        slice7();
        slice8();
        upgradeChips();
    }

    /**
     * The upgrade chips (DESIGN.md §7.3): on each circuit tier, a speed chip (the circuit, redstone and gold wire), an
     * efficiency chip (the circuit, a capacitor and copper wire), and two balanced chips from one of each.
     */
    private void upgradeChips() {
        List<net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item>> circuits = List.of(GBParts.BASIC_CIRCUIT,
                GBParts.ADVANCED_CIRCUIT, GBParts.MILITARY_CIRCUIT, GBParts.QUANTUM_CIRCUIT);
        for (var tier : dev.groundburst.content.machine.UpgradeChipItem.Tier.values()) {
            var circuit = circuits.get(tier.ordinal());
            var speed = dev.groundburst.content.machine.GBUpgrades.chip(dev.groundburst.content.machine.UpgradeChipItem.Kind.SPEED, tier);
            var efficiency = dev.groundburst.content.machine.GBUpgrades.chip(dev.groundburst.content.machine.UpgradeChipItem.Kind.EFFICIENCY, tier);
            var balanced = dev.groundburst.content.machine.GBUpgrades.chip(dev.groundburst.content.machine.UpgradeChipItem.Kind.BALANCED, tier);
            shapeless(RecipeCategory.MISC, speed.get(), 1)
                    .requires(circuit).requires(Items.REDSTONE).requires(Items.REDSTONE).requires(GBTags.commonItem("wires/gold"))
                    .unlockedBy(getHasName(circuit), has(circuit)).save(output);
            shapeless(RecipeCategory.MISC, efficiency.get(), 1)
                    .requires(circuit).requires(GBParts.CAPACITOR).requires(GBTags.commonItem("wires/copper")).requires(GBTags.commonItem("wires/copper"))
                    .unlockedBy(getHasName(circuit), has(circuit)).save(output);
            shapeless(RecipeCategory.MISC, balanced.get(), 2)
                    .requires(speed).requires(efficiency)
                    .unlockedBy(getHasName(speed), has(speed)).save(output);
        }
    }

    /** DESIGN.md §26.1: vanilla-only recipes that get the player into the power + machine loop. */
    private void bootstrap() {
        shaped(RecipeCategory.REDSTONE, GBMachines.COMBUSTION_GENERATOR.item(), 1)
                .define('I', Tags.Items.INGOTS_IRON).define('F', Items.FURNACE).define('C', Tags.Items.INGOTS_COPPER).define('R', Tags.Items.DUSTS_REDSTONE)
                .pattern("III").pattern("CFC").pattern("IRI")
                .unlockedBy(getHasName(Items.FURNACE), has(Items.FURNACE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.ORE_CRUSHER.item(), 1)
                .define('I', Tags.Items.INGOTS_IRON).define('S', Items.STONECUTTER).define('P', Items.PISTON).define('G', Items.GRINDSTONE)
                .pattern("ISI").pattern("PGP").pattern("I I")
                .unlockedBy(getHasName(Items.GRINDSTONE), has(Items.GRINDSTONE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.CABLES.get(0).item(), 6)
                .define('C', Tags.Items.INGOTS_COPPER).define('K', Items.DRIED_KELP)
                .pattern("KKK").pattern("CCC").pattern("KKK")
                .unlockedBy(getHasName(Items.COPPER_INGOT), has(Items.COPPER_INGOT))
                .save(output);
    }

    /** Ore Crusher (DESIGN.md §6, §8): ores/raw/ingots to dust, with byproduct chances. */
    private void crushing() {
        crushOre("uranium", GBResources.URANIUM_DUST, null, 0);
        crushOre("lithium", GBResources.LITHIUM_DUST, null, 0);
        crushOre("tungsten", GBResources.TUNGSTEN_DUST, null, 0);
        crushOre("lead", GBResources.LEAD_DUST, null, 0);
        // Monazite carries thorium and a little uranium; cobalt comes with copper (Copperbelt-style) and some iron ores.
        crushOre("rare_earth", GBResources.RARE_EARTH_DUST, GBResources.URANIUM_DUST, 0.05f);
        crushOre("iron", GBResources.IRON_DUST, GBResources.COBALT_DUST, 0.10f);
        crushOre("gold", GBResources.GOLD_DUST, null, 0);
        crushOre("copper", GBResources.COPPER_DUST, GBResources.COBALT_DUST, 0.15f);
        for (String metal : new String[] {"uranium", "lead", "tungsten", "iron", "gold", "copper"}) {
            ItemLike dust = switch (metal) {
                case "uranium" -> GBResources.URANIUM_DUST;
                case "lead" -> GBResources.LEAD_DUST;
                case "tungsten" -> GBResources.TUNGSTEN_DUST;
                case "iron" -> GBResources.IRON_DUST;
                case "gold" -> GBResources.GOLD_DUST;
                default -> GBResources.COPPER_DUST;
            };
            crush("crushing/" + metal + "_dust_from_ingot", tag(GBTags.commonItem("ingots/" + metal)), 1, dust, 1, null, 0, 100);
        }
        crush("crushing/sulfur_dust_from_ore", tag(GBTags.commonItem("ores/sulfur")), 1, GBResources.SULFUR_DUST, 5, null, 0, 160);
        crush("crushing/saltpeter_dust_from_ore", tag(GBTags.commonItem("ores/saltpeter")), 1, GBResources.SALTPETER_DUST, 4, null, 0, 160);
        // Vanilla Sulfur Caves blocks: a renewable-feeling sulfur and mercury source.
        crush("crushing/sulfur_dust_from_sulfur", Ingredient.of(Items.SULFUR), 1, GBResources.SULFUR_DUST, 4, null, 0, 120);
        crush("crushing/sulfur_dust_from_potent_sulfur", Ingredient.of(Items.POTENT_SULFUR), 1, GBResources.SULFUR_DUST, 6, null, 0, 120);
        crush("crushing/cinnabar_dust", Ingredient.of(Items.CINNABAR), 1, GBResources.CINNABAR_DUST, 4, null, 0, 160);
        crush("crushing/gravel_from_cobblestone", Ingredient.of(Items.COBBLESTONE), 1, Items.GRAVEL, 1, Items.FLINT, 0.10f, 100);
        crush("crushing/sand_from_gravel", Ingredient.of(Items.GRAVEL), 1, Items.SAND, 1, null, 0, 100);
    }

    /** Raw material -> 2 dust; ore block -> 2 dust with a 50% chance of a third. */
    private void crushOre(String metal, ItemLike dust, ItemLike byproduct, float byChance) {
        crush("crushing/" + metal + "_dust_from_raw", tag(GBTags.commonItem("raw_materials/" + metal)), 1, dust, 2, byproduct, byChance, 200);
        crush("crushing/" + metal + "_dust_from_ore", tag(GBTags.commonItem("ores/" + metal)), 1, dust, 2,
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
        machine(kind, name, inputs, catalyst, List.of(), outputs, List.of(), time, energyPerTick);
    }

    private void machine(ProcessingKind kind, String name, List<SizedIngredient> inputs, Ingredient catalyst, List<SizedFluidIngredient> fluids,
            List<ProcessingRecipe.Output> outputs, List<FluidStackTemplate> fluidOutputs, int time, int energyPerTick) {
        ProcessingRecipe recipe = new ProcessingRecipe(kind, inputs, Optional.ofNullable(catalyst), fluids, outputs, fluidOutputs, time, energyPerTick);
        output.accept(ResourceKey.create(Registries.RECIPE, GroundBurst.id(name)), recipe, null);
    }

    /** Canning Machine (DESIGN.md §8): every fluid with a canister can be canned and poured out again, 1000 mB a canister. */
    private void canning() {
        int fe = dev.groundburst.content.machine.CanningMachineBlockEntity.ENERGY_PER_TICK;
        int time = dev.groundburst.content.machine.CanningMachineBlockEntity.TIME;
        for (ChemFluid f : GBFluids.all()) {
            machine(ProcessingKind.CANNING, "canning/fill_" + f.name(), List.of(in(Ingredient.of(f.emptyCanister().get()), 1)), null,
                    List.of(fluid(f, 1_000)), List.of(out(f.canister().get(), 1)), List.of(), time, fe);
            machine(ProcessingKind.CANNING, "canning/empty_" + f.name(), List.of(in(Ingredient.of(f.canister().get()), 1)), null,
                    List.of(), List.of(out(f.emptyCanister().get(), 1)), List.of(fluidOut(f, 1_000)), time, fe);
        }
        shaped(RecipeCategory.REDSTONE, GBMachines.CANNING_MACHINE.item(), 1)
                .define('P', GBTags.commonItem("plates/steel")).define('E', GBParts.EMPTY_CANISTER).define('G', Items.GLASS)
                .define('F', GBParts.MACHINE_FRAME).define('B', GBTags.BASIC_CIRCUITS)
                .pattern("PEP").pattern("GFG").pattern(" B ")
                .unlockedBy(getHasName(GBParts.EMPTY_CANISTER), has(GBParts.EMPTY_CANISTER))
                .save(output);
        suits();
        radiation();
        aftermath();
        construction();
        grenades();
    }

    /** Thrown explosives (DESIGN.md §14). */
    private void grenades() {
        shaped(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE, 2)
                .define('S', Items.STRING).define('P', Items.PAPER).define('G', Items.GUNPOWDER)
                .pattern(" S ").pattern("PGP").pattern("PGP")
                .unlockedBy(getHasName(Items.GUNPOWDER), has(Items.GUNPOWDER)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE, 2)
                .define('N', Items.IRON_NUGGET).define('P', GBParts.STEEL_PLATE).define('G', Items.GUNPOWDER)
                .pattern("NPN").pattern("NGN").pattern("NNN")
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.STICKY_GRENADE, 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE).requires(Items.SLIME_BALL)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE),
                        has(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.INCENDIARY_GRENADE, 2)
                .requires(GBChemicals.NAPALM_CANISTER).requires(GBParts.STEEL_PLATE).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(GBChemicals.NAPALM_CANISTER), has(GBChemicals.NAPALM_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE, 2)
                .requires(GBChemicals.CHLORINE_CANISTER).requires(GBParts.STEEL_PLATE).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(GBChemicals.CHLORINE_CANISTER), has(GBChemicals.CHLORINE_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.TOXIN_GRENADE, 2)
                .requires(GBChemicals.TOXIN_CANISTER).requires(GBParts.STEEL_PLATE).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(GBChemicals.TOXIN_CANISTER), has(GBChemicals.TOXIN_CANISTER)).save(output);
        // Placed charges and their kit (slice 6b).
        var ord = new Object() {
            final net.minecraft.world.level.ItemLike c4 = dev.groundburst.content.ordnance.GBOrdnance.C4.item();
            final net.minecraft.world.level.ItemLike breach = dev.groundburst.content.ordnance.GBOrdnance.BREACHING_CHARGE.item();
            final net.minecraft.world.level.ItemLike thermite = dev.groundburst.content.ordnance.GBOrdnance.THERMITE_CHARGE.item();
            final net.minecraft.world.level.ItemLike bomb = dev.groundburst.content.ordnance.GBOrdnance.TIMED_BOMB.item();
        };
        shapeless(RecipeCategory.COMBAT, ord.c4, 2)
                .requires(GBChemicals.PLASTIC_EXPLOSIVE).requires(GBChemicals.PLASTIC_EXPLOSIVE).requires(GBParts.BASIC_CIRCUIT)
                .requires(GBTags.commonItem("wires/copper"))
                .unlockedBy(getHasName(GBChemicals.PLASTIC_EXPLOSIVE), has(GBChemicals.PLASTIC_EXPLOSIVE)).save(output);
        shapeless(RecipeCategory.COMBAT, ord.breach, 1)
                .requires(GBChemicals.PLASTIC_EXPLOSIVE).requires(GBTags.commonItem("plates/steel")).requires(Items.STRING).requires(Items.STRING)
                .requires(GBParts.BASIC_CIRCUIT)
                .unlockedBy(getHasName(GBChemicals.PLASTIC_EXPLOSIVE), has(GBChemicals.PLASTIC_EXPLOSIVE)).save(output);
        shapeless(RecipeCategory.COMBAT, ord.thermite, 1)
                .requires(Items.TERRACOTTA).requires(GBTags.commonItem("dusts/iron")).requires(GBTags.commonItem("dusts/iron"))
                .requires(Items.GUNPOWDER).requires(Tags.Items.DUSTS_REDSTONE)
                .unlockedBy(getHasName(GBResources.IRON_DUST), has(GBResources.IRON_DUST)).save(output);
        shapeless(RecipeCategory.COMBAT, ord.bomb, 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE).requires(dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(Items.CLOCK).requires(GBParts.BASIC_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE), has(dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE))
                .save(output);
        // Mines (slice 6c).
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.LANDMINE.item(), 2)
                .requires(GBTags.commonItem("plates/steel")).requires(Items.STONE_PRESSURE_PLATE).requires(Items.GUNPOWDER).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.BOUNCING_MINE.item(), 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE).requires(GBTags.commonItem("plates/steel"))
                .requires(Items.STONE_PRESSURE_PLATE).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE), has(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.CLAYMORE.item(), 1)
                .requires(GBChemicals.PLASTIC_EXPLOSIVE).requires(Items.IRON_NUGGET).requires(Items.IRON_NUGGET).requires(Items.IRON_NUGGET)
                .requires(Items.IRON_NUGGET).requires(GBTags.commonItem("plates/steel")).requires(GBParts.BASIC_CIRCUIT)
                .unlockedBy(getHasName(GBChemicals.PLASTIC_EXPLOSIVE), has(GBChemicals.PLASTIC_EXPLOSIVE)).save(output);
        // Warheads are assembled at the Ordnance Assembly Bench (Phase 7): payload cores, casings and fuzes.
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.HE_CHARGE, 1)
                .requires(GBChemicals.RDX_CRYSTALS).requires(GBChemicals.RDX_CRYSTALS)
                .requires(GBChemicals.RDX_CRYSTALS).requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBChemicals.RDX_CRYSTALS), has(GBChemicals.RDX_CRYSTALS)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.HEAVY_HE_CHARGE, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE)
                .requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE).requires(GBChemicals.RDX_CRYSTALS)
                .requires(GBChemicals.RDX_CRYSTALS).requires(GBChemicals.PLASTIC_EXPLOSIVE)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE), has(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.THERMOBARIC_CHARGE, 1)
                .requires(GBChemicals.THERMOBARIC_SLURRY_CANISTER).requires(GBChemicals.THERMOBARIC_SLURRY_CANISTER)
                .requires(GBChemicals.RDX_CRYSTALS).requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBChemicals.THERMOBARIC_SLURRY_CANISTER), has(GBChemicals.THERMOBARIC_SLURRY_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.CLUSTER_DISPENSER, 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE), has(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.PENETRATOR_CHARGE, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE).requires(GBParts.TUNGSTEN_ROD)
                .requires(GBParts.TUNGSTEN_ROD).requires(GBParts.TUNGSTEN_CARBIDE_INGOT)
                .requires(GBParts.TUNGSTEN_CARBIDE_INGOT).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE), has(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.INCENDIARY_CHARGE, 1)
                .requires(GBChemicals.NAPALM_CANISTER).requires(GBChemicals.NAPALM_CANISTER)
                .requires(GBChemicals.NAPALM_CANISTER).requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBChemicals.NAPALM_CANISTER), has(GBChemicals.NAPALM_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.MASSIVE_ORDNANCE_CHARGE, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.HEAVY_HE_CHARGE)
                .requires(dev.groundburst.content.assembly.GBAssembly.HEAVY_HE_CHARGE)
                .requires(dev.groundburst.content.assembly.GBAssembly.HEAVY_HE_CHARGE)
                .requires(dev.groundburst.content.assembly.GBAssembly.HEAVY_HE_CHARGE)
                .requires(GBChemicals.THERMOBARIC_SLURRY_CANISTER).requires(GBChemicals.THERMOBARIC_SLURRY_CANISTER)
                .requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.HEAVY_HE_CHARGE), has(dev.groundburst.content.assembly.GBAssembly.HEAVY_HE_CHARGE)).save(output);
        // Nuclear cores (Phase 8, DESIGN.md §9.2): a plutonium pit in high-explosive lenses, a tamper round it.
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.TACTICAL_FISSION_CORE, 1)
                .requires(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET).requires(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET)
                .requires(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET).requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET), has(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.BOOSTED_FISSION_CORE, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.FISSION_CORE).requires(dev.groundburst.content.chem.GBChemicals.TRITIUM_CANISTER)
                .requires(dev.groundburst.content.chem.GBChemicals.DEUTERIUM_CANISTER).requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.FISSION_CORE), has(dev.groundburst.content.assembly.GBAssembly.FISSION_CORE))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.NEUTRON_CORE, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.TACTICAL_FISSION_CORE).requires(dev.groundburst.content.chem.GBChemicals.TRITIUM_CANISTER)
                .requires(dev.groundburst.content.chem.GBChemicals.TRITIUM_CANISTER).requires(GBParts.TUNGSTEN_CARBIDE_INGOT)
                .requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.TACTICAL_FISSION_CORE),
                        has(dev.groundburst.content.assembly.GBAssembly.TACTICAL_FISSION_CORE))
                .save(output);
        // Phase 11 (DESIGN.md §16.3): antimatter in a Penning trap, its superconducting magnets and a quantum circuit to hold it.
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.ANTIMATTER_TRAP, 1)
                .requires(dev.groundburst.content.nuclear.GBNuclear.ANTIMATTER_CELL, 4).requires(GBParts.SUPERCONDUCTING_COIL, 2)
                .requires(GBParts.QUANTUM_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.nuclear.GBNuclear.ANTIMATTER_CELL), has(dev.groundburst.content.nuclear.GBNuclear.ANTIMATTER_CELL))
                .save(output);
        // A singularity seed held by four containment cells round a quantum circuit's field (DESIGN.md §16.2).
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.SINGULARITY_CORE, 1)
                .requires(dev.groundburst.content.nuclear.GBNuclear.SINGULARITY_SEED).requires(dev.groundburst.content.nuclear.GBNuclear.CONTAINMENT_CELL, 4)
                .requires(GBParts.QUANTUM_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.nuclear.GBNuclear.SINGULARITY_SEED), has(dev.groundburst.content.nuclear.GBNuclear.SINGULARITY_SEED))
                .save(output);
        // A Tesla storm generator (DESIGN.md §16.14): capacitor banks, two superconducting coils, a quantum circuit to fire them
        // (after the user's look: a military circuit at first).
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.STORM_CORE, 1)
                .requires(GBParts.CAPACITOR, 4).requires(GBParts.SUPERCONDUCTING_COIL, 2).requires(GBParts.QUANTUM_CIRCUIT)
                .requires(GBTags.commonItem("plates/copper")).requires(GBTags.commonItem("plates/copper"))
                .unlockedBy(getHasName(GBParts.SUPERCONDUCTING_COIL), has(GBParts.SUPERCONDUCTING_COIL))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.DIRTY_PAYLOAD, 1)
                .requires(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE).requires(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE)
                .requires(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE).requires(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE)
                .requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE).requires(GBParts.LEAD_PLATE)
                .unlockedBy(getHasName(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE), has(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.FISSION_CORE, 1)
                .requires(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_INGOT).requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE)
                .requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE).requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE)
                .requires(dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT).requires(GBParts.LEAD_PLATE)
                .requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_INGOT), has(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_INGOT))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.GAS_DISPENSER, 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE), has(dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.SMOKE_DISPENSER, 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.BASIC_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE), has(dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.CRYO_DISPENSER, 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE), has(dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.EMP_DISPENSER, 1)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE)
                .requires(dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE), has(dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.TOXIC_WASTE_TANK, 1)
                .requires(GBChemicals.TOXIN_CANISTER).requires(GBChemicals.TOXIN_CANISTER)
                .requires(GBChemicals.TOXIN_CANISTER).requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBChemicals.TOXIN_CANISTER), has(GBChemicals.TOXIN_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.CHEMICAL_AGENT_CANISTER, 1)
                .requires(GBChemicals.TOXIN_CANISTER).requires(GBChemicals.TOXIN_CANISTER)
                .requires(GBChemicals.CHLORINE_CANISTER).requires(GBChemicals.CHLORINE_CANISTER)
                .requires(GBParts.PRESSURE_VESSEL).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(GBChemicals.TOXIN_CANISTER), has(GBChemicals.TOXIN_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.LIGHT_CASING, 1)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.STEEL_ROD)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.MEDIUM_CASING, 1)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.STEEL_ROD).requires(GBParts.STEEL_ROD)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.HEAVY_CASING, 1)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBParts.STEEL_ROD).requires(GBParts.STEEL_ROD)
                .unlockedBy(getHasName(GBParts.DENSE_STEEL_PLATE), has(GBParts.DENSE_STEEL_PLATE)).save(output);
        // Phase 8: a heavy casing lined with lead, for the nuclear cores (class XL).
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.STRATEGIC_CASING, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.HEAVY_CASING).requires(GBParts.LEAD_PLATE)
                .requires(GBParts.LEAD_PLATE).requires(GBParts.LEAD_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.DENSE_STEEL_PLATE)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.HEAVY_CASING), has(dev.groundburst.content.assembly.GBAssembly.HEAVY_CASING))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.IMPACT_FUZE, 2)
                .requires(GBTags.commonItem("plates/steel")).requires(Items.GUNPOWDER)
                .requires(Items.STONE_PRESSURE_PLATE)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.BAROMETRIC_FUZE, 2)
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.ADVANCED_CIRCUIT)
                .requires(GBParts.COPPER_PLATE)
                .unlockedBy(getHasName(GBParts.ADVANCED_CIRCUIT), has(GBParts.ADVANCED_CIRCUIT)).save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.assembly.GBAssembly.ASSEMBLY_BENCH.item(), 1)
                .define('P', GBTags.commonItem("plates/steel")).define('S', GBParts.SERVO).define('W', Items.CRAFTING_TABLE).define('A', Items.ANVIL)
                .define('C', GBParts.BASIC_CIRCUIT).define('T', Items.CHEST)
                .pattern("PPP").pattern("SWA").pattern("CTC")
                .unlockedBy(getHasName(GBParts.SERVO), has(GBParts.SERVO)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE.get(), 2)
                .requires(GBChemicals.LIQUID_NITROGEN_CANISTER).requires(GBTags.commonItem("plates/steel")).requires(Items.IRON_NUGGET)
                .requires(Items.IRON_NUGGET).requires(Items.SNOWBALL)
                .unlockedBy(getHasName(GBChemicals.LIQUID_NITROGEN_CANISTER), has(GBChemicals.LIQUID_NITROGEN_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE.get(), 2)
                .requires(GBTags.commonItem("wires/copper")).requires(GBTags.commonItem("wires/copper")).requires(GBTags.commonItem("wires/copper"))
                .requires(GBParts.BASIC_CIRCUIT).requires(Items.REDSTONE).requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBParts.BASIC_CIRCUIT), has(GBParts.BASIC_CIRCUIT)).save(output);
        // Launching (DESIGN.md §17, §26.7): the structures' blocks, the controllers, the Basic Missile.
        var l = new Object() {
            final net.minecraft.world.level.ItemLike pad = dev.groundburst.content.launch.GBLaunch.LAUNCH_PAD.item();
            final net.minecraft.world.level.ItemLike frame = dev.groundburst.content.launch.GBLaunch.GANTRY_FRAME.item();
            final net.minecraft.world.level.ItemLike arm = dev.groundburst.content.launch.GBLaunch.GANTRY_ARM.item();
            final net.minecraft.world.level.ItemLike mast = dev.groundburst.content.launch.GBLaunch.RADAR_MAST.item();
            final net.minecraft.world.level.ItemLike dish = dev.groundburst.content.launch.GBLaunch.RADAR_DISH.item();
            final net.minecraft.world.level.ItemLike antenna = dev.groundburst.content.launch.GBLaunch.ANTENNA.item();
            final net.minecraft.world.level.ItemLike silo = dev.groundburst.content.launch.GBLaunch.SILO_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike computer = dev.groundburst.content.launch.GBLaunch.COMPUTER_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike complex = dev.groundburst.content.launch.GBLaunch.COMPLEX_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike deflector = dev.groundburst.content.launch.GBLaunch.FLAME_DEFLECTOR.item();
        };
        shapeless(RecipeCategory.BUILDING_BLOCKS, l.pad, 4)
                .requires(Items.CONCRETE.gray()).requires(Items.CONCRETE.gray()).requires(Items.CONCRETE.gray()).requires(Items.CONCRETE.gray())
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel")).requires(Tags.Items.DYES_YELLOW)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, l.frame, 4)
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("plates/steel")).requires(Tags.Items.DYES_RED)
                .unlockedBy(getHasName(GBParts.STEEL_ROD), has(GBParts.STEEL_ROD)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, l.arm, 1)
                .requires(l.frame).requires(l.frame).requires(GBParts.SERVO).requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(l.frame), has(l.frame)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, l.mast, 2)
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBParts.STEEL_ROD), has(GBParts.STEEL_ROD)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, l.dish, 1)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBParts.SENSOR_ARRAY).requires(GBParts.SERVO).requires(GBTags.commonItem("wires/copper"))
                .unlockedBy(getHasName(GBParts.SENSOR_ARRAY), has(GBParts.SENSOR_ARRAY)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, l.antenna, 1)
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("wires/copper"))
                .requires(GBTags.commonItem("wires/copper")).requires(GBParts.BASIC_CIRCUIT)
                .unlockedBy(getHasName(GBParts.BASIC_CIRCUIT), has(GBParts.BASIC_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, l.silo, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.CRT_DISPLAY).requires(GBParts.ADVANCED_CIRCUIT).requires(Items.LEVER)
                .unlockedBy(getHasName(GBParts.ADVANCED_CIRCUIT), has(GBParts.ADVANCED_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, l.complex, 1)
                .requires(l.silo).requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.MILITARY_CIRCUIT)
                .requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.LEAD_PLATE)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        // The Launch Control Station (DESIGN.md §17.12, §26): its controller and its Launch Codes.
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.CRT_DISPLAY).requires(GBParts.CRT_DISPLAY)
                .requires(GBParts.CRT_DISPLAY).requires(GBParts.MILITARY_CIRCUIT).requires(l.antenna).requires(Items.LEVER).requires(Items.LEVER)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.launch.GBLaunch.LAUNCH_CODES.get(), 2)
                .requires(Items.PAPER).requires(Tags.Items.NUGGETS_GOLD).requires(Tags.Items.DYES_RED)
                .unlockedBy(getHasName(dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.item()),
                        has(dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.item())).save(output);
        // The Missile Silo (slice 9b, DESIGN.md §17.10, §26.7): its concrete, its hatch and its controller.
        var m = new Object() {
            final net.minecraft.world.level.ItemLike concrete = dev.groundburst.content.launch.GBLaunch.REINFORCED_CONCRETE.item();
            final net.minecraft.world.level.ItemLike hazard = dev.groundburst.content.launch.GBLaunch.HAZARD_CONCRETE.item();
            final net.minecraft.world.level.ItemLike hatch = dev.groundburst.content.launch.GBLaunch.SILO_HATCH.item();
            final net.minecraft.world.level.ItemLike silo = dev.groundburst.content.launch.GBLaunch.MISSILE_SILO_CONTROLLER.item();
        };
        shaped(RecipeCategory.BUILDING_BLOCKS, m.concrete, 8)
                .define('C', Tags.Items.CONCRETES).define('R', GBTags.commonItem("rods/steel"))
                .pattern("CCC").pattern("CRC").pattern("CCC")
                .unlockedBy(getHasName(GBParts.STEEL_ROD), has(GBParts.STEEL_ROD)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, m.hazard, 4)
                .requires(m.concrete).requires(m.concrete).requires(m.concrete).requires(m.concrete)
                .requires(Tags.Items.DYES_YELLOW).requires(Tags.Items.DYES_BLACK)
                .unlockedBy(getHasName(m.concrete), has(m.concrete)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, m.hatch, 1)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.SERVO).requires(GBParts.SERVO).requires(m.hazard)
                .unlockedBy(getHasName(GBParts.DENSE_STEEL_PLATE), has(GBParts.DENSE_STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.REDSTONE, m.silo, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.CRT_DISPLAY)
                .requires(m.concrete).requires(m.concrete).requires(Items.LEVER)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        // The Cruise Missile Launcher (slice 9c, DESIGN.md §17.11): its armored Launch Boxes (dense plate, a frangible cover's
        // steel, a servo for the cover) and its controller (the silo's, with a servo to raise the boxes).
        shapeless(RecipeCategory.BUILDING_BLOCKS, dev.groundburst.content.launch.GBLaunch.LAUNCH_BOX.item(), 1)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel")).requires(GBParts.SERVO)
                .unlockedBy(getHasName(GBParts.DENSE_STEEL_PLATE), has(GBParts.DENSE_STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.launch.GBLaunch.CRUISE_CONTROLLER.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.CRT_DISPLAY).requires(GBParts.ADVANCED_CIRCUIT)
                .requires(GBParts.PRESSURE_VESSEL).requires(GBParts.ELECTRIC_MOTOR).requires(Items.LEVER)
                .unlockedBy(getHasName(GBParts.ADVANCED_CIRCUIT), has(GBParts.ADVANCED_CIRCUIT)).save(output);
        // The SCUD Storm (DESIGN.md §19.5): adobe (mud brick and sand bound with straw), the tank of toxin, the SCUDs'
        // erectors (hydraulics and a pump), and its controller (the toxin's canister for its placard).
        var sw = new Object() {
            final net.minecraft.world.level.ItemLike adobe = dev.groundburst.content.superweapon.GBSuperweapons.ADOBE_WALL.item();
            final net.minecraft.world.level.ItemLike tank = dev.groundburst.content.superweapon.GBSuperweapons.TOXIN_TANK.item();
            final net.minecraft.world.level.ItemLike erector = dev.groundburst.content.superweapon.GBSuperweapons.SCUD_ERECTOR.item();
            final net.minecraft.world.level.ItemLike scudStorm = dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike fieldstone = dev.groundburst.content.superweapon.GBSuperweapons.FIELDSTONE_WALL.item();
            final net.minecraft.world.level.ItemLike missileErector = dev.groundburst.content.superweapon.GBSuperweapons.MISSILE_ERECTOR.item();
            final net.minecraft.world.level.ItemLike nuke = dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike armsDealer = dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike warFactory = dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_CONTROLLER.item();
        };
        shapeless(RecipeCategory.BUILDING_BLOCKS, sw.adobe, 4)
                .requires(Items.MUD_BRICKS).requires(Items.MUD_BRICKS).requires(Tags.Items.SANDS).requires(Tags.Items.SANDS).requires(Items.WHEAT)
                .unlockedBy(getHasName(Items.MUD_BRICKS), has(Items.MUD_BRICKS)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, sw.tank, 2)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.PRESSURE_VESSEL).requires(Tags.Items.DYES_GREEN)
                .unlockedBy(getHasName(GBParts.PRESSURE_VESSEL), has(GBParts.PRESSURE_VESSEL)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, sw.erector, 1)
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.PRESSURE_VESSEL).requires(GBParts.ELECTRIC_MOTOR)
                .unlockedBy(getHasName(GBParts.ELECTRIC_MOTOR), has(GBParts.ELECTRIC_MOTOR)).save(output);
        shapeless(RecipeCategory.REDSTONE, sw.scudStorm, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.MILITARY_CIRCUIT)
                .requires(GBParts.CRT_DISPLAY).requires(GBParts.SENSOR_ARRAY).requires(dev.groundburst.content.assembly.GBAssembly.CHEMICAL_AGENT_CANISTER)
                .requires(Items.LEVER)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        // The Nuclear Missile Silo (DESIGN.md §19.5): dressed fieldstone (stone set in mortar), the missile's erector (a
        // strongback on two rams), and its controller (lead-lined: it looks after a nuclear missile).
        shaped(RecipeCategory.BUILDING_BLOCKS, sw.fieldstone, 8)
                .define('C', Items.COBBLESTONE).define('S', Items.STONE_BRICKS).define('M', Items.MOSSY_COBBLESTONE)
                .pattern("CSC").pattern("SMS").pattern("CSC")
                .unlockedBy(getHasName(Items.STONE_BRICKS), has(Items.STONE_BRICKS)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, sw.missileErector, 1)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.PRESSURE_VESSEL)
                .requires(GBParts.PRESSURE_VESSEL).requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.ELECTRIC_MOTOR).requires(Tags.Items.DYES_GREEN)
                .unlockedBy(getHasName(GBParts.PRESSURE_VESSEL), has(GBParts.PRESSURE_VESSEL)).save(output);
        shapeless(RecipeCategory.REDSTONE, sw.nuke, 1)
                .requires(GBMultiblocks.LEAD_LINED_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.MILITARY_CIRCUIT)
                .requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.CRT_DISPLAY).requires(GBParts.SENSOR_ARRAY).requires(Tags.Items.DYES_RED)
                .requires(Items.LEVER)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        // The superweapons' ammunition factories (DESIGN.md §19.5): the Arms Dealer a Medium Missile Assembler's rung
        // (motors, an advanced circuit), the War Factory a Strategic one's (a military circuit, a sensor array).
        shapeless(RecipeCategory.REDSTONE, sw.armsDealer, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.ADVANCED_CIRCUIT).requires(GBParts.ELECTRIC_MOTOR)
                .requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.SERVO).requires(GBTags.commonItem("plates/steel")).requires(sw.adobe)
                .unlockedBy(getHasName(GBParts.ADVANCED_CIRCUIT), has(GBParts.ADVANCED_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, sw.warFactory, 1)
                .requires(GBMultiblocks.LEAD_LINED_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.SENSOR_ARRAY)
                .requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.PRESSURE_VESSEL).requires(sw.fieldstone)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        // Whole weapons (slice 8c, DESIGN.md §15.2): the ICBM Assembly Hall, its rails, and the parts only it uses.
        var s = new Object() {
            final net.minecraft.world.level.ItemLike rail = dev.groundburst.content.strategic.GBStrategic.CRANE_RAIL.item();
            final net.minecraft.world.level.ItemLike hall = dev.groundburst.content.strategic.GBStrategic.HALL_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike secondary = dev.groundburst.content.strategic.GBStrategic.FUSION_SECONDARY.get();
            final net.minecraft.world.level.ItemLike jacket = dev.groundburst.content.strategic.GBStrategic.COBALT_JACKET.get();
            final net.minecraft.world.level.ItemLike booster = dev.groundburst.content.strategic.GBStrategic.INTERCONTINENTAL_BOOSTER.get();
            final net.minecraft.world.level.ItemLike tertiary = dev.groundburst.content.strategic.GBStrategic.TERTIARY_STAGE.get();
            final net.minecraft.world.level.ItemLike twin = dev.groundburst.content.strategic.GBStrategic.TWIN_BOOSTED_CORE.get();
        };
        shapeless(RecipeCategory.BUILDING_BLOCKS, s.rail, 4)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel")).requires(Tags.Items.DYES_YELLOW)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        // The missile assemblers' ladder (DESIGN.md §10.4), each a rung harder than the last, the hall at the top: the
        // Light's servos and basic circuits (the early missile age), the Medium's electric motors and an advanced circuit,
        // the Heavy's hydraulics (pressure vessels), tungsten and a military circuit, the Strategic's lead-lined casing, two
        // military circuits and a sensor array; the hall's superconducting coils and three military circuits.
        var a = new Object() {
            final net.minecraft.world.level.ItemLike light = dev.groundburst.content.assembler.GBAssemblers.LIGHT_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike medium = dev.groundburst.content.assembler.GBAssemblers.MEDIUM_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike heavy = dev.groundburst.content.assembler.GBAssemblers.HEAVY_CONTROLLER.item();
            final net.minecraft.world.level.ItemLike strategic = dev.groundburst.content.assembler.GBAssemblers.STRATEGIC_CONTROLLER.item();
        };
        shapeless(RecipeCategory.REDSTONE, a.light, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.BASIC_CIRCUIT).requires(GBParts.BASIC_CIRCUIT)
                .requires(GBParts.SERVO).requires(GBParts.SERVO).requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GBParts.SERVO), has(GBParts.SERVO)).save(output);
        shapeless(RecipeCategory.REDSTONE, a.medium, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.ADVANCED_CIRCUIT).requires(GBParts.BASIC_CIRCUIT)
                .requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.SERVO).requires(GBParts.CRT_DISPLAY)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .unlockedBy(getHasName(GBParts.ADVANCED_CIRCUIT), has(GBParts.ADVANCED_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, a.heavy, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.ADVANCED_CIRCUIT)
                .requires(GBParts.PRESSURE_VESSEL).requires(GBParts.PRESSURE_VESSEL).requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.ELECTRIC_MOTOR)
                .requires(GBParts.TUNGSTEN_CARBIDE_INGOT).requires(GBParts.DENSE_STEEL_PLATE)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, a.strategic, 1)
                .requires(GBMultiblocks.LEAD_LINED_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.MILITARY_CIRCUIT)
                .requires(GBParts.SENSOR_ARRAY).requires(GBParts.PRESSURE_VESSEL).requires(GBParts.ELECTRIC_MOTOR).requires(GBParts.ELECTRIC_MOTOR)
                .requires(GBParts.TUNGSTEN_PLATE).requires(GBParts.TUNGSTEN_PLATE)
                .unlockedBy(getHasName(GBParts.SENSOR_ARRAY), has(GBParts.SENSOR_ARRAY)).save(output);
        shapeless(RecipeCategory.REDSTONE, s.hall, 1)
                .requires(GBMultiblocks.LEAD_LINED_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.MILITARY_CIRCUIT)
                .requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.SUPERCONDUCTING_COIL).requires(GBParts.SUPERCONDUCTING_COIL)
                .requires(GBParts.SENSOR_ARRAY).requires(GBParts.TUNGSTEN_PLATE).requires(GBParts.CRT_DISPLAY)
                .unlockedBy(getHasName(GBParts.SUPERCONDUCTING_COIL), has(GBParts.SUPERCONDUCTING_COIL)).save(output);
        // Missile defence (DESIGN.md §18.2): the Interceptor Battery, and its missiles two at a time (a tactical body, a
        // motor, guidance, a sensor array for its seeker and proximity fuze, an HE charge for its warhead).
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.defence.GBDefence.BATTERY.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.SERVO).requires(GBParts.SERVO)
                .requires(GBParts.STEEL_PLATE).requires(GBParts.STEEL_PLATE).requires(GBParts.STEEL_PLATE).requires(GBParts.STEEL_PLATE)
                .requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.SENSOR_ARRAY)
                .unlockedBy(getHasName(GBParts.SENSOR_ARRAY), has(GBParts.SENSOR_ARRAY)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.defence.GBDefence.INTERCEPTOR_MISSILE.get(), 2)
                .requires(dev.groundburst.content.assembly.GBAssembly.TACTICAL_BODY.get()).requires(dev.groundburst.content.assembly.GBAssembly.SRM_I.get())
                .requires(dev.groundburst.content.assembly.GBAssembly.INERTIAL_GUIDANCE.get()).requires(GBParts.SENSOR_ARRAY)
                .requires(dev.groundburst.content.assembly.GBAssembly.HE_CHARGE)
                .unlockedBy(getHasName(GBParts.SENSOR_ARRAY), has(GBParts.SENSOR_ARRAY)).save(output);
        // The base alarm system (DESIGN.md §18.6). The receiver: a CRT display and an advanced circuit to read the radar
        // network; the panel: buttons on a circuit; the monitor: a Geiger counter's tube on a circuit; the siren: an electric
        // motor turning a steel horn, painted yellow; the klaxon: a note block's horn on a circuit; the bell: a bell on a
        // circuit; the beacon: a redstone lamp under glass on a motor; the emergency light: a redstone lamp in an iron cage;
        // the display: a CRT display on a circuit; the relay: redstone on a circuit.
        var al = new Object() {
            final net.minecraft.world.level.ItemLike plate = GBParts.STEEL_PLATE.get();
            final net.minecraft.world.level.ItemLike basic = GBParts.BASIC_CIRCUIT.get();
            final net.minecraft.world.level.ItemLike wire = GBParts.COPPER_WIRE.get();
        };
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.RECEIVER.item(), 1)
                .requires(al.plate).requires(al.plate).requires(GBParts.ADVANCED_CIRCUIT).requires(GBParts.CRT_DISPLAY).requires(al.wire)
                .requires(Items.REDSTONE).unlockedBy(getHasName(GBParts.CRT_DISPLAY), has(GBParts.CRT_DISPLAY)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.PANEL.item(), 1)
                .requires(al.plate).requires(al.basic).requires(Items.STONE_BUTTON).requires(Items.STONE_BUTTON).requires(Items.REDSTONE)
                .unlockedBy(getHasName(GBParts.BASIC_CIRCUIT), has(GBParts.BASIC_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.MONITOR.item(), 1)
                .requires(al.plate).requires(al.plate).requires(al.basic).requires(dev.groundburst.content.radiation.RadiationItems.GEIGER_COUNTER.get())
                .requires(Items.GLASS_PANE).unlockedBy(getHasName(dev.groundburst.content.radiation.RadiationItems.GEIGER_COUNTER.get()),
                        has(dev.groundburst.content.radiation.RadiationItems.GEIGER_COUNTER.get())).save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.SIREN.item(), 1)
                .define('P', GBTags.commonItem("plates/steel")).define('M', GBParts.ELECTRIC_MOTOR).define('W', al.wire)
                .define('Y', Tags.Items.DYES_YELLOW).define('C', al.basic)
                .pattern("PYP").pattern("PMP").pattern("WCW")
                .unlockedBy(getHasName(GBParts.ELECTRIC_MOTOR), has(GBParts.ELECTRIC_MOTOR)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.KLAXON.item(), 1)
                .requires(al.plate).requires(al.basic).requires(Items.NOTE_BLOCK).requires(al.wire)
                .unlockedBy(getHasName(GBParts.BASIC_CIRCUIT), has(GBParts.BASIC_CIRCUIT)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.BELL.item(), 1)
                .requires(Items.BELL).requires(al.basic).requires(al.wire)
                .unlockedBy(getHasName(GBParts.BASIC_CIRCUIT), has(GBParts.BASIC_CIRCUIT)).save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.BEACON.item(), 2)
                .define('G', Items.GLASS).define('L', Items.REDSTONE_LAMP).define('M', GBParts.ELECTRIC_MOTOR)
                .define('P', GBTags.commonItem("plates/steel")).define('R', Tags.Items.DYES_RED)
                .pattern("RGR").pattern("GLG").pattern("PMP")
                .unlockedBy(getHasName(GBParts.ELECTRIC_MOTOR), has(GBParts.ELECTRIC_MOTOR)).save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.LIGHT.item(), 4)
                .define('I', Items.IRON_BARS).define('L', Items.REDSTONE_LAMP).define('P', GBTags.commonItem("plates/steel"))
                .pattern(" I ").pattern("ILI").pattern(" P ")
                .unlockedBy(getHasName(Items.REDSTONE_LAMP), has(Items.REDSTONE_LAMP)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.DISPLAY.item(), 1)
                .requires(al.plate).requires(al.basic).requires(GBParts.CRT_DISPLAY)
                .unlockedBy(getHasName(GBParts.CRT_DISPLAY), has(GBParts.CRT_DISPLAY)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.alarm.GBAlarms.RELAY.item(), 1)
                .requires(al.plate).requires(al.basic).requires(Items.REDSTONE).requires(Items.REDSTONE).requires(al.wire)
                .unlockedBy(getHasName(GBParts.BASIC_CIRCUIT), has(GBParts.BASIC_CIRCUIT)).save(output);
        // The Interceptor Complex (DESIGN.md §18.5), dearer after the user's look (2026-10-31): its controller built round a
        // whole Interceptor Battery, with two military circuits and two CRT displays for its fire control and a
        // superconducting coil for its radar's power; a Launch Cell from a machine frame boxed in dense steel with a
        // tungsten blast plate and a servo for its hatches; a Phased Array Panel from four sensor arrays on a military
        // circuit in dense steel.
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.defence.GBDefence.COMPLEX_CONTROLLER.item(), 1)
                .requires(dev.groundburst.content.defence.GBDefence.BATTERY.item()).requires(GBMultiblocks.MACHINE_CASING.item())
                .requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.CRT_DISPLAY).requires(GBParts.CRT_DISPLAY)
                .requires(GBParts.SUPERCONDUCTING_COIL).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .unlockedBy(getHasName(dev.groundburst.content.defence.GBDefence.BATTERY.item()),
                        has(dev.groundburst.content.defence.GBDefence.BATTERY.item())).save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, dev.groundburst.content.defence.GBDefence.LAUNCH_CELL.item(), 1)
                .define('D', GBParts.DENSE_STEEL_PLATE).define('F', GBParts.MACHINE_FRAME).define('S', GBParts.SERVO)
                .define('T', GBParts.TUNGSTEN_PLATE)
                .pattern("DSD").pattern("DFD").pattern("DTD")
                .unlockedBy(getHasName(dev.groundburst.content.defence.GBDefence.BATTERY.item()),
                        has(dev.groundburst.content.defence.GBDefence.BATTERY.item())).save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, dev.groundburst.content.defence.GBDefence.PHASED_ARRAY.item(), 1)
                .define('D', GBParts.DENSE_STEEL_PLATE).define('A', GBParts.SENSOR_ARRAY).define('M', GBParts.MILITARY_CIRCUIT)
                .pattern("DAD").pattern("AMA").pattern("DAD")
                .unlockedBy(getHasName(GBParts.SENSOR_ARRAY), has(GBParts.SENSOR_ARRAY)).save(output);
        // The Special Weapons Assembler (DESIGN.md §16.15), a rung over the hall: two quantum circuits and a containment
        // emitter to hold an exotic core's field while it is built in.
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.strategic.GBStrategic.SPECIAL_CONTROLLER.item(), 1)
                .requires(GBMultiblocks.LEAD_LINED_CASING.item()).requires(GBParts.QUANTUM_CIRCUIT).requires(GBParts.QUANTUM_CIRCUIT)
                .requires(GBMultiblocks.CONTAINMENT_EMITTER.item()).requires(GBParts.SUPERCONDUCTING_COIL).requires(GBParts.SUPERCONDUCTING_COIL)
                .requires(GBParts.SENSOR_ARRAY).requires(GBParts.TUNGSTEN_PLATE).requires(GBParts.CRT_DISPLAY)
                .unlockedBy(getHasName(GBParts.QUANTUM_CIRCUIT), has(GBParts.QUANTUM_CIRCUIT)).save(output);
        shapeless(RecipeCategory.COMBAT, s.secondary, 1)
                .requires(dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET).requires(dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET)
                .requires(dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET).requires(dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET)
                .requires(dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT).requires(dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT)
                .requires(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET).requires(GBParts.LEAD_PLATE)
                .requires(dev.groundburst.content.chem.GBChemicals.PLASTIC_SHEET)
                .unlockedBy(getHasName(dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET),
                        has(dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET)).save(output);
        shapeless(RecipeCategory.COMBAT, s.jacket, 1)
                .requires(GBParts.COBALT_INGOT).requires(GBParts.COBALT_INGOT).requires(GBParts.COBALT_INGOT).requires(GBParts.COBALT_INGOT)
                .requires(GBParts.DENSE_STEEL_PLATE)
                .unlockedBy(getHasName(GBParts.COBALT_INGOT), has(GBParts.COBALT_INGOT)).save(output);
        shapeless(RecipeCategory.COMBAT, s.booster, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.LIQUID_FUEL_STAGE).requires(dev.groundburst.content.assembly.GBAssembly.LIQUID_FUEL_STAGE)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.TUNGSTEN_CARBIDE_INGOT).requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.LIQUID_FUEL_STAGE),
                        has(dev.groundburst.content.assembly.GBAssembly.LIQUID_FUEL_STAGE)).save(output);
        // The Tsar's third stage (slice 8d): two fusion secondaries in a lead radiation channel, and its own firing circuits.
        shapeless(RecipeCategory.COMBAT, s.tertiary, 1)
                .requires(s.secondary).requires(s.secondary).requires(GBParts.LEAD_PLATE).requires(GBParts.LEAD_PLATE)
                .requires(GBParts.LEAD_PLATE).requires(GBParts.LEAD_PLATE).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.MILITARY_CIRCUIT)
                .requires(GBParts.DENSE_STEEL_PLATE)
                .unlockedBy(getHasName(s.secondary), has(s.secondary)).save(output);
        // The Tsar's primary (after 9b): two boosted cores, fresh tritium for both, one firing circuit to set them off together.
        shapeless(RecipeCategory.COMBAT, s.twin, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.BOOSTED_FISSION_CORE).requires(dev.groundburst.content.assembly.GBAssembly.BOOSTED_FISSION_CORE)
                .requires(dev.groundburst.content.chem.GBChemicals.TRITIUM_CANISTER).requires(dev.groundburst.content.chem.GBChemicals.TRITIUM_CANISTER)
                .requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.DENSE_STEEL_PLATE)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.BOOSTED_FISSION_CORE),
                        has(dev.groundburst.content.assembly.GBAssembly.BOOSTED_FISSION_CORE)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, l.deflector, 1)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.TUNGSTEN_CARBIDE_INGOT).requires(Items.CONCRETE.gray()).requires(Items.CONCRETE.gray())
                .unlockedBy(getHasName(GBParts.DENSE_STEEL_PLATE), has(GBParts.DENSE_STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.REDSTONE, l.computer, 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.CRT_DISPLAY).requires(GBParts.CRT_DISPLAY)
                .requires(GBParts.ADVANCED_CIRCUIT).requires(GBParts.SENSOR_ARRAY).requires(Items.LEVER)
                .unlockedBy(getHasName(GBParts.ADVANCED_CIRCUIT), has(GBParts.ADVANCED_CIRCUIT)).save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, dev.groundburst.content.launch.GBLaunch.LARGE_RADAR_DISH.item(), 1)
                .requires(l.dish).requires(l.dish).requires(l.dish).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(l.dish), has(l.dish)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.launch.GBLaunch.RADAR_CONTROLLER.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.CRT_DISPLAY).requires(GBParts.ADVANCED_CIRCUIT)
                .requires(GBParts.ADVANCED_CIRCUIT).requires(GBParts.SENSOR_ARRAY).requires(GBParts.SENSOR_ARRAY)
                .unlockedBy(getHasName(GBParts.SENSOR_ARRAY), has(GBParts.SENSOR_ARRAY)).save(output);
        // Missile parts (Phase 7 slice 7b): bodies, motors, guidance, the hardened module.
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.TACTICAL_BODY, 1)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.MEDIUM_BODY, 1)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.HEAVY_BODY, 1)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GBParts.DENSE_STEEL_PLATE), has(GBParts.DENSE_STEEL_PLATE)).save(output);
        // Phase 8: the two-stage Strategic body (a Heavy body stretched, with an interstage and its own avionics bay).
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.STRATEGIC_BODY, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.HEAVY_BODY).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.DENSE_STEEL_PLATE).requires(GBParts.LEAD_PLATE)
                .requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.HEAVY_BODY), has(dev.groundburst.content.assembly.GBAssembly.HEAVY_BODY))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.SRM_I, 1)
                .requires(GBChemicals.SOLID_PROPELLANT).requires(GBChemicals.SOLID_PROPELLANT)
                .requires(GBChemicals.SOLID_PROPELLANT).requires(GBTags.commonItem("plates/steel"))
                .requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GBChemicals.SOLID_PROPELLANT), has(GBChemicals.SOLID_PROPELLANT)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.SRM_II, 1)
                .requires(GBChemicals.SOLID_PROPELLANT).requires(GBChemicals.SOLID_PROPELLANT)
                .requires(GBChemicals.SOLID_PROPELLANT).requires(GBChemicals.SOLID_PROPELLANT)
                .requires(GBChemicals.SOLID_PROPELLANT).requires(GBChemicals.SOLID_PROPELLANT)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel"))
                .requires(GBParts.TUNGSTEN_PLATE)
                .unlockedBy(getHasName(GBChemicals.SOLID_PROPELLANT), has(GBChemicals.SOLID_PROPELLANT)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.LIQUID_FUEL_STAGE, 1)
                .requires(GBChemicals.LIQUID_ROCKET_FUEL_CANISTER).requires(GBChemicals.LIQUID_ROCKET_FUEL_CANISTER)
                .requires(GBChemicals.LIQUID_ROCKET_FUEL_CANISTER).requires(GBChemicals.LIQUID_OXYGEN_CANISTER)
                .requires(GBChemicals.LIQUID_OXYGEN_CANISTER).requires(GBParts.DENSE_STEEL_PLATE)
                .requires(GBParts.PRESSURE_VESSEL).requires(GBParts.ADVANCED_CIRCUIT)
                .unlockedBy(getHasName(GBChemicals.LIQUID_ROCKET_FUEL_CANISTER), has(GBChemicals.LIQUID_ROCKET_FUEL_CANISTER)).save(output);
        // Slice 9c (DESIGN.md §17.11): the Cruise Engine, a small turbojet (a pressure vessel for its combustor, tungsten
        // for its turbine, an advanced circuit to run it, jet fuel) and its launch booster (a Solid Rocket Motor I).
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.CRUISE_ENGINE, 1)
                .requires(dev.groundburst.content.assembly.GBAssembly.SRM_I).requires(GBChemicals.SYNTHETIC_FUEL_CANISTER)
                .requires(GBChemicals.SYNTHETIC_FUEL_CANISTER).requires(GBParts.PRESSURE_VESSEL).requires(GBParts.TUNGSTEN_PLATE)
                .requires(GBParts.ADVANCED_CIRCUIT).requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(dev.groundburst.content.assembly.GBAssembly.SRM_I), has(dev.groundburst.content.assembly.GBAssembly.SRM_I))
                .save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.INERTIAL_GUIDANCE, 1)
                .requires(GBParts.ADVANCED_CIRCUIT).requires(GBParts.SERVO)
                .requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBParts.ADVANCED_CIRCUIT), has(GBParts.ADVANCED_CIRCUIT)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.GPS_GUIDANCE, 1)
                .requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.SENSOR_ARRAY).requires(GBParts.COPPER_WIRE)
                .requires(GBTags.commonItem("plates/steel"))
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.assembly.GBAssembly.HARDENED_ELECTRONICS, 1)
                .requires(GBParts.LEAD_PLATE).requires(GBParts.LEAD_PLATE).requires(GBParts.ADVANCED_CIRCUIT)
                .requires(GBParts.COPPER_WIRE)
                .unlockedBy(getHasName(GBParts.LEAD_PLATE), has(GBParts.LEAD_PLATE)).save(output);
        shapeless(RecipeCategory.TOOLS, dev.groundburst.content.ordnance.GBOrdnance.REMOTE_DETONATOR, 1)
                .requires(GBParts.BASIC_CIRCUIT).requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("rods/steel"))
                .requires(Tags.Items.DUSTS_REDSTONE).requires(Items.STONE_BUTTON)
                .unlockedBy(getHasName(GBParts.BASIC_CIRCUIT), has(GBParts.BASIC_CIRCUIT)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.MOLOTOV_COCKTAIL, 2)
                .requires(GBChemicals.SYNTHETIC_FUEL_CANISTER).requires(Items.GLASS_BOTTLE).requires(Items.GLASS_BOTTLE)
                .requires(Items.STRING).requires(Items.STRING)
                .unlockedBy(getHasName(GBChemicals.SYNTHETIC_FUEL_CANISTER), has(GBChemicals.SYNTHETIC_FUEL_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE, 2)
                .requires(GBParts.STEEL_PLATE).requires(GBTags.commonItem("dusts/saltpeter")).requires(GBTags.commonItem("dusts/saltpeter"))
                .requires(Items.CHARCOAL)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
    }

    /**
     * Radiation's instruments and medicine (DESIGN.md §21.8, §26.8): the Geiger Counter (a steel case, its dial's glass, the
     * probe's wire, a circuit and a battery), the Dosimeter Badge, the Lead Container (lead round a chest), Rad-Away (a
     * plastic bag of water, sugar and charcoal), Potassium Iodide (iodine from kelp, potassium from saltpeter) and the
     * Anti-Rad Injector (Rad-Away and iodide in a steel pen with its needle).
     */
    private void radiation() {
        var geiger = dev.groundburst.content.radiation.RadiationItems.GEIGER_COUNTER;
        shaped(RecipeCategory.TOOLS, geiger, 1)
                .define('S', GBParts.STEEL_PLATE).define('G', Items.GLASS_PANE).define('W', GBParts.COPPER_WIRE)
                .define('C', GBTags.BASIC_CIRCUITS).define('B', GBParts.BATTERY)
                .pattern("SGS").pattern("WCW").pattern("SBS")
                .unlockedBy(getHasName(GBParts.BATTERY), has(GBParts.BATTERY)).save(output);
        var badge = dev.groundburst.content.radiation.RadiationItems.DOSIMETER_BADGE;
        shapeless(RecipeCategory.TOOLS, badge, 1)
                .requires(Items.PAPER).requires(Items.GLASS_PANE).requires(Items.IRON_NUGGET)
                .unlockedBy(getHasName(Items.PAPER), has(Items.PAPER)).save(output);
        var lead = dev.groundburst.content.radiation.RadiationItems.LEAD_CONTAINER;
        shaped(RecipeCategory.TOOLS, lead, 1)
                .define('L', GBParts.LEAD_PLATE).define('C', Tags.BlockItems.CHESTS_WOODEN.item())
                .pattern("LLL").pattern("LCL").pattern(" L ")
                .unlockedBy(getHasName(GBParts.LEAD_PLATE), has(GBParts.LEAD_PLATE)).save(output);
        var plastic = dev.groundburst.content.chem.GBChemicals.PLASTIC_SHEET;
        var radAway = dev.groundburst.content.radiation.RadiationItems.RAD_AWAY;
        shapeless(RecipeCategory.FOOD, radAway, 2)
                .requires(plastic).requires(Items.WATER_BUCKET).requires(Items.SUGAR).requires(Items.CHARCOAL).requires(Items.CHARCOAL)
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        var iodide = dev.groundburst.content.radiation.RadiationItems.POTASSIUM_IODIDE;
        shapeless(RecipeCategory.FOOD, iodide, 4)
                .requires(Items.PAPER).requires(Items.DRIED_KELP).requires(Items.DRIED_KELP).requires(GBTags.commonItem("dusts/saltpeter"))
                .unlockedBy(getHasName(GBResources.SALTPETER_DUST), has(GBResources.SALTPETER_DUST)).save(output);
        var injector = dev.groundburst.content.radiation.RadiationItems.ANTI_RAD_INJECTOR;
        shapeless(RecipeCategory.FOOD, injector, 1)
                .requires(radAway).requires(iodide).requires(iodide).requires(GBParts.STEEL_PLATE).requires(Items.IRON_NUGGET)
                .unlockedBy(getHasName(radAway), has(radAway)).save(output);
    }

    /** The aftermath (slice 12b): charred wood makes planks, burns to charcoal, and logs square into wood. */
    private void aftermath() {
        var log = dev.groundburst.content.fallout.GBFallout.CHARRED_LOG.item();
        var wood = dev.groundburst.content.fallout.GBFallout.CHARRED_WOOD.item();
        var planks = dev.groundburst.content.fallout.GBFallout.CHARRED_PLANKS.item();
        shapeless(RecipeCategory.BUILDING_BLOCKS, planks, 4).requires(log)
                .unlockedBy(getHasName(log), has(log)).save(output, id("charred_planks_from_log"));
        shapeless(RecipeCategory.BUILDING_BLOCKS, planks, 4).requires(wood)
                .unlockedBy(getHasName(wood), has(wood)).save(output, id("charred_planks_from_wood"));
        shaped(RecipeCategory.BUILDING_BLOCKS, wood, 3).define('L', log).pattern("LL").pattern("LL")
                .unlockedBy(getHasName(log), has(log)).save(output);
        for (var burnt : java.util.List.of(log, wood)) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(burnt), RecipeCategory.MISC, CookingBookCategory.MISC, Items.CHARCOAL, 0.15f, 200,
                    SmeltingRecipe::new).unlockedBy(getHasName(burnt), has(burnt)).save(output, id("charcoal_from_" + getItemName(burnt)));
        }
    }

    /**
     * The Construction set (DESIGN.md §22.2): the stonecutter cuts a steel plate into sheeting, panels, grating, girders
     * and lattice, a rod into barbed wire, white concrete into radome panels; six rods make sixteen lengths of chain-link
     * as six iron make sixteen bars; eight round a water bucket rust; stairs and slabs as vanilla's, by hand or cut.
     */
    private void construction() {
        var plate = GBParts.STEEL_PLATE;
        var rod = GBParts.STEEL_ROD;
        cut(plate, dev.groundburst.content.construction.GBConstruction.CORRUGATED_STEEL.item(), 4);
        cut(plate, dev.groundburst.content.construction.GBConstruction.STEEL_PANEL.item(), 2);
        cut(plate, dev.groundburst.content.construction.GBConstruction.STEEL_GRATING.item(), 4);
        cut(plate, dev.groundburst.content.construction.GBConstruction.I_BEAM.item(), 2);
        cut(plate, dev.groundburst.content.construction.GBConstruction.LATTICE_FRAME.item(), 2);
        cut(rod, dev.groundburst.content.construction.GBConstruction.BARBED_WIRE.item(), 4);
        cut(Items.CONCRETE.white(), dev.groundburst.content.construction.GBConstruction.RADOME_PANEL.item(), 1);
        shaped(RecipeCategory.DECORATIONS, dev.groundburst.content.construction.GBConstruction.CHAIN_LINK_FENCE.item(), 16)
                .define('R', rod).pattern("RRR").pattern("RRR")
                .unlockedBy(getHasName(rod), has(rod)).save(output);
        rusted(dev.groundburst.content.construction.GBConstruction.CORRUGATED_STEEL.item(), dev.groundburst.content.construction.GBConstruction.RUSTED_CORRUGATED_STEEL.item());
        rusted(dev.groundburst.content.construction.GBConstruction.STEEL_PANEL.item(), dev.groundburst.content.construction.GBConstruction.RUSTED_STEEL_PANEL.item());
        stairsAndSlab(dev.groundburst.content.construction.GBConstruction.CORRUGATED_STEEL.item(), dev.groundburst.content.construction.GBConstruction.CORRUGATED_STEEL_STAIRS.item(), dev.groundburst.content.construction.GBConstruction.CORRUGATED_STEEL_SLAB.item());
        stairsAndSlab(dev.groundburst.content.construction.GBConstruction.RUSTED_CORRUGATED_STEEL.item(), dev.groundburst.content.construction.GBConstruction.RUSTED_CORRUGATED_STEEL_STAIRS.item(),
                dev.groundburst.content.construction.GBConstruction.RUSTED_CORRUGATED_STEEL_SLAB.item());
        stairsAndSlab(dev.groundburst.content.construction.GBConstruction.RADOME_PANEL.item(), dev.groundburst.content.construction.GBConstruction.RADOME_PANEL_STAIRS.item(), dev.groundburst.content.construction.GBConstruction.RADOME_PANEL_SLAB.item());
        stairsAndSlab(dev.groundburst.content.construction.GBConstruction.STEEL_GRATING.item(), null, dev.groundburst.content.construction.GBConstruction.STEEL_GRATING_SLAB.item());
        var sandbags = dev.groundburst.content.construction.GBConstruction.SANDBAGS.item();
        shapeless(RecipeCategory.BUILDING_BLOCKS, sandbags, 4).requires(Items.SAND, 4).requires(Items.STRING)
                .unlockedBy("has_sand", has(Items.SAND)).save(output);
        stairsAndSlab(sandbags, null, dev.groundburst.content.construction.GBConstruction.SANDBAG_SLAB.item());
        shaped(RecipeCategory.DECORATIONS, dev.groundburst.content.construction.GBConstruction.FLOODLIGHT.item(), 2)
                .define('P', plate).define('G', Items.GLOWSTONE).define('L', Items.GLASS_PANE)
                .pattern(" P ").pattern("PGP").pattern(" L ")
                .unlockedBy(getHasName(plate), has(plate)).save(output);
        shaped(RecipeCategory.DECORATIONS, dev.groundburst.content.construction.GBConstruction.SEARCH_RADAR.item(), 1)
                .define('P', plate).define('S', rod).define('R', Items.REDSTONE).define('L', dev.groundburst.content.construction.GBConstruction.LATTICE_FRAME.item())
                .pattern("PPP").pattern("SRS").pattern(" L ")
                .unlockedBy(getHasName(dev.groundburst.content.construction.GBConstruction.LATTICE_FRAME.item()), has(dev.groundburst.content.construction.GBConstruction.LATTICE_FRAME.item())).save(output);
        var concrete = dev.groundburst.content.launch.GBLaunch.REINFORCED_CONCRETE.item();
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.construction.GBConstruction.LARGE_SILO_DOOR.item(), 1)
                .define('D', GBParts.DENSE_STEEL_PLATE).define('C', concrete).define('P', Items.PISTON)
                .pattern("DDD").pattern("CPC").pattern("DDD")
                .unlockedBy(getHasName(GBParts.DENSE_STEEL_PLATE), has(GBParts.DENSE_STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.REDSTONE, dev.groundburst.content.construction.GBConstruction.RUSTED_LARGE_SILO_DOOR.item(), 1)
                .requires(dev.groundburst.content.construction.GBConstruction.LARGE_SILO_DOOR.item()).requires(Items.WATER_BUCKET)
                .unlockedBy(getHasName(dev.groundburst.content.construction.GBConstruction.LARGE_SILO_DOOR.item()), has(dev.groundburst.content.construction.GBConstruction.LARGE_SILO_DOOR.item())).save(output);
        sign(dev.groundburst.content.construction.GBConstruction.RADIATION_SIGN.item(), Tags.Items.DYES_YELLOW, Ingredient.of(Items.DYE.black()));
        sign(dev.groundburst.content.construction.GBConstruction.HIGH_VOLTAGE_SIGN.item(), Tags.Items.DYES_YELLOW, Ingredient.of(Items.REDSTONE));
        sign(dev.groundburst.content.construction.GBConstruction.KEEP_OUT_SIGN.item(), Tags.Items.DYES_RED, Ingredient.of(Items.DYE.white()));
        sign(dev.groundburst.content.construction.GBConstruction.TOXIC_SIGN.item(), Tags.Items.DYES_ORANGE, Ingredient.of(Items.DYE.black()));
    }

    /** One item cut in the stonecutter into {@code count} of another. */
    private void cut(net.minecraft.world.level.ItemLike from, net.minecraft.world.level.ItemLike to, int count) {
        net.minecraft.data.recipes.SingleItemRecipeBuilder.stonecutting(Ingredient.of(from), RecipeCategory.BUILDING_BLOCKS, to, count)
                .unlockedBy(getHasName(from), has(from)).save(output, id(getItemName(to) + "_from_" + getItemName(from) + "_stonecutting"));
    }

    /** Eight round a water bucket rust (the bucket comes back). */
    private void rusted(net.minecraft.world.level.ItemLike clean, net.minecraft.world.level.ItemLike rusty) {
        shaped(RecipeCategory.BUILDING_BLOCKS, rusty, 8).define('#', clean).define('W', Items.WATER_BUCKET)
                .pattern("###").pattern("#W#").pattern("###")
                .unlockedBy(getHasName(clean), has(clean)).save(output);
    }

    /** Stairs (six make four) and a slab (three make six) from a block, and both cut from it in the stonecutter. */
    private void stairsAndSlab(net.minecraft.world.level.ItemLike block, net.minecraft.world.level.ItemLike stairs,
                               net.minecraft.world.level.ItemLike slab) {
        if (stairs != null) {
            stairBuilder(stairs, Ingredient.of(block)).unlockedBy(getHasName(block), has(block)).save(output);
            cut(block, stairs, 1);
        }
        slabBuilder(RecipeCategory.BUILDING_BLOCKS, slab, Ingredient.of(block)).unlockedBy(getHasName(block), has(block)).save(output);
        cut(block, slab, 2);
    }

    /** A warning sign: a steel plate painted with a dye and the sign's mark, three to a plate. */
    private void sign(net.minecraft.world.level.ItemLike sign, TagKey<net.minecraft.world.item.Item> paint, Ingredient mark) {
        shapeless(RecipeCategory.DECORATIONS, sign, 3).requires(GBParts.STEEL_PLATE).requires(paint).requires(mark)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE)).save(output);
    }

    /**
     * Hazmat suits (DESIGN.md §21.5): plastic sheeting, a glass visor and a charcoal filter in the hood, yellow dye in
     * the coat; the lead-lined suit is the plain one with four lead plates round each piece.
     */
    private void suits() {
        var plastic = dev.groundburst.content.chem.GBChemicals.PLASTIC_SHEET;
        shaped(RecipeCategory.COMBAT, dev.groundburst.content.gear.GBArmor.HAZMAT_HOOD, 1)
                .define('P', plastic).define('G', Items.GLASS_PANE).define('C', Items.CHARCOAL)
                .pattern("PCP").pattern("PGP")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundburst.content.gear.GBArmor.HAZMAT_COAT, 1)
                .define('P', plastic).define('Y', Tags.Items.DYES_YELLOW)
                .pattern("P P").pattern("PYP").pattern("PPP")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundburst.content.gear.GBArmor.HAZMAT_TROUSERS, 1)
                .define('P', plastic)
                .pattern("PPP").pattern("P P").pattern("P P")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundburst.content.gear.GBArmor.HAZMAT_BOOTS, 1)
                .define('P', plastic).define('L', Items.LEATHER)
                .pattern("P P").pattern("L L")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        var plain = dev.groundburst.content.gear.GBArmor.HAZMAT_SET;
        var lined = dev.groundburst.content.gear.GBArmor.LEAD_HAZMAT_SET;
        for (int i = 0; i < plain.size(); i++) {
            shaped(RecipeCategory.COMBAT, lined.get(i), 1)
                    .define('L', GBParts.LEAD_PLATE).define('H', plain.get(i))
                    .pattern(" L ").pattern("LHL").pattern(" L ")
                    .unlockedBy(getHasName(plain.get(i)), has(plain.get(i))).save(output);
        }
    }

    /** Electrolytic Separator and Cryogenic Condenser (DESIGN.md §6): deuterium, liquid oxygen, liquid nitrogen. */
    private void gases() {
        // Products go to the machines' output tanks; canisters and buckets fill there (or pipes take them away).
        machine(ProcessingKind.ELECTROLYSIS, "electrolysis/deuterium", List.of(), null, List.of(water(8_000)),
                List.of(), List.of(fluidOut(GBFluids.DEUTERIUM, 1_000)), 400, ElectrolyticSeparatorBlockEntity.ENERGY_PER_TICK);
        int cryo = CryogenicCondenserBlockEntity.ENERGY_PER_TICK;
        machine(ProcessingKind.CRYO_CONDENSING, "cryo_condensing/liquid_oxygen", List.of(), Ingredient.of(GBParts.OXYGEN_MEMBRANE), List.of(),
                List.of(), List.of(fluidOut(GBFluids.LIQUID_OXYGEN, 1_000)), 300, cryo);
        machine(ProcessingKind.CRYO_CONDENSING, "cryo_condensing/liquid_nitrogen", List.of(), Ingredient.of(GBParts.NITROGEN_MEMBRANE), List.of(),
                List.of(), List.of(fluidOut(GBFluids.LIQUID_NITROGEN, 1_000)), 300, cryo);

        shapeless(RecipeCategory.MISC, GBChemicals.EMPTY_GAS_CANISTER, 1)
                .requires(GBTags.commonItem("plates/steel")).requires(GBTags.commonItem("plates/steel")).requires(Items.GLASS)
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE))
                .save(output);
        membrane(GBParts.OXYGEN_MEMBRANE, Items.LAPIS_LAZULI);
        membrane(GBParts.NITROGEN_MEMBRANE, Items.QUARTZ);
        // DESIGN.md §26.4: the separator takes a basic circuit, the condenser an advanced one.
        shaped(RecipeCategory.REDSTONE, GBMachines.ELECTROLYTIC_SEPARATOR.item(), 1)
                .define('W', GBTags.commonItem("wires/gold")).define('G', Items.GLASS).define('P', GBTags.commonItem("plates/copper"))
                .define('F', GBParts.MACHINE_FRAME).define('B', GBTags.BASIC_CIRCUITS).define('K', Items.CAULDRON)
                .pattern("WGW").pattern("PFP").pattern("BK ")
                .unlockedBy(getHasName(GBParts.MACHINE_FRAME), has(GBParts.MACHINE_FRAME))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.CRYOGENIC_CONDENSER.item(), 1)
                .define('P', GBTags.commonItem("plates/copper")).define('M', GBParts.ELECTRIC_MOTOR).define('F', GBParts.MACHINE_FRAME)
                .define('V', GBParts.PRESSURE_VESSEL).define('A', GBTags.ADVANCED_CIRCUITS)
                .pattern("PMP").pattern("PFP").pattern("AV ")
                .unlockedBy(getHasName(GBParts.PRESSURE_VESSEL), has(GBParts.PRESSURE_VESSEL))
                .save(output);
    }

    /**
     * Chemical Plant (DESIGN.md §6). Game abstractions: each step is one machine recipe with a few ingredients and no
     * real-world quantities or conditions. Spent canisters come back as the second output.
     */
    private void chemistry() {
        int fe = ChemicalPlantBlockEntity.ENERGY_PER_TICK;
        Ingredient coal = tag(ItemTags.COALS);
        Ingredient saltpeter = tag(GBTags.commonItem("dusts/saltpeter"));
        Ingredient ironDust = tag(GBTags.commonItem("dusts/iron"));
        Ingredient slime = tag(Tags.Items.SLIME_BALLS);
        Ingredient rdx = Ingredient.of(GBChemicals.RDX_CRYSTALS);
        Ingredient plastic = Ingredient.of(GBChemicals.PLASTIC_SHEET);
        chem("sulfuric_acid", List.of(in(tag(GBTags.commonItem("dusts/sulfur")), 1)), List.of(water(1_000)),
                List.of(), GBFluids.SULFURIC_ACID, 200, fe);
        chem("nitric_acid", List.of(in(saltpeter, 1)), List.of(fluid(GBFluids.SULFURIC_ACID, 1_000)),
                List.of(), GBFluids.NITRIC_ACID, 200, fe);
        chem("synthetic_fuel", List.of(in(coal, 2)), List.of(water(1_000)), List.of(), GBFluids.SYNTHETIC_FUEL, 240, fe);
        chem("plastic_sheet", List.of(in(coal, 1)), List.of(fluid(GBFluids.SYNTHETIC_FUEL, 1_000)),
                List.of(out(GBChemicals.PLASTIC_SHEET, 4)), null, 200, fe);
        chem("rdx_crystals", List.of(in(coal, 1)), List.of(fluid(GBFluids.NITRIC_ACID, 1_000), water(500)),
                List.of(out(GBChemicals.RDX_CRYSTALS, 2)), null, 300, fe);
        chem("plastic_explosive", List.of(in(rdx, 1), in(plastic, 1), in(slime, 1)), List.of(),
                List.of(out(GBChemicals.PLASTIC_EXPLOSIVE, 2)), null, 200, fe);
        chem("thermobaric_slurry", List.of(in(rdx, 1), in(ironDust, 1)), List.of(fluid(GBFluids.SYNTHETIC_FUEL, 1_000)),
                List.of(), GBFluids.THERMOBARIC_SLURRY, 300, fe);
        chem("napalm", List.of(in(plastic, 1), in(slime, 1)), List.of(fluid(GBFluids.SYNTHETIC_FUEL, 1_000)),
                List.of(), GBFluids.NAPALM, 200, fe);
        chem("solid_propellant", List.of(in(saltpeter, 1), in(ironDust, 1), in(slime, 1)), List.of(fluid(GBFluids.SULFURIC_ACID, 1_000)),
                List.of(out(GBChemicals.SOLID_PROPELLANT, 2)), null, 300, fe);
        chem("liquid_rocket_fuel", List.of(), List.of(fluid(GBFluids.SYNTHETIC_FUEL, 1_000), fluid(GBFluids.LIQUID_OXYGEN, 1_000)),
                List.of(), GBFluids.LIQUID_ROCKET_FUEL, 300, fe);
        // Chemical weapons: salt leached out of dried kelp, chlorine from salt water, toxin from chlorine.
        chem("salt", List.of(in(Ingredient.of(Items.DRIED_KELP), 4)), List.of(water(1_000)), List.of(out(GBChemicals.SALT, 2)), null, 200, fe);
        chem("chlorine", List.of(in(Ingredient.of(GBChemicals.SALT), 2)), List.of(water(1_000)), List.of(), GBFluids.CHLORINE, 300, fe);
        chem("toxin", List.of(in(Ingredient.of(Items.SPIDER_EYE), 2), in(Ingredient.of(Items.POISONOUS_POTATO), 1)),
                List.of(fluid(GBFluids.CHLORINE, 1_000), fluid(GBFluids.SULFURIC_ACID, 500)), List.of(), GBFluids.TOXIN, 300, fe);
        // Heavy water (reactor coolant): deuterium and liquid oxygen.
        chem("heavy_water", List.of(), List.of(fluid(GBFluids.DEUTERIUM, 1_000), fluid(GBFluids.LIQUID_OXYGEN, 500)),
                List.of(), GBFluids.HEAVY_WATER, 300, fe);
        chem("yellowcake", List.of(in(tag(GBTags.commonItem("dusts/uranium")), 2)), List.of(fluid(GBFluids.SULFURIC_ACID, 1_000)),
                List.of(out(GBChemicals.YELLOWCAKE, 2)), null, 300, fe);
        chem("uranium_hexafluoride", List.of(in(Ingredient.of(GBChemicals.YELLOWCAKE), 1)), List.of(fluid(GBFluids.SULFURIC_ACID, 1_000)),
                List.of(), GBFluids.URANIUM_HEXAFLUORIDE, 400, fe);
        // Lithium-6: two lithium dust and a mercury flask give one Li-6 dust; the flask usually comes back.
        chem("lithium6_dust", List.of(in(tag(GBTags.commonItem("dusts/lithium")), 2), in(Ingredient.of(GBChemicals.MERCURY_FLASK), 1)), List.of(),
                List.of(out(GBChemicals.LITHIUM6_DUST, 1), new ProcessingRecipe.Output(new ItemStackTemplate(GBChemicals.MERCURY_FLASK.get(), 1), 0.9f)),
                null, 300, fe);
        // Phase 15: a damaged warhead found in an old silo or a crashed missile, its core leached out with acid.
        chem("salvaged_warhead", List.of(in(Ingredient.of(dev.groundburst.content.salvage.GBSalvage.DAMAGED_WARHEAD), 1)), List.of(fluid(GBFluids.SULFURIC_ACID, 1_000)),
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_NUGGET, 4),
                        new ProcessingRecipe.Output(new ItemStackTemplate(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET.get(), 2), 0.5f),
                        out(GBParts.STEEL_PLATE, 2)), null, 400, fe);
        Ingredient canister = Ingredient.of(GBParts.EMPTY_CANISTER);
        // Mercury flask from cinnabar: Arc Furnace (roasting).
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/mercury_flask", List.of(in(tag(GBTags.commonItem("dusts/cinnabar")), 4), in(canister, 1)), null,
                List.of(out(GBChemicals.MERCURY_FLASK, 1)), 300, ArcFurnaceBlockEntity.ENERGY_PER_TICK);

        // The plant's blocks (DESIGN.md §26.4).
        shaped(RecipeCategory.REDSTONE, GBMachines.CHEMICAL_PLANT.item(), 1)
                .define('F', GBParts.MACHINE_FRAME).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('V', GBParts.PRESSURE_VESSEL)
                .define('M', GBParts.ELECTRIC_MOTOR).define('B', GBTags.BASIC_CIRCUITS).define('K', Items.CAULDRON)
                .pattern("GVG").pattern("MFB").pattern("GKG")
                .unlockedBy(getHasName(GBParts.MACHINE_FRAME), has(GBParts.MACHINE_FRAME))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.MACHINE_CASING.item(), 2)
                .define('P', GBTags.commonItem("plates/steel")).define('F', GBParts.MACHINE_FRAME)
                .pattern(" P ").pattern("PFP").pattern(" P ")
                .unlockedBy(getHasName(GBParts.MACHINE_FRAME), has(GBParts.MACHINE_FRAME))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.REACTOR_GLASS.item(), 4)
                .define('L', GBTags.commonItem("plates/lead")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .pattern("LGL").pattern("G G").pattern("LGL")
                .unlockedBy(getHasName(GBParts.LEAD_PLATE), has(GBParts.LEAD_PLATE))
                .save(output);
        shaped(RecipeCategory.TOOLS, GBMachines.WRENCH.get(), 1)
                .define('I', GBTags.commonItem("ingots/steel"))
                .pattern("I I").pattern(" I ").pattern(" I ")
                .unlockedBy(getHasName(GBParts.STEEL_INGOT), has(GBParts.STEEL_INGOT))
                .save(output);
        // Water pump: motor and pressure vessel over an intake pipe.
        shaped(RecipeCategory.REDSTONE, GBMachines.WATER_PUMP.item(), 1)
                .define('M', GBParts.ELECTRIC_MOTOR).define('V', GBParts.PRESSURE_VESSEL).define('F', GBParts.MACHINE_FRAME)
                .define('P', GBFluids.FLUID_PIPE.item()).define('B', Items.BUCKET)
                .pattern(" M ").pattern("PFP").pattern("VBV")
                .unlockedBy(getHasName(GBParts.MACHINE_FRAME), has(GBParts.MACHINE_FRAME))
                .save(output);
        // Fluid tank: a steel-framed glass box.
        shaped(RecipeCategory.REDSTONE, GBFluids.FLUID_TANK.item(), 1)
                .define('P', GBTags.commonItem("plates/steel")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('R', GBTags.commonItem("rods/steel"))
                .pattern("RPR").pattern("G G").pattern("RPR")
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE))
                .save(output);
        // Fluid pipe: steel plates round a glass sight-tube.
        // Copper pipe: the starter tier.
        shaped(RecipeCategory.REDSTONE, GBFluids.COPPER_FLUID_PIPE.item(), 6)
                .define('P', GBTags.commonItem("plates/copper")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .pattern("PPP").pattern("GGG").pattern("PPP")
                .unlockedBy(getHasName(GBParts.COPPER_PLATE), has(GBParts.COPPER_PLATE))
                .save(output);
        // Pressurized pipe: steel pipes wrapped in tungsten and plastic.
        shaped(RecipeCategory.REDSTONE, GBFluids.PRESSURIZED_FLUID_PIPE.item(), 4)
                .define('T', GBTags.commonItem("plates/tungsten")).define('P', GBFluids.FLUID_PIPE.item()).define('S', GBChemicals.PLASTIC_SHEET)
                .pattern("TPT").pattern("PSP").pattern("TPT")
                .unlockedBy(getHasName(GBFluids.FLUID_PIPE.item()), has(GBFluids.FLUID_PIPE.item()))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBFluids.FLUID_PIPE.item(), 6)
                .define('P', GBTags.commonItem("plates/steel")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .pattern("PPP").pattern("GGG").pattern("PPP")
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE))
                .save(output);
        // Item pipes (DESIGN.md §6.5): iron sight-tubes with a hopper's worth of iron, then steel, then pneumatic.
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.pipe.GBPipes.IRON_ITEM_PIPE.item(), 8)
                .define('I', Tags.Items.INGOTS_IRON).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('H', Items.HOPPER)
                .pattern("III").pattern("GHG").pattern("III")
                .unlockedBy(getHasName(Items.HOPPER), has(Items.HOPPER))
                .save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.pipe.GBPipes.ITEM_PIPE.item(), 8)
                .define('P', GBTags.commonItem("plates/steel")).define('I', dev.groundburst.content.pipe.GBPipes.IRON_ITEM_PIPE.item())
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .pattern("PPP").pattern("IRI").pattern("PPP")
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundburst.content.pipe.GBPipes.PNEUMATIC_ITEM_PIPE.item(), 4)
                .define('C', GBTags.commonItem("plates/copper")).define('P', dev.groundburst.content.pipe.GBPipes.ITEM_PIPE.item())
                .define('M', GBParts.ELECTRIC_MOTOR)
                .pattern("CPC").pattern("PMP").pattern("CPC")
                .unlockedBy(getHasName(GBParts.ELECTRIC_MOTOR), has(GBParts.ELECTRIC_MOTOR))
                .save(output);
        // DESIGN.md §26.8: glass, diamond, advanced circuit, battery.
        shaped(RecipeCategory.TOOLS, GBMultiblocks.HOLO_PROJECTOR.get(), 1)
                .define('G', Tags.Items.GLASS_PANES_COLORLESS).define('D', Tags.Items.GEMS_DIAMOND).define('A', GBTags.ADVANCED_CIRCUITS)
                .define('B', GBParts.BATTERY)
                .pattern("GDG").pattern(" A ").pattern(" B ")
                .unlockedBy("has_advanced_circuit", has(GBTags.ADVANCED_CIRCUITS))
                .save(output);
        // The Field Manual: a book and some gunpowder.
        shapeless(RecipeCategory.MISC, dev.groundburst.content.manual.GBManual.FIELD_MANUAL.get(), 1)
                .requires(Items.BOOK).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(Items.BOOK), has(Items.BOOK))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GBMultiblocks.ITEM_PORT.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(Items.HOPPER)
                .unlockedBy(getHasName(GBMultiblocks.MACHINE_CASING.item()), has(GBMultiblocks.MACHINE_CASING.item()))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GBMultiblocks.FLUID_PORT.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(Items.BUCKET)
                .unlockedBy(getHasName(GBMultiblocks.MACHINE_CASING.item()), has(GBMultiblocks.MACHINE_CASING.item()))
                .save(output);
    }

    /**
     * Slice 5 (DESIGN.md §26.2-26.4): Circuit Fabricator, Gas Centrifuge, Fusion Fuel Synthesizer and Exotic Matter
     * Fabricator recipes, their machines, the battery, enriched/depleted uranium and insulated cable. Game abstractions:
     * one machine step each.
     */
    private void slice5() {
        Ingredient rareEarth = tag(GBTags.commonItem("dusts/rare_earth"));
        Ingredient goldWire = tag(GBTags.commonItem("wires/gold"));
        Ingredient filament = Ingredient.of(GBParts.TUNGSTEN_FILAMENT);
        Ingredient advanced = tag(GBTags.ADVANCED_CIRCUITS);

        // Circuit Fabricator: circuits, sensor arrays, superconducting coils (coils use liquid nitrogen from the coolant tank).
        fab("basic_circuit", List.of(in(tag(GBTags.commonItem("wires/copper")), 2), in(tag(Tags.Items.DUSTS_REDSTONE), 2),
                in(Ingredient.of(GBChemicals.PLASTIC_SHEET), 1)), List.of(), out(GBParts.BASIC_CIRCUIT, 2), 200);
        fab("advanced_circuit", List.of(in(tag(GBTags.BASIC_CIRCUITS), 2), in(goldWire, 2), in(rareEarth, 1), in(tag(Tags.Items.GEMS_QUARTZ), 1)),
                List.of(), out(GBParts.ADVANCED_CIRCUIT, 1), 300);
        fab("military_circuit", List.of(in(advanced, 2), in(filament, 1), in(tag(Tags.Items.GEMS_DIAMOND), 1), in(rareEarth, 2)),
                List.of(), out(GBParts.MILITARY_CIRCUIT, 1), 400);
        fab("quantum_circuit", List.of(in(Ingredient.of(GBParts.MILITARY_CIRCUIT), 1),
                in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.STABILISED_EXOTIC_MATTER), 1), in(Ingredient.of(GBParts.SUPERCONDUCTING_COIL), 1)),
                List.of(), out(GBParts.QUANTUM_CIRCUIT, 1), 600);
        fab("sensor_array", List.of(in(advanced, 1), in(tag(Tags.Items.GLASS_BLOCKS_COLORLESS), 1), in(rareEarth, 1), in(goldWire, 2)),
                List.of(), out(GBParts.SENSOR_ARRAY, 1), 300);
        fab("superconducting_coil", List.of(in(filament, 2), in(rareEarth, 1), in(goldWire, 2)),
                List.of(fluid(GBFluids.LIQUID_NITROGEN, 1_000)), out(GBParts.SUPERCONDUCTING_COIL, 1), 400);

        // Gas Centrifuge: one run of UF6 gives enriched uranium nuggets and depleted uranium dust (cascades run faster).
        machine(ProcessingKind.CENTRIFUGING, "centrifuging/enriched_uranium", List.of(), null,
                List.of(fluid(GBFluids.URANIUM_HEXAFLUORIDE, 1_000)),
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_NUGGET, 2), out(dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_DUST, 1)),
                List.of(), 600, dev.groundburst.content.machine.GasCentrifugeBlockEntity.ENERGY_PER_TICK);
        storage("nuggets/enriched_uranium", "ingots/enriched_uranium", dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_NUGGET,
                dev.groundburst.content.nuclear.GBNuclear.ENRICHED_URANIUM_INGOT);
        cook("dusts/depleted_uranium", dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_DUST,
                dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT, 0.3f);
        crush("crushing/depleted_uranium_dust_from_ingot", tag(GBTags.commonItem("ingots/depleted_uranium")), 1,
                dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_DUST, 1, null, 0, 100);

        // Fusion Fuel Synthesizer: lithium-6 dust and deuterium gas into a lithium deuteride pellet.
        machine(ProcessingKind.FUSION_SYNTHESIS, "fusion_synthesis/lithium_deuteride_pellet",
                List.of(in(tag(GBTags.commonItem("dusts/lithium_6")), 1)), null, List.of(fluid(GBFluids.DEUTERIUM, 1_000)),
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.LITHIUM_DEUTERIDE_PELLET, 1)), List.of(), 300,
                dev.groundburst.content.machine.FusionFuelSynthesizerBlockEntity.ENERGY_PER_TICK);

        // Exotic Matter Fabricator.
        int ef = dev.groundburst.content.machine.ExoticMatterFabricatorBlockEntity.ENERGY_PER_TICK;
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/stabilised_exotic_matter",
                List.of(in(tag(GBTags.commonItem("gems/exotic_matter")), 4), in(Ingredient.of(GBParts.NEODYMIUM_MAGNET), 1)), null,
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.STABILISED_EXOTIC_MATTER, 1)), 600, ef);
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/containment_cell",
                List.of(in(tag(GBTags.commonItem("plates/tungsten")), 2), in(Ingredient.of(GBParts.SUPERCONDUCTING_COIL), 2),
                        in(tag(Tags.Items.GLASS_BLOCKS_COLORLESS), 1)), null,
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.CONTAINMENT_CELL, 1)), 400, ef);
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/singularity_seed",
                List.of(in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.STABILISED_EXOTIC_MATTER), 4),
                        in(tag(GBTags.commonItem("storage_blocks/tungsten")), 4), in(Ingredient.of(GBParts.QUANTUM_CIRCUIT), 1)), null,
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.SINGULARITY_SEED, 1)), 1200, ef);

        // Battery (DESIGN.md §26.2): a crafting part until the Battery Charger arrives.
        shaped(RecipeCategory.MISC, GBParts.BATTERY, 1)
                .define('C', GBTags.commonItem("plates/copper")).define('R', Tags.Items.DUSTS_REDSTONE)
                .define('L', GBTags.commonItem("dusts/lithium")).define('P', GBChemicals.PLASTIC_SHEET)
                .pattern(" C ").pattern("LRL").pattern(" P ")
                .unlockedBy(getHasName(GBChemicals.PLASTIC_SHEET), has(GBChemicals.PLASTIC_SHEET))
                .save(output);
        // Cable tiers (DESIGN.md §26.4): each one wraps six of the tier below. Insulated: plastic jacketing and a gold
        // wire (Chemical Plant + press tier); high-voltage: tungsten filament conductors and a tungsten plate sheath
        // (Arc Furnace tier).
        shaped(RecipeCategory.REDSTONE, GBMachines.CABLES.get(1).item(), 6)
                .define('C', GBMachines.CABLES.get(0).item()).define('P', GBChemicals.PLASTIC_SHEET).define('G', GBTags.commonItem("wires/gold"))
                .pattern("CCC").pattern("PGP").pattern("CCC")
                .unlockedBy(getHasName(GBChemicals.PLASTIC_SHEET), has(GBChemicals.PLASTIC_SHEET))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.CABLES.get(2).item(), 6)
                .define('I', GBMachines.CABLES.get(1).item()).define('F', GBParts.TUNGSTEN_FILAMENT).define('T', GBTags.commonItem("plates/tungsten"))
                .pattern("III").pattern("FTF").pattern("III")
                .unlockedBy(getHasName(GBMachines.CABLES.get(1).item()), has(GBMachines.CABLES.get(1).item()))
                .save(output);

        // The machines (DESIGN.md §26.4).
        shaped(RecipeCategory.REDSTONE, GBMachines.CIRCUIT_FABRICATOR.item(), 1)
                .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('S', GBParts.SERVO).define('F', GBParts.MACHINE_FRAME)
                .define('H', GBParts.HANDMADE_CIRCUIT_BOARD)
                .pattern(" G ").pattern("SFS").pattern(" H ")
                .unlockedBy(getHasName(GBParts.SERVO), has(GBParts.SERVO))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.GAS_CENTRIFUGE.item(), 1)
                .define('R', GBTags.commonItem("rods/tungsten")).define('M', GBParts.ELECTRIC_MOTOR).define('F', GBParts.MACHINE_FRAME)
                .define('D', GBParts.DENSE_STEEL_PLATE).define('A', GBTags.ADVANCED_CIRCUITS)
                .pattern("DRD").pattern("MFM").pattern(" A ")
                .unlockedBy("has_advanced_circuit", has(GBTags.ADVANCED_CIRCUITS))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.FUSION_FUEL_SYNTHESIZER.item(), 1)
                .define('V', GBParts.PRESSURE_VESSEL).define('T', GBTags.commonItem("plates/tungsten")).define('F', GBParts.MACHINE_FRAME)
                .define('H', GBParts.HEATING_ELEMENT).define('A', GBTags.ADVANCED_CIRCUITS)
                .pattern(" T ").pattern("VFV").pattern("HA ")
                .unlockedBy("has_advanced_circuit", has(GBTags.ADVANCED_CIRCUITS))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.EXOTIC_MATTER_FABRICATOR.item(), 1)
                .define('C', GBParts.SUPERCONDUCTING_COIL).define('E', Items.ENDER_EYE).define('T', GBTags.commonItem("plates/tungsten"))
                .define('F', GBParts.MACHINE_FRAME).define('M', GBParts.MILITARY_CIRCUIT)
                .pattern("CEC").pattern("TFT").pattern("CMC")
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT))
                .save(output);
    }

    /** Slice 6 (DESIGN.md §26.2, §26.4): mid-game power, storage, charging and the decontamination shower. */
    private void slice6() {
        shaped(RecipeCategory.MISC, GBParts.CAPACITOR, 1)
                .define('P', GBTags.commonItem("plates/copper")).define('S', GBChemicals.PLASTIC_SHEET).define('W', GBTags.commonItem("wires/gold"))
                .pattern("PSP").pattern(" W ")
                .unlockedBy(getHasName(GBChemicals.PLASTIC_SHEET), has(GBChemicals.PLASTIC_SHEET))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.DIESEL_GENERATOR.item(), 1)
                .define('M', GBParts.ELECTRIC_MOTOR).define('V', GBParts.PRESSURE_VESSEL).define('F', GBParts.MACHINE_FRAME)
                .define('G', GBMachines.COMBUSTION_GENERATOR.item())
                .pattern("MV").pattern("GF")
                .unlockedBy(getHasName(GBParts.PRESSURE_VESSEL), has(GBParts.PRESSURE_VESSEL))
                .save(output);
        // The RTG's design recipe carried a plutonium charge; its fuel now goes in a slot, so an advanced circuit
        // (the thermocouple controller) takes that place.
        shaped(RecipeCategory.REDSTONE, GBMachines.RTG.item(), 1)
                .define('L', GBTags.commonItem("plates/lead")).define('T', GBTags.commonItem("plates/tungsten"))
                .define('W', GBTags.commonItem("wires/copper")).define('A', GBTags.ADVANCED_CIRCUITS)
                .pattern("LTL").pattern("WAW").pattern("LTL")
                .unlockedBy(getHasName(GBParts.LEAD_PLATE), has(GBParts.LEAD_PLATE))
                .save(output);
        var banks = GBMachines.CAPACITOR_BANKS;
        shaped(RecipeCategory.REDSTONE, banks.get(0).item(), 1)
                .define('C', GBParts.CAPACITOR).define('F', GBParts.MACHINE_FRAME)
                .pattern("C C").pattern(" F ").pattern("C C")
                .unlockedBy(getHasName(GBParts.CAPACITOR), has(GBParts.CAPACITOR))
                .save(output);
        shaped(RecipeCategory.REDSTONE, banks.get(1).item(), 1)
                .define('W', GBTags.commonItem("wires/gold")).define('B', banks.get(0).item()).define('A', GBTags.ADVANCED_CIRCUITS)
                .pattern("W W").pattern(" B ").pattern("WAW")
                .unlockedBy(getHasName(banks.get(0).item()), has(banks.get(0).item()))
                .save(output);
        shaped(RecipeCategory.REDSTONE, banks.get(2).item(), 1)
                .define('C', GBParts.SUPERCONDUCTING_COIL).define('B', banks.get(1).item()).define('M', GBParts.MILITARY_CIRCUIT)
                .pattern("C C").pattern(" B ").pattern("CMC")
                .unlockedBy(getHasName(banks.get(1).item()), has(banks.get(1).item()))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.BATTERY_CHARGER.item(), 1)
                .define('C', GBParts.CAPACITOR).define('B', GBTags.BASIC_CIRCUITS).define('F', GBParts.MACHINE_FRAME)
                .pattern("CBC").pattern(" F ")
                .unlockedBy(getHasName(GBParts.CAPACITOR), has(GBParts.CAPACITOR))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.DECONTAMINATION_SHOWER.item(), 1)
                .define('P', GBTags.commonItem("plates/steel")).define('I', Items.IRON_BARS).define('W', Items.WATER_BUCKET)
                .define('B', GBTags.BASIC_CIRCUITS).define('M', GBParts.ELECTRIC_MOTOR)
                .pattern("PPP").pattern("IWI").pattern("BM ")
                .unlockedBy(getHasName(GBParts.ELECTRIC_MOTOR), has(GBParts.ELECTRIC_MOTOR))
                .save(output);
    }

    /**
     * Slice 7 (DESIGN.md §6, §26.3-26.4, §26.7): reactor fuel and breeding targets, their reprocessing in the Chemical
     * Plant, plutonium, antimatter, and the parts of the Fission Reactor and Particle Accelerator. Game abstractions.
     */
    /** Slice 8 (DESIGN.md §26.4): the Fusion and Singularity Reactors' parts and controllers. */
    private void slice8() {
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.TOROIDAL_FIELD_COIL.item(), 4)
                .define('D', GBParts.DENSE_STEEL_PLATE).define('C', GBParts.SUPERCONDUCTING_COIL).define('T', GBTags.commonItem("plates/tungsten"))
                .define('N', GBParts.NEODYMIUM_MAGNET)
                .pattern("DCD").pattern("TNT").pattern("DCD")
                .unlockedBy(getHasName(GBParts.SUPERCONDUCTING_COIL), has(GBParts.SUPERCONDUCTING_COIL))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.VACUUM_VESSEL.item(), 4)
                .define('T', GBTags.commonItem("plates/tungsten")).define('M', GBMultiblocks.MACHINE_CASING.item())
                .pattern("TMT").pattern("M M").pattern("TMT")
                .unlockedBy(getHasName(GBMultiblocks.MACHINE_CASING.item()), has(GBMultiblocks.MACHINE_CASING.item()))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.CENTRAL_SOLENOID.item(), 1)
                .define('W', GBTags.commonItem("wires/copper")).define('C', GBParts.SUPERCONDUCTING_COIL)
                .define('R', GBTags.commonItem("rods/tungsten")).define('D', GBParts.DENSE_STEEL_PLATE)
                .pattern("WCW").pattern("WRW").pattern("WDW")
                .unlockedBy(getHasName(GBParts.SUPERCONDUCTING_COIL), has(GBParts.SUPERCONDUCTING_COIL))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GBMachines.FUSION_REACTOR.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.CRT_DISPLAY)
                .requires(GBParts.CRT_DISPLAY).requires(GBParts.PRESSURE_VESSEL)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT))
                .save(output);
        // The singularity cage's own parts are made in the Exotic Matter Fabricator.
        int ef = dev.groundburst.content.machine.ExoticMatterFabricatorBlockEntity.ENERGY_PER_TICK;
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/containment_emitter",
                List.of(in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.CONTAINMENT_CELL), 1), in(Ingredient.of(GBParts.SUPERCONDUCTING_COIL), 2),
                        in(Ingredient.of(GBParts.MILITARY_CIRCUIT), 1)), null,
                List.of(out(GBMultiblocks.CONTAINMENT_EMITTER.item(), 1)), 400, ef);
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/core_cradle",
                List.of(in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.STABILISED_EXOTIC_MATTER), 1),
                        in(tag(GBTags.commonItem("plates/tungsten")), 4), in(Ingredient.of(GBParts.NEODYMIUM_MAGNET), 1)), null,
                List.of(out(GBMultiblocks.CORE_CRADLE.item(), 1)), 400, ef);
        shapeless(RecipeCategory.REDSTONE, GBMachines.SINGULARITY_REACTOR.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.QUANTUM_CIRCUIT).requires(GBParts.CRT_DISPLAY)
                .requires(GBParts.CRT_DISPLAY).requires(dev.groundburst.content.nuclear.GBNuclear.STABILISED_EXOTIC_MATTER)
                .unlockedBy(getHasName(GBParts.QUANTUM_CIRCUIT), has(GBParts.QUANTUM_CIRCUIT))
                .save(output);
    }

    private void slice7() {
        // Display used by controllers (DESIGN.md §26.2).
        shaped(RecipeCategory.MISC, GBParts.CRT_DISPLAY, 1)
                .define('G', Tags.Items.GLASS_PANES_COLORLESS).define('L', Tags.Items.DUSTS_GLOWSTONE).define('B', GBTags.BASIC_CIRCUITS)
                .define('R', Tags.Items.DUSTS_REDSTONE).define('P', GBTags.commonItem("plates/steel"))
                .pattern(" G ").pattern("LBR").pattern(" P ")
                .unlockedBy("has_basic_circuit", has(GBTags.BASIC_CIRCUITS))
                .save(output);
        // Fuel and targets (DESIGN.md §26.3).
        shapeless(RecipeCategory.MISC, dev.groundburst.content.nuclear.GBNuclear.URANIUM_FUEL_ROD, 1)
                .requires(GBTags.commonItem("ingots/uranium")).requires(GBTags.commonItem("ingots/uranium")).requires(GBTags.commonItem("ingots/uranium"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GBResources.URANIUM_INGOT), has(GBResources.URANIUM_INGOT))
                .save(output);
        shapeless(RecipeCategory.MISC, dev.groundburst.content.nuclear.GBNuclear.DU_TARGET_ROD, 1)
                .requires(GBTags.commonItem("ingots/depleted_uranium")).requires(GBTags.commonItem("ingots/depleted_uranium"))
                .requires(GBTags.commonItem("ingots/depleted_uranium")).requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT), has(dev.groundburst.content.nuclear.GBNuclear.DEPLETED_URANIUM_INGOT))
                .save(output);
        shapeless(RecipeCategory.MISC, dev.groundburst.content.nuclear.GBNuclear.LI6_TARGET_ROD, 1)
                .requires(GBTags.commonItem("dusts/lithium_6")).requires(GBTags.commonItem("dusts/lithium_6")).requires(GBTags.commonItem("dusts/lithium_6"))
                .requires(GBTags.commonItem("rods/steel")).requires(GBTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GBChemicals.LITHIUM6_DUST), has(GBChemicals.LITHIUM6_DUST))
                .save(output);
        storage("nuggets/plutonium", "ingots/plutonium", dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET,
                dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_INGOT);
        // Reprocessing in the Chemical Plant: plutonium from DU targets, tritium from Li-6 targets, uranium back out of
        // spent fuel. The steel rods come back too.
        int fe = ChemicalPlantBlockEntity.ENERGY_PER_TICK;
        chem("plutonium", List.of(in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.IRRADIATED_DU_TARGET_ROD), 1)),
                List.of(fluid(GBFluids.SULFURIC_ACID, 1_000)),
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_NUGGET, 3), out(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE, 1)),
                null, 400, fe);
        chem("tritium", List.of(in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.IRRADIATED_LI6_TARGET_ROD), 1)), List.of(),
                List.of(out(GBParts.STEEL_ROD, 2)), GBFluids.TRITIUM, 300, fe);
        chem("spent_fuel_reprocessing", List.of(in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.SPENT_FUEL_ROD), 1)),
                List.of(fluid(GBFluids.SULFURIC_ACID, 1_000)),
                List.of(out(GBResources.URANIUM_DUST, 2), out(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE, 1)), null, 400, fe);
        // Antimatter (Particle Accelerator): slow and very power-hungry.
        machine(ProcessingKind.ACCELERATING, "accelerating/antimatter_cell",
                List.of(in(Ingredient.of(dev.groundburst.content.nuclear.GBNuclear.CONTAINMENT_CELL), 1)), null,
                List.of(out(dev.groundburst.content.nuclear.GBNuclear.ANTIMATTER_CELL, 1)), 3_600,
                dev.groundburst.content.machine.ParticleAcceleratorBlockEntity.ENERGY_PER_TICK);

        // Fission Reactor parts (DESIGN.md §26.4).
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.LEAD_LINED_CASING.item(), 4)
                .define('L', GBTags.commonItem("plates/lead")).define('D', GBParts.DENSE_STEEL_PLATE).define('C', Tags.Items.CONCRETES)
                .pattern("LDL").pattern("DCD").pattern("LDL")
                .unlockedBy(getHasName(GBParts.LEAD_PLATE), has(GBParts.LEAD_PLATE))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GBMultiblocks.ENERGY_PORT.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBMachines.CABLES.get(1).item()).requires(Tags.Items.DUSTS_REDSTONE)
                .unlockedBy(getHasName(GBMultiblocks.MACHINE_CASING.item()), has(GBMultiblocks.MACHINE_CASING.item()))
                .save(output);
        // Fuel Channels: steel rods for the corner posts, steel plates for the spacer grids (DESIGN.md §23.3).
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.FUEL_CHANNEL.item(), 2)
                .define('R', GBParts.STEEL_ROD).define('P', GBTags.commonItem("plates/steel"))
                .pattern("RPR").pattern("R R").pattern("RPR")
                .unlockedBy(getHasName(GBParts.STEEL_ROD), has(GBParts.STEEL_ROD))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GBMachines.FISSION_REACTOR.item(), 1)
                .requires(GBMultiblocks.LEAD_LINED_CASING.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBParts.CRT_DISPLAY)
                .unlockedBy(getHasName(GBParts.MILITARY_CIRCUIT), has(GBParts.MILITARY_CIRCUIT))
                .save(output);
        // Structural steel and the accelerator ring (DESIGN.md §26.4).
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.STEEL_PLATING.item(), 4)
                .define('P', GBTags.commonItem("plates/steel"))
                .pattern("PP").pattern("PP")
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.MAGNET_SEGMENT.item(), 4)
                .define('D', GBParts.DENSE_STEEL_PLATE).define('C', GBParts.SUPERCONDUCTING_COIL).define('W', GBTags.commonItem("wires/copper"))
                .pattern("DCD").pattern(" W ").pattern("DCD")
                .unlockedBy(getHasName(GBParts.SUPERCONDUCTING_COIL), has(GBParts.SUPERCONDUCTING_COIL))
                .save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.INJECTOR.item(), 1)
                .requires(GBMultiblocks.MAGNET_SEGMENT.item()).requires(GBParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(GBMultiblocks.MAGNET_SEGMENT.item()), has(GBMultiblocks.MAGNET_SEGMENT.item()))
                .save(output);
        // The target chamber also takes reactor glass (its viewing window), so its recipe differs from the injector's.
        shapeless(RecipeCategory.BUILDING_BLOCKS, GBMultiblocks.TARGET_CHAMBER.item(), 1)
                .requires(GBMultiblocks.MAGNET_SEGMENT.item()).requires(GBParts.MILITARY_CIRCUIT).requires(GBMultiblocks.REACTOR_GLASS.item())
                .unlockedBy(getHasName(GBMultiblocks.MAGNET_SEGMENT.item()), has(GBMultiblocks.MAGNET_SEGMENT.item()))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GBMachines.PARTICLE_ACCELERATOR.item(), 1)
                .requires(GBMultiblocks.MACHINE_CASING.item()).requires(GBParts.QUANTUM_CIRCUIT).requires(GBParts.CRT_DISPLAY)
                .unlockedBy(getHasName(GBParts.QUANTUM_CIRCUIT), has(GBParts.QUANTUM_CIRCUIT))
                .save(output);
    }

    private void fab(String name, List<SizedIngredient> inputs, List<SizedFluidIngredient> fluids, ProcessingRecipe.Output result, int time) {
        machine(ProcessingKind.FABRICATING, "fabricating/" + name, inputs, null, fluids, List.of(result), List.of(), time,
                dev.groundburst.content.machine.CircuitFabricatorBlockEntity.ENERGY_PER_TICK);
    }

    private void chem(String name, List<SizedIngredient> inputs, List<SizedFluidIngredient> fluids, List<ProcessingRecipe.Output> outputs,
            ChemFluid product, int time, int fe) {
        machine(ProcessingKind.CHEMISTRY, "chemistry/" + name, inputs, null, fluids, outputs,
                product == null ? List.of() : List.of(fluidOut(product, 1_000)), time, fe);
    }

    private void membrane(ItemLike membrane, ItemLike sieve) {
        shaped(RecipeCategory.TOOLS, membrane, 1)
                .define('R', GBTags.commonItem("rods/steel")).define('C', Items.CLAY_BALL).define('L', sieve)
                .pattern("RCR").pattern("CLC").pattern("RCR")
                .unlockedBy(getHasName(Items.CLAY_BALL), has(Items.CLAY_BALL))
                .save(output);
    }

    /** Arc Furnace (DESIGN.md §26.2-26.3): steel, refractory metals, carbide, magnets, cobalt. */
    private void arcSmelting() {
        int fe = ArcFurnaceBlockEntity.ENERGY_PER_TICK;
        Ingredient coal = tag(ItemTags.COALS);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/steel_ingot", List.of(in(tag(Tags.Items.INGOTS_IRON), 1), in(coal, 1)), null,
                List.of(out(GBParts.STEEL_INGOT, 1)), 200, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/steel_ingot_from_dust", List.of(in(tag(GBTags.commonItem("dusts/iron")), 1), in(coal, 1)), null,
                List.of(out(GBParts.STEEL_INGOT, 1)), 160, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/tungsten_ingot_from_dust", List.of(in(tag(GBTags.commonItem("dusts/tungsten")), 1)), null,
                List.of(out(GBResources.TUNGSTEN_INGOT, 1)), 240, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/tungsten_ingot_from_raw", List.of(in(tag(GBTags.commonItem("raw_materials/tungsten")), 1)), null,
                List.of(out(GBResources.TUNGSTEN_INGOT, 1)), 300, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/tungsten_carbide_ingot", List.of(in(tag(GBTags.commonItem("ingots/tungsten")), 1), in(coal, 2)), null,
                List.of(out(GBParts.TUNGSTEN_CARBIDE_INGOT, 1)), 300, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/neodymium_magnet",
                List.of(in(tag(GBTags.commonItem("dusts/rare_earth")), 2), in(tag(GBTags.commonItem("dusts/iron")), 1)), null,
                List.of(out(GBParts.NEODYMIUM_MAGNET, 1)), 240, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/cobalt_ingot", List.of(in(tag(GBTags.commonItem("dusts/cobalt")), 8)), null,
                List.of(out(GBParts.COBALT_INGOT, 1)), 300, fe);
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/lead_ingot_from_raw", List.of(in(tag(GBTags.commonItem("raw_materials/lead")), 1)), null,
                List.of(out(GBResources.LEAD_INGOT, 1), new ProcessingRecipe.Output(new ItemStackTemplate(GBResources.LEAD_DUST.get(), 1), 0.25f)), 120, fe);
    }

    /** Industrial Press (DESIGN.md §26.2): the die in the catalyst slot picks the product; dies are not consumed. */
    private void pressing() {
        int fe = IndustrialPressBlockEntity.ENERGY_PER_TICK;
        Ingredient plate = Ingredient.of(GBParts.PLATE_DIE), rod = Ingredient.of(GBParts.ROD_DIE),
                wire = Ingredient.of(GBParts.WIRE_DIE), canister = Ingredient.of(GBParts.CANISTER_DIE);
        press("steel_plate", tag(GBTags.commonItem("ingots/steel")), 1, plate, GBParts.STEEL_PLATE, 1, 100, fe);
        press("copper_plate", tag(Tags.Items.INGOTS_COPPER), 1, plate, GBParts.COPPER_PLATE, 1, 100, fe);
        press("lead_plate", tag(GBTags.commonItem("ingots/lead")), 1, plate, GBParts.LEAD_PLATE, 1, 100, fe);
        press("tungsten_plate", tag(GBTags.commonItem("ingots/tungsten")), 1, plate, GBParts.TUNGSTEN_PLATE, 1, 160, fe);
        press("dense_steel_plate", tag(GBTags.commonItem("plates/steel")), 4, plate, GBParts.DENSE_STEEL_PLATE, 1, 200, fe);
        press("steel_rod", tag(GBTags.commonItem("ingots/steel")), 1, rod, GBParts.STEEL_ROD, 2, 100, fe);
        press("tungsten_rod", tag(GBTags.commonItem("ingots/tungsten")), 1, rod, GBParts.TUNGSTEN_ROD, 1, 160, fe);
        press("copper_wire", tag(Tags.Items.INGOTS_COPPER), 1, wire, GBParts.COPPER_WIRE, 4, 100, fe);
        press("gold_wire", tag(Tags.Items.INGOTS_GOLD), 1, wire, GBParts.GOLD_WIRE, 4, 100, fe);
        press("tungsten_filament", tag(GBTags.commonItem("ingots/tungsten")), 1, wire, GBParts.TUNGSTEN_FILAMENT, 4, 160, fe);
        press("empty_canister", tag(GBTags.commonItem("plates/steel")), 2, canister, GBParts.EMPTY_CANISTER, 2, 120, fe);
    }

    private void press(String name, Ingredient input, int count, Ingredient die, ItemLike result, int resultCount, int time, int fe) {
        machine(ProcessingKind.PRESSING, "pressing/" + name, List.of(in(input, count)), die, List.of(out(result, resultCount)), time, fe);
    }

    /** Crafting-table parts, machines and dies (DESIGN.md §26.1-26.2, §26.4). */
    private void parts() {
        shaped(RecipeCategory.REDSTONE, GBMachines.ARC_FURNACE.item(), 1)
                .define('B', Tags.Items.STORAGE_BLOCKS_IRON).define('K', Items.CAULDRON).define('C', Tags.Items.INGOTS_COPPER)
                .define('F', Items.BLAST_FURNACE).define('R', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                .pattern("BKB").pattern("CFC").pattern("BRB")
                .unlockedBy(getHasName(Items.BLAST_FURNACE), has(Items.BLAST_FURNACE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GBMachines.INDUSTRIAL_PRESS.item(), 1)
                .define('I', Tags.Items.INGOTS_IRON).define('A', Items.ANVIL).define('P', Items.PISTON).define('R', Tags.Items.DUSTS_REDSTONE)
                .pattern("IAI").pattern("PRP").pattern("I I")
                .unlockedBy(getHasName(Items.ANVIL), has(Items.ANVIL))
                .save(output);
        die(GBParts.PLATE_DIE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
        die(GBParts.ROD_DIE, Items.IRON_BARS);
        die(GBParts.WIRE_DIE, Items.IRON_CHAIN);
        die(GBParts.CANISTER_DIE, Items.BUCKET);

        shaped(RecipeCategory.MISC, GBParts.HANDMADE_CIRCUIT_BOARD, 1)
                .define('C', Tags.Items.INGOTS_COPPER).define('R', Tags.Items.DUSTS_REDSTONE).define('P', Items.PAPER).define('G', Tags.Items.NUGGETS_GOLD)
                .pattern("CRC").pattern("RPG")
                .unlockedBy(getHasName(Items.REDSTONE), has(Items.REDSTONE))
                .save(output);
        TagKey<Item> circuit = GBTags.BASIC_CIRCUITS;
        shaped(RecipeCategory.MISC, GBParts.ELECTRIC_MOTOR, 1)
                .define('W', GBTags.commonItem("wires/copper")).define('P', GBTags.commonItem("plates/steel"))
                .define('M', GBParts.NEODYMIUM_MAGNET).define('R', GBTags.commonItem("rods/steel"))
                .pattern("WPW").pattern("MRM").pattern("WPW")
                .unlockedBy(getHasName(GBParts.NEODYMIUM_MAGNET), has(GBParts.NEODYMIUM_MAGNET))
                .save(output);
        shaped(RecipeCategory.MISC, GBParts.HEATING_ELEMENT, 1)
                .define('F', GBParts.TUNGSTEN_FILAMENT).define('P', GBTags.commonItem("plates/steel")).define('C', GBTags.commonItem("wires/copper"))
                .pattern("FFF").pattern("PCP")
                .unlockedBy(getHasName(GBParts.TUNGSTEN_FILAMENT), has(GBParts.TUNGSTEN_FILAMENT))
                .save(output);
        shaped(RecipeCategory.MISC, GBParts.PRESSURE_VESSEL, 1)
                .define('D', GBParts.DENSE_STEEL_PLATE).define('R', GBTags.commonItem("rods/steel"))
                .pattern(" D ").pattern("DRD").pattern(" D ")
                .unlockedBy(getHasName(GBParts.DENSE_STEEL_PLATE), has(GBParts.DENSE_STEEL_PLATE))
                .save(output);
        shaped(RecipeCategory.MISC, GBParts.MACHINE_FRAME, 1)
                .define('R', GBTags.commonItem("rods/steel")).define('P', GBTags.commonItem("plates/steel")).define('C', circuit)
                .pattern("RPR").pattern("PCP").pattern("RPR")
                .unlockedBy(getHasName(GBParts.STEEL_PLATE), has(GBParts.STEEL_PLATE))
                .save(output);
        shapeless(RecipeCategory.MISC, GBParts.SERVO, 1)
                .requires(GBParts.ELECTRIC_MOTOR).requires(GBTags.commonItem("rods/steel")).requires(circuit)
                .unlockedBy(getHasName(GBParts.ELECTRIC_MOTOR), has(GBParts.ELECTRIC_MOTOR))
                .save(output);
    }

    private void die(ItemLike die, ItemLike shape) {
        shaped(RecipeCategory.TOOLS, die, 1)
                .define('I', Tags.Items.INGOTS_IRON).define('X', shape)
                .pattern(" I ").pattern("IXI").pattern(" I ")
                .unlockedBy(getHasName(GBMachines.INDUSTRIAL_PRESS.item()), has(GBMachines.INDUSTRIAL_PRESS.item()))
                .save(output);
    }

    /** Smelting + blasting from a c: tag (`named` only names the recipe and unlocks it). */
    private void cook(String inputTag, ItemLike named, ItemLike result, float xp) {
        Ingredient input = tag(GBTags.commonItem(inputTag));
        SimpleCookingRecipeBuilder.generic(input, RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 200, SmeltingRecipe::new)
                .unlockedBy("has_" + inputTag.replace('/', '_'), has(GBTags.commonItem(inputTag)))
                .save(output, id(getItemName(result) + "_from_smelting_" + getItemName(named)));
        SimpleCookingRecipeBuilder.generic(input, RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 100, BlastingRecipe::new)
                .unlockedBy("has_" + inputTag.replace('/', '_'), has(GBTags.commonItem(inputTag)))
                .save(output, id(getItemName(result) + "_from_blasting_" + getItemName(named)));
    }

    /** 9 units <-> 1 block, both directions taking any item in the c: tags. */
    private void storage(String unitTag, String blockTag, ItemLike unit, ItemLike block) {
        shaped(RecipeCategory.BUILDING_BLOCKS, block, 1)
                .define('#', GBTags.commonItem(unitTag))
                .pattern("###").pattern("###").pattern("###")
                .unlockedBy("has_" + unitTag.replace('/', '_'), has(GBTags.commonItem(unitTag)))
                .save(output);   // default id: the result's own id (groundburst:<block>)
        shapeless(RecipeCategory.MISC, unit, 9)
                .requires(GBTags.commonItem(blockTag))
                .unlockedBy("has_" + blockTag.replace('/', '_'), has(GBTags.commonItem(blockTag)))
                .save(output, id(getItemName(unit) + "_from_" + getItemName(block)));
    }
}
