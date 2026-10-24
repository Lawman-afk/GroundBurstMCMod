package dev.groundzero.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.content.assembler.AssemblerClass;
import dev.groundzero.content.assembler.MissileAssemblerBlockEntity;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileItem;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.launch.CruiseLauncherBlockEntity;
import dev.groundzero.content.launch.Flights;
import dev.groundzero.content.launch.GZLaunch;
import dev.groundzero.content.launch.LaunchComplexBlockEntity;
import dev.groundzero.content.launch.LaunchSiloBlockEntity;
import dev.groundzero.content.launch.MissileSiloBlockEntity;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.multiblock.StructureSpawnerItem;
import dev.groundzero.content.ordnance.GZOrdnance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Cruise missiles and the Cruise Missile Launcher (Phase 9 slice 9c, DESIGN.md §17.11): the launcher forms on its deck
 * with the space its boxes sweep clear, takes cruise missiles and nothing else (and no other launcher takes them), and
 * fires both its missiles at the target 3 s apart from its raised boxes, then lowers them; a cruise missile burns in its
 * box, climbs out at 45 degrees on its booster, drops it, flies low over the ground at its speed, pops up and dives onto
 * its target; an assembler builds one when its motor is a Cruise Engine.
 */
final class CruiseTests {
    private CruiseTests() {}

    static void register() {
        GZGameTests.test("cruise_launcher_forms_and_takes_only_cruise_missiles", GZGameTests.FLOOR_16, 20, CruiseTests::forms);
        GZGameTests.test("cruise_launcher_needs_its_boxes_sweep_clear", GZGameTests.FLOOR_16, 20, CruiseTests::sweep);
        // Open sky: its missiles fly off over the plot.
        GZGameTests.testOpenSky("cruise_launcher_fires_both_missiles_and_lowers", GZGameTests.FLOOR_16, 260, CruiseTests::fires);
        GZGameTests.testOpenSky("cruise_missile_flies_low_to_its_target", GZGameTests.FLOOR_16, 20, CruiseTests::flight);
        GZGameTests.test("light_missile_assembler_builds_a_cruise_missile", GZGameTests.FLOOR_16, 40, CruiseTests::assembles);
    }

