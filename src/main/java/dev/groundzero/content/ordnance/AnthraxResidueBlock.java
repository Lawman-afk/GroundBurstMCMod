package dev.groundzero.content.ordnance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The glowing residue a toxin SCUD spreads over the ground (DESIGN.md §19.5): the toxin grenade's sludge, but thicker,
 * brighter and far longer lasting (about eight minutes, drying out in four stages), bubbling and fuming, and harsher to
 * walk through. Like the sludge, anything can be built over it and it goes if the floor under it does.
 */
public class AnthraxResidueBlock extends ToxicSludgeBlock {
    /** Ticks a stage lasts (plus up to 30 s more, so a strike's residue dries out unevenly). */
    public static final int STAGE_TICKS = 2400;
    private static final DustParticleOptions FUME = new DustParticleOptions(0x7BE84A, 1.6f);
    private static final DustParticleOptions BUBBLE = new DustParticleOptions(0xD4FF96, 0.5f);

    public AnthraxResidueBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, STAGE_TICKS + level.getRandom().nextInt(600));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living && living.tickCount % 10 == 0) {
            Toxins.expose(server, living, 2.5f, 140, Toxins.TOXIN, Toxins.TOXIN_NAUSEA);
        }
    }

    /** Thick green fumes rolling off it and bubbles popping on it, both thinning as it dries. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (random.nextInt(1 + age) == 0) {
            level.addParticle(FUME, pos.getX() + random.nextDouble(), pos.getY() + 0.1, pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.01, 0.015 + random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.01);
        }
        if (random.nextInt(3 + age * 2) == 0) {
            double x = pos.getX() + 0.15 + random.nextDouble() * 0.7, z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
            level.addParticle(BUBBLE, x, pos.getY() + 0.08, z, 0, 0.04, 0);
            if (random.nextInt(24) == 0) {
                level.playLocalSound(x, pos.getY(), z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.25f,
                        0.6f + random.nextFloat() * 0.4f, false);
            }
        }
    }
}
