package dev.groundburst.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.groundburst.blast.BlastProfile;
import dev.groundburst.blast.Blasts;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.naval.DepthCharge;
import dev.groundburst.content.naval.DepthChargeRackBlockEntity;
import dev.groundburst.content.naval.GBNaval;
import dev.groundburst.content.naval.SeaMine;
import dev.groundburst.content.pipe.GBPipes;
import dev.groundburst.content.pipe.PipeBlock;
import dev.groundburst.registry.GBDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN.md §14.6: sea mines (laying, riding their chains, what sets them off, chains parted), depth charges and the Depth
 * Charge Rack (loaded by hopper, rolling a charge off on a pulse and a pattern while powered, setting its depth).
 */
final class NavalTests {
    private NavalTests() {}

    static void register() {
        GBGameTests.test("moored_mine_rises_to_its_depth", GBGameTests.FLOOR_16, 240, NavalTests::moored);
        GBGameTests.test("boat_sets_off_a_sea_mine", GBGameTests.FLOOR_16, 300, NavalTests::boat);
        GBGameTests.test("fish_slip_past_a_sea_mine", GBGameTests.FLOOR_16, 120, NavalTests::fish);
        GBGameTests.test("safe_sea_mine_is_taken_up", GBGameTests.FLOOR_16, 120, NavalTests::takenUp);
        GBGameTests.test("blast_parts_a_mine_chain", GBGameTests.FLOOR_16, 320, NavalTests::parted);
        GBGameTests.test("depth_charge_fires_at_its_depth", GBGameTests.FLOOR_16, 200, NavalTests::depth);
        GBGameTests.test("depth_charge_fires_on_the_bottom", GBGameTests.FLOOR_16, 200, NavalTests::bottom);
        GBGameTests.test("depth_charge_lies_on_dry_ground", GBGameTests.FLOOR_16, 100, NavalTests::dry);
        GBGameTests.test("dispenser_lays_a_mine", GBGameTests.FLOOR_16, 60, NavalTests::minelayer);
        GBGameTests.test("dispenser_rolls_out_a_depth_charge", GBGameTests.FLOOR_16, 60, NavalTests::rack);
        GBGameTests.test("blasts_break_boats", GBGameTests.FLOOR_16, 40, NavalTests::boats);
        GBGameTests.test("depth_charge_rack_loads_by_hopper", GBGameTests.FLOOR_16, 120, NavalTests::rackHopper);
        GBGameTests.test("depth_charge_rack_loads_by_pipe", GBGameTests.FLOOR_16, 200, NavalTests::rackPipe);
        GBGameTests.test("depth_charge_rack_rolls_one_off_on_a_pulse", GBGameTests.FLOOR_16, 60, NavalTests::rackPulse);
        GBGameTests.test("depth_charge_rack_pattern_while_powered", GBGameTests.FLOOR_16, 200, NavalTests::rackPattern);
        GBGameTests.test("depth_charge_rack_sets_its_depth", GBGameTests.FLOOR_16, 60, NavalTests::rackDepth);
        GBGameTests.test("depth_charge_rack_unformed_does_nothing", GBGameTests.FLOOR_16, 60, NavalTests::rackUnformed);
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(p, block);
        }
    }

    /** A stone basin with water from y 4 to 13 inside its walls (x and z 2 to 13); returns the water's surface (absolute). */
    private static double basin(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 14, 13, 14, Blocks.STONE);
        fill(helper, 2, 4, 2, 13, 13, 13, Blocks.WATER);
        return helper.absolutePos(new BlockPos(0, 13, 0)).getY() + 8 / 9.0;
    }

    private static SeaMine lay(GameTestHelper helper, Vec3 at, boolean moored, int depth) {
        return SeaMine.lay(helper.getLevel(), helper.absoluteVec(at), moored, depth, null);
    }

    private static List<Entity> around(GameTestHelper helper, Class<? extends Entity> type) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(16, 16, 16);
        return List.copyOf(helper.getLevel().getEntitiesOfClass(type, area));
    }

    private static boolean water(GameTestHelper helper, Vec3 absolute) {
        return helper.getLevel().getFluidState(BlockPos.containing(absolute)).is(net.minecraft.tags.FluidTags.WATER);
    }

    private static BlastProfile profile(GameTestHelper helper, String id) {
        return helper.getLevel().registryAccess().lookupOrThrow(BlastProfile.REGISTRY).getOrThrow(Blasts.profile(id)).value();
    }

    /** Laid on the surface, a moored mine sinks with its sinker to the bottom and rises on its chain to its depth. */
    private static void moored(GameTestHelper helper) {
        double surface = basin(helper);
        SeaMine m = lay(helper, new Vec3(8, 13.5, 8), true, 2);
        double floor = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
        helper.succeedWhen(() -> {
            helper.assertTrue(m.stage() == SeaMine.Stage.MOORED, "moored, not " + m.stage());
            helper.assertTrue(Math.abs(m.anchor().y - floor) < 0.01, "its sinker on the bottom (" + m.anchor().y + ", floor " + floor + ")");
            double top = m.centre().y + SeaMine.TOP;
            helper.assertTrue(Math.abs(surface - 2 - top) < 0.3, "its top 2 under the surface (" + top + ", surface " + surface + ")");
            helper.assertTrue(m.chain() > 4, "on a long chain (" + m.chain() + ")");
            m.discard();
        });
    }

    /** A boat touching a live mine sets it off: the boat is broken, and the sea round the burst is still the sea. */
    private static void boat(GameTestHelper helper) {
        basin(helper);
        SeaMine m = lay(helper, new Vec3(8, 13.5, 8), true, 0);
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(m.stage() == SeaMine.Stage.MOORED && m.isAlive(), "settled and waiting");
            m.dissolvePlugForTest();
            Vec3 at = m.centre();
            Entity boat = helper.spawn(EntityType.OAK_BOAT, helper.relativeVec(at.add(0, 0.5, 0)));
            helper.succeedWhen(() -> {
                helper.assertTrue(!m.isAlive(), "the mine has gone off");
                helper.assertTrue(!boat.isAlive(), "the boat is broken");
                for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(at).offset(-2, -2, -2), BlockPos.containing(at).offset(2, -1, 2))) {
                    helper.assertTrue(water(helper, Vec3.atCenterOf(p)), "still water at " + p);
                }
            });
        });
    }

    /** The sea's own creatures slip past a mine's horns. */
    private static void fish(GameTestHelper helper) {
        basin(helper);
        SeaMine m = lay(helper, new Vec3(8, 9, 8), false, 0);
        m.dissolvePlugForTest();
        helper.runAfterDelay(110, () -> {
            m.dissolvePlugForTest();
            helper.spawn(EntityType.COD, helper.relativeVec(m.centre()));
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(m.isAlive(), "a fish does not set it off");
                m.discard();
                helper.succeed();
            });
        });
    }

    /** Used, a mine is made safe; punched while safe, it is taken up as its item. */
    private static void takenUp(GameTestHelper helper) {
        basin(helper);
        SeaMine m = lay(helper, new Vec3(8, 13.5, 8), false, 0);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        m.interact(player, InteractionHand.MAIN_HAND, m.centre());
        helper.assertTrue(!m.armed(), "made safe");
        m.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1);
        helper.assertTrue(!m.isAlive(), "taken up");
        helper.succeedWhen(() -> helper.assertTrue(around(helper, ItemEntity.class).stream()
                .anyMatch(e -> ((ItemEntity) e).getItem().is(GBNaval.DRIFTING_MINE.get())), "dropped as its item"));
    }

    /** A blast a little way off parts a moored mine's chain: the mine bobs up to the surface and drifts there awash. */
    private static void parted(GameTestHelper helper) {
        double surface = basin(helper);
        SeaMine m = lay(helper, new Vec3(8, 13.5, 8), true, 5);
        helper.runAfterDelay(170, () -> {
            helper.assertTrue(m.stage() == SeaMine.Stage.MOORED, "moored, not " + m.stage());
            m.shocked(helper.getLevel(), m.centre().add(10, 0, 0), 10, profile(helper, "depth_charge"));
            helper.assertTrue(m.stage() == SeaMine.Stage.ADRIFT, "its chain parted");
            helper.succeedWhen(() -> {
                helper.assertTrue(Math.abs(m.centre().y - (surface - 0.1)) < 0.2, "awash at the surface (" + m.centre().y + ", surface " + surface + ")");
                m.discard();
            });
        });
    }

    private static DepthCharge charge(GameTestHelper helper, Vec3 at, int depth) {
        ItemStack stack = new ItemStack(GBNaval.DEPTH_CHARGE.get());
        stack.set(GBDataComponents.NAVAL_DEPTH.get(), depth);
        return DepthCharge.release(helper.getLevel(), helper.absoluteVec(at), Vec3.ZERO, stack, null);
    }

    /** Dropped into deep water, a depth charge sinks and fires at its depth; the sea round the burst stays. */
    private static void depth(GameTestHelper helper) {
        double surface = basin(helper);
        DepthCharge d = charge(helper, new Vec3(8.5, 13.6, 8.5), 5);
        double[] last = {0};
        helper.onEachTick(() -> {
            if (d.isAlive()) {
                last[0] = d.getY() + DepthCharge.MIDDLE;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!d.isAlive(), "it has fired");
            double depthNow = surface - last[0];
            helper.assertTrue(Math.abs(depthNow - 5) < 0.35, "at 5 under the surface (" + depthNow + ")");
            BlockPos column = helper.absolutePos(new BlockPos(8, 0, 8));
            Vec3 burst = new Vec3(column.getX() + 0.5, last[0], column.getZ() + 0.5);
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(burst).offset(-2, -2, -2), BlockPos.containing(burst).offset(2, 2, 2))) {
                helper.assertTrue(water(helper, Vec3.atCenterOf(p)), "still water at " + p);
            }
        });
    }

    /** Set deeper than the water, it fires on the bottom: the bottom is holed, and the hole is full of water. */
    private static void bottom(GameTestHelper helper) {
        basin(helper);
        DepthCharge d = charge(helper, new Vec3(8.5, 13.6, 8.5), 24);
        helper.succeedWhen(() -> {
            helper.assertTrue(!d.isAlive(), "it has fired");
            helper.assertTrue(water(helper, helper.absoluteVec(new Vec3(8.5, 3.5, 8.5))), "the bottom under it holed and filled with water");
        });
    }

    /** Out of water a depth charge never fires: it comes to rest on the ground as its item. */
    private static void dry(GameTestHelper helper) {
        DepthCharge d = charge(helper, new Vec3(8.5, 4, 8.5), 5);
        helper.succeedWhen(() -> {
            helper.assertTrue(!d.isAlive(), "no longer in flight");
            helper.assertTrue(around(helper, ItemEntity.class).stream().anyMatch(e -> ((ItemEntity) e).getItem().is(GBNaval.DEPTH_CHARGE.get())),
                    "lying there as its item");
            helper.assertBlockPresent(Blocks.SMOOTH_STONE, 8, 0, 8);
        });
    }

    private static void dispense(GameTestHelper helper, BlockPos at, Direction facing, ItemStack stack) {
        helper.setBlock(at, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing));
        if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof DispenserBlockEntity dispenser) {
            dispenser.setItem(0, stack);
        }
        helper.setBlock(at.above(), Blocks.REDSTONE_BLOCK);
    }

    /** A dispenser facing down onto the water lays its mine there. */
    private static void minelayer(GameTestHelper helper) {
        basin(helper);
        dispense(helper, new BlockPos(8, 14, 8), Direction.DOWN, new ItemStack(GBNaval.MOORED_MINE.get()));
        helper.succeedWhen(() -> {
            List<Entity> mines = around(helper, SeaMine.class);
            helper.assertTrue(mines.size() == 1, "one mine laid (" + mines.size() + ")");
            mines.forEach(Entity::discard);
        });
    }

    /** A dispenser rolls a depth charge out of its front. */
    private static void rack(GameTestHelper helper) {
        basin(helper);
        dispense(helper, new BlockPos(8, 14, 8), Direction.DOWN, new ItemStack(GBNaval.DEPTH_CHARGE.get()));
        helper.succeedWhen(() -> {
            List<Entity> charges = around(helper, DepthCharge.class);
            helper.assertTrue(charges.size() == 1, "one depth charge let go (" + charges.size() + ")");
            charges.forEach(Entity::discard);
        });
    }

    /**
     * A Depth Charge Rack on a stone shelf at the basin's edge, built facing north: its release end at (8, 14, 5) over the
     * water, its rails behind it and its Item Port at (8, 14, 8); {@code charges} put on it.
     */
    private static DepthChargeRackBlockEntity sternRack(GameTestHelper helper, int charges) {
        basin(helper);
        fill(helper, 7, 13, 5, 9, 13, 9, Blocks.STONE);
        BlockPos at = new BlockPos(8, 14, 5);
        helper.setBlock(at, GBNaval.RACK.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : GBNaval.RACK_STRUCTURE.cells(helper.absolutePos(at), Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER) {
                helper.getLevel().setBlockAndUpdate(cell.pos(), GBNaval.RACK_STRUCTURE.legend().get(cell.key()).preview().get());
            }
        }
        DepthChargeRackBlockEntity rack = helper.getBlockEntity(at, DepthChargeRackBlockEntity.class);
        rack.revalidate();
        helper.assertTrue(rack.isFormed(), "the rack formed");
        for (int i = 0; i < charges; i++) {
            rack.inventory().setItem(i, new ItemStack(GBNaval.DEPTH_CHARGE.get()));
        }
        return rack;
    }

    /** A hopper over the rack's Item Port loads it: the charges roll down to the trap; a comparator reads how many. */
    private static void rackHopper(GameTestHelper helper) {
        DepthChargeRackBlockEntity rack = sternRack(helper, 0);
        BlockPos hopper = new BlockPos(8, 15, 8);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        if (helper.getLevel().getBlockEntity(helper.absolutePos(hopper)) instanceof HopperBlockEntity h) {
            h.setItem(0, new ItemStack(GBNaval.DEPTH_CHARGE.get(), 4));
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(rack.loaded() == 4, "four loaded (" + rack.loaded() + ")");
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(!rack.inventory().getItem(i).isEmpty(), "rolled down to the trap (place " + i + ")");
            }
            helper.assertTrue(rack.comparatorSignal() == 10, "a comparator reads 10 (" + rack.comparatorSignal() + ")");
        });
    }

    /** An item pipe from a chest over the rack's Item Port loads it: five charges piped in, rolled down to the trap. */
    private static void rackPipe(GameTestHelper helper) {
        DepthChargeRackBlockEntity rack = sternRack(helper, 0);
        BlockPos pipe = new BlockPos(8, 15, 8), chest = new BlockPos(8, 16, 8);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(pipe, GBPipes.ITEM_PIPE.block().get());
        BlockPos at = helper.absolutePos(pipe);
        BlockState state = helper.getLevel().getBlockState(at);
        helper.assertTrue(PipeBlock.mode(state, Direction.UP) == PipeBlock.Mode.NORMAL && PipeBlock.mode(state, Direction.DOWN) == PipeBlock.Mode.NORMAL,
                "the pipe joins the chest over it and the Item Port under it");
        BlockState pulling = state.setValue(PipeBlock.SIDES.get(Direction.UP), PipeBlock.Mode.PULL);
        helper.getLevel().setBlock(at, pulling, Block.UPDATE_ALL);
        PipeBlock.changed(helper.getLevel(), at, pulling);
        if (helper.getBlockEntity(chest, BlockEntity.class) instanceof Container c) {
            c.setItem(0, new ItemStack(GBNaval.DEPTH_CHARGE.get(), 5));
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(rack.loaded() == 5, "five piped in (" + rack.loaded() + ")");
            helper.assertTrue(!rack.inventory().getItem(0).isEmpty(), "rolled down to the trap");
        });
    }

    /** A redstone pulse beside a rail rolls the front charge off over the trap, into the sea ahead; the rest roll down. */
    private static void rackPulse(GameTestHelper helper) {
        DepthChargeRackBlockEntity rack = sternRack(helper, 3);
        BlockPos side = new BlockPos(9, 14, 6);
        helper.runAfterDelay(4, () -> helper.setBlock(side, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(6, () -> helper.setBlock(side, Blocks.AIR));
        double front = helper.absolutePos(new BlockPos(8, 14, 5)).getZ();
        helper.runAfterDelay(24, () -> {
            List<Entity> charges = around(helper, DepthCharge.class);
            helper.assertTrue(charges.size() == 1, "one charge rolled off (" + charges.size() + ")");
            helper.assertTrue(charges.getFirst().getZ() < front, "it went off the front, toward the sea");
            helper.assertTrue(rack.loaded() == 2, "two left on the rack (" + rack.loaded() + ")");
            helper.assertTrue(!rack.inventory().getItem(0).isEmpty() && !rack.inventory().getItem(1).isEmpty(), "they rolled down to the trap");
            charges.forEach(Entity::discard);
            helper.succeed();
        });
    }

    /** Held powered, the rack rolls off one charge each interval, a pattern, until it is empty. */
    private static void rackPattern(GameTestHelper helper) {
        DepthChargeRackBlockEntity rack = sternRack(helper, 3);
        helper.setBlock(new BlockPos(9, 14, 5), Blocks.REDSTONE_BLOCK);
        int[] last = {3};
        List<Long> released = new ArrayList<>();
        helper.onEachTick(() -> {
            int n = rack.loaded();
            if (n < last[0]) {
                released.add(helper.getLevel().getGameTime());
                last[0] = n;
            }
            around(helper, DepthCharge.class).forEach(Entity::discard);   // (none goes off: the other tests share the blasts' time)
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(released.size() == 3, "three rolled off (" + released.size() + ")");
            int interval = rack.interval();
            helper.assertTrue(released.get(1) - released.get(0) >= interval && released.get(2) - released.get(1) >= interval,
                    "one each interval (" + interval + " ticks): " + released);
            helper.assertTrue(released.get(2) - released.get(0) <= 2 * interval + 4, "not slower than its interval: " + released);
            around(helper, DepthCharge.class).forEach(Entity::discard);
        });
    }

    /** Set to one depth, the rack sets every charge it rolls off to it, whatever the charge's own. */
    private static void rackDepth(GameTestHelper helper) {
        DepthChargeRackBlockEntity rack = sternRack(helper, 1);
        rack.handleButton(2);
        helper.assertTrue(rack.depthSetting() == 3, "set to 3 blocks (" + rack.depthSetting() + ")");
        rack.releaseOne();
        helper.succeedWhen(() -> {
            List<Entity> charges = around(helper, DepthCharge.class);
            helper.assertTrue(charges.size() == 1, "one charge rolled off (" + charges.size() + ")");
            int depth = ((DepthCharge) charges.getFirst()).depth();
            helper.assertTrue(depth == 3, "its pistol set to 3 blocks (" + depth + ")");
            charges.forEach(Entity::discard);
        });
    }

    /** With a rail taken out the rack is no rack: a signal rolls nothing off. */
    private static void rackUnformed(GameTestHelper helper) {
        DepthChargeRackBlockEntity rack = sternRack(helper, 2);
        helper.setBlock(new BlockPos(8, 14, 7), Blocks.AIR);
        rack.revalidate();
        helper.setBlock(new BlockPos(9, 14, 5), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!rack.isFormed(), "not formed");
            helper.assertTrue(rack.loaded() == 2 && around(helper, DepthCharge.class).isEmpty(), "nothing rolled off");
            helper.succeed();
        });
    }

    /** A blast breaks a boat it reaches, as it hurts a creature. */
    private static void boats(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        Entity boat = helper.spawn(EntityType.OAK_BOAT, new Vec3(8.5, 2, 8.5));
        Blasts.detonate(helper.getLevel(), helper.absoluteVec(new Vec3(10.5, 2.5, 8.5)), Blasts.profile("test/small"), null);
        helper.succeedWhen(() -> helper.assertTrue(!boat.isAlive(), "the boat is broken"));
    }
}
