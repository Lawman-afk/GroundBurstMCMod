package dev.groundburst.gametest;

import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.defence.GBDefence;
import dev.groundburst.content.defence.Interception;
import dev.groundburst.content.defence.InterceptorBatteryBlockEntity;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.defence.InterceptorComplexBlockEntity;
import dev.groundburst.content.defence.Interceptors;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Missile defence (DESIGN.md §18.2, §18.5): the Interceptor Battery shoots down a missile coming down near it, lets one
 * flying out from it go, and its interceptors' kill chances fall with the missile's class, without radar, and against a
 * hot re-entry or a superweapon's missile; an EMP fries its interceptors in the air and a storm's strike knocks it out;
 * the Interceptor Complex forms from its pattern and meets a missile with a volley, at its own reach and odds.
 */
final class DefenceTests {
    private DefenceTests() {}

    static void register() {
        GBGameTests.test("interceptor_battery_shoots_down_an_incoming_missile", GBGameTests.FLOOR_16, 400, DefenceTests::shootsDown);
        GBGameTests.test("interceptor_battery_lets_outgoing_missiles_go", GBGameTests.FLOOR_16, 80, DefenceTests::outgoing);
        GBGameTests.test("interceptor_kill_chances", GBGameTests.FLOOR_16, 20, DefenceTests::chances);
        GBGameTests.test("interceptors_are_fried_flying_through_an_emp", GBGameTests.FLOOR_16, 400, DefenceTests::fried);
        GBGameTests.test("storm_strike_knocks_out_an_interceptor_battery", GBGameTests.FLOOR_16, 40, DefenceTests::stormPulse);
        GBGameTests.test("interceptor_complex_shoots_down_with_a_volley", GBGameTests.FLOOR_16, 400, DefenceTests::complexVolley);
        GBGameTests.test("interceptor_complex_needs_its_structure", GBGameTests.FLOOR_16, 20, DefenceTests::complexUnformed);
        GBGameTests.test("interceptor_complex_reach_and_odds", GBGameTests.FLOOR_16, 20, DefenceTests::complexFigures);
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

    /** An M-class missile from 180 blocks bound for 4 blocks from a battery at (8, 1, 8), its kill made certain. */
    private static Flights.Flight incoming(GameTestHelper helper, SizeClass body) {
        Vec3 target = helper.absoluteVec(new Vec3(12.5, 1, 8.5));
        Flights.Flight[] flight = new Flights.Flight[1];
        var spec = new dev.groundburst.content.assembly.MissileSpec(new ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()),
                body, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false);
        Charges.scaled(0.25f, () -> flight[0] = Flights.launch(helper.getLevel(), target.add(-180, 0, 0), target, spec, null));
        return flight[0];
    }

    /**
     * An EMP field hanging over the battery (30 blocks up, 18 round: the battery itself outside it): the interceptor it
     * fires climbs into it and is fried, bursting there, its missile untouched (a miss on its tally, no kill).
     */
    private static void fried(GameTestHelper helper) {
        InterceptorBatteryBlockEntity battery = battery(helper);
        var level = helper.getLevel();
        Interception.killOverride = 1;
        Flights.Flight f = incoming(helper, SizeClass.M);
        battery.engageOnlyForTest(g -> g == f);
        EmpFields.add(level, helper.absoluteVec(new Vec3(8.5, 31, 8.5)), 18, 400, null);
        helper.onEachTick(() -> {
            if (battery.ready() < InterceptorBatteryBlockEntity.CELLS) {
                Interception.killOverride = -1;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!battery.emped(), "the battery itself is outside the field");
            helper.assertTrue(battery.misses() >= 1, "an interceptor fried (misses " + battery.misses() + ")");
            helper.assertTrue(battery.kills() == 0, "and nothing shot down");
            helper.assertTrue(Flights.of(level).all().contains(f), "its missile flies on");
            Flights.of(level).cancelForTest(level, f);
        });
    }

