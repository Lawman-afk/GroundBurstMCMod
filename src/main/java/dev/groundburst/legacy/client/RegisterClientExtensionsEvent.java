package dev.groundburst.legacy.client;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * NeoForge's client extension registration (an item's own renderer, a fluid's textures and fog), posted on the mod bus at
 * client setup ({@link ClientLifecycle}). Forge 1.20.1 asks each item, fluid type and block for its own as it is made;
 * these are handed out in their place (dev.groundburst.legacy.mixin's ItemMixin, FluidTypeMixin and BlockMixin).
 */
public final class RegisterClientExtensionsEvent extends Event implements IModBusEvent {
    private static final Map<Item, IClientItemExtensions> ITEMS = new IdentityHashMap<>();
    private static final Map<FluidType, IClientFluidTypeExtensions> FLUIDS = new IdentityHashMap<>();
    private static final Map<Block, IClientBlockExtensions> BLOCKS = new IdentityHashMap<>();

    public RegisterClientExtensionsEvent() {}

    public void registerItem(IClientItemExtensions extensions, ItemLike... items) {
        for (ItemLike item : items) {
            ITEMS.put(item.asItem(), extensions);
        }
    }

    public void registerFluidType(IClientFluidTypeExtensions extensions, FluidType... types) {
        for (FluidType type : types) {
            FLUIDS.put(type, extensions);
        }
    }

    public void registerBlock(IClientBlockExtensions extensions, Block... blocks) {
        for (Block block : blocks) {
            BLOCKS.put(block, extensions);
        }
    }

    public static @Nullable IClientItemExtensions item(Item item) {
        return ITEMS.get(item);
    }

    public static @Nullable IClientFluidTypeExtensions fluidType(FluidType type) {
        return FLUIDS.get(type);
    }

    public static @Nullable IClientBlockExtensions block(Block block) {
        return BLOCKS.get(block);
    }

    public static boolean isRegistered(Item item) {
        return ITEMS.containsKey(item);
    }
}
