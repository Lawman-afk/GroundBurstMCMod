package dev.groundzero.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import dev.groundzero.GroundZero;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registration helpers. Every item is registered together with its English name and creative tab,
 * so the language file and tab contents are derived from one place.
 */
public final class GZItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GroundZero.MODID);

    private static final Map<GZTab, List<DeferredItem<? extends Item>>> TAB_CONTENTS = new EnumMap<>(GZTab.class);
    private static final Map<DeferredItem<? extends Item>, GZTab> TAB_OF = new IdentityHashMap<>();

    private GZItems() {}

    public static <I extends Item> DeferredItem<I> register(String name, String english, GZTab tab,
            Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
        DeferredItem<I> item = ITEMS.registerItem(name, factory, properties);
        GZLang.add("item." + GroundZero.MODID + "." + name, english);
        addToTab(item, tab);
        return item;
    }

    public static <I extends Item> DeferredItem<I> register(String name, String english, GZTab tab, Function<Item.Properties, ? extends I> factory) {
        return register(name, english, tab, factory, UnaryOperator.identity());
    }

    public static DeferredItem<Item> simple(String name, String english, GZTab tab) {
        return register(name, english, tab, Item::new);
    }

    public static DeferredItem<Item> simple(String name, String english, GZTab tab, UnaryOperator<Item.Properties> properties) {
        return register(name, english, tab, Item::new, properties);
    }

    /** Registers the item form of a block. The block's English name is recorded by {@link GZBlocks}. */
    public static DeferredItem<BlockItem> blockItem(DeferredBlock<? extends Block> block, GZTab tab, UnaryOperator<Item.Properties> properties) {
        DeferredItem<BlockItem> item = ITEMS.registerSimpleBlockItem(block, properties);
        addToTab(item, tab);
        return item;
    }

    private static void addToTab(DeferredItem<? extends Item> item, GZTab tab) {
        TAB_CONTENTS.computeIfAbsent(tab, t -> new ArrayList<>()).add(item);
        TAB_OF.put(item, tab);
    }

    public static List<DeferredItem<? extends Item>> contents(GZTab tab) {
        return Collections.unmodifiableList(TAB_CONTENTS.getOrDefault(tab, List.of()));
    }

    public static GZTab tabOf(DeferredItem<? extends Item> item) {
        return TAB_OF.get(item);
    }
}
