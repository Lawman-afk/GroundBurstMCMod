package dev.groundburst.gametest;

import dev.groundburst.content.construction.BlastDoorBlock;
import dev.groundburst.content.construction.BlastDoorBlockEntity;
import dev.groundburst.content.construction.GBConstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
 * its lid standing up out of the floor and the way through it clear; the Pressure Door: a signal opens both its halves.
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
            helper.assertTrue(shape.bounds().maxY > 1.5, "its lid stands up out of the floor: " + shape.bounds());
            helper.assertTrue(shape.toAabbs().stream().noneMatch(b -> b.contains(0.5, 0.9, 0.5)), "the way through it clear");
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
