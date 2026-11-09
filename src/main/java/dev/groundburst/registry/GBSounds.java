package dev.groundburst.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import dev.groundburst.GroundBurst;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events. Each is declared with its subtitle and the synthesised files that back it (tools/sound), so the
 * sounds.json data generator and the language file both come from here.
 */
public final class GBSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, GroundBurst.MODID);

    /** One sound event: its files (relative to assets/groundburst/sounds) and how they are loaded. */
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
    // The Tsar's own, heavier and longer (slice 8d).
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_NEAR =
            register("blast.tsar.near", "Thermonuclear detonation", List.of("blast/tsar_near"), true, 768);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_MID =
            register("blast.tsar.mid", "Thermonuclear detonation thunders", List.of("blast/tsar_mid"), true, 1536);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_FAR =
            register("blast.tsar.far", "Distant thermonuclear detonation", List.of("blast/tsar_far"), true, 3072);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TSAR_VERY_FAR =
            register("blast.tsar.very_far", "Far-off thunder", List.of("blast/tsar_very_far"), true, 6144);

    // Conventional explosions by size, heard near / mid / far / very far (the distance-delayed sound system picks one).
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_NEAR =
            register("blast.small.near", "Small explosion", List.of("blast/small_near"), true, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_MID =
            register("blast.small.mid", "Explosion echoes", List.of("blast/small_mid"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_FAR =
            register("blast.small.far", "Distant explosion", List.of("blast/small_far"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_VERY_FAR =
            register("blast.small.very_far", "Faint distant boom", List.of("blast/small_very_far"), true, 2048);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_NEAR =
            register("blast.medium.near", "Explosion", List.of("blast/medium_near"), true, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_MID =
            register("blast.medium.mid", "Explosion rumbles", List.of("blast/medium_mid"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_FAR =
            register("blast.medium.far", "Distant explosion", List.of("blast/medium_far"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_MEDIUM_VERY_FAR =
            register("blast.medium.very_far", "Far-off rumble", List.of("blast/medium_very_far"), true, 2048);
    // Charges going off under water (DESIGN.md §14.6): a deep thud, the column going up and its water falling back.
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NAVAL_NEAR =
            register("blast.naval.near", "Underwater explosion", List.of("blast/naval_near"), true, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NAVAL_MID =
            register("blast.naval.mid", "Underwater explosion, water falling", List.of("blast/naval_mid"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NAVAL_FAR =
            register("blast.naval.far", "Distant underwater explosion", List.of("blast/naval_far"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NAVAL_VERY_FAR =
            register("blast.naval.very_far", "Far-off thud", List.of("blast/naval_very_far"), true, 2048);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_NEAR =
            register("blast.large.near", "Huge explosion", List.of("blast/large_near"), true, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_MID =
            register("blast.large.mid", "Huge explosion rumbles", List.of("blast/large_mid"), true, 512);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_FAR =
            register("blast.large.far", "Distant huge explosion", List.of("blast/large_far"), true, 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_LARGE_VERY_FAR =
            register("blast.large.very_far", "Far-off rumble", List.of("blast/large_very_far"), true, 2048);

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
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_CRYSTAL_PULLER =
            register("machine.crystal_puller", "Crystal Puller hums", List.of("machine/crystal_puller"), false, 16);

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
    /** The Depth Charge Rack's trap letting a charge go: its clack, the drum rumbling off over the edge (DESIGN.md §14.6). */
    public static final DeferredHolder<SoundEvent, SoundEvent> RACK_RELEASE =
            register("naval.rack_release", "Depth charge rolls off", List.of("naval/rack_release"), false, 24);
    /** The Blast Door (DESIGN.md §22.3): its handwheel drawing the bolts, the leaf swinging, the leaf home on its frame, the bolts thrown. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_DOOR_UNLOCK =
            register("door.blast_door_unlock", "Blast door unbolts", List.of("door/blast_door_unlock"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_DOOR_SWING =
            register("door.blast_door_swing", "Blast door swings", List.of("door/blast_door_swing"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_DOOR_SEAT =
            register("door.blast_door_seat", "Blast door thuds shut", List.of("door/blast_door_seat"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_DOOR_LOCK =
            register("door.blast_door_lock", "Blast door bolts", List.of("door/blast_door_lock"), false, 24);
    /** The Bunker Hatch and the Pressure Door (DESIGN.md §22.3): the bunker set's open and shut sounds. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BUNKER_HATCH_OPEN =
            register("door.bunker_hatch_open", "Hatch opens", List.of("door/bunker_hatch_open"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> BUNKER_HATCH_CLOSE =
            register("door.bunker_hatch_close", "Hatch shuts", List.of("door/bunker_hatch_close"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESSURE_DOOR_OPEN =
            register("door.pressure_door_open", "Pressure door opens", List.of("door/pressure_door_open"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESSURE_DOOR_CLOSE =
            register("door.pressure_door_close", "Pressure door shuts", List.of("door/pressure_door_close"), false, 16);
    /** The command room (DESIGN.md §22.4): the Filing Cabinet's drawer, the Tape Computer's switch (it runs silent). */
    public static final DeferredHolder<SoundEvent, SoundEvent> FILING_CABINET_OPEN =
            register("door.filing_cabinet_open", "Drawer opens", List.of("door/filing_cabinet_open"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> FILING_CABINET_CLOSE =
            register("door.filing_cabinet_close", "Drawer shuts", List.of("door/filing_cabinet_close"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> TAPE_SWITCH =
            register("machine.tape_switch", "Switch clicks", List.of("machine/tape_switch"), false, 12);
    /** The bunker fittings (DESIGN.md §22.5): the Steel Locker's door, the Pipe Valve's wheel, the Fuse Box's breaker, the
     *  Bunker Lamp's switch, the Fire Extinguisher's burst. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STEEL_LOCKER_OPEN =
            register("door.steel_locker_open", "Locker opens", List.of("door/steel_locker_open"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> STEEL_LOCKER_CLOSE =
            register("door.steel_locker_close", "Locker shuts", List.of("door/steel_locker_close"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_VALVE_TURN =
            register("fitting.pipe_valve_turn", "Valve wheel turns", List.of("fitting/pipe_valve_turn"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSE_BOX_SWITCH =
            register("fitting.fuse_box_switch", "Breaker clunks", List.of("fitting/fuse_box_switch"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_SWITCH =
            register("fitting.lamp_switch", "Lamp switch clicks", List.of("fitting/lamp_switch"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTINGUISHER_SPRAY =
            register("fitting.extinguisher_spray", "Extinguisher sprays", List.of("fitting/extinguisher_spray"), false, 16);
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
    // Artillery (DESIGN.md §20.1): the mortar firing (two takes), and a shell's whistle in its last two seconds.
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_FIRE =
            register("artillery.mortar_fire", "Mortar fires", variants("artillery/mortar_fire", 2), false, 128);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELL_INCOMING =
            register("artillery.shell_incoming", "Shell whistles in", List.of("artillery/shell_incoming"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> HOWITZER_FIRE =
            register("artillery.howitzer_fire", "Howitzer fires", variants("artillery/howitzer_fire", 2), false, 256);
    // The base alarm system (DESIGN.md §18.6): the Air-Raid Siren's two signals, the Klaxon's five tones, the Alarm Bell
    // (all loops, played while they sound), and the Missile Warning Receiver's attention tone.
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_SIREN_WAIL =
            register("alarm.siren_wail", "Air-raid siren wails", List.of("alarm/siren_wail"), true, 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_SIREN_STEADY =
            register("alarm.siren_steady", "Air-raid siren sounds steady", List.of("alarm/siren_steady"), true, 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_KLAXON =
            register("alarm.klaxon", "Klaxon blares", List.of("alarm/klaxon"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_WHOOP =
            register("alarm.whoop", "Alarm whoops", List.of("alarm/whoop"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_EVACUATE =
            register("alarm.evacuate", "Evacuation alarm sounds", List.of("alarm/evacuate"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_HILO =
            register("alarm.hilo", "Two-tone alarm sounds", List.of("alarm/hilo"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_GENERAL =
            register("alarm.general", "General alarm sounds", List.of("alarm/general"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_BELL =
            register("alarm.bell", "Alarm bell rings", List.of("alarm/bell"), false, 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_ATTENTION =
            register("alarm.attention", "Missile warning tone", List.of("alarm/attention"), false, 32);
    // The Launch Control Station (DESIGN.md §17.12): its key switch, the LAUNCH button and its tones, the teletype.
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_KEY =
            register("station.key", "Launch key turns", List.of("launch/station_key"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_LAUNCH =
            register("station.launch", "Launch ordered", List.of("launch/station_launch"), false, 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_TELETYPE =
            register("station.teletype", "Teletype prints a report", List.of("launch/station_teletype"), false, 16);
    // The Laser Designator (DESIGN.md §17.13): switched on, a lock, fire called.
    public static final DeferredHolder<SoundEvent, SoundEvent> DESIGNATOR_ON =
            register("designator.on", "Laser designator hums", List.of("launch/designator_on"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> DESIGNATOR_LOCK =
            register("designator.lock", "Designator locks on", List.of("launch/designator_lock"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> DESIGNATOR_FIRE =
            register("designator.fire", "Fire called", List.of("launch/designator_fire"), false, 16);
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
    /** The Large Silo Door's leaves sliding open or shut: eight seconds, as long as they take (DESIGN.md §22.2). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LARGE_SILO_DOOR =
            register("silo.large_door", "Large silo door grinds", List.of("construction/large_silo_door"), false, 64);
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
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_CLEAN_ROOM =
            register("machine.clean_room", "Clean Room's fans whir", List.of("machine/clean_room"), false, 20);

    // Radiation instruments and medicine (DESIGN.md §21.8): the Geiger Counter's alarm and its switch, the Lead
    // Container's lid, the Anti-Rad Injector.
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_ALARM =
            register("geiger.alarm", "Geiger counter alarm beeps", List.of("geiger/alarm"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_SWITCH =
            register("geiger.switch", "Geiger counter switches", List.of("geiger/switch"), false, 8);
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAD_CONTAINER_OPEN =
            register("lead_container.open", "Lead container opens", List.of("gear/lead_container_open"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAD_CONTAINER_CLOSE =
            register("lead_container.close", "Lead container closes", List.of("gear/lead_container_close"), false, 12);
    public static final DeferredHolder<SoundEvent, SoundEvent> MEDICAL_INJECTOR =
            register("medical.injector", "Injector hisses", List.of("gear/injector"), false, 12);
    // Exotic warheads (Phase 11, DESIGN.md §16): played by the client when the sound arrives (AnnihilationFx).
    public static final DeferredHolder<SoundEvent, SoundEvent> ANTIMATTER_NEAR =
            register("antimatter.near", "Antimatter annihilates", List.of("exotic/antimatter_near"), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> ANTIMATTER_FAR =
            register("antimatter.far", "Distant annihilation", List.of("exotic/antimatter_far"), false, 64);
    /** The singularity's drone (looped while it grows, pitched up as it does) and the ground tearing loose round it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGULARITY_DRONE =
            register("singularity.drone", "Singularity drones", List.of("exotic/singularity_drone"), false, 80);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGULARITY_TEAR =
            register("singularity.tear", "Ground tears loose", variants("exotic/singularity_tear", 3), false, 48);
    /** The storm warhead's supercell (DESIGN.md §16.14): its wind, and its thunder near and far. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_WIND =
            register("storm.wind", "Storm winds howl", List.of("exotic/storm_wind"), false, 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_THUNDER_NEAR =
            register("storm.thunder_near", "Lightning cracks", variants("exotic/storm_thunder_near", 3), false, 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_THUNDER_FAR =
            register("storm.thunder_far", "Thunder rolls", variants("exotic/storm_thunder_far", 3), false, 320);
    /**
     * The cryogenic warhead's deep freeze (DESIGN.md §16.16): its burst (a thump and the roar of its cryogen boiling off,
     * ice cracking through it), its front crackling past, a spire of ice groaning up, its cold snap's wind.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYO_ICBM_BURST =
            register("cryo.icbm_burst", "Cryo bomb bursts", List.of("exotic/cryo_icbm_burst"), false, 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYO_FRONT =
            register("cryo.front", "Ice cracks across the land", variants("exotic/cryo_front", 2), false, 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYO_SPIRE =
            register("cryo.spire", "Ice spire bursts up", variants("exotic/cryo_spire", 2), false, 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYO_WIND =
            register("cryo.wind", "Freezing wind whistles", List.of("exotic/cryo_wind"), false, 96);
    /**
     * The Particle Uplink Cannon (DESIGN.md §19.6): its charged core crackling; the uplink, the beam going up out of its
     * pit to the satellite; the beam striking from orbit, its roar as it burns (looped, following it) and its dying away.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> PARTICLE_CHARGED =
            register("particle_cannon.charged", "Particle Cannon crackles", List.of("superweapon/particle_charged"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> PARTICLE_UPLINK =
            register("particle_cannon.uplink", "Particle Cannon fires its uplink", List.of("superweapon/particle_uplink"), false, 192);
    public static final DeferredHolder<SoundEvent, SoundEvent> PARTICLE_STRIKE =
            register("particle_cannon.strike", "Particle beam strikes", List.of("superweapon/particle_strike"), false, 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> PARTICLE_BEAM =
            register("particle_cannon.beam", "Particle beam roars", List.of("superweapon/particle_beam_loop"), false, 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> PARTICLE_FADE =
            register("particle_cannon.fade", "Particle beam dies away", List.of("superweapon/particle_fade"), false, 128);
    // The war room (DESIGN.md §17.17): the Seismograph's drum and pen, the War Room Display's new contact, the Dead Hand.
    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMOGRAPH_LOOP =
            register("machine.seismograph", "Seismograph's drum turns", List.of("machine/seismograph_loop"), false, 10);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMOGRAPH_EVENT =
            register("seismograph.event", "Seismograph records a blast", List.of("machine/seismograph_event"), false, 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> WAR_ROOM_CONTACT =
            register("war_room.contact", "New contact on the war room display", List.of("launch/war_room_contact"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> DEAD_HAND_ALERT =
            register("dead_hand.alert", "Dead Hand counts down", List.of("launch/dead_hand_alert"), false, 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> DEAD_HAND_LID =
            register("dead_hand.lid", "Command rocket's tube opens", List.of("launch/dead_hand_lid"), false, 32);
    /** The singularity's end (DESIGN.md §16.12): an inrush, then the thud as its horizon closes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGULARITY_IMPLODE =
            register("singularity.implode", "Singularity implodes", List.of("exotic/singularity_implode"), false, 160);

    private GBSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name, String subtitle, List<String> files,
            boolean stream, int attenuationDistance) {
        DeferredHolder<SoundEvent, SoundEvent> holder = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(GroundBurst.id(name)));
        String subtitleKey = GBLang.add("subtitles." + GroundBurst.MODID + "." + name, subtitle);
        DEFS.add(new Def(holder, subtitleKey, files, stream, attenuationDistance));
        return holder;
    }

    private static List<String> variants(String base, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> base + i).toList();
    }

    /** A blast sound by set (small, medium, large, nuke, tsar) and distance band (0 near, 1 mid, 2 far, 3 very far). */
    public static SoundEvent blast(String set, int band) {
        List<DeferredHolder<SoundEvent, SoundEvent>> sounds = switch (set) {
            case "small" -> List.of(BLAST_SMALL_NEAR, BLAST_SMALL_MID, BLAST_SMALL_FAR, BLAST_SMALL_VERY_FAR);
            case "large" -> List.of(BLAST_LARGE_NEAR, BLAST_LARGE_MID, BLAST_LARGE_FAR, BLAST_LARGE_VERY_FAR);
            case "nuke" -> List.of(BLAST_NUKE_NEAR, BLAST_NUKE_MID, BLAST_NUKE_FAR, BLAST_NUKE_VERY_FAR);
            case "tsar" -> List.of(BLAST_TSAR_NEAR, BLAST_TSAR_MID, BLAST_TSAR_FAR, BLAST_TSAR_VERY_FAR);
            case "naval" -> List.of(BLAST_NAVAL_NEAR, BLAST_NAVAL_MID, BLAST_NAVAL_FAR, BLAST_NAVAL_VERY_FAR);
            default -> List.of(BLAST_MEDIUM_NEAR, BLAST_MEDIUM_MID, BLAST_MEDIUM_FAR, BLAST_MEDIUM_VERY_FAR);
        };
        return sounds.get(Math.max(0, Math.min(3, band))).get();
    }

    public static List<Def> definitions() {
        return Collections.unmodifiableList(DEFS);
    }
}
