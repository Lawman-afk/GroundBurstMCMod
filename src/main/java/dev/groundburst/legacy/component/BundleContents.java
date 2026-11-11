package dev.groundburst.legacy.component;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** Minecraft 1.21's bundle contents (1.20.1's "Items" in a bundle's tag). */
public record BundleContents(List<ItemStack> list) {
    public Iterable<ItemStack> items() {
        return this.list;
    }

    public int size() {
        return this.list.size();
    }

    public boolean isEmpty() {
        return this.list.isEmpty();
    }
}
