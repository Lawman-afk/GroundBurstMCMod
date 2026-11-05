package dev.groundburst.port;

import java.io.File;
import java.util.List;

import dev.groundburst.GroundBurst;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * This branch's check that the mod draws on 1.21.1 ({@code gradlew runPortCheck}): makes a flat creative world, sets the
 * mod's blocks out in a row (machines, launchers, guns, charges, warheads; a Warning Beacon lit by redstone; buckets in
 * the hotbar), photographs them, opens the Field Manual, sets a blast off and photographs that, brings a Particle Uplink
 * beam down beside the row and photographs it and its trench, lays sea mines in a walled sea and photographs them from
 * under the water and from above, sets a depth charge off under them and photographs its column, then quits. The pictures go to
 * run/screenshots/port_*.png; anything that goes wrong is in the log.
 */
public final class PortCheck {
    private static final boolean ON = Boolean.getBoolean("groundburst.portCheck");
    private static final String WORLD = "port_check";
    private static final List<String> BLOCKS = List.of("assembly_bench", "combustion_generator", "arc_furnace", "capacitor_bank",
            "air_raid_siren", "alert_display", "c4", "he_warhead", "howitzer", "barbed_wire", "corrugated_steel", "dead_grass_block",
            "cryo_crystal", "chain_link_fence", "emergency_light");
    private static boolean started;
    private static int ticks = -1;

    private PortCheck() {}

    public static void register() {
        if (!ON) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            // Once the game has loaded (whatever screen it shows first: the title, or a warning), make the world.
            if (!started && mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                started = true;
                GroundBurst.LOGGER.info("Port check: making the world (from {})", mc.screen.getClass().getSimpleName());
                createWorld();
            }
            tick(mc);
        });
    }

    private static void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        try (var access = mc.getLevelSource().createAccess(WORLD)) {
            access.deleteLevel();
        } catch (Exception ignored) {
            // none yet
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261103L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    private static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "port_" + name + ".png", mc.getMainRenderTarget(), message -> GroundBurst.LOGGER.info("Port check: {}", message.getString()));
    }

    /** Clears the chat and the toasts a moment before a picture. */
    private static void tidy(Minecraft mc) {
        mc.gui.getChat().clearMessages(false);
        mc.getToasts().clear();
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        // Never paused (the window may be behind another): a paused game stops its server, and every picture would be the menu.
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        ticks++;
        switch (ticks) {
            case 40 -> {
                command(mc, "gamerule doDaylightCycle false");
                command(mc, "gamerule doWeatherCycle false");
                command(mc, "time set 6000");
                command(mc, "weather clear");
                command(mc, "tp @s 0 -59 -9 0 18");
            }
            case 50 -> {
                for (int i = 0; i < BLOCKS.size(); i++) {
                    int x = (i % 5) * 3 - 6, z = (i / 5) * 4;
                    command(mc, "setblock " + x + " -60 " + z + " " + GroundBurst.MODID + ":" + BLOCKS.get(i));
                }
                for (String item : List.of("geiger_counter", "dosimeter_badge", "napalm_bucket", "howitzer", "frag_grenade",
                        "laser_designator", "argon_bucket", "chlorine_bucket")) {
                    command(mc, "give @s " + GroundBurst.MODID + ":" + item);
                }
                command(mc, "setblock -2 -60 -4 " + GroundBurst.MODID + ":warning_beacon");
            }
            case 60 -> command(mc, "setblock -2 -61 -4 minecraft:redstone_block");   // lights the beacon
            case 130 -> tidy(mc);
            case 140 -> shot(mc, "blocks");
            case 145 -> command(mc, "tp @s 0 -59 -3 0 30");
            case 165 -> tidy(mc);
            case 170 -> shot(mc, "blocks_near");
            case 175 -> dev.groundburst.client.manual.ManualHotkey.open(null);
            case 200 -> shot(mc, "manual");
            case 205 -> dev.groundburst.client.manual.ManualHotkey.open("ordnance/cruise_launcher");
            case 215 -> {
                // The entry's structure is on its next spread.
                if (mc.screen != null) {
                    mc.screen.keyPressed(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT, 0, 0);
                }
            }
            case 235 -> shot(mc, "manual_structure");
            case 240 -> {
                mc.setScreen(null);
                command(mc, "tp @s 0 -59 -9 0 18");
            }
            case 250 -> command(mc, "groundburst blast groundburst:he_warhead 0 -59 30");
            case 255 -> tidy(mc);
            case 256 -> shot(mc, "blast_early");
            case 280 -> shot(mc, "blast_late");
            case 300 -> {
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        dev.groundburst.blast.ParticleBeams.burnOverride = 80;
                        try {
                            dev.groundburst.blast.ParticleBeams.strike(server.overworld(), new net.minecraft.core.BlockPos(-14, -60, 18), null, false);
                        } finally {
                            dev.groundburst.blast.ParticleBeams.burnOverride = 0;
                        }
                    });
                }
            }
            case 335 -> tidy(mc);
            case 340 -> shot(mc, "beam");
            case 415 -> tidy(mc);
            case 420 -> shot(mc, "beam_after");
            case 425 -> {                                                         // over its trench, looking down into it
                command(mc, "setblock -14 -50 8 minecraft:glass");
                command(mc, "tp @s -14 -49 8 0 55");
            }
            case 445 -> tidy(mc);
            case 450 -> shot(mc, "beam_trench");
            // Sea mines and depth charges (main's DESIGN.md §14.6): a walled sea 13 deep, two moored mines (one awash, one 3
            // down) and a drifting one, from under the water and from above; then a depth charge 5 down, its column.
            case 470 -> command(mc, "fill 40 -60 -10 70 -48 20 minecraft:stone");
            case 472 -> command(mc, "fill 41 -60 -9 69 -48 19 minecraft:water");
            case 474 -> command(mc, "fill 55 -48 -3 55 -42 -3 minecraft:stone");      // a post to look down from
            case 476 -> {
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var level = server.overworld();
                        dev.groundburst.content.naval.SeaMine.lay(level, new net.minecraft.world.phys.Vec3(55.5, -48.6, 5.5), true, 0, null);
                        dev.groundburst.content.naval.SeaMine.lay(level, new net.minecraft.world.phys.Vec3(58.5, -48.6, 8.5), true, 3, null);
                        dev.groundburst.content.naval.SeaMine.lay(level, new net.minecraft.world.phys.Vec3(52.5, -48.6, 2.5), false, 0, null);
                    });
                }
            }
            case 480 -> command(mc, "tp @s 55.5 -54 -6 0 -4");
            case 640 -> tidy(mc);
            case 645 -> shot(mc, "naval_under");
            case 650 -> command(mc, "tp @s 55.5 -41 -3 0 40");
            case 670 -> tidy(mc);
            case 675 -> shot(mc, "naval_mines");
            case 680 -> command(mc, "tp @s 62.5 -47 -10 15 -6");                    // on the sea's wall, the post out of the way
            case 690 -> command(mc, "groundburst blast groundburst:depth_charge 55 -53 14");
            case 708 -> tidy(mc);
            case 710 -> shot(mc, "naval_column");
            case 735 -> shot(mc, "naval_column_late");
            case 760 -> {
                GroundBurst.LOGGER.info("Port check: done");
                mc.stop();
            }
            default -> {
            }
        }
    }

    /** Where the pictures go. */
    public static File folder() {
        return new File(Minecraft.getInstance().gameDirectory, "screenshots");
    }
}
