package dev.groundburst.port;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.UseAnim;

/**
 * Minecraft 26.x's consumable component, as 1.21.1 has one: food that fills nothing and is always edible, eaten quickly
 * (under 1.2 s) or at the usual pace, giving its effects. (1.21.1's food eats: the animation and sounds are its own.)
 */
public final class Consumable {
    private Consumable() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private float seconds = 1.6f;
        private final List<MobEffectInstance> effects = new ArrayList<>();

        public Builder consumeSeconds(float seconds) {
            this.seconds = seconds;
            return this;
        }

        public Builder animation(UseAnim animation) {
            return this;
        }

        public Builder sound(SoundEvent sound) {
            return this;
        }

        public Builder sound(Holder<SoundEvent> sound) {
            return this;
        }

        public Builder soundAfterConsume(Holder<SoundEvent> sound) {
            return this;
        }

        public Builder hasConsumeParticles(boolean particles) {
            return this;
        }

        public Builder onConsume(ApplyStatusEffectsConsumeEffect effect) {
            this.effects.addAll(effect.effects());
            return this;
        }

        public FoodProperties build() {
            FoodProperties.Builder food = new FoodProperties.Builder().nutrition(0).saturationMod(0).alwaysEat();
            if (this.seconds < 1.2f) {
                food.fast();
            }
            for (MobEffectInstance effect : this.effects) {
                food.effect(() -> new MobEffectInstance(effect), 1.0f);
            }
            return food.build();
        }
    }
}
