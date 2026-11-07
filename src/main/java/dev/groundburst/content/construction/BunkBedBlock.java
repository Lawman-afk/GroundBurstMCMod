package dev.groundburst.content.construction;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Bunk Bed (DESIGN.md §22.5): a steel bunk two beds high and two blocks long, four blocks in all (a bed's foot and
 * head, {@link #HALF} lower and upper). Each bunk is a bed of its own: two can sleep in it, each sets a spawn point (the
 * upper one's, as vanilla's beds stacked, a bunk's: its sleeper stands up beside it or below). Its four blocks stand or
 * fall together; the lower head drops the item.
 */
public class BunkBedBlock extends BedBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Where a sleeper lies: on its mattress, 9.6 pixels up its block. */
    private static final double MATTRESS = 9.6 / 16.0;
    // (Each block's shape in its own frame, facing north: its posts, its bunk, its end; the upper bunk's guard rails, the
    // back one its whole length, the front one two thirds of it from the head; the ladder up the front past it.)
    private static final VoxelShape[] FOOT_LOWER = Fittings.facing(new double[] {0.6, 0, 14, 2, 16, 15.4}, new double[] {14, 0, 14, 15.4, 16, 15.4},
            new double[] {0.6, 6.4, 0, 15.4, 9.6, 15.4}, new double[] {2, 6.4, 14, 14, 12, 15.4}, new double[] {0, 0, 6.2, 0.6, 16, 12.6});
    private static final VoxelShape[] HEAD_LOWER = Fittings.facing(new double[] {0.6, 0, 0.6, 2, 16, 2}, new double[] {14, 0, 0.6, 15.4, 16, 2},
            new double[] {0.6, 6.4, 0.6, 15.4, 9.6, 16}, new double[] {2, 6.4, 0.6, 14, 12, 2});
    private static final VoxelShape[] FOOT_UPPER = Fittings.facing(new double[] {0.6, 0, 14, 2, 14, 15.4}, new double[] {14, 0, 14, 15.4, 14, 15.4},
            new double[] {0.6, 6.4, 0, 15.4, 9.6, 15.4}, new double[] {2, 6.4, 14, 14, 12, 15.4}, new double[] {0, 0, 6.2, 0.6, 11.4, 12.6},
            new double[] {14, 9.6, 0, 15.4, 12.4, 14}, new double[] {0.6, 9.6, 0, 2, 12.4, 4.7});
    private static final VoxelShape[] HEAD_UPPER = Fittings.facing(new double[] {0.6, 0, 0.6, 2, 14, 2}, new double[] {14, 0, 0.6, 15.4, 14, 2},
            new double[] {0.6, 6.4, 0.6, 15.4, 9.6, 16}, new double[] {2, 6.4, 0.6, 14, 12, 2}, new double[] {14, 9.6, 2, 15.4, 12.4, 16},
            new double[] {0.6, 9.6, 2, 2, 12.4, 16});

    public BunkBedBlock(Properties properties) {
        super(DyeColor.GRAY, properties);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;                            // its own model, not a bed's block entity
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    /** Its four blocks: the lower head (the one that drops it) first, then the lower foot, the upper foot, the upper head. */
    public static List<BlockPos> cells(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos foot = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (state.getValue(PART) == BedPart.HEAD) {
            foot = foot.relative(facing.getOpposite());
        }
        return List.of(foot.relative(facing), foot, foot.above(), foot.relative(facing).above());
    }

    /** Placed with its foot where it was put and its head ahead, if the three blocks it needs more are free. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        for (BlockPos p : List.of(pos.relative(facing), pos.above(), pos.relative(facing).above())) {
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(p)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        BlockPos head = pos.relative(state.getValue(FACING));
        level.setBlock(head, state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.setBlock(head.above(), state.setValue(PART, BedPart.HEAD).setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** A block whose bunk over or under it is gone goes too (the bed's own head and foot as a bed's). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) { net.minecraft.world.level.LevelAccessor ticks = level; net.minecraft.util.RandomSource random = level.getRandom();
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (direction == (lower ? Direction.UP : Direction.DOWN)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != state.getValue(HALF) && neighbor.getValue(PART) == state.getValue(PART)
                    && neighbor.getValue(FACING) == state.getValue(FACING) ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** In creative, the block that drops it is taken first and quietly, so breaking any of its blocks gives nothing back. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.getAbilities().instabuild) {
            BlockPos drops = cells(state, pos).get(0);
            BlockState there = level.getBlockState(drops);
            if (!drops.equals(pos) && there.is(this)) {
                level.setBlock(drops, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, drops, Block.getId(there));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean head = state.getValue(PART) == BedPart.HEAD;
        VoxelShape[] shapes = state.getValue(HALF) == DoubleBlockHalf.LOWER ? head ? HEAD_LOWER : FOOT_LOWER : head ? HEAD_UPPER : FOOT_UPPER;
        return shapes[state.getValue(FACING).get2DDataValue()];
    }
}
