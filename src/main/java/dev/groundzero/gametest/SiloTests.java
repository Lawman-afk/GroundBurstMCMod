package dev.groundzero.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.launch.Flights;
import dev.groundzero.content.launch.GZLaunch;
import dev.groundzero.content.launch.LaunchSiloBlockEntity;
import dev.groundzero.content.launch.MissileSiloBlockEntity;
import dev.groundzero.content.launch.SiloHatchBlock;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.multiblock.StructureSpawnerItem;
import dev.groundzero.content.strategic.StrategicAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Missile Silo (DESIGN.md §17.10, Phase 9 slice 9b): it forms dug into the plot, takes any missile but an
 * intercontinental one, its doors open and shut the hatch, it holds its launch until they are open and its missile
 * climbs straight up the middle of the shaft, and a block over the hatch stops it.
 */
final class SiloTests {
    private SiloTests() {}

    static void register() {
        // Open sky: the silo needs the air over its hatch clear (a test plot's lid would block it).
        GZGameTests.testOpenSky("missile_silo_forms_and_fires_all_but_intercontinental", GZGameTests.FLOOR_16, 20, SiloTests::forms);
        GZGameTests.testOpenSky("missile_silo_doors_open_and_shut_the_hatch", GZGameTests.FLOOR_16, 200, SiloTests::doors);
        GZGameTests.testOpenSky("missile_silo_launches_straight_up_its_shaft", GZGameTests.FLOOR_16, 200, SiloTests::launches);
        GZGameTests.testOpenSky("missile_silo_will_not_fire_through_a_blocked_hatch", GZGameTests.FLOOR_16, 20, SiloTests::blocked);
        GZGameTests.test("silo_hatch_places_and_breaks_whole", GZGameTests.FLOOR_16, 40, SiloTests::hatch);
    }

    /** The controller, in the middle of the top ring's front: the silo fills x 5-9, z 3-7, from its floor at y 1 to y 14. */
    private static final BlockPos CONTROLLER = new BlockPos(7, 14, 3);

