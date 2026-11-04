package dev.groundburst.port;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Minecraft 26.3's pair of a block tag and the item tag of the same blocks' items (26.1.2 keeps the two apart). */
public record BlockItemTagId(TagKey<Block> block, TagKey<Item> item) {
    public static BlockItemTagId create(Identifier block, Identifier item) {
        return new BlockItemTagId(TagKey.create(Registries.BLOCK, block), TagKey.create(Registries.ITEM, item));
    }
}
