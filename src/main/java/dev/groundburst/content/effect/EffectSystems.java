package dev.groundburst.content.effect;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.nuclear.GBEffects;
import dev.groundburst.registry.GBLang;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import dev.groundburst.legacy.DeferredHolder;

/**
 * The aftermath systems built in Phase 6 slice 6e (DESIGN.md §16.4, §16.7, §21.4): the gas grid, EMP fields and
 * freezing, ticked here with each level.
 */
public final class EffectSystems {
    /** Frozen solid (see {@link Frost}). */
    public static final DeferredHolder<MobEffect, FrozenEffect> FROZEN = GBEffects.EFFECTS.register("frozen", FrozenEffect::new);

    static {
        GBLang.add("effect." + GroundBurst.MODID + ".frozen", "Frozen");
    }

    private EffectSystems() {}

    public static void init() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.LevelTickEvent e) -> {if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (((Object) e.level) instanceof ServerLevel level) {
                GasGrid.of(level).tick(level);
                EmpFields.tick(level);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            GasGrid.clearAll();
            EmpFields.clearAll();
        });
        // When the Frozen effect ends (or is taken off, as by milk) the creature thaws: its ice goes.
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.MobEffectEvent.Expired e) -> {
            if (e.getEffectInstance() != null && e.getEffectInstance().getEffect() == FROZEN.get()) {
                e.getEntity().setTicksFrozen(0);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.MobEffectEvent.Remove e) -> {
            if (e.getEffect() == FROZEN.get()) {
                e.getEntity().setTicksFrozen(0);
            }
        });
        // A frozen mob (not a player, not a boss) of 40 health or less hit hard enough shatters.
        MinecraftForge.EVENT_BUS.addListener((LivingHurtEvent e) -> {
            LivingEntity mob = e.getEntity();
            if (((Object) mob.level()) instanceof ServerLevel level && mob.hasEffect(FROZEN.get()) && !(mob instanceof Player)
                    && !mob.getType().is(Tags.EntityTypes.BOSSES) && mob.getMaxHealth() <= Frost.SHATTER_MAX_HEALTH
                    && e.getAmount() >= Frost.SHATTER_DAMAGE) {
                e.setAmount(mob.getHealth() + mob.getAbsorptionAmount() + 100);
                Frost.shatter(level, mob);
            }
        });
    }
}
