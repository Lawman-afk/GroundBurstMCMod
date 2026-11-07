package dev.groundburst.content.construction;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Bunker Lamp (DESIGN.md §22.5): a vapour-proof lamp in a cage, standing out of whatever face it was put on (a
 * ceiling, a wall, the floor). Lit while a redstone signal reaches it ({@link #LIT}, light 14), as a Redstone Lamp is (the
 * user's wish: by redstone, not by a click); it goes dark a moment after the signal does, a relay's click each way.
 */
public class BunkerLampBlock extends DirectionalBlock {
    @Override
    protected com.mojang.serialization.MapCodec<? extends BunkerLampBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Map<Direction, VoxelShape> SHAPES = Fittings.pointing(new double[] {3.8, 0, 3.8, 12.2, 6.7, 12.2});

    public BunkerLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace())
                .setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** A signal coming lights it at once; one gone puts it out four ticks on (if it has not come back). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos_, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean lit = state.getValue(LIT);
        if (lit != level.hasNeighborSignal(pos)) {
            if (lit) {
                level.scheduleTick(pos, this, 4);
            } else {
                switchTo(state, level, pos, true);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && !level.hasNeighborSignal(pos)) {
            switchTo(state, level, pos, false);
        }
    }

    private static void switchTo(BlockState state, Level level, BlockPos pos, boolean lit) {
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, GBSounds.LAMP_SWITCH.get(), SoundSource.BLOCKS, 0.35f, lit ? 1.1f : 0.9f);
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
}
