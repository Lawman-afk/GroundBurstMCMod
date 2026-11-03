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
        GBGameTests.test("moved_launcher_keeps_its_network", GBGameTests.FLOOR_16, 40, CarryTests::moved);
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
