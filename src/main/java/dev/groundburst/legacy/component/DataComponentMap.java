package dev.groundburst.legacy.component;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Minecraft 1.21's components of a stack, read from a copy of it (1.20.1: its tag, with its item's defaults: {@link Comps}). */
public final class DataComponentMap {
    private final ItemStack stack;

    private DataComponentMap(ItemStack stack) {
        this.stack = stack;
    }

    public static DataComponentMap of(ItemStack stack) {
        return new DataComponentMap(stack.copy());
    }

    /** The data a block entity keeps in its item (1.21's BlockEntity.collectComponents), read off an item made to hold it. */
    public static DataComponentMap collect(net.minecraft.world.level.block.entity.BlockEntity be) {
        ItemStack carrier = new ItemStack(be.getBlockState().getBlock().asItem());
        if (be instanceof ImplicitComponents implicit) {
            Builder b = new Builder();
            implicit.collectImplicitComponents(b);
            b.applyTo(carrier, null);
        }
        return new DataComponentMap(carrier);
    }

    /** A fluid's components: its tag, read as an item's would be (only the tag matters to a component). */
    public static DataComponentMap of(FluidStack stack) {
        ItemStack carrier = new ItemStack(net.minecraft.world.item.Items.BUCKET);
        carrier.setTag(stack.getTag() == null ? null : stack.getTag().copy());
        return new DataComponentMap(carrier);
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

    public boolean has(DataComponentType<?> type) {
        return Comps.has(this.stack, type);
    }

    public boolean isEmpty() {
        return this.stack.getTag() == null;
    }

    /** Components gathered to be put on an item (a block entity's, as it drops: {@link ImplicitComponents}). */
    public static final class Builder {
        private final java.util.Map<DataComponentType<?>, Object> values = new java.util.LinkedHashMap<>();

        public <T> Builder set(DataComponentType<T> type, @Nullable T value) {
            if (value == null) {
                this.values.remove(type);
            } else {
                this.values.put(type, value);
            }
            return this;
        }

        public <T> Builder set(Supplier<? extends DataComponentType<T>> type, @Nullable T value) {
            return set(type.get(), value);
        }

        /** Puts those named in {@code include} (all, if it is null) on {@code stack}. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void applyTo(ItemStack stack, java.util.@Nullable Set<net.minecraft.resources.ResourceLocation> include) {
            for (java.util.Map.Entry<DataComponentType<?>, Object> e : this.values.entrySet()) {
                if (include == null || include.contains(e.getKey().id())) {
                    Comps.set(stack, (DataComponentType) e.getKey(), e.getValue());
                }
            }
        }
    }
}
