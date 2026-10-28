package dev.groundburst.registry;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import dev.groundburst.GroundBurst;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block registration helpers; blocks with an item form are registered together with it. */
public final class GBBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GroundBurst.MODID);

    private GBBlocks() {}

    /** A registered block plus its item form. */
    public record WithItem<B extends Block>(DeferredBlock<B> block, DeferredItem<BlockItem> item) {}

    public static <B extends Block> DeferredBlock<B> blockOnly(String name, String english,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        GBLang.add("block." + GroundBurst.MODID + "." + name, english);
        return block;
    }

    public static <B extends Block> WithItem<B> register(String name, String english, GBTab tab,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties,
            UnaryOperator<Item.Properties> itemProperties) {
        DeferredBlock<B> block = blockOnly(name, english, factory, properties);
        return new WithItem<>(block, GBItems.blockItem(block, tab, itemProperties));
    }

    public static <B extends Block> WithItem<B> register(String name, String english, GBTab tab,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
        return register(name, english, tab, factory, properties, UnaryOperator.identity());
    }

    /** A block whose item form is a custom BlockItem subclass (e.g. a GeckoLib-rendered item). */
    public static <B extends Block> WithItem<B> registerWithItem(String name, String english, GBTab tab,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties,
            java.util.function.BiFunction<B, Item.Properties, ? extends BlockItem> itemFactory) {
        DeferredBlock<B> block = blockOnly(name, english, factory, properties);
        return new WithItem<>(block, GBItems.blockItem(block, tab, itemFactory));
    }

    public static WithItem<Block> simple(String name, String english, GBTab tab, UnaryOperator<BlockBehaviour.Properties> properties) {
        return register(name, english, tab, Block::new, properties);
    }
}
