package dev.groundburst.legacy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** NeoForge's handle on a registered block (over Forge 1.20.1's RegistryObject): an ItemLike too, as its item. */
public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {
    public static <T extends Block> DeferredBlock<T> createBlock(ResourceLocation key) {
        return createBlock(ResourceKey.create(Registries.BLOCK, key));
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceKey<Block> key) {
        return new DeferredBlock<>(key);
    }

    protected DeferredBlock(ResourceKey<Block> key) {
        super(key);
    }

    /** The holder of a block Forge's register registers (bound as it is registered: {@link DeferredHolder}). */
    protected DeferredBlock(ResourceKey<Block> key, net.minecraftforge.registries.RegistryObject<? extends Block> object) {
        super(key, object);
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
