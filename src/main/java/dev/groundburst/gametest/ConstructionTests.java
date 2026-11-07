package dev.groundburst.gametest;

import java.util.List;

import dev.groundburst.content.construction.BlastDoorBlock;
import dev.groundburst.content.construction.BlastDoorBlockEntity;
import dev.groundburst.content.construction.GBConstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

/**
 * The see-through construction blocks (DESIGN.md §22.2, after the user's look): lattice, grating and grating slabs leave
 * out the faces between them only where the other covers a face whole, so a stack of them shows its outside (drawn from
 * both sides by their models) and never two faces in one plane. The Blast Door (§22.3): it opens and shuts by a click or
 * by redstone, lays its leaf's blocks in front of its hinges as it opens and takes them away when it has shut, will not
 * swing into a wall, and goes whole (one item back) when any of its blocks is broken. The Bunker Hatch: a signal opens it,
 * its lid standing up out of the floor in a block of its own (clicked, it shuts the hatch) and the way through it clear;
 * the Pressure Door: a signal opens both its halves. The props held on or open by a redstone signal (the user's wish);
 * the Bunker Lamp lit by one; the Fire Extinguisher hung on a wall.
 */
final class ConstructionTests {
    private ConstructionTests() {}

    static void register() {
        GBGameTests.test("see_through_blocks_show_only_their_outside", GBGameTests.FLOOR_16, 20, ConstructionTests::seeThrough);
        GBGameTests.test("blast_door_swings_open_and_shuts", GBGameTests.FLOOR_16, 200, ConstructionTests::blastDoorSwings);
        GBGameTests.test("blast_door_will_not_swing_into_a_wall", GBGameTests.FLOOR_16, 40, ConstructionTests::blastDoorBlocked);
        GBGameTests.test("blast_door_follows_redstone", GBGameTests.FLOOR_16, 200, ConstructionTests::blastDoorRedstone);
        GBGameTests.test("blast_door_breaks_whole_for_one_item", GBGameTests.FLOOR_16, 40, ConstructionTests::blastDoorBreaks);
        GBGameTests.test("bunker_hatch_opens_up_out_of_the_floor", GBGameTests.FLOOR_16, 20, ConstructionTests::hatchOpens);
        GBGameTests.test("pressure_door_opens_on_a_signal", GBGameTests.FLOOR_16, 20, ConstructionTests::pressureDoorOpens);
        GBGameTests.test("filing_cabinet_holds_27_stacks", GBGameTests.FLOOR_16, 20, ConstructionTests::cabinetHolds);
        GBGameTests.test("tape_computer_stands_two_high", GBGameTests.FLOOR_16, 20, ConstructionTests::tapeTwoHigh);
        GBGameTests.test("map_table_breaks_whole_for_one_item", GBGameTests.FLOOR_16, 40, ConstructionTests::mapTableBreaks);
        GBGameTests.test("bunker_lamp_lights_on_a_signal", GBGameTests.FLOOR_16, 40, ConstructionTests::lampLights);
        GBGameTests.test("props_are_held_on_or_open_by_redstone", GBGameTests.FLOOR_16, 40, ConstructionTests::propsHeld);
        GBGameTests.test("fire_extinguisher_hangs_on_a_wall", GBGameTests.FLOOR_16, 20, ConstructionTests::extinguisherHangs);
        GBGameTests.test("ducts_join_ducts_and_a_grilles_back", GBGameTests.FLOOR_16, 20, ConstructionTests::ductsJoin);
        GBGameTests.test("wall_pipes_run_on_into_their_neighbours", GBGameTests.FLOOR_16, 20, ConstructionTests::pipesJoin);
        GBGameTests.test("valve_and_fuse_box_give_a_signal", GBGameTests.FLOOR_16, 20, ConstructionTests::switchesSignal);
        GBGameTests.test("fire_extinguisher_puts_out_fire", GBGameTests.FLOOR_16, 20, ConstructionTests::extinguisherPutsOut);
        GBGameTests.test("steel_locker_stands_two_high_and_holds_27", GBGameTests.FLOOR_16, 40, ConstructionTests::lockerHolds);
        GBGameTests.test("bunk_bed_sleeps_two_and_breaks_whole", GBGameTests.FLOOR_16, 40, ConstructionTests::bunkSleepsTwo);
        GBGameTests.test("concrete_obstacles_stand_up_as_reinforced_concrete", GBGameTests.FLOOR_16, 20, ConstructionTests::obstaclesHard);
        GBGameTests.test("drums_stand_four_to_a_block_and_drop_each", GBGameTests.FLOOR_16, 40, ConstructionTests::drumsStack);
        GBGameTests.test("ammo_crates_hold_27_stacks", GBGameTests.FLOOR_16, 20, ConstructionTests::cratesHold);
        GBGameTests.test("searchlight_switches_on_and_sweeps", GBGameTests.FLOOR_16, 80, ConstructionTests::searchlightSweeps);
    }

