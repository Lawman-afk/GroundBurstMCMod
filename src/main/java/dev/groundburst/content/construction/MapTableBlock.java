package dev.groundburst.content.construction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Situation Map Table (DESIGN.md §22.4): a table two blocks by two with a map on it, one item laying its four blocks
 * ({@link #U} across to the placer's right, {@link #W} ahead of them); each block draws its quarter (its own block model,
 * cut from the whole: tools/assets/command_room.py). Breaking any block takes the whole table, one item back (the
 * placer's block's loot).
 */
public class MapTableBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty U = IntegerProperty.create("u", 0, 1);
    public static final IntegerProperty W = IntegerProperty.create("w", 0, 1);
    /** The table's top: every block stands under it. */
    private static final VoxelShape TOP = Block.box(0, 12.5, 0, 16, 14, 16);
    /** Its legs, at the table's outer corners, by block (u, w) as it faces north: x, z of the leg's corner. */
    private static final double[][] LEGS = {{0.6, 13.4}, {13.4, 13.4}, {0.6, 0.6}, {13.4, 0.6}};
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int i = 0; i < 4; i++) {
                double[] leg = LEGS[i];
                SHAPES[facing.get2DDataValue()][i] = Shapes.or(TOP,
                        BlastDoorBlock.box(facing, false, leg[0], 0, leg[1], leg[0] + 2, 12.5, leg[1] + 2));
            }
        }
    }

    public MapTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(U, 0).setValue(W, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, U, W);
    }

    /** The block {@code (u, w)} of the table whose placer's block is {@code base}, facing {@code facing}. */
    public static BlockPos cell(BlockPos base, Direction facing, int u, int w) {
        return base.relative(facing.getClockWise(), u).relative(facing, w);
    }

    private static boolean isBase(BlockState state) {
        return state.getValue(U) == 0 && state.getValue(W) == 0;
    }

    private @Nullable BlockPos baseOf(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos base = pos.relative(facing.getClockWise(), -state.getValue(U)).relative(facing, -state.getValue(W));
        BlockState s = level.getBlockState(base);
        return s.is(this) && isBase(s) && s.getValue(FACING) == facing ? base : null;
    }

    /** Facing the way its placer looks, laid to their right and ahead: only if all four places are free. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        Level level = context.getLevel();
        for (int u = 0; u < 2; u++) {
            for (int w = 0; w < 2; w++) {
                BlockPos p = cell(context.getClickedPos(), facing, u, w);
                if ((u > 0 || w > 0) && !level.getBlockState(p).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        for (int u = 0; u < 2; u++) {
            for (int w = 0; w < 2; w++) {
                if (u > 0 || w > 0) {
                    level.setBlock(cell(pos, state.getValue(FACING), u, w), state.setValue(U, u).setValue(W, w), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !isBase(state)) {
            BlockPos base = baseOf(level, pos, state);
            if (base != null) {
                level.destroyBlock(base, !player.preventsBlockDrops(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos base = isBase(state) ? pos : baseOf(level, pos, state);
        if (base == null) {
            return;
        }
        for (int u = 0; u < 2; u++) {
            for (int w = 0; w < 2; w++) {
                BlockPos p = cell(base, state.getValue(FACING), u, w);
                if (level.getBlockState(p).is(this)) {
                    level.scheduleTick(p, this, 1);
                }
            }
        }
    }

    /** A block whose table has lost its placer's block goes; a table missing a block falls apart, one item back. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos base = baseOf(level, pos, state);
        if (base == null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (isBase(state)) {
            for (int u = 0; u < 2; u++) {
                for (int w = 0; w < 2; w++) {
                    BlockState s = level.getBlockState(cell(pos, state.getValue(FACING), u, w));
                    if (!s.is(this) || s.getValue(U) != u || s.getValue(W) != w || s.getValue(FACING) != state.getValue(FACING)) {
                        level.destroyBlock(pos, true);
                        return;
                    }
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(U) + 2 * state.getValue(W)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
