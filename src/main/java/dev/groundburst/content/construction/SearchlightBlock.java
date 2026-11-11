package dev.groundburst.content.construction;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Searchlight (DESIGN.md §22.6): a big searchlight on a stand two blocks high, its lower block's block entity drawing
 * it (tools/assets/searchlight.py). A click switches it on ({@link #ACTIVE}): its lens lit, its beam out into the sky, it
 * sweeps the sky, turning on its yoke and raising and lowering its drum; another switches it off. A redstone signal at
 * either of its blocks holds it on, whatever its clicks chose ({@link Fittings#SWITCHED}). Its two halves stand or
 * fall together, as a door's do; the lower drops the item.
 */
public class SearchlightBlock extends HorizontalDirectionalBlock implements EntityBlock {
    

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ACTIVE = MachineBlock.ACTIVE;
    /** What its clicks last chose ({@link Fittings#SWITCHED}). */
    public static final BooleanProperty SWITCHED = Fittings.SWITCHED;
    private static final VoxelShape[] LOWER = Fittings.facing(new double[] {3, 0, 3, 13, 1.2, 13}, new double[] {5.6, 1.2, 5.6, 10.4, 16, 10.4});
    private static final VoxelShape[] UPPER = Fittings.facing(new double[] {1.5, 0, 1.5, 14.5, 9, 14.5});
    private final Supplier<? extends BlockEntityType<?>> type;

    public SearchlightBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(ACTIVE, false)
                .setValue(Fittings.SWITCHED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, ACTIVE, Fittings.SWITCHED);
    }

    /** Placed facing away from its placer (its lens to the way they look), if the block over it is free. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos up = context.getClickedPos().above();
        Level level = context.getLevel();
        if (level.isOutsideBuildHeight(up) || !level.getBlockState(up).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(ACTIVE, Fittings.tallPowered(level, context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too; the upper is lit as the lower is. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) { net.minecraft.world.level.LevelAccessor ticks = level; net.minecraft.util.RandomSource random = level.getRandom();
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half
                    ? state.setValue(ACTIVE, neighbor.getValue(ACTIVE)).setValue(Fittings.SWITCHED, neighbor.getValue(Fittings.SWITCHED))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** A player breaking the upper half breaks the lower, which drops the item (none in creative). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) {
                level.destroyBlock(below, !player.getAbilities().instabuild, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player); return;
    }

    /** A click switches it on, or off. */
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean on = Fittings.clickTall(this, state, level, pos, ACTIVE);
            level.playSound(null, pos, GBSounds.FUSE_BOX_SWITCH.get(), SoundSource.BLOCKS, 0.8f, on ? 0.9f : 0.75f);
            level.gameEvent(player, on ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** A redstone signal at either block holds it on; gone, it is as its last click left it. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos_, boolean movedByPiston) {
        if (!level.isClientSide()) {
            Fittings.lookTall(this, state, level, pos, ACTIVE);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER)[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public boolean isPathfindable(BlockState state, net.minecraft.world.level.BlockGetter level_, BlockPos pos_, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;                            // the lower block's block entity draws the light
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.type.get().create(pos, state) : null;
    }

    @Override
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state_, net.minecraft.world.level.Level level_, net.minecraft.core.BlockPos pos_, net.minecraft.world.entity.player.Player player_, net.minecraft.world.InteractionHand hand_, net.minecraft.world.phys.BlockHitResult hit_) {
        return hand_ == net.minecraft.world.InteractionHand.MAIN_HAND ? this.useWithoutItem(state_, level_, pos_, player_, hit_)
                : net.minecraft.world.InteractionResult.PASS;
    }
}
