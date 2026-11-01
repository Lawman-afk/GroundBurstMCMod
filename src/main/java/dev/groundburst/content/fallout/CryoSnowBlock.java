package dev.groundburst.content.fallout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Snow a Cryo ICBM's front laid over the land (DESIGN.md §16.16): vanilla's snow in every way (its layers, a lamp
 * melting it, snowballs from a shovel), but where it is not cold enough for snow to lie it thaws, a layer at a time
 * ({@link #THAW_CHANCE} a random tick: a layer goes in about seven minutes), so the land comes back from under it; in
 * snowy places it stays.
 */
public class CryoSnowBlock extends SnowLayerBlock {
    public static final float THAW_CHANCE = 1f / 6;

    public CryoSnowBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);              // a lamp melts it as it does vanilla's
        if (!level.getBlockState(pos).is(this) || random.nextFloat() >= THAW_CHANCE || FrostedBlock.cold(level, pos)) {
            return;
        }
        int layers = state.getValue(LAYERS);
        if (layers > 1) {
            level.setBlockAndUpdate(pos, state.setValue(LAYERS, layers - 1));
        } else {
            level.removeBlock(pos, false);
        }
    }
}
