package dev.groundzero.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import dev.groundzero.GroundZero;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events. Each is declared with its subtitle and the synthesised files that back it (tools/sound), so the
 * sounds.json data generator and the language file both come from here.
 */
public final class GZSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, GroundZero.MODID);

    /** One sound event: its files (relative to assets/groundzero/sounds) and how they are loaded. */
    public record Def(DeferredHolder<SoundEvent, SoundEvent> holder, String subtitleKey, List<String> files,
                      boolean stream, int attenuationDistance) {}

    private static final List<Def> DEFS = new ArrayList<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_CLICK =
            register("geiger.click", "Geiger counter clicks", variants("geiger/click", 4), false, 16);

    // Nuclear detonation as heard at increasing distances; the distance-delayed sound system picks the band.
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NUKE_NEAR =
            register("blast.nuke.near", "Nuclear detonation", blastVariants("blast/nuke_near"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NUKE_MID =
            register("blast.nuke.mid", "Nuclear detonation rumbles", blastVariants("blast/nuke_mid"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NUKE_FAR =
            register("blast.nuke.far", "Distant nuclear detonation", blastVariants("blast/nuke_far"), true, 2048);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NUKE_VERY_FAR =
            register("blast.nuke.very_far", "Far-off rumble", blastVariants("blast/nuke_very_far"), true, 4096);
    // The Tsar's own, heavier and longer (slice 8d).
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_NEAR =
            register("blast.tsar.near", "Thermonuclear detonation", blastVariants("blast/tsar_near"), true, 768);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_MID =
            register("blast.tsar.mid", "Thermonuclear detonation thunders", blastVariants("blast/tsar_mid"), true, 1536);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_FAR =
            register("blast.tsar.far", "Distant thermonuclear detonation", blastVariants("blast/tsar_far"), true, 3072);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_VERY_FAR =
            register("blast.tsar.very_far", "Far-off thunder", blastVariants("blast/tsar_very_far"), true, 6144);

    // Conventional explosions by size, heard near / mid / far / very far (the distance-delayed sound system picks one).
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_NEAR =
            register("blast.small.near", "Small explosion", blastVariants("blast/small_near"), true, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_MID =
            register("blast.small.mid", "Explosion echoes", blastVariants("blast/small_mid"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_FAR =
            register("blast.small.far", "Distant explosion", blastVariants("blast/small_far"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_VERY_FAR =
            register("blast.small.very_far", "Faint distant boom", blastVariants("blast/small_very_far"), true, 2048);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_NEAR =
            register("blast.medium.near", "Explosion", blastVariants("blast/medium_near"), true, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_MID =
            register("blast.medium.mid", "Explosion rumbles", blastVariants("blast/medium_mid"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_FAR =
            register("blast.medium.far", "Distant explosion", blastVariants("blast/medium_far"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_VERY_FAR =
            register("blast.medium.very_far", "Far-off rumble", blastVariants("blast/medium_very_far"), true, 2048);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_NEAR =
            register("blast.large.near", "Huge explosion", blastVariants("blast/large_near"), true, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_MID =
            register("blast.large.mid", "Huge explosion rumbles", blastVariants("blast/large_mid"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_FAR =
            register("blast.large.far", "Distant huge explosion", blastVariants("blast/large_far"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_VERY_FAR =
            register("blast.large.very_far", "Far-off rumble", blastVariants("blast/large_very_far"), true, 2048);

    // Machine work loops (2 s, seamless); played by the client while a machine's ACTIVE state is set.
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_COMBUSTION_GENERATOR =
            register("machine.combustion_generator", "Generator chugs", List.of("machine/combustion_generator"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ORE_CRUSHER =
            register("machine.ore_crusher", "Ore Crusher grinds", List.of("machine/ore_crusher"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ARC_FURNACE =
            register("machine.arc_furnace", "Arc Furnace crackles", List.of("machine/arc_furnace"), false, 20);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_INDUSTRIAL_PRESS =
            register("machine.industrial_press", "Industrial Press thumps", List.of("machine/industrial_press"), false, 20);

    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ELECTROLYTIC_SEPARATOR =
            register("machine.electrolytic_separator", "Electrolytic Separator bubbles", List.of("machine/electrolytic_separator"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_WATER_PUMP =
            register("machine.water_pump", "Water Pump runs", List.of("machine/water_pump"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_CRYOGENIC_CONDENSER =
            register("machine.cryogenic_condenser", "Cryogenic Condenser hisses", List.of("machine/cryogenic_condenser"), false, 20);

    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_CIRCUIT_FABRICATOR =
            register("machine.circuit_fabricator", "Circuit Fabricator whirs", List.of("machine/circuit_fabricator"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_GAS_CENTRIFUGE =
            register("machine.gas_centrifuge", "Gas Centrifuge whines", List.of("machine/gas_centrifuge"), false, 20);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_FUSION_FUEL_SYNTHESIZER =
            register("machine.fusion_fuel_synthesizer", "Fusion Fuel Synthesizer presses", List.of("machine/fusion_fuel_synthesizer"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_CANNING_MACHINE =
            register("machine.canning_machine", "Canning Machine hisses", List.of("machine/canning_machine"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_EXOTIC_MATTER_FABRICATOR =
            register("machine.exotic_matter_fabricator", "Exotic Matter Fabricator thrums", List.of("machine/exotic_matter_fabricator"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_DIESEL_GENERATOR =
            register("machine.diesel_generator", "Diesel Generator rumbles", List.of("machine/diesel_generator"), false, 20);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_RTG =
            register("machine.rtg", "RTG hums", List.of("machine/rtg"), false, 10);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_BATTERY_CHARGER =
            register("machine.battery_charger", "Battery Charger whines", List.of("machine/battery_charger"), false, 10);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_DECONTAMINATION_SHOWER =
            register("machine.decontamination_shower", "Decontamination Shower sprays", List.of("machine/decontamination_shower"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_FUSION_REACTOR =
            register("machine.fusion_reactor", "Fusion Reactor roars", List.of("machine/fusion_reactor"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_SINGULARITY_REACTOR =
            register("machine.singularity_reactor", "Singularity Reactor thrums", List.of("machine/singularity_reactor"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGULARITY_COLLAPSE =
            register("reactor.singularity_collapse", "Singularity collapses", List.of("reactor/singularity_collapse"), false, 64);
    // Thrown explosives (Phase 6).
    public static final DeferredHolder<SoundEvent, SoundEvent> GRENADE_PIN =
            register("grenade.pin", "Grenade pin pulled", List.of("ordnance/grenade_pin"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> DYNAMITE_LIGHT =
            register("dynamite.light", "Fuse fizzes", List.of("ordnance/dynamite_light"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> STICKY_STICK =
            register("sticky.stick", "Sticky grenade sticks", List.of("ordnance/sticky_stick"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> SMOKE_HISS =
            register("smoke.hiss", "Smoke hisses", List.of("ordnance/smoke_hiss"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ASSEMBLY_BENCH =
            register("machine.assembly_bench", "Assembly Bench clanks and ratchets", List.of("machine/assembly_bench"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> ASSEMBLY_CLANK =
            register("assembly.clank", "Warhead assembled", List.of("machine/assembly_clank"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_ARM =
            register("charge.arm", "Charge armed", List.of("ordnance/charge_arm"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_DISARM =
            register("charge.disarm", "Charge made safe", List.of("ordnance/charge_disarm"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB_BEEP =
            register("bomb.beep", "Bomb beeps", List.of("ordnance/bomb_beep"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> THERMITE_BURN =
            register("thermite.burn", "Thermite roars", List.of("ordnance/thermite_burn"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_CLICK =
            register("detonator.click", "Detonator clicks", List.of("ordnance/detonator_click"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRE_SNIP =
            register("wire.snip", "Wire cut", List.of("ordnance/wire_snip"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_CLICK =
            register("mine.click", "Mine clicks", List.of("ordnance/mine_click"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_POP =
            register("mine.pop", "Mine pops up", List.of("ordnance/mine_pop"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> FUEL_DISPERSE =
            register("bomb.fuel_disperse", "Fuel cloud bursts out", List.of("ordnance/fuel_disperse"), false, 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB_DRILL =
            register("bomb.drill", "Bunker buster bores", List.of("ordnance/bomb_drill"), false, 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_ZAP =
            register("emp.zap", "EMP discharges", List.of("effect/emp_zap"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYO_BURST =
            register("cryo.burst", "Cryo burst freezes", List.of("effect/cryo_burst"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB_WHISTLE =
            register("bomb.whistle", "Bomb whistles down", List.of("ordnance/bomb_whistle"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_SIREN =
            register("launch.siren", "Launch siren wails", List.of("launch/siren"), false, 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_LAUNCH =
            register("missile.launch", "Missile lights", List.of("launch/missile_launch"), false, 160);
    // The superweapons (DESIGN.md §19.5): the announcer's warning tone and its ready chime, heard by everyone in the
    // dimension; the SCUD Storm's erectors raising their missiles.
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERWEAPON_WARNING =
            register("superweapon.warning", "Superweapon warning sounds", List.of("superweapon/warning"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERWEAPON_READY =
            register("superweapon.ready", "Superweapon ready", List.of("superweapon/ready"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> SCUD_ERECT =
            register("scud_storm.erect", "Erectors raise the SCUDs", List.of("superweapon/scud_erect"), false, 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> NUCLEAR_ERECT =
            register("nuclear_missile.erect", "The roof opens and the missile is raised", List.of("superweapon/nuclear_erect"), false, 128);
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_ENGINE =
            register("missile.engine", "Missile roars overhead", List.of("launch/missile_engine_loop"), false, 256);
    // Slice 9a: a stage parting in flight, and a warhead's nose coming back down.
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_STAGING =
            register("missile.staging", "Missile stage separates", List.of("launch/stage_separation"), false, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_REENTRY =
            register("missile.reentry", "Missile screams down", List.of("launch/reentry_loop"), false, 192);
    // Slice 9b: the Missile Silo's doors unlatching and grinding apart (or shut).
    public static final DeferredHolder<SoundEvent, SoundEvent> SILO_DOORS =
            register("silo.doors", "Silo doors grind", List.of("launch/silo_doors"), false, 64);
    // Slice 9c (DESIGN.md §17.11): a cruise missile's box cover banging open and its booster's roar out of the box; its
    // jet's whine following it; the Cruise Missile Launcher's rams raising (or lowering) its boxes.
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_LAUNCH =
            register("cruise.launch", "Cruise missile fires from its box", List.of("launch/cruise_launch"), false, 128);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_ENGINE =
            register("cruise.engine", "Cruise missile whines past", List.of("launch/cruise_engine_loop"), false, 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_ELEVATE =
            register("cruise.elevate", "Launcher boxes rise", List.of("launch/cruise_elevate"), false, 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_ALARM =
            register("reactor.alarm", "Reactor alarm wails", List.of("reactor/alarm"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_FISSION_REACTOR =
            register("machine.fission_reactor", "Reactor hums", List.of("machine/fission_reactor"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_PARTICLE_ACCELERATOR =
            register("machine.particle_accelerator", "Particle Accelerator thrums", List.of("machine/particle_accelerator"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_CHEMICAL_PLANT =
            register("machine.chemical_plant", "Chemical Plant churns", List.of("machine/chemical_plant"), false, 20);

    private GZSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name, String subtitle, List<String> files,
            boolean stream, int attenuationDistance) {
        DeferredHolder<SoundEvent, SoundEvent> holder = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(GroundZero.id(name)));
        String subtitleKey = GZLang.add("subtitles." + GroundZero.MODID + "." + name, subtitle);
        DEFS.add(new Def(holder, subtitleKey, files, stream, attenuationDistance));
        return holder;
    }

    private static List<String> variants(String base, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> base + i).toList();
    }

    /**
     * A blast sound's files: the one rated in the Sound Lab and its two variants, {@code _2} and {@code _3}
     * (tools/sound/blasts.py, Vary): the game picks one at random each time, so blasts in a row differ.
     */
    private static List<String> blastVariants(String base) {
        return List.of(base, base + "_2", base + "_3");
    }

    /** A blast sound by set (small, medium, large, nuke, tsar) and distance band (0 near, 1 mid, 2 far, 3 very far). */
    public static SoundEvent blast(String set, int band) {
        List<DeferredHolder<SoundEvent, SoundEvent>> sounds = switch (set) {
            case "small" -> List.of(BLAST_SMALL_NEAR, BLAST_SMALL_MID, BLAST_SMALL_FAR, BLAST_SMALL_VERY_FAR);
            case "large" -> List.of(BLAST_LARGE_NEAR, BLAST_LARGE_MID, BLAST_LARGE_FAR, BLAST_LARGE_VERY_FAR);
            case "nuke" -> List.of(BLAST_NUKE_NEAR, BLAST_NUKE_MID, BLAST_NUKE_FAR, BLAST_NUKE_VERY_FAR);
            case "tsar" -> List.of(BLAST_TSAR_NEAR, BLAST_TSAR_MID, BLAST_TSAR_FAR, BLAST_TSAR_VERY_FAR);
            default -> List.of(BLAST_MEDIUM_NEAR, BLAST_MEDIUM_MID, BLAST_MEDIUM_FAR, BLAST_MEDIUM_VERY_FAR);
        };
        return sounds.get(Math.max(0, Math.min(3, band))).get();
    }

    public static List<Def> definitions() {
        return Collections.unmodifiableList(DEFS);
    }
}
