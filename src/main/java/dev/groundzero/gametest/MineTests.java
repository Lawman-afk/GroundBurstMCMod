package dev.groundzero.gametest;

import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.Shrapnel;
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

/** Phase 6 slice 6c: mines (pressure, the bouncing canister, the claymore's laser and cone, forward only). */
final class MineTests {
    private MineTests() {}

    static void register() {
        GZGameTests.test("landmine_ignores_steps_until_armed", GZGameTests.FLOOR_16, 40, MineTests::safeAndGrace);
        GZGameTests.test("landmine_goes_off_when_stepped_on", GZGameTests.FLOOR_16, 120, MineTests::landmine);
        GZGameTests.test("bouncing_mine_pops_up_and_bursts", GZGameTests.FLOOR_16, 120, MineTests::bouncing);
        GZGameTests.test("claymore_laser_fires_a_cone_ahead", GZGameTests.FLOOR_16, 120, MineTests::claymore);
        GZGameTests.test("mines_take_no_detonator_signal", GZGameTests.FLOOR_16, 20, MineTests::noRemote);
        GZGameTests.test("claymore_spares_what_is_behind_it", GZGameTests.FLOOR_16, 60, MineTests::behind);
        GZGameTests.test("claymore_kills_at_half_range", GZGameTests.FLOOR_16, 60, MineTests::lethal);
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    private static ChargeBlockEntity place(GameTestHelper helper, Block block, BlockPos rel, Direction facing) {
        helper.setBlock(rel, block.defaultBlockState().setValue(ChargeBlock.FACING, facing));
        ChargeBlockEntity be = ChargeBlock.at(helper.getLevel(), helper.absolutePos(rel));
        helper.assertTrue(be != null, "a mine block entity");
        return be;
    }

    private static boolean is(GameTestHelper helper, BlockPos rel, Block block) {
        return helper.getLevel().getBlockState(helper.absolutePos(rel)).is(block);
    }

    /** A pig standing on a safe landmine does nothing; arming it sets it off at once. */
    private static void safeAndGrace(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity mine = place(helper, GZOrdnance.LANDMINE.block().get(), at, Direction.NORTH);
        var pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(is(helper, at, GZOrdnance.LANDMINE.block().get()), "safe: stepped on, nothing");
            mine.arm(helper.getLevel(), null);
            helper.succeedWhen(() -> helper.assertTrue(!is(helper, at, GZOrdnance.LANDMINE.block().get()), "armed, it goes off"));
        });
    }

    /** Armed and live, a landmine goes off when a pig walks onto it, and blows a hole in the ground. */
    private static void landmine(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity mine = place(helper, GZOrdnance.LANDMINE.block().get(), at, Direction.NORTH);
        mine.arm(helper.getLevel(), null);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(mine.live(), "live");
            var pig = helper.spawn(EntityTypes.PIG, at);
            pig.setNoAi(true);
            helper.succeedWhen(() -> {
                helper.assertTrue(!is(helper, at, GZOrdnance.LANDMINE.block().get()), "gone off");
                helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "a hole under it");
            });
        });
    }

    /** Stepped on, a bouncing mine throws its canister up, which bursts into fragments in the air. */
    private static void bouncing(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity mine = place(helper, GZOrdnance.BOUNCING_MINE.block().get(), at, Direction.NORTH);
        mine.arm(helper.getLevel(), null);
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(16, 12, 16);
        double ground = helper.absolutePos(at).getY();
        helper.runAfterDelay(2, () -> {
            var pig = helper.spawn(EntityTypes.PIG, at);
            pig.setNoAi(true);
            helper.runAfterDelay(4, () -> {
                var cans = helper.getLevel().getEntitiesOfClass(ThrownExplosive.class, area);
                helper.assertTrue(!is(helper, at, GZOrdnance.BOUNCING_MINE.block().get()) && cans.size() == 1, "the canister popped out");
                helper.assertTrue(cans.getFirst().getY() > ground + 0.3, "and is rising (" + (cans.getFirst().getY() - ground) + ")");
                helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(Shrapnel.class, area).size() >= 20,
                        "it burst into fragments"));
            });
        });
    }

    /** A claymore facing east: a pig crossing its laser 4 blocks ahead sets it off, and the fragments fly east. */
    private static void claymore(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(3, 2, 8);
        ChargeBlockEntity mine = place(helper, GZOrdnance.CLAYMORE.block().get(), at, Direction.EAST);
        mine.arm(helper.getLevel(), null);
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(16, 12, 16);
        helper.runAfterDelay(2, () -> {
            double reach = Charges.laserLength(helper.getLevel(), helper.absolutePos(at), helper.getLevel().getBlockState(helper.absolutePos(at)));
            helper.assertTrue(reach >= Charges.LASER_RANGE - 0.01, "the laser reaches its full range (" + reach + ")");
            var pig = helper.spawn(EntityTypes.PIG, new BlockPos(7, 2, 8));
            pig.setNoAi(true);
            double x = helper.absoluteVec(new Vec3(3.5, 2, 8.5)).x;
            helper.succeedWhen(() -> {
                helper.assertTrue(!is(helper, at, GZOrdnance.CLAYMORE.block().get()), "gone off");
                var frags = helper.getLevel().getEntitiesOfClass(Shrapnel.class, area);
                long ahead = frags.stream().filter(s -> s.getX() > x).count();
                helper.assertTrue(frags.size() >= 30 && ahead == frags.size(), "fragments all flying ahead (" + ahead + "/" + frags.size() + ")");
            });
        });
    }

    /** A landmine has no frequency: a detonator on 0 (its default) does nothing to it. */
    private static void noRemote(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity mine = place(helper, GZOrdnance.LANDMINE.block().get(), at, Direction.NORTH);
        mine.arm(helper.getLevel(), null);
        Charges.remote(helper.getLevel(), helper.absoluteVec(new Vec3(8, 3, 8)), 0, null, 10);
        helper.assertTrue(is(helper, at, GZOrdnance.LANDMINE.block().get()), "still there");
        helper.succeed();
    }

    /** A pig right behind a claymore, and one ahead in its cone: it fires; the one behind is unhurt. */
    private static void behind(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(6, 2, 8);
        ChargeBlockEntity mine = place(helper, GZOrdnance.CLAYMORE.block().get(), at, Direction.EAST);
        var back = helper.spawn(EntityTypes.PIG, new BlockPos(5, 2, 8));
        back.setNoAi(true);
        mine.arm(helper.getLevel(), null);
        mine.trigger(helper.getLevel(), null);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!is(helper, at, GZOrdnance.CLAYMORE.block().get()), "gone off");
            helper.assertTrue(back.isAlive() && back.getHealth() == back.getMaxHealth(), "the pig behind it is unhurt (" + back.getHealth() + ")");
            helper.succeed();
        });
    }

    /** A villager (20 health and no armour, like a bare player) 5 blocks ahead of a claymore is killed by it. */
    private static void lethal(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(3, 2, 8);
        ChargeBlockEntity mine = place(helper, GZOrdnance.CLAYMORE.block().get(), at, Direction.EAST);
        var target = helper.spawn(EntityTypes.VILLAGER, new BlockPos(8, 2, 8));
        target.setNoAi(true);
        mine.arm(helper.getLevel(), null);
        mine.trigger(helper.getLevel(), null);
        helper.succeedWhen(() -> helper.assertTrue(!target.isAlive(), "killed (" + target.getHealth() + " left)"));
    }
}
