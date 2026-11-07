package dev.groundburst.content.construction;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the bunker fittings (DESIGN.md §22.5) and the other props share: their shapes turned as their models are, a
 * switch's signal, and a redstone signal holding a prop on or open (the user's wish: so it can be built with open).
 */
final class Fittings {
    /**
     * A switch that gives a signal (the Pipe Valve, the Fuse Box) held on by a redstone signal at it: shown open, or on,
     * its signal still its own switch's alone (were it given while held, its own signal could hold it on).
     */
    static final BooleanProperty HELD = BooleanProperty.create("held");
    /**
     * What a two-high prop's clicks last chose (the Searchlight, the Tape Computer): it shows on ({@code ACTIVE}) while this
     * is on or a redstone signal reaches either of its blocks.
     */
    static final BooleanProperty SWITCHED = BooleanProperty.create("switched");

    private Fittings() {}

    /** A signal-giving switch looking again at the redstone round it (held on, or let go): its look alone, told to clients. */
    static void hold(BlockState state, Level level, BlockPos pos) {
        hold(state, level, pos, s -> false);
    }

    /**
     * As {@link #hold(BlockState, Level, BlockPos)}, deaf to the signal of any block {@code ignored} accepts: two Fuse Boxes
     * side by side, or back to back through the wall they hang on, would otherwise hold each other on.
     */
    static void hold(BlockState state, Level level, BlockPos pos, Predicate<BlockState> ignored) {
        boolean held = signalFromOthers(level, pos, ignored);
        if (held != state.getValue(HELD)) {
            level.setBlock(pos, state.setValue(HELD, held), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Whether a redstone signal reaches {@code pos} as the level's {@code hasNeighborSignal} finds one, but none given by a
     * block {@code ignored} accepts, nor passed on by a solid block from one (a block strongly powered by it).
     */
    static boolean signalFromOthers(Level level, BlockPos pos, Predicate<BlockState> ignored) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            if (ignored.test(ns)) {
                continue;
            }
            if (ns.getSignal(level, n, d) > 0) {
                return true;
            }
            if (ns.shouldCheckWeakPower(level, n, d)) {
                for (Direction e : Direction.values()) {
                    BlockPos m = n.relative(e);
                    BlockState ms = level.getBlockState(m);
                    if (!m.equals(pos) && !ignored.test(ms) && ms.getDirectSignal(level, m, e) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether a redstone signal reaches either block of a two-high prop whose lower block is {@code lower}. */
    static boolean tallPowered(Level level, BlockPos lower) {
        return level.hasNeighborSignal(lower) || level.hasNeighborSignal(lower.above());
    }

    private static BlockPos lowerOf(BlockState state, BlockPos pos) {
        return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** A click on a two-high prop: its switch thrown, both halves on while it is on or powered. Returns whether it shows on. */
    static boolean clickTall(Block block, BlockState state, Level level, BlockPos pos, BooleanProperty active) {
        BlockPos lower = lowerOf(state, pos);
        boolean switched = !state.getValue(SWITCHED);
        boolean on = switched || tallPowered(level, lower);
        setTall(block, level, lower, active, on, switched);
        return on;
    }

    /** A two-high prop looking again at the redstone round it: on while switched on or powered. */
    static void lookTall(Block block, BlockState state, Level level, BlockPos pos, BooleanProperty active) {
        BlockPos lower = lowerOf(state, pos);
        boolean on = state.getValue(SWITCHED) || tallPowered(level, lower);
        if (on != state.getValue(active)) {
            setTall(block, level, lower, active, on, state.getValue(SWITCHED));
        }
    }

    private static void setTall(Block block, Level level, BlockPos lower, BooleanProperty active, boolean on, boolean switched) {
        for (BlockPos p : new BlockPos[] {lower, lower.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.is(block)) {
                level.setBlock(p, s.setValue(active, on).setValue(SWITCHED, switched), Block.UPDATE_ALL);
            }
        }
    }

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
