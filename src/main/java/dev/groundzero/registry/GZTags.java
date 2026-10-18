package dev.groundzero.registry;

import dev.groundzero.GroundZero;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockItemTagId;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** The mod's own tags plus the convention (c:) tags it adds entries to. */
public final class GZTags {
    /** Blocks and items that emit radiation (strength comes from a data map in Phase 12). */
    public static final BlockItemTagId RADIOACTIVE = BlockItemTagId.create(GroundZero.id("radioactive"), GroundZero.id("radioactive"));

    private GZTags() {}

    /** A convention tag pair like c:ores/uranium. */
    public static BlockItemTagId common(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("c", path);
        return BlockItemTagId.create(id, id);
    }

    public static TagKey<Item> commonItem(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    public static TagKey<Block> commonBlock(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }
}
