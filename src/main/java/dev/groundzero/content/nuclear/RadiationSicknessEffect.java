package dev.groundzero.content.nuclear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Radiation Sickness (DESIGN.md §21.2, §7 meltdown): what contamination does to a living thing, by level. Every level
 * drains hunger; from level II it deals radiation damage, every 4 s at II, every 2 s at III, every second at IV.
 * Until the radiation field and dose arrive (Phase 12) it comes from contaminated zones ({@link Contamination}); the
 * Decontamination Shower washes it off. Undead are immune (they are never given it).
 */
public class RadiationSicknessEffect extends MobEffect {
    public RadiationSicknessEffect() {
        super(MobEffectCategory.HARMFUL, 0x8CE04A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        if (mob instanceof Player player) {
            player.causeFoodExhaustion(0.006f * (1 + amplification));
        }
        if (amplification >= 1) {
            int every = 80 >> Math.min(2, amplification - 1);
            if (mob.tickCount % every == 0) {
                mob.hurtServer(level, level.damageSources().source(GZEffects.RADIATION_DAMAGE), 1.0f);
            }
        }
        return true;
    }
}
