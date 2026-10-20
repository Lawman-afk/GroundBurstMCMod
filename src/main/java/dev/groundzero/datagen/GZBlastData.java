package dev.groundzero.datagen;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import dev.groundzero.GroundZero;
import dev.groundzero.blast.BlastProfile;
import dev.groundzero.blast.Blasts;
import dev.groundzero.blast.TerrainTable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

/**
 * Explosion engine data (DESIGN.md §11): blast profiles and the blast damage type. The {@code test/*} profiles are for
 * the {@code /groundzero blast} command and the GameTests; each weapon gets its own profile when it is built.
 */
public final class GZBlastData {
    private GZBlastData() {}

    public static void gather(GatherDataRegistryEntriesEvent event) {
        event.add(BlastProfile.REGISTRY, GZBlastData::profiles);
        event.add(Registries.DAMAGE_TYPE, GZBlastData::damageTypes);
    }

    private static BlastProfile profile(float core, float crater, float structural, float scorch, float fire, float lethal, float damage,
            float frontSpeed, float craterDepth, float lobes, float streaks, float ejecta, float lethalDamage, float edgeDamage,
            float knockback, Identifier terrain, Optional<Boolean> instant) {
        return new BlastProfile(core, crater, structural, scorch, fire,
                new BlastProfile.EntityZones(lethal, damage, lethalDamage, edgeDamage, knockback), frontSpeed, craterDepth, lobes, streaks,
                ejecta, 0f, terrain, instant, Optional.empty(), Optional.empty());
    }

    private static void profiles(BootstrapContext<BlastProfile> ctx) {
        // core, crater, structural, scorch, fire, lethal, damage, speed, depth, lobes, streaks, ejecta, lethal dmg, edge dmg, knockback
        ctx.register(Blasts.profile("test/small"), profile(1, 3, 5, 4, 0, 3, 6, 4, 0.7f, 0.1f, 0.3f, 0.3f, 30, 8, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.empty()));
        ctx.register(Blasts.profile("test/medium"), profile(3, 12, 18, 16, 10, 10, 22, 3, 0.6f, 0.15f, 0.35f, 0.5f, 120, 20, 2.0f,
                TerrainTable.CONVENTIONAL, Optional.empty()));
        ctx.register(Blasts.profile("test/large"), profile(8, 32, 48, 44, 30, 28, 60, 4, 0.55f, 0.18f, 0.35f, 0.5f, 400, 30, 2.5f,
                TerrainTable.CONVENTIONAL, Optional.empty()));
        ctx.register(Blasts.profile("test/nuclear"), profile(16, 60, 140, 120, 100, 90, 180, 3, 0.5f, 0.2f, 0.35f, 0.4f, 1000, 40, 3.0f,
                TerrainTable.NUCLEAR, Optional.empty()));
        // The Fission Reactor's meltdown: a dirty, smoky blast that wrecks the reactor hall (DESIGN.md §7). Instant (it is
        // small), so the corium it leaves can be poured into the finished crater at once.
        ctx.register(Blasts.profile("reactor_meltdown"), new BlastProfile(3, 8, 12, 14, 12, new BlastProfile.EntityZones(8, 16, 60, 14, 2.0f), 3,
                0.6f, 0.15f, 0.35f, 0.3f, 0f, TerrainTable.CONVENTIONAL, Optional.of(true), Optional.of("large"), Optional.of("meltdown")));
        // GameTest profiles (smooth, so their shapes are predictable): a crater-only charge, and a slow one that must take several ticks.
        ctx.register(Blasts.profile("test/crater_only"), profile(0, 6, 0, 0, 0, 0, 0, 4, 1.0f, 0f, 0f, 0f, 0, 0, 0,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("test/slow"), profile(1, 5, 0, 0, 0, 4, 6, 0.5f, 1.0f, 0f, 0f, 0f, 50, 8, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(false)));
        // ...and one each for the crater's streaks, the ground pass's scorch, and the ejecta.
        ctx.register(Blasts.profile("test/streaky"), profile(0, 5, 0, 0, 0, 0, 0, 4, 1.0f, 0f, 0.5f, 0f, 0, 0, 0,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("test/scorch"), profile(0, 2, 0, 7, 0, 0, 0, 4, 1.0f, 0f, 0f, 0f, 0, 0, 0,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("test/ejecta"), profile(0, 4, 0, 0, 0, 0, 0, 4, 0.7f, 0f, 0.35f, 1.0f, 0, 0, 0,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
    }

    private static void damageTypes(BootstrapContext<DamageType> ctx) {
        ctx.register(Blasts.BLAST_DAMAGE, new DamageType(GroundZero.MODID + ".blast", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f));
        ctx.register(dev.groundzero.content.nuclear.GZEffects.RADIATION_DAMAGE,
                new DamageType(GroundZero.MODID + ".radiation", DamageScaling.NEVER, 0f));
    }

    /** {@code groundzero:blast} counts as an explosion (Blast Protection, explosion-proof mobs). */
    public static final class DamageTags extends DamageTypeTagsProvider {
        public DamageTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, GroundZero.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            tag(DamageTypeTags.IS_EXPLOSION).add(Blasts.BLAST_DAMAGE);
            // Radiation sickness: armour does not help, and it does not push anyone about.
            tag(DamageTypeTags.BYPASSES_ARMOR).add(dev.groundzero.content.nuclear.GZEffects.RADIATION_DAMAGE);
            tag(DamageTypeTags.NO_KNOCKBACK).add(dev.groundzero.content.nuclear.GZEffects.RADIATION_DAMAGE);
        }
    }
}
