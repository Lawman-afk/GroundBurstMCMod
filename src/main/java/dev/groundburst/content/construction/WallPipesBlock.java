package dev.groundburst.content.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wall Pipes (DESIGN.md §22.5): three pipes along a wall on brackets, running on into wall pipes (and pipe valves) beside
 * them that face the same way; where nothing joins them, their ends are capped. {@link #LEFT} and {@link #RIGHT}: joined on
 * the left and the right as one looks at them.
 */
public class WallPipesBlock extends HorizontalDirectionalBlock {
    

    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    private static final VoxelShape[] SHAPES = Fittings.facing(new double[] {0, 6.6, 12, 16, 16, 16});

    public WallPipesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT);
    }

    /** The way to its left as one looks at it (its model's +x, built facing north). */
    static Direction left(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    private static boolean joins(BlockState state, BlockState neighbour) {
        return neighbour.getBlock() instanceof WallPipesBlock && neighbour.getValue(FACING) == state.getValue(FACING);
    }

    /** Its ends joined to whatever pipes stand beside it now. */
    public BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        Direction left = left(state);
        return state.setValue(LEFT, joins(state, level.getBlockState(pos.relative(left))))
                .setValue(RIGHT, joins(state, level.getBlockState(pos.relative(left.getOpposite()))));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joined(defaultBlockState().setValue(FACING, Fittings.wallFacing(context)), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) { net.minecraft.world.level.LevelAccessor ticks = level; net.minecraft.util.RandomSource random = level.getRandom();
        Direction left = left(state);
        if (direction == left) {
            return state.setValue(LEFT, joins(state, neighbor));
        }
        if (direction == left.getOpposite()) {
            return state.setValue(RIGHT, joins(state, neighbor));
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    /** Mirrored, its left is its right. */
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? out : out.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }
}
