package dev.groundburst.datagen;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.resource.GBResources;
import dev.groundburst.world.ConfigToggleFilter;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.GeodeFeature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.GeodeBlockSettings;
import net.minecraft.world.level.levelgen.GeodeCrackSettings;
import net.minecraft.world.level.levelgen.GeodeLayerSettings;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Ore and geode generation (DESIGN.md §5): features, their placements, and NeoForge biome modifiers. Every
 * placement includes a {@link ConfigToggleFilter} so each resource can be switched off in the common config.
 */
public final class GBWorldgenData {
    private record Spec(String name, String toggle, List<PlacementModifier> placement, TagKey<Biome> biomes, GenerationStep.Decoration step) {}

    private static final List<Spec> SPECS = new ArrayList<>();

    private GBWorldgenData() {}

    private static ResourceKey<Feature> featureKey(String name) {
        return ResourceKey.create(Registries.FEATURE, GroundBurst.id(name));
    }

    private static ResourceKey<PlacedFeature> placedKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, GroundBurst.id(name));
    }

    private static List<PlacementModifier> ores(String toggle, PlacementModifier frequency, HeightRangePlacement height) {
        return List.of(new ConfigToggleFilter(toggle), frequency, InSquarePlacement.spread(), height, BiomeFilter.biome());
    }

    private static HeightRangePlacement triangle(int lo, int hi) {
        return HeightRangePlacement.triangle(VerticalAnchor.absolute(lo), VerticalAnchor.absolute(hi));
    }

    private static HeightRangePlacement uniform(int lo, int hi) {
        return HeightRangePlacement.uniform(VerticalAnchor.absolute(lo), VerticalAnchor.absolute(hi));
    }

    public static void gather(GatherDataRegistryEntriesEvent event) {
        SPECS.clear();
        // name, toggle, placement, biomes. Numbers follow DESIGN.md §5.
        SPECS.add(new Spec("ore_uranium", "uranium", ores("uranium", CountPlacement.of(3), triangle(-48, 24)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_uranium_badlands", "uranium", ores("uranium", CountPlacement.of(2), triangle(-48, 24)), BiomeTags.IS_BADLANDS, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_lithium", "lithium", ores("lithium", CountPlacement.of(4), uniform(-16, 64)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_lithium_mountain", "lithium", ores("lithium", CountPlacement.of(3), uniform(16, 128)), Tags.Biomes.IS_MOUNTAIN, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_sulfur", "sulfur", ores("sulfur", CountPlacement.of(5), uniform(-64, 16)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_sulfur_nether", "sulfur", ores("sulfur", CountPlacement.of(8), uniform(10, 117)), BiomeTags.IS_NETHER, GenerationStep.Decoration.UNDERGROUND_DECORATION));
        SPECS.add(new Spec("ore_saltpeter", "saltpeter", ores("saltpeter", CountPlacement.of(4), uniform(0, 80)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_saltpeter_sandstone", "saltpeter", ores("saltpeter", CountPlacement.of(8), uniform(40, 100)), Tags.Biomes.IS_DESERT, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_saltpeter_sandstone_badlands", "saltpeter", ores("saltpeter", CountPlacement.of(6), uniform(40, 100)), BiomeTags.IS_BADLANDS, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_tungsten", "tungsten", ores("tungsten", CountPlacement.of(2), triangle(-80, 0)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_lead", "lead", ores("lead", CountPlacement.of(5), triangle(-32, 48)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_rare_earth", "rare_earth", ores("rare_earth", CountPlacement.of(2), uniform(-32, 48)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_rare_earth_mountain", "rare_earth", ores("rare_earth", CountPlacement.of(2), uniform(0, 96)), Tags.Biomes.IS_MOUNTAIN, GenerationStep.Decoration.UNDERGROUND_ORES));
        SPECS.add(new Spec("ore_fluorite", "fluorite", ores("fluorite", CountPlacement.of(3), uniform(-24, 72)), BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES));
        // Void geodes: anchored inside End-island stone so they hang in the islands' undersides and flanks.
        SPECS.add(new Spec("exotic_geode", "exotic_geodes", List.of(new ConfigToggleFilter("exotic_geodes"),
                RarityFilter.onAverageOnceEvery(3), CountPlacement.of(4), InSquarePlacement.spread(), uniform(16, 72),
                BlockPredicateFilter.forPredicate(BlockPredicate.matchesBlocks(Blocks.END_STONE)), BiomeFilter.biome()),
                BiomeTags.IS_END, GenerationStep.Decoration.UNDERGROUND_DECORATION));

        event.add(Registries.FEATURE, GBWorldgenData::features)
                .add(Registries.PLACED_FEATURE, GBWorldgenData::placed)
                .add(Registries.BIOME, GBWorldgenData::biomes)
                .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, GBWorldgenData::biomeModifiers);
    }

    /**
     * The Nuclear Wasteland (DESIGN.md §21.7, slice 12e): never generated, only painted over a nuclear blast's scorched
     * ground ({@code Wasteland}). Grey-brown grass and leaves, dead-olive water, a dusty sky and brown-orange fog, ash
     * drifting down; silence instead of music; monsters (husks and parched among them) but no animals.
     */
    private static void biomes(BootstrapContext<Biome> context) {
        net.minecraft.world.level.biome.MobSpawnSettings.Builder mobs = new net.minecraft.world.level.biome.MobSpawnSettings.Builder();
        net.minecraft.data.worldgen.BiomeDefaultFeatures.caveSpawns(mobs);
        net.minecraft.data.worldgen.BiomeDefaultFeatures.monsters(mobs, 60, 10, 0, 70, false);
        mobs.addSpawn(net.minecraft.world.entity.EntityTypes.HUSK, 60, 4, 4);
        mobs.addSpawn(net.minecraft.world.entity.EntityTypes.PARCHED, 30, 2, 4);
        var generation = new net.minecraft.world.level.biome.BiomeGenerationSettings.Builder(
                context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CARVER));
        Biome wasteland = new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.9f)
                .downfall(0.2f)
                .setAttribute(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR, net.minecraft.util.ARGB.vector3fFromRGB24(0x9C9384))
                .setAttribute(net.minecraft.world.attribute.EnvironmentAttributes.FOG_COLOR, net.minecraft.util.ARGB.vector3fFromRGB24(0x9A7B5E))
                .setAttribute(net.minecraft.world.attribute.EnvironmentAttributes.WATER_FOG_COLOR, net.minecraft.util.ARGB.vector3fFromRGB24(0x343C22))
                .setAttribute(net.minecraft.world.attribute.EnvironmentAttributes.AMBIENT_PARTICLES,
                        net.minecraft.world.attribute.AmbientParticle.of(dev.groundburst.content.fallout.GBFallout.FALLOUT_ASH_PARTICLE.get(), 0.006f))
                .setAttribute(net.minecraft.world.attribute.EnvironmentAttributes.BACKGROUND_MUSIC, net.minecraft.world.attribute.BackgroundMusic.EMPTY)
                .setAttribute(net.minecraft.world.attribute.EnvironmentAttributes.MUSIC_VOLUME, 0f)
                .specialEffects(new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder()
                        .waterColor(0x5E6B3E)
                        .grassColorOverride(0x8A8264)
                        .foliageColorOverride(0x7E7656)
                        .dryFoliageColorOverride(0x8C7E5C)
                        .build())
                .mobSpawnSettings(mobs.build())
                .generationSettings(generation.build())
                .build();
        context.register(dev.groundburst.content.fallout.Wasteland.BIOME, wasteland);
    }

    private static void features(BootstrapContext<Feature> context) {
        RuleTest stone = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslate = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        RuleTest netherrack = new BlockMatchTest(Blocks.NETHERRACK);
        RuleTest sandstone = new BlockMatchTest(Blocks.SANDSTONE);

        context.register(featureKey("ore_uranium"), new OreFeature(pair(stone, GBResources.URANIUM_ORE.block().get(), deepslate, GBResources.DEEPSLATE_URANIUM_ORE.block().get()), 4));
        context.register(featureKey("ore_uranium_badlands"), new OreFeature(pair(stone, GBResources.URANIUM_ORE.block().get(), deepslate, GBResources.DEEPSLATE_URANIUM_ORE.block().get()), 4));
        context.register(featureKey("ore_lithium"), new OreFeature(pair(stone, GBResources.LITHIUM_ORE.block().get(), deepslate, GBResources.DEEPSLATE_LITHIUM_ORE.block().get()), 6));
        context.register(featureKey("ore_lithium_mountain"), new OreFeature(pair(stone, GBResources.LITHIUM_ORE.block().get(), deepslate, GBResources.DEEPSLATE_LITHIUM_ORE.block().get()), 6));
        context.register(featureKey("ore_sulfur"), new OreFeature(pair(stone, GBResources.SULFUR_ORE.block().get(), deepslate, GBResources.DEEPSLATE_SULFUR_ORE.block().get()), 8));
        context.register(featureKey("ore_sulfur_nether"), new OreFeature(netherrack, GBResources.NETHER_SULFUR_ORE.block().get().defaultBlockState(), 10));
        context.register(featureKey("ore_saltpeter"), new OreFeature(pair(stone, GBResources.SALTPETER_ORE.block().get(), deepslate, GBResources.DEEPSLATE_SALTPETER_ORE.block().get()), 7));
        context.register(featureKey("ore_saltpeter_sandstone"), new OreFeature(sandstone, GBResources.SANDSTONE_SALTPETER_ORE.block().get().defaultBlockState(), 8));
        context.register(featureKey("ore_saltpeter_sandstone_badlands"), new OreFeature(sandstone, GBResources.SANDSTONE_SALTPETER_ORE.block().get().defaultBlockState(), 8));
        context.register(featureKey("ore_tungsten"), new OreFeature(pair(stone, GBResources.TUNGSTEN_ORE.block().get(), deepslate, GBResources.DEEPSLATE_TUNGSTEN_ORE.block().get()), 5));
        context.register(featureKey("ore_lead"), new OreFeature(pair(stone, GBResources.LEAD_ORE.block().get(), deepslate, GBResources.DEEPSLATE_LEAD_ORE.block().get()), 8));
        context.register(featureKey("ore_rare_earth"), new OreFeature(pair(stone, GBResources.RARE_EARTH_ORE.block().get(), deepslate, GBResources.DEEPSLATE_RARE_EARTH_ORE.block().get()), 4));
        context.register(featureKey("ore_rare_earth_mountain"), new OreFeature(pair(stone, GBResources.RARE_EARTH_ORE.block().get(), deepslate, GBResources.DEEPSLATE_RARE_EARTH_ORE.block().get()), 4));
        context.register(featureKey("ore_fluorite"), new OreFeature(pair(stone, GBResources.FLUORITE_ORE.block().get(), deepslate, GBResources.DEEPSLATE_FLUORITE_ORE.block().get()), 7));

        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);
        context.register(featureKey("exotic_geode"), new GeodeFeature(
                new GeodeBlockSettings(
                        BlockStateProvider.holderOf(Blocks.AIR),
                        BlockStateProvider.holderOf(GBResources.VOIDSTONE.block().get()),
                        BlockStateProvider.holderOf(GBResources.BUDDING_VOIDSTONE.block().get()),
                        BlockStateProvider.holderOf(Blocks.OBSIDIAN),
                        BlockStateProvider.holderOf(Blocks.CRYING_OBSIDIAN),
                        List.of(GBResources.SMALL_EXOTIC_BUD.block().get().defaultBlockState(),
                                GBResources.LARGE_EXOTIC_BUD.block().get().defaultBlockState(),
                                GBResources.EXOTIC_CRYSTAL_CLUSTER.block().get().defaultBlockState()),
                        blocks.getOrThrow(BlockTags.FEATURES_CANNOT_REPLACE),
                        blocks.getOrThrow(BlockTags.GEODE_INVALID_BLOCKS)),
                new GeodeLayerSettings(1.7, 2.2, 3.2, 4.2),
                new GeodeCrackSettings(0.6, 2.0, 2),
                0.12, 0.06, true,
                UniformInt.of(4, 5), UniformInt.of(3, 4), UniformInt.of(1, 2),
                -16, 16, 0.05, 1));
    }

    private static List<BlockReplacement> pair(RuleTest stone, Block stoneOre, RuleTest deepslate, Block deepslateOre) {
        return List.of(BlockReplacement.replace(stone, stoneOre.defaultBlockState()), BlockReplacement.replace(deepslate, deepslateOre.defaultBlockState()));
    }

    private static void placed(BootstrapContext<PlacedFeature> context) {
        HolderGetter<Feature> features = context.lookup(Registries.FEATURE);
        for (Spec spec : SPECS) {
            context.register(placedKey(spec.name()), new PlacedFeature(features.getOrThrow(featureKey(spec.name())), spec.placement()));
        }
    }

    private static void biomeModifiers(BootstrapContext<BiomeModifier> context) {
        HolderGetter<PlacedFeature> placed = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        for (Spec spec : SPECS) {
            HolderSet<Biome> where = spec.biomes() == BiomeTags.IS_END
                    // Outer islands only: keep the dragon's central island clean.
                    ? HolderSet.direct(biomes.getOrThrow(Biomes.END_HIGHLANDS), biomes.getOrThrow(Biomes.END_MIDLANDS),
                            biomes.getOrThrow(Biomes.END_BARRENS), biomes.getOrThrow(Biomes.SMALL_END_ISLANDS))
                    : biomes.getOrThrow(spec.biomes());
            context.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, GroundBurst.id(spec.name())),
                    new BiomeModifiers.AddFeaturesBiomeModifier(where, HolderSet.direct(placed.getOrThrow(placedKey(spec.name()))), spec.step()));
        }
    }
}
