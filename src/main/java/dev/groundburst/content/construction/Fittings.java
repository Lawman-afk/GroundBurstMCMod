package dev.groundburst.content.construction;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** What the bunker fittings (DESIGN.md §22.5) share: their shapes turned as their models are, a switch's signal. */
final class Fittings {
    private Fittings() {}

    /**
     * Boxes (x0, y0, z0, x1, y1, z1 in block pixels) of a model built standing on a floor and pointing up, turned to point
     * each way as its blockstate turns it (x 90 tips it to point north, y then turns it round): {pointing: shape}.
     */
    static Map<Direction, VoxelShape> pointing(double[]... boxes) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, pointed(d, b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            out.put(d, shape);
        }
        return out;
    }

    private static VoxelShape pointed(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] lo = {16, 16, 16};
        double[] hi = {0, 0, 0};
        for (double[] p : new double[][] {{x0, y0, z0}, {x1, y1, z1}}) {
            double x = p[0];
            double y = p[1];
            double z = p[2];
            double[] q = switch (d) {
                case UP -> new double[] {x, y, z};
                case DOWN -> new double[] {x, 16 - y, 16 - z};
                case NORTH -> new double[] {x, z, 16 - y};
                case SOUTH -> new double[] {16 - x, z, y};
                case EAST -> new double[] {y, z, x};
                case WEST -> new double[] {16 - y, z, 16 - x};
            };
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], q[k]);
                hi[k] = Math.max(hi[k], q[k]);
            }
        }
        return Block.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    /** Boxes of a model built facing north (block pixels), turned to face each of the four ways: by {@code get2DDataValue()}. */
    static VoxelShape[] facing(double[]... boxes) {
        VoxelShape[] out = new VoxelShape[4];
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, BlastDoorBlock.box(facing, false, b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            out[facing.get2DDataValue()] = shape;
        }
        return out;
    }

    /** A wall fitting's facing as it is put up: out of the wall clicked, or (on a floor or a ceiling) toward its placer. */
    static Direction wallFacing(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
    }

    /** A switch thrown (the Fuse Box's breaker, the Pipe Valve's wheel): its neighbours told, and the block it hangs on. */
    static void switched(Block block, BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite()), block);
    }
}
