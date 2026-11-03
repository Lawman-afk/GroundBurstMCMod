package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.content.launch.CruiseLauncherBlockEntity;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.Launches;
import dev.groundburst.content.machine.Frame;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.assembly.SizeClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Launchers carried about by another mod (DESIGN.md §17.16): one in a moving, turned frame (as a Create contraption or a
 * Sable ship carries it) launches from where it is in the world, the way it points there, and its network and computers
 * find it there; one another mod sets down somewhere else keeps its place on its network.
 */
final class CarryTests {
    private CarryTests() {}

    static void register() {
        GBGameTests.testOpenSky("carried_cruise_launcher_fires_from_where_it_is", GBGameTests.FLOOR_16, 40, CarryTests::carried);
        GBGameTests.testOpenSky("carried_launch_silo_lifts_off_where_it_is", GBGameTests.FLOOR_16, 40, CarryTests::carriedSilo);
        GBGameTests.test("moved_launcher_keeps_its_network", GBGameTests.FLOOR_16, 40, CarryTests::moved);
        GBGameTests.test("pipe_set_down_keeps_its_pull_side", GBGameTests.FLOOR_16, 20, CarryTests::pipeKeepsPull);
        GBGameTests.test("pipe_turned_turns_its_sides", GBGameTests.FLOOR_16, 5, CarryTests::pipeTurns);
        GBGameTests.test("machine_emptied_before_a_move_drops_nothing", GBGameTests.FLOOR_16, 20, CarryTests::clearedDropsNothing);
    }

    /**
     * A frame turned a quarter clockwise (seen from above) about {@code pivot} and moved by {@code shift}: north becomes
     * east. What a contraption on a bearing or a ship that has turned does to the blocks it carries.
     */
    record Turned(Vec3 pivot, Vec3 shift) implements Frame {
        @Override
        public Vec3 toWorld(Vec3 local) {
            Vec3 d = local.subtract(this.pivot);
            return new Vec3(-d.z, d.y, d.x).add(this.pivot).add(this.shift);
        }

        @Override
        public Vec3 directionToWorld(Vec3 local) {
            return new Vec3(-local.z, local.y, local.x);
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            Vec3 d = world.subtract(this.shift).subtract(this.pivot);
            return new Vec3(d.z, d.y, -d.x).add(this.pivot);
        }
    }

    /**
     * A Cruise Missile Launcher facing north, carried in a frame turned to face east and moved off: its missile lights at
     * its box's tail where that is in the world and heads east; carried, the computers find it where it is now.
     */
    private static void carried(GameTestHelper helper) {
        CruiseLauncherBlockEntity be = CruiseTests.launcher(helper);
        ServerLevel level = helper.getLevel();
        Turned frame = new Turned(Vec3.atCenterOf(be.getBlockPos()), new Vec3(9, 2, -5));
        be.inventory().setItem(CruiseLauncherBlockEntity.SLOT_A,
                CruiseTests.cruise(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.S));
        be.energy().generate(CruiseLauncherBlockEntity.ENERGY_CAPACITY);
        be.carry(frame);
        helper.assertTrue(be.worldBlockPos().equals(frame.toWorld(be.getBlockPos())), "carried, it is where its frame has it: " + be.worldBlockPos());
        BlockPos target = be.worldBlockPos().offset(420, 0, 30);
        Vec3 aim = Vec3.atBottomCenterOf(target);
        java.util.function.Predicate<Flights.Flight> bound = f -> Math.hypot(f.target().x - aim.x, f.target().z - aim.z) < 2.01;
        helper.assertTrue(be.setTarget(target.getX(), target.getZ()) && be.arm(true), "aimed and armed within reach of where it is now");
        helper.assertTrue(be.launchArmed(level, null), "it counts down");
        be.skipCountdownForTest();
        helper.onEachTick(() -> {
            if (be.carried()) {
                be.carriedTick(level);                   // as its carrier ticks it (the world does not)
            }
        });
        helper.runAfterDelay(5, () -> {
            List<Flights.Flight> ours = Flights.of(level).all().stream().filter(bound).toList();
            helper.assertTrue(ours.size() == 1, "its missile is lit: " + ours.size());
            Flights.Flight f = ours.getFirst();
            Vec3 tail = frame.toWorld(be.tailPoint(CruiseLauncherBlockEntity.SLOT_A));
            helper.assertTrue(f.position().distanceTo(tail) < 1e-6, "lit at its box's tail in the world: " + f.position() + " vs " + tail);
            helper.assertTrue(Math.abs(Math.sin(f.heading()) - 1) < 1e-4, "heading east, the way its boxes point now: " + f.heading());
            helper.assertTrue(Launches.site(level, be.getBlockPos()) == be, "the computers find it by its key while it is carried");
            helper.assertTrue(Launches.silosNear(level, be.worldBlockPos()).contains(be), "and near where it is now");
            for (Flights.Flight x : ours) {
                Flights.of(level).cancelForTest(level, x);
            }
            be.carry(null);
            helper.succeed();
        });
    }

