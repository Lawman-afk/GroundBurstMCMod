package dev.groundzero.content.nuclear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Radiation Sickness (DESIGN.md §21.2): the stage the dose taken has reached, I to IV, kept up once a second by the
 * dose ({@link dev.groundzero.content.radiation.Radiation#sicken}), which also brings the vanilla effects of each stage
 * (nausea, weakness, mining fatigue, slowness, wither). This effect itself drains hunger like Hunger I to IV and, from
 * stage III, does a heart of radiation damage (it ignores armour) every 5 s, every 3 s at IV. Undead are immune.
 */
public class RadiationSicknessEffect extends MobEffect {
    /** Food exhaustion a tick, by amplifier (stage I = 0): as Hunger I to IV. */
    private static final float[] EXHAUSTION = {0.005f, 0.01f, 0.015f, 0.02f};
    /** Ticks between hearts of damage, by amplifier (0: none). */
    private static final int[] DAMAGE_EVERY = {0, 0, 100, 60};

    public RadiationSicknessEffect() {
        super(MobEffectCategory.HARMFUL, 0x8CE04A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        int k = Math.max(0, Math.min(EXHAUSTION.length - 1, amplification));
        if (mob instanceof Player player) {
            player.causeFoodExhaustion(EXHAUSTION[k]);
        }
        if (DAMAGE_EVERY[k] > 0 && mob.tickCount % DAMAGE_EVERY[k] == 0) {
            mob.hurtServer(level, level.damageSources().source(GZEffects.RADIATION_DAMAGE), 2);
        }
        return true;
    }
}
