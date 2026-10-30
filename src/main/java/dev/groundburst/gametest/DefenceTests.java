package dev.groundburst.gametest;

import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.defence.GBDefence;
import dev.groundburst.content.defence.Interception;
import dev.groundburst.content.defence.InterceptorBatteryBlockEntity;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Missile defence (DESIGN.md §18.2): the Interceptor Battery shoots down a missile coming down near it, lets one flying
 * out from it go, and its interceptors' kill chances fall with the missile's class, without radar, and against a hot
 * re-entry or a superweapon's missile.
 */
final class DefenceTests {
    private DefenceTests() {}

    static void register() {
        GBGameTests.test("interceptor_battery_shoots_down_an_incoming_missile", GBGameTests.FLOOR_16, 400, DefenceTests::shootsDown);
        GBGameTests.test("interceptor_battery_lets_outgoing_missiles_go", GBGameTests.FLOOR_16, 80, DefenceTests::outgoing);
        GBGameTests.test("interceptor_kill_chances", GBGameTests.FLOOR_16, 20, DefenceTests::chances);
    }

    /** A battery at (8, 1, 8), facing north, charged, its cells full. */
    private static InterceptorBatteryBlockEntity battery(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
        BlockPos at = new BlockPos(8, 1, 8);
        var block = GBDefence.BATTERY.block().get();
        var state = block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH);
        helper.setBlock(at, state);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(at), state, null, ItemStack.EMPTY);   // its upper half
        var battery = helper.getBlockEntity(at, InterceptorBatteryBlockEntity.class);
        battery.energy().generate(InterceptorBatteryBlockEntity.ENERGY_CAPACITY);
        battery.fillForTest();
        return battery;
    }

    /**
     * A missile from 180 blocks off, bound for a spot 4 blocks from the battery: the battery fires (an interceptor
     * leaves a cell) and, its kill chance set to certain, shoots it down before it lands: the missile's flight ends with
     * nothing at its target, the ground there untouched.
     */
    private static void shootsDown(GameTestHelper helper) {
        InterceptorBatteryBlockEntity battery = battery(helper);
        var level = helper.getLevel();
        Vec3 target = helper.absoluteVec(new Vec3(12.5, 1, 8.5));
        Vec3 pad = target.add(-180, 0, 0);
        Interception.killOverride = 1;
        Flights.Flight[] flight = new Flights.Flight[1];
        var spec = new dev.groundburst.content.assembly.MissileSpec(new ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()),
                SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false);
        Charges.scaled(0.25f, () -> flight[0] = Flights.launch(level, pad, target, spec, null));
        battery.engageOnlyForTest(f -> f == flight[0]);
        int due = flight[0].ticksToImpact();
        helper.onEachTick(() -> {
            if (battery.ready() < InterceptorBatteryBlockEntity.CELLS) {
                Interception.killOverride = -1;                    // fired: the next tests' interceptors roll their own
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(battery.ready() < InterceptorBatteryBlockEntity.CELLS, "it fired (due in " + due + " ticks)");
            helper.assertTrue(!Flights.of(level).all().contains(flight[0]), "the missile is down");
            helper.assertTrue(helper.getBlockState(new BlockPos(12, 0, 8)).is(Blocks.STONE), "and its warhead never went off at its target");
        });
    }

    /** A missile flying out from beside the battery to 400 blocks away: the battery lets it go. */
    private static void outgoing(GameTestHelper helper) {
        InterceptorBatteryBlockEntity battery = battery(helper);
        var level = helper.getLevel();
        Vec3 pad = helper.absoluteVec(new Vec3(3.5, 1, 3.5));
        Flights.Flight f = Flights.launch(level, pad, pad.add(400, 0, 0), ItemStack.EMPTY, null);
        battery.engageOnlyForTest(g -> g == f);
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(battery.ready() == InterceptorBatteryBlockEntity.CELLS, "no interceptor fired: " + battery.ready());
            Flights.of(level).cancelForTest(level, f);
            helper.succeed();
        });
    }

    /** The kill chances: 0.8 for a small missile radar-tracked, down by class, without radar, re-entering, a superweapon's. */
    private static void chances(GameTestHelper helper) {
        helper.assertTrue(Math.abs(Interception.killChance(SizeClass.S, true, false, false) - 0.8) < 1e-9, "small, tracked: 0.8");
        helper.assertTrue(Math.abs(Interception.killChance(SizeClass.XL, true, false, false) - 0.48) < 1e-9, "class XL: 0.48");
        helper.assertTrue(Math.abs(Interception.killChance(SizeClass.XXL, true, false, false) - 0.4) < 1e-9, "intercontinental: 0.4");
        helper.assertTrue(Math.abs(Interception.killChance(SizeClass.S, false, false, false) - 0.48) < 1e-9, "no radar: six tenths");
        helper.assertTrue(Math.abs(Interception.killChance(SizeClass.M, true, true, true) - 0.8 * 0.9 * 0.85 * 0.5) < 1e-9,
                "re-entering, a superweapon's");
        helper.assertTrue(Interception.salvo(SizeClass.M) == 1 && Interception.salvo(SizeClass.L) == 2 && Interception.salvo(SizeClass.XXL) == 2,
                "two for the big ones");
        helper.succeed();
    }
}
