package dev.groundzero.content.nuclear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundzero.GroundZero;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Contaminated zones (DESIGN.md §7.1, meltdown): until the radiation field of Phase 12 exists, a meltdown leaves a zone
 * round the reactor. Once a second every living thing inside (undead excepted, and players in creative or spectator)
 * is given Radiation Sickness, stronger toward the centre: level = strength x (1 - distance / radius) x (1 - suit
 * protection), sickness level {@code min(IV, 1 + floor(4 x level))} above 0.05; the strength fades to nothing over the
 * zone's life. Zones are the level's saved data ({@code contamination.dat}).
 */
public final class Contamination extends SavedData {
    /** One zone: centre, radius, starting strength (0 to 1), when it began and how long it lasts (ticks). */
    public record Zone(Vec3 center, float radius, float strength, long start, int duration) {
        static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
                Vec3.CODEC.fieldOf("center").forGetter(Zone::center),
                Codec.FLOAT.fieldOf("radius").forGetter(Zone::radius),
                Codec.FLOAT.fieldOf("strength").forGetter(Zone::strength),
                Codec.LONG.fieldOf("start").forGetter(Zone::start),
                Codec.INT.fieldOf("duration").forGetter(Zone::duration)
        ).apply(i, Zone::new));

        /** How strong it is at game time {@code now}, at {@code distance} from its centre (0 outside). */
        public float levelAt(long now, double distance) {
            if (distance >= this.radius || now >= this.start + this.duration) {
                return 0;
            }
            float age = (now - this.start) / (float) this.duration;
            return this.strength * (1 - age) * (float) (1 - distance / this.radius);
        }
    }

    static final SavedDataType<Contamination> TYPE = new SavedDataType<Contamination>(GroundZero.id("contamination"), () -> new Contamination(),
            Zone.CODEC.listOf().xmap(Contamination::new, c -> List.copyOf(c.zones)));
    private static final Map<ServerLevel, Contamination> BY_LEVEL = new WeakHashMap<>();
    /** Sickness given lasts this long, so it wears off a few seconds after leaving a zone. */
    private static final int EFFECT_TICKS = 100;

    private final List<Zone> zones = new ArrayList<>();

    private Contamination() {}

    private Contamination(List<Zone> zones) {
        this.zones.addAll(zones);
    }

    public static Contamination of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(TYPE));
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
                of(level).tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                BY_LEVEL.remove(level);
            }
        });
    }

    public void add(Zone zone) {
        this.zones.add(zone);
        setDirty();
    }

    /** For GameTests: removes the zones centred within {@code within} of {@code centre}. */
    public void removeNear(Vec3 centre, double within) {
        if (this.zones.removeIf(z -> z.center().distanceTo(centre) <= within)) {
            setDirty();
        }
    }

    public List<Zone> zones() {
        return List.copyOf(this.zones);
    }

    /** The strongest contamination at {@code pos} now (0 to 1). */
    public float levelAt(ServerLevel level, Vec3 pos) {
        float most = 0;
        for (Zone z : this.zones) {
            most = Math.max(most, z.levelAt(level.getGameTime(), pos.distanceTo(z.center())));
        }
        return most;
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (this.zones.removeIf(z -> now >= z.start() + z.duration())) {
            setDirty();
        }
        for (Zone z : this.zones) {
            AABB box = new AABB(z.center(), z.center()).inflate(z.radius());
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (e.is(EntityTypeTags.UNDEAD) || e instanceof net.minecraft.world.entity.decoration.ArmorStand
                        || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                    continue;
                }
                // A hazmat suit stops most of it.
                float strength = levelAt(level, e.position()) * (1 - dev.groundzero.content.gear.GZArmor.radiationProtection(e));
                if (strength < 0.05f) {
                    continue;
                }
                int amplifier = Math.min(3, (int) (strength * 4));
                e.addEffect(new MobEffectInstance(GZEffects.RADIATION_SICKNESS, EFFECT_TICKS, amplifier));
            }
        }
    }
}
