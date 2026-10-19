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
            register("blast.nuke.near", "Nuclear detonation", List.of("blast/nuke_near"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NUKE_MID =
            register("blast.nuke.mid", "Nuclear detonation rumbles", List.of("blast/nuke_mid"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NUKE_FAR =
            register("blast.nuke.far", "Distant nuclear detonation", List.of("blast/nuke_far"), true, 2048);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NUKE_VERY_FAR =
            register("blast.nuke.very_far", "Far-off rumble", List.of("blast/nuke_very_far"), true, 4096);

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

    public static List<Def> definitions() {
        return Collections.unmodifiableList(DEFS);
    }
}
