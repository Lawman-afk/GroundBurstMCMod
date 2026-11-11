package dev.groundburst.legacy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * NeoForge's capability registration: posted on the mod bus at common setup ({@link Lifecycle}), its registrations kept
 * by the capabilities themselves ({@link BlockCapability}, {@link ItemCapability}).
 */
public final class RegisterCapabilitiesEvent extends Event implements IModBusEvent {
    public RegisterCapabilitiesEvent() {}

    public <T, C> void registerBlock(BlockCapability<T, C> capability, IBlockCapabilityProvider<T, C> provider, Block... blocks) {
        for (Block block : blocks) {
            capability.addBlock(block, provider);
        }
    }

    public <T, C, BE extends BlockEntity> void registerBlockEntity(BlockCapability<T, C> capability, BlockEntityType<BE> type,
            ICapabilityProvider<? super BE, C, T> provider) {
        capability.addBlockEntity(type, provider);
    }

    public <T, C> void registerItem(ItemCapability<T, C> capability, ICapabilityProvider<ItemStack, C, T> provider, ItemLike... items) {
        for (ItemLike item : items) {
            capability.add(item.asItem(), provider);
        }
    }

    public boolean isBlockRegistered(BlockCapability<?, ?> capability, Block block) {
        return capability.hasProviders(block);
    }

    public boolean isItemRegistered(ItemCapability<?, ?> capability, net.minecraft.world.item.Item item) {
        return capability.byItem.containsKey(item);
    }
}
