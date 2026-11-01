package dev.groundburst.content.chem;

import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Canisters and chemical intermediates (DESIGN.md §6). Fluids and gases travel as canister items so they stack,
 * slot into machines and show up in JEI like any other ingredient.
 */
public final class GBChemicals {
    public static final DeferredItem<Item> EMPTY_GAS_CANISTER = GBItems.simple("empty_gas_canister", "Empty Gas Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> DEUTERIUM_CANISTER = GBItems.simple("deuterium_canister", "Deuterium Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> LIQUID_OXYGEN_CANISTER = GBItems.simple("liquid_oxygen_canister", "Liquid Oxygen Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> LIQUID_NITROGEN_CANISTER = GBItems.simple("liquid_nitrogen_canister", "Liquid Nitrogen Canister", GBTab.RESOURCES);

    // Chemical Plant products (and the Arc Furnace's mercury flask).
    public static final DeferredItem<Item> SULFURIC_ACID_CANISTER = GBItems.simple("sulfuric_acid_canister", "Sulfuric Acid Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> NITRIC_ACID_CANISTER = GBItems.simple("nitric_acid_canister", "Nitric Acid Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> SYNTHETIC_FUEL_CANISTER = GBItems.simple("synthetic_fuel_canister", "Synthetic Fuel Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> LIQUID_ROCKET_FUEL_CANISTER = GBItems.simple("liquid_rocket_fuel_canister", "Liquid Rocket Fuel Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> THERMOBARIC_SLURRY_CANISTER = GBItems.simple("thermobaric_slurry_canister", "Thermobaric Slurry Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> NAPALM_CANISTER = GBItems.simple("napalm_canister", "Napalm Gel Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> HEAVY_WATER_CANISTER = GBItems.simple("heavy_water_canister", "Heavy Water Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> TRITIUM_CANISTER = GBItems.simple("tritium_canister", "Tritium Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> UF6_CANISTER = GBItems.simple("uf6_canister", "UF6 Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> CHLORINE_CANISTER = GBItems.simple("chlorine_canister", "Chlorine Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> TOXIN_CANISTER = GBItems.simple("toxin_canister", "Toxin Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> SALT = GBItems.simple("salt", "Salt", GBTab.RESOURCES);
    public static final DeferredItem<Item> MERCURY_FLASK = GBItems.simple("mercury_flask", "Mercury Flask", GBTab.RESOURCES);
    public static final DeferredItem<Item> PLASTIC_SHEET = GBItems.simple("plastic_sheet", "Plastic Sheet", GBTab.RESOURCES);
    public static final DeferredItem<Item> RDX_CRYSTALS = GBItems.simple("rdx_crystals", "RDX Crystals", GBTab.RESOURCES);
    public static final DeferredItem<Item> PLASTIC_EXPLOSIVE = GBItems.simple("plastic_explosive", "Plastic Explosive", GBTab.RESOURCES);
    public static final DeferredItem<Item> SOLID_PROPELLANT = GBItems.simple("solid_propellant", "Solid Propellant", GBTab.RESOURCES);
    public static final DeferredItem<Item> YELLOWCAKE = GBItems.simple("yellowcake", "Yellowcake", GBTab.RESOURCES);
    public static final DeferredItem<Item> LITHIUM6_DUST = GBItems.simple("lithium6_dust", "Lithium-6 Dust", GBTab.RESOURCES);

    // The semiconductor chain (DESIGN.md §8.1).
    public static final DeferredItem<Item> HYDROGEN_CANISTER = GBItems.simple("hydrogen_canister", "Hydrogen Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> HYDROGEN_CHLORIDE_CANISTER = GBItems.simple("hydrogen_chloride_canister", "Hydrogen Chloride Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> TRICHLOROSILANE_CANISTER = GBItems.simple("trichlorosilane_canister", "Trichlorosilane Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> ARGON_CANISTER = GBItems.simple("argon_canister", "Argon Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> PHOTORESIST_CANISTER = GBItems.simple("photoresist_canister", "Photoresist Canister", GBTab.RESOURCES);
    public static final DeferredItem<Item> HYDROFLUORIC_ACID_CANISTER = GBItems.simple("hydrofluoric_acid_canister", "Hydrofluoric Acid Canister", GBTab.RESOURCES);
    /** The chips' dopant: bone meal (calcium phosphate) reduced with coal in the Arc Furnace. */
    public static final DeferredItem<Item> RED_PHOSPHORUS = GBItems.simple("red_phosphorus", "Red Phosphorus", GBTab.RESOURCES);

    private GBChemicals() {}

    public static void init() {}
}
