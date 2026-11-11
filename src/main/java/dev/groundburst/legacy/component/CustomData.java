package dev.groundburst.legacy.component;

import java.util.function.Consumer;

import net.minecraft.nbt.CompoundTag;

/** Minecraft 1.21's custom data of an item (1.20.1's whole tag): read as a copy. */
public final class CustomData {
    public static final CustomData EMPTY = new CustomData(new CompoundTag());

    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag.copy());
    }

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean contains(String key) {
        return this.tag.contains(key);
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public CustomData update(Consumer<CompoundTag> f) {
        CompoundTag copy = this.tag.copy();
        f.accept(copy);
        return new CustomData(copy);
    }
}
