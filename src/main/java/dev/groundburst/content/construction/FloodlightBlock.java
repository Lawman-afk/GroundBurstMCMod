package dev.groundburst.content.construction;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A floodlight (DESIGN.md §22.2): a lamp head on a short bracket, its lens facing the way it was placed from the face
 * clicked (out of a wall, up from the ground, down from a ceiling), lighting at full strength.
 */
public class FloodlightBlock extends DirectionalBlock {
    /** The head's shape facing each way: a housing 10 wide and 7 deep, the lens at its face, on a bracket back to the mount. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            SHAPES.put(d, Shapes.or(box(d, 3, 3, 6, 13, 13, 13), box(d, 6, 6, 13, 10, 10, 16)));
        }
    }

    public FloodlightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * A box given as if the head faced north (-z) with its back at z 16, turned to face {@code d}: the mount is always
     * on the side the head faces away from.
     */
    static VoxelShape box(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[][] c = {{x0, y0, z0}, {x1, y1, z1}};
        double[] lo = {16, 16, 16}, hi = {0, 0, 0};
        for (double[] p : c) {
            double x = p[0], y = p[1], z = p[2];
            double[] q = switch (d) {
                case NORTH -> new double[] {x, y, z};
                case SOUTH -> new double[] {16 - x, y, 16 - z};
                case WEST -> new double[] {z, y, 16 - x};
                case EAST -> new double[] {16 - z, y, x};
                case UP -> new double[] {x, 16 - z, y};
                case DOWN -> new double[] {x, z, 16 - y};
            };
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], q[k]);
                hi[k] = Math.max(hi[k], q[k]);
            }
        }
        return Block.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }
}
