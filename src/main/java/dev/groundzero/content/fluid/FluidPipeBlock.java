package dev.groundzero.content.fluid;

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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A steel fluid pipe. Each side is one of (DESIGN.md §6.4, set with the Wrench like Mekanism's configurator or
 * Pipez's wrench):
 * <ul>
 * <li>{@code none}: nothing to connect to;</li>
 * <li>{@code normal}: connected both ways: whatever a machine pushes in travels on, and fluid on the network is
 *     delivered into it;</li>
 * <li>{@code pull}: the pipe draws fluid out of it (a tank, a machine's product tank) and sends it along the network,
 *     never back into it;</li>
 * <li>{@code disabled}: switched off with the Wrench and remembered, even when something connectable is there.</li>
 * </ul>
 * Runs of connected pipe form a {@link FluidPipeNetwork}.
 */
public class FluidPipeBlock extends Block {
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
    /** Ticks between pulls (each pulling side draws up to the tier's per-tick rate each time, so it pulls at full rate). */
    public static final int PULL_INTERVAL = 1;
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final VoxelShape CORE = Block.box(4.5, 4.5, 4.5, 11.5, 11.5, 11.5);

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
    }

    private final FluidPipeTier tier;

    public FluidPipeBlock(Properties properties, FluidPipeTier tier) {
        super(properties);
        this.tier = tier;
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

    public FluidPipeTier tier() {
        return this.tier;
    }

    public static Mode mode(BlockState state, Direction side) {
        return state.getValue(SIDES.get(side));
    }

    /** The arm box on a side (for the Wrench to tell which side was clicked). */
    public static VoxelShape arm(Direction side) {
        return ARMS.get(side);
    }

    /** Whether something on that side could connect: another pipe whose facing side is not switched off, or a fluid handler. */
    private static boolean connectable(LevelReader level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        BlockState there = level.getBlockState(other);
        if (there.getBlock() instanceof FluidPipeBlock) {
            return mode(there, side.getOpposite()) != Mode.DISABLED;
        }
        return level instanceof Level real && real.getCapability(Capabilities.Fluid.BLOCK, other, side.getOpposite()) != null;
    }

    /** A side's mode after its neighbour changed: switched-off stays off, pull stays pull while there is something to pull from. */
    private static Mode refresh(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        Mode current = mode(state, side);
        if (current == Mode.DISABLED) {
            return Mode.DISABLED;
        }
        if (!connectable(level, pos, side)) {
            return Mode.NONE;
        }
        boolean pipe = level.getBlockState(pos.relative(side)).getBlock() instanceof FluidPipeBlock;
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
        VoxelShape shape = CORE;
        for (Direction d : Direction.values()) {
            if (mode(state, d).connected()) {
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
        FluidPipeNetwork.invalidate(level);
    }

    /** Connections changed: rebuild networks and keep pulling sides ticking. */
    public static void changed(Level level, BlockPos pos, BlockState state) {
        FluidPipeNetwork.invalidate(level);
        level.invalidateCapabilities(pos);
        if (!level.isClientSide() && pulls(state)) {
            level.scheduleTick(pos, state.getBlock(), PULL_INTERVAL);
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
            if (mode(state, d) != Mode.PULL) {
                continue;
            }
            BlockPos source = pos.relative(d);
            ResourceHandler<FluidResource> from = level.getCapability(Capabilities.Fluid.BLOCK, source, d.getOpposite());
            if (from != null) {
                ResourceHandlerUtil.move(from, FluidPipeNetwork.handler(level, pos, d), r -> true, this.tier.rate(), null);
            }
        }
        level.scheduleTick(pos, this, PULL_INTERVAL);
    }
}
