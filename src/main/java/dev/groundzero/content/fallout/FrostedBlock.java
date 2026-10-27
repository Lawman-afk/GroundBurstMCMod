package dev.groundzero.content.fallout;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ground a cryo burst froze (DESIGN.md §21.7, slice 12e): stone or grass furred with rime. Where it is not cold
 * enough for snow to lie, it thaws back now and then ({@link #THAW_CHANCE} a random tick, about a quarter of an hour on
 * average); in snowy places it stays frosted.
 */
public class FrostedBlock extends Block {
    public static final float THAW_CHANCE = 1f / 12;
    private final Supplier<Block> thawsTo;

    public FrostedBlock(Properties properties, Supplier<Block> thawsTo) {
        super(properties);
        this.thawsTo = thawsTo;
    }

    /** Whether frost holds at {@code pos} (a biome cold enough for snow to lie). */
    public static boolean cold(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < THAW_CHANCE && !cold(level, pos.above())) {
            level.setBlockAndUpdate(pos, this.thawsTo.get().defaultBlockState());
        }
    }
}
