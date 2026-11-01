package dev.groundburst.content.part;

import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/** Crafting parts (DESIGN.md §26.2): alloys, plates, rods, wire, canisters, assemblies and press dies. */
public final class GBParts {
    // --- materials -------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> STEEL_INGOT = GBItems.simple("steel_ingot", "Steel Ingot", GBTab.RESOURCES);
    public static final DeferredItem<Item> COBALT_INGOT = GBItems.simple("cobalt_ingot", "Cobalt Ingot", GBTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_CARBIDE_INGOT = GBItems.simple("tungsten_carbide_ingot", "Tungsten Carbide Ingot", GBTab.RESOURCES);
    public static final DeferredItem<Item> STEEL_PLATE = GBItems.simple("steel_plate", "Steel Plate", GBTab.RESOURCES);
    public static final DeferredItem<Item> DENSE_STEEL_PLATE = GBItems.simple("dense_steel_plate", "Dense Steel Plate", GBTab.RESOURCES);
    public static final DeferredItem<Item> COPPER_PLATE = GBItems.simple("copper_plate", "Copper Plate", GBTab.RESOURCES);
    public static final DeferredItem<Item> LEAD_PLATE = GBItems.simple("lead_plate", "Lead Plate", GBTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_PLATE = GBItems.simple("tungsten_plate", "Tungsten Plate", GBTab.RESOURCES);
    public static final DeferredItem<Item> STEEL_ROD = GBItems.simple("steel_rod", "Steel Rod", GBTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_ROD = GBItems.simple("tungsten_rod", "Tungsten Rod", GBTab.RESOURCES);
    public static final DeferredItem<Item> COPPER_WIRE = GBItems.simple("copper_wire", "Copper Wire", GBTab.RESOURCES);
    public static final DeferredItem<Item> GOLD_WIRE = GBItems.simple("gold_wire", "Gold Wire", GBTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_FILAMENT = GBItems.simple("tungsten_filament", "Tungsten Filament", GBTab.RESOURCES);
    public static final DeferredItem<Item> NEODYMIUM_MAGNET = GBItems.simple("neodymium_magnet", "Neodymium Magnet", GBTab.RESOURCES);
    public static final DeferredItem<Item> EMPTY_CANISTER = GBItems.simple("empty_canister", "Empty Canister", GBTab.RESOURCES);

    // --- assemblies ----------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> HANDMADE_CIRCUIT_BOARD = GBItems.simple("handmade_circuit_board", "Handmade Circuit Board", GBTab.MACHINES);
    public static final DeferredItem<Item> MACHINE_FRAME = GBItems.simple("machine_frame", "Machine Frame", GBTab.MACHINES);
    public static final DeferredItem<Item> ELECTRIC_MOTOR = GBItems.simple("electric_motor", "Electric Motor", GBTab.MACHINES);
    public static final DeferredItem<Item> PRESSURE_VESSEL = GBItems.simple("pressure_vessel", "Pressure Vessel", GBTab.MACHINES);
    public static final DeferredItem<Item> HEATING_ELEMENT = GBItems.simple("heating_element", "Heating Element", GBTab.MACHINES);
    public static final DeferredItem<Item> SERVO = GBItems.simple("servo", "Servo", GBTab.MACHINES);
    /** Rechargeable FE cell (charged in the Battery Charger). */
    public static final DeferredItem<dev.groundburst.content.power.BatteryItem> BATTERY = GBItems.register("battery", "Battery", GBTab.MACHINES,
            dev.groundburst.content.power.BatteryItem::new);

    public static final DeferredItem<Item> CRT_DISPLAY = GBItems.simple("crt_display", "CRT Display", GBTab.MACHINES);
    public static final DeferredItem<Item> CAPACITOR = GBItems.simple("capacitor", "Capacitor", GBTab.MACHINES);

    // --- Circuit Fabricator products (DESIGN.md §26.2) -----------------------------------------------------------------------
    public static final DeferredItem<Item> BASIC_CIRCUIT = GBItems.simple("basic_circuit", "Basic Circuit", GBTab.MACHINES);
    public static final DeferredItem<Item> ADVANCED_CIRCUIT = GBItems.simple("advanced_circuit", "Advanced Circuit", GBTab.MACHINES);
    public static final DeferredItem<Item> MILITARY_CIRCUIT = GBItems.simple("military_circuit", "Military Circuit", GBTab.MACHINES);
    public static final DeferredItem<Item> QUANTUM_CIRCUIT = GBItems.simple("quantum_circuit", "Quantum Circuit", GBTab.MACHINES,
            p -> p.rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    public static final DeferredItem<Item> SENSOR_ARRAY = GBItems.simple("sensor_array", "Sensor Array", GBTab.MACHINES);
    public static final DeferredItem<Item> SUPERCONDUCTING_COIL = GBItems.simple("superconducting_coil", "Superconducting Coil", GBTab.MACHINES);

    // --- the semiconductor chain (DESIGN.md §8.1) -----------------------------------------------------------------------
    /** Metallurgical silicon from the Arc Furnace (quartz reduced with coal). */
    public static final DeferredItem<Item> SILICON = GBItems.simple("silicon", "Silicon", GBTab.RESOURCES);
    /** Silicon purified through trichlorosilane (the Siemens process): what crystals are grown from. */
    public static final DeferredItem<Item> POLYSILICON = GBItems.simple("polysilicon", "Polysilicon", GBTab.RESOURCES);
    /** Fused quartz; one holds the melt for each crystal grown. */
    public static final DeferredItem<Item> QUARTZ_CRUCIBLE = GBItems.simple("quartz_crucible", "Quartz Crucible", GBTab.RESOURCES);
    /** A military chip's sealed housing: co-fired alumina with gold-plated leads. */
    public static final DeferredItem<Item> CERAMIC_PACKAGE = GBItems.simple("ceramic_package", "Ceramic Package", GBTab.RESOURCES);

    // --- press dies (not consumed) -----------------------------------------------------------------------------------------
    public static final DeferredItem<Item> PLATE_DIE = die("plate_die", "Plate Die");
    public static final DeferredItem<Item> ROD_DIE = die("rod_die", "Rod Die");
    public static final DeferredItem<Item> WIRE_DIE = die("wire_die", "Wire Die");
    public static final DeferredItem<Item> CANISTER_DIE = die("canister_die", "Canister Die");

    // --- condenser membranes (not consumed) ----------------------------------------------------------------------------------
    public static final DeferredItem<Item> OXYGEN_MEMBRANE = GBItems.simple("oxygen_membrane", "Oxygen Separation Membrane", GBTab.MACHINES, p -> p.stacksTo(1));
    public static final DeferredItem<Item> NITROGEN_MEMBRANE = GBItems.simple("nitrogen_membrane", "Nitrogen Separation Membrane", GBTab.MACHINES, p -> p.stacksTo(1));
    /** A zeolite sieve that holds back oxygen and nitrogen and lets argon, 1% of the air, through. */
    public static final DeferredItem<Item> ARGON_SIEVE = GBItems.simple("argon_sieve", "Argon Molecular Sieve", GBTab.MACHINES, p -> p.stacksTo(1));

    private GBParts() {}

    public static void init() {}

    private static DeferredItem<Item> die(String name, String english) {
        return GBItems.simple(name, english, GBTab.MACHINES, p -> p.stacksTo(1));
    }
}
