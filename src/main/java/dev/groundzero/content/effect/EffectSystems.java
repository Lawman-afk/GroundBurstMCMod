package dev.groundzero.content.effect;

import dev.groundzero.GroundZero;
import dev.groundzero.content.nuclear.GZEffects;
import dev.groundzero.registry.GZLang;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The aftermath systems built in Phase 6 slice 6e (DESIGN.md §16.4, §16.7, §21.4): the gas grid, EMP fields and
 * freezing, ticked here with each level.
 */
public final class EffectSystems {
    /** Frozen solid (see {@link Frost}). */
    public static final DeferredHolder<MobEffect, FrozenEffect> FROZEN = GZEffects.EFFECTS.register("frozen", FrozenEffect::new);

    static {
        GZLang.add("effect." + GroundZero.MODID + ".frozen", "Frozen");
    }

    private EffectSystems() {}

    public static void init() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                GasGrid.of(level).tick(level);
                EmpFields.tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            GasGrid.clearAll();
            EmpFields.clearAll();
        });
        // A frozen mob (not a player, not a boss) of 40 health or less hit hard enough shatters.
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e) -> {
            LivingEntity mob = e.getEntity();
            if (mob.level() instanceof ServerLevel level && mob.hasEffect(FROZEN) && !(mob instanceof Player)
                    && !mob.typeHolder().is(Tags.EntityTypes.BOSSES) && mob.getMaxHealth() <= Frost.SHATTER_MAX_HEALTH
                    && e.getAmount() >= Frost.SHATTER_DAMAGE) {
                e.setAmount(mob.getHealth() + mob.getAbsorptionAmount() + 100);
                Frost.shatter(level, mob);
            }
        });
    }
}
