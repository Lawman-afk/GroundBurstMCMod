package dev.groundzero.content.power;

import java.util.EnumMap;
import java.util.Map;


import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * A power cable. Connects to other cables and to anything exposing an energy handler on the facing side. Cables
 * store nothing: energy offered to a cable is delivered straight to the network's consumers (see {@link CableNetwork}).
 */
public class CableBlock extends Block {
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final VoxelShape CORE = Block.box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5);

    static {
        ARMS.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
        ARMS.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
        ARMS.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
        ARMS.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
        ARMS.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
        ARMS.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
    }

    private final CableTier tier;

    public CableBlock(Properties properties, CableTier tier) {
        super(properties);
        this.tier = tier;
        BlockState state = this.stateDefinition.any();
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    public CableTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(new BooleanProperty[0]));
    }

    private static boolean connects(LevelReader level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        if (level.getBlockState(other).getBlock() instanceof CableBlock) {
            return true;
        }
        return level instanceof Level real && real.getCapability(Capabilities.Energy.BLOCK, other, side.getOpposite()) != null;
    }

    private BlockState connections(LevelReader level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), connects(level, pos, d));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connects(level, pos, direction));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction d : Direction.values()) {
            if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) {
                shape = Shapes.or(shape, ARMS.get(d));
            }
        }
        return shape;
    }

    /** A neighbour's capabilities can change without a block change (e.g. a machine's side configuration). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        BlockState updated = connections(level, pos, state);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
            CableNetwork.invalidate(level);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            CableNetwork.invalidate(level);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        CableNetwork.invalidate(level);
    }
}
