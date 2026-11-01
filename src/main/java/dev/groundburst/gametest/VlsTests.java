package dev.groundburst.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.VerticalLaunchBatteryBlockEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.content.ordnance.GBOrdnance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Vertical Launch Battery (DESIGN.md §17.15): it forms with the air over its cells clear, takes Light and Medium
 * conventional missiles only, wants power for its whole load, and ripples its volley out of its cells a quarter of a
 * second apart, each missile to its own point of its spread round the target.
 */
final class VlsTests {
    private VlsTests() {}

    static void register() {
        GBGameTests.test("vls_forms_and_takes_light_and_medium_only", GBGameTests.FLOOR_16, 20, VlsTests::forms);
        // Open sky: its missiles climb out of the plot.
        GBGameTests.testOpenSky("vls_ripples_its_volley_round_the_target", GBGameTests.FLOOR_16, 120, VlsTests::ripple);
    }

    /** The controller, second from the left of the front row: the battery fills x 3-6, z 2-7 at y 1. */
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 2);

    /** Builds the battery the way the Structure Spawner does and forms it, facing north. */
    static VerticalLaunchBatteryBlockEntity battery(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos c = helper.absolutePos(CONTROLLER);
        MultiblockDefinition def = GBLaunch.VLS;
        for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, c, Direction.NORTH)) {
            BlockState state = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                state = state.setValue(MachineBlock.FACING, Direction.NORTH);
            }
            level.setBlockAndUpdate(cell.pos(), state);
        }
        VerticalLaunchBatteryBlockEntity be = (VerticalLaunchBatteryBlockEntity) level.getBlockEntity(c);
        helper.assertTrue(be != null, "a battery controller");
        be.revalidate();
        helper.assertTrue(be.isFormed(), "formed: " + def.check(level, c, Direction.NORTH));
        return be;
    }

    static ItemStack missile(SizeClass body, MissileParts.Motor motor) {
        return MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), body, motor,
                MissileParts.Guidance.GPS, false));
    }

    private static void forms(GameTestHelper helper) {
        VerticalLaunchBatteryBlockEntity be = battery(helper);
        var level = helper.getLevel();
        BlockState cell = level.getBlockState(helper.absolutePos(new BlockPos(5, 1, 4)));
        helper.assertTrue(cell.is(GBLaunch.STRIKE_CELL.block().get()) && cell.getValue(MultiblockPartBlock.FORMED), "a Strike Cell, hidden: " + cell);
        helper.assertTrue(be.readiness() == LaunchSiloBlockEntity.Readiness.NO_MISSILE, "wants missiles: " + be.readiness());
        ItemStack light = missile(SizeClass.S, MissileParts.Motor.SRM_I), medium = LaunchTests.missile();
        helper.assertTrue(be.inventory().canPlaceItem(0, light) && be.inventory().canPlaceItem(15, medium), "takes Light and Medium missiles");
        helper.assertTrue(!be.inventory().canPlaceItem(0, missile(SizeClass.L, MissileParts.Motor.SRM_II)), "not a Heavy one");
        helper.assertTrue(!be.inventory().canPlaceItem(0, CruiseTests.cruise(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.S)),
                "not a cruise missile");
        be.inventory().setItem(0, light);
        be.inventory().setItem(15, medium);
        helper.assertTrue(be.loaded() == 2 && be.readiness() == LaunchSiloBlockEntity.Readiness.NO_POWER, "loaded, wants power: " + be.readiness());
        be.energy().generate(VerticalLaunchBatteryBlockEntity.LAUNCH_COST);
        helper.assertTrue(be.readiness() == LaunchSiloBlockEntity.Readiness.NO_POWER, "power for one is not enough for two");
        be.energy().generate(VerticalLaunchBatteryBlockEntity.LAUNCH_COST);
        helper.assertTrue(be.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready: " + be.readiness());
        helper.assertTrue(be.range() == Math.min(MissileItemRange.of(light), MissileItemRange.of(medium)), "its shorter missile's range: " + be.range());
        helper.assertTrue(be.setSpread(LaunchNetwork.Pattern.GRID, 8) && Math.abs(be.spreadRadius(4) - Math.hypot(4, 4)) < 1e-6,
                "a 2x2 grid 8 apart reaches 5.7 from its middle: " + be.spreadRadius(4));
        helper.assertTrue(be.handleButton(0) && be.pattern() == LaunchNetwork.Pattern.POINT && be.spreadRadius(16) == 0, "the pattern button cycles round");
        helper.assertTrue(be.handleButton(1) && be.spacing() == 12, "wider by 4: " + be.spacing());
        // A block over a cell stops it forming: its hatch could not open.
        BlockPos over = helper.absolutePos(new BlockPos(5, 2, 4));
        level.setBlockAndUpdate(over, Blocks.STONE.defaultBlockState());
        be.revalidate();
        helper.assertTrue(!be.isFormed(), "a block over its cells stops it");
        level.removeBlock(over, false);
        be.revalidate();
        helper.assertTrue(be.isFormed(), "formed again once it is gone");
        helper.succeed();
    }

    /** Four missiles rippled out, a quarter of a second apart, each to its own corner of a 2x2 grid 8 apart round the target. */
    private static void ripple(GameTestHelper helper) {
        VerticalLaunchBatteryBlockEntity be = battery(helper);
        var level = helper.getLevel();
        for (int cell : new int[] {0, 5, 10, 15}) {
            be.inventory().setItem(cell, LaunchTests.missile());
        }
        be.energy().generate(VerticalLaunchBatteryBlockEntity.ENERGY_CAPACITY);
        be.setSpread(LaunchNetwork.Pattern.GRID, 8);
        BlockPos target = be.getBlockPos().offset(0, 0, -200);
        // (Other tests' missiles fly in the same world: ours are the ones bound for our target.)
        java.util.function.Predicate<Flights.Flight> bound = f -> Math.hypot(f.target().x - (target.getX() + 0.5),
                f.target().z - (target.getZ() + 0.5)) < 9;
        be.setTarget(target.getX(), target.getZ());
        helper.assertTrue(be.arm(true) && be.launchArmed(level, null), "armed and launched");
        helper.assertTrue(be.phase() == VerticalLaunchBatteryBlockEntity.Phase.COUNTDOWN && be.countdown() == VerticalLaunchBatteryBlockEntity.COUNTDOWN,
                "a 10 s countdown: " + be.countdown());
        be.skipCountdownForTest();
        List<Flights.Flight> ours = new ArrayList<>();
        helper.runAfterDelay(3, () -> {
            var now = Flights.of(level).all().stream().filter(bound).toList();
            helper.assertTrue(now.size() == 1, "the first is lit: " + now.size());
            helper.assertTrue(!be.boneRotations(0).isEmpty(), "its cell's hatch swings open");
        });
        helper.runAfterDelay(3 * VerticalLaunchBatteryBlockEntity.RIPPLE + 4, () -> {
            ours.addAll(Flights.of(level).all().stream().filter(bound).toList());
            helper.assertTrue(ours.size() == 4, "all four gone: " + ours.size());
            helper.assertTrue(be.loaded() == 0, "the cells empty");
            helper.assertTrue(be.energy().stored() == VerticalLaunchBatteryBlockEntity.ENERGY_CAPACITY - 4L * VerticalLaunchBatteryBlockEntity.LAUNCH_COST,
                    "20k FE a missile: " + be.energy().stored());
            // Each its own corner of the grid (GPS scatter is small next to the grid's 8 blocks).
            long corners = ours.stream().map(f -> (f.target().x > target.getX() + 0.5 ? 1 : 0) + (f.target().z > target.getZ() + 0.5 ? 2 : 0))
                    .distinct().count();
            helper.assertTrue(corners == 4, "four corners of the spread: " + corners);
            for (Flights.Flight f : ours) {
                Flights.of(level).cancelForTest(level, f);
            }
        });
        helper.runAfterDelay(3 * VerticalLaunchBatteryBlockEntity.RIPPLE + VerticalLaunchBatteryBlockEntity.LINGER + 6, () -> {
            helper.assertTrue(be.phase() == VerticalLaunchBatteryBlockEntity.Phase.IDLE, "idle again: " + be.phase());
            helper.succeed();
        });
    }

    /** A missile item's range, as its spec has it. */
    private static final class MissileItemRange {
        static int of(ItemStack stack) {
            MissileSpec spec = dev.groundburst.content.assembly.MissileItem.spec(stack);
            return spec == null ? 0 : spec.range();
        }
    }
}
