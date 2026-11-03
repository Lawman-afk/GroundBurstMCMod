package dev.groundburst.port;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Minecraft 26.x's pair of a block tag and the item tag of the same blocks' items. */
public record BlockItemTagId(TagKey<Block> block, TagKey<Item> item) {
    public static BlockItemTagId create(ResourceLocation block, ResourceLocation item) {
        return new BlockItemTagId(TagKey.create(Registries.BLOCK, block), TagKey.create(Registries.ITEM, item));
    }
}
