package dev.groundzero.content.launch;

import dev.groundzero.content.multiblock.SpreadPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Large Radar Dish (DESIGN.md §17.7): one item that places a 3x3 dish lying face up, the Radar Array's crown (see
 * {@link SpreadPartBlock}). Each block of it is 10 px tall where it stands.
 */
public class LargeRadarDishBlock extends SpreadPartBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public LargeRadarDishBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? super.getShape(state, level, pos, context) : SHAPE;
    }
}
