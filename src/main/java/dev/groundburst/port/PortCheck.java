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
 * mod's blocks out in a row (machines, launchers, guns, charges, warheads), photographs them, opens the Field Manual,
 * sets a blast off and photographs that, then quits. The pictures go to run/screenshots/port_*.png; anything that goes
 * wrong is in the log.
 */
public final class PortCheck {
    private static final boolean ON = Boolean.getBoolean("groundburst.portCheck");
    private static final String WORLD = "port_check";
    private static final List<String> BLOCKS = List.of("assembly_bench", "combustion_generator", "arc_furnace", "capacitor_bank",
            "air_raid_siren", "alert_display", "c4", "he_warhead", "atomic_cannon", "barbed_wire", "corrugated_steel", "dead_grass_block",
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
                for (String item : List.of("geiger_counter", "dosimeter_badge", "combustion_generator", "atomic_cannon", "frag_grenade",
                        "laser_designator", "argon_bucket", "he_warhead")) {
                    command(mc, "give @s " + GroundBurst.MODID + ":" + item);
                }
            }
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
            case 400 -> {
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
