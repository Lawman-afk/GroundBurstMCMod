package dev.groundzero.content.nuclear;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Status effects and the radiation damage type. */
public final class GZEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, GroundZero.MODID);

    public static final DeferredHolder<MobEffect, RadiationSicknessEffect> RADIATION_SICKNESS =
            EFFECTS.register("radiation_sickness", RadiationSicknessEffect::new);
    /** Rad-Away working (DESIGN.md §21.6): the dose falls 5 rad a second ({@code Radiation}). */
    public static final DeferredHolder<MobEffect, MobEffect> RAD_AWAY = EFFECTS.register("rad_away",
            () -> new Medicine(0xE8862A));
    /** Potassium Iodide taken: half the dose is taken up ({@code Radiation}). */
    public static final DeferredHolder<MobEffect, MobEffect> POTASSIUM_IODIDE = EFFECTS.register("potassium_iodide",
            () -> new Medicine(0xB98AE0));

    /** A medicine's effect: does nothing itself; the dose system reads it. */
    private static final class Medicine extends MobEffect {
        Medicine(int colour) {
            super(MobEffectCategory.BENEFICIAL, colour);
        }
    }

    /** {@code groundzero:radiation}: what radiation sickness deals. */
    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("radiation"));

    static {
        GZLang.add("effect." + GroundZero.MODID + ".radiation_sickness", "Radiation Sickness");
        GZLang.add("effect." + GroundZero.MODID + ".rad_away", "Rad-Away");
        GZLang.add("effect." + GroundZero.MODID + ".potassium_iodide", "Potassium Iodide");
        GZLang.add("death.attack." + GroundZero.MODID + ".radiation", "%1$s died of radiation sickness");
        GZLang.add("death.attack." + GroundZero.MODID + ".radiation.player", "%1$s died of radiation sickness whilst fighting %2$s");
    }

    private GZEffects() {}
}
