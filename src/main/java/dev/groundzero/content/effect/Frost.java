package dev.groundzero.content.effect;

import org.jspecify.annotations.Nullable;

import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Freezing (DESIGN.md §16.4): a burst of cold that freezes water to ice (packed ice near its middle), lava to obsidian
 * (flowing lava to cobblestone), puts out fire, frosts open ground with snow, and freezes every living thing in it
 * solid ({@link FrozenEffect}): it cannot move, and a mob of 40 health or less hit hard (6 or more) while frozen shatters.
 * Bosses are not frozen.
 */
public final class Frost {
    /** Damage that shatters a frozen mob, and the most health a mob can have to shatter. */
    public static final float SHATTER_DAMAGE = 6, SHATTER_MAX_HEALTH = 40;

    private Frost() {}

    public static void freeze(ServerLevel level, Vec3 centre, float radius, int ticks, @Nullable Entity by) {
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(centre);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        var random = level.getRandom();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > radius * radius) {
                        continue;
                    }
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    var fluid = s.getFluidState();
                    if (fluid.is(FluidTags.WATER) && (s.is(Blocks.WATER) || s.canBeReplaced())) {
                        level.setBlockAndUpdate(p, (d2 < radius * radius * 0.12 ? Blocks.PACKED_ICE : Blocks.ICE).defaultBlockState());
                    } else if (fluid.is(FluidTags.LAVA) && s.is(Blocks.LAVA)) {
                        level.setBlockAndUpdate(p, (fluid.isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
                    } else if (s.getBlock() instanceof BaseFireBlock || s.is(dev.groundzero.content.ordnance.GZOrdnance.NAPALM_FIRE.get())) {
                        level.removeBlock(p, false);
                    } else if (s.isAir() && random.nextFloat() < 0.8f) {
                        BlockPos below = p.below();
                        BlockState under = level.getBlockState(below);
                        BlockState snow = Blocks.SNOW.defaultBlockState();
                        if (!under.isAir() && under.isFaceSturdy(level, below, Direction.UP) && snow.canSurvive(level, p)) {
                            level.setBlockAndUpdate(p, snow);
                        }
                    }
                }
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius))) {
            if (e.position().distanceTo(centre) > radius || e.typeHolder().is(Tags.EntityTypes.BOSSES) || e.isSpectator()
                    || e instanceof Player pl && pl.isCreative()) {
                continue;
            }
            e.addEffect(new MobEffectInstance(EffectSystems.FROZEN, ticks, 0, false, true, true));
            e.setTicksFrozen(e.getTicksRequiredToFreeze() + ticks);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, centre.x, centre.y + 1, centre.z, (int) (40 * radius), radius * 0.4, 1, radius * 0.4, 0.15);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), centre.x, centre.y + 0.5, centre.z,
                (int) (12 * radius), radius * 0.3, 0.6, radius * 0.3, 0.2);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 0.5, centre.z, (int) (10 * radius), radius * 0.35, 0.5, radius * 0.35, 0.02);
        level.playSound(null, centre.x, centre.y, centre.z, GZSounds.CRYO_BURST.get(), SoundSource.BLOCKS, 2f, 1f);
    }

    /** A frozen mob hit hard enough breaks apart (shards of ice, the sound of breaking glass). */
    static void shatter(ServerLevel level, LivingEntity e) {
        Vec3 at = e.position().add(0, e.getBbHeight() / 2, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), at.x, at.y, at.z, 60,
                e.getBbWidth() * 0.4, e.getBbHeight() * 0.35, e.getBbWidth() * 0.4, 0.25);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.7f);
    }
}