    /** A storm over the plot: a strike three blocks from a battery knocks it out (an EMP pulse), so it fires at nothing. */
    private static void stormPulse(GameTestHelper helper) {
        InterceptorBatteryBlockEntity battery = battery(helper);
        var level = helper.getLevel();
        BlockPos eye = new BlockPos(8, 1, 8);
        dev.groundburst.blast.Storms.lifeOverride = 200;
        dev.groundburst.blast.Storms.rateOverride = 0.001;
        dev.groundburst.blast.Storms.quietOverride = true;
        try {
            Charges.scaled(6f / dev.groundburst.blast.Storms.RADIUS, () -> Charges.impact(level,
                    dev.groundburst.content.ordnance.charge.ChargeKind.STORM_WARHEAD, helper.absolutePos(eye), null));
        } finally {
            dev.groundburst.blast.Storms.lifeOverride = 0;
            dev.groundburst.blast.Storms.rateOverride = 0;
            dev.groundburst.blast.Storms.quietOverride = false;
        }
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(eye));
        dev.groundburst.blast.Storms.strikeForTest(level, centre, helper.absolutePos(new BlockPos(5, 1, 8)));
        helper.succeedWhen(() -> {
            helper.assertTrue(battery.emped(), "the battery knocked out by the strike's pulse");
            helper.assertTrue(battery.ready() == InterceptorBatteryBlockEntity.CELLS, "and it fired nothing");
        });
    }

    /** The complex built round a controller at (8, 1, 3) facing north (5 wide, x 6-10; 5 deep, z 3-7; 3 high), charged, its cells full. */
    private static InterceptorComplexBlockEntity complex(GameTestHelper helper, boolean build) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
        BlockPos rel = new BlockPos(8, 1, 3);
        helper.setBlock(rel, GBDefence.COMPLEX_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        if (build) {
            for (MultiblockDefinition.Cell cell : GBDefence.COMPLEX.cells(helper.absolutePos(rel), Direction.NORTH)) {
                if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                    helper.getLevel().setBlockAndUpdate(cell.pos(), GBDefence.COMPLEX.legend().get(cell.key()).preview().get());
                }
            }
        }
        var complex = helper.getBlockEntity(rel, InterceptorComplexBlockEntity.class);
        complex.revalidate();
        helper.assertTrue(complex.isFormed() == build,
                "formed: " + complex.isFormed() + " " + GBDefence.COMPLEX.check(helper.getLevel(), complex.getBlockPos(), Direction.NORTH));
        complex.energy().generate(InterceptorComplexBlockEntity.ENERGY_CAPACITY);
        complex.fillForTest();
        return complex;
    }

    /**
     * A class L missile from 180 blocks bound for beside the complex: it fires a volley (at least two, out of their own
     * cells) and, its kill made certain, shoots the missile down before it lands.
     */
    private static void complexVolley(GameTestHelper helper) {
        InterceptorComplexBlockEntity complex = complex(helper, true);
        var level = helper.getLevel();
        Interception.killOverride = 1;
        Flights.Flight f = incoming(helper, SizeClass.L);
        complex.engageOnlyForTest(g -> g == f);
        int[] most = {0};
        helper.onEachTick(() -> {
            most[0] = Math.max(most[0], Interceptors.assigned(level, f.id()));
            if (InterceptorComplexBlockEntity.CELLS - complex.ready() >= 2) {
                Interception.killOverride = -1;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(InterceptorComplexBlockEntity.CELLS - complex.ready() >= 2, "a volley: " + (InterceptorComplexBlockEntity.CELLS - complex.ready()));
            helper.assertTrue(most[0] >= 2, "two in the air at once: " + most[0]);
            helper.assertTrue(!Flights.of(level).all().contains(f), "the missile is down");
            helper.assertTrue(complex.kills() == 1, "one kill on its tally: " + complex.kills());
            helper.assertTrue(helper.getBlockState(new BlockPos(12, 0, 8)).is(Blocks.STONE), "and its warhead never went off");
        });
    }

    /** Its controller alone (no structure): not formed, and it fires at nothing. */
    private static void complexUnformed(GameTestHelper helper) {
        InterceptorComplexBlockEntity complex = complex(helper, false);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!complex.isFormed(), "not formed");
            helper.assertTrue(complex.ready() == InterceptorComplexBlockEntity.CELLS, "nothing fired");
            helper.succeed();
        });
    }

    /** The complex's figures: two and a half times a battery's reach, a surer interceptor never short of radar, twice the volley. */
    private static void complexFigures(GameTestHelper helper) {
        helper.assertTrue(Interception.COMPLEX_RADAR_RANGE == 640 && Interception.COMPLEX_OWN_RANGE == 240, "reach 640 and 240");
        helper.assertTrue(Math.abs(Interception.killChance(Interception.COMPLEX_KILL, SizeClass.S, true, false, false) - 0.95) < 1e-9,
                "a small missile: 0.95");
        helper.assertTrue(Interception.killChance(Interception.COMPLEX_KILL, SizeClass.XXL, true, true, false)
                > Interception.killChance(SizeClass.XXL, true, true, false), "surer than a battery's");
        helper.assertTrue(Interception.volley(SizeClass.M) == 2 && Interception.volley(SizeClass.L) == 4 && Interception.volley(SizeClass.XXL) == 4,
                "volleys of two and four");
        helper.succeed();
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
