package dev.groundburst.content.fallout;

import dev.groundburst.content.effect.GasAgent;
import dev.groundburst.content.effect.GasGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Earth soaked in anthrax (DESIGN.md §16.7, §21.7, slice 12e): where a heavy cloud of it lies on the ground, or a toxin
 * SCUD's residue settles, the soil turns. It kills what grows on it (crops wither), breathes a thin anthrax haze back
 * up now and then, and after about a day of that it is spent: coarse dirt ({@link #SPENT_CHANCE} a random tick).
 */
public class ToxicSoilBlock extends Block {
    /** A random tick's chance of the soil being spent (random ticks come about once a minute: about a day on average). */
    public static final float SPENT_CHANCE = 1f / 18;
    /** The haze it breathes up each random tick (the grid's full strength is 0.3). */
    public static final float BREATH = 0.05f;

    public ToxicSoilBlock(Properties properties) {
        super(properties);
    }

    /** Whether the gas or a residue can turn {@code state} into toxic soil. */
    public static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT) && !state.is(GBFallout.TOXIC_SOIL.block().get()) || state.is(BlockTags.GRASS_BLOCKS)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL) || state.is(Blocks.DIRT_PATH);
    }

    /** Turns the ground at {@code pos} (if it is soil) and kills what grows on it. */
    public static void taint(ServerLevel level, BlockPos pos) {
        if (soil(level.getBlockState(pos))) {
            kill(level, pos.above());                      // first, or a crop would pop off its farmland before it withers
            level.setBlockAndUpdate(pos, GBFallout.TOXIC_SOIL.block().get().defaultBlockState());
        }
    }

    /** What grows at {@code pos} dies: crops wither, other plants go. */
    static void kill(ServerLevel level, BlockPos pos) {
        BlockState plant = level.getBlockState(pos);
        if (plant.getBlock() instanceof CropBlock) {
            level.setBlockAndUpdate(pos, GBFallout.WITHERED_CROPS.block().get().defaultBlockState());
        } else if (plant.getBlock() instanceof VegetationBlock && !plant.is(GBFallout.WITHERED_CROPS.block().get())
                || plant.is(BlockTags.REPLACEABLE_BY_TREES) && !plant.isAir() && plant.getFluidState().isEmpty()) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        kill(level, above);
        if (GasGrid.passable(level, above)) {
            GasGrid.of(level).emit(level, above, GasAgent.ANTHRAX, BREATH);
        }
        if (random.nextFloat() < SPENT_CHANCE) {
            level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
        }
    }
}
