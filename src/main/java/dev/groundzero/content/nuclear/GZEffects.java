package dev.groundzero.content.nuclear;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Status effects and the radiation damage type. */
public final class GZEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, GroundZero.MODID);

    public static final DeferredHolder<MobEffect, RadiationSicknessEffect> RADIATION_SICKNESS =
            EFFECTS.register("radiation_sickness", RadiationSicknessEffect::new);

    /** {@code groundzero:radiation}: what radiation sickness deals. */
    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("radiation"));

    static {
        GZLang.add("effect." + GroundZero.MODID + ".radiation_sickness", "Radiation Sickness");
        GZLang.add("death.attack." + GroundZero.MODID + ".radiation", "%1$s died of radiation sickness");
        GZLang.add("death.attack." + GroundZero.MODID + ".radiation.player", "%1$s died of radiation sickness whilst fighting %2$s");
    }

    private GZEffects() {}
}
