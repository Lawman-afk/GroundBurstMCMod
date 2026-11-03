package dev.groundburst.port;

import java.util.List;

import net.minecraft.world.effect.MobEffectInstance;

/** Minecraft 26.x's consume effect that gives status effects ({@link Consumable}). */
public record ApplyStatusEffectsConsumeEffect(List<MobEffectInstance> effects) {
    public ApplyStatusEffectsConsumeEffect(MobEffectInstance effect) {
        this(List.of(effect));
    }
}
