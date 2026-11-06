package dev.groundburst.content.construction;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Blast Door (DESIGN.md §22.3): a steel leaf on two hinges over a doorway two blocks wide and three high, its frame
 * lining the doorway, locked by three bolts its handwheels drive out into the frame. A click on any of its blocks sets it
 * opening or shutting, as does a redstone signal coming on or going off at any of them: the wheel draws the bolts and the
 * leaf swings out to the front; shutting, the leaf swings home and the wheel throws the bolts.
 *
 * <p>Its blocks: the doorway's six ({@link #U} 0 the hinges' column, 1 the other; {@link #V} up; {@link #W} 0), the hinges'
 * lowest holding the block entity that draws the door and moves it; and, while it stands open, six more in front of the
 * hinges' column where the leaf stands ({@link #W} 1 and 2), laid as it starts to open (it opens only if their places are
 * free) and taken away when it has shut. One item lays the doorway, with the leaf reaching to its placer's right (sneaking:
 * to the left; either way where there is room); breaking any block takes the whole door, one item back.
 *
 * <p>{@link #HINGE} is the side its hinges are on as seen from the front ({@link #FACING}: the way the front looks, the
 * leaf's swing). Shut, a doorway block is the leaf across its front and the frame behind; open, the frame alone.
 */
public class BlastDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    @Override
    protected com.mojang.serialization.MapCodec<? extends BlastDoorBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final IntegerProperty U = IntegerProperty.create("u", 0, 1);
    public static final IntegerProperty V = IntegerProperty.create("v", 0, 2);
    public static final IntegerProperty W = IntegerProperty.create("w", 0, 2);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape[] SHAPES = new VoxelShape[4 * 2 * 2 * 3 * 3 * 2];
    private final Supplier<? extends BlockEntityType<?>> type;

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int m = 0; m < 2; m++) {
                for (int u = 0; u < 2; u++) {
                    for (int v = 0; v < 3; v++) {
                        for (int w = 0; w < 3; w++) {
                            for (int o = 0; o < 2; o++) {
                                SHAPES[index(facing, m == 1, u, v, w, o == 1)] = shape(facing, m == 1, u, v, w, o == 1);
                            }
                        }
                    }
                }
            }
        }
    }

    public BlastDoorBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HINGE, DoorHingeSide.LEFT).setValue(U, 0)
                .setValue(V, 0).setValue(W, 0).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HINGE, U, V, W, OPEN);
    }

    // --- where its blocks are -------------------------------------------------------------------------------------------------------
    /** The way across the doorway from the hinges' column to the other. */
    public static Direction across(Direction facing, DoorHingeSide hinge) {
        return hinge == DoorHingeSide.RIGHT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    public static Direction across(BlockState state) {
        return across(state.getValue(FACING), state.getValue(HINGE));
    }

    /** The model's hand: +1 as drawn (hinges on the right, seen from the front), -1 mirrored. */
    public static int hand(BlockState state) {
        return state.getValue(HINGE) == DoorHingeSide.RIGHT ? 1 : -1;
    }

    /** The block {@code (u, v, w)} of the door whose hinges' lowest block is {@code base}. */
    public static BlockPos cell(BlockPos base, Direction facing, Direction across, int u, int v, int w) {
        return base.relative(across, u).above(v).relative(facing, w);
    }

    public static boolean isController(BlockState state) {
        return state.getValue(U) == 0 && state.getValue(V) == 0 && state.getValue(W) == 0;
    }

    /** The hinges' lowest block (the block entity's), from any of the door's blocks, or null if it is not there. */
    public @Nullable BlockPos controllerOf(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos base = pos.relative(across(state), -state.getValue(U)).below(state.getValue(V)).relative(facing, -state.getValue(W));
        BlockState s = level.getBlockState(base);
        return s.is(this) && isController(s) && s.getValue(FACING) == facing && s.getValue(HINGE) == state.getValue(HINGE) ? base : null;
    }

    // --- placing and breaking ------------------------------------------------------------------------------------------------------
    /** Its front toward its placer, the leaf reaching to the placer's right (sneaking: left), or the other way if only that is free. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        DoorHingeSide first = context.isSecondaryUseActive() ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
        for (DoorHingeSide hinge : new DoorHingeSide[] {first, first == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT}) {
            if (free(context, facing, hinge)) {
                return defaultBlockState().setValue(FACING, facing).setValue(HINGE, hinge);
            }
        }
        return null;
    }

    private static boolean free(BlockPlaceContext context, Direction facing, DoorHingeSide hinge) {
        Level level = context.getLevel();
        BlockPos base = context.getClickedPos();
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 3; v++) {
                BlockPos p = cell(base, facing, across(facing, hinge), u, v, 0);
                if (!p.equals(base) && (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 3; v++) {
                if (u > 0 || v > 0) {
                    level.setBlock(cell(pos, state.getValue(FACING), across(state), u, v, 0), state.setValue(U, u).setValue(V, v), Block.UPDATE_ALL);
                }
            }
        }
        if (level.getBlockEntity(pos) instanceof BlastDoorBlockEntity door && level instanceof ServerLevel server) {
            door.lookAtPower(server);
        }
    }

    /** A player breaking any block breaks the hinges' lowest (it drops the item, unless in creative); the rest follow it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !isController(state)) {
            BlockPos c = controllerOf(level, pos, state);
            if (c != null) {
                level.destroyBlock(c, !player.preventsBlockDrops(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A block gone however it went (a player, a blast, the door shutting): the rest are told to look again. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos c = isController(state) ? pos : controllerOf(level, pos, state);
        if (c == null) {
            return;
        }
        Direction facing = state.getValue(FACING), across = across(state);
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 3; v++) {
                for (int w = 0; w < 3; w++) {
                    BlockPos p = cell(c, facing, across, u, v, w);
                    if (level.getBlockState(p).is(this)) {
                        level.scheduleTick(p, this, 1);
                    }
                }
            }
        }
    }

    /**
     * Looking again: a block whose door has lost its hinges' lowest goes; a door missing a doorway block, or (open or
     * opening) a block of its leaf, falls apart, its hinges' lowest dropping the one item.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos c = controllerOf(level, pos, state);
        if (c == null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (isController(state) && !complete(level, pos, state)) {
            level.destroyBlock(pos, true);
        }
    }

    private boolean complete(ServerLevel level, BlockPos base, BlockState state) {
        boolean swing = level.getBlockEntity(base) instanceof BlastDoorBlockEntity door && door.needsSwing();
        Direction facing = state.getValue(FACING), across = across(state);
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 3; v++) {
                for (int w = 0; w < 3; w++) {
                    if (w > 0 && (u > 0 || !swing)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(cell(base, facing, across, u, v, w));
                    if (!s.is(this) || s.getValue(FACING) != facing || s.getValue(HINGE) != state.getValue(HINGE) || s.getValue(U) != u
                            || s.getValue(V) != v || s.getValue(W) != w) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // --- the leaf's blocks while it stands open ----------------------------------------------------------------------------------
    /** Whether the six places in front of the hinges' column, where the leaf will stand, are free (or already its own). */
    public boolean swingFree(Level level, BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING), across = across(state);
        for (int v = 0; v < 3; v++) {
            for (int w = 1; w < 3; w++) {
                BlockPos p = cell(base, facing, across, 0, v, w);
                BlockState s = level.getBlockState(p);
                if (!s.is(this) && (level.isOutsideBuildHeight(p) || !s.canBeReplaced())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Lays the leaf's six blocks in front of the hinges' column (their places free: {@link #swingFree}). */
    public void laySwing(Level level, BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING), across = across(state);
        for (int v = 0; v < 3; v++) {
            for (int w = 1; w < 3; w++) {
                BlockPos p = cell(base, facing, across, 0, v, w);
                if (!level.getBlockState(p).is(this)) {
                    level.setBlock(p, defaultBlockState().setValue(FACING, facing).setValue(HINGE, state.getValue(HINGE)).setValue(V, v)
                            .setValue(W, w).setValue(OPEN, true), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Takes the leaf's six blocks away again (it has shut). */
    public void clearSwing(Level level, BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING), across = across(state);
        for (int v = 0; v < 3; v++) {
            for (int w = 1; w < 3; w++) {
                BlockPos p = cell(base, facing, across, 0, v, w);
                BlockState s = level.getBlockState(p);
                if (s.is(this) && s.getValue(W) == w) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** The doorway's six blocks told whether the leaf stands clear of it (their shapes: the frame alone, or the leaf too). */
    public void setOpen(Level level, BlockPos base, BlockState state, boolean open) {
        Direction facing = state.getValue(FACING), across = across(state);
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 3; v++) {
                BlockPos p = cell(base, facing, across, u, v, 0);
                BlockState s = level.getBlockState(p);
                if (s.is(this) && s.getValue(OPEN) != open) {
                    level.setBlock(p, s.setValue(OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }

    // --- using it -------------------------------------------------------------------------------------------------------------------
    /** A click on any of its blocks: the hinges' lowest opens it or shuts it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            BlockPos c = controllerOf(level, pos, state);
            if (c != null && level.getBlockEntity(c) instanceof BlastDoorBlockEntity door) {
                door.toggle(server, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel server) {
            BlockPos c = controllerOf(level, pos, state);
            if (c != null && level.getBlockEntity(c) instanceof BlastDoorBlockEntity door) {
                door.lookAtPower(server);
            }
        }
    }

    /** A redstone signal at any of its doorway's blocks. */
    public static boolean powered(Level level, BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING), across = across(state);
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 3; v++) {
                if (level.hasNeighborSignal(cell(base, facing, across, u, v, 0))) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- shape ------------------------------------------------------------------------------------------------------------------------
    private static int index(Direction facing, boolean mirrored, int u, int v, int w, boolean open) {
        return ((((facing.get2DDataValue() * 2 + (mirrored ? 1 : 0)) * 2 + u) * 3 + v) * 3 + w) * 2 + (open ? 1 : 0);
    }

    /**
     * A block's shape, worked out as the door stands facing north with its hinges on the right (the model's frame: the leaf
     * across the front four pixels of a doorway block, the frame's jambs three pixels wide behind it at the doorway's
     * sides and its lintel three high across the top; open, the leaf standing out in front of the hinges' column, from
     * x 4.5 to 10), then mirrored and turned to the door's.
     */
    private static VoxelShape shape(Direction facing, boolean mirrored, int u, int v, int w, boolean open) {
        if (w > 0) {
            double top = v == 2 ? 14.5 : 16;
            return u > 0 ? Shapes.empty() : w == 1 ? box(facing, mirrored, 4.5, 0, 0, 10, top, 12) : box(facing, mirrored, 4.5, 0, 0.4, 10, top, 16);
        }
        VoxelShape frame = u == 0 ? box(facing, mirrored, 0, 0, 4, 3, 16, 16) : box(facing, mirrored, 13, 0, 4, 16, 16, 16);
        if (v == 2) {
            frame = Shapes.or(frame, box(facing, mirrored, 0, 13, 4, 16, 16, 16));
        }
        return open ? frame : Shapes.or(frame, box(facing, mirrored, 0, 0, 0, 16, 16, 4));
    }

    /** A box in the door's own frame (pixels; facing north, hinges on the right), mirrored and turned to this state's. */
    static VoxelShape box(Direction facing, boolean mirrored, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (mirrored) {
            double a = 16 - x1;
            x1 = 16 - x0;
            x0 = a;
        }
        double[] p = turn(facing, x0, z0), q = turn(facing, x1, z1);
        return Block.box(Math.min(p[0], q[0]), y0, Math.min(p[1], q[1]), Math.max(p[0], q[0]), y1, Math.max(p[1], q[1]));
    }

    /** A point (x, z) of a block turned from facing north to facing {@code facing}, as the renderer turns the model. */
    private static double[] turn(Direction facing, double x, double z) {
        return switch (facing) {
            case SOUTH -> new double[] {16 - x, 16 - z};
            case EAST -> new double[] {16 - z, x};
            case WEST -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[index(state.getValue(FACING), state.getValue(HINGE) == DoorHingeSide.LEFT, state.getValue(U), state.getValue(V),
                state.getValue(W), state.getValue(OPEN))];
    }

    /** Mobs walk through its doorway while it stands open, never through its leaf. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && state.getValue(W) == 0 && state.getValue(OPEN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;                            // the hinges' lowest block entity draws the whole door
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? this.type.get().create(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !isController(state) || type != this.type.get()) {
            return null;
        }
        return (l, p, s, be) -> ((BlastDoorBlockEntity) be).serverTick((ServerLevel) l);
    }
}
