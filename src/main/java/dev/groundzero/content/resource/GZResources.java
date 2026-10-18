package dev.groundzero.content.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import dev.groundzero.registry.GZBlocks;
import dev.groundzero.registry.GZBlocks.WithItem;
import dev.groundzero.registry.GZItems;
import dev.groundzero.registry.GZTab;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Raw resources (DESIGN.md §5): ores in every host rock, raw materials, ingots, powders, storage blocks, and the
 * End's exotic-matter crystals. Each ore records what it drops and which tool tier it needs so loot tables and
 * tags are generated from here. Declaration order is the creative-tab order (ores, materials, blocks, exotic).
 */
public final class GZResources {
    /** Host rock of an ore variant; decides hardness, sound and which worldgen target it replaces. */
    public enum Host { STONE, DEEPSLATE, NETHERRACK, SANDSTONE }

    /** Tool tier needed to get drops. */
    public enum Tier { STONE, IRON, DIAMOND }

    /** One ore block. `drop` is the raw item or powder; min/max > 1 means a multi-drop powder ore. */
    public record Ore(String family, Host host, WithItem<Block> block, Supplier<? extends Item> drop, int min, int max, Tier tier) {}

    private static final List<Ore> ORES = new ArrayList<>();

    // --- ores -------------------------------------------------------------------------------------------------------------
    public static final WithItem<Block> URANIUM_ORE = ore("uranium", Host.STONE, "Uranium Ore", () -> GZResources.RAW_URANIUM.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> DEEPSLATE_URANIUM_ORE = ore("uranium", Host.DEEPSLATE, "Deepslate Uranium Ore", () -> GZResources.RAW_URANIUM.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> LITHIUM_ORE = ore("lithium", Host.STONE, "Lithium Ore", () -> GZResources.RAW_LITHIUM.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_LITHIUM_ORE = ore("lithium", Host.DEEPSLATE, "Deepslate Lithium Ore", () -> GZResources.RAW_LITHIUM.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> SULFUR_ORE = ore("sulfur", Host.STONE, "Sulfur Ore", () -> GZResources.SULFUR_DUST.get(), 2, 4, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_SULFUR_ORE = ore("sulfur", Host.DEEPSLATE, "Deepslate Sulfur Ore", () -> GZResources.SULFUR_DUST.get(), 2, 4, Tier.STONE);
    public static final WithItem<Block> NETHER_SULFUR_ORE = ore("sulfur", Host.NETHERRACK, "Nether Sulfur Ore", () -> GZResources.SULFUR_DUST.get(), 2, 4, Tier.STONE);
    public static final WithItem<Block> SALTPETER_ORE = ore("saltpeter", Host.STONE, "Saltpeter Ore", () -> GZResources.SALTPETER_DUST.get(), 2, 3, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_SALTPETER_ORE = ore("saltpeter", Host.DEEPSLATE, "Deepslate Saltpeter Ore", () -> GZResources.SALTPETER_DUST.get(), 2, 3, Tier.STONE);
    public static final WithItem<Block> SANDSTONE_SALTPETER_ORE = ore("saltpeter", Host.SANDSTONE, "Sandstone Saltpeter Ore", () -> GZResources.SALTPETER_DUST.get(), 2, 3, Tier.STONE);
    public static final WithItem<Block> TUNGSTEN_ORE = ore("tungsten", Host.STONE, "Tungsten Ore", () -> GZResources.RAW_TUNGSTEN.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> DEEPSLATE_TUNGSTEN_ORE = ore("tungsten", Host.DEEPSLATE, "Deepslate Tungsten Ore", () -> GZResources.RAW_TUNGSTEN.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> LEAD_ORE = ore("lead", Host.STONE, "Lead Ore", () -> GZResources.RAW_LEAD.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_LEAD_ORE = ore("lead", Host.DEEPSLATE, "Deepslate Lead Ore", () -> GZResources.RAW_LEAD.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> RARE_EARTH_ORE = ore("rare_earth", Host.STONE, "Rare-Earth Ore", () -> GZResources.RAW_RARE_EARTH.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> DEEPSLATE_RARE_EARTH_ORE = ore("rare_earth", Host.DEEPSLATE, "Deepslate Rare-Earth Ore", () -> GZResources.RAW_RARE_EARTH.get(), 1, 1, Tier.IRON);

    // --- raw materials & products --------------------------------------------------------------------------------
    public static final DeferredItem<Item> RAW_URANIUM = GZItems.simple("raw_uranium", "Raw Uranium", GZTab.RESOURCES);
    public static final DeferredItem<Item> RAW_LITHIUM = GZItems.simple("raw_lithium", "Raw Lithium", GZTab.RESOURCES);
    public static final DeferredItem<Item> RAW_TUNGSTEN = GZItems.simple("raw_tungsten", "Raw Tungsten", GZTab.RESOURCES);
    public static final DeferredItem<Item> RAW_LEAD = GZItems.simple("raw_lead", "Raw Lead", GZTab.RESOURCES);
    public static final DeferredItem<Item> RAW_RARE_EARTH = GZItems.simple("raw_rare_earth", "Raw Rare Earths", GZTab.RESOURCES);
    public static final DeferredItem<Item> SULFUR_DUST = GZItems.simple("sulfur_dust", "Sulfur Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> SALTPETER_DUST = GZItems.simple("saltpeter_dust", "Saltpeter Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> URANIUM_DUST = GZItems.simple("uranium_dust", "Uranium Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> LITHIUM_DUST = GZItems.simple("lithium_dust", "Lithium Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_DUST = GZItems.simple("tungsten_dust", "Tungsten Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> LEAD_DUST = GZItems.simple("lead_dust", "Lead Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> RARE_EARTH_DUST = GZItems.simple("rare_earth_dust", "Rare-Earth Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> COBALT_DUST = GZItems.simple("cobalt_dust", "Cobalt Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> IRON_DUST = GZItems.simple("iron_dust", "Iron Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> GOLD_DUST = GZItems.simple("gold_dust", "Gold Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> COPPER_DUST = GZItems.simple("copper_dust", "Copper Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> CINNABAR_DUST = GZItems.simple("cinnabar_dust", "Cinnabar Dust", GZTab.RESOURCES);
    public static final DeferredItem<Item> URANIUM_INGOT = GZItems.simple("uranium_ingot", "Natural Uranium Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> LEAD_INGOT = GZItems.simple("lead_ingot", "Lead Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_INGOT = GZItems.simple("tungsten_ingot", "Tungsten Ingot", GZTab.RESOURCES);
    public static final DeferredItem<Item> EXOTIC_SHARD = GZItems.simple("exotic_shard", "Exotic Matter Shard", GZTab.RESOURCES,
            p -> p.rarity(net.minecraft.world.item.Rarity.RARE));

    // --- storage blocks ------------------------------------------------------------------------------------------------------
    public static final WithItem<Block> URANIUM_BLOCK = metalBlock("uranium_block", "Block of Natural Uranium", MapColor.COLOR_GREEN, 5f, 6f);
    public static final WithItem<Block> LEAD_BLOCK = metalBlock("lead_block", "Block of Lead", MapColor.COLOR_LIGHT_BLUE, 5f, 12f);
    public static final WithItem<Block> TUNGSTEN_BLOCK = metalBlock("tungsten_block", "Block of Tungsten", MapColor.COLOR_BLUE, 8f, 18f);
    public static final WithItem<Block> RAW_URANIUM_BLOCK = rawBlock("raw_uranium_block", "Block of Raw Uranium", MapColor.COLOR_GREEN);
    public static final WithItem<Block> RAW_LITHIUM_BLOCK = rawBlock("raw_lithium_block", "Block of Raw Lithium", MapColor.COLOR_PINK);
    public static final WithItem<Block> RAW_TUNGSTEN_BLOCK = rawBlock("raw_tungsten_block", "Block of Raw Tungsten", MapColor.COLOR_BROWN);
    public static final WithItem<Block> RAW_LEAD_BLOCK = rawBlock("raw_lead_block", "Block of Raw Lead", MapColor.COLOR_LIGHT_GRAY);
    public static final WithItem<Block> RAW_RARE_EARTH_BLOCK = rawBlock("raw_rare_earth_block", "Block of Raw Rare Earths", MapColor.COLOR_ORANGE);

    // --- exotic matter (End void geodes) -------------------------------------------------------------------------------------
    public static final WithItem<Block> VOIDSTONE = GZBlocks.register("voidstone", "Voidstone", GZTab.RESOURCES, Block::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3f, 9f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final WithItem<BuddingVoidstoneBlock> BUDDING_VOIDSTONE = GZBlocks.register("budding_voidstone", "Budding Voidstone",
            GZTab.RESOURCES, BuddingVoidstoneBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3f, 9f).randomTicks().sound(SoundType.DEEPSLATE)
                    .pushReaction(PushReaction.IMMOVEABLE).lightLevel(s -> 2));
    public static final WithItem<AmethystClusterBlock> SMALL_EXOTIC_BUD = crystal("small_exotic_bud", "Small Exotic Bud", 3f, 8f, 2, SoundType.SMALL_AMETHYST_BUD);
    public static final WithItem<AmethystClusterBlock> LARGE_EXOTIC_BUD = crystal("large_exotic_bud", "Large Exotic Bud", 5f, 10f, 4, SoundType.LARGE_AMETHYST_BUD);
    public static final WithItem<AmethystClusterBlock> EXOTIC_CRYSTAL_CLUSTER = crystal("exotic_crystal_cluster", "Exotic Crystal Cluster", 7f, 10f, 6, SoundType.AMETHYST_CLUSTER);

    private GZResources() {}

    /** Forces class loading so every registration above runs; called from the mod constructor. */
    public static void init() {}

    public static List<Ore> ores() {
        return Collections.unmodifiableList(ORES);
    }

    private static WithItem<Block> ore(String family, Host host, String english, Supplier<? extends Item> drop, int min, int max, Tier tier) {
        String name = switch (host) {
            case STONE -> family + "_ore";
            case DEEPSLATE -> "deepslate_" + family + "_ore";
            case NETHERRACK -> "nether_" + family + "_ore";
            case SANDSTONE -> "sandstone_" + family + "_ore";
        };
        boolean powder = max > 1;
        WithItem<Block> block = GZBlocks.register(name, english, GZTab.RESOURCES,
                powder ? props -> new DropExperienceBlock(UniformInt.of(0, 2), props) : Block::new,
                p -> hostProperties(p, host));
        ORES.add(new Ore(family, host, block, drop, min, max, tier));
        return block;
    }

    private static BlockBehaviour.Properties hostProperties(BlockBehaviour.Properties p, Host host) {
        p.requiresCorrectToolForDrops().instrument(NoteBlockInstrument.BASEDRUM);
        return switch (host) {
            case STONE -> p.mapColor(MapColor.STONE).strength(3f, 3f).sound(SoundType.STONE);
            case DEEPSLATE -> p.mapColor(MapColor.DEEPSLATE).strength(4.5f, 3f).sound(SoundType.DEEPSLATE);
            case NETHERRACK -> p.mapColor(MapColor.NETHER).strength(3f, 3f).sound(SoundType.NETHER_ORE);
            case SANDSTONE -> p.mapColor(MapColor.SAND).strength(1.6f, 1.6f).sound(SoundType.STONE);
        };
    }

    private static WithItem<Block> metalBlock(String name, String english, MapColor color, float hardness, float resistance) {
        return GZBlocks.register(name, english, GZTab.RESOURCES, Block::new,
                p -> p.mapColor(color).strength(hardness, resistance).requiresCorrectToolForDrops().sound(SoundType.METAL)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE));
    }

    private static WithItem<Block> rawBlock(String name, String english, MapColor color) {
        return GZBlocks.register(name, english, GZTab.RESOURCES, Block::new,
                p -> p.mapColor(color).strength(5f, 6f).requiresCorrectToolForDrops().instrument(NoteBlockInstrument.BASEDRUM));
    }

    private static WithItem<AmethystClusterBlock> crystal(String name, String english, float height, float width, int light, SoundType sound) {
        return GZBlocks.register(name, english, GZTab.RESOURCES, props -> new AmethystClusterBlock(height, width, props),
                p -> p.mapColor(MapColor.COLOR_PURPLE).forceSolidOn().noOcclusion().sound(sound).strength(1.5f)
                        .lightLevel(s -> light).pushReaction(PushReaction.POPPED));
    }
}