    // --- the yard ---------------------------------------------------------------------------------------------------------------
    private static void obstaclesHard(GameTestHelper helper) {
        float concrete = dev.groundburst.content.launch.GBLaunch.REINFORCED_CONCRETE.block().get().getExplosionResistance();
        helper.assertTrue(GBConstruction.JERSEY_BARRIER.block().get().getExplosionResistance() == concrete
                && GBConstruction.DRAGONS_TEETH.block().get().getExplosionResistance() == concrete, "barriers and teeth blast as reinforced concrete");
        helper.assertTrue(GBConstruction.CZECH_HEDGEHOG.block().get().getExplosionResistance() >= 300
                && GBConstruction.RUSTY_CZECH_HEDGEHOG.block().get().getExplosionResistance() >= 300, "a hedgehog's steel, painted or rusty, is hard to blast");
        helper.succeed();
    }

    private static void drumsStack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        var drum = GBConstruction.OIL_DRUM.block().get();
        helper.setBlock(at, drum.defaultBlockState());
        var player = dev.groundburst.port.Mock.serverPlayer(helper, net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(GBConstruction.OIL_DRUM.item().get(), 8));
        for (int i = 0; i < 4; i++) {
            helper.useBlock(at, player);
        }
        BlockPos abs = helper.absolutePos(at);
        helper.assertTrue(level.getBlockState(abs).getValue(dev.groundburst.content.construction.DrumBlock.DRUMS) == 4, "four drums, no more");
        helper.assertTrue(level.getBlockState(abs.north()).is(drum), "a fifth goes beside them, as a sea pickle does");
        helper.assertTrue(player.getMainHandItem().getCount() == 4, "four put down (" + player.getMainHandItem().getCount() + " left)");
        level.destroyBlock(abs, true);
        helper.runAfterDelay(4, () -> {
            long items = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3)).stream()
                    .filter(e -> e.getItem().is(GBConstruction.OIL_DRUM.item())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(items == 4, "all four back: " + items);
            helper.succeed();
        });
    }

    private static void cratesHold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GBConstruction.AMMO_CRATES.block().get().defaultBlockState());
        BlockPos abs = helper.absolutePos(at);
        var crates = level.getBlockEntity(abs) instanceof dev.groundburst.content.construction.AmmoCratesBlockEntity c ? c : null;
        helper.assertTrue(crates != null && crates.getContainerSize() == 27, "27 slots");
        crates.setItem(0, new ItemStack(net.minecraft.world.item.Items.ARROW, 64));
        helper.assertTrue(level.getBlockState(abs).getAnalogOutputSignal(level, abs, Direction.NORTH) > 0, "a comparator reads them");
        helper.succeed();
    }

    private static void searchlightSweeps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        var light = GBConstruction.SEARCHLIGHT.block().get();
        BlockState state = light.defaultBlockState();
        helper.setBlock(at, state);
        BlockPos abs = helper.absolutePos(at);
        light.setPlacedBy(level, abs, state, null, ItemStack.EMPTY);
        helper.assertTrue(level.getBlockState(abs.above()).is(light), "two blocks high");
        helper.useBlock(at);
        helper.assertTrue(level.getBlockState(abs).getValue(dev.groundburst.content.construction.SearchlightBlock.ACTIVE)
                && level.getBlockState(abs.above()).getValue(dev.groundburst.content.construction.SearchlightBlock.ACTIVE), "a click switches it on");
        helper.assertTrue(level.getBlockState(abs).getLightEmission() == 15, "and it shines");
        var entity = (dev.groundburst.content.construction.SearchlightBlockEntity) level.getBlockEntity(abs);
        var first = entity.forward(0);
        helper.runAfterDelay(60, () -> {
            var later = entity.forward(0);
            helper.assertTrue(first.distanceTo(later) > 0.05, "it sweeps the sky: " + first + " then " + later);
            helper.assertTrue(later.y > 0.2, "pointing up into it");
            level.destroyBlock(abs.above(), false);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(level.getBlockState(abs).isAir(), "the lower half goes with the upper");
                helper.succeed();
            });
        });
    }

    // --- the bunker fittings ----------------------------------------------------------------------------------------------------
    private static void lampLights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GBConstruction.BUNKER_LAMP.block().get().defaultBlockState());
        BlockPos abs = helper.absolutePos(at);
        helper.assertTrue(level.getBlockState(abs).getLightEmission() == 0, "dark with no signal");
        helper.useBlock(at);
        helper.assertTrue(level.getBlockState(abs).getLightEmission() == 0, "a click does nothing");
        helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        helper.assertTrue(level.getBlockState(abs).getLightEmission() == 14, "a signal lights it");
        helper.setBlock(at.east(), Blocks.AIR);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!level.getBlockState(abs).getValue(dev.groundburst.content.construction.BunkerLampBlock.LIT), "the signal gone, it goes dark");
            helper.succeed();
        });
    }

    /**
     * Each prop beside a redstone block: the containers stand open, the two-high props on (whatever their clicks chose), the
     * switches that give a signal look on but give none of their own (else they would hold themselves on); the redstone gone,
     * each as it was.
     */
    private static void propsHeld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cabinet = new BlockPos(2, 1, 3), crates = new BlockPos(5, 1, 3), locker = new BlockPos(8, 1, 3), light = new BlockPos(11, 1, 3),
                tape = new BlockPos(14, 1, 3), valve = new BlockPos(4, 2, 10), fuse = new BlockPos(10, 2, 10);
        helper.setBlock(cabinet, GBConstruction.FILING_CABINET.block().get().defaultBlockState());
        helper.setBlock(crates, GBConstruction.AMMO_CRATES.block().get().defaultBlockState());
        for (Block b : List.of(GBConstruction.STEEL_LOCKER.block().get(), GBConstruction.SEARCHLIGHT.block().get(), GBConstruction.TAPE_COMPUTER.block().get())) {
            BlockPos p = b == GBConstruction.STEEL_LOCKER.block().get() ? locker : b == GBConstruction.SEARCHLIGHT.block().get() ? light : tape;
            helper.setBlock(p, b.defaultBlockState());
            b.setPlacedBy(level, helper.absolutePos(p), b.defaultBlockState(), null, ItemStack.EMPTY);
        }
        helper.useBlock(tape);                                              // stopped by a click
        for (BlockPos p : List.of(valve, fuse)) {
            helper.setBlock(p.south(), Blocks.STONE);
        }
        helper.setBlock(valve, GBConstruction.PIPE_VALVE.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(fuse, GBConstruction.FUSE_BOX.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        var open = net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;
        var active = dev.groundburst.content.machine.MachineBlock.ACTIVE;
        var held = dev.groundburst.content.construction.PipeValveBlock.HELD;
        helper.assertTrue(!level.getBlockState(helper.absolutePos(tape)).getValue(active), "a click stops the tape computer");
        // The redstone beside each (the two-high ones' upper halves: either holds the whole).
        List<BlockPos> power = List.of(cabinet.east(), crates.east(), locker.above().east(), light.above().east(), tape.above().east(),
                valve.east(), fuse.east());
        power.forEach(p -> helper.setBlock(p, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(2, () -> {
            for (BlockPos p : List.of(cabinet, crates, locker, locker.above())) {
                helper.assertTrue(level.getBlockState(helper.absolutePos(p)).getValue(open), "held open: " + level.getBlockState(helper.absolutePos(p)));
            }
            for (BlockPos p : List.of(light, light.above(), tape, tape.above())) {
                helper.assertTrue(level.getBlockState(helper.absolutePos(p)).getValue(active), "held on: " + level.getBlockState(helper.absolutePos(p)));
            }
            helper.useBlock(light);                                         // a click while it is held: still on
            helper.assertTrue(level.getBlockState(helper.absolutePos(light)).getValue(active), "a held light stays on when clicked");
            helper.useBlock(light);
            for (BlockPos p : List.of(valve, fuse)) {
                BlockState s = level.getBlockState(helper.absolutePos(p));
                helper.assertTrue(s.getValue(held), "held on to look at: " + s);
                helper.assertTrue(level.getDirectSignal(helper.absolutePos(p), Direction.NORTH) == 0, "but no signal of its own: " + s);
            }
            power.forEach(p -> helper.setBlock(p, Blocks.AIR));
            helper.runAfterDelay(2, () -> {
                for (BlockPos p : List.of(cabinet, crates, locker, locker.above())) {
                    helper.assertTrue(!level.getBlockState(helper.absolutePos(p)).getValue(open), "shut again: " + level.getBlockState(helper.absolutePos(p)));
                }
                for (BlockPos p : List.of(light, tape)) {
                    helper.assertTrue(!level.getBlockState(helper.absolutePos(p)).getValue(active), "off again: " + level.getBlockState(helper.absolutePos(p)));
                }
                for (BlockPos p : List.of(valve, fuse)) {
                    helper.assertTrue(!level.getBlockState(helper.absolutePos(p)).getValue(held), "let go");
                }
                helper.succeed();
            });
        });
    }

    private static void extinguisherHangs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at.south(), Blocks.STONE);
        BlockState hung = GBConstruction.FIRE_EXTINGUISHER.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(dev.groundburst.content.construction.FireExtinguisherBlock.WALL, true);
        BlockPos abs = helper.absolutePos(at);
        helper.assertTrue(hung.canSurvive(level, abs), "it hangs on the wall behind it");
        helper.setBlock(at, hung);
        helper.assertTrue(level.getBlockState(abs).getShape(level, abs).bounds().maxZ > 0.99, "against the wall");
        helper.setBlock(at.south(), Blocks.AIR);
        helper.assertTrue(!level.getBlockState(abs).is(GBConstruction.FIRE_EXTINGUISHER.block().get()), "the wall gone, it falls");
        helper.succeed();
    }

    private static void ductsJoin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var duct = GBConstruction.VENTILATION_DUCT.block().get();
        BlockPos a = new BlockPos(6, 2, 8), b = a.east(), c = b.above();
        for (BlockPos p : List.of(a, b, c)) {
            helper.setBlock(p, duct.defaultBlockState());
            BlockPos abs = helper.absolutePos(p);
            level.setBlock(abs, duct.joined(level.getBlockState(abs), level, abs), 3);    // as placing joins it
        }
        helper.setBlock(a.west(), GBConstruction.VENT_GRILLE.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST));
        helper.setBlock(b.north(), GBConstruction.VENT_GRILLE.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.UP));
        BlockState sa = level.getBlockState(helper.absolutePos(a)), sb = level.getBlockState(helper.absolutePos(b));
        helper.assertTrue(sa.getValue(net.minecraft.world.level.block.PipeBlock.EAST) && sb.getValue(net.minecraft.world.level.block.PipeBlock.WEST),
                "duct joins duct");
        helper.assertTrue(sb.getValue(net.minecraft.world.level.block.PipeBlock.UP), "and the one over it");
        helper.assertTrue(sa.getValue(net.minecraft.world.level.block.PipeBlock.WEST), "a grille facing away from it, its back to the duct: joined");
        helper.assertTrue(!sb.getValue(net.minecraft.world.level.block.PipeBlock.NORTH), "a grille facing another way: not");
        helper.assertTrue(!sa.getValue(net.minecraft.world.level.block.PipeBlock.DOWN), "nor the floor");
        helper.succeed();
    }

    private static void pipesJoin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pipes = GBConstruction.WALL_PIPES.block().get();
        BlockState north = pipes.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH);
        BlockPos west = new BlockPos(6, 2, 8), mid = west.east(), east = mid.east();
        helper.setBlock(west, north);
        helper.setBlock(mid, GBConstruction.PIPE_VALVE.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(east, north.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockPos m = helper.absolutePos(mid);
        level.setBlock(m, GBConstruction.PIPE_VALVE.block().get().joined(level.getBlockState(m), level, m), 3);
        BlockState sw = level.getBlockState(helper.absolutePos(west)), sm = level.getBlockState(m);
        // Facing north, its left (as one looks at it) is east.
        helper.assertTrue(sw.getValue(dev.groundburst.content.construction.WallPipesBlock.LEFT), "the pipes run on into the valve beside them");
        helper.assertTrue(!sw.getValue(dev.groundburst.content.construction.WallPipesBlock.RIGHT), "and are capped where nothing joins them");
        helper.assertTrue(sm.getValue(dev.groundburst.content.construction.WallPipesBlock.RIGHT), "the valve runs on into the pipes");
        helper.assertTrue(!sm.getValue(dev.groundburst.content.construction.WallPipesBlock.LEFT), "but not into pipes facing another way");
        helper.succeed();
    }

    private static void switchesSignal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos valve = new BlockPos(5, 2, 8), fuse = new BlockPos(10, 2, 8);
        for (BlockPos p : List.of(valve, fuse)) {
            helper.setBlock(p.south(), Blocks.STONE);                        // the wall they hang on
            helper.setBlock(p.west(), Blocks.REDSTONE_LAMP);
        }
        helper.setBlock(valve, GBConstruction.PIPE_VALVE.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(fuse, GBConstruction.FUSE_BOX.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.assertTrue(!level.getBlockState(helper.absolutePos(valve.west())).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),
                "shut, the valve gives no signal");
        helper.useBlock(valve);
        helper.useBlock(fuse);
        helper.runAfterDelay(2, () -> {
            for (BlockPos p : List.of(valve, fuse)) {
                helper.assertTrue(level.getBlockState(helper.absolutePos(p.west())).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),
                        "turned or thrown, it lights the lamp beside it");
                helper.assertTrue(level.getDirectSignal(helper.absolutePos(p), Direction.NORTH) == 15, "and powers the wall it hangs on");
            }
            helper.succeed();
        });
    }

    private static void extinguisherPutsOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GBConstruction.FIRE_EXTINGUISHER.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        BlockPos fire = at.north(2);
        helper.setBlock(fire, Blocks.FIRE);
        helper.assertTrue(level.getBlockState(helper.absolutePos(fire)).is(Blocks.FIRE), "a fire before it");
        helper.useBlock(at);
        helper.assertTrue(level.getBlockState(helper.absolutePos(fire)).isAir(), "a burst puts it out");
        helper.succeed();
    }

    private static void lockerHolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        var block = GBConstruction.STEEL_LOCKER.block().get();
        BlockState state = block.defaultBlockState();
        helper.setBlock(at, state);
        BlockPos abs = helper.absolutePos(at);
        block.setPlacedBy(level, abs, state, null, ItemStack.EMPTY);
        BlockState upper = level.getBlockState(abs.above());
        helper.assertTrue(upper.is(block) && upper.getValue(dev.groundburst.content.construction.SteelLockerBlock.HALF) == DoubleBlockHalf.UPPER,
                "its upper half laid over it");
        var locker = level.getBlockEntity(abs) instanceof dev.groundburst.content.construction.SteelLockerBlockEntity l ? l : null;
        helper.assertTrue(locker != null && locker.getContainerSize() == 27, "27 slots in its lower half");
        locker.setItem(3, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        level.setBlockAndUpdate(abs, level.getBlockState(abs).setValue(dev.groundburst.content.construction.SteelLockerBlock.OPEN, true));
        helper.assertTrue(level.getBlockState(abs.above()).getValue(dev.groundburst.content.construction.SteelLockerBlock.OPEN),
                "its upper half's door stands open with the lower's");
        level.destroyBlock(abs.above(), true);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(level.getBlockState(abs).isAir(), "the lower half goes with the upper");
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3));
            helper.assertTrue(items.stream().filter(e -> e.getItem().is(GBConstruction.STEEL_LOCKER.item())).count() == 1, "one locker back");
            helper.assertTrue(items.stream().anyMatch(e -> e.getItem().is(net.minecraft.world.item.Items.IRON_HELMET)), "and what it held");
            helper.succeed();
        });
    }

    private static void bunkSleepsTwo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var bunk = GBConstruction.BUNK_BED.block().get();
        BlockPos at = new BlockPos(8, 1, 8);
        BlockState state = bunk.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH);
        helper.setBlock(at, state);
        BlockPos foot = helper.absolutePos(at);
        bunk.setPlacedBy(level, foot, state, null, ItemStack.EMPTY);
        BlockPos head = foot.north();
        for (BlockPos p : List.of(head, foot.above(), head.above())) {
            helper.assertTrue(level.getBlockState(p).is(bunk), "four blocks laid: " + p);
        }
        var low = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var high = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        low.startSleeping(head);
        high.startSleeping(head.above());
        helper.assertTrue(low.isSleeping() && high.isSleeping(), "a bed in each bunk");
        double lowAt = low.getY() - head.getY(), highAt = high.getY() - head.getY();     // (on its mattress: 0.725 up, 9.6 px and a lift)
        helper.assertTrue(lowAt > 0.6 && lowAt < 0.8, "the lower sleeper on its mattress: " + lowAt);
        helper.assertTrue(highAt > 1.6 && highAt < 1.8, "the upper on its own: " + highAt);
        helper.assertTrue(level.getBlockState(foot).getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                && level.getBlockState(foot.above()).getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED), "each bunk taken, foot and head");
        level.destroyBlock(foot.above(), true);
        helper.runAfterDelay(4, () -> {
            for (BlockPos p : List.of(foot, head, foot.above(), head.above())) {
                helper.assertTrue(level.getBlockState(p).isAir(), "all gone: " + p);
            }
            long items = level.getEntitiesOfClass(ItemEntity.class, new AABB(foot).inflate(4)).stream()
                    .filter(e -> e.getItem().is(GBConstruction.BUNK_BED.item())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(items == 1, "one bunk back: " + items);
            helper.succeed();
        });
    }

    // --- the command room -------------------------------------------------------------------------------------------------------
    private static void cabinetHolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GBConstruction.FILING_CABINET.block().get().defaultBlockState());
        BlockPos abs = helper.absolutePos(at);
        var cabinet = level.getBlockEntity(abs) instanceof dev.groundburst.content.construction.FilingCabinetBlockEntity c ? c : null;
        helper.assertTrue(cabinet != null && cabinet.getContainerSize() == 27, "27 slots");
        cabinet.setItem(5, new ItemStack(net.minecraft.world.item.Items.PAPER, 64));
        helper.assertTrue(cabinet.getItem(5).getCount() == 64, "it keeps what is put in it");
        helper.assertTrue(level.getBlockState(abs).getAnalogOutputSignal(level, abs, Direction.NORTH) > 0, "a comparator reads it");
        helper.succeed();
    }

    private static void tapeTwoHigh(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        var block = GBConstruction.TAPE_COMPUTER.block().get();
        BlockState state = block.defaultBlockState();
        helper.setBlock(at, state);
        BlockPos abs = helper.absolutePos(at);
        block.setPlacedBy(level, abs, state, null, ItemStack.EMPTY);
        BlockState upper = level.getBlockState(abs.above());
        helper.assertTrue(upper.is(block) && upper.getValue(dev.groundburst.content.construction.TapeComputerBlock.HALF) == DoubleBlockHalf.UPPER,
                "its upper half laid over it");
        helper.assertTrue(level.getBlockEntity(abs) instanceof dev.groundburst.content.construction.TapeComputerBlockEntity, "its lower half draws it");
        helper.assertTrue(state.getValue(dev.groundburst.content.construction.TapeComputerBlock.ACTIVE), "running as it is placed");
        level.destroyBlock(abs.above(), false);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(abs).isAir(), "the lower half goes with the upper");
            helper.succeed();
        });
    }

    private static void mapTableBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(6, 1, 8);
        var table = GBConstruction.SITUATION_MAP_TABLE.block().get();
        BlockState state = table.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH);
        helper.setBlock(at, state);
        BlockPos base = helper.absolutePos(at);
        table.setPlacedBy(level, base, state, null, ItemStack.EMPTY);
        BlockPos far = dev.groundburst.content.construction.MapTableBlock.cell(base, Direction.NORTH, 1, 1);
        helper.assertTrue(level.getBlockState(far).is(table), "laid two by two");
        level.destroyBlock(far, true);
        helper.runAfterDelay(6, () -> {
            for (int u = 0; u < 2; u++) {
                for (int w = 0; w < 2; w++) {
                    helper.assertTrue(level.getBlockState(dev.groundburst.content.construction.MapTableBlock.cell(base, Direction.NORTH, u, w)).isAir(),
                            "all gone");
                }
            }
            long items = level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4)).stream()
                    .filter(e -> e.getItem().is(GBConstruction.SITUATION_MAP_TABLE.item())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(items == 1, "one table back: " + items);
            helper.succeed();
        });
    }

    // --- the Bunker Hatch and the Pressure Door -------------------------------------------------------------------------------
    private static void hatchOpens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at, GBConstruction.BUNKER_HATCH.block().get().defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(TrapDoorBlock.HALF, Half.TOP));
        BlockPos abs = helper.absolutePos(at);
        helper.assertTrue(level.getBlockState(abs).getCollisionShape(level, abs).bounds().minY > 0.75, "shut, a floor hatch lies at its block's top");
        helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            BlockState s = level.getBlockState(abs);
            helper.assertTrue(s.getValue(TrapDoorBlock.OPEN), "a signal opens it");
            var shape = s.getCollisionShape(level, abs);
            helper.assertTrue(shape.toAabbs().stream().noneMatch(b -> b.contains(0.5, 0.9, 0.5)), "the way through it clear");
            BlockState lid = level.getBlockState(abs.above());
            helper.assertTrue(lid.is(GBConstruction.BUNKER_HATCH_LID.get()), "its lid stands up out of the floor, in a block of its own: " + lid);
            helper.assertTrue(lid.getShape(level, abs.above()).bounds().maxY > 0.8, "solid up its height");
            helper.useBlock(at.above());                                    // a click on the lid
            helper.assertTrue(!level.getBlockState(abs).getValue(TrapDoorBlock.OPEN), "shuts the hatch");
            helper.assertTrue(level.getBlockState(abs.above()).isAir(), "and the lid's block goes");
            helper.succeed();
        });
    }

    private static void pressureDoorOpens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(8, 1, 8);
        DoorBlock door = (DoorBlock) GBConstruction.PRESSURE_DOOR.block().get();
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.setBlock(at, lower);
        helper.setBlock(at.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        BlockPos abs = helper.absolutePos(at);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(abs).getValue(DoorBlock.OPEN) && level.getBlockState(abs.above()).getValue(DoorBlock.OPEN),
                    "a signal opens both halves");
            helper.assertTrue(door.type().canOpenByHand(), "a hand opens it too");
            helper.succeed();
        });
    }

    // --- the Blast Door -----------------------------------------------------------------------------------------------------
    /** A door laid as its item lays it: facing north, its hinges on the left (east), its doorway running west from them. */
    private static BlockPos blastDoor(GameTestHelper helper, BlockPos at) {
        BlastDoorBlock block = GBConstruction.BLAST_DOOR.block().get();
        BlockState state = block.defaultBlockState().setValue(BlastDoorBlock.FACING, Direction.NORTH).setValue(BlastDoorBlock.HINGE, DoorHingeSide.LEFT);
        helper.setBlock(at, state);
        BlockPos base = helper.absolutePos(at);
        block.setPlacedBy(helper.getLevel(), base, state, null, ItemStack.EMPTY);
        return base;
    }

    private static boolean doorway(ServerLevel level, BlockPos base, boolean open) {
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 3; v++) {
                BlockState s = level.getBlockState(base.west(u).above(v));
                if (!s.is(GBConstruction.BLAST_DOOR.block().get()) || s.getValue(BlastDoorBlock.OPEN) != open) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The leaf's six blocks in front of the hinges: all there, or none. */
    private static int leafBlocks(ServerLevel level, BlockPos base) {
        int n = 0;
        for (int v = 0; v < 3; v++) {
            for (int w = 1; w < 3; w++) {
                n += level.getBlockState(base.above(v).north(w)).is(GBConstruction.BLAST_DOOR.block().get()) ? 1 : 0;
            }
        }
        return n;
    }

    private static void blastDoorSwings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = blastDoor(helper, new BlockPos(8, 1, 8));
        BlastDoorBlockEntity door = (BlastDoorBlockEntity) level.getBlockEntity(base);
        helper.assertTrue(door != null && doorway(level, base, false), "laid shut, all six blocks");
        BlockPos mid = base.west().above();
        helper.assertTrue(level.getBlockState(mid).getCollisionShape(level, mid).bounds().minZ < 0.01, "shut, its leaf closes the doorway's front");
        door.toggle(level, null);
        helper.assertTrue(leafBlocks(level, base) == 6, "the leaf's blocks laid as it starts: " + leafBlocks(level, base));
        helper.runAfterDelay(BlastDoorBlockEntity.TRAVEL + 3, () -> {
            helper.assertTrue(door.position() == BlastDoorBlockEntity.TRAVEL && doorway(level, base, true), "open: " + door.position());
            helper.assertTrue(level.getBlockState(mid).getCollisionShape(level, mid).bounds().minZ > 0.2, "open, only the frame in the doorway");
            door.toggle(level, null);
            helper.runAfterDelay(BlastDoorBlockEntity.TRAVEL + 3, () -> {
                helper.assertTrue(door.position() == 0 && doorway(level, base, false), "shut again: " + door.position());
                helper.assertTrue(leafBlocks(level, base) == 0, "the leaf's blocks taken away: " + leafBlocks(level, base));
                helper.succeed();
            });
        });
    }

    private static void blastDoorBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = blastDoor(helper, new BlockPos(8, 1, 8));
        BlastDoorBlockEntity door = (BlastDoorBlockEntity) level.getBlockEntity(base);
        level.setBlockAndUpdate(base.north(2).above(), Blocks.STONE.defaultBlockState());
        door.toggle(level, null);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!door.opening() && door.position() == 0, "stays shut: " + door.position());
            helper.assertTrue(leafBlocks(level, base) == 0 && level.getBlockState(base.north(2).above()).is(Blocks.STONE), "lays nothing, the wall stands");
            helper.succeed();
        });
    }

    private static void blastDoorRedstone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = blastDoor(helper, new BlockPos(8, 1, 8));
        BlastDoorBlockEntity door = (BlastDoorBlockEntity) level.getBlockEntity(base);
        BlockPos power = base.west(2);
        level.setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(BlastDoorBlockEntity.TRAVEL + 3, () -> {
            helper.assertTrue(door.position() == BlastDoorBlockEntity.TRAVEL, "a signal opens it: " + door.position());
            level.setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(BlastDoorBlockEntity.TRAVEL + 3, () -> {
                helper.assertTrue(door.position() == 0 && leafBlocks(level, base) == 0, "the signal gone, it shuts: " + door.position());
                helper.succeed();
            });
        });
    }

    private static void blastDoorBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = blastDoor(helper, new BlockPos(8, 1, 8));
        level.destroyBlock(base.west().above(2), true);
        helper.runAfterDelay(6, () -> {
            for (int u = 0; u < 2; u++) {
                for (int v = 0; v < 3; v++) {
                    helper.assertTrue(level.getBlockState(base.west(u).above(v)).isAir(), "all gone: " + base.west(u).above(v));
                }
            }
            long items = level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4)).stream()
                    .filter(e -> e.getItem().is(GBConstruction.BLAST_DOOR.item())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(items == 1, "one door back: " + items);
            helper.succeed();
        });
    }

    private static BlockState slab(SlabType type) {
        return GBConstruction.STEEL_GRATING_SLAB.block().get().defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static void hides(GameTestHelper helper, BlockState a, BlockState b, Direction dir, boolean expected, String what) {
        helper.assertTrue(a.skipRendering(b, dir) == expected, what + ": " + (expected ? "left out" : "drawn") + " toward " + dir);
    }

    private static void seeThrough(GameTestHelper helper) {
        BlockState lattice = GBConstruction.LATTICE_FRAME.block().get().defaultBlockState();
        BlockState grating = GBConstruction.STEEL_GRATING.block().get().defaultBlockState();
        BlockState bottom = slab(SlabType.BOTTOM), top = slab(SlabType.TOP), dbl = slab(SlabType.DOUBLE);
        for (Direction d : Direction.values()) {
            hides(helper, lattice, lattice, d, true, "lattice by lattice");
            hides(helper, lattice, grating, d, true, "lattice by grating");
            hides(helper, grating, lattice, d, true, "grating by lattice");
            hides(helper, grating, grating, d, true, "grating by grating");
            hides(helper, lattice, dbl, d, true, "lattice by a double slab");
            hides(helper, lattice, Blocks.GLASS.defaultBlockState(), d, false, "lattice by glass");
            hides(helper, lattice, Blocks.AIR.defaultBlockState(), d, false, "lattice by air");
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            hides(helper, bottom, bottom, d, true, "bottom slab by bottom slab");
            hides(helper, top, top, d, true, "top slab by top slab");
            hides(helper, bottom, top, d, false, "bottom slab by top slab");
            hides(helper, top, bottom, d, false, "top slab by bottom slab");
            hides(helper, bottom, grating, d, true, "bottom slab by grating (it covers the half)");
            hides(helper, grating, bottom, d, false, "grating by bottom slab (only half covered)");
        }
        hides(helper, bottom, top, Direction.DOWN, true, "bottom slab over a top slab");
        hides(helper, top, bottom, Direction.UP, true, "top slab under a bottom slab");
        hides(helper, bottom, bottom, Direction.DOWN, false, "bottom slab over a bottom slab (a gap between)");
        hides(helper, grating, top, Direction.DOWN, true, "grating over a top slab");
        hides(helper, grating, bottom, Direction.DOWN, false, "grating over a bottom slab");
        helper.succeed();
    }
}
