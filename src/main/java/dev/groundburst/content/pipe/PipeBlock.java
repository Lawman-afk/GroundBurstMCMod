package dev.groundburst.content.pipe;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pipe (fluid or item): a core with an arm to each connected side. Each side is one of (DESIGN.md §6.4, set with the
 * Wrench like Mekanism's configurator or Pipez's wrench):
 * <ul>
 * <li>{@code none}: nothing to connect to;</li>
 * <li>{@code normal}: connected both ways: whatever is pushed in travels on, and the network delivers into it;</li>
 * <li>{@code pull}: the pipe draws out of the block there and sends it along the network, never back into it;</li>
 * <li>{@code disabled}: switched off with the Wrench and remembered, even when something connectable is there.</li>
 * </ul>
 * Pipes only join pipes of their own kind ({@link #isSameKind}). Subclasses say what they connect to, which network
 * they belong to and how a pull side draws.
 */
public abstract class PipeBlock extends Block {
    public enum Mode implements StringRepresentable {
        NONE, NORMAL, PULL, DISABLED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean connected() {
            return this == NORMAL || this == PULL;
        }
    }

    public static final Map<Direction, EnumProperty<Mode>> SIDES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final VoxelShape CORE = Block.box(4.5, 4.5, 4.5, 11.5, 11.5, 11.5);
    /** Outline and collision for every combination of connected sides (bit i = Direction i), built once. */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (Direction d : Direction.values()) {
            SIDES.put(d, EnumProperty.create(d.getSerializedName(), Mode.class));
        }
        ARMS.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
        ARMS.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
        ARMS.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
        ARMS.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
        ARMS.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
        ARMS.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
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

    protected PipeBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any();
        for (EnumProperty<Mode> p : SIDES.values()) {
            state = state.setValue(p, Mode.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES.values().toArray(new EnumProperty[0]));
    }

    // --- what a kind of pipe connects to and does ----------------------------------------------------------------------------
    /** Whether {@code state} is a pipe this one joins (same kind, any tier). */
    public abstract boolean isSameKind(BlockState state);

    /** Whether the block at {@code pos} offers this pipe's kind of handler on {@code side}. */
    protected abstract boolean hasHandler(Level level, BlockPos pos, Direction side);

    /** Connections changed somewhere: forget the cached networks of this kind. */
    protected abstract void invalidateNetworks(Level level);

    /** Ticks between pulls. */
    protected abstract int pullInterval();

    /** A pull side draws from what it touches ({@code side} of this pipe) and passes it along the network. */
    protected abstract void pull(ServerLevel level, BlockPos pos, Direction side);

    // --- shared behaviour ----------------------------------------------------------------------------------------------------
    public static Mode mode(BlockState state, Direction side) {
        return state.getValue(SIDES.get(side));
    }

    /** The arm box on a side (for the Wrench to tell which side was clicked). */
    public static VoxelShape arm(Direction side) {
        return ARMS.get(side);
    }

    /** Whether something on that side could connect: a pipe of this kind whose facing side is not switched off, or a handler. */
    private boolean connectable(LevelReader level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        BlockState there = level.getBlockState(other);
        if (there.getBlock() instanceof PipeBlock) {
            return isSameKind(there) && mode(there, side.getOpposite()) != Mode.DISABLED;
        }
        return level instanceof Level real && hasHandler(real, other, side.getOpposite());
    }

    /** A side's mode after its neighbour changed: switched-off stays off, pull stays pull while there is something to pull from. */
    private Mode refresh(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        Mode current = mode(state, side);
        if (current == Mode.DISABLED) {
            return Mode.DISABLED;
        }
        if (!connectable(level, pos, side)) {
            return Mode.NONE;
        }
        boolean pipe = level.getBlockState(pos.relative(side)).getBlock() instanceof PipeBlock;
        return current == Mode.PULL && !pipe ? Mode.PULL : Mode.NORMAL;
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
            if (mode(state, d).connected()) {
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
            changed(level, pos, updated);
        }
    }

    /** However it was placed (by a player, a command, a structure), work out its connections. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            BlockState connected = connections(level, pos, state);
            if (connected != state) {
                level.setBlock(pos, connected, Block.UPDATE_ALL);
                return;                                    // the new state's own onPlace finishes the job
            }
        }
        changed(level, pos, state);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        invalidateNetworks(level);
    }

    /** Connections changed: rebuild networks and keep pulling sides ticking. */
    public static void changed(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PipeBlock pipe)) {
            return;
        }
        pipe.invalidateNetworks(level);
        level.invalidateCapabilities(pos);
        if (!level.isClientSide() && pulls(state)) {
            level.scheduleTick(pos, pipe, pipe.pullInterval());
        }
    }

    private static boolean pulls(BlockState state) {
        for (Direction d : Direction.values()) {
            if (mode(state, d) == Mode.PULL) {
                return true;
            }
        }
        return false;
    }

    /** Pulling sides draw from what they touch and pass it along the network (never back where it came from). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!pulls(state)) {
            return;
        }
        for (Direction d : Direction.values()) {
            if (mode(state, d) == Mode.PULL) {
                pull(level, pos, d);
            }
        }
        level.scheduleTick(pos, this, pullInterval());
    }
}
