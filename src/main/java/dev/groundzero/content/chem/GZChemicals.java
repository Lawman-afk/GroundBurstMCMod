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

    private GZChemicals() {}

    public static void init() {}
}
