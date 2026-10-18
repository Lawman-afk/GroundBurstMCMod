package dev.groundzero.content.part;

import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/** Crafting parts (DESIGN.md §26.2): alloys, plates, rods, wire, canisters, assemblies and press dies. */
public final class GZParts {
    // --- materials -------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> STEEL_INGOT = GZItems.simple("steel_ingot", "Steel Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> COBALT_INGOT = GZItems.simple("cobalt_ingot", "Cobalt Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_CARBIDE_INGOT = GZItems.simple("tungsten_carbide_ingot", "Tungsten Carbide Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> STEEL_PLATE = GZItems.simple("steel_plate", "Steel Plate", GZTab.RESOURCES);
    public static final DeferredItem<Item> DENSE_STEEL_PLATE = GZItems.simple("dense_steel_plate", "Dense Steel Plate", GZTab.RESOURCES);
    public static final DeferredItem<Item> COPPER_PLATE = GZItems.simple("copper_plate", "Copper Plate", GZTab.RESOURCES);
    public static final DeferredItem<Item> LEAD_PLATE = GZItems.simple("lead_plate", "Lead Plate", GZTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_PLATE = GZItems.simple("tungsten_plate", "Tungsten Plate", GZTab.RESOURCES);
    public static final DeferredItem<Item> STEEL_ROD = GZItems.simple("steel_rod", "Steel Rod", GZTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_ROD = GZItems.simple("tungsten_rod", "Tungsten Rod", GZTab.RESOURCES);
    public static final DeferredItem<Item> COPPER_WIRE = GZItems.simple("copper_wire", "Copper Wire", GZTab.RESOURCES);
    public static final DeferredItem<Item> GOLD_WIRE = GZItems.simple("gold_wire", "Gold Wire", GZTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_FILAMENT = GZItems.simple("tungsten_filament", "Tungsten Filament", GZTab.RESOURCES);
    public static final DeferredItem<Item> NEODYMIUM_MAGNET = GZItems.simple("neodymium_magnet", "Neodymium Magnet", GZTab.RESOURCES);
    public static final DeferredItem<Item> EMPTY_CANISTER = GZItems.simple("empty_canister", "Empty Canister", GZTab.RESOURCES);

    // --- assemblies ----------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> HANDMADE_CIRCUIT_BOARD = GZItems.simple("handmade_circuit_board", "Handmade Circuit Board", GZTab.MACHINES);
    public static final DeferredItem<Item> MACHINE_FRAME = GZItems.simple("machine_frame", "Machine Frame", GZTab.MACHINES);
    public static final DeferredItem<Item> ELECTRIC_MOTOR = GZItems.simple("electric_motor", "Electric Motor", GZTab.MACHINES);
    public static final DeferredItem<Item> PRESSURE_VESSEL = GZItems.simple("pressure_vessel", "Pressure Vessel", GZTab.MACHINES);
    public static final DeferredItem<Item> HEATING_ELEMENT = GZItems.simple("heating_element", "Heating Element", GZTab.MACHINES);
    public static final DeferredItem<Item> SERVO = GZItems.simple("servo", "Servo", GZTab.MACHINES);

    // --- press dies (not consumed) -----------------------------------------------------------------------------------------
    public static final DeferredItem<Item> PLATE_DIE = die("plate_die", "Plate Die");
    public static final DeferredItem<Item> ROD_DIE = die("rod_die", "Rod Die");
    public static final DeferredItem<Item> WIRE_DIE = die("wire_die", "Wire Die");
    public static final DeferredItem<Item> CANISTER_DIE = die("canister_die", "Canister Die");

    private GZParts() {}

    public static void init() {}

    private static DeferredItem<Item> die(String name, String english) {
        return GZItems.simple(name, english, GZTab.MACHINES, p -> p.stacksTo(1));
    }
}
