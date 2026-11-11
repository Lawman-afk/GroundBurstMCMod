package dev.groundburst.legacy.component;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft 1.21's component calls on an item stack ({@code stack.get(type)}, {@code set}, {@code getOrDefault}...), as
 * the port writes them on 1.20.1 ({@code Comps.get(stack, type)}): the value in the stack's tag, or else its item's
 * default (an item's components given with its properties: {@link #withDefault}).
 */
public final class Comps {
    private Comps() {}

    /** Item properties given defaults, until their item is made (dev.groundburst.legacy.mixin.ItemMixin takes them). */
    private static final Map<Item.Properties, Map<DataComponentType<?>, Object>> PENDING = new IdentityHashMap<>();
    /** Each item's default components. */
    private static final Map<Item, Map<DataComponentType<?>, Object>> DEFAULTS = new IdentityHashMap<>();

    /** 1.21's {@code properties.component(type, value)}: the item made with these properties has it by default. */
    public static <T> Item.Properties withDefault(Item.Properties properties, DataComponentType<T> type, T value) {
        synchronized (PENDING) {
            PENDING.computeIfAbsent(properties, p -> new HashMap<>()).put(type, value);
        }
        return properties;
    }

    public static <T> Item.Properties withDefault(Item.Properties properties, Supplier<? extends DataComponentType<T>> type, T value) {
        return withDefault(properties, type.get(), value);
    }

    /** Called as an item is made: its properties' defaults become its own. */
    public static void itemMade(Item item, Item.Properties properties) {
        Map<DataComponentType<?>, Object> defaults;
        synchronized (PENDING) {
            defaults = PENDING.remove(properties);
        }
        if (defaults != null) {
            synchronized (DEFAULTS) {
                DEFAULTS.put(item, defaults);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> @Nullable T defaultOf(Item item, DataComponentType<T> type) {
        synchronized (DEFAULTS) {
            Map<DataComponentType<?>, Object> map = DEFAULTS.get(item);
            return map == null ? null : (T) map.get(type);
        }
    }

    public static <T> @Nullable T get(ItemStack stack, DataComponentType<T> type) {
        if (stack.isEmpty()) {
            return null;
        }
        T value = type.read(stack);
        return value != null ? value : defaultOf(stack.getItem(), type);
    }

    public static <T> @Nullable T get(ItemStack stack, Supplier<? extends DataComponentType<T>> type) {
        return get(stack, type.get());
    }

    public static <T> T getOrDefault(ItemStack stack, DataComponentType<T> type, T fallback) {
        T value = get(stack, type);
        return value != null ? value : fallback;
    }

    public static <T> T getOrDefault(ItemStack stack, Supplier<? extends DataComponentType<T>> type, T fallback) {
        return getOrDefault(stack, type.get(), fallback);
    }

    public static boolean has(ItemStack stack, DataComponentType<?> type) {
        return !stack.isEmpty() && (type.present(stack) || defaultOf(stack.getItem(), type) != null);
    }

    public static boolean has(ItemStack stack, Supplier<? extends DataComponentType<?>> type) {
        return has(stack, type.get());
    }

    public static <T> @Nullable T set(ItemStack stack, DataComponentType<T> type, @Nullable T value) {
        T before = get(stack, type);
        if (!stack.isEmpty()) {
            type.write(stack, value);
        }
        return before;
    }

    public static <T> @Nullable T set(ItemStack stack, Supplier<? extends DataComponentType<T>> type, @Nullable T value) {
        return set(stack, type.get(), value);
    }

    public static <T> @Nullable T remove(ItemStack stack, DataComponentType<T> type) {
        T before = get(stack, type);
        type.write(stack, null);
        return before;
    }

    public static <T> @Nullable T remove(ItemStack stack, Supplier<? extends DataComponentType<T>> type) {
        return remove(stack, type.get());
    }

    public static <T> @Nullable T update(ItemStack stack, DataComponentType<T> type, T fallback, UnaryOperator<T> f) {
        return set(stack, type, f.apply(getOrDefault(stack, type, fallback)));
    }

    public static <T> @Nullable T update(ItemStack stack, Supplier<? extends DataComponentType<T>> type, T fallback, UnaryOperator<T> f) {
        return update(stack, type.get(), fallback, f);
    }

    // A block entity's components gathered as it drops, and read as it is placed (ImplicitComponents).
    public static <T> void set(DataComponentMap.Builder components, DataComponentType<T> type, @Nullable T value) {
        components.set(type, value);
    }

    public static <T> @Nullable T get(DataComponentInput components, DataComponentType<T> type) {
        return components.get(type);
    }

    public static <T> T getOrDefault(DataComponentInput components, DataComponentType<T> type, T fallback) {
        return components.getOrDefault(type, fallback);
    }

    public static <T> @Nullable T get(DataComponentMap map, DataComponentType<T> type) {
        return map.get(type);
    }

    public static <T> T getOrDefault(DataComponentMap map, DataComponentType<T> type, T fallback) {
        return map.getOrDefault(type, fallback);
    }

    public static boolean has(DataComponentMap map, DataComponentType<?> type) {
        return map.has(type);
    }
}
