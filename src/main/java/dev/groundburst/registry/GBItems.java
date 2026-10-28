package dev.groundburst.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import dev.groundburst.GroundBurst;
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
public final class GBItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GroundBurst.MODID);

    private static final Map<GBTab, List<DeferredItem<? extends Item>>> TAB_CONTENTS = new EnumMap<>(GBTab.class);
    private static final Map<DeferredItem<? extends Item>, GBTab> TAB_OF = new IdentityHashMap<>();

    private GBItems() {}

    public static <I extends Item> DeferredItem<I> register(String name, String english, GBTab tab,
            Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
        DeferredItem<I> item = ITEMS.registerItem(name, factory, properties);
        GBLang.add("item." + GroundBurst.MODID + "." + name, english);
        addToTab(item, tab);
        return item;
    }

    public static <I extends Item> DeferredItem<I> register(String name, String english, GBTab tab, Function<Item.Properties, ? extends I> factory) {
        return register(name, english, tab, factory, UnaryOperator.identity());
    }

    public static DeferredItem<Item> simple(String name, String english, GBTab tab) {
        return register(name, english, tab, Item::new);
    }

    public static DeferredItem<Item> simple(String name, String english, GBTab tab, UnaryOperator<Item.Properties> properties) {
        return register(name, english, tab, Item::new, properties);
    }

    /** Registers the item form of a block. The block's English name is recorded by {@link GBBlocks}. */
    public static DeferredItem<BlockItem> blockItem(DeferredBlock<? extends Block> block, GBTab tab, UnaryOperator<Item.Properties> properties) {
        DeferredItem<BlockItem> item = ITEMS.registerSimpleBlockItem(block, properties);
        addToTab(item, tab);
        return item;
    }

    /** Registers a custom BlockItem subclass for a block. */
    @SuppressWarnings("unchecked")
    public static <B extends Block> DeferredItem<BlockItem> blockItem(DeferredBlock<B> block, GBTab tab,
            java.util.function.BiFunction<B, Item.Properties, ? extends BlockItem> factory) {
        DeferredItem<BlockItem> item = (DeferredItem<BlockItem>) (DeferredItem<?>) ITEMS.registerItem(block.getId().getPath(),
                props -> factory.apply(block.get(), props), Item.Properties::useBlockDescriptionPrefix);
        addToTab(item, tab);
        return item;
    }

    private static void addToTab(DeferredItem<? extends Item> item, GBTab tab) {
        TAB_CONTENTS.computeIfAbsent(tab, t -> new ArrayList<>()).add(item);
        TAB_OF.put(item, tab);
    }

    public static List<DeferredItem<? extends Item>> contents(GBTab tab) {
        return Collections.unmodifiableList(TAB_CONTENTS.getOrDefault(tab, List.of()));
    }

    public static GBTab tabOf(DeferredItem<? extends Item> item) {
        return TAB_OF.get(item);
    }
}
