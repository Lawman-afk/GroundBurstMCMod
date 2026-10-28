package dev.groundburst.content.fallout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plant a cryo burst froze solid (DESIGN.md §21.7, slice 12e): a tuft of ice-glazed stems, vanilla's grass drawn
 * through an icy tint. It stands on any solid ground, gives nothing, and melts away once it is warm enough
 * ({@link #MELT_CHANCE} a random tick): the cold killed it.
 */
public class FrozenPlantBlock extends VegetationBlock {
    public static final float MELT_CHANCE = 1f / 8;
    private static final VoxelShape SHAPE = Block.column(12, 0, 13);

    public FrozenPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < MELT_CHANCE && !FrostedBlock.cold(level, pos)) {
            level.removeBlock(pos, false);
        }
    }
}
