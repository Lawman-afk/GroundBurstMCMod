package dev.groundburst.legacy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** NeoForge's handle on a registered item (over Forge 1.20.1's RegistryObject): an ItemLike too. */
public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> implements ItemLike {
    public static <T extends Item> DeferredItem<T> createItem(ResourceLocation key) {
        return createItem(ResourceKey.create(Registries.ITEM, key));
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceKey<Item> key) {
        return new DeferredItem<>(key);
    }

    protected DeferredItem(ResourceKey<Item> key) {
        super(key);
    }

    /** The holder of an item Forge's register registers (bound as it is registered: {@link DeferredHolder}). */
    protected DeferredItem(ResourceKey<Item> key, net.minecraftforge.registries.RegistryObject<? extends Item> object) {
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
        return get();
    }
}
