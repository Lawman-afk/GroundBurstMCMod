package dev.groundzero.content.chem;

import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Canisters and chemical intermediates (DESIGN.md §6). Fluids and gases travel as canister items so they stack,
 * slot into machines and show up in JEI like any other ingredient.
 */
public final class GZChemicals {
    public static final DeferredItem<Item> EMPTY_GAS_CANISTER = GZItems.simple("empty_gas_canister", "Empty Gas Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> DEUTERIUM_CANISTER = GZItems.simple("deuterium_canister", "Deuterium Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> LIQUID_OXYGEN_CANISTER = GZItems.simple("liquid_oxygen_canister", "Liquid Oxygen Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> LIQUID_NITROGEN_CANISTER = GZItems.simple("liquid_nitrogen_canister", "Liquid Nitrogen Canister", GZTab.RESOURCES);

    // Chemical Plant products (and the Arc Furnace's mercury flask).
    public static final DeferredItem<Item> SULFURIC_ACID_CANISTER = GZItems.simple("sulfuric_acid_canister", "Sulfuric Acid Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> NITRIC_ACID_CANISTER = GZItems.simple("nitric_acid_canister", "Nitric Acid Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> SYNTHETIC_FUEL_CANISTER = GZItems.simple("synthetic_fuel_canister", "Synthetic Fuel Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> LIQUID_ROCKET_FUEL_CANISTER = GZItems.simple("liquid_rocket_fuel_canister", "Liquid Rocket Fuel Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> THERMOBARIC_SLURRY_CANISTER = GZItems.simple("thermobaric_slurry_canister", "Thermobaric Slurry Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> NAPALM_CANISTER = GZItems.simple("napalm_canister", "Napalm Gel Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> TRITIUM_CANISTER = GZItems.simple("tritium_canister", "Tritium Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> UF6_CANISTER = GZItems.simple("uf6_canister", "UF6 Canister", GZTab.RESOURCES);
    public static final DeferredItem<Item> MERCURY_FLASK = GZItems.simple("mercury_flask", "Mercury Flask", GZTab.RESOURCES);
    public static final DeferredItem<Item> PLASTIC_SHEET = GZItems.simple("plastic_sheet", "Plastic Sheet", GZTab.RESOURCES);
    public static final DeferredItem<Item> RDX_CRYSTALS = GZItems.simple("rdx_crystals", "RDX Crystals", GZTab.RESOURCES);
    public static final DeferredItem<Item> PLASTIC_EXPLOSIVE = GZItems.simple("plastic_explosive", "Plastic Explosive", GZTab.RESOURCES);
    public static final DeferredItem<Item> SOLID_PROPELLANT = GZItems.simple("solid_propellant", "Solid Propellant", GZTab.RESOURCES);
    public static final DeferredItem<Item> YELLOWCAKE = GZItems.simple("yellowcake", "Yellowcake", GZTab.RESOURCES);
    public static final DeferredItem<Item> LITHIUM6_DUST = GZItems.simple("lithium6_dust", "Lithium-6 Dust", GZTab.RESOURCES);

    private GZChemicals() {}

    public static void init() {}
}
