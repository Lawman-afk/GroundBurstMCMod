package dev.groundzero.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.groundzero.GroundZero;
import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.machine.ArcFurnaceBlockEntity;
import dev.groundzero.content.fluid.ChemFluid;
import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.ChemicalPlantBlockEntity;
import dev.groundzero.content.multiblock.GZMultiblocks;
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
import net.neoforged.neoforge.fluids.FluidStackTemplate;
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
    private final net.minecraft.core.HolderGetter<net.minecraft.world.level.material.Fluid> fluids;

    public GZRecipeProvider(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
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
        canning();
        chemistry();
        slice5();
        slice6();
        slice7();
        slice8();
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
        machine(kind, name, inputs, catalyst, List.of(), outputs, List.of(), time, energyPerTick);
    }

    private void machine(ProcessingKind kind, String name, List<SizedIngredient> inputs, Ingredient catalyst, List<SizedFluidIngredient> fluids,
            List<ProcessingRecipe.Output> outputs, List<FluidStackTemplate> fluidOutputs, int time, int energyPerTick) {
        ProcessingRecipe recipe = new ProcessingRecipe(kind, inputs, Optional.ofNullable(catalyst), fluids, outputs, fluidOutputs, time, energyPerTick);
        output.accept(ResourceKey.create(Registries.RECIPE, GroundZero.id(name)), recipe, null);
    }

    /** Canning Machine (DESIGN.md §8): every fluid with a canister can be canned and poured out again, 1000 mB a canister. */
    private void canning() {
        int fe = dev.groundzero.content.machine.CanningMachineBlockEntity.ENERGY_PER_TICK;
        int time = dev.groundzero.content.machine.CanningMachineBlockEntity.TIME;
        for (ChemFluid f : GZFluids.all()) {
            machine(ProcessingKind.CANNING, "canning/fill_" + f.name(), List.of(in(Ingredient.of(f.emptyCanister().get()), 1)), null,
                    List.of(fluid(f, 1_000)), List.of(out(f.canister().get(), 1)), List.of(), time, fe);
            machine(ProcessingKind.CANNING, "canning/empty_" + f.name(), List.of(in(Ingredient.of(f.canister().get()), 1)), null,
                    List.of(), List.of(out(f.emptyCanister().get(), 1)), List.of(fluidOut(f, 1_000)), time, fe);
        }
        shaped(RecipeCategory.REDSTONE, GZMachines.CANNING_MACHINE.item(), 1)
                .define('P', GZTags.commonItem("plates/steel")).define('E', GZParts.EMPTY_CANISTER).define('G', Items.GLASS)
                .define('F', GZParts.MACHINE_FRAME).define('B', GZTags.BASIC_CIRCUITS)
                .pattern("PEP").pattern("GFG").pattern(" B ")
                .unlockedBy(getHasName(GZParts.EMPTY_CANISTER), has(GZParts.EMPTY_CANISTER))
                .save(output);
        suits();
        grenades();
    }

    /** Thrown explosives (DESIGN.md §14). */
    private void grenades() {
        shaped(RecipeCategory.COMBAT, dev.groundzero.content.ordnance.GZOrdnance.DYNAMITE, 2)
                .define('S', Items.STRING).define('P', Items.PAPER).define('G', Items.GUNPOWDER)
                .pattern(" S ").pattern("PGP").pattern("PGP")
                .unlockedBy(getHasName(Items.GUNPOWDER), has(Items.GUNPOWDER)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundzero.content.ordnance.GZOrdnance.FRAG_GRENADE, 2)
                .define('N', Items.IRON_NUGGET).define('P', GZParts.STEEL_PLATE).define('G', Items.GUNPOWDER)
                .pattern("NPN").pattern("NGN").pattern("NNN")
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundzero.content.ordnance.GZOrdnance.STICKY_GRENADE, 1)
                .requires(dev.groundzero.content.ordnance.GZOrdnance.FRAG_GRENADE).requires(Items.SLIME_BALL)
                .unlockedBy(getHasName(dev.groundzero.content.ordnance.GZOrdnance.FRAG_GRENADE),
                        has(dev.groundzero.content.ordnance.GZOrdnance.FRAG_GRENADE)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundzero.content.ordnance.GZOrdnance.INCENDIARY_GRENADE, 2)
                .requires(GZChemicals.NAPALM_CANISTER).requires(GZParts.STEEL_PLATE).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(GZChemicals.NAPALM_CANISTER), has(GZChemicals.NAPALM_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundzero.content.ordnance.GZOrdnance.GAS_GRENADE, 2)
                .requires(GZChemicals.CHLORINE_CANISTER).requires(GZParts.STEEL_PLATE).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(GZChemicals.CHLORINE_CANISTER), has(GZChemicals.CHLORINE_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundzero.content.ordnance.GZOrdnance.TOXIN_GRENADE, 2)
                .requires(GZChemicals.TOXIN_CANISTER).requires(GZParts.STEEL_PLATE).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(GZChemicals.TOXIN_CANISTER), has(GZChemicals.TOXIN_CANISTER)).save(output);
        shapeless(RecipeCategory.COMBAT, dev.groundzero.content.ordnance.GZOrdnance.SMOKE_GRENADE, 2)
                .requires(GZParts.STEEL_PLATE).requires(GZTags.commonItem("dusts/saltpeter")).requires(GZTags.commonItem("dusts/saltpeter"))
                .requires(Items.CHARCOAL)
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE)).save(output);
    }

    /**
     * Hazmat suits (DESIGN.md §21.5): plastic sheeting, a glass visor and a charcoal filter in the hood, yellow dye in
     * the coat; the lead-lined suit is the plain one with four lead plates round each piece.
     */
    private void suits() {
        var plastic = dev.groundzero.content.chem.GZChemicals.PLASTIC_SHEET;
        shaped(RecipeCategory.COMBAT, dev.groundzero.content.gear.GZArmor.HAZMAT_HOOD, 1)
                .define('P', plastic).define('G', Items.GLASS_PANE).define('C', Items.CHARCOAL)
                .pattern("PCP").pattern("PGP")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundzero.content.gear.GZArmor.HAZMAT_COAT, 1)
                .define('P', plastic).define('Y', Tags.Items.DYES_YELLOW)
                .pattern("P P").pattern("PYP").pattern("PPP")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundzero.content.gear.GZArmor.HAZMAT_TROUSERS, 1)
                .define('P', plastic)
                .pattern("PPP").pattern("P P").pattern("P P")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        shaped(RecipeCategory.COMBAT, dev.groundzero.content.gear.GZArmor.HAZMAT_BOOTS, 1)
                .define('P', plastic).define('L', Items.LEATHER)
                .pattern("P P").pattern("L L")
                .unlockedBy(getHasName(plastic), has(plastic)).save(output);
        var plain = dev.groundzero.content.gear.GZArmor.HAZMAT_SET;
        var lined = dev.groundzero.content.gear.GZArmor.LEAD_HAZMAT_SET;
        for (int i = 0; i < plain.size(); i++) {
            shaped(RecipeCategory.COMBAT, lined.get(i), 1)
                    .define('L', GZParts.LEAD_PLATE).define('H', plain.get(i))
                    .pattern(" L ").pattern("LHL").pattern(" L ")
                    .unlockedBy(getHasName(plain.get(i)), has(plain.get(i))).save(output);
        }
    }

    /** Electrolytic Separator and Cryogenic Condenser (DESIGN.md §6): deuterium, liquid oxygen, liquid nitrogen. */
    private void gases() {
        // Products go to the machines' output tanks; canisters and buckets fill there (or pipes take them away).
        machine(ProcessingKind.ELECTROLYSIS, "electrolysis/deuterium", List.of(), null, List.of(water(8_000)),
                List.of(), List.of(fluidOut(GZFluids.DEUTERIUM, 1_000)), 400, ElectrolyticSeparatorBlockEntity.ENERGY_PER_TICK);
        int cryo = CryogenicCondenserBlockEntity.ENERGY_PER_TICK;
        machine(ProcessingKind.CRYO_CONDENSING, "cryo_condensing/liquid_oxygen", List.of(), Ingredient.of(GZParts.OXYGEN_MEMBRANE), List.of(),
                List.of(), List.of(fluidOut(GZFluids.LIQUID_OXYGEN, 1_000)), 300, cryo);
        machine(ProcessingKind.CRYO_CONDENSING, "cryo_condensing/liquid_nitrogen", List.of(), Ingredient.of(GZParts.NITROGEN_MEMBRANE), List.of(),
                List.of(), List.of(fluidOut(GZFluids.LIQUID_NITROGEN, 1_000)), 300, cryo);

        shapeless(RecipeCategory.MISC, GZChemicals.EMPTY_GAS_CANISTER, 1)
                .requires(GZTags.commonItem("plates/steel")).requires(GZTags.commonItem("plates/steel")).requires(Items.GLASS)
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE))
                .save(output);
        membrane(GZParts.OXYGEN_MEMBRANE, Items.LAPIS_LAZULI);
        membrane(GZParts.NITROGEN_MEMBRANE, Items.QUARTZ);
        // DESIGN.md §26.4: the separator takes a basic circuit, the condenser an advanced one.
        shaped(RecipeCategory.REDSTONE, GZMachines.ELECTROLYTIC_SEPARATOR.item(), 1)
                .define('W', GZTags.commonItem("wires/gold")).define('G', Items.GLASS).define('P', GZTags.commonItem("plates/copper"))
                .define('F', GZParts.MACHINE_FRAME).define('B', GZTags.BASIC_CIRCUITS).define('K', Items.CAULDRON)
                .pattern("WGW").pattern("PFP").pattern("BK ")
                .unlockedBy(getHasName(GZParts.MACHINE_FRAME), has(GZParts.MACHINE_FRAME))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.CRYOGENIC_CONDENSER.item(), 1)
                .define('P', GZTags.commonItem("plates/copper")).define('M', GZParts.ELECTRIC_MOTOR).define('F', GZParts.MACHINE_FRAME)
                .define('V', GZParts.PRESSURE_VESSEL).define('A', GZTags.ADVANCED_CIRCUITS)
                .pattern("PMP").pattern("PFP").pattern("AV ")
                .unlockedBy(getHasName(GZParts.PRESSURE_VESSEL), has(GZParts.PRESSURE_VESSEL))
                .save(output);
    }

    /**
     * Chemical Plant (DESIGN.md §6). Game abstractions: each step is one machine recipe with a few ingredients and no
     * real-world quantities or conditions. Spent canisters come back as the second output.
     */
    private void chemistry() {
        int fe = ChemicalPlantBlockEntity.ENERGY_PER_TICK;
        Ingredient coal = tag(ItemTags.COALS);
        Ingredient saltpeter = tag(GZTags.commonItem("dusts/saltpeter"));
        Ingredient ironDust = tag(GZTags.commonItem("dusts/iron"));
        Ingredient slime = tag(Tags.Items.SLIME_BALLS);
        Ingredient rdx = Ingredient.of(GZChemicals.RDX_CRYSTALS);
        Ingredient plastic = Ingredient.of(GZChemicals.PLASTIC_SHEET);
        chem("sulfuric_acid", List.of(in(tag(GZTags.commonItem("dusts/sulfur")), 1)), List.of(water(1_000)),
                List.of(), GZFluids.SULFURIC_ACID, 200, fe);
        chem("nitric_acid", List.of(in(saltpeter, 1)), List.of(fluid(GZFluids.SULFURIC_ACID, 1_000)),
                List.of(), GZFluids.NITRIC_ACID, 200, fe);
        chem("synthetic_fuel", List.of(in(coal, 2)), List.of(water(1_000)), List.of(), GZFluids.SYNTHETIC_FUEL, 240, fe);
        chem("plastic_sheet", List.of(in(coal, 1)), List.of(fluid(GZFluids.SYNTHETIC_FUEL, 1_000)),
                List.of(out(GZChemicals.PLASTIC_SHEET, 4)), null, 200, fe);
        chem("rdx_crystals", List.of(in(coal, 1)), List.of(fluid(GZFluids.NITRIC_ACID, 1_000), water(500)),
                List.of(out(GZChemicals.RDX_CRYSTALS, 2)), null, 300, fe);
        chem("plastic_explosive", List.of(in(rdx, 1), in(plastic, 1), in(slime, 1)), List.of(),
                List.of(out(GZChemicals.PLASTIC_EXPLOSIVE, 2)), null, 200, fe);
        chem("thermobaric_slurry", List.of(in(rdx, 1), in(ironDust, 1)), List.of(fluid(GZFluids.SYNTHETIC_FUEL, 1_000)),
                List.of(), GZFluids.THERMOBARIC_SLURRY, 300, fe);
        chem("napalm", List.of(in(plastic, 1), in(slime, 1)), List.of(fluid(GZFluids.SYNTHETIC_FUEL, 1_000)),
                List.of(), GZFluids.NAPALM, 200, fe);
        chem("solid_propellant", List.of(in(saltpeter, 1), in(ironDust, 1), in(slime, 1)), List.of(fluid(GZFluids.SULFURIC_ACID, 1_000)),
                List.of(out(GZChemicals.SOLID_PROPELLANT, 2)), null, 300, fe);
        chem("liquid_rocket_fuel", List.of(), List.of(fluid(GZFluids.SYNTHETIC_FUEL, 1_000), fluid(GZFluids.LIQUID_OXYGEN, 1_000)),
                List.of(), GZFluids.LIQUID_ROCKET_FUEL, 300, fe);
        // Chemical weapons: salt leached out of dried kelp, chlorine from salt water, toxin from chlorine.
        chem("salt", List.of(in(Ingredient.of(Items.DRIED_KELP), 4)), List.of(water(1_000)), List.of(out(GZChemicals.SALT, 2)), null, 200, fe);
        chem("chlorine", List.of(in(Ingredient.of(GZChemicals.SALT), 2)), List.of(water(1_000)), List.of(), GZFluids.CHLORINE, 300, fe);
        chem("toxin", List.of(in(Ingredient.of(Items.SPIDER_EYE), 2), in(Ingredient.of(Items.POISONOUS_POTATO), 1)),
                List.of(fluid(GZFluids.CHLORINE, 1_000), fluid(GZFluids.SULFURIC_ACID, 500)), List.of(), GZFluids.TOXIN, 300, fe);
        // Heavy water (reactor coolant): deuterium and liquid oxygen.
        chem("heavy_water", List.of(), List.of(fluid(GZFluids.DEUTERIUM, 1_000), fluid(GZFluids.LIQUID_OXYGEN, 500)),
                List.of(), GZFluids.HEAVY_WATER, 300, fe);
        chem("yellowcake", List.of(in(tag(GZTags.commonItem("dusts/uranium")), 2)), List.of(fluid(GZFluids.SULFURIC_ACID, 1_000)),
                List.of(out(GZChemicals.YELLOWCAKE, 2)), null, 300, fe);
        chem("uranium_hexafluoride", List.of(in(Ingredient.of(GZChemicals.YELLOWCAKE), 1)), List.of(fluid(GZFluids.SULFURIC_ACID, 1_000)),
                List.of(), GZFluids.URANIUM_HEXAFLUORIDE, 400, fe);
        // Lithium-6: two lithium dust and a mercury flask give one Li-6 dust; the flask usually comes back.
        chem("lithium6_dust", List.of(in(tag(GZTags.commonItem("dusts/lithium")), 2), in(Ingredient.of(GZChemicals.MERCURY_FLASK), 1)), List.of(),
                List.of(out(GZChemicals.LITHIUM6_DUST, 1), new ProcessingRecipe.Output(new ItemStackTemplate(GZChemicals.MERCURY_FLASK.get(), 1), 0.9f)),
                null, 300, fe);
        Ingredient canister = Ingredient.of(GZParts.EMPTY_CANISTER);
        // Mercury flask from cinnabar: Arc Furnace (roasting).
        machine(ProcessingKind.ARC_SMELTING, "arc_smelting/mercury_flask", List.of(in(tag(GZTags.commonItem("dusts/cinnabar")), 4), in(canister, 1)), null,
                List.of(out(GZChemicals.MERCURY_FLASK, 1)), 300, ArcFurnaceBlockEntity.ENERGY_PER_TICK);

        // The plant's blocks (DESIGN.md §26.4).
        shaped(RecipeCategory.REDSTONE, GZMachines.CHEMICAL_PLANT.item(), 1)
                .define('F', GZParts.MACHINE_FRAME).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('V', GZParts.PRESSURE_VESSEL)
                .define('M', GZParts.ELECTRIC_MOTOR).define('B', GZTags.BASIC_CIRCUITS).define('K', Items.CAULDRON)
                .pattern("GVG").pattern("MFB").pattern("GKG")
                .unlockedBy(getHasName(GZParts.MACHINE_FRAME), has(GZParts.MACHINE_FRAME))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.MACHINE_CASING.item(), 2)
                .define('P', GZTags.commonItem("plates/steel")).define('F', GZParts.MACHINE_FRAME)
                .pattern(" P ").pattern("PFP").pattern(" P ")
                .unlockedBy(getHasName(GZParts.MACHINE_FRAME), has(GZParts.MACHINE_FRAME))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.REACTOR_GLASS.item(), 4)
                .define('L', GZTags.commonItem("plates/lead")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .pattern("LGL").pattern("G G").pattern("LGL")
                .unlockedBy(getHasName(GZParts.LEAD_PLATE), has(GZParts.LEAD_PLATE))
                .save(output);
        shaped(RecipeCategory.TOOLS, GZMachines.WRENCH.get(), 1)
                .define('I', GZTags.commonItem("ingots/steel"))
                .pattern("I I").pattern(" I ").pattern(" I ")
                .unlockedBy(getHasName(GZParts.STEEL_INGOT), has(GZParts.STEEL_INGOT))
                .save(output);
        // Water pump: motor and pressure vessel over an intake pipe.
        shaped(RecipeCategory.REDSTONE, GZMachines.WATER_PUMP.item(), 1)
                .define('M', GZParts.ELECTRIC_MOTOR).define('V', GZParts.PRESSURE_VESSEL).define('F', GZParts.MACHINE_FRAME)
                .define('P', GZFluids.FLUID_PIPE.item()).define('B', Items.BUCKET)
                .pattern(" M ").pattern("PFP").pattern("VBV")
                .unlockedBy(getHasName(GZParts.MACHINE_FRAME), has(GZParts.MACHINE_FRAME))
                .save(output);
        // Fluid tank: a steel-framed glass box.
        shaped(RecipeCategory.REDSTONE, GZFluids.FLUID_TANK.item(), 1)
                .define('P', GZTags.commonItem("plates/steel")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('R', GZTags.commonItem("rods/steel"))
                .pattern("RPR").pattern("G G").pattern("RPR")
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE))
                .save(output);
        // Fluid pipe: steel plates round a glass sight-tube.
        // Copper pipe: the starter tier.
        shaped(RecipeCategory.REDSTONE, GZFluids.COPPER_FLUID_PIPE.item(), 6)
                .define('P', GZTags.commonItem("plates/copper")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .pattern("PPP").pattern("GGG").pattern("PPP")
                .unlockedBy(getHasName(GZParts.COPPER_PLATE), has(GZParts.COPPER_PLATE))
                .save(output);
        // Pressurized pipe: steel pipes wrapped in tungsten and plastic.
        shaped(RecipeCategory.REDSTONE, GZFluids.PRESSURIZED_FLUID_PIPE.item(), 4)
                .define('T', GZTags.commonItem("plates/tungsten")).define('P', GZFluids.FLUID_PIPE.item()).define('S', GZChemicals.PLASTIC_SHEET)
                .pattern("TPT").pattern("PSP").pattern("TPT")
                .unlockedBy(getHasName(GZFluids.FLUID_PIPE.item()), has(GZFluids.FLUID_PIPE.item()))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZFluids.FLUID_PIPE.item(), 6)
                .define('P', GZTags.commonItem("plates/steel")).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .pattern("PPP").pattern("GGG").pattern("PPP")
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE))
                .save(output);
        // Item pipes (DESIGN.md §6.5): iron sight-tubes with a hopper's worth of iron, then steel, then pneumatic.
        shaped(RecipeCategory.REDSTONE, dev.groundzero.content.pipe.GZPipes.IRON_ITEM_PIPE.item(), 8)
                .define('I', Tags.Items.INGOTS_IRON).define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('H', Items.HOPPER)
                .pattern("III").pattern("GHG").pattern("III")
                .unlockedBy(getHasName(Items.HOPPER), has(Items.HOPPER))
                .save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundzero.content.pipe.GZPipes.ITEM_PIPE.item(), 8)
                .define('P', GZTags.commonItem("plates/steel")).define('I', dev.groundzero.content.pipe.GZPipes.IRON_ITEM_PIPE.item())
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .pattern("PPP").pattern("IRI").pattern("PPP")
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE))
                .save(output);
        shaped(RecipeCategory.REDSTONE, dev.groundzero.content.pipe.GZPipes.PNEUMATIC_ITEM_PIPE.item(), 4)
                .define('C', GZTags.commonItem("plates/copper")).define('P', dev.groundzero.content.pipe.GZPipes.ITEM_PIPE.item())
                .define('M', GZParts.ELECTRIC_MOTOR)
                .pattern("CPC").pattern("PMP").pattern("CPC")
                .unlockedBy(getHasName(GZParts.ELECTRIC_MOTOR), has(GZParts.ELECTRIC_MOTOR))
                .save(output);
        // DESIGN.md §26.8: glass, diamond, advanced circuit, battery.
        shaped(RecipeCategory.TOOLS, GZMultiblocks.HOLO_PROJECTOR.get(), 1)
                .define('G', Tags.Items.GLASS_PANES_COLORLESS).define('D', Tags.Items.GEMS_DIAMOND).define('A', GZTags.ADVANCED_CIRCUITS)
                .define('B', GZParts.BATTERY)
                .pattern("GDG").pattern(" A ").pattern(" B ")
                .unlockedBy("has_advanced_circuit", has(GZTags.ADVANCED_CIRCUITS))
                .save(output);
        // The Field Manual: a book and some gunpowder.
        shapeless(RecipeCategory.MISC, dev.groundzero.content.manual.GZManual.FIELD_MANUAL.get(), 1)
                .requires(Items.BOOK).requires(Items.GUNPOWDER)
                .unlockedBy(getHasName(Items.BOOK), has(Items.BOOK))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GZMultiblocks.ITEM_PORT.item(), 1)
                .requires(GZMultiblocks.MACHINE_CASING.item()).requires(Items.HOPPER)
                .unlockedBy(getHasName(GZMultiblocks.MACHINE_CASING.item()), has(GZMultiblocks.MACHINE_CASING.item()))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GZMultiblocks.FLUID_PORT.item(), 1)
                .requires(GZMultiblocks.MACHINE_CASING.item()).requires(Items.BUCKET)
                .unlockedBy(getHasName(GZMultiblocks.MACHINE_CASING.item()), has(GZMultiblocks.MACHINE_CASING.item()))
                .save(output);
    }

    /**
     * Slice 5 (DESIGN.md §26.2-26.4): Circuit Fabricator, Gas Centrifuge, Fusion Fuel Synthesizer and Exotic Matter
     * Fabricator recipes, their machines, the battery, enriched/depleted uranium and insulated cable. Game abstractions:
     * one machine step each.
     */
    private void slice5() {
        Ingredient rareEarth = tag(GZTags.commonItem("dusts/rare_earth"));
        Ingredient goldWire = tag(GZTags.commonItem("wires/gold"));
        Ingredient filament = Ingredient.of(GZParts.TUNGSTEN_FILAMENT);
        Ingredient advanced = tag(GZTags.ADVANCED_CIRCUITS);

        // Circuit Fabricator: circuits, sensor arrays, superconducting coils (coils use liquid nitrogen from the coolant tank).
        fab("basic_circuit", List.of(in(tag(GZTags.commonItem("wires/copper")), 2), in(tag(Tags.Items.DUSTS_REDSTONE), 2),
                in(Ingredient.of(GZChemicals.PLASTIC_SHEET), 1)), List.of(), out(GZParts.BASIC_CIRCUIT, 2), 200);
        fab("advanced_circuit", List.of(in(tag(GZTags.BASIC_CIRCUITS), 2), in(goldWire, 2), in(rareEarth, 1), in(tag(Tags.Items.GEMS_QUARTZ), 1)),
                List.of(), out(GZParts.ADVANCED_CIRCUIT, 1), 300);
        fab("military_circuit", List.of(in(advanced, 2), in(filament, 1), in(tag(Tags.Items.GEMS_DIAMOND), 1), in(rareEarth, 2)),
                List.of(), out(GZParts.MILITARY_CIRCUIT, 1), 400);
        fab("quantum_circuit", List.of(in(Ingredient.of(GZParts.MILITARY_CIRCUIT), 1),
                in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.STABILISED_EXOTIC_MATTER), 1), in(Ingredient.of(GZParts.SUPERCONDUCTING_COIL), 1)),
                List.of(), out(GZParts.QUANTUM_CIRCUIT, 1), 600);
        fab("sensor_array", List.of(in(advanced, 1), in(tag(Tags.Items.GLASS_BLOCKS_COLORLESS), 1), in(rareEarth, 1), in(goldWire, 2)),
                List.of(), out(GZParts.SENSOR_ARRAY, 1), 300);
        fab("superconducting_coil", List.of(in(filament, 2), in(rareEarth, 1), in(goldWire, 2)),
                List.of(fluid(GZFluids.LIQUID_NITROGEN, 1_000)), out(GZParts.SUPERCONDUCTING_COIL, 1), 400);

        // Gas Centrifuge: one run of UF6 gives enriched uranium nuggets and depleted uranium dust (cascades run faster).
        machine(ProcessingKind.CENTRIFUGING, "centrifuging/enriched_uranium", List.of(), null,
                List.of(fluid(GZFluids.URANIUM_HEXAFLUORIDE, 1_000)),
                List.of(out(dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_NUGGET, 2), out(dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_DUST, 1)),
                List.of(), 600, dev.groundzero.content.machine.GasCentrifugeBlockEntity.ENERGY_PER_TICK);
        storage("nuggets/enriched_uranium", "ingots/enriched_uranium", dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_NUGGET,
                dev.groundzero.content.nuclear.GZNuclear.ENRICHED_URANIUM_INGOT);
        cook("dusts/depleted_uranium", dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_DUST,
                dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_INGOT, 0.3f);
        crush("crushing/depleted_uranium_dust_from_ingot", tag(GZTags.commonItem("ingots/depleted_uranium")), 1,
                dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_DUST, 1, null, 0, 100);

        // Fusion Fuel Synthesizer: lithium-6 dust and deuterium gas into a lithium deuteride pellet.
        machine(ProcessingKind.FUSION_SYNTHESIS, "fusion_synthesis/lithium_deuteride_pellet",
                List.of(in(tag(GZTags.commonItem("dusts/lithium_6")), 1)), null, List.of(fluid(GZFluids.DEUTERIUM, 1_000)),
                List.of(out(dev.groundzero.content.nuclear.GZNuclear.LITHIUM_DEUTERIDE_PELLET, 1)), List.of(), 300,
                dev.groundzero.content.machine.FusionFuelSynthesizerBlockEntity.ENERGY_PER_TICK);

        // Exotic Matter Fabricator.
        int ef = dev.groundzero.content.machine.ExoticMatterFabricatorBlockEntity.ENERGY_PER_TICK;
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/stabilised_exotic_matter",
                List.of(in(tag(GZTags.commonItem("gems/exotic_matter")), 4), in(Ingredient.of(GZParts.NEODYMIUM_MAGNET), 1)), null,
                List.of(out(dev.groundzero.content.nuclear.GZNuclear.STABILISED_EXOTIC_MATTER, 1)), 600, ef);
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/containment_cell",
                List.of(in(tag(GZTags.commonItem("plates/tungsten")), 2), in(Ingredient.of(GZParts.SUPERCONDUCTING_COIL), 2),
                        in(tag(Tags.Items.GLASS_BLOCKS_COLORLESS), 1)), null,
                List.of(out(dev.groundzero.content.nuclear.GZNuclear.CONTAINMENT_CELL, 1)), 400, ef);
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/singularity_seed",
                List.of(in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.STABILISED_EXOTIC_MATTER), 4),
                        in(tag(GZTags.commonItem("storage_blocks/tungsten")), 4), in(Ingredient.of(GZParts.QUANTUM_CIRCUIT), 1)), null,
                List.of(out(dev.groundzero.content.nuclear.GZNuclear.SINGULARITY_SEED, 1)), 1200, ef);

        // Battery (DESIGN.md §26.2): a crafting part until the Battery Charger arrives.
        shaped(RecipeCategory.MISC, GZParts.BATTERY, 1)
                .define('C', GZTags.commonItem("plates/copper")).define('R', Tags.Items.DUSTS_REDSTONE)
                .define('L', GZTags.commonItem("dusts/lithium")).define('P', GZChemicals.PLASTIC_SHEET)
                .pattern(" C ").pattern("LRL").pattern(" P ")
                .unlockedBy(getHasName(GZChemicals.PLASTIC_SHEET), has(GZChemicals.PLASTIC_SHEET))
                .save(output);
        // Cable tiers (DESIGN.md §26.4): each one wraps six of the tier below. Insulated: plastic jacketing and a gold
        // wire (Chemical Plant + press tier); high-voltage: tungsten filament conductors and a tungsten plate sheath
        // (Arc Furnace tier).
        shaped(RecipeCategory.REDSTONE, GZMachines.CABLES.get(1).item(), 6)
                .define('C', GZMachines.CABLES.get(0).item()).define('P', GZChemicals.PLASTIC_SHEET).define('G', GZTags.commonItem("wires/gold"))
                .pattern("CCC").pattern("PGP").pattern("CCC")
                .unlockedBy(getHasName(GZChemicals.PLASTIC_SHEET), has(GZChemicals.PLASTIC_SHEET))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.CABLES.get(2).item(), 6)
                .define('I', GZMachines.CABLES.get(1).item()).define('F', GZParts.TUNGSTEN_FILAMENT).define('T', GZTags.commonItem("plates/tungsten"))
                .pattern("III").pattern("FTF").pattern("III")
                .unlockedBy(getHasName(GZMachines.CABLES.get(1).item()), has(GZMachines.CABLES.get(1).item()))
                .save(output);

        // The machines (DESIGN.md §26.4).
        shaped(RecipeCategory.REDSTONE, GZMachines.CIRCUIT_FABRICATOR.item(), 1)
                .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS).define('S', GZParts.SERVO).define('F', GZParts.MACHINE_FRAME)
                .define('H', GZParts.HANDMADE_CIRCUIT_BOARD)
                .pattern(" G ").pattern("SFS").pattern(" H ")
                .unlockedBy(getHasName(GZParts.SERVO), has(GZParts.SERVO))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.GAS_CENTRIFUGE.item(), 1)
                .define('R', GZTags.commonItem("rods/tungsten")).define('M', GZParts.ELECTRIC_MOTOR).define('F', GZParts.MACHINE_FRAME)
                .define('D', GZParts.DENSE_STEEL_PLATE).define('A', GZTags.ADVANCED_CIRCUITS)
                .pattern("DRD").pattern("MFM").pattern(" A ")
                .unlockedBy("has_advanced_circuit", has(GZTags.ADVANCED_CIRCUITS))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.FUSION_FUEL_SYNTHESIZER.item(), 1)
                .define('V', GZParts.PRESSURE_VESSEL).define('T', GZTags.commonItem("plates/tungsten")).define('F', GZParts.MACHINE_FRAME)
                .define('H', GZParts.HEATING_ELEMENT).define('A', GZTags.ADVANCED_CIRCUITS)
                .pattern(" T ").pattern("VFV").pattern("HA ")
                .unlockedBy("has_advanced_circuit", has(GZTags.ADVANCED_CIRCUITS))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.EXOTIC_MATTER_FABRICATOR.item(), 1)
                .define('C', GZParts.SUPERCONDUCTING_COIL).define('E', Items.ENDER_EYE).define('T', GZTags.commonItem("plates/tungsten"))
                .define('F', GZParts.MACHINE_FRAME).define('M', GZParts.MILITARY_CIRCUIT)
                .pattern("CEC").pattern("TFT").pattern("CMC")
                .unlockedBy(getHasName(GZParts.MILITARY_CIRCUIT), has(GZParts.MILITARY_CIRCUIT))
                .save(output);
    }

    /** Slice 6 (DESIGN.md §26.2, §26.4): mid-game power, storage, charging and the decontamination shower. */
    private void slice6() {
        shaped(RecipeCategory.MISC, GZParts.CAPACITOR, 1)
                .define('P', GZTags.commonItem("plates/copper")).define('S', GZChemicals.PLASTIC_SHEET).define('W', GZTags.commonItem("wires/gold"))
                .pattern("PSP").pattern(" W ")
                .unlockedBy(getHasName(GZChemicals.PLASTIC_SHEET), has(GZChemicals.PLASTIC_SHEET))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.DIESEL_GENERATOR.item(), 1)
                .define('M', GZParts.ELECTRIC_MOTOR).define('V', GZParts.PRESSURE_VESSEL).define('F', GZParts.MACHINE_FRAME)
                .define('G', GZMachines.COMBUSTION_GENERATOR.item())
                .pattern("MV").pattern("GF")
                .unlockedBy(getHasName(GZParts.PRESSURE_VESSEL), has(GZParts.PRESSURE_VESSEL))
                .save(output);
        // The RTG's design recipe carried a plutonium charge; its fuel now goes in a slot, so an advanced circuit
        // (the thermocouple controller) takes that place.
        shaped(RecipeCategory.REDSTONE, GZMachines.RTG.item(), 1)
                .define('L', GZTags.commonItem("plates/lead")).define('T', GZTags.commonItem("plates/tungsten"))
                .define('W', GZTags.commonItem("wires/copper")).define('A', GZTags.ADVANCED_CIRCUITS)
                .pattern("LTL").pattern("WAW").pattern("LTL")
                .unlockedBy(getHasName(GZParts.LEAD_PLATE), has(GZParts.LEAD_PLATE))
                .save(output);
        var banks = GZMachines.CAPACITOR_BANKS;
        shaped(RecipeCategory.REDSTONE, banks.get(0).item(), 1)
                .define('C', GZParts.CAPACITOR).define('F', GZParts.MACHINE_FRAME)
                .pattern("C C").pattern(" F ").pattern("C C")
                .unlockedBy(getHasName(GZParts.CAPACITOR), has(GZParts.CAPACITOR))
                .save(output);
        shaped(RecipeCategory.REDSTONE, banks.get(1).item(), 1)
                .define('W', GZTags.commonItem("wires/gold")).define('B', banks.get(0).item()).define('A', GZTags.ADVANCED_CIRCUITS)
                .pattern("W W").pattern(" B ").pattern("WAW")
                .unlockedBy(getHasName(banks.get(0).item()), has(banks.get(0).item()))
                .save(output);
        shaped(RecipeCategory.REDSTONE, banks.get(2).item(), 1)
                .define('C', GZParts.SUPERCONDUCTING_COIL).define('B', banks.get(1).item()).define('M', GZParts.MILITARY_CIRCUIT)
                .pattern("C C").pattern(" B ").pattern("CMC")
                .unlockedBy(getHasName(banks.get(1).item()), has(banks.get(1).item()))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.BATTERY_CHARGER.item(), 1)
                .define('C', GZParts.CAPACITOR).define('B', GZTags.BASIC_CIRCUITS).define('F', GZParts.MACHINE_FRAME)
                .pattern("CBC").pattern(" F ")
                .unlockedBy(getHasName(GZParts.CAPACITOR), has(GZParts.CAPACITOR))
                .save(output);
        shaped(RecipeCategory.REDSTONE, GZMachines.DECONTAMINATION_SHOWER.item(), 1)
                .define('P', GZTags.commonItem("plates/steel")).define('I', Items.IRON_BARS).define('W', Items.WATER_BUCKET)
                .define('B', GZTags.BASIC_CIRCUITS).define('M', GZParts.ELECTRIC_MOTOR)
                .pattern("PPP").pattern("IWI").pattern("BM ")
                .unlockedBy(getHasName(GZParts.ELECTRIC_MOTOR), has(GZParts.ELECTRIC_MOTOR))
                .save(output);
    }

    /**
     * Slice 7 (DESIGN.md §6, §26.3-26.4, §26.7): reactor fuel and breeding targets, their reprocessing in the Chemical
     * Plant, plutonium, antimatter, and the parts of the Fission Reactor and Particle Accelerator. Game abstractions.
     */
    /** Slice 8 (DESIGN.md §26.4): the Fusion and Singularity Reactors' parts and controllers. */
    private void slice8() {
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.TOROIDAL_FIELD_COIL.item(), 4)
                .define('D', GZParts.DENSE_STEEL_PLATE).define('C', GZParts.SUPERCONDUCTING_COIL).define('T', GZTags.commonItem("plates/tungsten"))
                .define('N', GZParts.NEODYMIUM_MAGNET)
                .pattern("DCD").pattern("TNT").pattern("DCD")
                .unlockedBy(getHasName(GZParts.SUPERCONDUCTING_COIL), has(GZParts.SUPERCONDUCTING_COIL))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.VACUUM_VESSEL.item(), 4)
                .define('T', GZTags.commonItem("plates/tungsten")).define('M', GZMultiblocks.MACHINE_CASING.item())
                .pattern("TMT").pattern("M M").pattern("TMT")
                .unlockedBy(getHasName(GZMultiblocks.MACHINE_CASING.item()), has(GZMultiblocks.MACHINE_CASING.item()))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.CENTRAL_SOLENOID.item(), 1)
                .define('W', GZTags.commonItem("wires/copper")).define('C', GZParts.SUPERCONDUCTING_COIL)
                .define('R', GZTags.commonItem("rods/tungsten")).define('D', GZParts.DENSE_STEEL_PLATE)
                .pattern("WCW").pattern("WRW").pattern("WDW")
                .unlockedBy(getHasName(GZParts.SUPERCONDUCTING_COIL), has(GZParts.SUPERCONDUCTING_COIL))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GZMachines.FUSION_REACTOR.item(), 1)
                .requires(GZMultiblocks.MACHINE_CASING.item()).requires(GZParts.MILITARY_CIRCUIT).requires(GZParts.CRT_DISPLAY)
                .requires(GZParts.CRT_DISPLAY).requires(GZParts.PRESSURE_VESSEL)
                .unlockedBy(getHasName(GZParts.MILITARY_CIRCUIT), has(GZParts.MILITARY_CIRCUIT))
                .save(output);
        // The singularity cage's own parts are made in the Exotic Matter Fabricator.
        int ef = dev.groundzero.content.machine.ExoticMatterFabricatorBlockEntity.ENERGY_PER_TICK;
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/containment_emitter",
                List.of(in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.CONTAINMENT_CELL), 1), in(Ingredient.of(GZParts.SUPERCONDUCTING_COIL), 2),
                        in(Ingredient.of(GZParts.MILITARY_CIRCUIT), 1)), null,
                List.of(out(GZMultiblocks.CONTAINMENT_EMITTER.item(), 1)), 400, ef);
        machine(ProcessingKind.EXOTIC_FABRICATING, "exotic_fabricating/core_cradle",
                List.of(in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.STABILISED_EXOTIC_MATTER), 1),
                        in(tag(GZTags.commonItem("plates/tungsten")), 4), in(Ingredient.of(GZParts.NEODYMIUM_MAGNET), 1)), null,
                List.of(out(GZMultiblocks.CORE_CRADLE.item(), 1)), 400, ef);
        shapeless(RecipeCategory.REDSTONE, GZMachines.SINGULARITY_REACTOR.item(), 1)
                .requires(GZMultiblocks.MACHINE_CASING.item()).requires(GZParts.QUANTUM_CIRCUIT).requires(GZParts.CRT_DISPLAY)
                .requires(GZParts.CRT_DISPLAY).requires(dev.groundzero.content.nuclear.GZNuclear.STABILISED_EXOTIC_MATTER)
                .unlockedBy(getHasName(GZParts.QUANTUM_CIRCUIT), has(GZParts.QUANTUM_CIRCUIT))
                .save(output);
    }

    private void slice7() {
        // Display used by controllers (DESIGN.md §26.2).
        shaped(RecipeCategory.MISC, GZParts.CRT_DISPLAY, 1)
                .define('G', Tags.Items.GLASS_PANES_COLORLESS).define('L', Tags.Items.DUSTS_GLOWSTONE).define('B', GZTags.BASIC_CIRCUITS)
                .define('R', Tags.Items.DUSTS_REDSTONE).define('P', GZTags.commonItem("plates/steel"))
                .pattern(" G ").pattern("LBR").pattern(" P ")
                .unlockedBy("has_basic_circuit", has(GZTags.BASIC_CIRCUITS))
                .save(output);
        // Fuel and targets (DESIGN.md §26.3).
        shapeless(RecipeCategory.MISC, dev.groundzero.content.nuclear.GZNuclear.URANIUM_FUEL_ROD, 1)
                .requires(GZTags.commonItem("ingots/uranium")).requires(GZTags.commonItem("ingots/uranium")).requires(GZTags.commonItem("ingots/uranium"))
                .requires(GZTags.commonItem("rods/steel")).requires(GZTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GZResources.URANIUM_INGOT), has(GZResources.URANIUM_INGOT))
                .save(output);
        shapeless(RecipeCategory.MISC, dev.groundzero.content.nuclear.GZNuclear.DU_TARGET_ROD, 1)
                .requires(GZTags.commonItem("ingots/depleted_uranium")).requires(GZTags.commonItem("ingots/depleted_uranium"))
                .requires(GZTags.commonItem("ingots/depleted_uranium")).requires(GZTags.commonItem("rods/steel")).requires(GZTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_INGOT), has(dev.groundzero.content.nuclear.GZNuclear.DEPLETED_URANIUM_INGOT))
                .save(output);
        shapeless(RecipeCategory.MISC, dev.groundzero.content.nuclear.GZNuclear.LI6_TARGET_ROD, 1)
                .requires(GZTags.commonItem("dusts/lithium_6")).requires(GZTags.commonItem("dusts/lithium_6")).requires(GZTags.commonItem("dusts/lithium_6"))
                .requires(GZTags.commonItem("rods/steel")).requires(GZTags.commonItem("rods/steel"))
                .unlockedBy(getHasName(GZChemicals.LITHIUM6_DUST), has(GZChemicals.LITHIUM6_DUST))
                .save(output);
        storage("nuggets/plutonium", "ingots/plutonium", dev.groundzero.content.nuclear.GZNuclear.PLUTONIUM_NUGGET,
                dev.groundzero.content.nuclear.GZNuclear.PLUTONIUM_INGOT);
        // Reprocessing in the Chemical Plant: plutonium from DU targets, tritium from Li-6 targets, uranium back out of
        // spent fuel. The steel rods come back too.
        int fe = ChemicalPlantBlockEntity.ENERGY_PER_TICK;
        chem("plutonium", List.of(in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.IRRADIATED_DU_TARGET_ROD), 1)),
                List.of(fluid(GZFluids.SULFURIC_ACID, 1_000)),
                List.of(out(dev.groundzero.content.nuclear.GZNuclear.PLUTONIUM_NUGGET, 3), out(dev.groundzero.content.nuclear.GZNuclear.NUCLEAR_WASTE, 1)),
                null, 400, fe);
        chem("tritium", List.of(in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.IRRADIATED_LI6_TARGET_ROD), 1)), List.of(),
                List.of(out(GZParts.STEEL_ROD, 2)), GZFluids.TRITIUM, 300, fe);
        chem("spent_fuel_reprocessing", List.of(in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.SPENT_FUEL_ROD), 1)),
                List.of(fluid(GZFluids.SULFURIC_ACID, 1_000)),
                List.of(out(GZResources.URANIUM_DUST, 2), out(dev.groundzero.content.nuclear.GZNuclear.NUCLEAR_WASTE, 1)), null, 400, fe);
        // Antimatter (Particle Accelerator): slow and very power-hungry.
        machine(ProcessingKind.ACCELERATING, "accelerating/antimatter_cell",
                List.of(in(Ingredient.of(dev.groundzero.content.nuclear.GZNuclear.CONTAINMENT_CELL), 1)), null,
                List.of(out(dev.groundzero.content.nuclear.GZNuclear.ANTIMATTER_CELL, 1)), 3_600,
                dev.groundzero.content.machine.ParticleAcceleratorBlockEntity.ENERGY_PER_TICK);

        // Fission Reactor parts (DESIGN.md §26.4).
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.LEAD_LINED_CASING.item(), 4)
                .define('L', GZTags.commonItem("plates/lead")).define('D', GZParts.DENSE_STEEL_PLATE).define('C', Tags.Items.CONCRETES)
                .pattern("LDL").pattern("DCD").pattern("LDL")
                .unlockedBy(getHasName(GZParts.LEAD_PLATE), has(GZParts.LEAD_PLATE))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GZMultiblocks.ENERGY_PORT.item(), 1)
                .requires(GZMultiblocks.MACHINE_CASING.item()).requires(GZMachines.CABLES.get(1).item()).requires(Tags.Items.DUSTS_REDSTONE)
                .unlockedBy(getHasName(GZMultiblocks.MACHINE_CASING.item()), has(GZMultiblocks.MACHINE_CASING.item()))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GZMachines.FISSION_REACTOR.item(), 1)
                .requires(GZMultiblocks.LEAD_LINED_CASING.item()).requires(GZParts.MILITARY_CIRCUIT).requires(GZParts.CRT_DISPLAY)
                .unlockedBy(getHasName(GZParts.MILITARY_CIRCUIT), has(GZParts.MILITARY_CIRCUIT))
                .save(output);
        // Structural steel and the accelerator ring (DESIGN.md §26.4).
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.STEEL_PLATING.item(), 4)
                .define('P', GZTags.commonItem("plates/steel"))
                .pattern("PP").pattern("PP")
                .unlockedBy(getHasName(GZParts.STEEL_PLATE), has(GZParts.STEEL_PLATE))
                .save(output);
        shaped(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.MAGNET_SEGMENT.item(), 4)
                .define('D', GZParts.DENSE_STEEL_PLATE).define('C', GZParts.SUPERCONDUCTING_COIL).define('W', GZTags.commonItem("wires/copper"))
                .pattern("DCD").pattern(" W ").pattern("DCD")
                .unlockedBy(getHasName(GZParts.SUPERCONDUCTING_COIL), has(GZParts.SUPERCONDUCTING_COIL))
                .save(output);
        shapeless(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.INJECTOR.item(), 1)
                .requires(GZMultiblocks.MAGNET_SEGMENT.item()).requires(GZParts.MILITARY_CIRCUIT)
                .unlockedBy(getHasName(GZMultiblocks.MAGNET_SEGMENT.item()), has(GZMultiblocks.MAGNET_SEGMENT.item()))
                .save(output);
        // The target chamber also takes reactor glass (its viewing window), so its recipe differs from the injector's.
        shapeless(RecipeCategory.BUILDING_BLOCKS, GZMultiblocks.TARGET_CHAMBER.item(), 1)
                .requires(GZMultiblocks.MAGNET_SEGMENT.item()).requires(GZParts.MILITARY_CIRCUIT).requires(GZMultiblocks.REACTOR_GLASS.item())
                .unlockedBy(getHasName(GZMultiblocks.MAGNET_SEGMENT.item()), has(GZMultiblocks.MAGNET_SEGMENT.item()))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, GZMachines.PARTICLE_ACCELERATOR.item(), 1)
                .requires(GZMultiblocks.MACHINE_CASING.item()).requires(GZParts.QUANTUM_CIRCUIT).requires(GZParts.CRT_DISPLAY)
                .unlockedBy(getHasName(GZParts.QUANTUM_CIRCUIT), has(GZParts.QUANTUM_CIRCUIT))
                .save(output);
    }

    private void fab(String name, List<SizedIngredient> inputs, List<SizedFluidIngredient> fluids, ProcessingRecipe.Output result, int time) {
        machine(ProcessingKind.FABRICATING, "fabricating/" + name, inputs, null, fluids, List.of(result), List.of(), time,
                dev.groundzero.content.machine.CircuitFabricatorBlockEntity.ENERGY_PER_TICK);
    }

    private void chem(String name, List<SizedIngredient> inputs, List<SizedFluidIngredient> fluids, List<ProcessingRecipe.Output> outputs,
            ChemFluid product, int time, int fe) {
        machine(ProcessingKind.CHEMISTRY, "chemistry/" + name, inputs, null, fluids, outputs,
                product == null ? List.of() : List.of(fluidOut(product, 1_000)), time, fe);
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
