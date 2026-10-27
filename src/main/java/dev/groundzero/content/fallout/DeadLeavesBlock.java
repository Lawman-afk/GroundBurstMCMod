package dev.groundzero.content.fallout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Leaves a strong radiation field has killed (DESIGN.md §21.8, slice 12b): vanilla's leaves drawn through a dead tint,
 * left on the branch for a while and then dropping away ({@link #DROP_CHANCE} a random tick: most are gone within an
 * hour or two), so the dead trees stand bare.
 */
public class DeadLeavesBlock extends Block {
    public static final float DROP_CHANCE = 1f / 40;

    public DeadLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < DROP_CHANCE) {
            level.removeBlock(pos, false);
        }
    }
}
