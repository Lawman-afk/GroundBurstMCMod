package dev.groundzero.content.nuclear;

import dev.groundzero.GroundZero;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Radiation Sickness (DESIGN.md §21.2, §7.1): what contamination does to a living thing, getting much worse with each
 * level. Radiation damage (it ignores armour) per second: I 0.5, II 1.5, III 4, IV 8, so a minute at level I costs
 * most of a player's health and level IV kills in under three seconds. Every level drains hunger (faster each level)
 * and slows (5% a level). Until the radiation field and dose arrive (Phase 12) it comes from contaminated zones
 * ({@link Contamination}), reduced by a hazmat suit; the Decontamination Shower washes it off. Undead are immune.
 */
public class RadiationSicknessEffect extends MobEffect {
    /** Damage per hit and ticks between hits, by amplifier (level I = 0). */
    private static final float[] DAMAGE = {1f, 1.5f, 2f, 4f};
    private static final int[] INTERVAL = {40, 20, 10, 10};

    public RadiationSicknessEffect() {
        super(MobEffectCategory.HARMFUL, 0x8CE04A);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, GroundZero.id("radiation_sickness_slowness"), -0.05,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        int k = Math.max(0, Math.min(DAMAGE.length - 1, amplification));
        if (mob instanceof Player player) {
            player.causeFoodExhaustion(0.01f * (1 + k));
        }
        if (mob.tickCount % INTERVAL[k] == 0) {
            mob.hurtServer(level, level.damageSources().source(GZEffects.RADIATION_DAMAGE), DAMAGE[k]);
        }
        return true;
    }
}