    /** Builds the silo the way the Structure Spawner does (each 3x3's middle before its edges) and forms it. */
    static MissileSiloBlockEntity silo(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos c = helper.absolutePos(CONTROLLER);
        MultiblockDefinition def = GZLaunch.MISSILE_SILO;
        for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, c, Direction.NORTH)) {
            BlockState state = cell.key() == '.' ? Blocks.AIR.defaultBlockState() : def.legend().get(cell.key()).preview().get();
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                state = state.setValue(MachineBlock.FACING, Direction.NORTH);
            }
            level.setBlockAndUpdate(cell.pos(), state);
        }
        MissileSiloBlockEntity s = (MissileSiloBlockEntity) level.getBlockEntity(c);
        helper.assertTrue(s != null, "a missile silo controller");
        s.revalidate();
        helper.assertTrue(s.isFormed(), "formed: " + def.check(level, c, Direction.NORTH));
        return s;
    }

    /** It takes Strategic (nuclear) and ordinary missiles but not an intercontinental one; its walls stay, its top hides. */
    private static void forms(GameTestHelper helper) {
        MissileSiloBlockEntity s = silo(helper);
        var level = helper.getLevel();
        helper.assertTrue(s.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, NuclearTests.nuke()), "takes a Strategic nuclear missile");
        helper.assertTrue(s.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile()), "and an HE one");
        ItemStack icbm = StrategicAssembly.assemble(ItemStack.EMPTY, new ItemStack(GZAssembly.GPS_GUIDANCE.get()), ItemStack.EMPTY);
        helper.assertTrue(!s.inventory().canPlaceItem(LaunchSiloBlockEntity.SLOT_MISSILE, icbm), "but not an intercontinental one");
        BlockPos wall = helper.absolutePos(new BlockPos(5, 6, 5));
        helper.assertTrue(level.getBlockState(wall).is(GZLaunch.REINFORCED_CONCRETE.block().get()), "the shaft's walls stay as built");
        BlockState ring = level.getBlockState(helper.absolutePos(new BlockPos(5, 14, 3)));
        helper.assertTrue(ring.is(GZLaunch.HAZARD_CONCRETE.block().get()) && ring.getValue(MultiblockPartBlock.FORMED), "the top ring is hidden");
        BlockPos hatch = s.hatchCentre();
        BlockState h = level.getBlockState(hatch);
        helper.assertTrue(h.is(GZLaunch.SILO_HATCH.block().get()) && h.getValue(MultiblockPartBlock.FORMED) && !h.getValue(SiloHatchBlock.OPEN),
                "a formed, shut hatch: " + h);
        double top = h.getCollisionShape(level, hatch).max(Direction.Axis.Y);
        helper.assertTrue(Math.abs(top - 1.5) < 1e-6, "shut, solid up to the doors' top: " + top);
        helper.assertTrue(s.padCentre().equals(helper.absolutePos(new BlockPos(7, 2, 5))), "the pad at the bottom of the shaft: " + s.padCentre());
        helper.assertTrue(Math.abs(s.hatchCentre().getY() + 1 - (s.padCentre().getY() + 3 / 16.0) - 12.8125) < 1e-6, "its shaft's height");
        helper.succeed();
    }

    /** The DOORS button opens the doors (the hatch then lets anything through) and shuts them again. */
    private static void doors(GameTestHelper helper) {
        MissileSiloBlockEntity s = silo(helper);
        var level = helper.getLevel();
        BlockPos hatch = s.hatchCentre();
        helper.assertTrue(s.doorState() == 0, "shut to start with");
        helper.assertTrue(s.handleButton(0), "the DOORS button opens them");
        helper.runAfterDelay(MissileSiloBlockEntity.DOOR_TICKS + 5, () -> {
            helper.assertTrue(s.doorState() == 2, "open: " + s.doorState());
            BlockState h = level.getBlockState(hatch);
            helper.assertTrue(h.getValue(SiloHatchBlock.OPEN) && h.getCollisionShape(level, hatch).isEmpty(), "the hatch open: " + h);
            helper.assertTrue(level.getBlockState(hatch.offset(1, 0, 1)).getValue(SiloHatchBlock.OPEN), "all of it");
            helper.assertTrue(s.handleButton(0), "and shuts them");
            helper.runAfterDelay(MissileSiloBlockEntity.DOOR_TICKS + 5, () -> {
                BlockState shut = level.getBlockState(hatch);
                helper.assertTrue(s.doorState() == 0 && !shut.getValue(SiloHatchBlock.OPEN), "shut again: " + shut);
                helper.succeed();
            });
        });
    }

    /**
     * Loaded, powered and launched, it counts down 20 s and holds at T-1 until its doors are open; then the missile is lit
     * on its ring at the bottom of the shaft, using 250k FE, and climbs straight up the middle of the shaft until it is
     * clear of the mouth, its speed running on smoothly where it turns.
     */
    private static void launches(GameTestHelper helper) {
        MissileSiloBlockEntity s = silo(helper);
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, NuclearTests.nuke());
        s.energy().generate(MissileSiloBlockEntity.SILO_COST);
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready: " + s.readiness());
        BlockPos target = s.getBlockPos().offset(300, 0, 0);
        helper.assertTrue(s.launch(helper.getLevel(), target, null), "counts down");
        helper.assertTrue(s.countdown() == MissileSiloBlockEntity.SILO_COUNTDOWN, "20 s: " + s.countdown());
        var before = Flights.of(helper.getLevel()).all();
        s.skipCountdownForTest();                                         // to T-1: it holds there while its doors open
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(s.countdown() == 1 && s.doorState() == 1, "held at T-1, doors opening: " + s.countdown() + " / " + s.doorState());
            helper.assertTrue(!s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(), "not lit yet");
        });
        helper.runAfterDelay(MissileSiloBlockEntity.DOOR_TICKS + 12, () -> {
            helper.assertTrue(s.inventory().getItem(LaunchSiloBlockEntity.SLOT_MISSILE).isEmpty(), "lit once the doors were open");
            helper.assertTrue(s.energy().stored() == 0, "250k FE used: " + s.energy().stored());
            helper.assertTrue(s.doorState() == 2, "the doors stay open after it: " + s.doorState());
            Vec3 aim = Vec3.atBottomCenterOf(target);
            var ours = Flights.of(helper.getLevel()).all().stream().filter(f -> !before.contains(f) && f.target().distanceTo(aim) < 4).toList();
            helper.assertTrue(ours.size() == 1, "a missile in the air: " + ours.size());
            Flights.Flight f = ours.getFirst();
            Vec3 mouth = f.siloMouth();
            Vec3 pad = Vec3.atBottomCenterOf(s.padCentre()).add(0, 3 / 16.0, 0);
            helper.assertTrue(mouth != null && Math.abs(mouth.y - (s.hatchCentre().getY() + 1)) < 1e-6, "from the silo, its mouth the ground: " + mouth);
            helper.assertTrue(f.position().distanceTo(pad) < 1e-6 && f.inShaft(), "lit on its ring: " + f.position());
            // Fly it on a tick at a time (through the rest of its ignition on the ring) and watch its tail.
            List<Vec3> path = new ArrayList<>();
            path.add(f.position());
            for (int i = 0; i < 400 && f.position().y < mouth.y + 12; i++) {
                f.skipForTest(1);
                path.add(f.position());
            }
            double clear = mouth.y + Flights.Flight.CLEAR_MOUTH;
            int join = -1;
            for (int i = 1; i < path.size(); i++) {
                Vec3 p = path.get(i);
                helper.assertTrue(p.y >= path.get(i - 1).y, "climbing: " + p);
                if (p.y < clear - 1e-6) {
                    helper.assertTrue(Math.abs(p.x - pad.x) < 1e-6 && Math.abs(p.z - pad.z) < 1e-6, "straight up the middle of the shaft: " + p);
                } else if (join < 0) {
                    join = i;
                }
            }
            helper.assertTrue(join > 1 && join + 2 < path.size(), "out of the mouth within its climb: " + join + " of " + path.size());
            double in = path.get(join - 1).distanceTo(path.get(join - 2)), out = path.get(join + 1).distanceTo(path.get(join));
            helper.assertTrue(out < in * 1.6 + 0.05 && out > in * 0.6 - 0.05, "no jolt where it turns: " + in + " then " + out);
            helper.assertTrue(path.getLast().x > pad.x + 1e-3, "then it turns toward its target: " + path.getLast());
            Flights.of(helper.getLevel()).cancelForTest(helper.getLevel(), f);
            helper.succeed();
        });
    }

    /** A block over the hatch: it will not arm or launch until it is gone. */
    private static void blocked(GameTestHelper helper) {
        MissileSiloBlockEntity s = silo(helper);
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        s.energy().generate(MissileSiloBlockEntity.SILO_COST);
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.READY, "ready: " + s.readiness());
        BlockPos over = s.hatchCentre().above();
        helper.getLevel().setBlockAndUpdate(over, Blocks.STONE.defaultBlockState());
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.BLOCKED, "blocked: " + s.readiness());
        helper.assertTrue(!s.launch(helper.getLevel(), s.getBlockPos().offset(200, 0, 0), null), "will not launch");
        helper.getLevel().removeBlock(over, false);
        helper.assertTrue(s.readiness() == LaunchSiloBlockEntity.Readiness.READY, "clear again: " + s.readiness());
        helper.succeed();
    }

    /** One Silo Hatch places a 3x3 lid; lose any part of it and the whole goes, dropping one hatch. */
    private static void hatch(GameTestHelper helper) {
        var level = helper.getLevel();
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
        var block = GZLaunch.SILO_HATCH.block().get();
        BlockPos c = helper.absolutePos(new BlockPos(8, 1, 8));
        level.setBlockAndUpdate(c, block.defaultBlockState());
        block.setPlacedBy(level, c, level.getBlockState(c), null, new ItemStack(GZLaunch.SILO_HATCH.item().get()));
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 0, 1))) {
            helper.assertTrue(level.getBlockState(p).is(block), "the 3x3 is hatch at " + p);
            helper.assertTrue(!level.getBlockState(p).getCollisionShape(level, p).isEmpty(), "a solid lid at " + p);
        }
        level.removeBlock(c.offset(-1, 0, 0), false);
        helper.succeedWhen(() -> {
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 0, 1))) {
                helper.assertTrue(!level.getBlockState(p).is(block), "all of it gone");
            }
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(3));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GZLaunch.SILO_HATCH.item().get()), "one hatch dropped");
        });
    }
}
