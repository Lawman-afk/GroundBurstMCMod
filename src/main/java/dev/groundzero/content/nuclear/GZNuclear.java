package dev.groundzero.content.nuclear;

import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Nuclear and exotic materials from the slice 5 machines (DESIGN.md §6, §26.3): enriched and depleted uranium from
 * the Gas Centrifuge, lithium deuteride from the Fusion Fuel Synthesizer, and the Exotic Matter Fabricator's products.
 * All of these are game abstractions (one machine step each), never real processes.
 */
public final class GZNuclear {
    // --- Gas Centrifuge ------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> ENRICHED_URANIUM_NUGGET = GZItems.simple("enriched_uranium_nugget", "Enriched Uranium Nugget", GZTab.RESOURCES);
    public static final DeferredItem<Item> ENRICHED_URANIUM_INGOT = GZItems.simple("enriched_uranium_ingot", "Enriched Uranium Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> DEPLETED_URANIUM_DUST = GZItems.simple("depleted_uranium_dust", "Depleted Uranium Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> DEPLETED_URANIUM_INGOT = GZItems.simple("depleted_uranium_ingot", "Depleted Uranium Ingot", GZTab.RESOURCES);

    // --- Fission Reactor (slice 7): fuel, breeding targets and what comes out -------------------------------------------
    public static final DeferredItem<Item> URANIUM_FUEL_ROD = GZItems.simple("uranium_fuel_rod", "Uranium Fuel Rod", GZTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> SPENT_FUEL_ROD = GZItems.simple("spent_fuel_rod", "Spent Fuel Rod", GZTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> DU_TARGET_ROD = GZItems.simple("du_target_rod", "Depleted Uranium Target Rod", GZTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> LI6_TARGET_ROD = GZItems.simple("li6_target_rod", "Lithium-6 Target Rod", GZTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> IRRADIATED_DU_TARGET_ROD = GZItems.simple("irradiated_du_target_rod", "Irradiated DU Target Rod", GZTab.NUCLEAR,
            p -> p.stacksTo(16));
    public static final DeferredItem<Item> IRRADIATED_LI6_TARGET_ROD = GZItems.simple("irradiated_li6_target_rod", "Irradiated Lithium-6 Target Rod",
            GZTab.NUCLEAR, p -> p.stacksTo(16));
    public static final DeferredItem<Item> PLUTONIUM_NUGGET = GZItems.simple("plutonium_nugget", "Plutonium Nugget", GZTab.RESOURCES);
    public static final DeferredItem<Item> PLUTONIUM_INGOT = GZItems.simple("plutonium_ingot", "Plutonium Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> NUCLEAR_WASTE = GZItems.simple("nuclear_waste", "Nuclear Waste", GZTab.NUCLEAR);

    // --- Particle Accelerator ------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> ANTIMATTER_CELL = GZItems.simple("antimatter_cell", "Antimatter Cell", GZTab.NUCLEAR,
            p -> p.rarity(Rarity.RARE).stacksTo(16));

    // --- Fusion Fuel Synthesizer ---------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> LITHIUM_DEUTERIDE_PELLET = GZItems.simple("lithium_deuteride_pellet", "Lithium Deuteride Pellet", GZTab.NUCLEAR);

    // --- Exotic Matter Fabricator --------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> STABILISED_EXOTIC_MATTER = GZItems.simple("stabilised_exotic_matter", "Stabilised Exotic Matter", GZTab.NUCLEAR,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CONTAINMENT_CELL = GZItems.simple("containment_cell", "Containment Cell", GZTab.NUCLEAR,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SINGULARITY_SEED = GZItems.simple("singularity_seed", "Singularity Seed", GZTab.NUCLEAR,
            p -> p.rarity(Rarity.EPIC).stacksTo(16));

    private GZNuclear() {}

    public static void init() {}
}
