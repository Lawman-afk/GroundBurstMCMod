package dev.groundburst.content.fallout;

import dev.groundburst.content.radiation.RadiationSource;
import dev.groundburst.content.radiation.RadiationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A grass block a strong radiation field has killed (DESIGN.md §21.8, slice 12b): vanilla's grass drawn through a dead
 * tint. It does not spread. Covered over it turns to dirt, as grass does; once its ground is safe again (under
 * {@link RadiationSource#SAFE}) and lit by day, now and then a random tick brings the grass back.
 */
public class DeadGrassBlock extends Block {
    /** A random tick's chance of the grass coming back, where it can (about 45 minutes on average). */
    public static final float REGROW_CHANCE = 1f / 40;

    public DeadGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        BlockState over = level.getBlockState(above);
        if (over.isSolidRender()) {
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            return;
        }
        if (random.nextFloat() < REGROW_CHANCE && level.getMaxLocalRawBrightness(above) >= 9
                && RadiationSources.of(level).rateAt(level, Vec3.atCenterOf(above)) < RadiationSource.SAFE) {
            level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
        }
    }
}
