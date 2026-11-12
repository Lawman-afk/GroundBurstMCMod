package dev.groundburst.gametest;

import dev.groundburst.config.GBServerConfig.HealPlayerBlocks;
import dev.groundburst.content.fallout.FarAftermath;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.heal.HealSettings;
import dev.groundburst.heal.Healer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A nuclear blast's far aftermath (DESIGN.md §21.10): the rings' land changed (grass scorched, leaves dead, the windows
 * facing the blast broken and those turned away left), a chunk changed once only, the far wave throwing and hurting what
 * stands in the open and passing over what stands behind a wall, and all of it healing back. Each test makes a far
 * aftermath of its own, its burst some way west of the plot, and changes only its own plot.
 */
final class AftermathTests {
    private AftermathTests() {}

    static void register() {
        GBGameTests.testOpenSky("far_rings_burn_break_and_wither", GBGameTests.FLOOR_16, 20, AftermathTests::rings);
        GBGameTests.testOpenSky("far_aftermath_changes_a_chunk_once", GBGameTests.FLOOR_16, 20, AftermathTests::once);
        GBGameTests.testOpenSky("far_wave_throws_what_is_in_the_open", GBGameTests.FLOOR_16, 20, AftermathTests::wave);
        GBGameTests.testOpenSky("far_aftermath_heals_back", GBGameTests.FLOOR_16, 20, AftermathTests::heals);
        GBGameTests.testOpenSky("far_rings_kill_a_trees_leaves", GBGameTests.FLOOR_16, 20, AftermathTests::tree);
    }

