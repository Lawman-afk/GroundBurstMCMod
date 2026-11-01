package dev.groundburst.client.fx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.Blasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid: with {@code -Dgroundburst.fxShots=true} (the {@code fxShots} Gradle run) the client makes a flat
 * creative world, and for each test blast profile stands a spectator some way off on fresh ground, sets the blast off
 * and screenshots it at a series of moments into {@code run/screenshots/fx_<profile>_<tick>.png}, then quits. Used to
 * review the Spectacle effects without playing.
 */
public final class FxPhotographer {
    /** A blast to photograph: shot name, profile, how far away to stand, camera pitch, and the moments (ticks after) to shoot. */
    private record Scene(String name, String profile, int distance, float pitch, List<Integer> moments, String time) {
        Scene(String name, String profile, int distance, float pitch, List<Integer> moments) {
            this(name, profile, distance, pitch, moments, "noon");
        }
    }

    private static final Deque<Scene> SCENES = new ArrayDeque<>();
    private static Scene scene;
    private static int step = -1;
    private static int clock;
    private static int site;
    private static boolean worldMade;

    private FxPhotographer() {}

    public static void register() {
        if (!Boolean.getBoolean("groundburst.fxShots")) {
            return;
        }
        String only = System.getProperty("groundburst.fxShots.only", "");
        List<Integer> moments = List.of(1, 3, 6, 10, 16, 25, 40, 70, 120, 200);
        for (Scene s : List.of(new Scene("small", "test/small", 14, 8, moments), new Scene("medium", "test/medium", 42, 0, moments),
                new Scene("large", "test/large", 95, -8, moments),
                new Scene("nuclear", "test/nuclear", 230, -16, List.of(1, 3, 6, 10, 20, 40, 80, 160, 300, 600, 1200, 2400)),
                new Scene("nuclear_close", "test/nuclear", 130, -32, List.of(30, 120, 300, 700)),
                new Scene("nuclear_night", "test/nuclear", 130, -32, List.of(10, 60, 300, 700), "midnight"),
                new Scene("meltdown", "reactor_meltdown", 55, 2, List.of(2, 8, 20, 60, 160, 400)),
                new Scene("warhead_he", "he_warhead", 26, 6, List.of(2, 6, 14, 40)),
                new Scene("warhead_heavy", "heavy_warhead", 45, 2, List.of(3, 10, 25, 80)),
                new Scene("warhead_thermobaric", "thermobaric_warhead", 55, 2, List.of(3, 10, 25, 80)),
                new Scene("warhead_massive", "massive_warhead", 110, -6, List.of(4, 20, 80, 300, 700)),
                new Scene("nuke_tactical", "tactical_nuclear_warhead", 200, -10, List.of(3, 20, 80, 300, 700)),
                new Scene("nuke_fission", "fission_warhead", 330, -14, List.of(3, 30, 120, 400, 900)),
                new Scene("nuke_boosted", "boosted_fission_warhead", 380, -16, List.of(3, 30, 120, 400, 900)),
                // From above late on: the stem's foot and the dust ring stay on the crater while the cap drifts.
                new Scene("nuke_tactical_aerial", "tactical_nuclear_warhead", 170, 24, List.of(300, 1200, 2400)),
                // A contaminated zone 180 blocks round: its fallout clouds from outside, then the green sky from inside.
                new Scene("fallout", "fallout", 260, -14, List.of(60, 400)),
                // Slice 8c: the ICBM Assembly Hall at work (the crane laying the missile section by section), its
                // screen, the Launch Complex with an intercontinental missile, the H-bomb, and the cobalt warhead's plume.
                new Scene("hall_aerial", "hall", 26, 24, List.of(60, 300, 700, 1000, 1260)),
                new Scene("hall_ui", "hall", 4, 0, List.of(30)),
                new Scene("hall_jei", "hall", 4, 0, List.of(25)),
                // After 9b: JEI's list filtered to the intercontinental missiles (one per warhead), then R on the Tsar's.
                new Scene("icbm_jei", "hall", 4, 0, List.of(25, 45)),
                new Scene("complex_icbm", "complex_icbm", 34, -12, List.of(20)),
                new Scene("complex_icbm_close", "complex_icbm", 13, -38, List.of(20)),
                new Scene("nuke_thermonuclear", "thermonuclear_warhead", 600, -18, List.of(3, 40, 160, 500, 1200)),
                new Scene("cobalt_aerial", "cobalt_plume", 420, 28, List.of(400, 3300)),
                // Slice 8d: the hall building a Tsar (a minute and a half), the complex carrying one, and the Tsar itself.
                new Scene("hall_tsar_aerial", "hall_tsar", 26, 24, List.of(60, 1000, 1850)),
                new Scene("hall_tsar_ui", "hall_tsar", 4, 0, List.of(30)),
                // Phase 11, after the user's second look (DESIGN.md §16.15): the exotic ICBMs built whole at the Special
                // Weapons Assembler from their cores, the missile laid through its turning containment rings.
                new Scene("special_aerial", "special_antimatter", 26, 24, List.of(60, 500, 1000, 1850)),
                new Scene("special_close", "special_antimatter", 13, 14, List.of(700)),
                new Scene("special_low", "special_antimatter", 11, -4, List.of(1300)),
                new Scene("special_night", "special_antimatter", 16, 18, List.of(1300), "midnight"),
                new Scene("special_ui", "special_antimatter", 4, 0, List.of(30)),
                new Scene("special_jei", "special_antimatter", 4, 0, List.of(25)),
                new Scene("special_singularity_aerial", "special_singularity", 26, 24, List.of(1850)),
                new Scene("special_storm_aerial", "special_storm", 26, 24, List.of(1850)),
                new Scene("complex_tsar", "complex_tsar", 34, -12, List.of(20)),
                new Scene("complex_tsar_close", "complex_tsar", 13, -38, List.of(20)),
                new Scene("nuke_tsar", "tsar_warhead", 900, -16, List.of(3, 40, 160, 500, 1200)),
                // The machines' glow layers (turned on after slice 8d): every machine with a glowmask, at midnight and at
                // noon, in three rows of single blocks and two of formed multiblocks, shown running (see glowAt).
                new Scene("glow_machines0_night", "glow_machines0", 9, 12, List.of(60), "midnight"),
                new Scene("glow_machines0_day", "glow_machines0", 9, 12, List.of(60)),
                new Scene("glow_machines1_night", "glow_machines1", 9, 12, List.of(60), "midnight"),
                new Scene("glow_machines1_day", "glow_machines1", 9, 12, List.of(60)),
                new Scene("glow_machines2_night", "glow_machines2", 9, 12, List.of(60), "midnight"),
                new Scene("glow_machines2_day", "glow_machines2", 9, 12, List.of(60)),
                // The Crystal Puller running for real, its crystal a third grown in the pull chamber's window.
                new Scene("glow_machines3_night", "glow_machines3", 5, 10, List.of(420), "midnight"),
                new Scene("glow_machines3_day", "glow_machines3", 5, 10, List.of(420)),
                new Scene("glow_reactors0_night", "glow_reactors0", 13, 16, List.of(60), "midnight"),
                new Scene("glow_reactors0_day", "glow_reactors0", 13, 16, List.of(60)),
                new Scene("glow_reactors1_night", "glow_reactors1", 19, 22, List.of(60), "midnight"),
                new Scene("glow_reactors1_day", "glow_reactors1", 19, 22, List.of(60)),
                // The Clean Room, its bay lit yellow through its windows.
                new Scene("glow_reactors2_night", "glow_reactors2", 7, 12, List.of(60), "midnight"),
                new Scene("glow_reactors2_day", "glow_reactors2", 7, 12, List.of(60)),
                new Scene("glow_radar_night", "radar", 10, -4, List.of(60), "midnight"),
                new Scene("glow_complex_night", "complex_tsar", 16, -24, List.of(30), "midnight"),
                new Scene("glow_hall_night", "hall_tsar", 14, 34, List.of(1850), "midnight"),
                // Slice 9a: an intercontinental missile climbing, side on (lift-off, both stages parting, burn-out), and a
                // missile coming back down over the camera, glowing.
                new Scene("flight_icbm", "flight_icbm", 230, -18, List.of(30, 70, 145, 175, 245, 300, 350, 420)),
                new Scene("flight_reentry", "flight_reentry", 120, -16, List.of(60, 110, 140, 160, 175, 185)),
                // Slice 9b: the Missile Silo dug into a plateau (the test world is flat), launched at clock 25: its doors
                // sliding apart from T-15, the vents and the shaft blasting at T-0, the missile climbing out, the doors
                // shutting again; from above into the open shaft; at night (its beacons); and its screen.
                new Scene("msilo", "msilo", 22, 22, List.of(20, 150, 230, 430, 455, 470, 480, 540, 720)),
                new Scene("msilo_close", "msilo", 9, 24, List.of(20, 230, 445)),
                new Scene("msilo_aerial", "msilo", 8, 58, List.of(230, 445, 470)),
                new Scene("msilo_night_aerial", "msilo", 8, 58, List.of(230, 445), "midnight"),
                new Scene("msilo_ui", "msilo", 5, 0, List.of(30)),
                // Smoke in front of water, seen from above: the water must still show through the smoke.
                new Scene("water_smoke_aerial", "water_smoke", 16, 28, List.of(60, 160)),
                new Scene("fallout_inside", "fallout", 30, -32, List.of(60, 400)),
                new Scene("nuke_neutron", "neutron_warhead", 70, -8, List.of(3, 20, 60, 200, 400)),
                new Scene("warhead_dirty", "dirty_warhead", 30, 2, List.of(2, 8, 20, 60)),
                new Scene("complex", "complex", 34, -12, List.of(20, 150, 270, 335, 380)),
                new Scene("complex_close", "complex", 13, -38, List.of(20, 150, 360)),
                new Scene("launch_close", "launch", 11, -32, List.of(20, 150, 240)),
                new Scene("meltdown_dusk", "reactor_meltdown", 40, 14, List.of(30, 200), "13000"),
                new Scene("suits", "suits", 3, 12, List.of(15)),
                new Scene("suits_back", "suits_back", 3, 12, List.of(15)),
                new Scene("grenades", "grenades", 7, 18, List.of(4, 12, 30, 62, 74, 90, 130)),
                new Scene("chemical", "chemical", 7, 18, List.of(20, 60, 120)),
                new Scene("charges", "charges", 4, 22, List.of(10, 15, 60)),
                new Scene("charge_ui", "charges", 4, 22, List.of(30, 45, 60)),
                new Scene("mines", "mines", 5, 25, List.of(3, 6, 9, 30)),
                new Scene("warheads", "warheads", 9, 25, List.of(10, 40)),
                new Scene("napalm", "napalm", 10, 30, List.of(8, 40)),
                new Scene("launch", "launch", 26, -14, List.of(20, 120, 240, 275, 300)),
                new Scene("launch_ui", "launch", 9, 0, List.of(22, 100, 300, 340)),
                new Scene("silo_ui", "launch", 5, 0, List.of(30)),
                new Scene("launch_dive", "launch_dive", 50, -20, List.of(285, 295, 302, 308)),
                new Scene("radar", "radar", 22, -16, List.of(20, 60)),
                new Scene("airburst", "airburst", 40, -18, List.of(4, 12, 30, 50)),
                new Scene("effects", "effects", 16, 22, List.of(8, 30, 70, 140)),
                new Scene("frozen", "frozen", 6, 18, List.of(10, 60)),
                new Scene("gas_wall", "gas_wall", 9, 20, List.of(60, 140)),
                // Slice 12a: the Geiger Counter in hand in a field of 2,000 RAD/h (its meter, the dose, the model in hand),
                // and the Lead Container's screen.
                new Scene("geiger", "geiger", 4, 15, List.of(40, 80)),
                new Scene("lead_ui", "lead_ui", 4, 15, List.of(30)),
                // After the user's look at 12b: the redrawn icons in real slots (the suits on vanilla's armour shapes, the
                // instruments and medicine re-centred, the soil and the dead grass), JEI showing the hazmat sets.
                new Scene("gear_icons", "gear_icons", 4, 15, List.of(30)),
                // Phase 15: the world's old sites, placed as generation would place them; the ones underground cut open
                // along their middle (in a block of stone: the flat test world is too thin for them), the rest from the air.
                new Scene("site_silo", "site:abandoned_silo:0:cut", 40, 4, List.of(90)),
                new Scene("site_silo_flooded", "site:abandoned_silo:1:cut", 40, 4, List.of(90)),
                new Scene("site_silo_collapsed", "site:abandoned_silo:2:cut", 40, 4, List.of(90)),
                new Scene("site_bunker", "site:command_bunker:0:cut", 40, 4, List.of(90)),
                new Scene("site_waste", "site:waste_storage:0:cut", 40, 4, List.of(90)),
                new Scene("site_radar_aerial", "site:radar_outpost:0:air", 34, 36, List.of(90)),
                new Scene("site_test_aerial", "site:test_site:0:air", 44, 38, List.of(90)),
                new Scene("site_crashed_aerial", "site:crashed_missile:0:air", 30, 40, List.of(90)),
                new Scene("site_station_aerial", "site:exotic_research_station:0:air", 34, 22, List.of(90)),
                // Slice 12e: the wasteland painted round the camera, a crater lake, frozen ground, toxic soil.
                new Scene("wasteland_aerial", "wasteland", 26, 30, List.of(90)),
                new Scene("aftermath", "aftermath", 6, 22, List.of(40, 200)),
                // Slice 12b: a tactical warhead's crater and scorched ground from above, once its cloud has gone.
                new Scene("crater_tactical_aerial", "tactical_nuclear_warhead", 80, 40, List.of(2400)),
                // A thermonuclear crater from above once its cloud has gone: its streaks eased for its size, no lone blocks.
                new Scene("crater_thermo_aerial", "thermonuclear_warhead", 260, 45, List.of(3600)),
                new Scene("radar_ui", "radar", 6, 0, List.of(60, 130)),
                new Scene("assembly", "assembly", 5, 18, List.of(10, 30, 50)),
                new Scene("assembly_ui", "assembly", 4, 0, List.of(30, 40)),
                new Scene("assembly_jei", "assembly", 4, 0, List.of(25)),
                new Scene("tall_ui", "tall", 4, 0, List.of(30)),
                // The Upgrades tab (DESIGN.md §7.3): a crusher at work with a quantum speed chip, its tab open.
                new Scene("upgrade_ui", "upgrade", 4, 0, List.of(30)),
                // After the user's look: a 3x3x3 cube of lattice and one of grating (a slab step beside it), looked into.
                new Scene("see_through", "see_through", 9, 18, List.of(20)),
                // Missile defence (DESIGN.md §18.2): a battery facing the camera, a missile coming at it from 300 blocks
                // beyond; the turret swings up, an interceptor climbs out and meets it in the sky (the kill made certain).
                new Scene("intercept", "intercept", 90, -12, List.of(120, 135, 148, 156, 162, 170)),
                new Scene("intercept_close", "intercept_near", 9, 2, List.of(30, 112, 124)),
                // The Interceptor Complex (DESIGN.md §18.5): off to the right, a missile from 300 blocks to the left; its
                // volley leaves the hatches two at a time and meets the missile in the sky; close up, the hatches swinging
                // open; its screen. After the user's look at the defence: the Storm's strikes' EMP pulses close up.
                new Scene("intercept_complex", "intercept_complex", 170, -16, List.of(40, 55, 70, 85, 100, 115, 130)),
                new Scene("intercept_complex_aerial", "intercept_complex_near", 10, 24, List.of(46, 48, 52, 60)),
                new Scene("intercept_complex_close", "intercept_complex_near", 17, 22, List.of(20, 40, 50, 60, 70, 80)),
                new Scene("complex_ui", "intercept_complex_near", 4, 0, List.of(30)),
                new Scene("storm_pulses", "storm_warhead", 40, 8, List.of(478, 480, 482, 485)),
                // The base alarm system (DESIGN.md §18.6): a wall of every device at midnight, set off by its panel at
                // clock 10: the beacons on top (rotating, flashing, steady; five colours), the siren on its post; and the
                // receiver's screen.
                new Scene("alarms", "alarms", 9, 6, List.of(20, 30, 33, 36, 60), "midnight"),
                new Scene("alarms_close", "alarms", 5, 12, List.of(30, 34), "midnight"),
                new Scene("alarm_ui", "alarms", 4, 0, List.of(30)),
                // The siren's horn up close (after the user's look: its walls meeting flush): one facing the camera, one
                // side on.
                new Scene("siren_close", "siren", 3, 14, List.of(20)),
                // The Launch Control Station (DESIGN.md §17.12): facing the camera, two linked silos behind it (the near one
                // armed: its keys turned and its cover up), a linked radar, one of its missiles in the air; close up on the
                // desk and the big screen; at night; its screen.
                new Scene("station", "station", 9, 14, List.of(30)),
                new Scene("station_close", "station", 4, 18, List.of(30, 31)),
                new Scene("station_night", "station", 5, 10, List.of(30), "midnight"),
                new Scene("station_desk", "station", 2, 42, List.of(30)),
                new Scene("station_ui", "station", 4, 0, List.of(30)),
                // The Laser Designator (DESIGN.md §17.13): through its sight, lasing a pillbox 40 blocks off while a
                // laser-guided missile homes on the spot and lands on it; at night; and from outside, held to the eye.
                new Scene("designator", "designator", 40, 1.5f, List.of(20, 60, 68, 72, 76, 90)),
                new Scene("designator_night", "designator", 40, 1.5f, List.of(25), "midnight"),
                new Scene("designator_front", "designator", 40, 1.5f, List.of(25)),
                new Scene("designator_back", "designator", 40, 1.5f, List.of(25), "13000"),
                new Scene("designator_carry", "designator", 40, 1.5f, List.of(25)),
                new Scene("designator_carry_front", "designator", 40, 1.5f, List.of(25)),
                // Far: the pillbox 300 blocks off, through the sight; and from a camera off to one side, the whole beam
                // out to it, drawn from the server's spot as other players see it.
                new Scene("designator_far", "designator", 300, 0.2f, List.of(300)),
                // Left clicks with real input, one launcher at a time: two laser-guided silos 150 blocks behind the
                // camera; #1 (the default) armed on pillbox A (clock 20); the laser swung onto pillbox B; the wheel picks
                // #2 (45), armed on B (55): a sphere on each target; #2, armed, launched by the next click (76; ten
                // seconds' countdown), the pick moving on to #1; a sneaking click disarms #1 (100), its sphere gone;
                // #2's missile comes down on B.
                new Scene("designator_call", "designator", 40, 1.5f, List.of(26, 52, 62, 82, 104, 330, 360, 380, 392)),
                new Scene("designator_side", "designator", 200, 0.3f, List.of(30)),
                new Scene("see_through_low", "see_through", 7, 2, List.of(20)),
                // The missile assemblers (DESIGN.md §10.4): all four at work side by side, each close up as it lays its
                // missile out section by section, the Strategic one from above, a screen, and JEI's recipe for a missile.
                new Scene("assemblers_aerial", "assemblers", 34, 28, List.of(60, 160, 260, 700)),
                new Scene("asm_light_close", "asm_light", 9, 16, List.of(60, 120, 180, 260)),
                new Scene("asm_medium_close", "asm_medium", 11, 18, List.of(100, 200, 280, 380)),
                new Scene("asm_heavy_close", "asm_heavy", 14, 18, List.of(130, 270, 370, 500)),
                new Scene("asm_strategic_close", "asm_strategic", 17, 6, List.of(60, 200, 400, 700)),
                new Scene("asm_strategic_aerial", "asm_strategic", 14, 34, List.of(200, 700)),
                new Scene("asm_ui", "asm_medium", 4, 0, List.of(30)),
                new Scene("asm_laser_ui", "asm_medium", 4, 0, List.of(30)),
                new Scene("asm_jei", "asm_medium", 4, 0, List.of(25, 45)),
                new Scene("assembly_mow_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("assembly_nuke_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("assembly_neutron_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("assembly_dirty_ui", "assembly", 4, 0, List.of(30, 44)),
                new Scene("launch_map_ui", "launch", 9, 0, List.of(40, 80)),
                // After 9b: the Fission Reactor at two sizes (the 4x4x4 example and a full 6x6x6), running: their Fuel
                // Channels lit and the coolant glowing through the glass, by day and at night; and the big one's screen.
                new Scene("fission", "fission", 15, 14, List.of(60)),
                new Scene("fission_night", "fission", 15, 14, List.of(60), "midnight"),
                new Scene("fission_close", "fission", 7, 10, List.of(60), "midnight"),
                new Scene("fission_ui", "fission", 5, 0, List.of(30)),
                // The reactor's plans: two lone controllers' ghost previews (a 4x4x4 half built, with stone in its core;
                // a 6x5x5 not started), and the plan panel on the unformed controller's screen.
                new Scene("fission_plan", "fission_plan", 13, 20, List.of(40)),
                new Scene("fission_plan_ui", "fission_plan", 5, 0, List.of(30)),
                // Close up: a 3x3x3 plan built but for its front window and its Fuel Channel: their ghosts, straight on.
                new Scene("fission_ghost", "fission_ghost", 2, 0, List.of(40)),
                // After the warheads' remaster: missiles carrying their warheads' own models, in rows of seven (their
                // flight models twice the item's size, four blocks tall): the usual missile's warheads, then the
                // Strategic missile's and the intercontinental missile's.
                new Scene("missiles0", "missiles0", 8, -6, List.of(20)),
                new Scene("missiles1", "missiles1", 8, -6, List.of(20)),
                new Scene("missiles2", "missiles2", 9, -14, List.of(20)),
                // The superweapons (DESIGN.md §19.5). The SCUD Storm, charged with nine Toxin SCUDs, launched at clock 25:
                // its erectors raising them, the countdown, the nine lighting one after another and climbing away; from the
                // air too. A SCUD strike close up: three toxin SCUDs coming down 0.6 s apart, the blasts, the toxin welling
                // up and its residue spreading out over the ground, the cloud drifting.
                new Scene("scud_storm", "scud_storm", 38, 16, List.of(20, 90, 228, 245, 270, 300, 330)),
                new Scene("scud_storm_aerial", "scud_storm", 34, 34, List.of(20, 90, 240, 290)),
                new Scene("scud_strike", "scud_strike", 26, 14, List.of(27, 36, 46, 60, 90, 160, 400)),
                new Scene("scud_strike_aerial", "scud_strike", 22, 40, List.of(46, 90, 400)),
                // Phase 11: an antimatter warhead arriving on top of a mesa of layered rock (ANTIMATTER_MESA high, the flat
                // world being too shallow for its sphere): the flash, the white sphere growing and fading, then the void,
                // from the plain to the west (a semicircle bitten clean out of the cliff) and from above (the bowl).
                new Scene("antimatter", "antimatter_warhead", 175, -12, List.of(1, 6, 14, 30, 60, 90, 160, 300)),
                new Scene("antimatter_aerial", "antimatter_warhead", 165, 32, List.of(40, 90, 260)),
                // A singularity on the same mesa: feeding on it for a minute and growing, imploding from 1200 to 1240.
                new Scene("singularity", "singularity_warhead", 170, 4, List.of(40, 200, 400, 700, 1000, 1190, 1212, 1226, 1236, 1241, 1300)),
                new Scene("singularity_aerial", "singularity_warhead", 170, 30, List.of(300, 700, 1190, 1230, 1400)),
                // A storm over the plain, a herd scattered about in the open: from inside it near its edge (the deck
                // overhead, bolts and their chains, rain), and from the air outside it (the whole deck turning).
                new Scene("storm", "storm_warhead", 110, -4, List.of(60, 200, 400, 700, 1000, 1400, 1750)),
                new Scene("storm_aerial", "storm_warhead", 330, 22, List.of(500, 1000)),
                // After the user's look: the storm's birth, its bolts going up from where it struck into the sky.
                new Scene("storm_birth", "storm_warhead", 110, -6, List.of(2, 8, 30, 70)),
                new Scene("storm_birth_close", "storm_warhead", 45, -10, List.of(2, 20)),
                // After the user's look: each strike explodes where it lands; from above, the craters it leaves.
                new Scene("storm_craters", "storm_warhead", 80, 48, List.of(1300)),
                // China's Nuclear Missile Silo, its missile loaded, charged and armed, launched at clock 25: the roof
                // folding open, the missile raised and the arm swinging in, vapour boiling off it, the launch.
                new Scene("nuke_silo", "nuke_silo", 30, 12, List.of(20, 70, 150, 228, 240, 260, 290)),
                new Scene("nuke_silo_aerial", "nuke_silo", 26, 34, List.of(20, 150, 235)),
                // The ammunition factories at work, their parts loaded: the Arms Dealer laying a Toxin SCUD out section by
                // section on its trestles under its hoist; the War Factory its Nuclear Missile under its gantry crane.
                // Both from above, close: the Arms Dealer from its front, over its wall into the yard; the War Factory
                // from its yard's side, the missile's length across the view.
                new Scene("factory_arms", "factory_arms", 2, 42, List.of(60, 200, 420, 640)),
                new Scene("factory_war", "factory_war", 5, 36, List.of(60, 500, 900, 1260)),
                // Slice 9c: the Cruise Missile Launcher side on (its front to the camera's left), two cruise missiles in
                // it, launched at clock 25: its boxes rising, raised with the beacon turning, box A's cover bursting as its
                // missile lights, the missile leaving its box (wings stowed), climbing on its booster (wings out), the
                // booster falling away, box B firing, the boxes coming down; close up, from the air, and its screen. A
                // cruise missile crossing the view low overhead, and another popping up and diving onto a target.
                new Scene("cruise", "cruise", 22, -10, List.of(20, 50, 110, 228, 232, 238, 244, 252, 262, 290, 300, 365, 440)),
                new Scene("cruise_close", "cruise", 11, -18, List.of(110, 228, 232, 238, 242)),
                new Scene("cruise_aerial", "cruise", 24, 30, List.of(110, 240, 300)),
                new Scene("cruise_ui", "cruise", 5, 0, List.of(30)),
                new Scene("cruise_flight", "cruise_flight", 30, -25, List.of(4, 12, 20, 28, 36)),
                // The Vertical Launch Battery's ripple: armed and launched at 25, its cells firing from about 227.
                new Scene("vls", "vls", 18, 12, List.of(30, 228, 234, 242, 254, 270, 300)),
                new Scene("vls_aerial", "vls", 22, 32, List.of(229, 245, 272)),
                new Scene("vls_ui", "vls", 5, 0, List.of(30)),                  // its screen: the CRT's four lines
                new Scene("cruise_dive", "cruise_dive", 40, -12, List.of(6, 16, 24, 32, 38, 44, 47, 60)))) {
            if (only.isEmpty() || java.util.Arrays.stream(only.split(",")).anyMatch(o -> s.name().startsWith(o.trim()))) {   // -PfxOnly=a,b
                SCENES.add(s);
            }
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    /** Two armour stands wearing the hazmat suits, side by side, facing the camera (or turned away). */
    private static void suits(ServerLevel level, int x, int ground, boolean back) {
        var sets = List.of(dev.groundburst.content.gear.GBArmor.HAZMAT_SET, dev.groundburst.content.gear.GBArmor.LEAD_HAZMAT_SET);
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < 2; i++) {
            var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, x + 0.5, ground, 0.5 + (i == 0 ? -0.8 : 0.8));
            stand.setYRot(back ? -90 : 90);
            stand.setYBodyRot(back ? -90 : 90);
            stand.setShowArms(true);
            for (int k = 0; k < 4; k++) {
                stand.setItemSlot(slots[k], new net.minecraft.world.item.ItemStack(sets.get(i).get(k).get()));
            }
            level.addFreshEntity(stand);
        }
    }

    /**
     * The Geiger Counter held in a field of 2,000 RAD/h with a dose of 250 rad taken (slice 12a): its meter and the dose
     * in the HUD, the counter's model in hand, the badge, the Lead Container and the medicine in the hotbar, uranium in view.
     */
    private static void geiger(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.SURVIVAL);                                     // the creative are immune to radiation
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.setSelectedSlot(0);
        inventory.setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.radiation.RadiationItems.GEIGER_COUNTER.get()));
        inventory.setItem(1, new net.minecraft.world.item.ItemStack(dev.groundburst.content.radiation.RadiationItems.LEAD_CONTAINER.get()));
        inventory.setItem(2, new net.minecraft.world.item.ItemStack(dev.groundburst.content.radiation.RadiationItems.RAD_AWAY.get(), 4));
        inventory.setItem(3, new net.minecraft.world.item.ItemStack(dev.groundburst.content.radiation.RadiationItems.POTASSIUM_IODIDE.get(), 8));
        inventory.setItem(4, new net.minecraft.world.item.ItemStack(dev.groundburst.content.radiation.RadiationItems.ANTI_RAD_INJECTOR.get(), 2));
        inventory.setItem(8, new net.minecraft.world.item.ItemStack(dev.groundburst.content.radiation.RadiationItems.DOSIMETER_BADGE.get()));
        dev.groundburst.content.radiation.Radiation.setDose(player, 250);
        dev.groundburst.content.radiation.RadiationSources.of(level).add(new dev.groundburst.content.radiation.RadiationSource(
                player.position(), 12, 2_000, 1_000_000, level.getGameTime()));
        for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(new net.minecraft.core.BlockPos(x - 1, ground, dz),
                    dev.groundburst.content.resource.GBResources.URANIUM_ORE.block().get().defaultBlockState(), 2);
        }
    }

    /**
     * Slice 12b's ground in front of the camera on dead grass: the glassed, molten and burnt blocks in a row, ash one to
     * eight layers deep and fallout ash behind them, a charred trunk and a dead tree, withered wheat on farmland.
     */
    private static void aftermath(ServerLevel level, int x, int ground) {
        var f = dev.groundburst.content.fallout.GBFallout.class;
        java.util.function.Function<dev.groundburst.registry.GBBlocks.WithItem<? extends net.minecraft.world.level.block.Block>, net.minecraft.world.level.block.state.BlockState> d =
                b -> b.block().get().defaultBlockState();
        for (int dx = -2; dx <= 12; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlock(new net.minecraft.core.BlockPos(x + dx, ground - 1, dz), d.apply(dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK), 2);
            }
        }
        var row = List.of(dev.groundburst.content.fallout.GBFallout.TRINITITE, dev.groundburst.content.fallout.GBFallout.GLASSED_SAND,
                dev.groundburst.content.fallout.GBFallout.SLAG, dev.groundburst.content.fallout.GBFallout.SCORCHED_EARTH,
                dev.groundburst.content.fallout.GBFallout.SCORCHED_STONE, dev.groundburst.content.fallout.GBFallout.IRRADIATED_SOIL,
                dev.groundburst.content.fallout.GBFallout.CHARRED_PLANKS, dev.groundburst.content.fallout.GBFallout.CHARRED_WOOD);
        for (int i = 0; i < row.size(); i++) {
            level.setBlock(new net.minecraft.core.BlockPos(x + 3, ground, -7 + 2 * i), d.apply(row.get(i)), 2);
        }
        for (int n = 1; n <= 8; n++) {
            level.setBlock(new net.minecraft.core.BlockPos(x + 6, ground, -9 + n), dev.groundburst.content.fallout.GBFallout.ASH.block().get().layers(n), 2);
            level.setBlock(new net.minecraft.core.BlockPos(x + 6, ground, n), dev.groundburst.content.fallout.GBFallout.FALLOUT_ASH.block().get().layers(n), 2);
        }
        for (int dz = -3; dz <= 3; dz++) {
            level.setBlock(new net.minecraft.core.BlockPos(x + 1, ground - 1, dz), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), 2);
            level.setBlock(new net.minecraft.core.BlockPos(x + 1, ground, dz), d.apply(dev.groundburst.content.fallout.GBFallout.WITHERED_CROPS), 2);
        }
        for (int y = 0; y < 5; y++) {
            level.setBlock(new net.minecraft.core.BlockPos(x + 10, ground + y, -5), d.apply(dev.groundburst.content.fallout.GBFallout.CHARRED_LOG), 2);
            level.setBlock(new net.minecraft.core.BlockPos(x + 10, ground + y, 5), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        for (int dy = 3; dy <= 5; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    var at = new net.minecraft.core.BlockPos(x + 10 + dx, ground + dy, 5 + dz);
                    if ((dx != 0 || dz != 0 || dy == 5) && Math.abs(dx) + Math.abs(dz) <= (dy == 5 ? 1 : 3)) {
                        level.setBlock(at, d.apply(dev.groundburst.content.fallout.GBFallout.DEAD_LEAVES), 2);
                    }
                }
            }
        }
    }

    /** The Lead Container's screen (slice 12a), with plutonium, a spent fuel rod and nuclear waste inside. */
    private static void leadUi(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);                                     // spectators open no menus
        var lead = new net.minecraft.world.item.ItemStack(dev.groundburst.content.radiation.RadiationItems.LEAD_CONTAINER.get());
        lead.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(
                new net.minecraft.world.item.ItemStack(dev.groundburst.content.nuclear.GBNuclear.PLUTONIUM_INGOT.get(), 16),
                new net.minecraft.world.item.ItemStack(dev.groundburst.content.nuclear.GBNuclear.SPENT_FUEL_ROD.get()),
                new net.minecraft.world.item.ItemStack(dev.groundburst.content.nuclear.GBNuclear.NUCLEAR_WASTE.get(), 32))));
        player.getInventory().setSelectedSlot(0);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, lead);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new dev.groundburst.content.radiation.LeadContainerMenu(id, inv,
                net.minecraft.world.InteractionHand.MAIN_HAND), lead.getHoverName()), buf -> buf.writeEnum(net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    /**
     * The protective gear and the radiation kit in the player's inventory (the plain hazmat suit worn, the lead-lined one
     * in the pack), the soil and the dead grass beside them; the screen opens in {@link #tick}.
     */
    private static void gearIcons(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.SURVIVAL);                                     // spectators open no menus
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.setSelectedSlot(0);
        java.util.function.BiFunction<net.minecraft.world.level.ItemLike, Integer, net.minecraft.world.item.ItemStack> stack =
                net.minecraft.world.item.ItemStack::new;
        var r = List.of(dev.groundburst.content.radiation.RadiationItems.GEIGER_COUNTER.get(), dev.groundburst.content.radiation.RadiationItems.LEAD_CONTAINER.get(),
                dev.groundburst.content.radiation.RadiationItems.RAD_AWAY.get(), dev.groundburst.content.radiation.RadiationItems.POTASSIUM_IODIDE.get(),
                dev.groundburst.content.radiation.RadiationItems.ANTI_RAD_INJECTOR.get(), dev.groundburst.content.radiation.RadiationItems.DOSIMETER_BADGE.get());
        for (int i = 0; i < r.size(); i++) {
            inventory.setItem(i, stack.apply(r.get(i), 1));
        }
        inventory.setItem(6, stack.apply(dev.groundburst.content.fallout.GBFallout.IRRADIATED_SOIL.block().get(), 16));
        inventory.setItem(7, stack.apply(dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK.block().get(), 16));
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int k = 0; k < 4; k++) {
            player.setItemSlot(slots[k], stack.apply(dev.groundburst.content.gear.GBArmor.HAZMAT_SET.get(k).get(), 1));
            inventory.setItem(9 + k, stack.apply(dev.groundburst.content.gear.GBArmor.HAZMAT_SET.get(k).get(), 1));
            inventory.setItem(18 + k, stack.apply(dev.groundburst.content.gear.GBArmor.LEAD_HAZMAT_SET.get(k).get(), 1));
        }
    }

    /**
     * A site (profile {@code site:<structure>:<variant>:<cut|air>}): placed as generation would; cut open (a site
     * underground, raised into a block of stone, cleared north of its origin, the camera to the north looking south at
     * the cut), or on the ground seen from the air.
     */
    private static void site(ServerLevel level, int x, int ground, String profile) {
        String[] p = profile.split(":");
        boolean cut = p[3].equals("cut");
        var origin = new net.minecraft.core.BlockPos(x, cut ? ground + 46 : ground - 1, 0);
        if (p[1].equals("exotic_research_station")) {
            origin = origin.above(12);
        }
        dev.groundburst.world.structure.GBStructures.preview(level, p[1], Integer.parseInt(p[2]), origin, cut, cut ? 0 : null);
        if (cut) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
            double along = p[1].equals("abandoned_silo") ? 22 : 2;
            double back = p[1].equals("abandoned_silo") ? 58 : 42;
            player.teleportTo(level, x + along + 0.5, origin.getY() - (p[1].equals("abandoned_silo") ? 16 : 6), -back, java.util.Set.of(), 0, 6, true);
        }
    }

    /**
     * The wasteland (slice 12e): the biome painted over the scene, dead grass, a crater flooded beside a pool, a patch
     * frozen by a cryo burst and a patch of toxic soil.
     */
    private static void wasteland(ServerLevel level, int x, int ground) {
        var dead = dev.groundburst.content.fallout.GBFallout.DEAD_GRASS_BLOCK.block().get().defaultBlockState();
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        int top = ground + 5;
        for (int dx = -2; dx <= 34; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                for (int y = ground; y < top; y++) {
                    level.setBlock(new net.minecraft.core.BlockPos(x + dx, y, dz), stone, 2);
                }
                level.setBlock(new net.minecraft.core.BlockPos(x + dx, top, dz), dead, 2);
            }
        }
        // A crater dug into it, a pool on its rim, flooded.
        int cx = x + 12;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.hypot(dx, dz);
                int depth = (int) Math.round(4 * (1 - d * d / 49));
                for (int k = 0; k < depth; k++) {
                    level.setBlock(new net.minecraft.core.BlockPos(cx + dx, top - k, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                if (depth > 0) {
                    level.setBlock(new net.minecraft.core.BlockPos(cx + dx, top - depth, dz),
                            dev.groundburst.content.fallout.GBFallout.SCORCHED_EARTH.block().get().defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(new net.minecraft.core.BlockPos(cx + 10, top, 0), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
        dev.groundburst.content.fallout.Wasteland.floodNow(level, new Vec3(cx + 0.5, top, 0.5), 7);
        // A frozen patch (stone and grass, plants on it) and a toxic one.
        for (int dx = 22; dx <= 30; dx++) {
            for (int dz = -12; dz <= -4; dz++) {
                var at = new net.minecraft.core.BlockPos(x + dx, top, dz);
                level.setBlock(at, ((dx + dz) & 1) == 0 ? stone : net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                if (((dx * 3 + dz) % 4) == 0) {
                    level.setBlock(at.above(), net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
        }
        dev.groundburst.content.effect.Frost.freeze(level, new Vec3(x + 26.5, top + 1, -8.5), 5, 100, null);
        for (int dx = 22; dx <= 30; dx++) {
            for (int dz = 4; dz <= 12; dz++) {
                var at = new net.minecraft.core.BlockPos(x + dx, top, dz);
                level.setBlock(at, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                if (Math.hypot(dx - 26, dz - 8) < 4) {
                    dev.groundburst.content.fallout.ToxicSoilBlock.taint(level, at);
                }
            }
        }
        dev.groundburst.content.fallout.Wasteland.paintNow(level, new Vec3(x + 10, top, 0), 60);
    }

    /** Thrown explosives in front of the camera: one of each, in flight, landing, smoking and going off. */
    /** The antimatter scene's mesa: its height, and how far it runs west of the burst (the rest of it east). */
    private static final int ANTIMATTER_MESA = 50, MESA_WEST = 28, MESA_EAST = 90, MESA_HALF = 90;

    /**
     * A mesa of layered rock for the antimatter warhead to land on: bands of stone, andesite, tuff and granite under
     * dirt and grass, so the sphere's cut shows clean through the layers. Its west face stands {@link #MESA_WEST} blocks
     * short of the burst, inside the sphere, so the sphere bites a semicircle out of it.
     */
    private static void antimatterMesa(ServerLevel level, int x, int ground) {
        var layers = new net.minecraft.world.level.block.state.BlockState[] {
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Blocks.ANDESITE.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Blocks.TUFF.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Blocks.GRANITE.defaultBlockState()};
        var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -MESA_WEST; dx <= MESA_EAST; dx++) {
            for (int z = -MESA_HALF; z <= MESA_HALF; z++) {
                level.getChunk((x + dx) >> 4, z >> 4);
                for (int dy = 0; dy < ANTIMATTER_MESA; dy++) {
                    int below = ANTIMATTER_MESA - 1 - dy;
                    var state = below == 0 ? grass : below < 4 ? dirt : layers[(dy / 7) % layers.length];
                    level.setBlock(p.set(x + dx, ground + dy, z), state, 2);
                }
            }
        }
    }

    private static void grenades(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE.get()));
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE.get(), new Vec3(x - 3, ground + 0.05, -1.2), Vec3.ZERO, 400);
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE.get(), new Vec3(x + 2, ground + 0.05, 2.5), Vec3.ZERO, 20);
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.FRAG_GRENADE.get(), new Vec3(x - 4, ground + 2.5, 3),
                new Vec3(0.35, 0.25, -0.45), 70);
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.INCENDIARY_GRENADE.get(), new Vec3(x + 3, ground + 6, -2.5),
                new Vec3(0, -0.05, 0), -1);
    }

    /** The chemical grenades: a poison gas cloud, a toxin splash, dynamite, and a Molotov falling; a toxin vial in hand. */
    /** A pool two deep with a red stripe on its floor, and a smoke grenade going off on the near bank. */
    private static void waterSmoke(ServerLevel level, int x, int ground) {
        for (int dx = -4; dx <= 12; dx++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlock(new net.minecraft.core.BlockPos(x + dx, ground - 3, z),
                        (z == 0 ? net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK : net.minecraft.world.level.block.Blocks.SAND).defaultBlockState(), 2);
                for (int y = ground - 2; y < ground; y++) {
                    level.setBlock(new net.minecraft.core.BlockPos(x + dx, y, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.SMOKE_GRENADE.get(), new Vec3(x - 6, ground + 0.05, 0.5), Vec3.ZERO, 5);
    }

    private static void chemical(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE.get(), new Vec3(x + 2, ground + 0.05, 2.5), Vec3.ZERO, 5);
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.TOXIN_GRENADE.get(), new Vec3(x - 1, ground + 4, -2.5),
                new Vec3(0, -0.2, 0), -1);
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.DYNAMITE.get(), new Vec3(x - 4, ground + 0.05, 0.5), Vec3.ZERO, 4000);
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.MOLOTOV_COCKTAIL.get(), new Vec3(x - 5, ground + 5, 4.5),
                new Vec3(0, -0.05, 0), -1);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.TOXIN_GRENADE.get()));
    }

    /** Where the charges scene put its charges, for the charge_ui scene to open their screens: breaching, C4, timed bomb. */
    private static final net.minecraft.core.BlockPos[] UI_CHARGES = new net.minecraft.core.BlockPos[3];

    /**
     * Placed charges facing the camera: stacked C4 and a timed bomb on the ground, breaching and thermite on a wall; all
     * armed, the thermite lit and the bomb counting, except the breaching charge (left safe, for its screen).
     */
    private static void charges(ServerLevel level, int x, int ground) {
        for (int z = -3; z <= 3; z++) {
            for (int y = 0; y < 4; y++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + 3, ground + y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        var F = dev.groundburst.content.ordnance.charge.ChargeBlock.FACING;
        var c4 = new net.minecraft.core.BlockPos(x, ground, -1);
        level.setBlockAndUpdate(c4, dev.groundburst.content.ordnance.GBOrdnance.C4.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.UP).setValue(dev.groundburst.content.ordnance.charge.ChargeBlock.COUNT, 3));
        var bomb = new net.minecraft.core.BlockPos(x, ground, 1);
        level.setBlockAndUpdate(bomb, dev.groundburst.content.ordnance.GBOrdnance.TIMED_BOMB.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.WEST));
        var breach = new net.minecraft.core.BlockPos(x + 2, ground + 2, -2);
        level.setBlockAndUpdate(breach, dev.groundburst.content.ordnance.GBOrdnance.BREACHING_CHARGE.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.WEST));
        var thermite = new net.minecraft.core.BlockPos(x + 2, ground + 1, 2);
        level.setBlockAndUpdate(thermite, dev.groundburst.content.ordnance.GBOrdnance.THERMITE_CHARGE.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.WEST));
        UI_CHARGES[0] = breach;
        UI_CHARGES[1] = c4;
        UI_CHARGES[2] = bomb;
        for (var p : java.util.List.of(c4, bomb, thermite)) {
            var be = dev.groundburst.content.ordnance.charge.ChargeBlock.at(level, p);
            if (be != null) {
                if (p == bomb) {
                    be.configure(dev.groundburst.content.ordnance.charge.ChargeBlockEntity.Mode.TIMER, 0, 45);
                }
                be.arm(level, null);
                if (p == thermite || p == bomb) {
                    be.trigger(level, null);
                }
            }
        }
    }

    /** Mines: a landmine, a claymore with its laser across the view, and a bouncing mine set off as the shots start. */
    private static void mines(ServerLevel level, int x, int ground) {
        var F = dev.groundburst.content.ordnance.charge.ChargeBlock.FACING;
        var land = new net.minecraft.core.BlockPos(x - 1, ground, 1);
        var clay = new net.minecraft.core.BlockPos(x + 1, ground, -3);
        var bounce = new net.minecraft.core.BlockPos(x + 3, ground, 2);
        level.setBlockAndUpdate(land, dev.groundburst.content.ordnance.GBOrdnance.LANDMINE.block().get().defaultBlockState());
        level.setBlockAndUpdate(clay, dev.groundburst.content.ordnance.GBOrdnance.CLAYMORE.block().get().defaultBlockState()
                .setValue(F, net.minecraft.core.Direction.SOUTH));
        level.setBlockAndUpdate(bounce, dev.groundburst.content.ordnance.GBOrdnance.BOUNCING_MINE.block().get().defaultBlockState());
        for (var p : java.util.List.of(land, clay, bounce)) {
            var be = dev.groundburst.content.ordnance.charge.ChargeBlock.at(level, p);
            if (be != null) {
                be.arm(level, null);
                if (p == bounce) {
                    be.trigger(level, null);
                }
            }
        }
    }

    /** The warheads in a row across the view, some armed; or a napalm warhead set off. */
    private static void bombs(ServerLevel level, int x, int ground, boolean napalm) {
        var F = dev.groundburst.content.ordnance.charge.ChargeBlock.FACING;
        if (napalm) {
            var at = new net.minecraft.core.BlockPos(x, ground, -6);
            level.setBlockAndUpdate(at, dev.groundburst.content.ordnance.GBOrdnance.NAPALM_WARHEAD.block().get().defaultBlockState()
                    .setValue(F, net.minecraft.core.Direction.SOUTH));
            var be = dev.groundburst.content.ordnance.charge.ChargeBlock.at(level, at);
            if (be != null) {
                be.arm(level, null);
                be.trigger(level, null);
            }
            return;
        }
        var bombs = dev.groundburst.content.ordnance.GBOrdnance.WARHEADS;
        for (int i = 0; i < bombs.size(); i++) {
            var at = new net.minecraft.core.BlockPos(x + (i % 2) * 3, ground, -9 + i * 2);
            level.setBlockAndUpdate(at, bombs.get(i).block().get().defaultBlockState().setValue(F, net.minecraft.core.Direction.SOUTH)
                    .setValue(dev.groundburst.content.ordnance.charge.ChargeBlock.ARMED, i % 2 == 0));
        }
    }

    /** The launch scenes' silo and computer, for the tick to launch from and open the screen of. */
    private static net.minecraft.core.BlockPos launchSilo, launchComputer, launchTarget;
    /** The fission scenes' 6x6x6 reactor (its screen is opened for the UI shot). */
    private static net.minecraft.core.BlockPos fissionBig;
    private static List<net.minecraft.core.BlockPos> fissionPlans = List.of();

    /**
     * A formed, powered Launch Complex with a Strategic tactical-nuke missile, aimed 400 blocks off and armed, and a Launch
     * Computer beside it (the "complex" scene launches at clock 25: 15 s of countdown, then ignition).
     */
    /** Where the hall scenes put the ICBM Assembly Hall's controller. */
    private static net.minecraft.core.BlockPos hallAt;
    private static net.minecraft.core.BlockPos assemblerAt;
    /** The assembler scene with the Laser Guidance Unit loaded (not started). */
    private static boolean assemblerLaser;

    /**
     * A missile assembler (DESIGN.md §10.4), facing north with its controller at {@code z} (the structure running south
     * from it, its missile's length across the camera's view), powered, given a warhead of its class with the class's
     * body, its longest-range motor and GPS, and set to work.
     */
    private static void assemblerSite(ServerLevel level, int x, int ground, dev.groundburst.content.assembler.AssemblerClass kind, int z) {
        var at = new net.minecraft.core.BlockPos(x, ground, z);
        assemblerAt = at;
        formed(level, at, dev.groundburst.content.assembler.GBAssemblers.controller(kind).block().get(),
                dev.groundburst.content.assembler.GBAssemblers.definition(kind), net.minecraft.core.Direction.NORTH);
        if (level.getBlockEntity(at) instanceof dev.groundburst.content.assembler.MissileAssemblerBlockEntity a) {
            a.revalidate();
            var warhead = switch (kind) {
                case LIGHT -> dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD;
                case MEDIUM -> dev.groundburst.content.ordnance.GBOrdnance.CRYO_WARHEAD;
                case HEAVY -> dev.groundburst.content.ordnance.GBOrdnance.THERMOBARIC_WARHEAD;
                case STRATEGIC -> dev.groundburst.content.ordnance.GBOrdnance.FISSION_WARHEAD;
            };
            var inv = a.inventory();
            inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_WARHEAD, new net.minecraft.world.item.ItemStack(warhead.item().get()));
            inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_BODY,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.BODIES.get(kind.size.ordinal()).get()));
            inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_MOTOR, new net.minecraft.world.item.ItemStack(
                    dev.groundburst.content.assembly.GBAssembly.MOTORS.get(dev.groundburst.content.assembly.MissileAssembly.rocketsFor(kind.size).getLast().ordinal()).get()));
            inv.setItem(dev.groundburst.content.assembler.MissileAssemblerBlockEntity.SLOT_GUIDANCE, new net.minecraft.world.item.ItemStack(
                    assemblerLaser ? dev.groundburst.content.assembly.GBAssembly.LASER_GUIDANCE.get() : dev.groundburst.content.assembly.GBAssembly.GPS_GUIDANCE.get()));
            a.energy().generate(kind.capacity);
            if (!assemblerLaser) {
                a.startAssembly();                          // (the laser one waits, ready: its screen shows what it will make)
            }
        }
    }

    /**
     * The ICBM Assembly Hall, its length across the camera's view (controller facing north, the hall running
     * south from it), powered and given a thermonuclear missile's parts with GPS, and set to work.
     */
    private static void hallSite(ServerLevel level, int x, int ground) {
        hallSite(level, x, ground, net.minecraft.world.item.ItemStack.EMPTY);
    }

    /**
     * The Special Weapons Assembler where the hall would stand (controller facing north, the bay running south from it),
     * powered and given {@code core}, a booster and GPS, and set to work.
     */
    private static void specialSite(ServerLevel level, int x, int ground, net.minecraft.world.item.ItemStack core) {
        hallAt = new net.minecraft.core.BlockPos(x, ground, -6);
        formed(level, hallAt, dev.groundburst.content.strategic.GBStrategic.SPECIAL_CONTROLLER.block().get(),
                dev.groundburst.content.strategic.GBStrategic.SPECIAL, net.minecraft.core.Direction.NORTH);
        if (level.getBlockEntity(hallAt) instanceof dev.groundburst.content.strategic.SpecialWeaponsBlockEntity special) {
            special.revalidate();
            var inv = special.inventory();
            inv.setItem(dev.groundburst.content.strategic.SpecialWeaponsBlockEntity.SLOT_CORE, core.copy());
            inv.setItem(dev.groundburst.content.strategic.SpecialWeaponsBlockEntity.SLOT_BOOSTER,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.strategic.GBStrategic.INTERCONTINENTAL_BOOSTER.get()));
            inv.setItem(dev.groundburst.content.strategic.SpecialWeaponsBlockEntity.SLOT_GUIDANCE,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.GPS_GUIDANCE.get()));
            special.energy().generate(dev.groundburst.content.strategic.SpecialWeaponsBlockEntity.CAPACITY);
            special.startAssembly();
        }
    }

    /** As {@link #hallSite(ServerLevel, int, int)}, with {@code option} in the jacket slot (a Tertiary Stage: a Tsar). */
    private static void hallSite(ServerLevel level, int x, int ground, net.minecraft.world.item.ItemStack option) {
        hallAt = new net.minecraft.core.BlockPos(x, ground, -6);
        formed(level, hallAt, dev.groundburst.content.strategic.GBStrategic.HALL_CONTROLLER.block().get(), dev.groundburst.content.strategic.GBStrategic.HALL,
                net.minecraft.core.Direction.NORTH);
        if (level.getBlockEntity(hallAt) instanceof dev.groundburst.content.strategic.StrategicAssemblyBlockEntity hall) {
            hall.revalidate();
            var inv = hall.inventory();
            inv.setItem(dev.groundburst.content.strategic.StrategicAssemblyBlockEntity.SLOT_PRIMARY,
                    dev.groundburst.content.strategic.StrategicAssembly.primaryFor(option));             // a Tsar's twin core
            inv.setItem(dev.groundburst.content.strategic.StrategicAssemblyBlockEntity.SLOT_SECONDARY,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.strategic.GBStrategic.FUSION_SECONDARY.get()));
            inv.setItem(dev.groundburst.content.strategic.StrategicAssemblyBlockEntity.SLOT_BOOSTER,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.strategic.GBStrategic.INTERCONTINENTAL_BOOSTER.get()));
            inv.setItem(dev.groundburst.content.strategic.StrategicAssemblyBlockEntity.SLOT_GUIDANCE,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.GPS_GUIDANCE.get()));
            inv.setItem(dev.groundburst.content.strategic.StrategicAssemblyBlockEntity.SLOT_JACKET, option.copy());
            hall.energy().generate(dev.groundburst.content.strategic.StrategicAssemblyBlockEntity.CAPACITY);
            hall.startAssembly();
        }
    }

    /**
     * The glow scenes' machines: the photographer turns them on (ACTIVE) on the client only, so they look as they do
     * running (fire, melt, plasma, cores) without each being fuelled and fed; the server leaves them be.
     */
    private static final List<net.minecraft.core.BlockPos> glowAt = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The flight scenes' missile, called off when the scene ends (an intercontinental one carries a live warhead). */
    private static volatile dev.groundburst.content.launch.Flights.@org.jspecify.annotations.Nullable Flight flightShown;

    /** The single-block machines with glowmasks, in three rows of seven (the glow scenes). */
    private static List<java.util.function.Supplier<net.minecraft.world.level.block.Block>> glowRow(int row) {
        var M = new Object() {
            java.util.function.Supplier<net.minecraft.world.level.block.Block> of(dev.groundburst.registry.GBBlocks.WithItem<?> w) {
                return () -> w.block().get();
            }
        };
        return switch (row) {
            case 0 -> List.of(M.of(dev.groundburst.content.machine.GBMachines.COMBUSTION_GENERATOR), M.of(dev.groundburst.content.machine.GBMachines.ORE_CRUSHER),
                    M.of(dev.groundburst.content.machine.GBMachines.ARC_FURNACE), M.of(dev.groundburst.content.machine.GBMachines.INDUSTRIAL_PRESS),
                    M.of(dev.groundburst.content.machine.GBMachines.ELECTROLYTIC_SEPARATOR), M.of(dev.groundburst.content.machine.GBMachines.WATER_PUMP),
                    M.of(dev.groundburst.content.machine.GBMachines.CRYOGENIC_CONDENSER));
            case 1 -> List.of(M.of(dev.groundburst.content.machine.GBMachines.CIRCUIT_FABRICATOR), M.of(dev.groundburst.content.machine.GBMachines.GAS_CENTRIFUGE),
                    M.of(dev.groundburst.content.machine.GBMachines.FUSION_FUEL_SYNTHESIZER), M.of(dev.groundburst.content.machine.GBMachines.EXOTIC_MATTER_FABRICATOR),
                    M.of(dev.groundburst.content.machine.GBMachines.DIESEL_GENERATOR), M.of(dev.groundburst.content.machine.GBMachines.RTG),
                    M.of(dev.groundburst.content.machine.GBMachines.BATTERY_CHARGER));
            case 3 -> List.of(M.of(dev.groundburst.content.machine.GBMachines.CRYSTAL_PULLER));
            default -> {
                List<java.util.function.Supplier<net.minecraft.world.level.block.Block>> out = new java.util.ArrayList<>();
                out.add(M.of(dev.groundburst.content.machine.GBMachines.DECONTAMINATION_SHOWER));
                out.add(M.of(dev.groundburst.content.machine.GBMachines.CANNING_MACHINE));
                dev.groundburst.content.machine.GBMachines.CAPACITOR_BANKS.forEach(b -> out.add(M.of(b)));
                out.add(M.of(dev.groundburst.content.assembly.GBAssembly.ASSEMBLY_BENCH));
                yield out;
            }
        };
    }

    /**
     * One row of single-block machines across the view, three blocks apart, fronts to the camera, charged; a coal fire
     * in the combustion generator and a battery charging in the charger.
     */
    private static void glowMachines(ServerLevel level, int x, int ground, int row) {
        var blocks = glowRow(row);
        glowAt.clear();
        int z = -3 * (blocks.size() - 1) / 2;
        for (var supplier : blocks) {
            var block = supplier.get();
            var pos = new net.minecraft.core.BlockPos(x, ground, z);
            var state = block.defaultBlockState().setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST);
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, null, net.minecraft.world.item.ItemStack.EMPTY);
            glowAt.add(pos);
            if (level.getBlockEntity(pos) instanceof dev.groundburst.content.machine.MachineBlockEntity machine) {
                if (machine instanceof dev.groundburst.content.power.CombustionGeneratorBlockEntity) {
                    machine.inventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 16));   // it burns
                } else {
                    machine.energy().generate(Integer.MAX_VALUE);
                }
                if (machine instanceof dev.groundburst.content.power.BatteryChargerBlockEntity) {
                    machine.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.part.GBParts.BATTERY.get()));
                }
                if (machine instanceof dev.groundburst.content.machine.CrystalPullerBlockEntity) {   // it really grows one
                    machine.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.part.GBParts.POLYSILICON.get(), 4));
                    machine.inventory().setItem(1, new net.minecraft.world.item.ItemStack(dev.groundburst.content.part.GBParts.QUARTZ_CRUCIBLE.get()));
                    machine.tank(0).fillInternal(new net.neoforged.neoforge.fluids.FluidStack(dev.groundburst.content.fluid.GBFluids.ARGON.source().get(), 1_000));
                }
            }
            z += 3;
        }
    }

    /**
     * Formed multiblocks across the view, fronts to the camera, powered: the chemical plant, fission reactor and
     * singularity reactor (row 0), or the fusion reactor and particle accelerator (row 1).
     */
    private static void glowReactors(ServerLevel level, int x, int ground, int row) {
        List<Object[]> list = row == 2
                ? List.<Object[]>of(new Object[] {dev.groundburst.content.machine.GBMachines.CLEAN_ROOM, dev.groundburst.content.multiblock.GBMultiblocks.CLEAN_ROOM})
                : row == 0
                ? List.of(new Object[] {dev.groundburst.content.machine.GBMachines.CHEMICAL_PLANT, dev.groundburst.content.multiblock.GBMultiblocks.CHEMICAL_PLANT},
                        new Object[] {dev.groundburst.content.machine.GBMachines.FISSION_REACTOR, dev.groundburst.content.multiblock.GBMultiblocks.FISSION_REACTOR},
                        new Object[] {dev.groundburst.content.machine.GBMachines.SINGULARITY_REACTOR, dev.groundburst.content.multiblock.GBMultiblocks.SINGULARITY_REACTOR})
                : List.of(new Object[] {dev.groundburst.content.machine.GBMachines.FUSION_REACTOR, dev.groundburst.content.multiblock.GBMultiblocks.FUSION_REACTOR},
                        new Object[] {dev.groundburst.content.machine.GBMachines.PARTICLE_ACCELERATOR, dev.groundburst.content.multiblock.GBMultiblocks.PARTICLE_ACCELERATOR});
        // Each one's width across the view (z), from its cells with the controller at the origin facing west.
        int total = 0;
        int[][] spans = new int[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            var def = (dev.groundburst.content.multiblock.MultiblockDefinition) list.get(i)[1];
            int lo = 0, hi = 0;
            for (var cell : def.cells(net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.WEST)) {
                lo = Math.min(lo, cell.pos().getZ());
                hi = Math.max(hi, cell.pos().getZ());
            }
            spans[i] = new int[] {lo, hi};
            total += hi - lo + 1 + (i > 0 ? 3 : 0);
        }
        int z = -total / 2;
        glowAt.clear();
        for (int i = 0; i < list.size(); i++) {
            @SuppressWarnings("unchecked")
            var controller = ((dev.groundburst.registry.GBBlocks.WithItem<? extends net.minecraft.world.level.block.Block>) list.get(i)[0]).block().get();
            var def = (dev.groundburst.content.multiblock.MultiblockDefinition) list.get(i)[1];
            var at = new net.minecraft.core.BlockPos(x, ground, z - spans[i][0]);
            formed(level, at, controller, def);
            glowAt.add(at);
            if (level.getBlockEntity(at) instanceof dev.groundburst.content.multiblock.MultiblockController mc) {
                mc.revalidate();
            }
            if (level.getBlockEntity(at) instanceof dev.groundburst.content.machine.MachineBlockEntity machine) {
                machine.energy().generate(Integer.MAX_VALUE);
            }
            z += spans[i][1] - spans[i][0] + 1 + 3;
        }
    }

    private static void complexSite(ServerLevel level, int x, int ground) {
        complexSite(level, x, ground, false, net.minecraft.world.item.ItemStack.EMPTY);
    }

    /**
     * The Launch Complex and its computer; with {@code icbm} an intercontinental missile on the pad, not armed (built
     * with {@code option} in the hall's jacket slot: nothing for a thermonuclear one, a Tertiary Stage for the Tsar).
     */
    private static void complexSite(ServerLevel level, int x, int ground, boolean icbm, net.minecraft.world.item.ItemStack option) {
        launchTarget = new net.minecraft.core.BlockPos(x + 400, 64, 140);
        launchSilo = new net.minecraft.core.BlockPos(x, ground, -4);
        launchComputer = new net.minecraft.core.BlockPos(x - 6, ground, 4);
        formed(level, launchSilo, dev.groundburst.content.launch.GBLaunch.COMPLEX_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.COMPLEX);
        formed(level, launchComputer, dev.groundburst.content.launch.GBLaunch.COMPUTER_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.COMPUTER);
        if (level.getBlockEntity(launchSilo) instanceof dev.groundburst.content.launch.LaunchComplexBlockEntity complex) {
            complex.revalidate();
            complex.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE, icbm
                    ? dev.groundburst.content.strategic.StrategicAssembly.assemble(option,
                            new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.GPS_GUIDANCE.get()), net.minecraft.world.item.ItemStack.EMPTY)
                    : dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.XL, dev.groundburst.content.assembly.MissileParts.Motor.LIQUID, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false)));
            complex.energy().generate(dev.groundburst.content.launch.LaunchComplexBlockEntity.COMPLEX_ENERGY);
            complex.setTarget(launchTarget.getX(), launchTarget.getZ());
            complex.arm(true);
        }
        if (level.getBlockEntity(launchComputer) instanceof dev.groundburst.content.launch.LaunchComputerBlockEntity computer) {
            computer.revalidate();
            computer.energy().generate(dev.groundburst.content.launch.LaunchComputerBlockEntity.ENERGY_CAPACITY);
        }
    }

    /**
     * A formed, loaded, powered Launch Silo and Launch Computer side by side, fronts to the camera; the silo aimed and
     * armed. With {@code extras}, five more silos behind them in different states, so the computer's list scrolls.
     */
    private static void launchSite(ServerLevel level, int x, int ground, boolean dive, boolean extras) {
        // The long shot aims 400 blocks off (the scope shows it in flight); the dive lands 35 blocks off, in view.
        launchTarget = dive ? new net.minecraft.core.BlockPos(x + 25, ground, 25) : new net.minecraft.core.BlockPos(x + 400, 64, 140);
        launchSilo = new net.minecraft.core.BlockPos(x, ground, -3);
        launchComputer = new net.minecraft.core.BlockPos(x - 4, ground, 5);
        formed(level, launchSilo, dev.groundburst.content.launch.GBLaunch.SILO_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.SILO);
        formed(level, launchComputer, dev.groundburst.content.launch.GBLaunch.COMPUTER_CONTROLLER.block().get(),
                dev.groundburst.content.launch.GBLaunch.COMPUTER);
        if (level.getBlockEntity(launchSilo) instanceof dev.groundburst.content.launch.LaunchSiloBlockEntity silo) {
            silo.revalidate();
            silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                    dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.L, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false)));
            silo.energy().generate(dev.groundburst.content.launch.LaunchSiloBlockEntity.ENERGY_CAPACITY);
            silo.setTarget(launchTarget.getX(), launchTarget.getZ());
            silo.arm(true);
        }
        if (level.getBlockEntity(launchComputer) instanceof dev.groundburst.content.launch.LaunchComputerBlockEntity computer) {
            computer.revalidate();
            computer.energy().generate(dev.groundburst.content.launch.LaunchComputerBlockEntity.ENERGY_CAPACITY);
        }
        for (int i = 0; extras && i < 5; i++) {
            var at = new net.minecraft.core.BlockPos(x + 12 + 7 * i, ground, -30);
            formed(level, at, dev.groundburst.content.launch.GBLaunch.SILO_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.SILO);
            if (level.getBlockEntity(at) instanceof dev.groundburst.content.launch.LaunchSiloBlockEntity silo) {
                silo.revalidate();
                if (i != 1) {                                  // one with no missile
                    silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                            dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HEAVY_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false)));
                }
                if (i != 2) {                                  // one still charging
                    silo.energy().generate(dev.groundburst.content.launch.LaunchSiloBlockEntity.ENERGY_CAPACITY);
                }
                if (i >= 3) {                                  // two aimed elsewhere
                    silo.setTarget(x - 150 + 60 * i, -200 + 40 * i);
                }
                if (i == 0) {                                  // one armed beside the main silo's aim: its strike drawn dark
                    silo.setTarget(x + 40, 45);
                    silo.arm(true);
                }
                if (i == 3) {                                  // one held stopped by redstone (wants a signal, has none)
                    silo.cycleRedstoneMode();
                }
                if (i == 4) {                                  // one knocked out by an EMP
                    dev.groundburst.content.effect.EmpFields.add(level, net.minecraft.world.phys.Vec3.atCenterOf(at), 3, 6000, null);
                }
            }
        }
    }

    private static net.minecraft.core.BlockPos radarAt;
    private static net.minecraft.core.BlockPos scudAt;

    /**
     * The SCUD Storm (DESIGN.md §19.5) facing the camera (its pad running away east), nine Toxin SCUDs loaded, charged,
     * aimed 170 blocks off and armed (the scene launches it at clock 25).
     */
    /** China's Nuclear Missile Silo (DESIGN.md §19.5) facing the camera, its missile loaded, charged, aimed 600 blocks off. */
    private static void nukeSiloSite(ServerLevel level, int x, int ground) {
        scudAt = new net.minecraft.core.BlockPos(x + 2, ground, 0);
        formed(level, scudAt, dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_CONTROLLER.block().get(),
                dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE, net.minecraft.core.Direction.WEST);
        if (level.getBlockEntity(scudAt) instanceof dev.groundburst.content.superweapon.NuclearMissileBlockEntity be) {
            be.revalidate();
            be.inventory().setItem(dev.groundburst.content.superweapon.SuperweaponBlockEntity.SLOT_AMMO,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.superweapon.GBSuperweapons.NUCLEAR_MISSILE_ROUND.get()));
            be.energy().generate(400_000);
            be.chargeForTest();
            be.setTarget(x + 600, -300);
            be.arm(true);
        }
    }

    /**
     * An ammunition factory (DESIGN.md §19.5), its parts loaded and powered: it sets to work at once. The Arms Dealer
     * faces the camera; the War Factory faces south, so its yard (on its right) is nearest the camera and its missile
     * lies across the view, the hall and the annex behind it.
     */
    private static void factorySite(ServerLevel level, int x, int ground, boolean arms) {
        var at = arms ? new net.minecraft.core.BlockPos(x + 2, ground, 0) : new net.minecraft.core.BlockPos(x + 6, ground, 6);
        formed(level, at, (arms ? dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER_CONTROLLER : dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY_CONTROLLER).block().get(),
                arms ? dev.groundburst.content.superweapon.GBSuperweapons.ARMS_DEALER : dev.groundburst.content.superweapon.GBSuperweapons.WAR_FACTORY,
                arms ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.SOUTH);
        if (level.getBlockEntity(at) instanceof dev.groundburst.content.superweapon.AmmoFactoryBlockEntity be) {
            be.revalidate();
            var inputs = be.kind().inputs;
            for (int i = 0; i < inputs.size(); i++) {
                be.inventory().setItem(i, new net.minecraft.world.item.ItemStack(inputs.get(i).item().get(), inputs.get(i).count()));
            }
            be.energy().generate(be.kind().capacity);
        }
    }

    private static net.minecraft.core.BlockPos cruiseAt;

    /**
     * The Cruise Missile Launcher (slice 9c), side on to the camera: facing north, its front to the camera's left, its
     * deck running across the view; an HE and a Cryo cruise missile in it, powered, aimed far off east of north, armed.
     */
    private static void cruiseSite(ServerLevel level, int x, int ground) {
        cruiseAt = new net.minecraft.core.BlockPos(x, ground, -3);
        formed(level, cruiseAt, dev.groundburst.content.launch.GBLaunch.CRUISE_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.CRUISE_LAUNCHER,
                net.minecraft.core.Direction.NORTH);
        if (level.getBlockEntity(cruiseAt) instanceof dev.groundburst.content.launch.CruiseLauncherBlockEntity be) {
            be.revalidate();
            be.inventory().setItem(dev.groundburst.content.launch.CruiseLauncherBlockEntity.SLOT_A, cruiseMissile(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD,
                    dev.groundburst.content.assembly.SizeClass.S));
            be.inventory().setItem(dev.groundburst.content.launch.CruiseLauncherBlockEntity.SLOT_B, cruiseMissile(dev.groundburst.content.ordnance.GBOrdnance.CRYO_WARHEAD,
                    dev.groundburst.content.assembly.SizeClass.M));
            be.energy().generate(dev.groundburst.content.launch.CruiseLauncherBlockEntity.ENERGY_CAPACITY);
            be.setTarget(x + 700, -500);
            be.arm(true);
        }
    }

    private static net.minecraft.core.BlockPos vlsAt;

    /**
     * The Vertical Launch Battery, side on to the camera: facing north, its walkway to the camera's left, its cells running
     * across the view; sixteen HE missiles in it (Light and Medium), powered, its spread a grid 12 apart, aimed far off
     * north-east, armed.
     */
    private static void vlsSite(ServerLevel level, int x, int ground) {
        vlsAt = new net.minecraft.core.BlockPos(x, ground, -3);
        formed(level, vlsAt, dev.groundburst.content.launch.GBLaunch.VLS_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.VLS,
                net.minecraft.core.Direction.NORTH);
        if (level.getBlockEntity(vlsAt) instanceof dev.groundburst.content.launch.VerticalLaunchBatteryBlockEntity be) {
            be.revalidate();
            for (int i = 0; i < dev.groundburst.content.launch.VerticalLaunchBatteryBlockEntity.CELLS; i++) {
                var body = i % 2 == 0 ? dev.groundburst.content.assembly.SizeClass.M : dev.groundburst.content.assembly.SizeClass.S;
                be.inventory().setItem(i, dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(
                        new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()), body,
                        dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false)));
            }
            be.energy().generate(dev.groundburst.content.launch.VerticalLaunchBatteryBlockEntity.ENERGY_CAPACITY);
            be.setSpread(dev.groundburst.content.launch.LaunchNetwork.Pattern.GRID, 12);
            be.setTarget(x + 300, -600);
            be.arm(true);
        }
    }

    private static net.minecraft.world.item.ItemStack cruiseMissile(dev.groundburst.registry.GBBlocks.WithItem<?> warhead, dev.groundburst.content.assembly.SizeClass body) {
        return dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(
                new net.minecraft.world.item.ItemStack(warhead.item().get()), body, dev.groundburst.content.assembly.MissileParts.Motor.CRUISE,
                dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false));
    }

    /**
     * A cruise missile from far off to the camera's left (north), southbound along a line 40 blocks beyond the site:
     * passing the camera's line of sight low overhead, or ({@code dive}) 70 blocks short of a target 10 blocks beyond
     * the site, about to pop up and dive onto it.
     */
    private static dev.groundburst.content.launch.Flights.Flight cruiseFlight(ServerLevel level, int x, int ground, boolean dive) {
        double lane = dive ? 10.5 : 40.5;
        Vec3 pad = new Vec3(x + lane, ground + 1, dive ? -300 : -520);
        Vec3 target = new Vec3(x + lane, ground, dive ? 0.5 : 520);
        var spec = dev.groundburst.content.assembly.MissileItem.spec(cruiseMissile(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD,
                dev.groundburst.content.assembly.SizeClass.S));
        var f = dev.groundburst.content.launch.Flights.launchCruise(level, pad, 0, target, spec, null);
        f.skipForTest(dive ? f.ticksToImpact() - 45 : 322);
        return f;
    }

    private static void scudStormSite(ServerLevel level, int x, int ground) {
        scudAt = new net.minecraft.core.BlockPos(x + 2, ground, 0);
        formed(level, scudAt, dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM_CONTROLLER.block().get(), dev.groundburst.content.superweapon.GBSuperweapons.SCUD_STORM,
                net.minecraft.core.Direction.WEST);
        if (level.getBlockEntity(scudAt) instanceof dev.groundburst.content.superweapon.ScudStormBlockEntity be) {
            be.revalidate();
            be.inventory().setItem(dev.groundburst.content.superweapon.SuperweaponBlockEntity.SLOT_AMMO,
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.superweapon.GBSuperweapons.TOXIN_SCUD.get(), dev.groundburst.content.superweapon.ScudStormBlockEntity.CAPACITY));
            be.energy().generate(400_000);
            be.chargeForTest();
            be.setTarget(x + 170, -60);
            be.arm(true);
        }
    }

    /**
     * A formed, powered Radar Array facing the camera, and missiles in the air round it (no warheads): two coming down
     * near it (incoming), two passing elsewhere.
     */
    private static void radarSite(ServerLevel level, int x, int ground) {
        radarAt = new net.minecraft.core.BlockPos(x, ground, 0);
        formed(level, radarAt, dev.groundburst.content.launch.GBLaunch.RADAR_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.RADAR);
        if (level.getBlockEntity(radarAt) instanceof dev.groundburst.content.launch.RadarArrayBlockEntity radar) {
            radar.revalidate();
            radar.energy().generate(dev.groundburst.content.launch.RadarArrayBlockEntity.ENERGY_CAPACITY);
        }
        // A Large Radar Dish on its own beside it, as placed.
        var dish = new net.minecraft.core.BlockPos(x - 3, ground, -7);
        var dishBlock = dev.groundburst.content.launch.GBLaunch.LARGE_RADAR_DISH.block().get();
        level.setBlockAndUpdate(dish, dishBlock.defaultBlockState());
        dishBlock.setPlacedBy(level, dish, level.getBlockState(dish), null, net.minecraft.world.item.ItemStack.EMPTY);
        Vec3 c = Vec3.atBottomCenterOf(radarAt);
        var empty = net.minecraft.world.item.ItemStack.EMPTY;
        dev.groundburst.content.launch.Flights.launch(level, c.add(-1500, 0, 600), c.add(120, 0, -60), empty, null);
        dev.groundburst.content.launch.Flights.launch(level, c.add(900, 0, 1400), c.add(-200, 0, 150), empty, null);
        dev.groundburst.content.launch.Flights.launch(level, c.add(-1200, 0, -1500), c.add(1500, 0, -900), empty, null);
        dev.groundburst.content.launch.Flights.launch(level, c.add(1600, 0, 300), c.add(1300, 0, 1500), empty, null);
    }

    private static net.minecraft.core.BlockPos benchAt, pressAt;
    /** The station scenes: the station (whose screen station_ui opens). */
    private static net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos stationAt;

    /**
     * The station scenes: the Launch Control Station facing the camera; behind it two Launch Silos, loaded and powered, and
     * a Radar Array off to the side, all linked to it; the near silo aimed 600 blocks off and armed, the far one aimed at a
     * ring round a point 900 blocks off; one of the network's missiles in the air.
     */
    /**
     * The Laser Designator's scenes: a reinforced-concrete pillbox (a slit in its west wall) to lase, and the camera, a
     * creative player standing 40 blocks west of it, the designator in hand (use is held down from the fourth tick). In the first, a laser-guided
     * missile from far beyond the pillbox, aimed 22 blocks south of it, 76 ticks out: it homes on the spot.
     */
    /** The side scene's camera (an armour stand's entity id; -1: none). */
    private static volatile int sideCamera = -1;

    private static void designatorSite(ServerLevel level, int x, int ground, Scene s) {
        var concrete = dev.groundburst.content.launch.GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 2;
                    boolean slit = dy == 1 && dx == -2 && Math.abs(dz) <= 1;
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground + dy, dz), wall && !slit ? concrete : air);
                }
            }
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, x - s.distance() + 0.5, ground, 0.5, java.util.Set.of(), -90, s.pitch(), true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(dev.groundburst.content.launch.GBLaunch.LASER_DESIGNATOR.get()));
        if (s.name().equals("designator_call")) {
            for (int dx = -2; dx <= 2; dx++) {                 // pillbox B, 14 blocks south of A
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy < 3; dy++) {
                        boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 2;
                        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground + dy, 14 + dz), wall ? concrete : air);
                    }
                }
            }
            var stationAt = new net.minecraft.core.BlockPos(x - 150, ground, 12);
            formed(level, stationAt, dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.STATION);
            if (level.getBlockEntity(stationAt) instanceof dev.groundburst.content.launch.LaunchStationBlockEntity st) {
                st.revalidate();
                st.energy().generate(dev.groundburst.content.launch.LaunchStationBlockEntity.ENERGY_CAPACITY);
                var network = st.network(level);
                for (int z : network == null ? new int[0] : new int[] {-10, -24}) {           // two silos with laser-guided missiles: #1 and #2
                    var siloAt = new net.minecraft.core.BlockPos(x - 150, ground, z);
                    formed(level, siloAt, dev.groundburst.content.launch.GBLaunch.SILO_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.SILO);
                    if (level.getBlockEntity(siloAt) instanceof dev.groundburst.content.launch.LaunchSiloBlockEntity silo) {
                        silo.revalidate();
                        silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                                dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(
                                        new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()),
                                        dev.groundburst.content.assembly.SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II,
                                        dev.groundburst.content.assembly.MissileParts.Guidance.LASER, false)));
                        silo.energy().generate(dev.groundburst.content.launch.LaunchSiloBlockEntity.ENERGY_CAPACITY);
                        dev.groundburst.content.launch.LaunchNetwork.of(level).link(level, network.id, silo);
                    }
                }
                if (network != null) {
                    player.getMainHandItem().set(dev.groundburst.registry.GBDataComponents.LAUNCH_CODES.get(), network.code());
                }
            }
        }
        if (s.name().equals("designator_side")) {
            // An invisible armour stand off to one side and behind, the camera: the holder on the left of the view, its
            // beam running away across it to the pillbox.
            var stand = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, level);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            double cx = x - s.distance() - 9.5, cy = ground + 2.5, cz = 13.5;
            double lx = x - s.distance() + 60, lz = 0.5;
            float yaw = (float) Math.toDegrees(Math.atan2(-(lx - cx), lz - cz));
            stand.snapTo(cx, cy, cz, yaw, 8);
            stand.setYHeadRot(yaw);
            level.addFreshEntity(stand);
            sideCamera = stand.getId();
        }
        if (s.name().equals("designator")) {
            var spec = new dev.groundburst.content.assembly.MissileSpec(
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()),
                    dev.groundburst.content.assembly.SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II,
                    dev.groundburst.content.assembly.MissileParts.Guidance.LASER, false);
            var f = dev.groundburst.content.launch.Flights.launch(level, new Vec3(x + 420, ground, 160), new Vec3(x + 0.5, ground, 22.5), spec, player);
            f.skipForTest(Math.max(0, f.ticksToImpact() - 76));
            flightShown = f;
        }
    }

    private static void stationSite(ServerLevel level, int x, int ground) {
        var west = net.minecraft.core.Direction.WEST;
        var at = new net.minecraft.core.BlockPos(x + 2, ground, 0);
        formed(level, at, dev.groundburst.content.launch.GBLaunch.STATION_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.STATION, west);
        stationAt = at;
        if (!(level.getBlockEntity(at) instanceof dev.groundburst.content.launch.LaunchStationBlockEntity st)) {
            return;
        }
        st.revalidate();
        st.energy().generate(dev.groundburst.content.launch.LaunchStationBlockEntity.ENERGY_CAPACITY);
        var network = st.network(level);
        var nets = dev.groundburst.content.launch.LaunchNetwork.of(level);
        java.util.List<dev.groundburst.content.launch.LaunchSiloBlockEntity> silos = new java.util.ArrayList<>();
        for (var siloAt : java.util.List.of(new net.minecraft.core.BlockPos(x + 14, ground, -9), new net.minecraft.core.BlockPos(x + 14, ground, 7))) {
            formed(level, siloAt, dev.groundburst.content.launch.GBLaunch.SILO_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.SILO, west);
            if (level.getBlockEntity(siloAt) instanceof dev.groundburst.content.launch.LaunchSiloBlockEntity silo) {
                silo.revalidate();
                silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                        dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(
                                new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()),
                                dev.groundburst.content.assembly.SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II,
                                dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false)));
                silo.energy().generate(dev.groundburst.content.launch.LaunchSiloBlockEntity.ENERGY_CAPACITY);
                nets.link(level, network.id, silo);
                silos.add(silo);
            }
        }
        var radarAt = new net.minecraft.core.BlockPos(x + 16, ground, 22);
        formed(level, radarAt, dev.groundburst.content.launch.GBLaunch.RADAR_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.RADAR, west);
        if (level.getBlockEntity(radarAt) instanceof dev.groundburst.content.launch.RadarArrayBlockEntity radar) {
            radar.revalidate();
            radar.energy().generate(dev.groundburst.content.launch.RadarArrayBlockEntity.ENERGY_CAPACITY);
            nets.link(level, network.id, radar);
        }
        if (silos.size() == 2) {
            var near = silos.get(0).getBlockPos();
            nets.aim(level, network.id, java.util.List.of(near), near.getX() + 600, near.getZ() - 200, dev.groundburst.content.launch.LaunchNetwork.Pattern.POINT, 0);
            nets.arm(level, network.id, java.util.List.of(near), true);
            var far = silos.get(1).getBlockPos();
            nets.aim(level, network.id, java.util.List.of(far), far.getX() + 900, far.getZ() + 300, dev.groundburst.content.launch.LaunchNetwork.Pattern.POINT, 0);
            // One of the network's missiles in the air, from the far silo's pad toward a point 800 blocks off.
            Vec3 pad = Vec3.atBottomCenterOf(far).add(1, 0, 1);
            var flight = dev.groundburst.content.launch.Flights.launch(level, pad, pad.add(800, 0, -400), net.minecraft.world.item.ItemStack.EMPTY, null);
            flight.skipForTest(300);
            dev.groundburst.content.launch.LaunchNetwork.launched(level, far, flight);
        }
    }

    /** The alarms scene: its panel, and the receiver whose screen alarm_ui opens. */
    private static net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos alarmPanelAt, alarmReceiverAt;
    /** The storm_pulses scene: its storm's eye, and where it strikes in front of the camera (so a pulse's ring shows near). */
    private static Vec3 stormCentre;
    private static net.minecraft.core.BlockPos stormSpot;
    /** Set by the JEI plugin when JEI is running: opens its Assembly Bench recipes (the "assembly_jei" scene). */
    public static Runnable showAssemblyRecipes = () -> {};
    /** Set by the JEI plugin when JEI is running: opens the ICBM Assembly Hall's recipes (the "hall_jei" scene). */
    public static Runnable showHallRecipes = () -> {};
    /** Set by the JEI plugin: opens the Special Weapons Assembler's recipes (the "special_jei" scene). */
    public static Runnable showSpecialRecipes = () -> {};
    /** Set by the JEI plugin: filters its list by some text (the "icbm_jei" scene). */
    public static java.util.function.Consumer<String> jeiFilter = s -> {};
    /** Set by the JEI plugin: shows the recipes that make a stack, as R on it in the list does. */
    public static java.util.function.Consumer<net.minecraft.world.item.ItemStack> jeiRecipesFor = s -> {};

    /**
     * Phase 7: two Ordnance Assembly Benches facing the camera. The near one is assembling a Poison Gas Warhead (parts
     * loaded, powered, hoist working); the far one has a finished warhead waiting in its output slot.
     */
    private static void assemblySite(ServerLevel level, int x, int ground) {
        var block = dev.groundburst.content.assembly.GBAssembly.ASSEMBLY_BENCH.block().get();
        benchAt = new net.minecraft.core.BlockPos(x, ground, -1);
        var far = new net.minecraft.core.BlockPos(x + 2, ground, -4);
        for (var at : java.util.List.of(benchAt, far)) {
            var state = block.defaultBlockState().setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST);
            level.setBlockAndUpdate(at, state);
            block.setPlacedBy(level, at, state, null, net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (level.getBlockEntity(benchAt) instanceof dev.groundburst.content.assembly.AssemblyBenchBlockEntity b) {
            b.energy().generate(dev.groundburst.content.assembly.AssemblyBenchBlockEntity.CAPACITY);
            b.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.GAS_DISPENSER.get(), 3));
            b.inventory().setItem(1, new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.MEDIUM_CASING.get(), 3));
            b.inventory().setItem(2, new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.BAROMETRIC_FUZE.get(), 3));
            b.startAssembly();
        }
        if (level.getBlockEntity(far) instanceof dev.groundburst.content.assembly.AssemblyBenchBlockEntity b) {
            b.inventory().setItem(3, new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()));
        }
    }

    private static void formed(ServerLevel level, net.minecraft.core.BlockPos at, net.minecraft.world.level.block.Block controller,
            dev.groundburst.content.multiblock.MultiblockDefinition def) {
        formed(level, at, controller, def, net.minecraft.core.Direction.WEST);
    }

    /**
     * The alarms scene: a stone-brick wall across the view, its face toward the camera; on it (facing the camera) the
     * receiver, the panel, the monitor and the display at eye height, the klaxon, the bell and two emergency lights (one
     * kept lit white) above; on top five beacons, red rotating, amber flashing, blue rotating, green steady, white
     * rotating; the siren on a post at its end; a relay on the ground. All on channel 0.
     */
    private static void alarmWall(ServerLevel level, int x, int ground) {
        var west = net.minecraft.core.Direction.WEST;
        for (int z = -5; z <= 5; z++) {
            for (int y = 0; y < 3; y++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + 1, ground + y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> put = level::setBlockAndUpdate;
        var h = dev.groundburst.content.alarm.AlarmBlock.HORIZONTAL;
        var six = dev.groundburst.content.alarm.AlarmBlock.Six.FACING;
        put.accept(new net.minecraft.core.BlockPos(x, ground + 1, -4), dev.groundburst.content.alarm.GBAlarms.RECEIVER.block().get().defaultBlockState().setValue(h, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground + 1, -2), dev.groundburst.content.alarm.GBAlarms.PANEL.block().get().defaultBlockState().setValue(h, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground + 1, 0), dev.groundburst.content.alarm.GBAlarms.MONITOR.block().get().defaultBlockState().setValue(h, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground + 1, 2), dev.groundburst.content.alarm.GBAlarms.DISPLAY.block().get().defaultBlockState().setValue(h, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground + 2, -3), dev.groundburst.content.alarm.GBAlarms.KLAXON.block().get().defaultBlockState().setValue(h, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground + 2, -1), dev.groundburst.content.alarm.GBAlarms.BELL.block().get().defaultBlockState().setValue(h, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground + 2, 1), dev.groundburst.content.alarm.GBAlarms.LIGHT.block().get().defaultBlockState().setValue(six, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground + 2, 3), dev.groundburst.content.alarm.GBAlarms.LIGHT.block().get().defaultBlockState().setValue(six, west));
        put.accept(new net.minecraft.core.BlockPos(x, ground, 4), dev.groundburst.content.alarm.GBAlarms.RELAY.block().get().defaultBlockState().setValue(h, west));
        int[][] beacons = {{-4, 0, 0}, {-2, 1, 1}, {0, 0, 2}, {2, 2, 3}, {4, 0, 4}};                 // z, mode, colour
        for (int[] b : beacons) {
            var at = new net.minecraft.core.BlockPos(x + 1, ground + 3, b[0]);
            put.accept(at, dev.groundburst.content.alarm.GBAlarms.BEACON.block().get().defaultBlockState());
            if (level.getBlockEntity(at) instanceof dev.groundburst.content.alarm.AlarmBlockEntity be) {
                be.applySettings(0, new int[] {b[1], b[2]});
            }
        }
        for (int y = 0; y < 3; y++) {
            put.accept(new net.minecraft.core.BlockPos(x, ground + y, -6), net.minecraft.world.level.block.Blocks.IRON_BARS.defaultBlockState());
        }
        put.accept(new net.minecraft.core.BlockPos(x, ground + 3, -6), dev.groundburst.content.alarm.GBAlarms.SIREN.block().get().defaultBlockState().setValue(h, west));
        if (level.getBlockEntity(new net.minecraft.core.BlockPos(x, ground + 2, 3)) instanceof dev.groundburst.content.alarm.AlarmBlockEntity light) {
            light.applySettings(0, new int[] {1});                                             // kept lit white
        }
        alarmPanelAt = new net.minecraft.core.BlockPos(x, ground + 1, -2);
        alarmReceiverAt = new net.minecraft.core.BlockPos(x, ground + 1, -4);
    }

    /** Builds a structure as the Structure Spawner does (each 3x3's middle before its edges; its required air dug out). */
    private static void formed(ServerLevel level, net.minecraft.core.BlockPos at, net.minecraft.world.level.block.Block controller,
            dev.groundburst.content.multiblock.MultiblockDefinition def, net.minecraft.core.Direction facing) {
        level.setBlockAndUpdate(at, controller.defaultBlockState().setValue(dev.groundburst.content.machine.MachineBlock.FACING, facing));
        for (var cell : dev.groundburst.content.multiblock.StructureSpawnerItem.buildOrder(def, at, facing)) {
            if (cell.key() == '.') {
                level.setBlockAndUpdate(cell.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            } else if (cell.key() != dev.groundburst.content.multiblock.MultiblockDefinition.CONTROLLER) {
                level.setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    /**
     * The fission scenes: the 4x4x4 example (eight rods, water) and a 6x6x6 reactor with 32 channels in a checkerboard
     * (heavy water), fronts to the camera, fuelled with rods part burned and the control rods 70% out, so both run.
     */
    private static void fissionSite(ServerLevel level, int x, int ground) {
        var controller = dev.groundburst.content.machine.GBMachines.FISSION_REACTOR.block().get();
        var small = new net.minecraft.core.BlockPos(x, ground, -5);
        formed(level, small, controller, dev.groundburst.content.multiblock.GBMultiblocks.FISSION_REACTOR);
        fissionBig = new net.minecraft.core.BlockPos(x, ground, 5);
        fissionBox(level, fissionBig, net.minecraft.core.Direction.WEST, 6, 6, 6);
        for (var at : List.of(small, fissionBig)) {
            if (level.getBlockEntity(at) instanceof dev.groundburst.content.machine.FissionReactorBlockEntity r) {
                r.revalidate();
                boolean big = at == fissionBig;
                r.tank(0).fillInternal(new net.neoforged.neoforge.fluids.FluidStack(big ? dev.groundburst.content.fluid.GBFluids.HEAVY_WATER.source().get()
                        : net.minecraft.world.level.material.Fluids.WATER, 32_000));
                int k = 0;
                for (int slot : r.channelSlots()) {
                    var rod = new net.minecraft.world.item.ItemStack(dev.groundburst.content.nuclear.GBNuclear.URANIUM_FUEL_ROD.get());
                    int life = dev.groundburst.content.machine.FissionReactorBlockEntity.ROD_LIFE + (k * 977) % 9_000 - 4_500;
                    rod.set(dev.groundburst.registry.GBDataComponents.FUEL_BURNUP.get(),
                            new dev.groundburst.content.nuclear.FuelBurnup(life * ((k * 37) % 90) / 100f, life));
                    r.inventory().setItem(slot, rod);
                    k++;
                }
                for (int i = 0; i < 7; i++) {
                    r.handleButton(dev.groundburst.content.machine.FissionReactorBlockEntity.BUTTON_WITHDRAW);
                }
            }
        }
    }

    /**
     * A {@code w} x {@code h} x {@code d} Fission Reactor round a controller at {@code at} (in the bottom of its front
     * wall, second from the left as patterns have it): lead casing, reactor glass in the side walls, the Item Port in the
     * roof, the Energy Port low in the back wall and the Fluid Port low in the left wall; Fuel Channels in a checkerboard
     * through the core, water between them.
     */
    private static void fissionBox(ServerLevel level, net.minecraft.core.BlockPos at, net.minecraft.core.Direction facing, int w, int h, int d) {
        level.setBlockAndUpdate(at, dev.groundburst.content.machine.GBMachines.FISSION_REACTOR.block().get().defaultBlockState()
                .setValue(dev.groundburst.content.machine.MachineBlock.FACING, facing));
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        for (int l = 0; l < h; l++) {
            for (int r = 0; r < d; r++) {
                for (int c = -1; c <= w - 2; c++) {
                    var pos = at.relative(right, c).above(l).relative(back, r);
                    if (pos.equals(at)) {
                        continue;
                    }
                    int sides = (c == -1 || c == w - 2 ? 1 : 0) + (l == 0 || l == h - 1 ? 1 : 0) + (r == 0 || r == d - 1 ? 1 : 0);
                    net.minecraft.world.level.block.Block block;
                    if (sides == 0) {
                        block = (c + l + r) % 2 == 0 ? dev.groundburst.content.multiblock.GBMultiblocks.FUEL_CHANNEL.block().get()
                                : net.minecraft.world.level.block.Blocks.AIR;
                    } else if (sides == 1 && l == h - 1) {
                        block = c == w / 2 - 1 && r == 1 ? dev.groundburst.content.multiblock.GBMultiblocks.ITEM_PORT.block().get()
                                : dev.groundburst.content.multiblock.GBMultiblocks.LEAD_LINED_CASING.block().get();
                    } else if (sides == 1 && l == 1 && r == d - 1 && c == (w - 3) / 2) {
                        block = dev.groundburst.content.multiblock.GBMultiblocks.ENERGY_PORT.block().get();
                    } else if (sides == 1 && l == 1 && c == -1 && r == d - 2) {
                        block = dev.groundburst.content.multiblock.GBMultiblocks.FLUID_PORT.block().get();
                    } else if (sides == 1 && l > 0) {
                        block = dev.groundburst.content.multiblock.GBMultiblocks.REACTOR_GLASS.block().get();
                    } else {
                        block = dev.groundburst.content.multiblock.GBMultiblocks.LEAD_LINED_CASING.block().get();
                    }
                    level.setBlockAndUpdate(pos, block.defaultBlockState());
                }
            }
        }
    }

    /**
     * The plan scenes: two lone Fission Reactor controllers, fronts to the camera: one planning the 4x4x4 example with its
     * floor and the front of its first layer built (and stone where a Fuel Channel should be), one planning a 6x5x5.
     */
    private static void fissionPlanSite(ServerLevel level, int x, int ground) {
        var controller = dev.groundburst.content.machine.GBMachines.FISSION_REACTOR.block().get();
        var small = new net.minecraft.core.BlockPos(x, ground, -4);
        var big = new net.minecraft.core.BlockPos(x, ground, 6);
        for (var at : List.of(small, big)) {
            level.setBlockAndUpdate(at, controller.defaultBlockState()
                    .setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST));
        }
        if (level.getBlockEntity(big) instanceof dev.groundburst.content.machine.FissionReactorBlockEntity r) {
            for (int b : new int[] {1, 1, 3, 5}) {                                 // wider twice, taller, deeper
                r.handleButton(dev.groundburst.content.machine.FissionReactorBlockEntity.BUTTON_PLAN + b);
            }
        }
        if (level.getBlockEntity(small) instanceof dev.groundburst.content.machine.FissionReactorBlockEntity r) {
            var plan = r.plan();
            for (int l = plan.layer0(); l <= plan.layer0() + 1; l++) {
                for (int row = 0; row < plan.depth(); row++) {
                    for (int c = plan.col0(); c <= plan.col1(); c++) {
                        var pos = plan.pos(c, l, row);
                        if (pos.equals(small) || l > plan.layer0() && row > 1) {
                            continue;
                        }
                        boolean core = plan.kind(c, l, row) == dev.groundburst.content.machine.FissionCore.Kind.CORE;
                        level.setBlockAndUpdate(pos, core && c == plan.col0() + 1 ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                                : plan.suggested(c, l, row));
                    }
                }
            }
        }
        fissionPlans = List.of(small, big);
    }

    /** A lone controller planning a 3x3x3 reactor, everything built but its front window and the Fuel Channel behind it. */
    private static void fissionGhostSite(ServerLevel level, int x, int ground) {
        var at = new net.minecraft.core.BlockPos(x, ground + 2, 0);                // its window at the camera's eye
        level.setBlockAndUpdate(at, dev.groundburst.content.machine.GBMachines.FISSION_REACTOR.block().get().defaultBlockState()
                .setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST));
        if (level.getBlockEntity(at) instanceof dev.groundburst.content.machine.FissionReactorBlockEntity r) {
            for (int b : new int[] {0, 2, 4}) {                                    // narrower, lower, shallower: 3x3x3
                r.handleButton(dev.groundburst.content.machine.FissionReactorBlockEntity.BUTTON_PLAN + b);
            }
            var plan = r.plan();
            for (int l = plan.layer0(); l <= plan.layer1(); l++) {
                for (int row = 0; row < plan.depth(); row++) {
                    for (int c = plan.col0(); c <= plan.col1(); c++) {
                        var pos = plan.pos(c, l, row);
                        boolean front = c == 0 && l == plan.layer0() + 1 && row == 0;
                        boolean core = plan.kind(c, l, row) == dev.groundburst.content.machine.FissionCore.Kind.CORE;
                        if (!pos.equals(at) && !front && !core) {
                            level.setBlockAndUpdate(pos, plan.suggested(c, l, row));
                        }
                    }
                }
            }
        }
        fissionPlans = List.of(at);
    }

    /** How high the silo scenes' plateau stands over the flat world: the silo's 14 layers go down into it. */
    private static final int SILO_PLATEAU = 14;

    /**
     * The silo scenes: a grassy plateau, the Missile Silo dug into it with its front (the controller) to the camera and
     * a Launch Computer beside it; a Strategic tactical-nuke missile in the shaft, aimed 400 blocks off and armed.
     */
    /**
     * A row of missiles carrying their warheads' own models, across the view: item displays of their flight models twice
     * the item's size (four blocks tall), their tails on the ground, their warheads' fronts to the camera. Group 0 and 1:
     * the usual missile's warheads; 2: the Strategic missile's and the intercontinental missile's.
     */
    private static void missileLineup(ServerLevel level, int x, int ground, int group) {
        List<String> looks = switch (group) {
            case 0 -> List.of("he_warhead", "heavy_warhead", "thermobaric_warhead", "cluster_warhead", "bunker_buster_warhead", "napalm_warhead",
                    "massive_warhead");
            case 1 -> List.of("gas_warhead", "smoke_warhead", "cryo_warhead", "emp_warhead", "chemical_warhead", "toxic_waste_warhead", "dirty_warhead");
            default -> List.of("tactical_nuclear_warhead", "fission_warhead", "boosted_fission_warhead", "neutron_warhead",
                    "thermonuclear_warhead", "cobalt_warhead", "tsar_warhead");
        };
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        for (int i = 0; i < looks.size(); i++) {
            String look = looks.get(i);
            // The item model is drawn about its middle; every missile's tail is at its y -15.5 (tools/assets/missiles.py).
            double y = ground + (8 + 15.5) / 16.0 * 2;
            double z = (i - (looks.size() - 1) / 2.0) * 1.8 + 0.5;
            level.getServer().getCommands().performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
                    "summon minecraft:item_display %.2f %.3f %.2f {item:{id:\"groundburst:missile\",count:1,components:{\"minecraft:custom_model_data\":"
                            + "{strings:[\"%s\"]}}},item_display:\"none\",transformation:{left_rotation:[0f,0.7071f,0f,0.7071f],"
                            + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]}}", x + 0.5, y, z, look));
        }
    }

    private static void siloSite(ServerLevel level, int x, int ground) {
        int top = ground + SILO_PLATEAU - 1;                                   // the plateau's grass: the silo's top layer
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, "fill " + (x - 40) + " " + ground + " -24 " + (x + 8) + " " + (top - 1)
                + " 24 minecraft:dirt");
        level.getServer().getCommands().performPrefixedCommand(source, "fill " + (x - 40) + " " + top + " -24 " + (x + 8) + " " + top
                + " 24 minecraft:grass_block");
        launchTarget = new net.minecraft.core.BlockPos(x + 400, 64, 140);
        launchSilo = new net.minecraft.core.BlockPos(x - 2, top, 0);
        launchComputer = new net.minecraft.core.BlockPos(x - 6, top + 1, 5);
        formed(level, launchSilo, dev.groundburst.content.launch.GBLaunch.MISSILE_SILO_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.MISSILE_SILO,
                net.minecraft.core.Direction.WEST);
        formed(level, launchComputer, dev.groundburst.content.launch.GBLaunch.COMPUTER_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.COMPUTER);
        if (level.getBlockEntity(launchSilo) instanceof dev.groundburst.content.launch.MissileSiloBlockEntity silo) {
            silo.revalidate();
            silo.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                    dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.XL, dev.groundburst.content.assembly.MissileParts.Motor.LIQUID, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false)));
            silo.energy().generate(dev.groundburst.content.launch.MissileSiloBlockEntity.SILO_ENERGY);
            silo.setTarget(launchTarget.getX(), launchTarget.getZ());
            silo.arm(true);
        }
        if (level.getBlockEntity(launchComputer) instanceof dev.groundburst.content.launch.LaunchComputerBlockEntity computer) {
            computer.revalidate();
            computer.energy().generate(dev.groundburst.content.launch.LaunchComputerBlockEntity.ENERGY_CAPACITY);
        }
    }

    /**
     * Slice 6e: a pond with a pig by it and a cryo grenade landing in it (left), an EMP grenade by a powered launch
     * computer (right), and a chemical warhead going off behind them.
     */
    private static void effects(ServerLevel level, int x, int ground) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -6; dz <= -3; dz++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground - 1, dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            }
        }
        var pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (pig != null) {
            pig.setPos(x + 2.5, ground, -2.5);
            pig.setNoAi(true);
            level.addFreshEntity(pig);
        }
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.CRYO_GRENADE.get(), new Vec3(x, ground + 0.2, -3.5), Vec3.ZERO, 4);
        var computer = new net.minecraft.core.BlockPos(x + 1, ground, 5);
        formed(level, computer, dev.groundburst.content.launch.GBLaunch.COMPUTER_CONTROLLER.block().get(), dev.groundburst.content.launch.GBLaunch.COMPUTER);
        if (level.getBlockEntity(computer) instanceof dev.groundburst.content.launch.LaunchComputerBlockEntity c) {
            c.revalidate();
            c.energy().generate(dev.groundburst.content.launch.LaunchComputerBlockEntity.ENERGY_CAPACITY);
        }
        throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.EMP_GRENADE.get(), new Vec3(x - 1, ground + 0.2, 3.5), Vec3.ZERO, 26);
        var chem = new net.minecraft.core.BlockPos(x + 10, ground, 0);
        level.setBlockAndUpdate(chem, dev.groundburst.content.ordnance.GBOrdnance.CHEMICAL_WARHEAD.block().get().defaultBlockState());
        var be = dev.groundburst.content.ordnance.charge.ChargeBlock.at(level, chem);
        if (be != null) {
            be.arm(level, null);
            be.trigger(level, null);
        }
    }

    private static void throwOne(ServerLevel level, ServerPlayer player, net.minecraft.world.item.Item item, Vec3 at, Vec3 velocity, int fuse) {
        var g = new dev.groundburst.content.ordnance.ThrownExplosive(level, player, new net.minecraft.world.item.ItemStack(item));
        g.setPos(at);
        g.setDeltaMovement(velocity);
        if (fuse >= 0) {
            g.setFuseForTest(fuse);
        }
        level.addFreshEntity(g);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;              // another window taking the focus must not pause the shots
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);    // no "Move with W, A, S and D" toast
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                var settings = new net.minecraft.world.level.LevelSettings("Fx shots", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = dev.groundburst.client.fx.DevWorlds.fresh(mc, "fx_shots");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings,
                        net.minecraft.client.gui.screens.worldselection.SelectWorldScreen.TEST_OPTIONS,
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createTestWorldDimensions, mc.gui.screen());
            } catch (java.io.IOException e) {
                GroundBurst.LOGGER.error("Fx shots: could not make a world", e);
            }
            return;
        }
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        clock++;
        if (scene == null) {
            scene = SCENES.poll();
            if (scene == null) {
                GroundBurst.LOGGER.info("Fx shots done");
                mc.stop();
                return;
            }
            step = -1;
            clock = 0;
            site++;
            mc.gui.setScreen(null);                        // a screen a scene opened stays shut for the next
            mc.options.renderDistance().set(24);
            if (scene.name().equals("designator_far")) {
                mc.options.broadcastOptions();             // the server sends a player only as far as it said it sees when it joined
            }
            Scene s = scene;
            int x = site * 1200;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + s.time());
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule advance_time false");
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SPECTATOR);
                level.getChunk(x >> 4, 0);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0);
                double lift = s.name().endsWith("_aerial") ? s.distance() * 0.5 : 1.6;    // an aerial scene looks down from above
                if (s.profile().startsWith("factory_")) {
                    lift = 7;                                  // over the factories' walls, into their yards
                }
                if (s.profile().equals("antimatter_warhead") || s.profile().equals("singularity_warhead")) {
                    antimatterMesa(level, x, ground);          // built now, so the client has it long before the burst
                    if (s.name().endsWith("_aerial")) {
                        lift += ANTIMATTER_MESA;               // over the mesa it lands on
                    } else if (s.profile().equals("singularity_warhead")) {
                        lift = ANTIMATTER_MESA + 6;            // level with the mesa's top, to see the hole on it
                    }
                }
                if (s.profile().equals("msilo")) {             // on the silo's plateau, looking down at it (steeply, into its shaft)
                    lift = SILO_PLATEAU + (s.name().endsWith("_ui") ? 1.6 : s.distance() * (s.name().endsWith("_aerial") ? 1.6 : 0.42));
                }
                player.teleportTo(level, x - s.distance() + 0.5, ground + lift, 0.5, java.util.Set.of(), -90, s.pitch(), true);
            });
            return;
        }
        if (step == -1) {
            if (clock < 100) {
                return;                                    // let the chunks round the new site load
            }
            Scene s = scene;
            int x = site * 1200;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getChunk(x >> 4, 0);                 // a far scene's middle may have unloaded: its height would read the world's floor
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 0);
                Vec3 at = new Vec3(x + 0.5, ground + 0.5, 0.5);
                if (s.profile().equals("grenades")) {
                    grenades(level, x, ground);
                    return;
                }
                if (s.profile().equals("geiger")) {
                    geiger(level, x, ground);
                    return;
                }
                if (s.profile().equals("lead_ui")) {
                    leadUi(level);
                    return;
                }
                if (s.profile().equals("gear_icons")) {
                    gearIcons(level);
                    return;
                }
                if (s.profile().startsWith("site:")) {
                    site(level, x, ground, s.profile());
                    return;
                }
                if (s.profile().equals("wasteland")) {
                    wasteland(level, x, ground);
                    return;
                }
                if (s.profile().equals("aftermath")) {
                    aftermath(level, x, ground);
                    return;
                }
                if (s.profile().equals("scud_storm")) {
                    scudStormSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("scud_strike")) {
                    return;                                // the SCUDs come down in tick()
                }
                if (s.profile().equals("nuke_silo")) {
                    nukeSiloSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("cruise")) {
                    cruiseSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("vls")) {
                    vlsSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("cruise_flight") || s.profile().equals("cruise_dive")) {
                    flightShown = cruiseFlight(level, x, ground, s.profile().equals("cruise_dive"));
                    return;
                }
                if (s.profile().startsWith("factory_")) {
                    factorySite(level, x, ground, s.profile().endsWith("arms"));
                    return;
                }
                if (s.profile().equals("fission")) {
                    fissionSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("fission_plan")) {
                    fissionPlanSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("fission_ghost")) {
                    fissionGhostSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("chemical")) {
                    chemical(level, x, ground);
                    return;
                }
                if (s.profile().equals("charges")) {
                    charges(level, x, ground);
                    return;
                }
                if (s.profile().equals("mines")) {
                    mines(level, x, ground);
                    return;
                }
                if (s.profile().equals("frozen")) {
                    // Animals by the camera, frozen solid in a burst of cold.
                    for (var type : List.of(net.minecraft.world.entity.EntityTypes.PIG, net.minecraft.world.entity.EntityTypes.COW,
                            net.minecraft.world.entity.EntityTypes.SHEEP)) {
                        var mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        if (mob != null) {
                            int i = level.getRandom().nextInt(3);
                            mob.setPos(x + 0.5 + (type == net.minecraft.world.entity.EntityTypes.COW ? 0 : 1.5), ground,
                                    type == net.minecraft.world.entity.EntityTypes.PIG ? -2 : type == net.minecraft.world.entity.EntityTypes.SHEEP ? 2 : 0.5 + i * 0);
                            mob.setYRot(90);
                            level.addFreshEntity(mob);
                        }
                    }
                    dev.groundburst.content.effect.Frost.freeze(level, new Vec3(x + 1, ground, 0.5), 5, 400, null);
                    return;
                }
                if (s.profile().equals("gas_wall")) {
                    // A stone wall across the view, a doorway in it; gas let out behind it.
                    for (int dz = -8; dz <= 8; dz++) {
                        for (int dy = 0; dy < 4; dy++) {
                            boolean door = dz == 3 && dy < 2;
                            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + 2, ground + dy, dz),
                                    door ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                                            : net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                    ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
                    throwOne(level, player, dev.groundburst.content.ordnance.GBOrdnance.GAS_GRENADE.get(), new Vec3(x + 5, ground + 0.2, -1.5), Vec3.ZERO, 2);
                    return;
                }
                if (s.profile().equals("effects")) {
                    effects(level, x, ground);
                    return;
                }
                if (s.profile().equals("airburst")) {
                    // A missile with a cluster warhead already high over a target 30 blocks in front of the camera.
                    Vec3 target = new Vec3(x + 0.5, ground, 0.5);
                    var f = dev.groundburst.content.launch.Flights.launch(level, target.add(-120, 0, 0), target,
                            new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.CLUSTER_WARHEAD.item().get()), null);
                    f.skipForTest(f.ticksToImpact() - 22);
                    return;
                }
                if (s.profile().equals("radar")) {
                    radarSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("assembly")) {
                    assemblySite(level, x, ground);
                    return;
                }
                if (s.profile().equals("intercept") || s.profile().equals("intercept_near")) {
                    // The wide scene: the battery off to the right (south), a missile from 300 blocks to the left (north)
                    // crossing the view, so the meeting is up in the sky across the frame. The near one: the battery in front.
                    int bz = s.profile().equals("intercept") ? 40 : 0;
                    var block = dev.groundburst.content.defence.GBDefence.BATTERY.block().get();
                    var batteryAt = new net.minecraft.core.BlockPos(x, ground, bz);
                    var state = block.defaultBlockState().setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST);
                    level.setBlockAndUpdate(batteryAt, state);
                    block.setPlacedBy(level, batteryAt, state, null, net.minecraft.world.item.ItemStack.EMPTY);
                    if (level.getBlockEntity(batteryAt) instanceof dev.groundburst.content.defence.InterceptorBatteryBlockEntity b) {
                        b.energy().generate(dev.groundburst.content.defence.InterceptorBatteryBlockEntity.ENERGY_CAPACITY);
                        b.fillForTest();
                    }
                    dev.groundburst.content.defence.Interception.killOverride = 1;
                    Vec3 target = new Vec3(x + 6.5, ground, bz + 4.5);
                    var spec = new dev.groundburst.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(
                            dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.M,
                            dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false);
                    flightShown = dev.groundburst.content.launch.Flights.launch(level, target.add(0, 0, -300), target, spec, null);
                    return;
                }
                if (s.profile().equals("intercept_complex") || s.profile().equals("intercept_complex_near")) {
                    // As the battery's: the complex facing the camera, off to the right in the wide scene; a missile from
                    // 300 blocks to the left, bound for just behind it.
                    boolean wide = s.profile().equals("intercept_complex");
                    var complexAt = new net.minecraft.core.BlockPos(x, ground, wide ? 100 : 0);
                    formed(level, complexAt, dev.groundburst.content.defence.GBDefence.COMPLEX_CONTROLLER.block().get(),
                            dev.groundburst.content.defence.GBDefence.COMPLEX, net.minecraft.core.Direction.WEST);
                    if (level.getBlockEntity(complexAt) instanceof dev.groundburst.content.defence.InterceptorComplexBlockEntity c) {
                        c.revalidate();
                        c.energy().generate(dev.groundburst.content.defence.InterceptorComplexBlockEntity.ENERGY_CAPACITY);
                        c.fillForTest();
                        c.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.defence.GBDefence.INTERCEPTOR_MISSILE.get(), 16));
                    }
                    pressAt = complexAt;
                    dev.groundburst.content.defence.Interception.killOverride = 1;
                    Vec3 target = new Vec3(x + 9.5, ground, complexAt.getZ() + 0.5);
                    var spec = new dev.groundburst.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(
                            dev.groundburst.content.ordnance.GBOrdnance.HE_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.L,
                            dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false);
                    flightShown = dev.groundburst.content.launch.Flights.launch(level, target.add(0, 0, -300), target, spec, null);
                    return;
                }
                if (s.profile().equals("alarms")) {
                    alarmWall(level, x, ground);
                    return;
                }
                if (s.profile().equals("siren")) {
                    var h = dev.groundburst.content.alarm.AlarmBlock.HORIZONTAL;
                    var siren = dev.groundburst.content.alarm.GBAlarms.SIREN.block().get().defaultBlockState();
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + 2, ground, -2), siren.setValue(h, net.minecraft.core.Direction.WEST));
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + 2, ground, 2), siren.setValue(h, net.minecraft.core.Direction.SOUTH));
                    return;
                }
                if (s.profile().equals("station")) {
                    stationSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("designator")) {
                    designatorSite(level, x, ground, s);
                    return;
                }
                if (s.profile().equals("see_through")) {
                    var lattice = dev.groundburst.content.construction.GBConstruction.LATTICE_FRAME.block().get().defaultBlockState();
                    var grating = dev.groundburst.content.construction.GBConstruction.STEEL_GRATING.block().get().defaultBlockState();
                    var slab = dev.groundburst.content.construction.GBConstruction.STEEL_GRATING_SLAB.block().get().defaultBlockState();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = 0; dy < 3; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground + dy, dz - 3), lattice);
                                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground + dy, dz + 2), grating);
                            }
                        }
                        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground, 4), slab);
                    }
                    return;
                }
                if (s.profile().equals("upgrade")) {
                    var crusher = dev.groundburst.content.machine.GBMachines.ORE_CRUSHER.block().get();
                    pressAt = new net.minecraft.core.BlockPos(x, ground, 0);
                    var state = crusher.defaultBlockState().setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST);
                    level.setBlockAndUpdate(pressAt, state);
                    if (level.getBlockEntity(pressAt) instanceof dev.groundburst.content.machine.MachineBlockEntity be) {
                        be.energy().generate(40_000);
                        be.inventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON, 24));
                        be.upgrades().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.machine.GBUpgrades.chip(
                                dev.groundburst.content.machine.UpgradeChipItem.Kind.SPEED, dev.groundburst.content.machine.UpgradeChipItem.Tier.QUANTUM).get()));
                        be.upgrades().setItem(1, new net.minecraft.world.item.ItemStack(dev.groundburst.content.machine.GBUpgrades.chip(
                                dev.groundburst.content.machine.UpgradeChipItem.Kind.EFFICIENCY, dev.groundburst.content.machine.UpgradeChipItem.Tier.MILITARY).get()));
                        be.upgrades().setItem(2, new net.minecraft.world.item.ItemStack(dev.groundburst.content.machine.GBUpgrades.chip(
                                dev.groundburst.content.machine.UpgradeChipItem.Kind.BALANCED, dev.groundburst.content.machine.UpgradeChipItem.Tier.ADVANCED).get()));
                    }
                    return;
                }
                if (s.profile().equals("tall")) {
                    var press = dev.groundburst.content.machine.GBMachines.INDUSTRIAL_PRESS.block().get();
                    pressAt = new net.minecraft.core.BlockPos(x, ground, 0);
                    var state = press.defaultBlockState().setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.WEST);
                    level.setBlockAndUpdate(pressAt, state);
                    press.setPlacedBy(level, pressAt, state, null, net.minecraft.world.item.ItemStack.EMPTY);
                    return;
                }
                if (s.profile().equals("warheads")) {
                    bombs(level, x, ground, false);
                    return;
                }
                if (s.profile().equals("complex")) {
                    complexSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("msilo")) {
                    siloSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("complex_icbm")) {
                    complexSite(level, x, ground, true, net.minecraft.world.item.ItemStack.EMPTY);
                    return;
                }
                if (s.profile().equals("complex_tsar")) {
                    complexSite(level, x, ground, true, new net.minecraft.world.item.ItemStack(dev.groundburst.content.strategic.GBStrategic.TERTIARY_STAGE.get()));
                    return;
                }
                if (s.profile().equals("hall")) {
                    hallSite(level, x, ground);
                    return;
                }
                if (s.profile().equals("assemblers")) {
                    // All four in a row across the view, north (left) to south: Light, Medium, Heavy, Strategic.
                    int z = -26;
                    for (var kind : dev.groundburst.content.assembler.AssemblerClass.values()) {
                        assemblerSite(level, x, ground, kind, z);
                        z += dev.groundburst.content.assembler.GBAssemblers.definition(kind).size()[2] + 3;
                    }
                    return;
                }
                if (s.profile().startsWith("asm_")) {
                    assemblerLaser = s.name().contains("laser");
                    var kind = dev.groundburst.content.assembler.AssemblerClass.valueOf(s.profile().substring(4).toUpperCase(java.util.Locale.ROOT));
                    assemblerSite(level, x, ground, kind, -(dev.groundburst.content.assembler.GBAssemblers.definition(kind).size()[2] - 1) / 2);
                    return;
                }
                if (s.profile().startsWith("missiles")) {
                    missileLineup(level, x, ground, s.profile().charAt(s.profile().length() - 1) - '0');
                    return;
                }
                if (s.profile().equals("flight_icbm")) {
                    // To 2,500 blocks off to the camera's right; called off before it gets there (see flightShown).
                    var spec = dev.groundburst.content.assembly.MissileItem.spec(dev.groundburst.content.strategic.StrategicAssembly.assemble(
                            net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY));
                    Vec3 pad = new Vec3(x + 0.5, ground, 0.5);
                    flightShown = dev.groundburst.content.launch.Flights.launch(level, pad, pad.add(0, 0, 2500), spec, null);
                    return;
                }
                if (s.profile().equals("flight_reentry")) {
                    // A missile with no warhead from 2,400 blocks off to the camera's left (north), most of the way down
                    // already, so its last dive crosses the view.
                    Vec3 target = new Vec3(x + 0.5, ground, 0.5);
                    var f = dev.groundburst.content.launch.Flights.launch(level, target.add(0, 0, -2400), target,
                            net.minecraft.world.item.ItemStack.EMPTY, null);
                    f.skipForTest((int) (f.ticksToImpact() * 0.74));
                    flightShown = f;
                    return;
                }
                if (s.profile().startsWith("glow_machines")) {
                    glowMachines(level, x, ground, s.profile().charAt(s.profile().length() - 1) - '0');
                    return;
                }
                if (s.profile().startsWith("glow_reactors")) {
                    glowReactors(level, x, ground, s.profile().charAt(s.profile().length() - 1) - '0');
                    return;
                }
                if (s.profile().equals("hall_tsar")) {
                    hallSite(level, x, ground, new net.minecraft.world.item.ItemStack(dev.groundburst.content.strategic.GBStrategic.TERTIARY_STAGE.get()));
                    return;
                }
                if (s.profile().equals("special_antimatter") || s.profile().equals("special_singularity") || s.profile().equals("special_storm")) {
                    var cores = dev.groundburst.content.strategic.SpecialWeapons.cores();
                    int core = s.profile().equals("special_antimatter") ? 0 : s.profile().equals("special_singularity") ? 1 : 2;
                    specialSite(level, x, ground, cores.get(core));
                    return;
                }
                if (s.profile().equals("storm_warhead")) {
                    // A herd scattered about in the open, for the strikes to find and chain through.
                    var random = net.minecraft.util.RandomSource.create(17);
                    for (int i = 0; i < 40; i++) {
                        double a = random.nextDouble() * 2 * Math.PI, r = 15 + random.nextDouble() * 120;
                        var pos = new net.minecraft.core.BlockPos((int) (x + r * Math.cos(a)), ground, (int) (r * Math.sin(a)));
                        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                        var animal = (i % 3 == 0 ? net.minecraft.world.entity.EntityTypes.COW : net.minecraft.world.entity.EntityTypes.PIG)
                                .create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        if (animal != null) {
                            animal.snapTo(pos.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                    pos.getX(), pos.getZ()), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
                            level.addFreshEntity(animal);
                        }
                    }
                    dev.groundburst.content.ordnance.charge.Charges.impact(level, dev.groundburst.content.ordnance.charge.ChargeKind.STORM_WARHEAD,
                            net.minecraft.core.BlockPos.containing(at), null);
                    if (s.name().equals("storm_pulses")) {
                        stormCentre = at;
                        stormSpot = new net.minecraft.core.BlockPos(x - 26, ground, 0);
                    }
                    return;
                }
                if (s.profile().equals("cobalt_plume")) {
                    dev.groundburst.content.ordnance.charge.Charges.impact(level, dev.groundburst.content.ordnance.charge.ChargeKind.COBALT_WARHEAD,
                            net.minecraft.core.BlockPos.containing(at), null);
                    return;
                }
                if (s.profile().startsWith("launch")) {
                    launchSite(level, x, ground, s.profile().equals("launch_dive"), s.name().equals("launch_ui") || s.name().equals("launch_map_ui"));
                    return;
                }
                if (s.profile().equals("napalm")) {
                    bombs(level, x, ground, true);
                    return;
                }
                if (s.profile().startsWith("suits")) {
                    suits(level, x, ground, s.profile().endsWith("back"));    // armour stands wearing the suits, no blast
                    return;
                }
                if (s.profile().equals("water_smoke")) {
                    waterSmoke(level, x, ground);
                    return;
                }
                if (s.profile().equals("fallout")) {
                    dev.groundburst.content.radiation.RadiationSources.of(level).add(dev.groundburst.content.radiation.RadiationSource.lasting(
                            new Vec3(x + 0.5, ground, 0.5), 180, 60_000, 48_000, level.getGameTime(), 1));
                    return;
                }
                if (s.profile().equals("antimatter_warhead") || s.profile().equals("singularity_warhead")) {   // on the mesa's top
                    dev.groundburst.content.ordnance.charge.Charges.impact(level, s.profile().equals("antimatter_warhead")
                            ? dev.groundburst.content.ordnance.charge.ChargeKind.ANTIMATTER_WARHEAD
                            : dev.groundburst.content.ordnance.charge.ChargeKind.SINGULARITY_WARHEAD, net.minecraft.core.BlockPos.containing(at), null);
                    return;
                }
                // These two through the warhead's own path: the neutrons wither the plants, the waste goes up as dust.
                if (s.profile().equals("neutron_warhead")) {
                    dev.groundburst.content.ordnance.charge.Charges.impact(level, dev.groundburst.content.ordnance.charge.ChargeKind.NEUTRON_WARHEAD,
                            net.minecraft.core.BlockPos.containing(at), null);
                    return;
                }
                if (s.profile().equals("dirty_warhead")) {
                    dev.groundburst.content.ordnance.charge.Charges.burst(level, dev.groundburst.content.ordnance.charge.ChargeKind.DIRTY_WARHEAD, at, null);
                    return;
                }
                var blast = Blasts.detonate(level, at, Blasts.profile(s.profile()), null);
                if (s.profile().equals("reactor_meltdown")) {
                    dev.groundburst.content.nuclear.Meltdown.afterBlast(level, at, blast);   // the corium a real meltdown leaves
                }
            });
            step = 0;
            clock = 0;
            return;
        }
        if (scene.name().equals("aftermath") && step >= 0 && clock == 60) {
            // Slice 12b: a plume over the camera in a light wind, ash settling and falling, its cloud overhead.
            int sx = site * 1200;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                dev.groundburst.content.fallout.Wind.of(level).hold((float) Math.PI / 2, 0.6f);
                Vec3 over = new Vec3(sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, 0), 0);
                dev.groundburst.content.fallout.Plumes.start(level, over, 30, 20_000, 2_000, 300, 120);
                dev.groundburst.content.radiation.RadiationSources.of(level).add(new dev.groundburst.content.radiation.RadiationSource(over, 60, 20_000,
                        2_000, level.getGameTime()));
            });
        }
        if (scene.name().equals("gear_icons") && step >= 0 && clock == 14) {
            mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            jeiFilter.accept("hazmat");
        }
        if (scene.name().equals("geiger") && step >= 0 && clock == 50) {
            // An Anti-Rad Injector between the shots: the second shows the badge's film cleared and the dose near nothing.
            server.execute(() -> dev.groundburst.content.radiation.Radiation.addDose(server.getPlayerList().getPlayers().getFirst(),
                    -dev.groundburst.content.radiation.AntiRadInjectorItem.DOSE));
        }
        if (scene.name().equals("geiger") && step >= 0 && clock == 60) {
            // The second shot with the meter in the top left: the counter's model in hand shows.
            dev.groundburst.config.GBClientConfig.GEIGER_CORNER.set(dev.groundburst.config.GBClientConfig.HudCorner.TOP_LEFT);
        }
        if (scene.name().equals("charge_ui") && step >= 0 && step < 3 && clock == scene.moments().get(step) - 5 && UI_CHARGES[step] != null) {
            dev.groundburst.content.ordnance.charge.ChargeBlock.screenHook.accept(UI_CHARGES[step]);   // safe, armed, counting
        }
        if ((scene.profile().startsWith("launch") || scene.profile().equals("complex") || scene.profile().equals("msilo")) && step >= 0
                && clock == 25 && launchSilo != null) {
            var silo = launchSilo;
            var computerAt = launchComputer;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(computerAt) instanceof dev.groundburst.content.launch.LaunchComputerBlockEntity be) {
                    be.launchArmed(server.overworld(), null);              // LAUNCH: every armed silo
                }
            });
        }
        if (scene.profile().equals("cruise") && !scene.name().endsWith("_ui") && step >= 0 && clock == 25 && cruiseAt != null) {
            var at = cruiseAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.launch.CruiseLauncherBlockEntity be) {
                    be.launchArmed(server.overworld(), null);
                }
            });
        }
        if (scene.name().equals("vls_ui") && step >= 0 && clock == 12 && vlsAt != null) {
            var at = vlsAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.launch.VerticalLaunchBatteryBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, at);
                }
            });
        }
        if (scene.profile().equals("vls") && !scene.name().equals("vls_ui") && step >= 0 && clock == 25 && vlsAt != null) {
            var at = vlsAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.launch.VerticalLaunchBatteryBlockEntity be) {
                    be.launchArmed(server.overworld(), null);
                }
            });
        }
        // Its missiles fly off far beyond the pictures: called off once they are out of them.
        if ((scene.profile().equals("cruise") || scene.profile().equals("vls")) && step >= 0 && clock == scene.moments().getLast() + 5) {
            server.execute(() -> {
                var flights = dev.groundburst.content.launch.Flights.of(server.overworld());
                flights.all().forEach(f -> flights.cancelForTest(server.overworld(), f));
            });
        }
        if (scene.name().equals("cruise_ui") && step >= 0 && clock == 12 && cruiseAt != null) {
            var at = cruiseAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.launch.CruiseLauncherBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, at);
                }
            });
        }
        if ((scene.profile().equals("scud_storm") || scene.profile().equals("nuke_silo")) && step >= 0 && clock == 25 && scudAt != null) {
            var at = scudAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.superweapon.SuperweaponBlockEntity be) {
                    be.launchArmed(server.overworld(), null);
                }
            });
        }
        if (scene.profile().equals("scud_strike") && step >= 0 && (clock == 25 || clock == 37 || clock == 49)) {
            int x = site * 1200, i = (clock - 25) / 12;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                int[][] spots = {{4, -7}, {11, 6}, {-3, 9}};
                int bx = x + spots[i][0], bz = spots[i][1];
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                dev.groundburst.content.ordnance.charge.Charges.impact(level, dev.groundburst.content.ordnance.charge.ChargeKind.SCUD_STORM_WARHEAD,
                        new net.minecraft.core.BlockPos(bx, y, bz), null);
            });
        }
        if (scene.name().equals("radar_ui") && step >= 0 && clock == 10 && radarAt != null) {
            dev.groundburst.content.launch.RadarArrayBlock.screenHook.accept(radarAt);
        }
        if ((scene.name().equals("launch_ui") || scene.name().equals("launch_map_ui")) && step >= 0 && clock == 10 && launchComputer != null) {
            dev.groundburst.content.launch.LaunchComputerBlock.screenHook.accept(launchComputer);
        }
        // The map's mouse, as the game sends it: a right-button drag 40 px left and up, then a left click right of centre.
        if (scene.name().equals("launch_ui") && step >= 0 && (clock == 14 || clock == 18) && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double cx = screen.width / 2.0 - 168 + 8 + 92, cy = screen.height / 2.0 - 107 + 14 + 92;
            var right = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, 0);
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            if (clock == 14) {
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(cx, cy, right), false);
                screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(cx - 40, cy - 20, right), -40, -20);
                screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(cx - 40, cy - 20, right));
            } else {
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(cx + 30, cy, left), false);
                screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(cx + 30, cy, left));
            }
        }
        // The computer's map zoomed all the way out (32 blocks a pixel: the whole 3000-block survey).
        if (scene.name().equals("launch_map_ui") && step >= 0 && clock == 14 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double cx = screen.width / 2.0 - 168 + 8 + 92, cy = screen.height / 2.0 - 107 + 14 + 92;
            for (int i = 0; i < 6; i++) {
                screen.mouseScrolled(cx, cy, 0, -1);
            }
        }
        // The biggest warhead on the bench (the preview must zoom out to fit it).
        // The neutron and dirty warheads have the longest stats lists (fallout rows, the neutron rows, a note).
        if ((scene.name().equals("assembly_nuke_ui") || scene.name().equals("assembly_neutron_ui") || scene.name().equals("assembly_dirty_ui"))
                && step >= 0 && clock == 8 && benchAt != null) {
            var bench = benchAt;
            boolean neutron = scene.name().equals("assembly_neutron_ui"), dirty = scene.name().equals("assembly_dirty_ui");
            server.execute(() -> {
                if (server.overworld().getBlockEntity(bench) instanceof dev.groundburst.content.assembly.AssemblyBenchBlockEntity be) {
                    be.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dirty ? dev.groundburst.content.assembly.GBAssembly.DIRTY_PAYLOAD.get()
                            : neutron ? dev.groundburst.content.assembly.GBAssembly.NEUTRON_CORE.get() : dev.groundburst.content.assembly.GBAssembly.FISSION_CORE.get()));
                    be.inventory().setItem(1, new net.minecraft.world.item.ItemStack(dirty ? dev.groundburst.content.assembly.GBAssembly.LIGHT_CASING.get()
                            : dev.groundburst.content.assembly.GBAssembly.STRATEGIC_CASING.get()));
                }
            });
        }
        if (scene.name().equals("assembly_mow_ui") && step >= 0 && clock == 8 && benchAt != null) {
            var bench = benchAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(bench) instanceof dev.groundburst.content.assembly.AssemblyBenchBlockEntity be) {
                    be.inventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.MASSIVE_ORDNANCE_CHARGE.get()));
                    be.inventory().setItem(1, new net.minecraft.world.item.ItemStack(dev.groundburst.content.assembly.GBAssembly.HEAVY_CASING.get()));
                }
            });
        }
        if ((scene.name().equals("assembly_ui") || scene.name().equals("assembly_mow_ui")
                || scene.name().equals("assembly_nuke_ui") || scene.name().equals("assembly_neutron_ui") || scene.name().equals("assembly_dirty_ui"))
                && step >= 0 && clock == 12 && benchAt != null) {
            var bench = benchAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(bench) instanceof dev.groundburst.content.assembly.AssemblyBenchBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, bench);
                }
            });
        }
        if ((scene.name().equals("asm_ui") || scene.name().equals("asm_laser_ui")) && step >= 0 && clock == 12 && assemblerAt != null) {
            var at = assemblerAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.assembler.MissileAssemblerBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, at);
                }
            });
        }
        if (scene.name().equals("asm_jei") && step >= 0 && clock == 10) {
            server.execute(() -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE));
        }
        if (scene.name().equals("asm_jei") && step >= 0 && clock == 14) {
            mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            jeiFilter.accept("Missile");
        }
        if (scene.name().equals("asm_jei") && step >= 0 && clock == 35) {
            jeiRecipesFor.accept(dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.CRYO_WARHEAD.item().get()),
                    dev.groundburst.content.assembly.SizeClass.M, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II,
                    dev.groundburst.content.assembly.MissileParts.Guidance.UNGUIDED, false)));
        }
        if ((scene.name().equals("hall_ui") || scene.name().equals("hall_tsar_ui") || scene.name().equals("special_ui"))
                && step >= 0 && clock == 12 && hallAt != null) {
            var hall = hallAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(hall) instanceof dev.groundburst.content.machine.MachineBlockEntity be
                        && (be instanceof dev.groundburst.content.strategic.StrategicAssemblyBlockEntity
                        || be instanceof dev.groundburst.content.strategic.SpecialWeaponsBlockEntity)) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, hall);
                }
            });
        }
        // A tall machine's screen with its side tab open (each half's faces).
        if (scene.name().equals("upgrade_ui") && step >= 0 && clock == 12 && pressAt != null) {
            var crusher = pressAt;
            dev.groundburst.content.machine.MachineMenu.upgradeTabOpen = true;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(crusher) instanceof dev.groundburst.content.machine.MachineBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);
                    player.openMenu(be, crusher);
                }
            });
        }
        if (scene.name().equals("storm_pulses") && step >= 0 && clock == 476 && stormCentre != null) {
            var centre = stormCentre;
            var spot = stormSpot;
            server.execute(() -> dev.groundburst.blast.Storms.strikeForTest(server.overworld(), centre, spot));
        }
        if (scene.profile().equals("alarms") && step >= 0 && clock == 10 && alarmPanelAt != null) {
            var at = alarmPanelAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.alarm.AlarmBlockEntity panel) {
                    panel.panel(dev.groundburst.content.alarm.AlarmBlockEntity.SOUND);
                }
            });
        }
        if (scene.name().equals("designator_call") && step >= 0) {
            if (clock == 20 || clock == 55 || clock == 76) {
                net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());   // left clicks: arm #1, arm #2, launch #2
            }
            if (clock >= 30 && clock <= 40) {                 // the laser walked onto pillbox B
                float k = (clock - 30) / 10f;
                mc.player.setYRot(-90 + 18.9f * k);
                mc.player.setYHeadRot(mc.player.getYRot());
            }
            if (clock == 45) {
                DesignatorScope.scroll(mc, -1);                // the wheel (down): the next launcher, #2
            }
            if (clock == 96 || clock == 106) {
                mc.options.keyShift.setDown(clock == 96);      // sneaking, the pick moved on to #1 (armed on A) ...
            }
            if (clock == 100) {
                net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());   // ... a left click disarms it
            }
        }
        if (scene.profile().equals("designator") && step >= 0 && clock == 4) {
            mc.options.setCameraType(scene.name().endsWith("_front") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                    : scene.name().endsWith("_back") ? net.minecraft.client.CameraType.THIRD_PERSON_BACK : net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.keyUse.setDown(!scene.name().contains("_carry"));   // use held down: it goes to the eye and lases while held
            if (scene.name().endsWith("_side") && sideCamera >= 0 && mc.level.getEntity(sideCamera) != null) {
                mc.setCameraEntity(mc.level.getEntity(sideCamera));
                LaserFx.serverBeamForSelf = true;
            }
        }
        if (scene.name().equals("station_ui") && step >= 0 && clock == 14 && stationAt != null) {
            dev.groundburst.content.launch.LaunchStationBlock.screenHook.accept(stationAt);
        }
        if (scene.name().equals("alarm_ui") && step >= 0 && clock == 14 && alarmReceiverAt != null) {
            dev.groundburst.content.alarm.AlarmBlock.screenHook.accept(alarmReceiverAt);
        }
        if (scene.name().equals("complex_ui") && step >= 0 && clock == 12 && pressAt != null) {
            var complex = pressAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(complex) instanceof dev.groundburst.content.machine.MachineBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);
                    player.openMenu(be, complex);
                }
            });
        }
        if (scene.name().equals("tall_ui") && step >= 0 && clock == 12 && pressAt != null) {
            var press = pressAt;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(press) instanceof dev.groundburst.content.machine.MachineBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);
                    player.openMenu(be, press);
                }
            });
        }
        if (scene.name().equals("tall_ui") && step >= 0 && clock == 20 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double tx = screen.width / 2.0 + 88 + 8, ty = screen.height / 2.0 - 83 + 14;
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left), false);
            screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left));
        }
        if (scene.name().equals("assembly_jei") && step >= 0 && clock == 12) {
            showAssemblyRecipes.run();
        }
        if (scene.name().equals("hall_jei") && step >= 0 && clock == 12) {
            showHallRecipes.run();
        }
        if (scene.name().equals("special_jei") && step >= 0 && clock == 12) {
            showSpecialRecipes.run();
        }
        if (scene.name().equals("icbm_jei") && step >= 0 && clock == 10) {
            server.execute(() -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE));
        }
        if (scene.name().equals("icbm_jei") && step >= 0 && clock == 14) {
            mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            jeiFilter.accept("Intercontinental");
        }
        if (scene.name().equals("icbm_jei") && step >= 0 && clock == 35) {
            jeiRecipesFor.accept(dev.groundburst.content.strategic.StrategicAssembly.assemble(
                    new net.minecraft.world.item.ItemStack(dev.groundburst.content.strategic.GBStrategic.TERTIARY_STAGE.get()),
                    net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY));      // an unguided one: any of its kind
        }
        // The glow scenes' machines shown running (on the client only: see glowAt).
        if (scene.name().startsWith("glow_machines") || scene.name().startsWith("glow_reactors")) {
            if (step >= 0 && clock == 30 && mc.level != null) {
                for (var pos : glowAt) {
                    var state = mc.level.getBlockState(pos);
                    if (state.hasProperty(dev.groundburst.content.machine.MachineBlock.ACTIVE)) {
                        mc.level.setBlock(pos, state.setValue(dev.groundburst.content.machine.MachineBlock.ACTIVE, true), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    }
                }
            }
        }
        // Then its side-configuration tab opened (the two-block layout), for the second shot.
        if (scene.name().equals("assembly_ui") && step == 1 && clock == 36 && mc.gui.screen() != null) {
            var screen = mc.gui.screen();
            double tx = screen.width / 2.0 + 144 + 8, ty = screen.height / 2.0 - 105 + 14;
            var left = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
            screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left), false);
            screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(tx, ty, left));
        }
        // The plans' ghost previews, pinned as a right-click on each controller pins them.
        if ((scene.name().equals("fission_plan") || scene.name().equals("fission_ghost")) && step >= 0 && clock == 12) {
            for (var at : fissionPlans) {
                dev.groundburst.content.multiblock.MultiblockClientHooks.togglePreview.accept(at);
            }
        }
        // The unformed controller's screen: the plan panel over its core map.
        if (scene.name().equals("fission_plan_ui") && step >= 0 && clock == 12 && !fissionPlans.isEmpty()) {
            var at = fissionPlans.getFirst();
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.machine.FissionReactorBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, at);
                }
            });
        }
        // The 6x6x6 Fission Reactor's screen: its core map, a layer of its checkerboard core, the rods part burned.
        if (scene.name().equals("fission_ui") && step >= 0 && clock == 12 && fissionBig != null) {
            var at = fissionBig;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(at) instanceof dev.groundburst.content.machine.FissionReactorBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, at);
                }
            });
        }
        // The Missile Silo's screen (its DOORS button, the doors' state on the CRT).
        if (scene.name().equals("msilo_ui") && step >= 0 && clock == 12 && launchSilo != null) {
            var silo = launchSilo;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(silo) instanceof dev.groundburst.content.launch.MissileSiloBlockEntity be) {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, silo);
                }
            });
        }
        // The silo's missile is a live tactical nuke: called off once it is out of the pictures.
        if (scene.profile().equals("msilo") && step >= 0 && clock == 600) {
            server.execute(() -> {
                var flights = dev.groundburst.content.launch.Flights.of(server.overworld());
                flights.all().forEach(f -> flights.cancelForTest(server.overworld(), f));
            });
        }
        // The silo's own screen, a Massive Ordnance Warhead loaded (the longest name).
        if (scene.name().equals("silo_ui") && step >= 0 && clock == 12 && launchSilo != null) {
            var silo = launchSilo;
            server.execute(() -> {
                if (server.overworld().getBlockEntity(silo) instanceof dev.groundburst.content.launch.LaunchSiloBlockEntity be) {
                    be.inventory().setItem(dev.groundburst.content.launch.LaunchSiloBlockEntity.SLOT_MISSILE,
                            dev.groundburst.content.assembly.MissileAssembly.missile(new dev.groundburst.content.assembly.MissileSpec(new net.minecraft.world.item.ItemStack(dev.groundburst.content.ordnance.GBOrdnance.MASSIVE_WARHEAD.item().get()), dev.groundburst.content.assembly.SizeClass.L, dev.groundburst.content.assembly.MissileParts.Motor.SRM_II, dev.groundburst.content.assembly.MissileParts.Guidance.GPS, false)));
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE);                         // spectators open no menus
                    player.openMenu(be, silo);
                }
            });
        }
        if (scene.profile().equals("singularity_warhead") && step >= 0 && clock == dev.groundburst.blast.Singularities.LIFE
                && SingularityFx.growing()) {
            clock--;   // its server, busy tearing, runs behind this clock: the moments after its life count from its collapse
        }
        if (step < scene.moments().size() && clock == scene.moments().get(step)) {
            String name = "fx_" + scene.name() + "_" + clock + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("Fx shot: {}", msg.getString()));
            step++;
        }
        if (step >= scene.moments().size() && clock > scene.moments().getLast() + 10) {
            if (scene.profile().equals("designator")) {
                mc.setCameraEntity(mc.player);
                LaserFx.serverBeamForSelf = false;
                sideCamera = -1;
                mc.options.keyUse.setDown(false);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.player.stopUsingItem();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    player.stopUsingItem();
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                });
            }
            if (scene.name().equals("geiger")) {
                dev.groundburst.config.GBClientConfig.GEIGER_CORNER.set(dev.groundburst.config.GBClientConfig.HudCorner.BOTTOM_RIGHT);
            }
            scene = null;
            var f = flightShown;
            if (f != null) {
                flightShown = null;
                server.execute(() -> dev.groundburst.content.launch.Flights.of(server.overworld()).cancelForTest(server.overworld(), f));
            }
        }
    }
}
