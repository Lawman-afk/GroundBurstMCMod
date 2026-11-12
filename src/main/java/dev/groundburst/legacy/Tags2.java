package dev.groundburst.legacy;

import java.util.Map;

import dev.groundburst.GroundBurst;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** NeoForge 1.21's common tags on Forge 1.20.1. */
public final class Tags2 {
    private Tags2() {}

    /** Every colour of concrete (no common tag on 1.20.1: the mod's own, data/groundburst/tags). */
    public static final TagKey<Block> CONCRETES = TagKey.create(Registries.BLOCK, GroundBurst.id("concretes"));

    /**
     * The common tags 1.20.1 names otherwise (c:glass_blocks is forge:glass): tools/backport/port.py's TAG_RENAMES, which
     * renames the data's and checks this table against its own.
     */
    private static final Map<String, String> RENAMES = Map.ofEntries(
            Map.entry("glass_blocks", "glass"),
            Map.entry("glass_blocks/colorless", "glass/colorless"),
            Map.entry("glass_panes/colorless", "glass_panes/colorless"),
            Map.entry("slime_balls", "slimeballs"),
            Map.entry("sands", "sand"),
            Map.entry("gravels", "gravel"),
            Map.entry("stones", "stone"),
            Map.entry("cobblestones", "cobblestone"),
            Map.entry("obsidians", "obsidian"),
            Map.entry("strings", "string"),
            Map.entry("leathers", "leather"),
            Map.entry("gunpowders", "gunpowder"),
            Map.entry("sands/colorless", "sand/colorless"),
            Map.entry("sands/red", "sand/red"),
            Map.entry("netherracks", "netherrack"),
            Map.entry("sandstone/blocks", "sandstone"));

    /** The common tag {@code c:<path>} (NeoForge 1.21's) as Forge 1.20.1's: {@code forge:<path>}, or its own name. */
    public static ResourceLocation common(String path) {
        return new ResourceLocation("forge", RENAMES.getOrDefault(path, path));
    }

    /** A common fluid tag: as {@link #common}, but water and lava, vanilla's own tags on 1.20.1. */
    public static ResourceLocation commonFluid(String path) {
        return switch (path) {
            case "water", "lava" -> new ResourceLocation("minecraft", path);
            default -> common(path);
        };
    }
}
