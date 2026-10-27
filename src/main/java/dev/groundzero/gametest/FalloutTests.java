package dev.groundzero.gametest;

import java.util.List;

import dev.groundzero.blast.TerrainTable;
import dev.groundzero.content.fallout.AshLayerBlock;
import dev.groundzero.content.fallout.FalloutAshBlock;
import dev.groundzero.content.fallout.FalloutPlume;
import dev.groundzero.content.fallout.GZFallout;
import dev.groundzero.content.fallout.Plumes;
import dev.groundzero.content.fallout.Wind;
import dev.groundzero.content.fallout.Withering;
import dev.groundzero.content.radiation.RadiationSource;
import dev.groundzero.content.radiation.RadiationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Slice 12b (DESIGN.md §21.8): the wind, fallout plumes and the ash they lay, the nuclear terrain table and the crater's
 * lining, plants dying in strong fields, dead grass under cover.
 */
final class FalloutTests {
    private FalloutTests() {}

    static void register() {
        GZGameTests.test("wind_blows_the_way_it_heads", GZGameTests.FLOOR_16, 20, FalloutTests::wind);
        // Open sky: ash settles on whatever is uppermost, and an ordinary plot has a lid.
        GZGameTests.testOpenSky("plume_drifts_downwind_laying_fallout", GZGameTests.FLOOR_16, 20, FalloutTests::plume);
        GZGameTests.testOpenSky("fallout_ash_settles_and_weathers", GZGameTests.FLOOR_16, 20, FalloutTests::ash);
        GZGameTests.test("nuclear_table_burns_and_lines_the_ground", GZGameTests.FLOOR_16, 20, FalloutTests::table);
        GZGameTests.testOpenSky("strong_fields_wither_plants", GZGameTests.FLOOR_16, 20, FalloutTests::wither);
        GZGameTests.test("dead_grass_under_cover_turns_to_dirt", GZGameTests.FLOOR_16, 20, FalloutTests::covered);
        GZGameTests.testOpenSky("nuclear_blast_glasses_the_sand_round_it", GZGameTests.FLOOR_16, 200, FalloutTests::glassed);
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** Heading 0 blows south (+z), a quarter turn east (+x); the vector is as long as the speed. */
    private static void wind(GameTestHelper helper) {
        Vec3 south = Wind.vector(0, 3), east = Wind.vector((float) Math.PI / 2, 2);
        helper.assertTrue(south.z > 2.99 && Math.abs(south.x) < 1e-6, "heading 0 is south: " + south);
        helper.assertTrue(east.x > 1.99 && Math.abs(east.z) < 1e-6, "a quarter turn is east: " + east);
        helper.succeed();
    }

    /**
     * A plume carried east 2 blocks a second along the plot: it lays sources as it goes, each further east, weaker and
     * with the burst's half-life, lets ash fall under it, and is spent at its reach.
     */
    private static void plume(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 8.5));
        int halfLife = Plumes.halfLife(40_000, 720_000);
        FalloutPlume plume = new FalloutPlume(start.x, start.y, start.z, 2, 11, 0, 0, 40_000, halfLife, 30);
        long now = level.getGameTime();
        List<RadiationSource> before = RadiationSources.of(level).sources();
        for (int s = 0; s < 12 && !plume.spent(); s++) {
            plume = Plumes.step(level, plume, new Vec3(2, 0, 0), level.getRandom(), now);
        }
        List<RadiationSource> laid = RadiationSources.of(level).sources().stream().filter(z -> !before.contains(z))
                .filter(z -> Math.abs(z.center().z - start.z) < 2 && z.center().x > start.x).sorted((a, b) -> Double.compare(a.center().x, b.center().x))
                .toList();
        for (RadiationSource z : laid) {
            RadiationSources.of(level).removeNear(z.center(), 0.01);
        }
        helper.assertTrue(plume.spent(), "spent at its reach: " + plume);
        helper.assertTrue(laid.size() >= 3, "it laid its fallout as it went: " + laid);
        for (int i = 0; i < laid.size(); i++) {
            helper.assertTrue(laid.get(i).halfLife() == halfLife, "the burst's half-life: " + laid.get(i));
            helper.assertTrue(i == 0 || laid.get(i).peak() < laid.get(i - 1).peak(), "weaker downwind: " + laid);
        }
        int ash = 0;
        for (BlockPos p : BlockPos.betweenClosed(0, 1, 4, 15, 1, 12)) {
            if (helper.getBlockState(p).is(GZFallout.FALLOUT_ASH.block().get())) {
                ash++;
            }
        }
        helper.assertTrue(ash > 0, "Fallout Ash under its path");
        helper.succeed();
    }

    /** Ash settles a layer at a time, on ground but not in the air over water; Fallout Ash weathers into plain ash. */
    private static void ash(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BlockPos rel = new BlockPos(4, 1, 4);
        BlockPos at = helper.absolutePos(rel);
        Plumes.settle(level, at.getX(), at.getZ());
        helper.assertBlockProperty(rel, AshLayerBlock.LAYERS, 1);
        Plumes.settle(level, at.getX(), at.getZ());
        helper.assertBlockProperty(rel, AshLayerBlock.LAYERS, 2);
        BlockState ash = level.getBlockState(at);
        ((FalloutAshBlock) ash.getBlock()).weather(ash, level, at);
        helper.assertBlockPresent(GZFallout.ASH.block().get(), rel);
        helper.assertBlockProperty(rel, AshLayerBlock.LAYERS, 2);
        helper.setBlock(new BlockPos(10, 0, 10), Blocks.WATER);
        BlockPos wet = helper.absolutePos(new BlockPos(10, 1, 10));
        Plumes.settle(level, wet.getX(), wet.getZ());
        helper.assertBlockNotPresent(GZFallout.FALLOUT_ASH.block().get(), new BlockPos(10, 1, 10));
        helper.succeed();
    }

    /** The nuclear table: earth, stone, sand and wood burnt; the crater's wall slag, trinitite and irradiated soil. */
    private static void table(GameTestHelper helper) {
        TerrainTable nuclear = TerrainTable.byId(TerrainTable.NUCLEAR), conventional = TerrainTable.byId(TerrainTable.CONVENTIONAL);
        helper.assertTrue(nuclear.scorched(Blocks.GRASS_BLOCK.defaultBlockState()).is(GZFallout.SCORCHED_EARTH.block().get()), "grass to scorched earth");
        helper.assertTrue(nuclear.scorched(Blocks.STONE.defaultBlockState()).is(GZFallout.SCORCHED_STONE.block().get()), "stone to scorched stone");
        helper.assertTrue(nuclear.scorched(Blocks.SAND.defaultBlockState()).is(GZFallout.GLASSED_SAND.block().get()), "sand glassed");
        helper.assertTrue(nuclear.scorched(Blocks.SANDSTONE.defaultBlockState()).is(GZFallout.GLASSED_SAND.block().get()), "sandstone glassed");
        helper.assertTrue(nuclear.scorched(Blocks.DEEPSLATE.defaultBlockState()).is(GZFallout.SCORCHED_STONE.block().get()), "deepslate scorched");
        BlockState log = nuclear.scorched(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        helper.assertTrue(log.is(GZFallout.CHARRED_LOG.block().get()) && log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "a log charred, lying as it lay");
        helper.assertTrue(nuclear.scorched(Blocks.OAK_WOOD.defaultBlockState()).is(GZFallout.CHARRED_WOOD.block().get()), "wood charred");
        helper.assertTrue(nuclear.scorched(Blocks.SPRUCE_PLANKS.defaultBlockState()).is(GZFallout.CHARRED_PLANKS.block().get()), "planks charred");
        helper.assertTrue(conventional.scorched(Blocks.GRASS_BLOCK.defaultBlockState()).is(Blocks.DIRT), "a conventional blast only burns grass to dirt");
        helper.assertTrue(nuclear.lining(Blocks.STONE.defaultBlockState()).is(GZFallout.SLAG.block().get()), "rock melts to slag");
        helper.assertTrue(nuclear.lining(Blocks.SAND.defaultBlockState()).is(GZFallout.TRINITITE.block().get()), "sand fuses to trinitite");
        helper.assertTrue(nuclear.lining(Blocks.SANDSTONE.defaultBlockState()).is(GZFallout.TRINITITE.block().get()), "and sandstone");
        helper.assertTrue(nuclear.lining(Blocks.DIRT.defaultBlockState()).is(GZFallout.IRRADIATED_SOIL.block().get()), "earth irradiated");
        helper.assertTrue(conventional.lining(Blocks.STONE.defaultBlockState()) == null, "a conventional crater is not lined");
        helper.succeed();
    }

    /**
     * Columns in a strong field die from the top down: grass and its grass block, leaves on the branch over a trunk that
     * stands, crops on farmland; a strong source kills plants within its reach, a weak one nowhere.
     */
    private static void wither(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.SHORT_GRASS);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        helper.setBlock(new BlockPos(9, 0, 9), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(9, 1, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        for (BlockPos rel : List.of(new BlockPos(3, 0, 3), new BlockPos(6, 0, 6), new BlockPos(9, 0, 9))) {
            BlockPos abs = helper.absolutePos(rel);
            Withering.wither(level, abs.getX(), abs.getZ(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        helper.assertBlockPresent(GZFallout.DEAD_GRASS_BLOCK.block().get(), new BlockPos(3, 0, 3));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
        helper.assertBlockPresent(GZFallout.DEAD_LEAVES.block().get(), new BlockPos(6, 2, 6));
        helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(6, 1, 6));
        helper.assertBlockPresent(GZFallout.WITHERED_CROPS.block().get(), new BlockPos(9, 1, 9));
        long now = level.getGameTime();
        Vec3 c = helper.absoluteVec(new Vec3(8, 1, 8));
        double strong = Withering.reach(new RadiationSource(c, 100, 60_000, 4_000, now), now);
        double weak = Withering.reach(new RadiationSource(c, 100, 800, 4_000, now), now);
        helper.assertTrue(strong > 80 && strong < 100, "60 000 RAD/h kills plants out to 1 000 RAD/h: " + strong);
        helper.assertTrue(weak == 0, "800 RAD/h kills none");
        helper.succeed();
    }

    /**
     * A tactical warhead at a tenth of its size on sand: the sand its fireball sees round the crater is glassed, and the
     * crater's sandy wall fused to trinitite.
     */
    private static void glassed(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.SANDSTONE);
        }
        for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 15, 2, 15)) {
            helper.setBlock(p, Blocks.SAND);
        }
        var level = helper.getLevel();
        BlockPos burst = helper.absolutePos(new BlockPos(8, 3, 8));
        float scale = dev.groundzero.content.ordnance.charge.Charges.bombScale;
        dev.groundzero.content.ordnance.charge.Charges.bombScale = 0.1f;
        dev.groundzero.content.ordnance.charge.Charges.impact(level, dev.groundzero.content.ordnance.charge.ChargeKind.TACTICAL_NUCLEAR_WARHEAD,
                burst, null);
        dev.groundzero.content.ordnance.charge.Charges.bombScale = scale;
        Vec3 centre = Vec3.atCenterOf(burst);
        helper.succeedWhen(() -> {
            int glassed = 0, trinitite = 0, sand = 0;
            for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 3, 15)) {
                BlockState s = helper.getBlockState(p);
                glassed += s.is(GZFallout.GLASSED_SAND.block().get()) ? 1 : 0;
                trinitite += s.is(GZFallout.TRINITITE.block().get()) ? 1 : 0;
                sand += s.is(Blocks.SAND) ? 1 : 0;
            }
            helper.assertTrue(glassed > 0, "glassed sand round the crater (" + glassed + " glassed, " + trinitite + " trinitite, " + sand + " sand)");
            helper.assertTrue(trinitite > 0, "trinitite in its wall (" + glassed + " glassed, " + trinitite + " trinitite, " + sand + " sand)");
            RadiationSources.of(level).removeNear(centre, 2);
            Plumes.of(level).removeNear(centre, 2);
        });
    }

    /** Dead grass covered over turns to dirt, as grass does. */
    private static void covered(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(new BlockPos(5, 0, 5), GZFallout.DEAD_GRASS_BLOCK.block().get());
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        BlockPos at = helper.absolutePos(new BlockPos(5, 0, 5));
        var level = helper.getLevel();
        level.getBlockState(at).randomTick(level, at, level.getRandom());
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, 0, 5));
        helper.succeed();
    }
}
