package dev.groundburst.content.resource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Budding voidstone: slowly grows exotic crystals on its open faces (small bud -> large bud -> cluster), like
 * budding amethyst but slower — exotic matter should stay precious. Cannot be moved or picked up.
 */
public class BuddingVoidstoneBlock extends Block {
    private static final Direction[] DIRECTIONS = Direction.values();
    /** 1-in-N chance per random tick (budding amethyst is 1 in 5). */
    private static final int GROWTH_CHANCE = 12;

    public BuddingVoidstoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) != 0) {
            return;
        }
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos growPos = pos.relative(direction);
        BlockState target = level.getBlockState(growPos);
        Block next = null;
        if (BuddingAmethystBlock.canClusterGrowAtState(target)) {
            next = GBResources.SMALL_EXOTIC_BUD.block().get();
        } else if (target.is(GBResources.SMALL_EXOTIC_BUD.block().get()) && target.getValue(AmethystClusterBlock.FACING) == direction) {
            next = GBResources.LARGE_EXOTIC_BUD.block().get();
        } else if (target.is(GBResources.LARGE_EXOTIC_BUD.block().get()) && target.getValue(AmethystClusterBlock.FACING) == direction) {
            next = GBResources.EXOTIC_CRYSTAL_CLUSTER.block().get();
        }
        if (next != null) {
            level.setBlockAndUpdate(growPos, next.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, direction)
                    .setValue(AmethystClusterBlock.WATERLOGGED, target.getFluidState().is(Fluids.WATER)));
        }
    }
}
