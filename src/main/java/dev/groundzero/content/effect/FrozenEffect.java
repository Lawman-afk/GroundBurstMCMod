package dev.groundzero.content.effect;

import dev.groundzero.GroundZero;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Frozen (DESIGN.md §16.4): frozen solid. It cannot walk or jump (a mob's AI stands still), it shakes and frosts over as
 * if it were stuck in powder snow, and frost drifts off it. While frozen, a mob of 40 health or less hit hard enough
 * shatters ({@link Frost}).
 */
public class FrozenEffect extends MobEffect {
    public FrozenEffect() {
        super(MobEffectCategory.HARMFUL, 0x9FE8FF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, GroundZero.id("frozen_still"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, GroundZero.id("frozen_no_jump"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        mob.setTicksFrozen(Math.max(mob.getTicksFrozen(), mob.getTicksRequiredToFreeze() + 2));
        mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
        if (mob instanceof Mob m) {
            m.getNavigation().stop();
        }
        if (mob.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 3,
                    mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.01);
        }
        return true;
    }
}
