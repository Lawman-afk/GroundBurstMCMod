package dev.groundburst.port.transfer;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.port.transfer.access.ItemAccess;
import dev.groundburst.port.transfer.energy.EnergyHandler;
import dev.groundburst.port.transfer.fluid.FluidResource;
import dev.groundburst.port.transfer.item.ItemResource;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * The mod's item, fluid and energy capabilities, as main has them (NeoForge 26.x's transfer API), over this branch's own
 * handlers; and the bridge to 1.21.1's: each of the mod's blocks and items offers 1.21.1's item, fluid and energy
 * handlers too (so hoppers and other mods' pipes reach it), and every other block and item answers the mod's
 * capabilities through its 1.21.1 handlers (so the mod's pipes reach a chest or another mod's tank).
 */
public final class Capabilities {
    private Capabilities() {}

    public static final class Item {
        public static final BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> BLOCK =
                BlockCapability.createSided(id("item_handler"), ResourceHandler.asClass());
        public static final ItemCapability<ResourceHandler<ItemResource>, ItemAccess> ITEM =
                ItemCapability.create(id("item_handler"), ResourceHandler.asClass(), ItemAccess.class);
    }

    public static final class Fluid {
        public static final BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction> BLOCK =
                BlockCapability.createSided(id("fluid_handler"), ResourceHandler.asClass());
        public static final ItemCapability<ResourceHandler<FluidResource>, ItemAccess> ITEM =
                ItemCapability.create(id("fluid_handler"), ResourceHandler.asClass(), ItemAccess.class);
    }

    public static final class Energy {
        public static final BlockCapability<EnergyHandler, @Nullable Direction> BLOCK = BlockCapability.createSided(id("energy_handler"), EnergyHandler.class);
        public static final ItemCapability<EnergyHandler, ItemAccess> ITEM = ItemCapability.create(id("energy_handler"), EnergyHandler.class, ItemAccess.class);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GroundBurst.MODID, "port_" + path);
    }

    /** Bridges them to 1.21.1's, after the mod has registered its own. */
    public static void register(IEventBus modBus) {
        modBus.addListener(EventPriority.LOWEST, Capabilities::bridge);
    }

    private static boolean ours(ResourceLocation id) {
        return GroundBurst.MODID.equals(id.getNamespace());
    }

    private static void bridge(RegisterCapabilitiesEvent event) {
        List<Block> mine = new ArrayList<>(), others = new ArrayList<>();
        for (var e : BuiltInRegistries.BLOCK.entrySet()) {
            (ours(e.getKey().location()) ? mine : others).add(e.getValue());
        }
        Block[] mineA = mine.toArray(Block[]::new), othersA = others.toArray(Block[]::new);
        // The mod's blocks, seen through 1.21.1's handlers.
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> Adapters.itemHandler(level.getCapability(Item.BLOCK, pos, state, be, side)), mineA);
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> Adapters.fluidHandler(level.getCapability(Fluid.BLOCK, pos, state, be, side)), mineA);
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> Adapters.energyStorage(level.getCapability(Energy.BLOCK, pos, state, be, side)), mineA);
        // Everything else, seen through the mod's.
        event.registerBlock(Item.BLOCK, (level, pos, state, be, side) -> Adapters.foreignItems(
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state, be, side)), othersA);
        event.registerBlock(Fluid.BLOCK, (level, pos, state, be, side) -> Adapters.foreignFluids(
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, state, be, side)), othersA);
        event.registerBlock(Energy.BLOCK, (level, pos, state, be, side) -> Adapters.foreignEnergy(
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, state, be, side)), othersA);

        List<net.minecraft.world.item.Item> myItems = new ArrayList<>(), otherItems = new ArrayList<>();
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            (ours(e.getKey().location()) ? myItems : otherItems).add(e.getValue());
        }
        var myItemsA = myItems.toArray(net.minecraft.world.item.Item[]::new);
        var otherItemsA = otherItems.toArray(net.minecraft.world.item.Item[]::new);
        // The mod's items (canisters, batteries), seen through 1.21.1's handlers.
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM, (stack, ctx) -> Adapters.fluidItem(stack), myItemsA);
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, ctx) -> Adapters.energyItem(stack), myItemsA);
        // Other items (buckets, other mods' cells), seen through the mod's.
        event.registerItem(Fluid.ITEM, (stack, access) -> Adapters.foreignFluidItem(stack, access), otherItemsA);
        event.registerItem(Energy.ITEM, (stack, access) -> Adapters.foreignEnergyItem(stack, access), otherItemsA);
    }
}
