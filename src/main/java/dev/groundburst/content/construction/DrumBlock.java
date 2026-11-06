package dev.groundburst.content.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A steel drum (DESIGN.md §22.6: the Oil Drum, the Fuel Drum, the Rusty Drum): up to four stand in a block, as candles
 * do (another put on it adds one; {@link #DRUMS}), set out as tools/assets/yard.py lays them, turned to their facing.
 */
public class DrumBlock extends HorizontalDirectionalBlock {
    @Override
    protected com.mojang.serialization.MapCodec<? extends DrumBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    public static final IntegerProperty DRUMS = IntegerProperty.create("drums", 1, 4);
    /** Where each drum stands (block pixels, facing north) for one to four: yard.py's DRUM_SPOTS. */
    private static final double[][][] SPOTS = {{{8, 8}}, {{4.1, 8}, {11.9, 8}}, {{4.1, 4.2}, {11.9, 4.2}, {8, 11.8}},
            {{4.1, 4.1}, {11.9, 4.1}, {4.1, 11.9}, {11.9, 11.9}}};
    private static final double R = 3.9, H = 12.5;
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][4];

    static {
        for (int n = 0; n < 4; n++) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (double[] s : SPOTS[n]) {
                    shape = Shapes.or(shape, BlastDoorBlock.box(facing, false, s[0] - R, 0, s[1] - R, s[0] + R, H, s[1] + R));
                }
                SHAPES[n][facing.get2DDataValue()] = shape;
            }
        }
    }

    public DrumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DRUMS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DRUMS);
    }

    /** On a block of these drums, one more; else one, facing its placer. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.is(this)) {
            return here.setValue(DRUMS, Math.min(4, here.getValue(DRUMS) + 1));
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(DRUMS) < 4
                || super.canBeReplaced(state, context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(DRUMS) - 1][state.getValue(FACING).get2DDataValue()];
    }
}
