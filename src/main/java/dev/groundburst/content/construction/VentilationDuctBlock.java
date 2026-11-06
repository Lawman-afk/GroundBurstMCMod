package dev.groundburst.content.construction;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Ventilation Duct (DESIGN.md §22.5): square galvanised duct that joins its neighbours on all six sides, and a Vent
 * Grille whose back is to it: a core, and an arm toward each it joins.
 */
public class VentilationDuctBlock extends Block {
    private static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        VoxelShape core = Block.box(3, 3, 3, 13, 13, 13);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, switch (d) {
                        case NORTH -> Block.box(3, 3, 0, 13, 13, 3);
                        case SOUTH -> Block.box(3, 3, 13, 13, 13, 16);
                        case WEST -> Block.box(0, 3, 3, 3, 13, 13);
                        case EAST -> Block.box(13, 3, 3, 16, 13, 13);
                        case UP -> Block.box(3, 13, 3, 13, 16, 13);
                        case DOWN -> Block.box(3, 0, 3, 13, 3, 13);
                    });
                }
            }
            SHAPES[mask] = shape;
        }
    }

    public VentilationDuctBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    /** Duct joins duct, and a grille whose back is to it. */
    private boolean joins(BlockState neighbour, Direction toward) {
        return neighbour.is(this) || neighbour.getBlock() instanceof VentGrilleBlock && neighbour.getValue(DirectionalBlock.FACING) == toward;
    }

    /** Its six sides joined to whatever stands round it now. */
    public BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(SIDES.get(d), joins(level.getBlockState(pos.relative(d)), d));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joined(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return state.setValue(SIDES.get(direction), joins(neighbor, direction));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(SIDES.get(d))) {
                mask |= 1 << d.ordinal();
            }
        }
        return SHAPES[mask];
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(SIDES.get(rotation.rotate(d)), state.getValue(SIDES.get(d)));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(SIDES.get(mirror.mirror(d)), state.getValue(SIDES.get(d)));
        }
        return out;
    }
}
