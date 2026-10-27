package dev.groundzero.content.fallout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ice crystals a cryo burst grows round its middle (DESIGN.md §16.4, §21.7, slice 12e): clusters of pale blue needles
 * on any face, glowing faintly. They melt away where it is not cold enough for snow ({@link #MELT_CHANCE} a random
 * tick); with silk touch they can be kept.
 */
public class CryoCrystalBlock extends AmethystClusterBlock {
    public static final float MELT_CHANCE = 1f / 10;

    public CryoCrystalBlock(Properties properties) {
        super(7, 3, properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < MELT_CHANCE && !FrostedBlock.cold(level, pos)) {
            level.removeBlock(pos, false);
        }
    }
}
