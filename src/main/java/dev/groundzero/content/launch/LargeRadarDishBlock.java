package dev.groundzero.content.launch;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Large Radar Dish (DESIGN.md §17.7): one item that places a 3x3 dish lying face up, the Radar Array's crown. The
 * middle block draws the whole dish; the eight round it are parts of it (drawn by the middle, each solid where it
 * stands). Breaking any of them takes the whole dish (one item back); lose a part to a blast and the rest goes too.
 * A multiblock part: invisible while its structure is formed.
 */
public class LargeRadarDishBlock extends MultiblockPartBlock {
    public enum Part implements StringRepresentable {
        CENTER, EDGE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public LargeRadarDishBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, Part.CENTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Placed at its middle: only if the 3x3 round it is free. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos c = context.getClickedPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                if ((dx != 0 || dz != 0) && !level.getBlockState(p).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        level.setBlock(pos.offset(dx, 0, dz), defaultBlockState().setValue(PART, Part.EDGE), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /** The dish's middle block next to an edge part (every part has it among the 8 blocks round it). */
    private @Nullable BlockPos centerOf(BlockGetter level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = pos.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                if ((dx != 0 || dz != 0) && s.is(this) && s.getValue(PART) == Part.CENTER) {
                    return p;
                }
            }
        }
        return null;
    }

    private boolean complete(BlockGetter level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState s = level.getBlockState(center.offset(dx, 0, dz));
                if ((dx != 0 || dz != 0) && !(s.is(this) && s.getValue(PART) == Part.EDGE)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Breaking a part breaks the dish: the middle drops the item (unless in creative), the rest go quietly. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(PART) == Part.EDGE) {
            BlockPos c = centerOf(level, pos);
            if (c != null) {
                level.destroyBlock(c, !player.preventsBlockDrops(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        // Only a piece of the dish going (not one being put in place, as a structure is laid out) breaks it up.
        boolean lost = direction.getAxis().isHorizontal() && !neighborState.is(this);
        if (lost && state.getValue(PART) == Part.EDGE) {
            if (centerOf(level, pos) == null) {
                return Blocks.AIR.defaultBlockState();       // its middle is gone: so is it
            }
        } else if (lost && !complete(level, pos)) {
            ticks.scheduleTick(pos, this, 1);                // a part was lost: the dish falls apart (dropping itself)
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) == Part.CENTER && !complete(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    /** The middle draws the whole dish; the parts round it draw nothing of their own. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == Part.EDGE ? RenderShape.INVISIBLE : super.getRenderShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? super.getShape(state, level, pos, context) : SHAPE;
    }
}
