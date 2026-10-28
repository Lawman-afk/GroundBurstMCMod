package dev.groundburst.client.fx;

import dev.groundburst.GroundBurst;
import dev.groundburst.world.structure.WorldSurvey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half of the world survey ({@link WorldSurvey}): makes a normal creative world (a fixed seed, or
 * {@code -Dgroundburst.worldSurvey.seed}), takes each picture the survey asks for into
 * {@code run/screenshots/survey_<name>.png} once the camera has arrived and the ground round what it looks at has come
 * in and been built, and quits once the report is written.
 */
public final class WorldSurveyClient {
    /** Ticks to wait at least once there, the ticks the section builder must have been idle, the most to wait. */
    private static final int SETTLE = 70, STABLE = 30, MOST = 900;
    private static boolean worldMade;
    private static WorldSurvey.Shot current;
    private static int there, stable, finishedFor;

    private WorldSurveyClient() {}

    public static void register() {
        if (!WorldSurvey.enabled()) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;              // another window taking the focus must not pause the survey
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                long seed = Long.getLong("groundburst.worldSurvey.seed", 1_230_027_951L);    // the seed every recorded survey ran on
                var settings = new net.minecraft.world.level.LevelSettings("World survey", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = dev.groundburst.client.fx.DevWorlds.fresh(mc, "world_survey");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.gui.screen());
            } catch (java.io.IOException e) {
                GroundBurst.LOGGER.error("World survey: could not make a world", e);
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.options.renderDistance().set(12);
        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);   // no cloud deck over the pictures from above
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();                           // no hand or hotbar in the pictures
        }
        if (WorldSurvey.stage == WorldSurvey.Stage.FINISHED) {
            if (++finishedFor == 40) {
                GroundBurst.LOGGER.info("World survey done: {}", WorldSurvey.report);
                mc.stop();
            }
            return;
        }
        WorldSurvey.Shot s = WorldSurvey.shot;
        if (s == null) {
            current = null;
            return;
        }
        if (s != current) {
            current = s;
            there = 0;
            stable = 0;
            if (s.name().startsWith("survey_blast_")) {
                Spectacle.clearAll();                      // the cloud and dust would hide the crater for minutes yet
            }
        }
        if (mc.level.dimension() != s.dimension() || mc.player.position().distanceTo(s.at()) > 1.5) {
            return;                                        // the camera is on its way
        }
        there++;
        // The ground round what the camera looks at must have come in, and the section builder have gone quiet.
        int cx = s.look().getX() >> 4, cz = s.look().getZ() >> 4;
        boolean arrived = true;
        for (int x = -3; x <= 3 && arrived; x++) {
            for (int z = -3; z <= 3 && arrived; z++) {
                arrived = mc.level.getChunkSource().getChunk(cx + x, cz + z, ChunkStatus.FULL, false) != null;
            }
        }
        stable = arrived && mc.levelRenderer.hasRenderedAllSections() ? stable + 1 : 0;
        if (there >= MOST || there >= SETTLE && stable >= STABLE) {
            if (there >= MOST) {
                GroundBurst.LOGGER.warn("World survey: {} taken before the ground had all come in", s.name());
            }
            Screenshot.grab(mc.gameDirectory, s.name(), mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("World survey shot: {}", msg.getString()));
            WorldSurvey.shot = null;
        }
    }
}
