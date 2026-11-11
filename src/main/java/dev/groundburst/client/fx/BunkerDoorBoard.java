package dev.groundburst.client.fx;

import java.util.List;

import dev.groundburst.content.construction.BlastDoorBlock;
import dev.groundburst.content.construction.BlastDoorBlockEntity;
import dev.groundburst.content.construction.GBConstruction;
import dev.groundburst.content.launch.GBLaunch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The bunker doors in a wall, for the site photographer ({@code -Psite=doors}; DESIGN.md §22.3): a reinforced concrete
 * wall four high with four Blast Doors in it, facing north: shut (hinges on the left), opening (left), opening (right:
 * the mirrored model) and shut (right); two Pressure Doors between them, shut and open; in the ground in front, two
 * Bunker Hatches over a shaft, shut and open, a ladder up each shaft's front wall to the hatch's own; under a concrete
 * roof beside, a ceiling hatch open. Seen from all round, and close up.
 */
final class BunkerDoorBoard {
    /** A close picture: its name, where the camera stands, the way it faces (Minecraft's yaw: 0 south) and looks down. */
    record CloseUp(String name, Vec3 at, float yaw, float pitch) {}

    /** Each door's hinges' column along the wall (x from the board's middle), its hinges' side, and whether it is opened. */
    private record Door(int x, DoorHingeSide hinge, boolean open) {}

    private static final List<Door> DOORS = List.of(new Door(-8, DoorHingeSide.LEFT, false), new Door(-1, DoorHingeSide.LEFT, true),
            new Door(4, DoorHingeSide.RIGHT, true), new Door(10, DoorHingeSide.RIGHT, false));

    private BunkerDoorBoard() {}

    static BoundingBox build(ServerLevel level, BlockPos o) {
        int x = o.getX(), y = o.getY() + 1, z = o.getZ();
        BlockState wall = GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(x - 13, y, z, x + 14, y + 3, z)) {
            level.setBlock(p, wall, 2);
        }
        BlastDoorBlock block = GBConstruction.BLAST_DOOR.block().get();
        for (Door d : DOORS) {
            BlockState state = block.defaultBlockState().setValue(BlastDoorBlock.FACING, Direction.NORTH).setValue(BlastDoorBlock.HINGE, d.hinge());
            BlockPos at = new BlockPos(x + d.x(), y, z);
            Direction across = BlastDoorBlock.across(state);
            for (int u = 0; u < 2; u++) {
                for (int v = 0; v < 3; v++) {
                    level.setBlock(at.relative(across, u).above(v), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            }
            level.setBlock(at, state, 3);
            block.setPlacedBy(level, at, state, null, ItemStack.EMPTY);
            if (d.open() && ((Object) level.getBlockEntity(at)) instanceof BlastDoorBlockEntity door) {
                door.toggle(level, null);
            }
        }
        // Two Pressure Doors in the wall: shut, and open.
        net.minecraft.world.level.block.DoorBlock pressure = (net.minecraft.world.level.block.DoorBlock) GBConstruction.PRESSURE_DOOR.block().get();
        for (int i = 0; i < 2; i++) {
            BlockPos at = new BlockPos(x + (i == 0 ? -5 : 1), y, z);
            BlockState lower = pressure.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, i == 1);
            level.setBlock(at, lower, 2);
            level.setBlock(at.above(), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
            level.setBlock(at.above(2), wall, 2);
        }
        // Two floor hatches in the ground before the wall, over a shaft three deep: shut, and open. A ladder up each shaft's
        // front wall (north: the hatches' own ladders' side, opposite their hinges) runs on up the hatch's.
        BlockState hatch = GBConstruction.BUNKER_HATCH.block().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
        for (int i = 0; i < 2; i++) {
            BlockPos at = new BlockPos(x - 5 + i * 3, y - 1, z - 6);
            for (int d = 1; d <= 3; d++) {
                level.setBlock(at.below(d), net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH), 2);
            }
            level.setBlock(at, hatch.setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, i == 1), 2);
        }
        // A concrete roof on posts beside the wall, an open ceiling hatch in it.
        for (BlockPos p : BlockPos.betweenClosed(x + 6, y + 3, z - 9, x + 8, y + 3, z - 7)) {
            level.setBlock(p, wall, 2);
        }
        for (int[] c : new int[][] {{6, -9}, {8, -9}, {6, -7}, {8, -7}}) {
            for (int d = 0; d < 3; d++) {
                level.setBlock(new BlockPos(x + c[0], y + d, z + c[1]), wall, 2);
            }
        }
        level.setBlock(new BlockPos(x + 7, y + 3, z - 8), hatch.setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.Half.BOTTOM).setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true), 2);
        return new BoundingBox(x - 14, o.getY() - 3, z - 10, x + 15, y + 5, z + 3);
    }

    /** The close pictures: each door from the front at a man's height, the open ones from beside, two from behind. */
    static List<CloseUp> closeUps(BlockPos o) {
        double x = o.getX() + 0.5, y = o.getY() + 1, z = o.getZ() + 0.5;
        return List.of(
                new CloseUp("front_shut", new Vec3(x - 8.5, y + 1.6, z - 6.5), 0, 6),
                new CloseUp("front_open", new Vec3(x - 1.5, y + 1.6, z - 7.5), 0, 6),
                new CloseUp("open_from_beside", new Vec3(x + 1.5, y + 1.8, z - 5.5), 55, 10),
                new CloseUp("front_open_mirrored", new Vec3(x + 4.5, y + 1.6, z - 7.5), 0, 6),
                new CloseUp("mirrored_from_beside", new Vec3(x + 1.2, y + 1.8, z - 5.5), -50, 10),
                new CloseUp("front_shut_mirrored", new Vec3(x + 10.5, y + 1.6, z - 6.5), 0, 6),
                new CloseUp("back_shut", new Vec3(x - 8.5, y + 1.6, z + 6.5), 180, 6),
                new CloseUp("back_open", new Vec3(x - 1.5, y + 1.6, z + 6.5), 180, 6),
                new CloseUp("hinges", new Vec3(x - 5.6, y + 2.0, z - 3.2), 60, 14),
                new CloseUp("pressure_doors", new Vec3(x - 2.0, y + 1.7, z - 5.0), 0, 6),
                new CloseUp("pressure_door_open", new Vec3(x + 3.2, y + 1.7, z - 2.6), 40, 12),
                new CloseUp("floor_hatches", new Vec3(x - 2.5, y + 2.2, z - 9.5), -20, 38),
                new CloseUp("floor_hatch_open", new Vec3(x - 0.2, y + 1.6, z - 7.0), 45, 30),
                new CloseUp("ceiling_hatch", new Vec3(x + 7.5, y + 1.0, z - 11.0), 0, -28),
                new CloseUp("hatch_open_close", new Vec3(x - 1.5, y - 0.72, z - 8.0), 0, 40),
                new CloseUp("hatch_ladder", new Vec3(x - 2.0, y - 3.0, z - 5.0), 180, -38),
                new CloseUp("pressure_door_open_close", new Vec3(x + 2.6, y + 0.0, z - 1.4), 45, 8));
    }
}
