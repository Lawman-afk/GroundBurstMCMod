package dev.groundburst.gametest;

import dev.groundburst.blast.Storms;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 11 (DESIGN.md §16.14): the storm warhead's supercell, shrunk to its plot: a region 6 blocks round (a missile's
 * storm at 0.0375 of its size) that lasts 200 ticks, striking faster than its size would.
 */
final class StormTests {
    private static final float SCALE = 6f / Storms.RADIUS;

    private StormTests() {}

    static void register() {
        GBGameTests.test("storm_strike_chains_through_creatures", GBGameTests.FLOOR_16, 60, StormTests::chain);
        GBGameTests.testOpenSky("storm_strikes_only_within_its_region", GBGameTests.FLOOR_16, 240, StormTests::region);
        GBGameTests.testOpenSky("lightning_rod_takes_the_storms_strikes", GBGameTests.FLOOR_16, 240, StormTests::rod);
        GBGameTests.test("storm_strike_blasts_a_crater", GBGameTests.FLOOR_16, 60, StormTests::crater);
    }

    private static Vec3 storm(GameTestHelper helper, int life, double rate) {
        return storm(helper, life, rate, false);
    }

    /** A storm over the plot's middle (its grass laid first); {@code quiet}: its strikes without their blasts. */
    private static Vec3 storm(GameTestHelper helper, int life, double rate, boolean quiet) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.GRASS_BLOCK);
        }
        Storms.lifeOverride = life;
        Storms.rateOverride = rate;
        Storms.quietOverride = quiet;
        BlockPos eye = new BlockPos(8, 1, 8);
        try {
            Charges.scaled(SCALE, () -> Charges.impact(helper.getLevel(), ChargeKind.STORM_WARHEAD, helper.absolutePos(eye), null));
        } finally {
            Storms.lifeOverride = 0;                         // read as it starts: the next storm is its own again
            Storms.rateOverride = 0;
            Storms.quietOverride = false;
        }
        return Vec3.atCenterOf(helper.absolutePos(eye));
    }

    /**
     * A bolt into a creeper: it is charged (vanilla lightning's own effect), the ground under it scorched and the machines
     * round it knocked out (an EMP field); the chain jumps on through the two pigs beside it, each hurt, and never reaches
     * the pig 11 blocks off.
     */
    private static void chain(GameTestHelper helper) {
        Vec3 eye = storm(helper, 100, 0.001);
        Creeper creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new BlockPos(4, 1, 4));
        Pig near = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(6, 1, 4));
        Pig next = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(7, 1, 7));
        Pig far = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(13, 1, 13));
        Storms.strikeForTest(helper.getLevel(), eye, creeper);
        helper.assertTrue(creeper.isPowered(), "the creeper is charged");
        helper.assertTrue(near.getHealth() < near.getMaxHealth(), "the chain hurts the pig beside it: " + near.getHealth());
        helper.assertTrue(next.getHealth() < next.getMaxHealth(), "and the next: " + next.getHealth());
        helper.assertTrue(far.getHealth() == far.getMaxHealth(), "but not the pig 11 off: " + far.getHealth());
        helper.assertTrue(!helper.getBlockState(new BlockPos(4, 0, 4)).is(Blocks.GRASS_BLOCK), "the grass under it is scorched");
        helper.assertTrue(EmpFields.inField(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4))), "machines round it are knocked out");
        helper.succeed();
    }

    /**
     * A storm over (8, 1, 8), 6 blocks round, striking 5 times a second at full strength (without its strikes' blasts,
     * which would throw the plot about): it strikes many times, scorching grass within its region, never the corners of the
     * plot (11 off); then it dies away.
     */
    private static void region(GameTestHelper helper) {
        Vec3 eye = storm(helper, 200, 5, true);
        int[] most = {0};
        helper.onEachTick(() -> most[0] = Math.max(most[0], Storms.strikesAt(helper.getLevel(), eye)));
        helper.runAfterDelay(190, () -> {
            helper.assertTrue(most[0] >= 10, "it struck many times: " + most[0]);
            int scorched = 0;
            for (BlockPos p : BlockPos.betweenClosed(2, 0, 2, 14, 0, 14)) {
                scorched += helper.getBlockState(p).is(Blocks.GRASS_BLOCK) ? 0 : 1;
            }
            helper.assertTrue(scorched > 0, "grass scorched in its region");
            for (BlockPos p : new BlockPos[] {new BlockPos(0, 0, 0), new BlockPos(15, 0, 0), new BlockPos(0, 0, 15), new BlockPos(15, 0, 15)}) {
                helper.assertTrue(helper.getBlockState(p).is(Blocks.GRASS_BLOCK), "nothing struck outside it: " + p);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(most[0] >= 10, "not done striking");
            helper.assertTrue(!Storms.ragingAt(helper.getLevel(), eye), "it has died away");
        });
    }

    /**
     * A bolt into open ground explodes where it comes down: a small crater (the grass at its foot and round it blown
     * away), nothing at the plot's corners.
     */
    private static void crater(GameTestHelper helper) {
        Vec3 eye = storm(helper, 100, 0.001);
        Storms.strikeForTest(helper.getLevel(), eye, helper.absolutePos(new BlockPos(8, 1, 8)));
        helper.runAfterDelay(10, () -> {
            int blown = 0;
            for (BlockPos p : BlockPos.betweenClosed(6, 0, 6, 10, 0, 10)) {
                blown += helper.getBlockState(p).isAir() ? 1 : 0;
            }
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 0, 8)).isAir(), "the ground at its foot blown away");
            helper.assertTrue(blown >= 5, "a crater round it: " + blown + " of 25 blown away");
            for (BlockPos p : new BlockPos[] {new BlockPos(0, 0, 0), new BlockPos(15, 0, 0), new BlockPos(0, 0, 15), new BlockPos(15, 0, 15)}) {
                helper.assertTrue(helper.getBlockState(p).is(Blocks.GRASS_BLOCK), "a small crater, not the plot: " + p);
            }
            helper.succeed();
        });
    }

    /**
     * A lightning rod standing in the storm draws its strikes: they come down on the rod, and the grass round it is
     * untouched; but each still sends out its EMP pulse from the rod (the user's call: a rod is no shield from the pulse).
     */
    private static void rod(GameTestHelper helper) {
        Vec3 eye = storm(helper, 200, 5);
        helper.setBlock(new BlockPos(8, 1, 10), Blocks.LIGHTNING_ROD.asList().getFirst());
        int[] most = {0};
        helper.onEachTick(() -> most[0] = Math.max(most[0], Storms.strikesAt(helper.getLevel(), eye)));
        helper.runAfterDelay(190, () -> {
            helper.assertTrue(most[0] >= 10, "it struck many times: " + most[0]);
            for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
                helper.assertTrue(helper.getBlockState(p).is(Blocks.GRASS_BLOCK), "the rod took every strike, none scorched " + p);
            }
            helper.assertTrue(dev.groundburst.content.effect.EmpFields.inField(helper.getLevel(), helper.absolutePos(new BlockPos(8, 1, 12))),
                    "the strikes the rod took still pulsed: an EMP field round it");
            helper.succeed();
        });
    }
}
