package dev.groundburst.content.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import dev.groundburst.registry.GBBlocks;
import dev.groundburst.registry.GBBlocks.WithItem;
import dev.groundburst.registry.GBItems;
import dev.groundburst.registry.GBTab;
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
public final class GBResources {
    /** Host rock of an ore variant; decides hardness, sound and which worldgen target it replaces. */
    public enum Host { STONE, DEEPSLATE, NETHERRACK, SANDSTONE }

    /** Tool tier needed to get drops. */
    public enum Tier { STONE, IRON, DIAMOND }

    /** One ore block. `drop` is the raw item or powder; min/max > 1 means a multi-drop powder ore. */
    public record Ore(String family, Host host, WithItem<Block> block, Supplier<? extends Item> drop, int min, int max, Tier tier) {}

    private static final List<Ore> ORES = new ArrayList<>();

    // --- ores -------------------------------------------------------------------------------------------------------------
    public static final WithItem<Block> URANIUM_ORE = ore("uranium", Host.STONE, "Uranium Ore", () -> GBResources.RAW_URANIUM.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> DEEPSLATE_URANIUM_ORE = ore("uranium", Host.DEEPSLATE, "Deepslate Uranium Ore", () -> GBResources.RAW_URANIUM.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> LITHIUM_ORE = ore("lithium", Host.STONE, "Lithium Ore", () -> GBResources.RAW_LITHIUM.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_LITHIUM_ORE = ore("lithium", Host.DEEPSLATE, "Deepslate Lithium Ore", () -> GBResources.RAW_LITHIUM.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> SULFUR_ORE = ore("sulfur", Host.STONE, "Sulfur Ore", () -> GBResources.SULFUR_DUST.get(), 2, 4, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_SULFUR_ORE = ore("sulfur", Host.DEEPSLATE, "Deepslate Sulfur Ore", () -> GBResources.SULFUR_DUST.get(), 2, 4, Tier.STONE);
    public static final WithItem<Block> NETHER_SULFUR_ORE = ore("sulfur", Host.NETHERRACK, "Nether Sulfur Ore", () -> GBResources.SULFUR_DUST.get(), 2, 4, Tier.STONE);
    public static final WithItem<Block> SALTPETER_ORE = ore("saltpeter", Host.STONE, "Saltpeter Ore", () -> GBResources.SALTPETER_DUST.get(), 2, 3, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_SALTPETER_ORE = ore("saltpeter", Host.DEEPSLATE, "Deepslate Saltpeter Ore", () -> GBResources.SALTPETER_DUST.get(), 2, 3, Tier.STONE);
    public static final WithItem<Block> SANDSTONE_SALTPETER_ORE = ore("saltpeter", Host.SANDSTONE, "Sandstone Saltpeter Ore", () -> GBResources.SALTPETER_DUST.get(), 2, 3, Tier.STONE);
    public static final WithItem<Block> TUNGSTEN_ORE = ore("tungsten", Host.STONE, "Tungsten Ore", () -> GBResources.RAW_TUNGSTEN.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> DEEPSLATE_TUNGSTEN_ORE = ore("tungsten", Host.DEEPSLATE, "Deepslate Tungsten Ore", () -> GBResources.RAW_TUNGSTEN.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> LEAD_ORE = ore("lead", Host.STONE, "Lead Ore", () -> GBResources.RAW_LEAD.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> DEEPSLATE_LEAD_ORE = ore("lead", Host.DEEPSLATE, "Deepslate Lead Ore", () -> GBResources.RAW_LEAD.get(), 1, 1, Tier.STONE);
    public static final WithItem<Block> RARE_EARTH_ORE = ore("rare_earth", Host.STONE, "Rare-Earth Ore", () -> GBResources.RAW_RARE_EARTH.get(), 1, 1, Tier.IRON);
    public static final WithItem<Block> DEEPSLATE_RARE_EARTH_ORE = ore("rare_earth", Host.DEEPSLATE, "Deepslate Rare-Earth Ore", () -> GBResources.RAW_RARE_EARTH.get(), 1, 1, Tier.IRON);

    // --- raw materials & products --------------------------------------------------------------------------------
    public static final DeferredItem<Item> RAW_URANIUM = GBItems.simple("raw_uranium", "Raw Uranium", GBTab.RESOURCES);
    public static final DeferredItem<Item> RAW_LITHIUM = GBItems.simple("raw_lithium", "Raw Lithium", GBTab.RESOURCES);
    public static final DeferredItem<Item> RAW_TUNGSTEN = GBItems.simple("raw_tungsten", "Raw Tungsten", GBTab.RESOURCES);
    public static final DeferredItem<Item> RAW_LEAD = GBItems.simple("raw_lead", "Raw Lead", GBTab.RESOURCES);
    public static final DeferredItem<Item> RAW_RARE_EARTH = GBItems.simple("raw_rare_earth", "Raw Rare Earths", GBTab.RESOURCES);
    public static final DeferredItem<Item> SULFUR_DUST = GBItems.simple("sulfur_dust", "Sulfur Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> SALTPETER_DUST = GBItems.simple("saltpeter_dust", "Saltpeter Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> URANIUM_DUST = GBItems.simple("uranium_dust", "Uranium Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> LITHIUM_DUST = GBItems.simple("lithium_dust", "Lithium Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_DUST = GBItems.simple("tungsten_dust", "Tungsten Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> LEAD_DUST = GBItems.simple("lead_dust", "Lead Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> RARE_EARTH_DUST = GBItems.simple("rare_earth_dust", "Rare-Earth Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> COBALT_DUST = GBItems.simple("cobalt_dust", "Cobalt Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> IRON_DUST = GBItems.simple("iron_dust", "Iron Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> GOLD_DUST = GBItems.simple("gold_dust", "Gold Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> COPPER_DUST = GBItems.simple("copper_dust", "Copper Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> CINNABAR_DUST = GBItems.simple("cinnabar_dust", "Cinnabar Dust", GBTab.RESOURCES);
    public static final DeferredItem<Item> URANIUM_INGOT = GBItems.simple("uranium_ingot", "Natural Uranium Ingot", GBTab.RESOURCES);
    public static final DeferredItem<Item> LEAD_INGOT = GBItems.simple("lead_ingot", "Lead Ingot", GBTab.RESOURCES);
    public static final DeferredItem<Item> TUNGSTEN_INGOT = GBItems.simple("tungsten_ingot", "Tungsten Ingot", GBTab.RESOURCES);
    public static final DeferredItem<Item> EXOTIC_SHARD = GBItems.simple("exotic_shard", "Exotic Matter Shard", GBTab.RESOURCES,
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
    public static final WithItem<Block> VOIDSTONE = GBBlocks.register("voidstone", "Voidstone", GBTab.RESOURCES, Block::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3f, 9f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final WithItem<BuddingVoidstoneBlock> BUDDING_VOIDSTONE = GBBlocks.register("budding_voidstone", "Budding Voidstone",
            GBTab.RESOURCES, BuddingVoidstoneBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3f, 9f).randomTicks().sound(SoundType.DEEPSLATE)
                    .pushReaction(PushReaction.IMMOVEABLE).lightLevel(s -> 2));
    public static final WithItem<AmethystClusterBlock> SMALL_EXOTIC_BUD = crystal("small_exotic_bud", "Small Exotic Bud", 3f, 8f, 2, SoundType.SMALL_AMETHYST_BUD);
    public static final WithItem<AmethystClusterBlock> LARGE_EXOTIC_BUD = crystal("large_exotic_bud", "Large Exotic Bud", 5f, 10f, 4, SoundType.LARGE_AMETHYST_BUD);
    public static final WithItem<AmethystClusterBlock> EXOTIC_CRYSTAL_CLUSTER = crystal("exotic_crystal_cluster", "Exotic Crystal Cluster", 7f, 10f, 6, SoundType.AMETHYST_CLUSTER);

    private GBResources() {}

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
        WithItem<Block> block = GBBlocks.register(name, english, GBTab.RESOURCES,
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
        return GBBlocks.register(name, english, GBTab.RESOURCES, Block::new,
                p -> p.mapColor(color).strength(hardness, resistance).requiresCorrectToolForDrops().sound(SoundType.METAL)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE));
    }

    private static WithItem<Block> rawBlock(String name, String english, MapColor color) {
        return GBBlocks.register(name, english, GBTab.RESOURCES, Block::new,
                p -> p.mapColor(color).strength(5f, 6f).requiresCorrectToolForDrops().instrument(NoteBlockInstrument.BASEDRUM));
    }

    private static WithItem<AmethystClusterBlock> crystal(String name, String english, float height, float width, int light, SoundType sound) {
        return GBBlocks.register(name, english, GBTab.RESOURCES, props -> new AmethystClusterBlock(height, width, props),
                p -> p.mapColor(MapColor.COLOR_PURPLE).forceSolidOn().noOcclusion().sound(sound).strength(1.5f)
                        .lightLevel(s -> light).pushReaction(PushReaction.POPPED));
    }
}
