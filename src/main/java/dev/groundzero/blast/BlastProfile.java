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
 * @param core         everything breakable inside is destroyed, whatever its resistance
 * @param crater       blocks are destroyed while a ray's energy lasts (dense blocks shield what is behind them)
 * @param structural   past the crater, light blocks are blown out (glass, leaves, plants, doors)
 * @param scorch       exposed surfaces are scorched (grass to dirt, snow melted)
 * @param fire         exposed flammable surfaces may catch fire (less often further out)
 * @param lethal       an exposed entity inside takes {@code lethalDamage}
 * @param damage       outside the lethal zone, damage and knockback fall off to nothing here
 * @param frontSpeed   how fast the destruction and shock front travels, in blocks a tick
 * @param craterDepth  energy of a straight-down ray relative to a sideways one (below 1: bowl craters, not spheres)
 * @param lobes        how irregular the crater rim is (0: round; 0.3: clearly lobed)
 * @param drops        chance each destroyed block drops itself (0 for anything big)
 * @param lethalDamage damage inside the lethal zone (scaled by exposure)
 * @param edgeDamage   damage just outside the lethal zone, falling to 0 at {@code damage}
 * @param knockback    knockback strength at the edge of the lethal zone
 * @param terrain      the terrain table for the structural and scorch bands ({@link TerrainTable})
 * @param instant      whether the whole blast happens in one tick (small charges); by default when the crater is 8 or less
 */
public record BlastProfile(float core, float crater, float structural, float scorch, float fire, float lethal, float damage,
        float frontSpeed, float craterDepth, float lobes, float drops, float lethalDamage, float edgeDamage, float knockback,
        Identifier terrain, Optional<Boolean> instant) {

    public static final ResourceKey<Registry<BlastProfile>> REGISTRY = ResourceKey.createRegistryKey(GroundZero.id("blast_profile"));

    public static final Codec<BlastProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("core", 0f).forGetter(BlastProfile::core),
            Codec.FLOAT.fieldOf("crater").forGetter(BlastProfile::crater),
            Codec.FLOAT.optionalFieldOf("structural", 0f).forGetter(BlastProfile::structural),
            Codec.FLOAT.optionalFieldOf("scorch", 0f).forGetter(BlastProfile::scorch),
            Codec.FLOAT.optionalFieldOf("fire", 0f).forGetter(BlastProfile::fire),
            Codec.FLOAT.optionalFieldOf("lethal", 0f).forGetter(BlastProfile::lethal),
            Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(BlastProfile::damage),
            Codec.FLOAT.optionalFieldOf("front_speed", 2f).forGetter(BlastProfile::frontSpeed),
            Codec.FLOAT.optionalFieldOf("crater_depth", 0.7f).forGetter(BlastProfile::craterDepth),
            Codec.FLOAT.optionalFieldOf("lobes", 0.15f).forGetter(BlastProfile::lobes),
            Codec.FLOAT.optionalFieldOf("drops", 0f).forGetter(BlastProfile::drops),
            Codec.FLOAT.optionalFieldOf("lethal_damage", 200f).forGetter(BlastProfile::lethalDamage),
            Codec.FLOAT.optionalFieldOf("edge_damage", 20f).forGetter(BlastProfile::edgeDamage),
            Codec.FLOAT.optionalFieldOf("knockback", 1.5f).forGetter(BlastProfile::knockback),
            Identifier.CODEC.optionalFieldOf("terrain", TerrainTable.CONVENTIONAL).forGetter(BlastProfile::terrain),
            Codec.BOOL.optionalFieldOf("instant").forGetter(BlastProfile::instant)
    ).apply(i, BlastProfile::new));

    /** Whether the blast runs to completion in the tick it goes off. */
    public boolean isInstant() {
        return this.instant.orElse(this.crater <= 8 && this.structural <= 12 && this.damage <= 16);
    }

    /** This profile with every radius multiplied by {@code k} and capped at {@code max}. */
    public BlastProfile scaled(float k, float max) {
        return new BlastProfile(Math.min(max, core * k), Math.min(max, crater * k), Math.min(max, structural * k),
                Math.min(max, scorch * k), Math.min(max, fire * k), Math.min(max, lethal * k), Math.min(max, damage * k),
                frontSpeed, craterDepth, lobes, drops, lethalDamage, edgeDamage, knockback, terrain, instant);
    }

    /** The radius the block work runs to (crater and the bands beyond it). */
    public float terrainRadius() {
        return Math.max(Math.max(crater, structural), Math.max(scorch, fire));
    }

    /** The radius the whole blast runs to (terrain and entity zones). */
    public float outerRadius() {
        return Math.max(terrainRadius(), Math.max(lethal, damage));
    }
}
