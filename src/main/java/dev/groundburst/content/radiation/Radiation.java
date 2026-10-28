package dev.groundburst.content.radiation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import dev.groundburst.config.GBServerConfig;
import dev.groundburst.content.gear.GBArmor;
import dev.groundburst.content.nuclear.GBEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The dose living things take (DESIGN.md §21.2, §21.8). Once a second each player's dose rate is measured where their
 * eyes are: the sources' field through the cover round them ({@link Shielding#ambient}), radioactive blocks near them
 * ({@link Emitters#blocks}) (both less what a suit stops), and what they carry; Potassium Iodide halves the lot. The
 * dose grows by rate / 3600 a second, falls {@link #RECOVERY} a second while the rate is under
 * {@link RadiationSource#SAFE} (a uranium ore or two in a pocket does not stop the body mending), and
 * {@link #RAD_AWAY_PER_SECOND} faster under Rad-Away. Mobs inside a source are
 * measured too, by the field alone. The dose brings Radiation Sickness by stage ({@link #sicken}).
 */
public final class Radiation {
    /** Dose given back a second while the dose rate is safe (under {@link RadiationSource#SAFE}): 1 rad a minute. */
    public static final float RECOVERY = 1f / 60;
    /** What Rad-Away takes off a second while it works: 150 rad over its 30 s. */
    public static final float RAD_AWAY_PER_SECOND = 5;
    /** The share of the dose taken under Potassium Iodide. */
    public static final float IODINE_UPTAKE = 0.5f;

    private Radiation() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer p && (p.tickCount + p.getId()) % 20 == 0) {
                update(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 10) {
                mobs(level);
            }
        });
        // Sickness III and worse: no natural regeneration (its small heals), as the marrow fails.
        NeoForge.EVENT_BUS.addListener((LivingHealEvent event) -> {
            MobEffectInstance sick = event.getEntity().getEffect(GBEffects.RADIATION_SICKNESS);
            if (sick != null && sick.getAmplifier() >= 2 && event.getAmount() <= 1.0f) {
                event.setCanceled(true);
            }
        });
    }

    public static boolean enabled() {
        return GBServerConfig.RADIATION_ENABLED.get();
    }

    /** Whether radiation does nothing to {@code body}: the undead, armour stands, players in creative or spectator. */
    public static boolean immune(LivingEntity body) {
        return body.is(EntityTypeTags.UNDEAD) || body instanceof ArmorStand || body instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    public static RadiationState state(LivingEntity body) {
        return body.hasData(GBRadiation.STATE) ? body.getData(GBRadiation.STATE) : RadiationState.NONE;
    }

    public static float dose(LivingEntity body) {
        return state(body).dose();
    }

    /** Adds {@code rad} to the dose {@code body} has taken, at once (a neutron burst; negative: medicine). */
    public static void addDose(LivingEntity body, float rad) {
        if (rad > 0 && immune(body)) {
            return;
        }
        RadiationState s = state(body);
        float dose = Math.max(0, s.dose() + rad);
        body.setData(GBRadiation.STATE, new RadiationState(dose, s.rate()));
        sicken(body, dose);
    }

    /** Sets the dose {@code body} has taken (GameTests, commands). */
    public static void setDose(LivingEntity body, float rad) {
        RadiationState s = state(body);
        body.setData(GBRadiation.STATE, new RadiationState(Math.max(0, rad), s.rate()));
    }

    /**
     * The dose rate {@code body} stands in now, RAD/h: the sources' field through its cover, and (with
     * {@code nearby}) radioactive blocks round it and what it carries; a suit stops its share of what comes from outside.
     */
    public static float rateAt(ServerLevel level, LivingEntity body, boolean nearby) {
        Vec3 eye = body.getEyePosition();
        float field = RadiationSources.of(level).rateAt(level, eye);
        if (field > 0) {
            field *= (float) Shielding.ambient(level, eye);
        }
        float blocks = nearby ? Emitters.blocks(level, eye) : 0;
        float outside = (field + blocks) * (1 - GBArmor.radiationProtection(body));
        float carried = nearby ? Emitters.carried(body) : 0;
        return (outside + carried) * uptake(body);
    }

    private static float uptake(LivingEntity body) {
        return body.hasEffect(GBEffects.POTASSIUM_IODIDE) ? IODINE_UPTAKE : 1;
    }

    private static void update(ServerPlayer player) {
        RadiationState s = player.getData(GBRadiation.STATE);
        if (!enabled() || immune(player) || !player.isAlive()) {
            if (s.rate() != 0) {
                player.setData(GBRadiation.STATE, new RadiationState(s.dose(), 0));
            }
            return;
        }
        step(player, s, rateAt((ServerLevel) player.level(), player, true));
    }

    /** One second at {@code rate}: the dose taken, given back and pulled out by Rad-Away, and the sickness it brings. */
    public static void step(LivingEntity body, RadiationState s, float rate) {
        float dose = s.dose() + rate / 3600f;
        if (rate < RadiationSource.SAFE) {
            dose -= RECOVERY;
        }
        if (body.hasEffect(GBEffects.RAD_AWAY)) {
            dose -= RAD_AWAY_PER_SECOND;
        }
        dose = Math.max(0, dose);
        if (dose != s.dose() || rate != s.rate()) {
            body.setData(GBRadiation.STATE, new RadiationState(dose, rate));
        }
        sicken(body, dose);
    }

    /** Mobs inside a source: the field alone, once a second. */
    private static void mobs(ServerLevel level) {
        if (!enabled()) {
            return;
        }
        List<RadiationSource> list = RadiationSources.of(level).sources();
        if (list.isEmpty()) {
            return;
        }
        Set<LivingEntity> done = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RadiationSource source : list) {
            AABB box = new AABB(source.center(), source.center()).inflate(source.radius());
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof Player) && e.isAlive() && !immune(e))) {
                if (!done.add(e)) {
                    continue;
                }
                float rate = rateAt(level, e, false);
                if (rate > 0 || e.hasData(GBRadiation.STATE)) {
                    step(e, state(e), rate);
                }
            }
        }
    }

    /**
     * What the dose does (§21.2), once a second: Radiation Sickness I to IV by stage (its own effect drains hunger and,
     * from III, does damage), nausea now and then, from II weakness and mining fatigue, from III slowness, at IV wither
     * bursts; from the lethal dose a quick death.
     */
    static void sicken(LivingEntity body, float dose) {
        int stage = RadiationState.stage(dose);
        if (stage <= 0 || !(body.level() instanceof ServerLevel level)) {
            return;
        }
        RandomSource random = body.getRandom();
        body.addEffect(new MobEffectInstance(GBEffects.RADIATION_SICKNESS, 60, Math.min(3, stage - 1), false, true, true));
        if (random.nextInt(40) == 0) {
            body.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 140, 0, false, false, true));
        }
        if (stage >= 2) {
            body.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
            body.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0, false, false, true));
        }
        if (stage >= 3) {
            body.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, stage >= 4 ? 1 : 0, false, false, true));
        }
        if (stage >= 4 && random.nextInt(15) == 0) {
            body.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, false, true));
        }
        if (stage >= 5) {
            body.hurtServer(level, level.damageSources().source(GBEffects.RADIATION_DAMAGE),
                    2 + (dose - RadiationState.STAGES[4]) / 250);
        }
    }
}
