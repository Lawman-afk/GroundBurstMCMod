package dev.groundburst.content.construction;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Large Silo Door (DESIGN.md §22.2): two leaves over a 7x7 opening that slide apart onto rails, four blocks of rail
 * either side, opened by a redstone signal at any of its blocks. For show: nothing under it but what the builder digs.
 * One item lays all 15x7 blocks (in the layer over the ground, the leaves sliding across the way its placer faced);
 * breaking any block takes the whole door, one item back.
 *
 * Each block knows where it lies along the slide ({@link #U}: 0 at the west end when it faces north, 7 the middle) and
 * how far apart the leaves stand ({@link #GAP}: 0 shut .. 4 open, in whole blocks either side), so its shape needs no
 * look-up: an opening block is a leaf (half a block thick) where a leaf covers it and open where it does not; a rail
 * block is the rail bed, or a leaf resting on it. The middle block ({@link #CENTER}) holds the block entity that draws
 * the door and moves it.
 */
public class SiloDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final IntegerProperty U = IntegerProperty.create("u", 0, 14);
    public static final IntegerProperty GAP = IntegerProperty.create("gap", 0, 4);
    public static final BooleanProperty CENTER = BooleanProperty.create("center");
    /** Half the door's length along the slide, and half its width across. */
    public static final int HALF_LENGTH = 7, HALF_WIDTH = 3;
    private static final VoxelShape LEAF = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape RAIL = Block.box(0, 0, 0, 16, 3, 16);
    private final Supplier<? extends BlockEntityType<?>> type;

    public SiloDoorBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(U, HALF_LENGTH).setValue(GAP, 0)
                .setValue(CENTER, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, U, GAP, CENTER);
    }

    /** The way the leaves slide (U grows that way): to the right of the way it faces. */
    public static Direction slide(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    /** Where the block {@code (u, v)} of the door with its middle at {@code center} stands (v across, -3..3). */
    public static BlockPos cell(BlockPos center, Direction facing, int u, int v) {
        return center.relative(facing.getClockWise(), u - HALF_LENGTH).relative(facing, v);
    }

    // --- placing and breaking ------------------------------------------------------------------------------------------------------
    /** Placed at its middle, facing the way its placer looks: only if all its blocks' places are free. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos c = context.getClickedPos();
        Level level = context.getLevel();
        for (int u = 0; u <= 2 * HALF_LENGTH; u++) {
            for (int v = -HALF_WIDTH; v <= HALF_WIDTH; v++) {
                BlockPos p = cell(c, facing, u, v);
                if (!p.equals(c) && !level.getBlockState(p).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int u = 0; u <= 2 * HALF_LENGTH; u++) {
            for (int v = -HALF_WIDTH; v <= HALF_WIDTH; v++) {
                BlockPos p = cell(pos, facing, u, v);
                if (!p.equals(pos)) {
                    level.setBlock(p, state.setValue(U, u).setValue(CENTER, false), Block.UPDATE_ALL);
                }
            }
        }
        if (level.getBlockEntity(pos) instanceof SiloDoorBlockEntity door) {
            door.checkPower();
        }
    }

    /** The middle of the door a block belongs to (searched across the door at the middle of its length), or null. */
    public @Nullable BlockPos centerOf(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getValue(CENTER)) {
            return pos;
        }
        Direction facing = state.getValue(FACING);
        BlockPos mid = pos.relative(facing.getClockWise(), HALF_LENGTH - state.getValue(U));
        for (int v = -HALF_WIDTH; v <= HALF_WIDTH; v++) {
            BlockPos p = mid.relative(facing, v);
            BlockState s = level.getBlockState(p);
            if (s.is(this) && s.getValue(CENTER) && s.getValue(FACING) == facing) {
                return p;
            }
        }
        return null;
    }

    /** A player breaking any block breaks the middle (it drops the item, unless in creative); the rest follow it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !state.getValue(CENTER)) {
            BlockPos c = centerOf(level, pos, state);
            if (c != null) {
                level.destroyBlock(c, !player.preventsBlockDrops(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A block gone however it went (a player, a blast): the rest are told to look again (they go with it). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Direction facing = state.getValue(FACING);
        BlockPos c = state.getValue(CENTER) ? pos : centerOf(level, pos, state);
        if (c == null) {
            return;
        }
        for (int u = 0; u <= 2 * HALF_LENGTH; u++) {
            for (int v = -HALF_WIDTH; v <= HALF_WIDTH; v++) {
                BlockPos p = cell(c, facing, u, v);
                if (level.getBlockState(p).is(this)) {
                    level.scheduleTick(p, this, 1);
                }
            }
        }
    }

    /** Looking again: a door missing a block falls apart, its middle dropping the one item, the rest nothing. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos c = centerOf(level, pos, state);
        if (c == null) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (state.getValue(CENTER) && !complete(level, pos, state)) {
            level.destroyBlock(pos, true);
        }
    }

    private boolean complete(BlockGetter level, BlockPos center, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (int u = 0; u <= 2 * HALF_LENGTH; u++) {
            for (int v = -HALF_WIDTH; v <= HALF_WIDTH; v++) {
                BlockState s = level.getBlockState(cell(center, facing, u, v));
                if (!s.is(this) || s.getValue(FACING) != facing || s.getValue(U) != u) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- redstone -------------------------------------------------------------------------------------------------------------------
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel) {
            BlockPos c = centerOf(level, pos, state);
            if (c != null && level.getBlockEntity(c) instanceof SiloDoorBlockEntity door) {
                door.checkPower();
            }
        }
    }

    /** A redstone signal at any of the door's blocks. */
    public static boolean powered(Level level, BlockPos center, Direction facing) {
        for (int u = 0; u <= 2 * HALF_LENGTH; u++) {
            for (int v = -HALF_WIDTH; v <= HALF_WIDTH; v++) {
                if (level.hasNeighborSignal(cell(center, facing, u, v))) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- shape ------------------------------------------------------------------------------------------------------------------------
    /** Covered by a leaf (shut over the opening, or slid onto a rail): half a block of steel. */
    public static boolean leafOver(int u, int gap) {
        if (u < HALF_LENGTH - HALF_WIDTH) {                      // the rails one way
            return u >= HALF_LENGTH - HALF_WIDTH - gap;
        }
        if (u > HALF_LENGTH + HALF_WIDTH) {                      // and the other
            return u <= HALF_LENGTH + HALF_WIDTH + gap;
        }
        return Math.abs(u - HALF_LENGTH) >= gap;                // the opening, open in its middle as the leaves part
    }

    private static boolean rail(int u) {
        return u < HALF_LENGTH - HALF_WIDTH || u > HALF_LENGTH + HALF_WIDTH;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int u = state.getValue(U);
        if (leafOver(u, state.getValue(GAP))) {
            return LEAF;
        }
        return rail(u) ? RAIL : Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;                            // the middle's block entity draws the whole door
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CENTER) ? this.type.get().create(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(CENTER) || type != this.type.get()) {
            return null;
        }
        return (l, p, s, be) -> ((SiloDoorBlockEntity) be).serverTick((ServerLevel) l);
    }
}