    /** The controller, the deck's front left: the deck fills x 4-5, z 2-7 at y 1, and the boxes rise over it to y 6. */
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 2);

    /** Builds the launcher the way the Structure Spawner does and forms it, facing north. */
    static CruiseLauncherBlockEntity launcher(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos c = helper.absolutePos(CONTROLLER);
        MultiblockDefinition def = GZLaunch.CRUISE_LAUNCHER;
        for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, c, Direction.NORTH)) {
            BlockState state = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                state = state.setValue(MachineBlock.FACING, Direction.NORTH);
            }
            level.setBlockAndUpdate(cell.pos(), state);
        }
        CruiseLauncherBlockEntity be = (CruiseLauncherBlockEntity) level.getBlockEntity(c);
        helper.assertTrue(be != null, "a cruise launcher controller");
        be.revalidate();
        helper.assertTrue(be.isFormed(), "formed: " + def.check(level, c, Direction.NORTH));
        return be;
    }

    /** A cruise missile carrying {@code warhead} on a {@code body} airframe, GPS-guided. */
    static ItemStack cruise(ItemStack warhead, SizeClass body) {
        return MissileAssembly.missile(new MissileSpec(warhead, body, MissileParts.Motor.CRUISE, MissileParts.Guidance.GPS, false));
    }

    private static ItemStack he() {
        return cruise(new ItemStack(GZOrdnance.HE_WARHEAD.item().get()), SizeClass.S);
    }

    private static ItemStack gas() {
        return cruise(new ItemStack(GZOrdnance.GAS_WARHEAD.item().get()), SizeClass.M);
    }

    /**
     * Formed, its deck and boxes hide (the model draws them); it takes a cruise missile in each box and no other missile;
     * no other launcher takes a cruise missile. Empty it asks for missiles, then power for both (60k FE each); its range
     * is its missiles' (the Cruise Engine's 1,500 blocks), and it arms only within it.
     */
    private static void forms(GameTestHelper helper) {
        CruiseLauncherBlockEntity be = launcher(helper);
        var level = helper.getLevel();
        BlockState pad = level.getBlockState(helper.absolutePos(new BlockPos(4, 1, 5)));
        helper.assertTrue(pad.is(GZLaunch.LAUNCH_PAD.block().get()) && pad.getValue(MultiblockPartBlock.FORMED), "the deck is hidden: " + pad);
        BlockState box = level.getBlockState(helper.absolutePos(new BlockPos(5, 2, 7)));
        helper.assertTrue(box.is(GZLaunch.LAUNCH_BOX.block().get()) && box.getValue(MultiblockPartBlock.FORMED), "a Launch Box, hidden: " + box);
        double top = pad.getCollisionShape(level, helper.absolutePos(new BlockPos(4, 1, 5))).max(Direction.Axis.Y);
        helper.assertTrue(Math.abs(top - 14 / 16.0) < 1e-6, "a deck 14 px high to walk on: " + top);
        helper.assertTrue(be.readiness() == LaunchSiloBlockEntity.Readiness.NO_MISSILE, "wants missiles: " + be.readiness());
        helper.assertTrue(be.inventory().canPlaceItem(CruiseLauncherBlockEntity.SLOT_A, he()), "takes a Light cruise missile");
        helper.assertTrue(be.inventory().canPlaceItem(CruiseLauncherBlockEntity.SLOT_B, gas()), "and a Medium one");
        helper.assertTrue(!be.inventory().canPlaceItem(CruiseLauncherBlockEntity.SLOT_A, LaunchTests.missile()), "not an ordinary missile");
        MissileSpec spec = MissileItem.spec(he());
        helper.assertTrue(spec != null && spec.cruise(), "a cruise missile: " + spec);
        BlockPos o = BlockPos.ZERO;
        helper.assertTrue(!new LaunchSiloBlockEntity(o, GZLaunch.SILO_CONTROLLER.block().get().defaultBlockState()).accepts(spec), "the Launch Silo refuses it");
        helper.assertTrue(!new LaunchComplexBlockEntity(o, GZLaunch.COMPLEX_CONTROLLER.block().get().defaultBlockState()).accepts(spec),
                "the Launch Complex refuses it");
        helper.assertTrue(!new MissileSiloBlockEntity(o, GZLaunch.MISSILE_SILO_CONTROLLER.block().get().defaultBlockState()).accepts(spec),
                "the Missile Silo refuses it");
        be.inventory().setItem(CruiseLauncherBlockEntity.SLOT_A, he());
        be.inventory().setItem(CruiseLauncherBlockEntity.SLOT_B, gas());
        helper.assertTrue(be.loaded() == 2 && be.readiness() == LaunchSiloBlockEntity.Readiness.NO_POWER, "loaded, wants power: " + be.readiness());
        be.energy().generate(CruiseLauncherBlockEntity.LAUNCH_COST);
        helper.assertTrue(be.readiness() == LaunchSiloBlockEntity.Readiness.NO_POWER, "power for one is not enough for two");
        be.energy().generate(CruiseLauncherBlockEntity.LAUNCH_COST);
        helper.assertTrue(be.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready: " + be.readiness());
        helper.assertTrue(be.range() == MissileParts.Motor.CRUISE.range, "the Cruise Engine's range: " + be.range());
        BlockPos c = be.getBlockPos();
        be.setTarget(c.getX(), c.getZ() - 1600);
        helper.assertTrue(!be.arm(true), "too far to arm");
        be.setTarget(c.getX() + 200, c.getZ() - 900);
        helper.assertTrue(be.arm(true) && be.armed(), "in range: armed");
        helper.succeed();
    }

    /**
     * Anything where its boxes lie or swing up stops it forming: over the front row four blocks up (a spot the old
     * pattern missed) and over the second row five up; over the front row five up nothing sweeps, so a block there is
     * fine.
     */
    private static void sweep(GameTestHelper helper) {
        CruiseLauncherBlockEntity be = launcher(helper);
        var level = helper.getLevel();
        for (BlockPos rel : new BlockPos[] {new BlockPos(4, 5, 2), new BlockPos(5, 6, 3), new BlockPos(5, 2, 2)}) {
            BlockPos at = helper.absolutePos(rel);
            level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
            be.revalidate();
            helper.assertTrue(!be.isFormed(), "a block in the boxes' sweep at " + rel + " stops it");
            level.removeBlock(at, false);
            be.revalidate();
            helper.assertTrue(be.isFormed(), "formed again once it is gone");
        }
        BlockPos free = helper.absolutePos(new BlockPos(4, 6, 2));
        level.setBlockAndUpdate(free, Blocks.STONE.defaultBlockState());
        be.revalidate();
        helper.assertTrue(be.isFormed(), "nothing sweeps over the front row five up");
        helper.succeed();
    }

    /**
     * Loaded, powered and launched: its boxes rise while it counts down 10 s; then its first missile lights at the tail of
     * its box (60k FE), pointing out along the launcher's front, and the second 3 s later from the other box; the boxes
     * come down after, and it is idle again with both missiles gone.
     */
    private static void fires(GameTestHelper helper) {
        CruiseLauncherBlockEntity be = launcher(helper);
        var level = helper.getLevel();
        be.inventory().setItem(CruiseLauncherBlockEntity.SLOT_A, he());
        be.inventory().setItem(CruiseLauncherBlockEntity.SLOT_B, gas());
        be.energy().generate(CruiseLauncherBlockEntity.ENERGY_CAPACITY);
        BlockPos target = be.getBlockPos().offset(40, 0, -400);
        Vec3 aim = Vec3.atBottomCenterOf(target);
        // (Other tests' missiles fly in the same world: ours are the ones bound for our target.)
        java.util.function.Predicate<Flights.Flight> bound = f -> Math.hypot(f.target().x - aim.x, f.target().z - aim.z) < 2.01;
        helper.assertTrue(be.launch(level, target, null), "it counts down");
        helper.assertTrue(be.raised() && be.phase() == CruiseLauncherBlockEntity.Phase.COUNTDOWN && be.countdown() == CruiseLauncherBlockEntity.COUNTDOWN,
                "the boxes rise, 10 s: " + be.countdown());
        be.skipCountdownForTest();
        List<Flights.Flight> ours = new ArrayList<>();
        helper.runAfterDelay(5, () -> {
            var now = Flights.of(level).all().stream().filter(bound).toList();
            helper.assertTrue(now.size() == 1, "the first missile is lit: " + now.size());
            Flights.Flight f = now.getFirst();
            ours.add(f);
            helper.assertTrue(f.cruise(), "a cruise flight");
            helper.assertTrue(f.position().distanceTo(be.tailPoint(CruiseLauncherBlockEntity.SLOT_A)) < 1e-6, "lit at its box's tail: " + f.position());
            helper.assertTrue(Math.abs(Math.cos(f.heading()) + 1) < 1e-4, "pointing out along the launcher's front (north): " + f.heading());
            helper.assertTrue(be.inventory().getItem(CruiseLauncherBlockEntity.SLOT_A).isEmpty()
                    && !be.inventory().getItem(CruiseLauncherBlockEntity.SLOT_B).isEmpty(), "box A's missile gone, B's waiting");
            helper.assertTrue(be.energy().stored() == CruiseLauncherBlockEntity.ENERGY_CAPACITY - CruiseLauncherBlockEntity.LAUNCH_COST,
                    "60k FE used: " + be.energy().stored());
        });
        helper.runAfterDelay(5 + CruiseLauncherBlockEntity.BETWEEN, () -> {
            var now = Flights.of(level).all().stream().filter(f -> bound.test(f) && !ours.contains(f)).toList();
            helper.assertTrue(now.size() == 1, "the second is lit 3 s later: " + now.size());
            Flights.Flight f = now.getFirst();
            ours.add(f);
            helper.assertTrue(f.position().distanceTo(be.tailPoint(CruiseLauncherBlockEntity.SLOT_B)) < 1e-6, "lit at box B's tail: " + f.position());
            helper.assertTrue(be.loaded() == 0 && be.raised(), "both gone, the boxes still up");
            helper.assertTrue(ours.size() == 2 && ours.get(0) != ours.get(1), "two missiles at the target (GPS)");
        });
        helper.runAfterDelay(5 + 2 * CruiseLauncherBlockEntity.BETWEEN + 10, () -> helper.assertTrue(
                be.phase() == CruiseLauncherBlockEntity.Phase.LOWERING && !be.raised(), "lowering the boxes: " + be.phase()));
        helper.runAfterDelay(5 + 2 * CruiseLauncherBlockEntity.BETWEEN + 85, () -> {
            helper.assertTrue(be.phase() == CruiseLauncherBlockEntity.Phase.IDLE && !be.raised(), "idle again: " + be.phase());
            for (Flights.Flight f : ours) {
                Flights.of(level).cancelForTest(level, f);
            }
            helper.succeed();
        });
    }

    /**
     * A cruise missile's flight, followed tick by tick: it burns in its box, then climbs at 45 degrees along its heading on
     * its booster and drops it; then it turns for the target and flies at
     * its cruise speed, never near the ground, pops up short of the target and comes down on it.
     */
    private static void flight(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 pad = helper.absoluteVec(new Vec3(8, 3, 8));
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(pad.x), (int) Math.floor(pad.z));
        Vec3 target = new Vec3(pad.x + 180, ground, pad.z + 70);
        MissileSpec spec = MissileItem.spec(he());
        Flights.Flight f = Flights.launchCruise(level, pad, Math.PI / 2, target, spec, null);   // heading east
        int ignition = Flights.CruisePlan.IGNITION, boost = Flights.CruisePlan.BOOST_TICKS;
        List<Vec3> path = new ArrayList<>();
        path.add(f.position());
        while (f.ticksToImpact() > 0) {
            f.skipForTest(1);
            path.add(f.position());
            int age = path.size() - 1;
            if (age == ignition + boost - 1) {
                helper.assertTrue(f.dropped() == 0, "still on its booster at the end of its boost");
            } else if (age == ignition + boost) {
                helper.assertTrue(f.dropped() == 1, "its booster dropped after it");
            }
        }
        Flights.of(level).cancelForTest(level, f);
        for (int age = 0; age < ignition; age++) {
            helper.assertTrue(path.get(age).equals(pad), "burning in its box at " + age + ": " + path.get(age));
        }
        for (int age = ignition; age <= ignition + boost; age++) {
            Vec3 p = path.get(age);
            helper.assertTrue(Math.abs(p.z - pad.z) < 1e-6 && Math.abs((p.y - pad.y) - (p.x - pad.x)) < 1e-6 && p.x >= pad.x,
                    "climbing out at 45 degrees along its heading at " + age + ": " + p);
        }
        double length = 0;
        int popAt = -1;
        for (int age = ignition + boost + 1; age < path.size(); age++) {
            Vec3 a = path.get(age - 1), b = path.get(age);
            double step = Math.hypot(b.x - a.x, b.z - a.z);
            length += step;
            helper.assertTrue(step < Flights.CruisePlan.SPEED + 1e-6, "no faster than its cruise speed: " + step + " at " + age);
            if (age < path.size() - 1) {                       // (round its turn a tick's chord is a hair short of its arc)
                helper.assertTrue(step > Flights.CruisePlan.SPEED - 0.01, "at its cruise speed: " + step + " at " + age);
            }
            double left = Math.hypot(target.x - b.x, target.z - b.z);
            if (left > Flights.CruisePlan.POP + 2) {
                helper.assertTrue(b.y >= ground + 20, "well over the ground while cruising: " + (b.y - ground) + " at " + age);
            } else if (popAt < 0) {
                popAt = age;
            }
        }
        double top = path.subList(popAt, path.size()).stream().mapToDouble(Vec3::y).max().orElse(0);
        // (Sampled a tick apart, 1.6 blocks, the highest sample may fall a little short of the pop-up's top.)
        helper.assertTrue(top >= ground + Flights.CruisePlan.POP_HEIGHT - 2, "it pops up short of the target: " + (top - ground));
        Vec3 end = path.getLast();
        helper.assertTrue(end.distanceTo(target) < 1e-6, "it comes down on the target: " + end + " vs " + target);
        helper.assertTrue(length > 150 && length < 260, "a sensible path's length: " + length);
        helper.succeed();
    }

    /** The Light assembler builds an HE cruise missile from a Cruise Engine: its own look, its name, its range. */
    private static void assembles(GameTestHelper helper) {
        MissileAssemblerBlockEntity a = AssemblerTests.assembler(helper, AssemblerClass.LIGHT, true);
        a.energy().generate(AssemblerClass.LIGHT.capacity);
        helper.assertTrue(MissileAssembly.motorsFor(SizeClass.S).contains(MissileParts.Motor.CRUISE)
                && MissileAssembly.motorsFor(SizeClass.M).contains(MissileParts.Motor.CRUISE)
                && !MissileAssembly.motorsFor(SizeClass.L).contains(MissileParts.Motor.CRUISE)
                && !MissileAssembly.rocketsFor(SizeClass.S).contains(MissileParts.Motor.CRUISE), "the Cruise Engine fits Light and Medium bodies");
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_WARHEAD, new ItemStack(GZOrdnance.HE_WARHEAD.item().get()));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_BODY, new ItemStack(GZAssembly.BODIES.get(SizeClass.S.ordinal()).get()));
        a.inventory().setItem(MissileAssemblerBlockEntity.SLOT_MOTOR, new ItemStack(GZAssembly.CRUISE_ENGINE.get()));
        helper.assertTrue(a.startAssembly(), "assembly starts: " + a.problem());
        a.skipToEndForTest();
        helper.succeedWhen(() -> {
            ItemStack out = a.inventory().getItem(MissileAssemblerBlockEntity.SLOT_OUTPUT);
            MissileSpec s = MissileItem.spec(out);
            helper.assertTrue(s != null && s.cruise() && s.motor() == MissileParts.Motor.CRUISE, "a cruise missile: " + s);
            helper.assertTrue("he_warhead_cruise".equals(s.look()), "its own look: " + s.look());
            helper.assertTrue(s.range() == MissileParts.Motor.CRUISE.range, "1,500 blocks: " + s.range());
            helper.assertTrue(out.getHoverName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("groundzero.missile.name.he_warhead_cruise"), "its name (HE Cruise Missile): " + out.getHoverName());
            var shown = a.shownMissile();
            helper.assertTrue(shown != null && "he_warhead_cruise".equals(shown.warhead()), "the cruise missile on show: " + shown);
        });
    }
}
