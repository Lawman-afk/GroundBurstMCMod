package dev.groundburst.registry;

import dev.groundburst.GroundBurst;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockItemTagId;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** The mod's own tags plus the convention (c:) tags it adds entries to. */
public final class GBTags {
    /** Blocks and items that emit radiation (strength comes from a data map in Phase 12). */
    public static final BlockItemTagId RADIOACTIVE = BlockItemTagId.create(GroundBurst.id("radioactive"), GroundBurst.id("radioactive"));

    /** Anything that counts as a basic circuit in recipes: the Handmade Circuit Board and every c:circuits/basic. */
    public static final TagKey<Item> BASIC_CIRCUITS = TagKey.create(Registries.ITEM, GroundBurst.id("basic_circuits"));
    /** Advanced circuits (ours and any mod's c:circuits/advanced). */
    public static final TagKey<Item> ADVANCED_CIRCUITS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "circuits/advanced"));
    /** Industrial Press dies. */
    public static final TagKey<Item> PRESS_DIES = TagKey.create(Registries.ITEM, GroundBurst.id("press_dies"));

    private GBTags() {}

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