    /** A Launch Silo carried off in a turned frame lifts its missile off from where its pad is now, on its stored power. */
    private static void carriedSilo(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, new BlockPos(5, 1, 3));
        silo.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        silo.energy().generate(LaunchSiloBlockEntity.LAUNCH_COST);
        Turned frame = new Turned(Vec3.atCenterOf(silo.getBlockPos()), new Vec3(-6, 3, 11));
        silo.carry(frame);
        BlockPos target = silo.worldBlockPos().offset(-300, 0, 120);
        Vec3 aim = Vec3.atBottomCenterOf(target);
        java.util.function.Predicate<Flights.Flight> bound = f -> Math.hypot(f.target().x - aim.x, f.target().z - aim.z) < 2.01;
        helper.assertTrue(silo.launch(level, target, null), "it counts down, carried");
        silo.skipCountdownForTest();
        helper.onEachTick(() -> {
            if (silo.carried()) {
                silo.carriedTick(level);
            }
        });
        helper.runAfterDelay(3, () -> {
            List<Flights.Flight> ours = Flights.of(level).all().stream().filter(bound).toList();
            helper.assertTrue(ours.size() == 1, "its missile is lit: " + ours.size());
            Vec3 pad = frame.toWorld(Vec3.atBottomCenterOf(silo.padCentre()));
            helper.assertTrue(ours.getFirst().position().distanceTo(pad) < 1e-6, "on its pad where that is in the world: "
                    + ours.getFirst().position() + " vs " + pad);
            helper.assertTrue(silo.energy().stored() == 0, "on the power it carried: " + silo.energy().stored());
            Flights.of(level).cancelForTest(level, ours.getFirst());
            silo.carry(null);
            helper.succeed();
        });
    }

    /**
     * An item pipe pulling from a chest, set down by something that moved them both (as Create does: block by block, a
     * mover's placement, the pipe before the chest): it still pulls once the chest is down.
     */
    private static void pipeKeepsPull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipe = helper.absolutePos(new BlockPos(4, 1, 4)), chest = pipe.east();
        BlockState pulling = dev.groundburst.content.pipe.GBPipes.ITEM_PIPE.block().get().defaultBlockState()
                .setValue(dev.groundburst.content.pipe.PipeBlock.SIDES.get(Direction.EAST), dev.groundburst.content.pipe.PipeBlock.Mode.PULL);
        int moved = net.minecraft.world.level.block.Block.UPDATE_ALL | net.minecraft.world.level.block.Block.UPDATE_MOVE_BY_PISTON;
        level.setBlock(pipe, pulling, moved);
        helper.assertTrue(dev.groundburst.content.pipe.PipeBlock.mode(level.getBlockState(pipe), Direction.EAST) == dev.groundburst.content.pipe.PipeBlock.Mode.PULL,
                "set down before the chest, it keeps its pull side: " + level.getBlockState(pipe));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), moved);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(dev.groundburst.content.pipe.PipeBlock.mode(level.getBlockState(pipe), Direction.EAST) == dev.groundburst.content.pipe.PipeBlock.Mode.PULL,
                    "and once the chest is down: " + level.getBlockState(pipe));
            helper.succeed();
        });
    }

    /** A pipe turned a quarter (a contraption set down turned): its pull and switched-off sides turn with it; mirrored, they swap. */
    private static void pipeTurns(GameTestHelper helper) {
        var sides = dev.groundburst.content.pipe.PipeBlock.SIDES;
        BlockState state = dev.groundburst.content.pipe.GBPipes.ITEM_PIPE.block().get().defaultBlockState()
                .setValue(sides.get(Direction.EAST), dev.groundburst.content.pipe.PipeBlock.Mode.PULL)
                .setValue(sides.get(Direction.NORTH), dev.groundburst.content.pipe.PipeBlock.Mode.DISABLED)
                .setValue(sides.get(Direction.UP), dev.groundburst.content.pipe.PipeBlock.Mode.NORMAL);
        BlockState turned = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        helper.assertTrue(turned.getValue(sides.get(Direction.SOUTH)) == dev.groundburst.content.pipe.PipeBlock.Mode.PULL
                && turned.getValue(sides.get(Direction.EAST)) == dev.groundburst.content.pipe.PipeBlock.Mode.DISABLED
                && turned.getValue(sides.get(Direction.UP)) == dev.groundburst.content.pipe.PipeBlock.Mode.NORMAL
                && turned.getValue(sides.get(Direction.NORTH)) == dev.groundburst.content.pipe.PipeBlock.Mode.NONE, "turned a quarter: " + turned);
        BlockState mirrored = state.mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK);
        helper.assertTrue(mirrored.getValue(sides.get(Direction.WEST)) == dev.groundburst.content.pipe.PipeBlock.Mode.PULL
                && mirrored.getValue(sides.get(Direction.NORTH)) == dev.groundburst.content.pipe.PipeBlock.Mode.DISABLED, "mirrored: " + mirrored);
        helper.succeed();
    }

    /**
     * A loaded machine emptied before its block goes without breaking (Sable does this, after saving it, as it takes a ship
     * aboard): nothing drops, so nothing is had twice.
     */
    private static void clearedDropsNothing(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, new BlockPos(5, 1, 3));
        silo.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        BlockPos at = silo.getBlockPos();
        ((net.minecraft.world.Clearable) silo).clearContent();            // as Sable does (Clearable.tryClear on 1.21.1)
        level.setBlock(at, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(3));
        helper.assertTrue(dropped.isEmpty(), "nothing dropped: " + dropped);
        helper.succeed();
    }

    /**
     * A Launch Silo on a network, picked up and set down six blocks south (as a Create contraption does: its block entity
     * comes back with what it saved): it stays on its network at its new place, formed again.
     */
    private static void moved(GameTestHelper helper) {
        LaunchTests.floor(helper);
        ServerLevel level = helper.getLevel();
        LaunchSiloBlockEntity silo = LaunchTests.silo(helper, new BlockPos(5, 1, 2));
        helper.assertTrue(silo.isFormed(), "formed where it was built");
        BlockPos from = silo.getBlockPos();
        LaunchNetwork nets = LaunchNetwork.of(level);
        long id = nets.create(level, helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(nets.link(level, id, silo) == LaunchNetwork.LinkResult.LINKED, "linked");
        helper.runAfterDelay(2, () -> {
            // Picked up: what it saved, then the whole structure taken away.
            CompoundTag saved = silo.saveWithFullMetadata(level.registryAccess());
            List<MultiblockDefinition.Cell> cells = GBLaunch.SILO.cells(from, Direction.NORTH);
            for (MultiblockDefinition.Cell cell : cells) {
                level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            // Set down six blocks south: the parts, then the controller with what it saved.
            BlockPos to = from.south(6);
            for (MultiblockDefinition.Cell cell : GBLaunch.SILO.cells(to, Direction.NORTH)) {
                if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                    level.setBlockAndUpdate(cell.pos(), GBLaunch.SILO.legend().get(cell.key()).preview().get());
                }
            }
            level.setBlockAndUpdate(to, GBLaunch.SILO_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH)
                    .setValue(MultiblockPartBlock.FORMED, true));
            LaunchSiloBlockEntity there = (LaunchSiloBlockEntity) level.getBlockEntity(to);
            helper.assertTrue(there != null, "a silo controller where it was set down");
            saved.putInt("x", to.getX());
            saved.putInt("y", to.getY());
            saved.putInt("z", to.getZ());
            there.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(nets.networkOf(from) == null, "nothing on the network where it stood");
                LaunchNetwork.Network n = nets.networkOf(to);
                helper.assertTrue(n != null && n.id == id, "on its network at its new place");
                LaunchNetwork.Member m = n.member(to);
                helper.assertTrue(m != null && m.role == LaunchNetwork.Role.LAUNCHER && m.at().equals(to), "a launcher member there: " + m);
                helper.assertTrue(there.isFormed(), "formed again at its new place");
                helper.succeed();
            });
        });
    }
}
