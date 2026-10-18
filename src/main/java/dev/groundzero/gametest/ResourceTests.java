package dev.groundzero.gametest;

import java.util.List;

import dev.groundzero.GroundZero;
import dev.groundzero.content.resource.GZResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Phase 3 checks: ore drops, tool tiers, exotic crystal growth, ore features and biome modifiers. */
final class ResourceTests {
    private ResourceTests() {}

    static void register() {
        GZGameTests.test("ore_drops", GZGameTests.FLOOR_16, 20, ResourceTests::oreDrops);
        GZGameTests.test("ore_tool_tiers", GZGameTests.FLOOR_16, 20, ResourceTests::toolTiers);
        GZGameTests.test("exotic_crystal_growth", GZGameTests.FLOOR_16, 20, ResourceTests::crystalGrowth);
        GZGameTests.test("ore_features_generate", GZGameTests.STONE_12, 20, ResourceTests::oreFeatures);
        GZGameTests.test("biome_modifiers_applied", GZGameTests.FLOOR_16, 20, ResourceTests::biomeModifiers);
    }

    private static List<ItemStack> drops(GameTestHelper helper, Block block, Item tool) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, block);
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = helper.getLevel().getBlockState(abs);
        return Block.getDrops(state, helper.getLevel(), abs, null, null, new ItemStack(tool));
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void oreDrops(GameTestHelper helper) {
        for (GZResources.Ore ore : GZResources.ores()) {
            List<ItemStack> stacks = drops(helper, ore.block().block().get(), Items.DIAMOND_PICKAXE);
            int n = count(stacks, ore.drop().get());
            helper.assertTrue(n >= ore.min() && n <= ore.max(),
                    ore.block().block().getId() + " dropped " + n + " " + ore.drop().get() + ", expected " + ore.min() + ".." + ore.max());
        }
        int shards = count(drops(helper, GZResources.EXOTIC_CRYSTAL_CLUSTER.block().get(), Items.IRON_PICKAXE), GZResources.EXOTIC_SHARD.get());
        helper.assertTrue(shards >= 1 && shards <= 3, "Exotic cluster dropped " + shards + " shards");
        helper.assertTrue(drops(helper, GZResources.BUDDING_VOIDSTONE.block().get(), Items.DIAMOND_PICKAXE).isEmpty(), "Budding voidstone must not drop");
        helper.succeed();
    }

    private static void toolTiers(GameTestHelper helper) {
        BlockState uranium = GZResources.URANIUM_ORE.block().get().defaultBlockState();
        BlockState lead = GZResources.LEAD_ORE.block().get().defaultBlockState();
        helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(lead), "Stone pickaxe should mine lead ore");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(uranium), "Stone pickaxe must not mine uranium ore");
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(uranium), "Iron pickaxe should mine uranium ore");
        helper.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(lead), "Wooden pickaxe must not mine lead ore");
        helper.succeed();
    }

    private static void crystalGrowth(GameTestHelper helper) {
        BlockPos rel = new BlockPos(7, 6, 7);
        helper.setBlock(rel, GZResources.BUDDING_VOIDSTONE.block().get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(rel);
        RandomSource random = RandomSource.create(42);
        Block cluster = GZResources.EXOTIC_CRYSTAL_CLUSTER.block().get();
        boolean sawCluster = false;
        for (int i = 0; i < 6000 && !sawCluster; i++) {
            level.getBlockState(abs).randomTick(level, abs, random);
            for (var d : net.minecraft.core.Direction.values()) {
                sawCluster |= level.getBlockState(abs.relative(d)).is(cluster);
            }
        }
        helper.assertTrue(sawCluster, "Budding voidstone never grew a full exotic cluster");
        helper.succeed();
    }

    private static void oreFeatures(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        String[][] cases = {
                {"ore_uranium", "uranium_ore"}, {"ore_lithium", "lithium_ore"}, {"ore_sulfur", "sulfur_ore"},
                {"ore_saltpeter", "saltpeter_ore"}, {"ore_tungsten", "tungsten_ore"}, {"ore_lead", "lead_ore"},
                {"ore_rare_earth", "rare_earth_ore"},
        };
        RandomSource random = RandomSource.create(7);
        BlockPos centre = helper.absolutePos(new BlockPos(6, 6, 6));
        for (String[] c : cases) {
            Feature feature = features.getOrThrow(ResourceKey.create(Registries.FEATURE, GroundZero.id(c[0]))).value();
            Block ore = level.registryAccess().lookupOrThrow(Registries.BLOCK).getOrThrow(ResourceKey.create(Registries.BLOCK, GroundZero.id(c[1]))).value();
            int placed = 0;
            for (int attempt = 0; attempt < 8 && placed == 0; attempt++) {
                feature.place(level, level.getChunkSource().getGenerator(), random, centre);
                placed = countIn(helper, ore);
            }
            helper.assertTrue(placed > 0, c[0] + " placed no " + c[1] + " in solid stone");
        }
        helper.succeed();
    }

    private static int countIn(GameTestHelper helper, Block block) {
        int n = 0;
        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 12; y++) {
                for (int z = 0; z < 12; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static void biomeModifiers(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        expect(helper, biomes.getOrThrow(Biomes.PLAINS), "ore_uranium");
        expect(helper, biomes.getOrThrow(Biomes.PLAINS), "ore_lead");
        expect(helper, biomes.getOrThrow(Biomes.BADLANDS), "ore_uranium_badlands");
        expect(helper, biomes.getOrThrow(Biomes.DESERT), "ore_saltpeter_sandstone");
        expect(helper, biomes.getOrThrow(Biomes.NETHER_WASTES), "ore_sulfur_nether");
        expect(helper, biomes.getOrThrow(Biomes.END_HIGHLANDS), "exotic_geode");
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, Holder<Biome> biome, String placed) {
        Identifier id = GroundZero.id(placed);
        boolean present = biome.value().getGenerationSettings().features().stream()
                .flatMap(set -> set.stream())
                .anyMatch(h -> h.unwrapKey().map(k -> k.identifier().equals(id)).orElse(false));
        helper.assertTrue(present, biome.unwrapKey().orElseThrow().identifier() + " is missing placed feature " + id);
    }

    @SuppressWarnings("unused")
    private static ResourceKey<PlacedFeature> placedKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, GroundZero.id(name));
    }
}
