package dev.groundzero.client.manual;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid: with {@code -Dgroundzero.manualShots=true} (the {@code manualShots} Gradle run) the client makes a
 * flat creative world from the title screen, opens the Field Manual on a list of pages, saves a screenshot of each into
 * {@code run/screenshots} (named {@code manual_<n>.png}) and quits, so the book's layout can be reviewed without
 * clicking through it.
 */
public final class ManualPhotographer {
    /** One shot: an entry or chapter id (null: the home spread), the spread, and an optional search. */
    private record Shot(@Nullable String target, int spread, @Nullable String query) {}

    private static final Deque<Shot> SHOTS = new ArrayDeque<>();
    private static int wait = -1;
    private static int number;
    private static boolean worldMade;

    private ManualPhotographer() {}

    public static void register() {
        if (!Boolean.getBoolean("groundzero.manualShots")) {
            return;
        }
        String only = System.getProperty("groundzero.manualShots.entry", "");
        if (!only.isEmpty()) {
            for (int spread = 0; spread < 8; spread++) {
                SHOTS.add(new Shot(only, spread, null));
            }
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
            return;
        }
        SHOTS.add(new Shot(null, 0, null));
        SHOTS.add(new Shot(null, 0, "uranium"));
        SHOTS.add(new Shot("getting_started", 0, null));
        SHOTS.add(new Shot("getting_started/field_manual", 0, null));
        SHOTS.add(new Shot("getting_started/field_manual", 1, null));
        SHOTS.add(new Shot("getting_started/multiblocks", 0, null));
        SHOTS.add(new Shot("resources/ores", 0, null));
        SHOTS.add(new Shot("resources/ores", 1, null));
        SHOTS.add(new Shot("power/capacitor_banks", 0, null));
        SHOTS.add(new Shot("processing/ore_crusher", 0, null));
        SHOTS.add(new Shot("processing/ore_crusher", 1, null));
        SHOTS.add(new Shot("processing/chemical_plant", 0, null));
        SHOTS.add(new Shot("processing/chemical_plant", 1, null));
        SHOTS.add(new Shot("processing/chemical_plant", 2, null));
        SHOTS.add(new Shot("components/circuits", 1, null));
        SHOTS.add(new Shot("nuclear/fission_reactor", 0, null));
        SHOTS.add(new Shot("nuclear/fission_reactor", 1, null));
        SHOTS.add(new Shot("nuclear/fission_reactor", 2, null));
        SHOTS.add(new Shot("nuclear/fission_reactor", 3, null));
        SHOTS.add(new Shot("nuclear/particle_accelerator", 1, null));
        SHOTS.add(new Shot("nuclear/fission_reactor", 4, null));
        SHOTS.add(new Shot("processing/chemical_plant", 3, null));
        SHOTS.add(new Shot("processing", 0, null));
        SHOTS.add(new Shot("nuclear/reprocessing", 0, null));
        SHOTS.add(new Shot("safety/decontamination_shower", 0, null));
        SHOTS.add(new Shot("processing/exotic_matter_fabricator", 0, null));
        SHOTS.add(new Shot("processing/canning_machine", 0, null));
        SHOTS.add(new Shot("processing/canning_machine", 1, null));
        SHOTS.add(new Shot("processing/fluids", 1, null));
        SHOTS.add(new Shot("nuclear/fusion_reactor", 0, null));
        SHOTS.add(new Shot("nuclear/fusion_reactor", 1, null));
        SHOTS.add(new Shot("nuclear/fusion_reactor", 2, null));
        SHOTS.add(new Shot("nuclear/singularity_reactor", 0, null));
        SHOTS.add(new Shot("nuclear/singularity_reactor", 1, null));
        SHOTS.add(new Shot("nuclear/singularity_reactor", 2, null));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                var settings = new net.minecraft.world.level.LevelSettings("Manual shots", net.minecraft.world.level.GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = net.minecraft.util.FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "manual_shots", "");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings,
                        net.minecraft.client.gui.screens.worldselection.SelectWorldScreen.TEST_OPTIONS,
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createTestWorldDimensions, mc.gui.screen());
            } catch (java.io.IOException e) {
                GroundZero.LOGGER.error("Field Manual shots: could not make a world", e);
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (wait < 0) {
            wait = 100;                                         // let the world and the recipes settle first
            // Exercise the blast sync and the distance-delayed sound once: a small test blast 40 blocks away.
            var server = mc.getSingleplayerServer();
            var pos = mc.player.position().add(40, 0, 0);
            if (server != null) {
                server.execute(() -> dev.groundzero.blast.Blasts.detonate(server.overworld(), pos,
                        dev.groundzero.blast.Blasts.profile("test/small"), null));
            }
        }
        if (--wait > 0) {
            return;
        }
        if (mc.gui.screen() instanceof FieldManualScreen && number > 0) {
            Screenshot.grab(mc.gameDirectory, "manual_" + number + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundZero.LOGGER.info("Field Manual shot: {}", msg.getString()));
        }
        Shot next = SHOTS.poll();
        if (next == null) {
            if (number > 0 && !(mc.gui.screen() instanceof FieldManualScreen)) {
                GroundZero.LOGGER.info("Field Manual shots done");
                mc.stop();
            } else if (number > 0) {
                mc.gui.setScreen(null);                         // the last shot is saving; quit on the next round
                wait = 20;
            }
            return;
        }
        number++;
        mc.gui.setScreen(FieldManualScreen.at(next.target(), next.spread(), next.query()));
        wait = 20;
    }
}
