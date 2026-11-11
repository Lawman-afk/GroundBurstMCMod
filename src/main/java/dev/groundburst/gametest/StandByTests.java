package dev.groundburst.gametest;

import java.util.List;
import java.util.function.Predicate;

import dev.groundburst.content.launch.CruiseLauncherBlockEntity;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.MissileSiloBlockEntity;
import dev.groundburst.content.launch.VerticalLaunchBatteryBlockEntity;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.superweapon.GBSuperweapons;
import dev.groundburst.content.superweapon.ScudStormBlockEntity;
import dev.groundburst.content.superweapon.SuperweaponBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Standing by for the Dead Hand's command rocket (DESIGN.md §17.19): every kind of launcher, launched to stand by, counts
 * down as ever and holds at its countdown's last tick with its missiles still aboard; released, it fires at once; stood
 * down (scrubbed), it keeps its missiles and is made safe.
 */
final class StandByTests {
    private StandByTests() {}

    static void register() {
        GBGameTests.testOpenSky("launch_silo_stands_by_then_fires_when_released", GBGameTests.FLOOR_16, 80, StandByTests::silo);
        GBGameTests.test("launch_silo_stood_down_keeps_its_missile", GBGameTests.FLOOR_16, 40, StandByTests::siloScrubbed);
        GBGameTests.testOpenSky("missile_silo_stands_by_with_its_doors_open", GBGameTests.FLOOR_16, 200, StandByTests::missileSilo);
        GBGameTests.testOpenSky("cruise_launcher_stands_by_with_its_boxes_up", GBGameTests.FLOOR_16, 120, StandByTests::cruise);
        GBGameTests.testOpenSky("vls_stands_by_then_ripples_when_released", GBGameTests.FLOOR_16, 80, StandByTests::vls);
        GBGameTests.test("scud_storm_stands_by_then_fires_when_released", GBGameTests.FLOOR_16, 200, StandByTests::scud);
    }

    /** The flights bound within {@code within} of {@code target}: ours (other tests' fly in the same world). */
    private static Predicate<Flights.Flight> bound(BlockPos target, double within) {
        return f -> Math.hypot(f.target().x - (target.getX() + 0.5), f.target().z - (target.getZ() + 0.5)) <= within;
    }

    private static List<Flights.Flight> flying(ServerLevel level, Predicate<Flights.Flight> ours) {
        return Flights.of(level).all().stream().filter(ours).toList();
    }

    /** Our flights ended where they are (nothing comes down on another test). */
    private static void cancel(ServerLevel level, Predicate<Flights.Flight> ours) {
        flying(level, ours).forEach(f -> Flights.of(level).cancelForTest(level, f));
    }

    /** A Launch Silo loaded, powered, aimed and armed. */
    private static LaunchSiloBlockEntity readySilo(GameTestHelper helper, BlockPos target) {
        LaunchSiloBlockEntity s = LaunchTests.formedSilo(helper);
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        s.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(s.setTarget(target.getX(), target.getZ()) && s.arm(true), "aimed and armed");
        return s;
    }

