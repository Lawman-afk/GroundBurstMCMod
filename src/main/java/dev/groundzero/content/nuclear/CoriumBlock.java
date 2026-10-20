package dev.groundzero.content.nuclear;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Corium (DESIGN.md §7, meltdown): the molten remains of a melted-down reactor core. It glows, smokes, burns whatever
 * stands on it, sets flammable things next to it alight, and over about ten minutes cools into Cooled Corium. Both are
 * {@code groundzero:radioactive} (the radiation field of Phase 12 will make them emit).
 */
public class CoriumBlock extends Block {
    /** Chance a random tick cools it (random ticks come about every minute per block at the default rate). */
    private static final float COOL_CHANCE = 0.1f;
    private final Supplier<? extends Block> cooled;

    public CoriumBlock(Properties properties, Supplier<? extends Block> cooled) {
        super(properties);
        this.cooled = cooled;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
        if (entity instanceof LivingEntity living && !living.fireImmune() && level instanceof ServerLevel server) {
            living.hurtServer(server, level.damageSources().hotFloor(), 3.0f);
            living.igniteForSeconds(4);
        }
        super.stepOn(level, pos, onState, entity);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < COOL_CHANCE) {
            level.setBlockAndUpdate(pos, this.cooled.get().defaultBlockState());
            level.levelEvent(1501, pos, 0);                // the hiss of lava cooling
            return;
        }
        // Like lava, it lights flammable things beside it.
        BlockPos at = pos.offset(random.nextInt(3) - 1, random.nextInt(2), random.nextInt(3) - 1);
        if (level.isEmptyBlock(at) && BaseFireBlock.canBePlacedAt(level, at, Direction.UP)) {
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (level.getBlockState(n).isFlammable(level, n, d.getOpposite())) {
                    level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
                    return;
                }
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0 && level.isEmptyBlock(pos.above())) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                    0, 0.04, 0);
        }
        if (random.nextInt(12) == 0 && level.isEmptyBlock(pos.above())) {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
