package dev.groundzero.registry;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import dev.groundzero.GroundZero;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block registration helpers; blocks with an item form are registered together with it. */
public final class GZBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GroundZero.MODID);

    private GZBlocks() {}

    /** A registered block plus its item form. */
    public record WithItem<B extends Block>(DeferredBlock<B> block, DeferredItem<BlockItem> item) {}

    public static <B extends Block> DeferredBlock<B> blockOnly(String name, String english,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        GZLang.add("block." + GroundZero.MODID + "." + name, english);
        return block;
    }

    public static <B extends Block> WithItem<B> register(String name, String english, GZTab tab,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties,
            UnaryOperator<Item.Properties> itemProperties) {
        DeferredBlock<B> block = blockOnly(name, english, factory, properties);
        return new WithItem<>(block, GZItems.blockItem(block, tab, itemProperties));
    }

    public static <B extends Block> WithItem<B> register(String name, String english, GZTab tab,
            Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
        return register(name, english, tab, factory, properties, UnaryOperator.identity());
    }

    public static WithItem<Block> simple(String name, String english, GZTab tab, UnaryOperator<BlockBehaviour.Properties> properties) {
        return register(name, english, tab, Block::new, properties);
    }
}
