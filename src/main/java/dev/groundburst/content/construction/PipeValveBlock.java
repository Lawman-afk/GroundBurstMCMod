package dev.groundburst.content.construction;

import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Pipe Valve (DESIGN.md §22.5): the big pipe of a run of Wall Pipes through a gate valve, its handwheel out in front.
 * A click turns the wheel: the valve opens ({@link #OPEN}: its stem out, its spokes a quarter round) and gives a signal as
 * a lever does (strong into the wall it is on); another shuts it.
 */
public class PipeValveBlock extends WallPipesBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape[] SHAPES = Fittings.facing(new double[] {0, 6.6, 12, 16, 16, 16}, new double[] {4.5, 8.5, 8.2, 11.5, 15.8, 12});

    public PipeValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean open = !state.getValue(OPEN);
            BlockState now = state.setValue(OPEN, open);
            level.setBlock(pos, now, Block.UPDATE_ALL);
            Fittings.switched(this, now, level, pos);
            level.playSound(null, pos, GBSounds.PIPE_VALVE_TURN.get(), SoundSource.BLOCKS, 0.7f, open ? 1.0f : 0.92f);
            level.gameEvent(player, open ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OPEN) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OPEN) && direction == state.getValue(FACING) ? 15 : 0;
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level_l, BlockPos pos, BlockState newState_, boolean movedByPiston) { super.onRemove(state, level_l, pos, newState_, movedByPiston); if (!(level_l instanceof net.minecraft.server.level.ServerLevel level) || state.is(newState_.getBlock())) return;
        if (!movedByPiston && state.getValue(OPEN)) {
            Fittings.switched(this, state, level, pos);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}
