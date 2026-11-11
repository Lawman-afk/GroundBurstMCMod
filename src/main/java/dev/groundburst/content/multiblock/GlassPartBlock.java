package dev.groundburst.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A see-through structural block (reactor glass): neighbouring panes of the same block merge like vanilla glass. */
public class GlassPartBlock extends MultiblockPartBlock {
    public GlassPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.is(this) || super.skipRendering(state, neighborState, direction);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level_, BlockPos pos_) {
        return true;
    }
}
