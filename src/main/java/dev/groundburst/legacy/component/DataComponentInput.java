package dev.groundburst.legacy.component;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

/** Minecraft 1.21's components a block entity is placed with: its item's. */
public final class DataComponentInput {
    private final ItemStack stack;

    public DataComponentInput(ItemStack stack) {
        this.stack = stack;
    }

    /** A block entity given its item's data (1.21's BlockEntity.applyComponentsFromItemStack). */
    public static void apply(net.minecraft.world.level.block.entity.BlockEntity be, ItemStack stack) {
        if (be instanceof ImplicitComponents implicit) {
            implicit.applyImplicitComponents(new DataComponentInput(stack));
            be.setChanged();
        }
    }

    public <T> @Nullable T get(DataComponentType<T> type) {
        return Comps.get(this.stack, type);
    }

    public <T> @Nullable T get(Supplier<? extends DataComponentType<T>> type) {
        return Comps.get(this.stack, type);
    }

    public <T> T getOrDefault(DataComponentType<T> type, T fallback) {
        return Comps.getOrDefault(this.stack, type, fallback);
    }

    public <T> T getOrDefault(Supplier<? extends DataComponentType<T>> type, T fallback) {
        return Comps.getOrDefault(this.stack, type, fallback);
    }
}
