package dev.groundburst.content.construction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An open Bunker Hatch's lid (DESIGN.md §22.3): the block where the lid stands while the hatch is open, over a floor hatch
 * ({@link #HALF} top, as the hatch's) or under a ceiling hatch (bottom). It draws nothing (the hatch's model draws the
 * lid); its shape is the lid's, solid, and a click on it shuts the hatch (the lid had stood out of the hatch's own block,
 * where the server turned clicks on it away). The hatch lays it and takes it away ({@link #follow}); it goes too if the
 * hatch is not there and open, and breaking it breaks the hatch. No item of its own.
 */
public class BunkerHatchLidBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    /** Shapes by facing (2D data value) and half (0 bottom, 1 top), worked out facing north: the lid at the south edge. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][2];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES[facing.get2DDataValue()][1] = BlastDoorBlock.box(facing, false, 1.2, 0, 13.8, 14.8, 14.5, 16);
            SHAPES[facing.get2DDataValue()][0] = BlastDoorBlock.box(facing, false, 1.2, 1.5, 13.8, 14.8, 16, 16);
        }
    }

    public BunkerHatchLidBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.TOP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** Where a hatch's lid stands while it is open: over a floor hatch, under a ceiling hatch. */
    static BlockPos lidOf(BlockPos hatch, BlockState state) {
        return state.getValue(TrapDoorBlock.HALF) == Half.TOP ? hatch.above() : hatch.below();
    }

    /** The hatch at {@code hatch} as it now stands: open, its lid's block laid where the lid stands, if that place is free; shut, taken away. */
    static void follow(Level level, BlockPos hatch, BlockState state) {
        BlockPos at = lidOf(hatch, state);
        Block lid = GBConstruction.BUNKER_HATCH_LID.get();
        BlockState there = level.getBlockState(at);
        if (state.getValue(TrapDoorBlock.OPEN)) {
            if (!there.is(lid) && !level.isOutsideBuildHeight(at) && there.canBeReplaced() && there.getFluidState().isEmpty()) {
                level.setBlock(at, lid.defaultBlockState().setValue(FACING, state.getValue(FACING)).setValue(HALF, state.getValue(TrapDoorBlock.HALF)),
                        Block.UPDATE_ALL);
            }
        } else if (there.is(lid)) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The hatch at {@code hatch} gone: its lid's block goes too. */
    static void clear(Level level, BlockPos hatch, BlockState state) {
        BlockPos at = lidOf(hatch, state);
        if (level.getBlockState(at).is(GBConstruction.BUNKER_HATCH_LID.get())) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The open hatch whose lid this is, or null if it is not there (or not open, or turned another way). */
    private static @Nullable BlockPos hatchOf(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos at = state.getValue(HALF) == Half.TOP ? pos.below() : pos.above();
        BlockState s = level.getBlockState(at);
        return s.getBlock() instanceof BunkerHatchBlock && s.getValue(TrapDoorBlock.OPEN) && s.getValue(TrapDoorBlock.HALF) == state.getValue(HALF)
                && s.getValue(FACING) == state.getValue(FACING) ? at : null;
    }

    /** A click on the lid is a click on its hatch: it shuts. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos hatch = hatchOf(level, pos, state);
        return hatch == null ? InteractionResult.PASS : level.getBlockState(hatch).useWithoutItem(level, player, hit.withPosition(hatch));
    }

    /** Its hatch gone, shut or turned: it goes. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        Direction toHatch = state.getValue(HALF) == Half.TOP ? Direction.DOWN : Direction.UP;
        if (direction == toHatch && hatchOf(level, pos, state) == null) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    /** A player breaking the lid breaks its hatch (which drops the item, none in creative). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos hatch = hatchOf(level, pos, state);
            if (hatch != null) {
                level.destroyBlock(hatch, !player.preventsBlockDrops(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(HALF) == Half.TOP ? 1 : 0];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;                            // the hatch's model draws the lid
    }
}
