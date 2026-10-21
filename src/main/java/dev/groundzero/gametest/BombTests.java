package dev.groundzero.gametest;

import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.NapalmFireBlock;
import dev.groundzero.content.ordnance.ThrownExplosive;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeBlockEntity;
import dev.groundzero.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 6 slice 6d: bombs (signal, the impact fuze on landing, falling safe, the bunker buster's bore, the thermobaric
 * delay, cluster bomblets, napalm). Bombs here are scaled down ({@link Charges#bombScale}) so they stay in their plot.
 */
final class BombTests {
    private static final float SCALE = 0.3f;

    private BombTests() {}

    static void register() {
        GZGameTests.test("he_bomb_goes_off_on_its_frequency", GZGameTests.FLOOR_16, 40, BombTests::signal);
        GZGameTests.test("armed_bomb_goes_off_when_it_lands", GZGameTests.FLOOR_16, 80, BombTests::impact);
        GZGameTests.test("safe_bomb_falls_and_lands_whole", GZGameTests.FLOOR_16, 80, BombTests::safeFall);
        GZGameTests.test("bunker_buster_bores_through_obsidian", GZGameTests.FLOOR_16, 60, BombTests::bunker);
        GZGameTests.test("thermobaric_lights_its_cloud_a_moment_later", GZGameTests.FLOOR_16, 60, BombTests::thermobaric);
        GZGameTests.test("cluster_bomb_scatters_bomblets", GZGameTests.FLOOR_16, 20, BombTests::cluster);
        GZGameTests.test("napalm_bomb_lays_burning_napalm", GZGameTests.FLOOR_16, 20, BombTests::napalm);
        GZGameTests.test("napalm_fire_burns_and_burns_out", GZGameTests.FLOOR_16, 40, BombTests::napalmFire);
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = SCALE;
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    private static ChargeBlockEntity place(GameTestHelper helper, Block block, BlockPos rel, Direction facing, boolean armed) {
        helper.setBlock(rel, block.defaultBlockState().setValue(ChargeBlock.FACING, facing).setValue(ChargeBlock.ARMED, armed));
        ChargeBlockEntity be = ChargeBlock.at(helper.getLevel(), helper.absolutePos(rel));
        helper.assertTrue(be != null, "a bomb block entity");
        return be;
    }

    private static boolean is(GameTestHelper helper, BlockPos rel, Block block) {
        return helper.getLevel().getBlockState(helper.absolutePos(rel)).is(block);
    }

    /** An armed HE bomb on frequency 12 goes off when a detonator on 12 is pressed. */
    private static void signal(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.HE_BOMB.block().get(), at, Direction.NORTH, false);
        be.setFrequency(12);
        be.arm(helper.getLevel(), null);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(Charges.remote(helper.getLevel(), helper.absoluteVec(new Vec3(8, 3, 8)), 12, null, 10) == 1, "it took the signal");
            helper.succeedWhen(() -> helper.assertTrue(!is(helper, at, GZOrdnance.HE_BOMB.block().get())
                    && is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "gone off, and blown a hole"));
        });
    }

    /** An armed bomb placed in the air falls and goes off where it lands. */
    private static void impact(GameTestHelper helper) {
        floor(helper);
        int before = Charges.fired;
        place(helper, GZOrdnance.HE_BOMB.block().get(), new BlockPos(8, 8, 8), Direction.NORTH, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(Charges.fired > before, "it went off");
            helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "on the ground, where it landed");
        });
    }

    /** A safe bomb placed in the air falls and lands whole, its fuze settings kept. */
    private static void safeFall(GameTestHelper helper) {
        floor(helper);
        ChargeBlockEntity be = place(helper, GZOrdnance.HE_BOMB.block().get(), new BlockPos(8, 8, 8), Direction.NORTH, false);
        be.setFrequency(77);
        helper.succeedWhen(() -> {
            helper.assertTrue(is(helper, new BlockPos(8, 2, 8), GZOrdnance.HE_BOMB.block().get()), "landed on the floor");
            ChargeBlockEntity landed = ChargeBlock.at(helper.getLevel(), helper.absolutePos(new BlockPos(8, 2, 8)));
            helper.assertTrue(landed != null && landed.frequency() == 77 && !landed.armed(), "safe, frequency kept");
        });
    }

    /** A bunker buster on a stone column with an obsidian layer bores straight down through both. */
    private static void bunker(GameTestHelper helper) {
        floor(helper);
        for (int y = 2; y <= 9; y++) {
            helper.setBlock(new BlockPos(8, y, 8), y == 4 ? Blocks.OBSIDIAN : Blocks.STONE);
        }
        ChargeBlockEntity be = place(helper, GZOrdnance.BUNKER_BUSTER.block().get(), new BlockPos(8, 10, 8), Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        helper.succeedWhen(() -> {
            for (int y = 2; y <= 9; y++) {
                helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(8, y, 8))).isAir(), "bored through at y " + y);
            }
        });
    }

    /** The thermobaric bomb disperses its cloud first: for a moment nothing is broken, then it is lit. */
    private static void thermobaric(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.THERMOBARIC_BOMB.block().get(), at, Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!is(helper, at, GZOrdnance.THERMOBARIC_BOMB.block().get()), "the bomb has burst");
            helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.STONE) && Charges.pendingAt(helper.getLevel(), helper.absolutePos(at)),
                    "but the cloud is not lit yet");
            helper.succeedWhen(() -> helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "then it is"));
        });
    }

    /** The cluster bomb bursts into 24 bomblets. */
    private static void cluster(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.CLUSTER_BOMB.block().get(), at, Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(16, 16, 16);
        var bomblets = helper.getLevel().getEntitiesOfClass(ThrownExplosive.class, area);
        helper.assertTrue(bomblets.size() == Charges.CLUSTER_BOMBLETS, "bomblets out (" + bomblets.size() + ")");
        helper.succeed();
    }

    /** The napalm bomb lays burning napalm ahead of its nose. */
    private static void napalm(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(3, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.NAPALM_BOMB.block().get(), at, Direction.EAST, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        int ahead = 0, behind = 0;
        for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(15, 4, 15)))) {
            if (helper.getLevel().getBlockState(p).is(GZOrdnance.NAPALM_FIRE.get())) {
                if (p.getX() > helper.absolutePos(at).getX()) {
                    ahead++;
                } else {
                    behind++;
                }
            }
        }
        helper.assertTrue(ahead >= 5 && ahead > behind * 3, "napalm ahead of it (" + ahead + " ahead, " + behind + " behind)");
        helper.succeed();
    }

    /** Napalm fire sets a pig in it alight, and burns out stage by stage. */
    private static void napalmFire(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at, GZOrdnance.NAPALM_FIRE.get());
        var pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(pig.getRemainingFireTicks() > 0, "the pig is alight");
            var level = helper.getLevel();
            BlockPos a = helper.absolutePos(at);
            for (int i = 0; i < NapalmFireBlock.STAGES; i++) {
                level.getBlockState(a).tick(level, a, level.getRandom());
            }
            helper.assertTrue(!is(helper, at, GZOrdnance.NAPALM_FIRE.get()), "burned out");
            pig.discard();
            helper.succeed();
        });
    }
}
