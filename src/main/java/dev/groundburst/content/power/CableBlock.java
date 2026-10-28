package dev.groundburst.content.power;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * A power cable. Connects to other cables and to anything exposing an energy handler on the facing side, unless that
 * side was switched off with the Wrench (DESIGN.md §7). Cables
 * store nothing: energy offered to a cable is delivered straight to the network's consumers (see {@link CableNetwork}).
 */
public class CableBlock extends Block {
    /** A cable side: nothing there, connected, or switched off with the Wrench (and remembered). */
    public enum Side implements StringRepresentable {
        NONE, CONNECTED, DISABLED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Map<Direction, EnumProperty<Side>> SIDES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final VoxelShape CORE = Block.box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5);
    /** Outline and collision for every combination of connected sides (bit i = Direction i), built once: the game asks
     * for a block's shape constantly (collision, raycasts, mobs walking past), so it must not be rebuilt each time. */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (Direction d : Direction.values()) {
            SIDES.put(d, EnumProperty.create(d.getSerializedName(), Side.class));
        }
        ARMS.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
        ARMS.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
        ARMS.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
        ARMS.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
        ARMS.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
        ARMS.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARMS.get(d));
                }
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    private final CableTier tier;

    public CableBlock(Properties properties, CableTier tier) {
        super(properties);
        this.tier = tier;
        BlockState state = this.stateDefinition.any();
        for (EnumProperty<Side> p : SIDES.values()) {
            state = state.setValue(p, Side.NONE);
        }
        registerDefaultState(state);
    }

    public CableTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES.values().toArray(new EnumProperty[0]));
    }

    public static Side side(BlockState state, Direction d) {
        return state.getValue(SIDES.get(d));
    }

    /** The arm box on a side (for the Wrench to tell which side was clicked). */
    public static VoxelShape arm(Direction side) {
        return ARMS.get(side);
    }

    private static boolean connectable(LevelReader level, BlockPos pos, Direction d) {
        BlockPos other = pos.relative(d);
        BlockState there = level.getBlockState(other);
        if (there.getBlock() instanceof CableBlock) {
            return side(there, d.getOpposite()) != Side.DISABLED;
        }
        return level instanceof Level real && real.getCapability(Capabilities.Energy.BLOCK, other, d.getOpposite()) != null;
    }

    private static Side refresh(LevelReader level, BlockPos pos, BlockState state, Direction d) {
        if (side(state, d) == Side.DISABLED) {
            return Side.DISABLED;
        }
        return connectable(level, pos, d) ? Side.CONNECTED : Side.NONE;
    }

    private BlockState connections(LevelReader level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) {
            state = state.setValue(SIDES.get(d), refresh(level, pos, state, d));
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
        return state.setValue(SIDES.get(direction), refresh(level, pos, state, direction));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (side(state, d) == Side.CONNECTED) {
                mask |= 1 << d.ordinal();
            }
        }
        return SHAPES[mask];
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

    /** However it was placed (by a player, a command, a structure), work out its connections. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            BlockState connected = connections(level, pos, state);
            if (connected != state) {
                level.setBlock(pos, connected, Block.UPDATE_ALL);
            }
            CableNetwork.invalidate(level);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        CableNetwork.invalidate(level);
    }
}