    /** A tree grown as the world grows them (its leaves not persistent, its trunk under them): in the charred ring its leaves die. */
    private static void tree(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.GRASS_BLOCK);
        }
        var level = helper.getLevel();
        var oak = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.data.worldgen.features.TreeFeatures.OAK).value();
        BlockPos root = helper.absolutePos(new BlockPos(8, 1, 8));
        helper.assertTrue(oak.place(level, level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(7), root), "the oak grows");
        int before = count3(helper, s -> s.is(Blocks.OAK_LEAVES));
        convert(helper, far(helper, 120, 16));
        int green = count3(helper, s -> s.is(Blocks.OAK_LEAVES)), dead = count3(helper, s -> s.is(GBFallout.DEAD_LEAVES.block().get()));
        helper.assertTrue(before >= 20, "a tree with leaves (" + before + ")");
        helper.assertTrue(green <= before / 3, "most of its leaves dead or gone (" + green + " of " + before + " green, " + dead + " dead)");
        helper.succeed();
    }

    private static int count3(GameTestHelper helper, java.util.function.Predicate<BlockState> test) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 15, 15, 15)) {
            if (test.test(helper.getBlockState(p))) {
                n++;
            }
        }
        return n;
    }

    /** A far aftermath whose burst is {@code west} blocks west of the plot's middle: reach 60, rings to 200, 400 and 520. */
    private static FarAftermath.Far far(GameTestHelper helper, double west, long seed) {
        Vec3 middle = helper.absoluteVec(new Vec3(8, 1, 8));
        return FarAftermath.forTests(middle.add(-west, 0, 0), 40, 60, 200, 400, 520, seed);
    }

    private static BoundingBox plot(GameTestHelper helper) {
        BlockPos a = helper.absolutePos(new BlockPos(0, 0, 0)), b = helper.absolutePos(new BlockPos(15, 15, 15));
        return BoundingBox.fromCorners(a, b);
    }

    /** Changes the plot's columns in each chunk it lies in; whether anything changed. */
    private static boolean convert(GameTestHelper helper, FarAftermath.Far f) {
        BoundingBox box = plot(helper);
        boolean any = false;
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = helper.getLevel().getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    any |= FarAftermath.convert(helper.getLevel(), chunk, f, box);
                }
            }
        }
        return any;
    }

    /**
     * A field of grass (rows 2-4), short grass on row 3, a row of leaves on row 5, and two glass walls (rows 8-13) under a
     * plank roof: the west one meets the wave, the east one has stone on its west face. (The counted rows keep two blocks
     * from the edge of the plot: a column's slope is read from the heights two blocks round it.)
     */
    private static void field(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.GRASS_BLOCK);
        }
        for (int x = 2; x <= 13; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.GRASS);
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        }
        for (int z = 8; z <= 13; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.GLASS);
                helper.setBlock(new BlockPos(10, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(11, y, z), Blocks.GLASS);
            }
            for (int x = 3; x <= 12; x++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.OAK_PLANKS);
            }
        }
    }

    private static int count(GameTestHelper helper, int x0, int y, int z0, int x1, int z1, java.util.function.Predicate<BlockState> test) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(x0, y, z0, x1, y, z1)) {
            if (test.test(helper.getBlockState(p))) {
                n++;
            }
        }
        return n;
    }

    /**
     * In the charred ring (all three rings' rules in full): the open grass scorched, coarse or dead, its plants burnt off,
     * the leaves dead or gone; the glass wall facing the blast mostly broken, the one turned away whole (both roofed, so
     * only the wave reaches them).
     */
    private static void rings(GameTestHelper helper) {
        field(helper);
        convert(helper, far(helper, 120, 11));
        int grass = count(helper, 2, 0, 2, 13, 4, s -> s.is(Blocks.GRASS_BLOCK));
        int plants = count(helper, 2, 1, 3, 13, 3, s -> s.is(Blocks.GRASS));
        int leaves = count(helper, 2, 1, 5, 13, 5, s -> s.is(Blocks.OAK_LEAVES));
        int deadLeaves = count(helper, 2, 1, 5, 13, 5, s -> s.is(GBFallout.DEAD_LEAVES.block().get()));
        int westGlass = count(helper, 4, 1, 8, 4, 13, s -> s.is(Blocks.GLASS)) + count(helper, 4, 2, 8, 4, 13, s -> s.is(Blocks.GLASS));
        int eastGlass = count(helper, 11, 1, 8, 11, 13, s -> s.is(Blocks.GLASS)) + count(helper, 11, 2, 8, 11, 13, s -> s.is(Blocks.GLASS));
        helper.assertTrue(grass <= 12, "most of the open grass burnt (" + grass + " of 36 still grass)");
        helper.assertTrue(plants <= 4, "its plants burnt off (" + plants + " of 12 left)");
        helper.assertTrue(leaves <= 4 && deadLeaves >= 4, "the leaves dead or gone (" + leaves + " green, " + deadLeaves + " dead)");
        helper.assertTrue(westGlass <= 5, "the glass facing the blast broken (" + westGlass + " of 12 whole)");
        helper.assertTrue(eastGlass == 12, "the glass turned away whole (" + eastGlass + " of 12)");
        helper.assertTrue(count(helper, 3, 3, 8, 12, 13, s -> s.is(Blocks.OAK_PLANKS)) == 60, "the roof stands");
        helper.succeed();
    }

    /** A chunk once changed is marked, and the same far aftermath changes it no more; past the withered ring nothing changes. */
    private static void once(GameTestHelper helper) {
        field(helper);
        FarAftermath.Far f = far(helper, 120, 12);
        helper.assertTrue(convert(helper, f), "the first time it changes");
        BoundingBox box = plot(helper);
        LevelChunk chunk = helper.getLevel().getChunkSource().getChunkNow(box.minX() >> 4, box.minZ() >> 4);
        helper.assertTrue(chunk != null && !FarAftermath.convert(helper.getLevel(), chunk, f, box), "the second time nothing");
        helper.setBlock(new BlockPos(8, 1, 8), Blocks.GRASS);
        helper.assertTrue(!convert(helper, far(helper, 900, 13)), "beyond its rings nothing changes");
        helper.assertBlockPresent(Blocks.GRASS, new BlockPos(8, 1, 8));
        helper.succeed();
    }

    /**
     * The far wave reaching a pig in the open throws it away from the blast (east) and, within twice the reach, hurts it;
     * a pig behind a wall on its west is neither thrown nor hurt.
     */
    private static void wave(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
        for (int z = 10; z <= 14; z++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(9, y, z), Blocks.STONE);
            }
        }
        var open = helper.spawn(EntityType.PIG, new BlockPos(10, 1, 4));
        var hidden = helper.spawn(EntityType.PIG, new BlockPos(10, 1, 12));
        open.setNoAi(true);
        hidden.setNoAi(true);
        float openWas = open.getHealth(), hiddenWas = hidden.getHealth();
        FarAftermath.Far f = far(helper, 90, 14);              // the pigs about 92 blocks off: past the reach (60), inside twice it
        FarAftermath.hit(helper.getLevel(), f, 60, 200, ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds(), 6);   // (its own plot's creatures; cover within it)
        helper.assertTrue(open.getDeltaMovement().x > 0.3, "the open pig thrown east: " + open.getDeltaMovement());
        helper.assertTrue(open.getHealth() < openWas, "and hurt (" + open.getHealth() + " of " + openWas + ")");
        helper.assertTrue(Math.abs(hidden.getDeltaMovement().x) < 0.05 && hidden.getHealth() >= hiddenWas, "the pig behind the wall untouched");
        helper.succeed();
    }

    /** What the far aftermath changes is remembered and healed back, block for block. */
    private static void heals(GameTestHelper helper) {
        field(helper);
        AABB area = ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds().inflate(2);
        HealSettings s = HealSettings.forTests(HealPlayerBlocks.REPLACE, 0);
        HealSettings.testArea(helper.getLevel(), area, s);
        convert(helper, far(helper, 120, 15));
        int burnt = 36 - count(helper, 2, 0, 2, 13, 4, b -> b.is(Blocks.GRASS_BLOCK));
        BlockPos a = BlockPos.containing(area.minX, area.minY, area.minZ), b = BlockPos.containing(area.maxX - 1e-3, area.maxY - 1e-3, area.maxZ - 1e-3);
        Healer.of(helper.getLevel()).healNow(BoundingBox.fromCorners(a, b), s);
        HealSettings.endTestArea(helper.getLevel(), area);
        helper.assertTrue(burnt > 18, "it burnt the grass first (" + burnt + ")");
        helper.assertTrue(count(helper, 2, 0, 2, 13, 4, x -> x.is(Blocks.GRASS_BLOCK)) == 36, "the grass back");
        helper.assertTrue(count(helper, 2, 1, 5, 13, 5, x -> x.is(Blocks.OAK_LEAVES)) == 12, "the leaves green again");
        helper.assertTrue(count(helper, 4, 1, 8, 4, 13, x -> x.is(Blocks.GLASS)) == 6 && count(helper, 4, 2, 8, 4, 13, x -> x.is(Blocks.GLASS)) == 6,
                "the windows whole");
        helper.succeed();
    }
}
