package dev.groundzero.gametest;

import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeBlockEntity;
import dev.groundzero.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Phase 6 slice 6b: placed charges (arming, fuzes, the detonator, stacking, sympathetic detonation, breaching, thermite, defusing). */
final class ChargeTests {
    private ChargeTests() {}

    static void register() {
        GZGameTests.test("charge_is_safe_until_armed", GZGameTests.FLOOR_16, 40, ChargeTests::safe);
        GZGameTests.test("detonator_sets_off_only_its_frequency", GZGameTests.FLOOR_16, 40, ChargeTests::detonator);
        GZGameTests.test("detonator_starts_a_timer", GZGameTests.FLOOR_16, 80, ChargeTests::timer);
        GZGameTests.test("c4_has_no_timer", GZGameTests.FLOOR_16, 20, ChargeTests::noTimer);
        GZGameTests.test("c4_stacks_up_to_four", GZGameTests.FLOOR_16, 20, ChargeTests::stacking);
        GZGameTests.test("charges_set_each_other_off", GZGameTests.FLOOR_16, 60, ChargeTests::sympathetic);
        GZGameTests.test("breaching_charge_cuts_a_3x3_hole_3_deep", GZGameTests.FLOOR_16, 40, ChargeTests::breach);
        GZGameTests.test("thermite_burns_a_shaft_until_obsidian", GZGameTests.FLOOR_16, 160, ChargeTests::thermite);
        GZGameTests.test("timed_bomb_defused_by_the_right_wire", GZGameTests.FLOOR_16, 40, ChargeTests::defuse);
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(p, block);
        }
    }

    /** A charge of {@code block} at {@code rel}, stuck to the face pointing {@code facing}. */
    private static ChargeBlockEntity place(GameTestHelper helper, Block block, BlockPos rel, Direction facing) {
        helper.setBlock(rel, block.defaultBlockState().setValue(ChargeBlock.FACING, facing));
        ChargeBlockEntity be = ChargeBlock.at(helper.getLevel(), helper.absolutePos(rel));
        helper.assertTrue(be != null, "a charge block entity");
        return be;
    }

    private static boolean is(GameTestHelper helper, BlockPos rel, Block block) {
        return helper.getLevel().getBlockState(helper.absolutePos(rel)).is(block);
    }

    /** Redstone does nothing to a safe charge; armed, it goes off. */
    private static void safe(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.C4.block().get(), at, Direction.UP);
        helper.setBlock(new BlockPos(9, 2, 8), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(is(helper, at, GZOrdnance.C4.block().get()) && !be.armed(), "safe: redstone does nothing");
            be.arm(helper.getLevel(), null);
            helper.setBlock(new BlockPos(9, 2, 8), Blocks.AIR);
            helper.setBlock(new BlockPos(9, 2, 8), Blocks.REDSTONE_BLOCK);
            helper.succeedWhen(() -> helper.assertTrue(!is(helper, at, GZOrdnance.C4.block().get()) && is(helper, new BlockPos(8, 1, 8), Blocks.AIR),
                    "armed: redstone sets it off, and it blows a hole"));
        });
    }

    /** A detonator on 42 sets off the armed charge on 42; not the one on 7, nor a safe one on 42 (it is not armed). */
    private static void detonator(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        BlockPos a = new BlockPos(3, 2, 3), b = new BlockPos(13, 2, 13);
        ChargeBlockEntity on42 = place(helper, GZOrdnance.BREACHING_CHARGE.block().get(), a, Direction.UP);
        ChargeBlockEntity on7 = place(helper, GZOrdnance.BREACHING_CHARGE.block().get(), b, Direction.UP);
        on42.setFrequency(42);
        on7.setFrequency(7);
        BlockPos c = new BlockPos(13, 2, 3);
        ChargeBlockEntity safe42 = place(helper, GZOrdnance.BREACHING_CHARGE.block().get(), c, Direction.UP);
        safe42.setFrequency(42);
        on42.arm(helper.getLevel(), null);
        on7.arm(helper.getLevel(), null);
        int fired = Charges.remote(helper.getLevel(), helper.absoluteVec(new Vec3(8, 3, 8)), 42, null);
        helper.assertTrue(fired == 1, "one charge on 42 (" + fired + ")");
        helper.assertTrue(!is(helper, a, GZOrdnance.BREACHING_CHARGE.block().get()), "the one on 42 went off");
        helper.assertTrue(is(helper, b, GZOrdnance.BREACHING_CHARGE.block().get()), "the one on 7 is still there");
        helper.assertTrue(is(helper, c, GZOrdnance.BREACHING_CHARGE.block().get()) && !safe42.counting(), "the safe one on 42 ignored it");
        helper.succeed();
    }

    /** A breaching charge on a 1 s timer: arming it starts nothing; the detonator starts the clock; a second later it goes. */
    private static void timer(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.BREACHING_CHARGE.block().get(), at, Direction.UP);
        be.configure(ChargeBlockEntity.Mode.TIMER, 5, 1);
        be.arm(helper.getLevel(), null);
        helper.assertTrue(!be.counting(), "arming does not start the clock");
        helper.assertTrue(Charges.remote(helper.getLevel(), helper.absoluteVec(new Vec3(8, 3, 8)), 5, null) == 1 && be.counting(),
                "the detonator starts it");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(is(helper, at, GZOrdnance.BREACHING_CHARGE.block().get()), "still waiting at half a second");
            helper.succeedWhen(() -> helper.assertTrue(!is(helper, at, GZOrdnance.BREACHING_CHARGE.block().get()), "gone off after a second"));
        });
    }

    /** C4 goes off at once: asked for a timer, it stays as it is, and a detonator sets it straight off. */
    private static void noTimer(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.C4.block().get(), at, Direction.UP);
        be.configure(ChargeBlockEntity.Mode.TIMER, 3, 20);
        helper.assertTrue(be.mode() == ChargeBlockEntity.Mode.REMOTE, "no timer on C4");
        be.arm(helper.getLevel(), null);
        Charges.remote(helper.getLevel(), helper.absoluteVec(new Vec3(8, 3, 8)), 3, null);
        helper.assertTrue(!is(helper, at, GZOrdnance.C4.block().get()), "gone off at once");
        helper.succeed();
    }

    /** Using C4 on placed C4 stacks it, up to four; armed, it cannot be added to. */
    private static void stacking(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        BlockPos at = new BlockPos(8, 2, 8);
        place(helper, GZOrdnance.C4.block().get(), at, Direction.UP);
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(GZOrdnance.C4.item().get(), 8));
        for (int i = 0; i < 5; i++) {
            helper.useBlock(at, player);
        }
        BlockState s = helper.getLevel().getBlockState(helper.absolutePos(at));
        helper.assertTrue(s.getValue(ChargeBlock.COUNT) == 4, "four stacked (" + s.getValue(ChargeBlock.COUNT) + ")");
        helper.assertTrue(player.getMainHandItem().getCount() == 5, "three used (" + player.getMainHandItem().getCount() + " left)");
        helper.succeed();
    }

    /** Two C4 charges 2 apart, the second safe: setting off the first sets off the second a moment later. */
    private static void sympathetic(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        BlockPos a = new BlockPos(6, 2, 8), b = new BlockPos(8, 2, 8);
        ChargeBlockEntity first = place(helper, GZOrdnance.C4.block().get(), a, Direction.UP);
        place(helper, GZOrdnance.C4.block().get(), b, Direction.UP);
        int before = Charges.fired;
        first.arm(helper.getLevel(), null);
        first.trigger(helper.getLevel(), null);
        helper.assertTrue(Charges.fired == before + 1, "the first went off");
        helper.succeedWhen(() -> {
            helper.assertTrue(!is(helper, b, GZOrdnance.C4.block().get()), "the second charge is gone");
            helper.assertTrue(Charges.fired >= before + 2, "and it went off too (" + (Charges.fired - before) + ")");
        });
    }

    /** Stuck to the west face of a stone wall, the breaching charge cuts a 3x3 hole 3 deep and leaves the 4th layer. */
    private static void breach(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        fill(helper, 8, 2, 5, 12, 8, 11, Blocks.STONE);
        BlockPos at = new BlockPos(7, 5, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.BREACHING_CHARGE.block().get(), at, Direction.WEST);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        for (BlockPos p : BlockPos.betweenClosed(8, 4, 7, 10, 6, 9)) {
            helper.assertTrue(is(helper, p.immutable(), Blocks.AIR), "cut through at " + p);
        }
        helper.assertTrue(is(helper, new BlockPos(11, 5, 8), Blocks.STONE), "the 4th layer is left");
        helper.assertTrue(is(helper, new BlockPos(9, 7, 8), Blocks.STONE) && is(helper, new BlockPos(9, 5, 10), Blocks.STONE),
                "nothing round the hole");
        helper.succeed();
    }

    /** Thermite on a stone wall burns 1x1 into it and stops at obsidian, leaving slag at the end of the shaft. */
    private static void thermite(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        fill(helper, 4, 2, 8, 14, 4, 8, Blocks.STONE);
        helper.setBlock(new BlockPos(9, 3, 8), Blocks.OBSIDIAN);
        BlockPos at = new BlockPos(3, 3, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.THERMITE_CHARGE.block().get(), at, Direction.WEST);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        helper.assertTrue(be.burning(), "lit");
        helper.succeedWhen(() -> {
            helper.assertTrue(!is(helper, at, GZOrdnance.THERMITE_CHARGE.block().get()), "burned out");
            for (int x = 4; x <= 7; x++) {
                helper.assertTrue(is(helper, new BlockPos(x, 3, 8), Blocks.AIR), "melted at x " + x);
            }
            helper.assertTrue(is(helper, new BlockPos(8, 3, 8), Blocks.MAGMA_BLOCK), "slag at the end");
            helper.assertTrue(is(helper, new BlockPos(9, 3, 8), Blocks.OBSIDIAN), "the obsidian stopped it");
            helper.assertTrue(is(helper, new BlockPos(5, 2, 8), Blocks.STONE) && is(helper, new BlockPos(5, 4, 8), Blocks.STONE), "1x1 only");
        });
    }

    /** A counting timed bomb cannot be made safe with the key; a wrong wire halves its time; the right one stops it. */
    private static void defuse(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.TIMED_BOMB.block().get(), at, Direction.NORTH);
        be.configure(ChargeBlockEntity.Mode.TIMER, 0, 60);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        var level = helper.getLevel();
        helper.assertTrue(be.counting() && !be.disarm(level), "counting, and it cannot simply be made safe");
        int right = be.defuseWireForTest(), boom = be.boomWireForTest();
        int dummy = 0;
        while (dummy == right || dummy == boom) {
            dummy++;
        }
        int before = be.countdown();
        helper.assertTrue(be.cut(level, dummy) == 0 && be.counting() && be.countdown() <= before / 2 + 1, "a wrong wire halves the time");
        helper.assertTrue(be.cut(level, dummy) == 0, "a cut wire cannot be cut again");
        helper.assertTrue(be.cut(level, right) == 1, "the right wire defuses it");
        helper.assertTrue(!be.counting() && !be.armed() && is(helper, at, GZOrdnance.TIMED_BOMB.block().get()), "stopped, safe, still there");
        helper.succeed();
    }
}
