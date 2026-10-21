package dev.groundzero.gametest;

import dev.groundzero.content.launch.GZLaunch;
import dev.groundzero.content.launch.LaunchComputerBlockEntity;
import dev.groundzero.content.launch.LaunchSiloBlockEntity;
import dev.groundzero.content.launch.Launches;
import dev.groundzero.content.launch.MissileEntity;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The basic launch site (DESIGN.md §17): the silo and the computer forming, the silo's readiness, a launch to impact. */
final class LaunchTests {
    private LaunchTests() {}

    static void register() {
        GZGameTests.test("launch_silo_forms_and_gets_ready", GZGameTests.FLOOR_16, 20, LaunchTests::siloReady);
        GZGameTests.test("launch_computer_forms_and_links_silos", GZGameTests.FLOOR_16, 20, LaunchTests::computerLinks);
        GZGameTests.test("silo_will_not_launch_unready", GZGameTests.FLOOR_16, 20, LaunchTests::unready);
        GZGameTests.test("computer_aims_and_arms_each_silo", GZGameTests.FLOOR_16, 20, LaunchTests::perSilo);
        GZGameTests.test("armed_silo_disarms_when_unready", GZGameTests.FLOOR_16, 20, LaunchTests::disarms);
        GZGameTests.testOpenSky("missile_flies_and_its_warhead_goes_off", GZGameTests.FLOOR_16, 400, LaunchTests::flight);
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    private static void build(GameTestHelper helper, BlockPos rel, Block controller, MultiblockDefinition def) {
        helper.setBlock(rel, controller.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : def.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    private static LaunchSiloBlockEntity silo(GameTestHelper helper, BlockPos rel) {
        build(helper, rel, GZLaunch.SILO_CONTROLLER.block().get(), GZLaunch.SILO);
        LaunchSiloBlockEntity s = (LaunchSiloBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(s != null, "a silo controller");
        s.revalidate();
        return s;
    }

    /** Formed, the silo asks for a missile, then a warhead, then power; then it is ready. */
    private static void siloReady(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        helper.assertTrue(s.isFormed(), "formed");
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.NO_MISSILE, "wants a missile: " + s.readiness());
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, new ItemStack(GZLaunch.BASIC_MISSILE.get()));
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.NO_WARHEAD, "wants a warhead: " + s.readiness());
        helper.assertTrue(!LaunchSiloBlockEntity.isWarhead(new ItemStack(GZOrdnance.C4.item().get())), "C4 is no warhead");
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_WARHEAD, new ItemStack(GZOrdnance.HE_BOMB.item().get()));
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.NO_POWER, "wants power: " + s.readiness());
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready: " + s.readiness());
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.STONE);              // something in the missile's column
        s.revalidate();
        helper.assertTrue(!s.isFormed(), "the missile's column must be clear");
        helper.succeed();
    }

    /** The computer forms, and finds the silo near it. */
    private static void computerLinks(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        BlockPos rel = new BlockPos(11, 1, 11);
        build(helper, rel, GZLaunch.COMPUTER_CONTROLLER.block().get(), GZLaunch.COMPUTER);
        LaunchComputerBlockEntity c = (LaunchComputerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(c != null, "a computer controller");
        c.revalidate();
        helper.assertTrue(c.isFormed(), "formed");
        helper.succeedWhen(() -> helper.assertTrue(Launches.silosNear(helper.getLevel(), helper.absolutePos(rel)).contains(s), "it links the silo"));
    }

    private static void load(LaunchSiloBlockEntity s) {
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, new ItemStack(GZLaunch.BASIC_MISSILE.get()));
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_WARHEAD, new ItemStack(GZOrdnance.HE_BOMB.item().get()));
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
    }

    /**
     * Two ready silos and a computer: each silo has its own target and is armed on its own (no arming without a
     * target), and LAUNCH fires only the armed one, at its own target.
     */
    private static void perSilo(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity a = silo(helper, new BlockPos(3, 1, 2)), b = silo(helper, new BlockPos(10, 1, 2));
        load(a);
        load(b);
        BlockPos rel = new BlockPos(6, 1, 10);
        build(helper, rel, GZLaunch.COMPUTER_CONTROLLER.block().get(), GZLaunch.COMPUTER);
        LaunchComputerBlockEntity c = (LaunchComputerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(c != null, "a computer controller");
        c.revalidate();
        c.energy().generate(LaunchComputerBlockEntity.ENERGY_CAPACITY);
        helper.runAfterDelay(2, () -> {                                       // powered from its first tick
            var level = helper.getLevel();
            BlockPos ta = helper.absolutePos(new BlockPos(14, 1, 14)), tb = helper.absolutePos(new BlockPos(1, 1, 14));
            helper.assertTrue(!c.arm(level, b.getBlockPos(), true), "no arming without a target");
            helper.assertTrue(c.setTarget(level, a.getBlockPos(), ta.getX(), ta.getZ()), "aim A");
            helper.assertTrue(c.setTarget(level, b.getBlockPos(), tb.getX(), tb.getZ()), "aim B");
            helper.assertTrue(a.target().getX() == ta.getX() && b.target().getX() == tb.getX(), "each silo keeps its own target");
            helper.assertTrue(c.arm(level, a.getBlockPos(), true) && a.armed() && !b.armed(), "only A armed");
            helper.assertTrue(c.launchArmed(level, null) == 1, "LAUNCH fires the one armed silo");
            helper.assertTrue(a.countdown() > 0 && b.countdown() < 0, "A counts down, B waits");
            helper.assertTrue(!a.armed(), "launching clears the arming");
            helper.assertTrue(!a.setTarget(1, 1), "no re-aiming during the countdown");
            helper.succeed();
        });
    }

    /** An armed silo whose missile is taken out makes itself safe. */
    private static void disarms(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        load(s);
        BlockPos t = helper.absolutePos(new BlockPos(12, 1, 12));
        s.setTarget(t.getX(), t.getZ());
        helper.assertTrue(s.arm(true), "armed");
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, ItemStack.EMPTY);
        helper.succeedWhen(() -> helper.assertTrue(!s.armed(), "disarmed once it is not ready"));
    }

    /** No warhead, no launch. */
    private static void unready(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(5, 1, 3));
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, new ItemStack(GZLaunch.BASIC_MISSILE.get()));
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(!s.launch(helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), null), "refused");
        helper.assertTrue(s.countdown() < 0, "no countdown");
        helper.succeed();
    }

    /**
     * Ready, launched at a spot 10 blocks off: the countdown runs, the missile lifts off (taking the missile and
     * warhead), flies its arc and comes down there, and its warhead (scaled down for the test) blows a hole.
     */
    private static void flight(GameTestHelper helper) {
        floor(helper);
        Charges.bombScale = 0.3f;
        LaunchSiloBlockEntity s = silo(helper, new BlockPos(3, 1, 2));
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, new ItemStack(GZLaunch.BASIC_MISSILE.get()));
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_WARHEAD, new ItemStack(GZOrdnance.HE_BOMB.item().get()));
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        BlockPos target = new BlockPos(13, 1, 13);
        helper.assertTrue(s.launch(helper.getLevel(), helper.absolutePos(target), null), "launch ordered");
        s.skipCountdownForTest();
        AABB sky = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(64, 300, 64);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(MissileEntity.class, sky).isEmpty(), "lit on the pad");
            helper.assertTrue(s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty()
                    && s.inventory().getItem(LaunchSiloBlockEntity.SLOT_WARHEAD).isEmpty(), "missile and warhead used");
            helper.succeedWhen(() -> {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(MissileEntity.class, sky).isEmpty(), "it has come down");
                helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(13, 0, 13))).isAir(), "a hole at the target");
            });
        });
    }
}