    /** Standing by: it counts down to T-1 and holds there, its missile on the pad; released, it lifts off at once. */
    private static void silo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 3)).offset(320, 0, 0);
        LaunchSiloBlockEntity s = readySilo(helper, target);
        Predicate<Flights.Flight> ours = bound(target, 4);
        helper.assertTrue(s.launchHeld(level, null), "launched to stand by");
        helper.assertTrue(s.countdown() == LaunchSiloBlockEntity.COUNTDOWN && s.standingBy(), "its countdown runs: " + s.countdown());
        s.skipCountdownForTest();
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(s.countdown() == 1 && s.standingBy() && s.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING,
                            "held at T-1: " + s.countdown());
                    helper.assertTrue(!s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty() && flying(level, ours).isEmpty(),
                            "its missile still on the pad");
                    s.release();
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty() && !s.standingBy(), "released: lit at once");
                    helper.assertTrue(flying(level, ours).size() == 1, "its missile on its way: " + flying(level, ours).size());
                    cancel(level, ours);
                })
                .thenSucceed();
    }

    /** Stood down at T-1: its countdown scrubbed, made safe, its missile kept, ready again. */
    private static void siloScrubbed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 3)).offset(-280, 0, 60);
        LaunchSiloBlockEntity s = readySilo(helper, target);
        helper.assertTrue(s.launchHeld(level, null), "launched to stand by");
        s.skipCountdownForTest();
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(s.countdown() == 1 && s.standingBy(), "held at T-1");
                    s.scrub();
                    helper.assertTrue(s.countdown() == -1 && !s.armed() && !s.standingBy(), "scrubbed and safe");
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(!s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(), "its missile kept");
                    helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready again: " + s.readiness());
                })
                .thenSucceed();
    }

    /** The Missile Silo stands by at T-1 with its doors open; released, its missile climbs out of the shaft. */
    private static void missileSilo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MissileSiloBlockEntity s = SiloTests.silo(helper);
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        s.energy().generate(MissileSiloBlockEntity.SILO_COST);
        BlockPos target = s.getBlockPos().offset(0, 0, -360);
        Predicate<Flights.Flight> ours = bound(target, 4);
        helper.assertTrue(s.setTarget(target.getX(), target.getZ()) && s.arm(true) && s.launchHeld(level, null), "aimed, armed, standing by");
        s.skipCountdownForTest();
        helper.startSequence()
                .thenExecuteAfter(MissileSiloBlockEntity.DOOR_TICKS + 20, () -> {
                    helper.assertTrue(s.doorState() == 2, "its doors open: " + s.doorState());
                    helper.assertTrue(s.countdown() == 1 && s.standingBy() && flying(level, ours).isEmpty(), "held at T-1, the missile below");
                    s.release();
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(flying(level, ours).size() == 1, "released: it goes: " + flying(level, ours).size());
                    cancel(level, ours);
                })
                .thenSucceed();
    }

    /** The cruise launcher stands by with its boxes up at its last tick; released, its first missile goes. */
    private static void cruise(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CruiseLauncherBlockEntity be = CruiseTests.launcher(helper);
        be.inventory().setItem(CruiseLauncherBlockEntity.SLOT_A, CruiseTests.cruise(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()),
                dev.groundburst.content.assembly.SizeClass.S));
        be.energy().generate(CruiseLauncherBlockEntity.ENERGY_CAPACITY);
        BlockPos target = be.getBlockPos().offset(30, 0, -420);
        Predicate<Flights.Flight> ours = bound(target, 4);
        helper.assertTrue(be.setTarget(target.getX(), target.getZ()) && be.arm(true) && be.launchHeld(level, null), "standing by");
        be.skipCountdownForTest();
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(be.phase() == CruiseLauncherBlockEntity.Phase.COUNTDOWN && be.countdown() == 1 && be.standingBy() && be.raised(),
                            "held at its last tick, the boxes up: " + be.phase() + " " + be.countdown());
                    helper.assertTrue(flying(level, ours).isEmpty() && be.loaded() == 1, "its missile still in its box");
                    be.release();
                })
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(flying(level, ours).size() == 1 && be.loaded() == 0, "released: its missile away");
                    cancel(level, ours);
                })
                .thenSucceed();
    }

    /** The Vertical Launch Battery stands by at its last tick; released, its ripple goes. */
    private static void vls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VerticalLaunchBatteryBlockEntity be = VlsTests.battery(helper);
        be.inventory().setItem(0, LaunchTests.missile());
        be.inventory().setItem(5, LaunchTests.missile());
        be.energy().generate(VerticalLaunchBatteryBlockEntity.ENERGY_CAPACITY);
        BlockPos target = be.getBlockPos().offset(-60, 0, -240);
        Predicate<Flights.Flight> ours = bound(target, 12);
        helper.assertTrue(be.setTarget(target.getX(), target.getZ()) && be.arm(true) && be.launchHeld(level, null), "standing by");
        be.skipCountdownForTest();
        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertTrue(be.phase() == VerticalLaunchBatteryBlockEntity.Phase.COUNTDOWN && be.countdown() == 1 && be.standingBy(),
                            "held at its last tick: " + be.phase() + " " + be.countdown());
                    helper.assertTrue(flying(level, ours).isEmpty() && be.loaded() == 2, "both still in their cells");
                    be.release();
                })
                .thenExecuteAfter(VerticalLaunchBatteryBlockEntity.RIPPLE + 4, () -> {
                    helper.assertTrue(flying(level, ours).size() == 2 && be.loaded() == 0, "released: the ripple away: " + flying(level, ours).size());
                    cancel(level, ours);
                })
                .thenSucceed();
    }

    /** The SCUD Storm stands by at its last tick, its erectors up; released, it fires its salvo; stood down, it keeps it. */
    private static void scud(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ScudStormBlockEntity s = SuperweaponTests.scudStorm(helper, true);
        s.inventory().setItem(SuperweaponBlockEntity.SLOT_AMMO, new ItemStack(GBSuperweapons.TOXIN_SCUD.get(), 2));
        s.energy().generate(400_000);
        s.chargeForTest();
        BlockPos target = s.getBlockPos().offset(380, 0, 240);
        Predicate<Flights.Flight> ours = bound(target, ScudStormBlockEntity.SPREAD + 1);
        helper.assertTrue(s.setTarget(target.getX(), target.getZ()) && s.arm(true) && s.launchHeld(level, null), "standing by");
        s.skipCountdownForTest();
        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING && s.countdown() == 1 && s.standingBy() && s.raised(),
                            "held at its last tick, raised: " + s.readiness() + " " + s.countdown());
                    helper.assertTrue(flying(level, ours).isEmpty() && s.loaded() == 2, "its SCUDs still on their erectors");
                    s.release();
                })
                .thenExecuteAfter(2 * ScudStormBlockEntity.FIRE_INTERVAL + 8, () -> {
                    helper.assertTrue(s.loaded() == 0 && flying(level, ours).size() == 2, "released: its salvo away: " + flying(level, ours).size());
                    cancel(level, ours);
                })
                .thenSucceed();
    }
}
