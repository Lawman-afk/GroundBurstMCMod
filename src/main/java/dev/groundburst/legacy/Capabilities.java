package dev.groundburst.legacy;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;

/**
 * NeoForge's own capabilities: item, fluid and energy handlers, the same interfaces as Forge 1.20.1's. Registered here
 * by the mod for its blocks and items (and given to Forge for other mods: {@link CapabilityBridge}); asked of anything
 * else, they are Forge's.
 */
public final class Capabilities {
    private Capabilities() {}

    public static final class ItemHandler {
        private ItemHandler() {}

        public static final BlockCapability<IItemHandler, @Nullable Direction> BLOCK =
                BlockCapability.forgeSided(new ResourceLocation("neoforge", "item_handler"), IItemHandler.class, ForgeCapabilities.ITEM_HANDLER);
        public static final ItemCapability<IItemHandler, @Nullable Void> ITEM =
                ItemCapability.forge(new ResourceLocation("neoforge", "item_handler"), IItemHandler.class, ForgeCapabilities.ITEM_HANDLER);
    }

    public static final class FluidHandler {
        private FluidHandler() {}

        public static final BlockCapability<IFluidHandler, @Nullable Direction> BLOCK =
                BlockCapability.forgeSided(new ResourceLocation("neoforge", "fluid_handler"), IFluidHandler.class, ForgeCapabilities.FLUID_HANDLER);
        public static final ItemCapability<IFluidHandlerItem, @Nullable Void> ITEM =
                ItemCapability.forge(new ResourceLocation("neoforge", "fluid_handler"), IFluidHandlerItem.class, ForgeCapabilities.FLUID_HANDLER_ITEM);
    }

    public static final class EnergyStorage {
        private EnergyStorage() {}

        public static final BlockCapability<IEnergyStorage, @Nullable Direction> BLOCK =
                BlockCapability.forgeSided(new ResourceLocation("neoforge", "energy"), IEnergyStorage.class, ForgeCapabilities.ENERGY);
        public static final ItemCapability<IEnergyStorage, @Nullable Void> ITEM =
                ItemCapability.forge(new ResourceLocation("neoforge", "energy"), IEnergyStorage.class, ForgeCapabilities.ENERGY);
    }
}
