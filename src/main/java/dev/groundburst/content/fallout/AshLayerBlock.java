package dev.groundburst.content.fallout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ash lying on the ground in layers, one to eight, as snow does (DESIGN.md §21.7): where a nuclear fire burned the
 * ground and, from a plume, Fallout Ash ({@link FalloutAshBlock}). Unlike snow it does not melt in the light; shovelled
 * away it is gone (it drops nothing).
 */
public class AshLayerBlock extends SnowLayerBlock {
    public AshLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Ash stays (snow's random tick would melt it under a torch).
    }

    /** A state of this ash {@code layers} deep (1 to 8). */
    public BlockState layers(int layers) {
        return defaultBlockState().setValue(LAYERS, Math.max(1, Math.min(MAX_HEIGHT, layers)));
    }
}
