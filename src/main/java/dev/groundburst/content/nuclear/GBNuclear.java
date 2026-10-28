package dev.groundburst.content.nuclear;

import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Nuclear and exotic materials from the slice 5 machines (DESIGN.md §6, §26.3): enriched and depleted uranium from
 * the Gas Centrifuge, lithium deuteride from the Fusion Fuel Synthesizer, and the Exotic Matter Fabricator's products.
 * All of these are game abstractions (one machine step each), never real processes.
 */
public final class GBNuclear {
    // --- Gas Centrifuge ------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> ENRICHED_URANIUM_NUGGET = GBItems.simple("enriched_uranium_nugget", "Enriched Uranium Nugget", GBTab.RESOURCES);
    public static final DeferredItem<Item> ENRICHED_URANIUM_INGOT = GBItems.simple("enriched_uranium_ingot", "Enriched Uranium Ingot", GBTab.RESOURCES);
    public static final DeferredItem<Item> DEPLETED_URANIUM_DUST = GBItems.simple("depleted_uranium_dust", "Depleted Uranium Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> DEPLETED_URANIUM_INGOT = GBItems.simple("depleted_uranium_ingot", "Depleted Uranium Ingot", GBTab.RESOURCES);

    // --- Fission Reactor (slice 7): fuel, breeding targets and what comes out -------------------------------------------
    public static final DeferredItem<FuelRodItem> URANIUM_FUEL_ROD = GBItems.register("uranium_fuel_rod", "Uranium Fuel Rod", GBTab.NUCLEAR,
            FuelRodItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<Item> SPENT_FUEL_ROD = GBItems.simple("spent_fuel_rod", "Spent Fuel Rod", GBTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> DU_TARGET_ROD = GBItems.simple("du_target_rod", "Depleted Uranium Target Rod", GBTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> LI6_TARGET_ROD = GBItems.simple("li6_target_rod", "Lithium-6 Target Rod", GBTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> IRRADIATED_DU_TARGET_ROD = GBItems.simple("irradiated_du_target_rod", "Irradiated DU Target Rod", GBTab.NUCLEAR,
            p -> p.stacksTo(16));
    public static final DeferredItem<Item> IRRADIATED_LI6_TARGET_ROD = GBItems.simple("irradiated_li6_target_rod", "Irradiated Lithium-6 Target Rod",
            GBTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> PLUTONIUM_NUGGET = GBItems.simple("plutonium_nugget", "Plutonium Nugget", GBTab.RESOURCES);
    public static final DeferredItem<Item> PLUTONIUM_INGOT = GBItems.simple("plutonium_ingot", "Plutonium Ingot", GBTab.RESOURCES);
    /** Fire- and lava-proof, so it cannot be got rid of by burning it (a dropped stack still despawns as usual). */
    public static final DeferredItem<Item> NUCLEAR_WASTE = GBItems.simple("nuclear_waste", "Nuclear Waste", GBTab.NUCLEAR, p -> p.fireResistant());

    // --- Particle Accelerator ------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> ANTIMATTER_CELL = GBItems.simple("antimatter_cell", "Antimatter Cell", GBTab.NUCLEAR,
            p -> p.rarity(Rarity.RARE).stacksTo(16));

    // --- Fusion Fuel Synthesizer ---------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> LITHIUM_DEUTERIDE_PELLET = GBItems.simple("lithium_deuteride_pellet", "Lithium Deuteride Pellet", GBTab.NUCLEAR);

    // --- Exotic Matter Fabricator --------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> STABILISED_EXOTIC_MATTER = GBItems.simple("stabilised_exotic_matter", "Stabilised Exotic Matter", GBTab.NUCLEAR,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CONTAINMENT_CELL = GBItems.simple("containment_cell", "Containment Cell", GBTab.NUCLEAR,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SINGULARITY_SEED = GBItems.simple("singularity_seed", "Singularity Seed", GBTab.NUCLEAR,
            p -> p.rarity(Rarity.EPIC).stacksTo(16));

    // --- Reactor meltdown ----------------------------------------------------------------------------------------------------
    /** What a melted-down core leaves: Corium cools into this. */
    public static final GBBlocks.WithItem<Block> COOLED_CORIUM = GBBlocks.simple("cooled_corium", "Cooled Corium", GBTab.NUCLEAR,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(5f, 30f).requiresCorrectToolForDrops().sound(SoundType.BASALT));
    /** Molten core remains: glowing, burning, cooling over about ten minutes. */
    public static final GBBlocks.WithItem<CoriumBlock> CORIUM = GBBlocks.register("corium", "Corium", GBTab.NUCLEAR,
            p -> new CoriumBlock(p, COOLED_CORIUM.block()),
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(5f, 30f).requiresCorrectToolForDrops().sound(SoundType.BASALT)
                    .lightLevel(s -> 12).randomTicks().isValidSpawn((s, l, pos, type) -> false).emissiveRendering(s -> true));

    private GBNuclear() {}

    public static void init() {}
}
