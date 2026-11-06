package dev.groundburst.content.construction;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Tape Computer (DESIGN.md §22.4): a tape drive cabinet two blocks high, its lower block's block entity drawing the
 * whole of it (tools/assets/tape_computer.py). It runs from the moment it is placed ({@link #ACTIVE}: its reels read,
 * stop and rewind, its lamps blink, now and then its motors whirr); a click stops it, another starts it again. Its two
 * halves stand or fall together, as a door's do; the lower drops the item.
 */
public class TapeComputerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    @Override
    protected com.mojang.serialization.MapCodec<? extends TapeComputerBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ACTIVE = MachineBlock.ACTIVE;
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][2];
    private final Supplier<? extends BlockEntityType<?>> type;

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES[facing.get2DDataValue()][0] = BlastDoorBlock.box(facing, false, 1, 0, 2, 15, 16, 16);
            SHAPES[facing.get2DDataValue()][1] = BlastDoorBlock.box(facing, false, 1, 0, 2, 15, 14.5, 16);
        }
    }

    public TapeComputerBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(ACTIVE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, ACTIVE);
    }

    /** Placed facing its placer, running, if the block over it is free. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos up = context.getClickedPos().above();
        Level level = context.getLevel();
        if (level.isOutsideBuildHeight(up) || !level.getBlockState(up).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) { net.minecraft.world.level.LevelAccessor ticks = level; net.minecraft.util.RandomSource random = level.getRandom();
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state.setValue(ACTIVE, neighbor.getValue(ACTIVE)) : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** A player breaking the upper half breaks the lower, which drops the item (none in creative). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) {
                level.destroyBlock(below, !player.getAbilities().instabuild, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A click stops it, or starts it again. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean on = !state.getValue(ACTIVE);
            BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            for (BlockPos p : new BlockPos[] {lower, lower.above()}) {
                BlockState s = level.getBlockState(p);
                if (s.is(this)) {
                    level.setBlock(p, s.setValue(ACTIVE, on), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, pos, GBSounds.TAPE_SWITCH.get(), SoundSource.BLOCKS, 0.7f, on ? 1.1f : 0.9f);
        }
        return InteractionResult.SUCCESS;
    }

    /** While it runs, its motors whirr now and then, the lower block heard. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(ACTIVE) && random.nextInt(36) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, GBSounds.TAPE_WHIRR.get(), SoundSource.BLOCKS,
                    0.35f, 0.9f + random.nextFloat() * 0.2f, false);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(HALF) == DoubleBlockHalf.LOWER ? 0 : 1];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;                            // the lower block's block entity draws the cabinet
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.type.get().create(pos, state) : null;
    }
}
