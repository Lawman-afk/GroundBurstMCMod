package dev.groundburst.content.construction;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of furniture facing one of four ways, its front toward its placer (DESIGN.md §22.4: the Control Console, the
 * Command Desk): its shape the boxes it is given as it stands facing north (block pixels), turned to its facing.
 */
public class DecorBlock extends HorizontalDirectionalBlock {
    @Override
    protected com.mojang.serialization.MapCodec<? extends DecorBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    private final VoxelShape[] shapes = new VoxelShape[4];

    /** {@code boxes}: x0, y0, z0, x1, y1, z1 each, in block pixels, facing north. */
    public DecorBlock(Properties properties, List<double[]> boxes) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, BlastDoorBlock.box(facing, false, b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            this.shapes[facing.get2DDataValue()] = shape;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
