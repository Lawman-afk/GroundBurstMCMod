package dev.groundburst.gametest;

import dev.groundburst.content.effect.Frost;
import dev.groundburst.content.effect.GasAgent;
import dev.groundburst.content.effect.GasGrid;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.content.fallout.ToxicSoilBlock;
import dev.groundburst.content.fallout.Wasteland;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Slice 12e (DESIGN.md §21.7, §21.8): the wasteland a nuclear blast leaves (its biome, its crater lake), frozen ground,
 * and toxic soil.
 */
final class WastelandTests {
    private WastelandTests() {}

    static void register() {
        GBGameTests.testOpenSky("wasteland_painted_over_the_scorched_ground", GBGameTests.FLOOR_16, 20, WastelandTests::paints);
        GBGameTests.testOpenSky("crater_floods_to_the_water_beside_it", GBGameTests.FLOOR_16, 20, WastelandTests::floods);
        GBGameTests.testOpenSky("crater_far_from_water_stays_dry", GBGameTests.FLOOR_16, 20, WastelandTests::dry);
        GBGameTests.testOpenSky("frost_rimes_the_ground_and_grows_crystals", GBGameTests.FLOOR_16, 20, WastelandTests::frost);
        GBGameTests.testOpenSky("anthrax_sours_the_soil_under_it", GBGameTests.FLOOR_16, 400, WastelandTests::sours);
        GBGameTests.test("toxic_soil_kills_what_grows_on_it", GBGameTests.FLOOR_16, 20, WastelandTests::kills);
    }

    private static void floor(GameTestHelper helper, Block block, int top) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, top, 15)) {
            helper.setBlock(p, block);
        }
    }

    /** Painted over a disc of 5: the middle is wasteland, a corner of the plot (8 blocks off) is not. */
    private static void paints(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 0);
        var level = helper.getLevel();
        Wasteland.paintNow(level, helper.absoluteVec(new Vec3(8, 1, 8)), 5);
        helper.assertTrue(level.getBiome(helper.absolutePos(new BlockPos(8, 1, 8))).is(Wasteland.BIOME), "the middle is wasteland");
        helper.assertTrue(level.getBiome(helper.absolutePos(new BlockPos(8, 8, 8))).is(Wasteland.BIOME), "up into the air over it too");
        helper.assertFalse(level.getBiome(helper.absolutePos(new BlockPos(15, 1, 15))).is(Wasteland.BIOME), "but not past its edge");
        helper.succeed();
    }

    /**
     * A bowl 3 deep and 4 across dug into ground 5 high, a pool beside it on the rim: the crater fills to a block under
     * the pool's surface (the rim's lowest point), to the shore only, and not above.
     */
    private static void floods(GameTestHelper helper) {
        bowl(helper);
        helper.setBlock(new BlockPos(14, 5, 8), Blocks.WATER);     // a pool on the rim, east
        var level = helper.getLevel();
        int water = Wasteland.floodNow(level, helper.absoluteVec(new Vec3(8.5, 5, 8.5)), 4);
        helper.assertTrue(water == helper.absolutePos(new BlockPos(0, 4, 0)).getY(), "it fills to a block under the rim (" + water + ")");
        helper.assertBlockPresent(Blocks.WATER, new BlockPos(8, 3, 8));
        helper.assertBlockPresent(Blocks.WATER, new BlockPos(8, 4, 8));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 5, 8));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 6, 8));
        helper.succeed();
    }

    /** The same bowl with no water near it, in a world whose sea is far below: it stays dry. */
    private static void dry(GameTestHelper helper) {
        bowl(helper);
        var level = helper.getLevel();
        boolean below = helper.absolutePos(new BlockPos(0, 2, 0)).getY() < level.getSeaLevel() - 1;
        int water = Wasteland.floodNow(level, helper.absoluteVec(new Vec3(8.5, 5, 8.5)), 4);
        helper.assertTrue(below ? water != Integer.MIN_VALUE : water == Integer.MIN_VALUE,
                below ? "under the sea level groundwater fills it" : "above the sea level and away from water it stays dry");
        helper.succeed();
    }

    private static void bowl(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 5);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double d = Math.hypot(x - 8, z - 8);
                int depth = (int) Math.round(3 * (1 - d * d / 16));
                for (int k = 0; k < depth; k++) {
                    helper.setBlock(new BlockPos(x, 5 - k, z), Blocks.AIR);
                }
            }
        }
    }

    /** Stone on the west half, grass on the east with plants on it; a burst of 5 in the middle. */
    private static void frost(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), x < 8 ? Blocks.STONE : Blocks.GRASS_BLOCK);
            }
        }
        for (int z = 6; z <= 10; z++) {
            helper.setBlock(new BlockPos(10, 1, z), Blocks.SHORT_GRASS);
        }
        helper.setBlock(new BlockPos(11, 1, 8), Blocks.POPPY);
        Frost.freeze(helper.getLevel(), helper.absoluteVec(new Vec3(8, 1, 8)), 5, 100, null);
        int stone = 0, grass = 0, plants = 0, crystals = 0;
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 3, 15)) {
            var s = helper.getBlockState(p);
            stone += s.is(GBFallout.FROSTED_STONE.block().get()) ? 1 : 0;
            grass += s.is(GBFallout.FROSTED_GRASS_BLOCK.block().get()) ? 1 : 0;
            plants += s.is(GBFallout.FROZEN_PLANT.block().get()) ? 1 : 0;
            crystals += s.is(GBFallout.CRYO_CRYSTAL.block().get()) ? 1 : 0;
        }
        helper.assertTrue(stone > 10, "stone rimed (" + stone + ")");
        helper.assertTrue(grass > 10, "grass rimed (" + grass + ")");
        helper.assertTrue(plants >= 4, "plants frozen solid (" + plants + ")");
        helper.assertTrue(crystals >= 1, "crystals grown round the middle (" + crystals + ")");
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(15, 0, 15));
        helper.succeed();
    }

    /** A thick anthrax cloud lying over grass turns some of it to toxic soil. */
    private static void sours(GameTestHelper helper) {
        floor(helper, Blocks.GRASS_BLOCK, 0);
        GasGrid.of(helper.getLevel()).fill(helper.getLevel(), helper.absoluteVec(new Vec3(8, 1.5, 8)), 4, GasAgent.ANTHRAX, 1.5f);
        helper.succeedWhen(() -> {
            int toxic = 0;
            for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
                toxic += helper.getBlockState(p).is(GBFallout.TOXIC_SOIL.block().get()) ? 1 : 0;
            }
            helper.assertTrue(toxic >= 2, "the soil sours under the cloud (" + toxic + ")");
        });
    }

    /** Soil turned under a crop: the crop withers; under a flower: the flower goes. */
    private static void kills(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 5));
        helper.setBlock(new BlockPos(8, 0, 8), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(8, 1, 8), Blocks.DANDELION);
        ToxicSoilBlock.taint(helper.getLevel(), helper.absolutePos(new BlockPos(4, 0, 4)));
        ToxicSoilBlock.taint(helper.getLevel(), helper.absolutePos(new BlockPos(8, 0, 8)));
        helper.assertBlockPresent(GBFallout.TOXIC_SOIL.block().get(), new BlockPos(4, 0, 4));
        helper.assertBlockPresent(GBFallout.WITHERED_CROPS.block().get(), new BlockPos(4, 1, 4));
        helper.assertBlockPresent(GBFallout.TOXIC_SOIL.block().get(), new BlockPos(8, 0, 8));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 1, 8));
        helper.succeed();
    }
}
