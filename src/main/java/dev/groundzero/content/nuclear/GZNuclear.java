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
