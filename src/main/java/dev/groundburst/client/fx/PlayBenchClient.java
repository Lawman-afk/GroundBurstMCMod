package dev.groundburst.client.fx;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.PlayBench;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half of the play bench ({@link PlayBench}): makes a normal creative world (a fixed seed, or
 * {@code -Dgroundburst.playBench.seed}), sets the view distance ({@code -Dgroundburst.playBench.view}, 32 by default) and
 * the simulation distance ({@code .sim}, 12), keeps every frame's time by the bench's phase, and quits once the report is
 * written.
 */
public final class PlayBenchClient {
    private static boolean worldMade;
    private static long lastFrame;
    private static int finishedFor;

    private PlayBenchClient() {}

    public static void register() {
        if (!PlayBench.enabled()) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> frame());
    }

    private static void frame() {
        long now = System.nanoTime();
        PlayBench.Phase p = PlayBench.phase;
        if (lastFrame != 0 && p != PlayBench.Phase.WAITING && p != PlayBench.Phase.FINISHED && Minecraft.getInstance().level != null) {
            synchronized (PlayBench.FRAMES) {
                PlayBench.FRAMES.computeIfAbsent(p, k -> new LongArrayList()).add(now - lastFrame);
            }
        }
        lastFrame = now;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;              // another window taking the focus must not pause the game
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        int view = Integer.getInteger("groundburst.playBench.view", 32), sim = Integer.getInteger("groundburst.playBench.sim", 12);
        if (mc.level == null && !worldMade && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            mc.options.renderDistance().set(view);
            mc.options.simulationDistance().set(sim);
            PlayBench.viewDistance = view;
            PlayBench.simulationDistance = sim;
            try {
                long seed = Long.getLong("groundburst.playBench.seed", 1_230_027_951L);
                var settings = new net.minecraft.world.level.LevelSettings("Play bench", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = DevWorlds.fresh(mc, "play_bench");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.screen);
            } catch (java.io.IOException e) {
                GroundBurst.LOGGER.error("Play bench: could not make a world", e);
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.options.renderDistance().set(view);
        mc.options.simulationDistance().set(sim);
        // Frames as fast as they come: no vsync, no limit, and no slowing to 30 a second when nobody has touched the keys.
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        if (PlayBench.phase == PlayBench.Phase.FINISHED && ++finishedFor == 40) {
            GroundBurst.LOGGER.info("Play bench done: {}", PlayBench.report);
            mc.stop();
        }
    }
}
