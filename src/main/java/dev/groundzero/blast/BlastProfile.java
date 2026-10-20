package dev.groundzero.blast;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundzero.GroundZero;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * How a blast behaves (DESIGN.md §11.1): a datapack registry entry ({@code data/<ns>/groundzero/blast_profile/*.json}),
 * so weapons only name a profile and pack makers can retune radii without code. Every radius is in blocks from the
 * burst point, before the server's radius multiplier.
 *
 * @param core        everything breakable inside is destroyed, whatever its resistance
 * @param crater      blocks are destroyed while a ray's energy lasts (dense blocks shield what is behind them)
 * @param structural  past the crater, light blocks are blown out (glass, leaves, plants, doors)
 * @param scorch      exposed surfaces are scorched (grass to dirt, snow melted)
 * @param fire        exposed flammable surfaces may catch fire (less often further out)
 * @param entities    what the blast does to entities ({@link EntityZones})
 * @param frontSpeed  how fast the destruction and shock front travels, in blocks a tick
 * @param craterDepth energy of a straight-down ray relative to a sideways one (below 1: bowl craters, not spheres)
 * @param lobes       how irregular the crater rim is (0: round; 0.3: clearly lobed)
 * @param streaks     how uneven neighbouring rays are (0: smooth; 0.35: a starburst crater with fingers past the rim, a
 *                    spiky floor, and scorch and ejecta in spokes)
 * @param ejecta      how much rubble is thrown out past the rim, along the spokes (0: none)
 * @param drops       chance each destroyed block drops itself (0 for anything big)
 * @param terrain     the terrain table for the structural and scorch bands ({@link TerrainTable})
 * @param instant     whether the whole blast happens in one tick (small charges); by default when it is small
 * @param sound       the sound set heard (small, medium, large, nuke); by default chosen by the crater's size
 * @param visual      the client visual preset (Phase 5, slice 5c); by default chosen by the crater's size
 */
public record BlastProfile(float core, float crater, float structural, float scorch, float fire, EntityZones entities,
        float frontSpeed, float craterDepth, float lobes, float streaks, float ejecta, float drops, Identifier terrain, Optional<Boolean> instant,
        Optional<String> sound, Optional<String> visual) {

    public static final ResourceKey<Registry<BlastProfile>> REGISTRY = ResourceKey.createRegistryKey(GroundZero.id("blast_profile"));

    /**
     * @param lethal       an exposed entity inside takes {@code lethalDamage}
     * @param damage       outside the lethal zone, damage and knockback fall off to nothing here
     * @param lethalDamage damage inside the lethal zone (scaled by exposure)
     * @param edgeDamage   damage just outside the lethal zone, falling to 0 at {@code damage}
     * @param knockback    knockback strength at the edge of the lethal zone
     */
    public record EntityZones(float lethal, float damage, float lethalDamage, float edgeDamage, float knockback) {
        public static final Codec<EntityZones> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("lethal", 0f).forGetter(EntityZones::lethal),
                Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(EntityZones::damage),
                Codec.FLOAT.optionalFieldOf("lethal_damage", 200f).forGetter(EntityZones::lethalDamage),
                Codec.FLOAT.optionalFieldOf("edge_damage", 20f).forGetter(EntityZones::edgeDamage),
                Codec.FLOAT.optionalFieldOf("knockback", 1.5f).forGetter(EntityZones::knockback)
        ).apply(i, EntityZones::new));
        public static final EntityZones NONE = new EntityZones(0, 0, 0, 0, 0);
    }

    public static final Codec<BlastProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("core", 0f).forGetter(BlastProfile::core),
            Codec.FLOAT.fieldOf("crater").forGetter(BlastProfile::crater),
            Codec.FLOAT.optionalFieldOf("structural", 0f).forGetter(BlastProfile::structural),
            Codec.FLOAT.optionalFieldOf("scorch", 0f).forGetter(BlastProfile::scorch),
            Codec.FLOAT.optionalFieldOf("fire", 0f).forGetter(BlastProfile::fire),
            EntityZones.CODEC.optionalFieldOf("entities", EntityZones.NONE).forGetter(BlastProfile::entities),
            Codec.FLOAT.optionalFieldOf("front_speed", 2f).forGetter(BlastProfile::frontSpeed),
            Codec.FLOAT.optionalFieldOf("crater_depth", 0.7f).forGetter(BlastProfile::craterDepth),
            Codec.FLOAT.optionalFieldOf("lobes", 0.15f).forGetter(BlastProfile::lobes),
            Codec.FLOAT.optionalFieldOf("streaks", 0.3f).forGetter(BlastProfile::streaks),
            Codec.FLOAT.optionalFieldOf("ejecta", 0.4f).forGetter(BlastProfile::ejecta),
            Codec.FLOAT.optionalFieldOf("drops", 0f).forGetter(BlastProfile::drops),
            Identifier.CODEC.optionalFieldOf("terrain", TerrainTable.CONVENTIONAL).forGetter(BlastProfile::terrain),
            Codec.BOOL.optionalFieldOf("instant").forGetter(BlastProfile::instant),
            Codec.STRING.optionalFieldOf("sound").forGetter(BlastProfile::sound),
            Codec.STRING.optionalFieldOf("visual").forGetter(BlastProfile::visual)
    ).apply(i, BlastProfile::new));

    public float lethal() {
        return this.entities.lethal();
    }

    public float damage() {
        return this.entities.damage();
    }

    public float lethalDamage() {
        return this.entities.lethalDamage();
    }

    public float edgeDamage() {
        return this.entities.edgeDamage();
    }

    public float knockback() {
        return this.entities.knockback();
    }

    /** Whether the blast runs to completion in the tick it goes off. */
    public boolean isInstant() {
        return this.instant.orElse(this.crater <= 8 && this.structural <= 12 && damage() <= 16);
    }

    /** The sound set heard: the profile's, or one chosen by the crater's size. */
    public String soundSet() {
        return this.sound.orElse(this.crater <= 4 ? "small" : this.crater <= 14 ? "medium" : this.crater <= 40 ? "large" : "nuke");
    }

    /** This profile with every radius multiplied by {@code k} and capped at {@code max}. */
    public BlastProfile scaled(float k, float max) {
        EntityZones e = this.entities;
        return new BlastProfile(Math.min(max, core * k), Math.min(max, crater * k), Math.min(max, structural * k),
                Math.min(max, scorch * k), Math.min(max, fire * k),
                new EntityZones(Math.min(max, e.lethal() * k), Math.min(max, e.damage() * k), e.lethalDamage(), e.edgeDamage(), e.knockback()),
                frontSpeed, craterDepth, lobes, streaks, ejecta, drops, terrain, instant, sound, visual);
    }

    /** The radius the block work runs to (the crater with its longest fingers, and the bands beyond it). */
    public float terrainRadius() {
        return Math.max(Math.max(crater * (streaks > 0 ? 1.8f : 1), structural), groundRadius());
    }

    /** The radius the ground pass runs to (scorch, fire, and ejecta, which lands up to 1.9 craters out). */
    public float groundRadius() {
        return Math.max(Math.max(scorch, fire), ejecta > 0 ? crater * 1.9f : 0);
    }

    /** The radius the whole blast runs to (terrain and entity zones). */
    public float outerRadius() {
        return Math.max(terrainRadius(), Math.max(lethal(), damage()));
    }
}
