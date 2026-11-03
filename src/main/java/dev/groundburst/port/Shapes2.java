package dev.groundburst.port;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Minecraft 26.x's shape turns that 1.21.1 lacks. */
public final class Shapes2 {
    private Shapes2() {}

    /** A shape drawn facing north, turned to face each horizontal way (clockwise from above: north, east, south, west). */
    public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape north) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        out.put(Direction.NORTH, north);
        out.put(Direction.EAST, turn(north, 1));
        out.put(Direction.SOUTH, turn(north, 2));
        out.put(Direction.WEST, turn(north, 3));
        return out;
    }

    /** A column {@code size} pixels across, centred on the block, from {@code minY} to {@code maxY} (in pixels). */
    public static VoxelShape column(double size, double minY, double maxY) {
        return column(size, size, minY, maxY);
    }

    public static VoxelShape column(double sizeX, double sizeZ, double minY, double maxY) {
        return net.minecraft.world.level.block.Block.box(8 - sizeX / 2, minY, 8 - sizeZ / 2, 8 + sizeX / 2, maxY, 8 + sizeZ / 2);
    }

    /** {@code shape} turned a quarter clockwise (seen from above) {@code quarters} times about the block's centre. */
    public static VoxelShape turn(VoxelShape shape, int quarters) {
        VoxelShape out = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double x0 = box.minX, z0 = box.minZ, x1 = box.maxX, z1 = box.maxZ;
            for (int q = 0; q < (quarters & 3); q++) {
                // (x, z) -> (1 - z, x): the north face (z = 0) comes round to the east (x = 1).
                double nx0 = 1 - z1, nx1 = 1 - z0, nz0 = x0, nz1 = x1;
                x0 = nx0;
                x1 = nx1;
                z0 = nz0;
                z1 = nz1;
            }
            out = Shapes.or(out, Shapes.box(x0, box.minY, z0, x1, box.maxY, z1));
        }
        return out.optimize();
    }
}
