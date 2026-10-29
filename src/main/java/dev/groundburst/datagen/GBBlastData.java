package dev.groundburst.datagen;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.BlastProfile;
import dev.groundburst.blast.Blasts;
import dev.groundburst.blast.TerrainTable;
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
 * the {@code /groundburst blast} command and the GameTests; each weapon gets its own profile when it is built.
 */
public final class GBBlastData {
    private GBBlastData() {}

    public static void gather(GatherDataRegistryEntriesEvent event) {
        event.add(BlastProfile.REGISTRY, GBBlastData::profiles);
        event.add(Registries.DAMAGE_TYPE, GBBlastData::damageTypes);
    }

    private static BlastProfile profile(float core, float crater, float structural, float scorch, float fire, float lethal, float damage,
            float frontSpeed, float craterDepth, float lobes, float streaks, float ejecta, float lethalDamage, float edgeDamage,
            float knockback, Identifier terrain, Optional<Boolean> instant) {
        return new BlastProfile(core, crater, structural, scorch, fire,
                new BlastProfile.EntityZones(lethal, damage, lethalDamage, edgeDamage, knockback), frontSpeed, craterDepth, lobes, streaks,
                ejecta, 0f, terrain, instant, Optional.empty(), Optional.empty());
    }

    /** A profile drawn with a particular visual set (its sound set stays chosen by size). */
    private static BlastProfile visual(BlastProfile p, String set) {
        return new BlastProfile(p.core(), p.crater(), p.structural(), p.scorch(), p.fire(), p.entities(), p.frontSpeed(), p.craterDepth(),
                p.lobes(), p.streaks(), p.ejecta(), p.drops(), p.terrain(), p.instant(), p.sound(), Optional.of(set));
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
        // The Fission Reactor's meltdown: a dirty, smoky blast that takes the reactor hall and everything round it (DESIGN.md
        // §7.1). Not instant: its front takes a few ticks, and the corium is poured once it has passed.
        ctx.register(Blasts.profile("reactor_meltdown"), new BlastProfile(5, 16, 28, 30, 24, new BlastProfile.EntityZones(16, 36, 80, 20, 3.0f), 3,
                0.6f, 0.15f, 0.35f, 0.35f, 0f, TerrainTable.CONVENTIONAL, Optional.of(false), Optional.of("large"), Optional.of("meltdown")));
        // Thrown explosives (DESIGN.md §14), all instant: small.
        // core, crater, structural, scorch, fire, lethal, damage, speed, depth, lobes, streaks, ejecta, lethal dmg, edge dmg, knockback
        ctx.register(Blasts.profile("dynamite"), profile(1.5f, 3.5f, 5.5f, 4, 0, 3.5f, 8, 4, 0.7f, 0.1f, 0.3f, 0.3f, 30, 10, 1.5f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("frag_grenade"), profile(0.5f, 2, 3, 2, 0, 4, 8, 4, 0.5f, 0.1f, 0.25f, 0.2f, 20, 6, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        // Placed charges (slice 6b). C4 is scaled by the cube root of how many are stacked.
        ctx.register(Blasts.profile("c4"), profile(2, 4.5f, 7, 5, 0, 5, 11, 4, 0.75f, 0.12f, 0.3f, 0.4f, 40, 12, 1.8f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("breaching_charge"), profile(0, 0.6f, 2, 1, 0, 1.5f, 4, 4, 0.5f, 0, 0, 0, 10, 4, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("timed_bomb"), profile(3.5f, 9, 13, 10, 0, 9, 20, 4, 0.75f, 0.12f, 0.3f, 0.4f, 60, 18, 2.5f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        // Mines (slice 6c): the bouncing mine bursts in the air (a small crater, fragments do the work).
        ctx.register(Blasts.profile("landmine"), profile(1, 2.5f, 4, 3, 0, 3, 7, 4, 0.7f, 0.1f, 0.3f, 0.3f, 30, 9, 1.4f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("bouncing_mine"), profile(0.3f, 1, 3, 2, 0, 4, 9, 4, 0.5f, 0, 0.1f, 0.1f, 25, 8, 1.2f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        // Bombs (slice 6d). The thermobaric's crater is weak (shallow) but its overpressure reaches far and burns.
        ctx.register(Blasts.profile("he_warhead"), visual(profile(2.5f, 6, 10, 8, 0, 7, 14, 4, 0.75f, 0.12f, 0.3f, 0.4f, 50, 15, 2.2f,
                TerrainTable.CONVENTIONAL, Optional.empty()), "he"));
        ctx.register(Blasts.profile("heavy_warhead"), visual(profile(5, 12, 20, 16, 0, 14, 28, 3, 0.75f, 0.15f, 0.3f, 0.5f, 80, 25, 3.0f,
                TerrainTable.CONVENTIONAL, Optional.empty()), "heavy"));
        ctx.register(Blasts.profile("thermobaric_warhead"), visual(profile(3, 12, 24, 20, 16, 24, 40, 3, 0.45f, 0.1f, 0.2f, 0.3f, 70, 30, 3.5f,
                TerrainTable.CONVENTIONAL, Optional.empty()), "thermobaric"));
        ctx.register(Blasts.profile("cluster_warhead"), profile(0, 1.5f, 3, 2, 0, 2, 5, 4, 0.5f, 0, 0.1f, 0.1f, 15, 6, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        // Bomblets: each a grenade-and-a-half (after the in-game look: they were too weak to matter).
        ctx.register(Blasts.profile("cluster_bomblet"), profile(1.5f, 4, 6, 5, 0, 4.5f, 10, 4, 0.75f, 0.12f, 0.3f, 0.35f, 40, 12, 1.8f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        // Gas, smoke, cryo and EMP warheads: only their casing bursting; the toxic waste warhead: a small burst under its splash.
        ctx.register(Blasts.profile("gas_warhead"), profile(0, 1.5f, 3, 2, 0, 2, 5, 4, 0.5f, 0, 0.1f, 0.1f, 15, 6, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("smoke_warhead"), profile(0, 1.5f, 3, 2, 0, 2, 5, 4, 0.5f, 0, 0.1f, 0.1f, 15, 6, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("cryo_warhead"), profile(0, 1.5f, 3, 2, 0, 2, 5, 4, 0.5f, 0, 0.1f, 0.1f, 15, 6, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("emp_warhead"), profile(0, 1.5f, 3, 2, 0, 2, 5, 4, 0.5f, 0, 0.1f, 0.1f, 15, 6, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("chemical_warhead"), profile(0, 1.5f, 3, 2, 0, 2, 5, 4, 0.5f, 0, 0.1f, 0.1f, 15, 6, 1.0f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("toxic_waste_warhead"), profile(0.5f, 2.5f, 4, 4, 0, 3, 8, 4, 0.6f, 0.1f, 0.2f, 0.2f, 25, 8, 1.2f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("bunker_buster_warhead"), profile(3.5f, 9, 14, 10, 0, 10, 20, 4, 0.9f, 0.12f, 0.3f, 0.4f, 60, 20, 2.5f,
                TerrainTable.CONVENTIONAL, Optional.empty()));
        ctx.register(Blasts.profile("napalm_warhead"), profile(0.5f, 2, 4, 6, 6, 3, 8, 4, 0.6f, 0.1f, 0.2f, 0.2f, 25, 8, 1.2f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        // The Massive Ordnance Warhead: a fireball, smoke column and dust ring (its own visual set; the large sounds).
        ctx.register(Blasts.profile("massive_warhead"), new BlastProfile(10, 28, 40, 34, 10, new BlastProfile.EntityZones(40, 70, 200, 60, 5.0f), 2.5f,
                0.7f, 0.15f, 0.3f, 0.6f, 0f, TerrainTable.CONVENTIONAL, Optional.empty(), Optional.of("large"), Optional.of("massive_warhead")));
        // Nuclear warheads (Phase 8, DESIGN.md §15): the nuclear terrain table, the nuke look and sound (the mushroom cloud
        // scales with the crater). The smallest nuke is half as big again as the Massive Ordnance Warhead (crater 28), and
        // each one above it a good step bigger (after the in-game look: the first tactical nuke was smaller than the MOW).
        // A fizzle is only its lenses going off unevenly.
        ctx.register(Blasts.profile("tactical_nuclear_warhead"), new BlastProfile(12, 42, 60, 75, 70, new BlastProfile.EntityZones(60, 105, 1000, 60, 4.5f),
                3, 0.45f, 0.15f, 0.35f, 0.5f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("nuke"), Optional.of("nuke")));
        ctx.register(Blasts.profile("fission_warhead"), new BlastProfile(22, 75, 110, 140, 130, new BlastProfile.EntityZones(120, 200, 1000, 80, 5.0f),
                3.5f, 0.4f, 0.2f, 0.35f, 0.5f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("nuke"), Optional.of("nuke")));
        ctx.register(Blasts.profile("boosted_fission_warhead"), new BlastProfile(28, 95, 140, 175, 165, new BlastProfile.EntityZones(150, 250, 1000, 100, 5.5f),
                3.5f, 0.4f, 0.2f, 0.35f, 0.5f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("nuke"), Optional.of("nuke")));
        // Slice 8c, whole weapons: a two-stage thermonuclear warhead, and the cobalt-salted one (a smaller blast, the fallout
        // is the point).
        ctx.register(Blasts.profile("thermonuclear_warhead"), new BlastProfile(45, 150, 220, 280, 260, new BlastProfile.EntityZones(260, 420, 1000, 140, 6.5f),
                4.5f, 0.35f, 0.2f, 0.35f, 0.5f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("nuke"), Optional.of("nuke")));
        ctx.register(Blasts.profile("cobalt_warhead"), new BlastProfile(18, 70, 105, 130, 110, new BlastProfile.EntityZones(110, 180, 1000, 80, 5.0f),
                3.5f, 0.4f, 0.2f, 0.3f, 0.5f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("nuke"), Optional.of("nuke")));
        // Slice 8d: the Tsar, the biggest weapon there is; it wrecks and burns further than the engine's old limit (320).
        ctx.register(Blasts.profile("tsar_warhead"), new BlastProfile(80, 250, 380, 480, 450, new BlastProfile.EntityZones(450, 700, 1000, 160, 8f),
                5f, 0.3f, 0.2f, 0.35f, 0.5f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("tsar"), Optional.of("tsar")));
        // The superweapons' warheads (DESIGN.md §19.5). A toxin SCUD: a heavy warhead's blast a third bigger and harder (the
        // user's rebalance; its toxin does the rest, Charges.scud); China's Nuclear Missile: just under the fission warhead's
        // blast, a fifth down on its first figures (the user's rebalance: radii and damage x0.8).
        ctx.register(Blasts.profile("scud_storm_warhead"), visual(profile(5.3f, 13.3f, 21.3f, 18.6f, 5.3f, 16, 32, 3, 0.7f, 0.15f, 0.3f, 0.45f, 106, 29,
                4.0f, TerrainTable.CONVENTIONAL, Optional.empty()), "heavy"));
        ctx.register(Blasts.profile("nuclear_missile_warhead"), new BlastProfile(20, 68, 100, 128, 120, new BlastProfile.EntityZones(108, 180, 800, 72, 4.2f),
                3.5f, 0.4f, 0.2f, 0.35f, 0.5f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("nuke"), Optional.of("nuke")));
        // The neutron warhead's blast is small: its neutrons do the killing (NeutronBurst).
        ctx.register(Blasts.profile("neutron_warhead"), new BlastProfile(4, 10, 16, 14, 12, new BlastProfile.EntityZones(10, 26, 200, 20, 2.5f),
                3, 0.5f, 0.15f, 0.3f, 0.4f, 0f, TerrainTable.NUCLEAR, Optional.empty(), Optional.of("nuke"), Optional.of("nuke")));
        // The dirty warhead is HE and waste: a small conventional blast.
        ctx.register(Blasts.profile("dirty_warhead"), new BlastProfile(1, 6, 9, 8, 5, new BlastProfile.EntityZones(10, 16, 60, 12, 2.0f),
                4, 0.7f, 0.1f, 0.3f, 0.3f, 0f, TerrainTable.CONVENTIONAL, Optional.empty(), Optional.of("medium"), Optional.of("he")));
        ctx.register(Blasts.profile("nuclear_fizzle"), new BlastProfile(1, 3, 5, 4, 0, new BlastProfile.EntityZones(3, 7, 40, 10, 1.5f),
                4, 0.7f, 0.1f, 0.3f, 0.3f, 0f, TerrainTable.CONVENTIONAL, Optional.empty(), Optional.of("medium"), Optional.of("he")));
        ctx.register(Blasts.profile("sticky_grenade"), profile(1, 3, 5, 3, 0, 3, 7, 4, 0.7f, 0.1f, 0.3f, 0.3f, 30, 8, 1.2f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
        ctx.register(Blasts.profile("incendiary_grenade"), profile(0, 1, 2, 4, 4, 1.5f, 4, 4, 0.7f, 0f, 0f, 0f, 8, 4, 0.6f,
                TerrainTable.CONVENTIONAL, Optional.of(true)));
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
        ctx.register(Blasts.BLAST_DAMAGE, new DamageType(GroundBurst.MODID + ".blast", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f));
        ctx.register(dev.groundburst.content.ordnance.GBOrdnance.SHRAPNEL_DAMAGE,
                new DamageType(GroundBurst.MODID + ".shrapnel", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f));
        ctx.register(dev.groundburst.content.nuclear.GBEffects.RADIATION_DAMAGE,
                new DamageType(GroundBurst.MODID + ".radiation", DamageScaling.NEVER, 0f));
        ctx.register(dev.groundburst.content.ordnance.GBOrdnance.TOXIN_DAMAGE, new DamageType(GroundBurst.MODID + ".toxin", DamageScaling.NEVER, 0f));
        // Phase 11 (DESIGN.md §16.3): the antimatter warhead's sphere and its gamma flash.
        ctx.register(dev.groundburst.blast.Annihilations.ANNIHILATED, new DamageType(GroundBurst.MODID + ".annihilated", DamageScaling.NEVER, 0f));
        ctx.register(dev.groundburst.content.exotic.Antimatter.GAMMA_FLASH, new DamageType(GroundBurst.MODID + ".gamma_flash", DamageScaling.NEVER, 0f));
    }

    /** {@code groundburst:blast} counts as an explosion (Blast Protection, explosion-proof mobs). */
    public static final class DamageTags extends DamageTypeTagsProvider {
        public DamageTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, GroundBurst.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            tag(DamageTypeTags.IS_EXPLOSION).add(Blasts.BLAST_DAMAGE);
            tag(DamageTypeTags.IS_PROJECTILE).add(dev.groundburst.content.ordnance.GBOrdnance.SHRAPNEL_DAMAGE);
            // Radiation sickness: armour does not help, and it does not push anyone about.
            tag(DamageTypeTags.BYPASSES_ARMOR).add(dev.groundburst.content.nuclear.GBEffects.RADIATION_DAMAGE,
                    dev.groundburst.content.ordnance.GBOrdnance.TOXIN_DAMAGE);
            tag(DamageTypeTags.NO_KNOCKBACK).add(dev.groundburst.content.nuclear.GBEffects.RADIATION_DAMAGE,
                    dev.groundburst.content.ordnance.GBOrdnance.TOXIN_DAMAGE);
            // Annihilation: nothing stops it but a creative player's invulnerability; a gamma flash goes through armour.
            var annihilated = dev.groundburst.blast.Annihilations.ANNIHILATED;
            var gamma = dev.groundburst.content.exotic.Antimatter.GAMMA_FLASH;
            tag(DamageTypeTags.BYPASSES_ARMOR).add(annihilated, gamma);
            tag(DamageTypeTags.NO_KNOCKBACK).add(annihilated, gamma);
            tag(DamageTypeTags.BYPASSES_SHIELD).add(annihilated, gamma);
            tag(DamageTypeTags.BYPASSES_EFFECTS).add(annihilated);
            tag(DamageTypeTags.BYPASSES_RESISTANCE).add(annihilated);
            tag(DamageTypeTags.BYPASSES_ENCHANTMENTS).add(annihilated);
            tag(DamageTypeTags.BYPASSES_COOLDOWN).add(annihilated);
            tag(DamageTypeTags.BYPASSES_WOLF_ARMOR).add(annihilated, gamma);
            tag(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS).add(annihilated);
        }
    }
}
