package dev.groundzero.content.fallout;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fallout Ash (DESIGN.md §21.8, slice 12b): the dust a fallout plume lays as it passes, in layers like snow. Weakly
 * radioactive (the {@code groundzero:radioactivity} data map), and it weathers: each random tick has a
 * {@link #WEATHER_CHANCE} chance of turning it into plain {@link AshLayerBlock} as deep, so most of it is plain ash
 * after a couple of hours.
 */
public class FalloutAshBlock extends AshLayerBlock {
    /** A random tick's chance of weathering (random ticks come about every 68 s a block at the default rate). */
    public static final float WEATHER_CHANCE = 1f / 80;
    private final Supplier<? extends Block> weathersTo;

    public FalloutAshBlock(Properties properties, Supplier<? extends Block> weathersTo) {
        super(properties);
        this.weathersTo = weathersTo;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < WEATHER_CHANCE) {
            weather(state, level, pos);
        }
    }

    /** Turns it into plain ash as deep as it is. */
    public void weather(BlockState state, ServerLevel level, BlockPos pos) {
        if (this.weathersTo.get() instanceof AshLayerBlock ash) {
            level.setBlock(pos, ash.layers(state.getValue(LAYERS)), Block.UPDATE_ALL);
        }
    }
}
