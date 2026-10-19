package dev.groundzero.gametest;

import dev.groundzero.content.fluid.FluidTankBlockEntity;
import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.fluid.GasCloudBlock;
import dev.groundzero.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.WaterPumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Expandable fluid tanks, the water pump and released gases. */
final class TankPumpTests {
    private TankPumpTests() {}

    static void register() {
        GZGameTests.test("tanks_merge_into_a_box", GZGameTests.FLOOR_16, 40, TankPumpTests::merge);
        GZGameTests.test("tanks_need_a_square_box", GZGameTests.FLOOR_16, 40, TankPumpTests::invalidShape);
        GZGameTests.test("tank_split_keeps_shares", GZGameTests.FLOOR_16, 40, TankPumpTests::split);
        GZGameTests.test("tank_drains_into_machine_below", GZGameTests.FLOOR_16, 40, TankPumpTests::drainDown);
        GZGameTests.test("pump_feeds_separator_through_pipe", GZGameTests.FLOOR_16, 100, TankPumpTests::pump);
        GZGameTests.test("gas_cloud_rises_and_pools", GZGameTests.FLOOR_16, 200, TankPumpTests::gasRises);
    }

    private static void tankAt(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, GZFluids.FLUID_TANK.block().get());
    }

    private static FluidTankBlockEntity tank(GameTestHelper helper, BlockPos rel) {
        return helper.getBlockEntity(rel, FluidTankBlockEntity.class);
    }

    private static int fill(GameTestHelper helper, BlockPos rel, int amount) {
        var h = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(rel), Direction.UP);
        try (var tx = Transaction.openRoot()) {
            int in = h.insert(FluidResource.of(Fluids.WATER), amount, tx);
            tx.commit();
            return in;
        }
    }

    private static void merge(GameTestHelper helper) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    tankAt(helper, new BlockPos(4 + x, 2 + y, 4 + z));
                }
            }
        }
        helper.succeedWhen(() -> {
            FluidTankBlockEntity c = tank(helper, new BlockPos(4, 2, 4));
            helper.assertTrue(c.isController() && c.width() == 2 && c.height() == 2, "the eight blocks form one 2x2x2 tank");
            helper.assertTrue(c.tank().capacity() == 8 * FluidTankBlockEntity.CAPACITY_PER_BLOCK, "capacity is 8 blocks' worth");
            FluidTankBlockEntity top = tank(helper, new BlockPos(5, 3, 5));
            helper.assertTrue(!top.isController() && top.controllerPos().equals(c.getBlockPos()), "every block points at the controller");
            var state = helper.getBlockState(new BlockPos(4, 2, 4));
            helper.assertTrue(state.getValue(PipeBlock.EAST) && state.getValue(PipeBlock.UP) && state.getValue(PipeBlock.SOUTH)
                    && !state.getValue(PipeBlock.WEST), "the corner block connects inward only");
            helper.assertTrue(fill(helper, new BlockPos(5, 3, 5), 100_000) == 100_000, "any block of the tank fills the whole");
            helper.assertTrue(c.tank().fluid().getAmount() == 100_000, "the controller holds it");
        });
    }

    private static void invalidShape(GameTestHelper helper) {
        tankAt(helper, new BlockPos(4, 2, 4));
        tankAt(helper, new BlockPos(5, 2, 4));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(tank(helper, new BlockPos(4, 2, 4)).width() == 1 && tank(helper, new BlockPos(5, 2, 4)).isController(),
                    "a 2x1 row is not a tank shape: both stay single");
            helper.assertTrue(!helper.getBlockState(new BlockPos(4, 2, 4)).getValue(PipeBlock.EAST), "single tanks show their own frame");
            helper.succeed();
        });
    }

    private static void split(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            tankAt(helper, new BlockPos(4, y, 4));
        }
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(tank(helper, new BlockPos(4, 2, 4)).height() == 3, "a 1x1x3 column forms");
            fill(helper, new BlockPos(4, 2, 4), 30_000);
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(4, 3, 4)), false);
            helper.assertTrue(tank(helper, new BlockPos(4, 2, 4)).tank().fluid().getAmount() == 10_000, "the bottom keeps its third");
            helper.assertTrue(tank(helper, new BlockPos(4, 4, 4)).tank().fluid().getAmount() == 10_000, "the top keeps its third");
            helper.assertTrue(tank(helper, new BlockPos(4, 4, 4)).isController(), "the top is a tank of its own");
            helper.succeed();
        });
    }

    private static void drainDown(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 1, 4), GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        ElectrolyticSeparatorBlockEntity sep = helper.getBlockEntity(new BlockPos(4, 1, 4), ElectrolyticSeparatorBlockEntity.class);
        tankAt(helper, new BlockPos(4, 2, 4));
        helper.runAfterDelay(3, () -> fill(helper, new BlockPos(4, 2, 4), 5_000));
        helper.succeedWhen(() -> helper.assertTrue(sep.tank(0).amount() == 5_000, "the tank empties into the machine below, has " + sep.tank(0).amount()));
    }

    private static void pump(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.WATER);
        helper.setBlock(new BlockPos(4, 2, 4), GZMachines.WATER_PUMP.block().get());
        WaterPumpBlockEntity pump = helper.getBlockEntity(new BlockPos(4, 2, 4), WaterPumpBlockEntity.class);
        pump.energy().generate(10_000);
        helper.setBlock(new BlockPos(5, 2, 4), GZFluids.FLUID_PIPE.block().get());
        helper.setBlock(new BlockPos(6, 2, 4), GZFluids.FLUID_PIPE.block().get());
        helper.setBlock(new BlockPos(7, 2, 4), GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        ElectrolyticSeparatorBlockEntity sep = helper.getBlockEntity(new BlockPos(7, 2, 4), ElectrolyticSeparatorBlockEntity.class);
        helper.succeedWhen(() -> helper.assertTrue(sep.tank(0).amount() >= 2_000, "pumped water reaches the separator, has " + sep.tank(0).amount()));
    }

    private static void gasRises(GameTestHelper helper) {
        // A stone ceiling over a 3x3 area at y=6; release a full cloud at y=2.
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 6, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(5, 2, 5), GZFluids.DEUTERIUM.cloud().get().defaultBlockState());
        helper.succeedWhen(() -> {
            helper.assertTrue(!helper.getBlockState(new BlockPos(5, 2, 5)).is(GZFluids.DEUTERIUM.cloud().get()), "the gas has left the ground");
            int total = 0;
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    var s = helper.getBlockState(new BlockPos(x, 5, z));
                    if (s.is(GZFluids.DEUTERIUM.cloud().get())) {
                        total += s.getValue(GasCloudBlock.VOLUME);
                    }
                }
            }
            helper.assertTrue(total >= 6 && helper.getBlockState(new BlockPos(4, 5, 5)).is(GZFluids.DEUTERIUM.cloud().get()),
                    "it pools under the ceiling and spreads sideways, total " + total);
        });
    }
}
