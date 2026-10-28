package dev.groundburst.content.fallout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Crops killed by a strong radiation field or a nuclear fire (DESIGN.md §21.8, slice 12b): dead stalks, vanilla's ripe
 * wheat drawn through a dead tint, on farmland or any earth. They give nothing, and crumble away in time
 * ({@link #CRUMBLE_CHANCE} a random tick).
 */
public class WitheredCropsBlock extends VegetationBlock {
    public static final float CRUMBLE_CHANCE = 1f / 30;
    private static final VoxelShape SHAPE = Block.column(16, 0, 10);

    public WitheredCropsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND) || state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS) || state.is(GBFallout.SCORCHED_EARTH.block().get());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < CRUMBLE_CHANCE) {
            level.removeBlock(pos, false);
        }
    }
}
