package dev.groundzero.gametest;

import dev.groundzero.content.fluid.CreativeFluidTankBlockEntity;
import dev.groundzero.content.fluid.FluidPipeBlock;
import dev.groundzero.content.fluid.FluidTankBlockEntity;
import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.fluid.GasCloudBlock;
import dev.groundzero.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.WaterPumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import dev.groundzero.content.power.CableBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        GZGameTests.test("tank_empties_only_through_pull_pipe", GZGameTests.FLOOR_16, 60, TankPumpTests::pullFromTank);
        GZGameTests.test("wrench_cycles_pipe_and_cable_sides", GZGameTests.FLOOR_16, 20, TankPumpTests::wrench);
        GZGameTests.test("pump_feeds_separator_through_pipe", GZGameTests.FLOOR_16, 100, TankPumpTests::pump);
        GZGameTests.test("gas_cloud_rises_and_pools", GZGameTests.FLOOR_16, 200, TankPumpTests::gasRises);
        GZGameTests.test("creative_tank_is_endless", GZGameTests.FLOOR_16, 20, TankPumpTests::creativeTank);
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

    /** A tank never pushes by itself; a pipe side set to pull draws from it and delivers to the separator. */
    private static void pullFromTank(GameTestHelper helper) {
        tankAt(helper, new BlockPos(4, 2, 4));
        helper.setBlock(new BlockPos(5, 2, 4), GZFluids.FLUID_PIPE.block().get());
        helper.setBlock(new BlockPos(6, 2, 4), GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        ElectrolyticSeparatorBlockEntity sep = helper.getBlockEntity(new BlockPos(6, 2, 4), ElectrolyticSeparatorBlockEntity.class);
        helper.runAfterDelay(3, () -> fill(helper, new BlockPos(4, 2, 4), 5_000));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(sep.tank(0).amount() == 0, "a normal pipe does not pull: the tank keeps its water");
            BlockPos pipe = helper.absolutePos(new BlockPos(5, 2, 4));
            var level = helper.getLevel();
            var state = level.getBlockState(pipe).setValue(FluidPipeBlock.SIDES.get(Direction.WEST), FluidPipeBlock.Mode.PULL);
            level.setBlock(pipe, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
            FluidPipeBlock.changed(level, pipe, state);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(sep.tank(0).amount() == 5_000, "the pulling side empties the tank into the separator, has " + sep.tank(0).amount());
            helper.succeed();
        });
    }

    /** The Wrench cycles a pipe side normal -> pull -> off -> normal, and switches a cable join off on both cables. */
    private static void wrench(GameTestHelper helper) {
        tankAt(helper, new BlockPos(4, 2, 4));
        helper.setBlock(new BlockPos(5, 2, 4), GZFluids.FLUID_PIPE.block().get());
        helper.setBlock(new BlockPos(4, 2, 6), GZMachines.CABLES.get(0).block().get());
        helper.setBlock(new BlockPos(5, 2, 6), GZMachines.CABLES.get(0).block().get());
        var level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wrench = new ItemStack(GZMachines.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos pipe = helper.absolutePos(new BlockPos(5, 2, 4));
        // Click the west arm of the pipe (the side touching the tank).
        Vec3 armPoint = new Vec3(pipe.getX() + 0.1, pipe.getY() + 0.5, pipe.getZ() + 0.5);
        java.util.function.Supplier<FluidPipeBlock.Mode> west = () -> FluidPipeBlock.mode(level.getBlockState(pipe), Direction.WEST);
        helper.assertTrue(west.get() == FluidPipeBlock.Mode.NORMAL, "the pipe connects to the tank");
        for (FluidPipeBlock.Mode expected : new FluidPipeBlock.Mode[] {FluidPipeBlock.Mode.PULL, FluidPipeBlock.Mode.DISABLED, FluidPipeBlock.Mode.NORMAL}) {
            wrench.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(armPoint, Direction.UP, pipe, false)));
            helper.assertTrue(west.get() == expected, "expected " + expected + ", got " + west.get());
        }
        BlockPos a = helper.absolutePos(new BlockPos(4, 2, 6)), b = helper.absolutePos(new BlockPos(5, 2, 6));
        Vec3 cableArm = new Vec3(a.getX() + 0.9, a.getY() + 0.5, a.getZ() + 0.5);
        wrench.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(cableArm, Direction.UP, a, false)));
        helper.assertTrue(CableBlock.side(level.getBlockState(a), Direction.EAST) == CableBlock.Side.DISABLED
                && CableBlock.side(level.getBlockState(b), Direction.WEST) == CableBlock.Side.DISABLED, "both cables switch the join off");
        helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, a, Direction.EAST) == null, "a switched-off cable side exposes nothing");
        // Sneak-click picks the pipe up.
        player.setShiftKeyDown(true);
        wrench.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(armPoint, Direction.UP, pipe, false)));
        helper.assertTrue(level.getBlockState(pipe).isAir(), "sneak-wrench dismantles the pipe");
        helper.succeed();
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
        var level = helper.getLevel();
        BlockPos p = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, p, Direction.SOUTH) == null
                && level.getCapability(Capabilities.Fluid.BLOCK, p, Direction.EAST) != null, "water only leaves by the two side outlets");
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

    /** The creative tank takes the first fluid, is then always full, and gives any amount without running down. */
    private static void creativeTank(GameTestHelper helper) {
        BlockPos rel = new BlockPos(4, 2, 4);
        helper.setBlock(rel, GZFluids.CREATIVE_FLUID_TANK.block().get());
        CreativeFluidTankBlockEntity be = helper.getBlockEntity(rel, CreativeFluidTankBlockEntity.class);
        var h = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(rel), Direction.NORTH);
        helper.assertTrue(h != null, "creative tank exposes a fluid handler");
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(h.extract(FluidResource.of(Fluids.WATER), 1000, tx) == 0, "an empty creative tank gives nothing");
        }
        helper.assertTrue(fill(helper, rel, 1000) == 1000, "the first fluid goes in");
        helper.assertTrue(be.tank().fluid().getFluid() == Fluids.WATER && be.tank().fluid().getAmount() == CreativeFluidTankBlockEntity.CAPACITY,
                "after the first fill the tank is full of that fluid");
        for (int i = 0; i < 3; i++) {
            try (var tx = Transaction.openRoot()) {
                helper.assertTrue(h.extract(FluidResource.of(Fluids.WATER), 50_000, tx) == 50_000, "the whole request comes out");
                tx.commit();
            }
        }
        helper.assertTrue(be.tank().fluid().getAmount() == CreativeFluidTankBlockEntity.CAPACITY, "drawing never runs it down");
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(h.insert(FluidResource.of(Fluids.LAVA), 1000, tx) == 0, "a full creative tank takes nothing more");
            helper.assertTrue(h.extract(FluidResource.of(Fluids.LAVA), 1000, tx) == 0, "it only gives its own fluid");
        }
        be.tank().clear();
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(h.insert(FluidResource.of(Fluids.LAVA), 1, tx) == 1, "a cleared tank takes a new fluid");
            tx.commit();
        }
        helper.assertTrue(be.tank().fluid().getFluid() == Fluids.LAVA && be.tank().fluid().getAmount() == CreativeFluidTankBlockEntity.CAPACITY,
                "and fills with it");
        helper.succeed();
    }
}
