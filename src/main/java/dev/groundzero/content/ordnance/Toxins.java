package dev.groundzero.content.ordnance;

import dev.groundzero.content.gear.GZArmor;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Chemical exposure (DESIGN.md §14.2): poison gas, toxin sludge, toxic liquids and gas clouds all go through here. A
 * hazmat or lead-lined suit keeps out its share of the dose (see {@link GZArmor#chemicalProtection}); a full suit
 * keeps out all of it.
 */
public final class Toxins {
    private Toxins() {}

    /**
     * One dose: {@code damage} of {@code groundzero:toxin} (armour does not help; undead feel it too) and Poison at
     * {@code amplifier} for {@code poisonTicks}, both cut by the suit.
     */
    public static void expose(ServerLevel level, LivingEntity entity, float damage, int poisonTicks, int amplifier) {
        float keep = 1 - GZArmor.chemicalProtection(entity);
        if (keep < 0.05f) {
            return;
        }
        if (damage > 0) {
            entity.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(GZOrdnance.TOXIN_DAMAGE)), damage * keep);
        }
        int ticks = Math.round(poisonTicks * keep);
        if (ticks > 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amplifier));
        }
    }
}
