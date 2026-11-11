package dev.groundburst.client.fx;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.PlayBench;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The client half of the play bench ({@link PlayBench}; this branch's own, main's makes its world through 26.x's dev
 * worlds): makes a normal creative world (a fixed seed, or {@code -Dgroundburst.playBench.seed}), sets the view distance
 * ({@code -Dgroundburst.playBench.view}, 32 by default) and the simulation distance ({@code .sim}, 12), frames as fast as
 * they come, keeps every frame's time by the bench's phase, and quits once the report is written.
 */
public final class PlayBenchClient {
    private static final String WORLD = "play_bench";
    private static boolean worldMade;
    private static long lastFrame;
    private static int finishedFor;

    private PlayBenchClient() {}

    public static void register() {
        if (!PlayBench.enabled()) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent e) -> { if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return; tick(); });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.RenderTickEvent e) -> { if (e.phase != net.minecraftforge.event.TickEvent.Phase.START) return; frame(); });
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
        if (mc.level == null && !worldMade && mc.getOverlay() == null && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            mc.options.renderDistance().set(view);
            mc.options.simulationDistance().set(sim);
            PlayBench.viewDistance = view;
            PlayBench.simulationDistance = sim;
            try (var access = mc.getLevelSource().createAccess(WORLD)) {
                access.deleteLevel();                      // the last run's world
            } catch (Exception ignored) {
                // none yet
            }
            long seed = Long.getLong("groundburst.playBench.seed", 1_230_027_951L);
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(),
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(seed, true, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.options.renderDistance().set(view);
        mc.options.simulationDistance().set(sim);
        mc.options.enableVsync().set(false);              // frames as fast as they come
        mc.options.framerateLimit().set(260);
        if (PlayBench.phase == PlayBench.Phase.FINISHED && ++finishedFor == 40) {
            GroundBurst.LOGGER.info("Play bench done: {}", PlayBench.report);
            mc.stop();
        }
    }
}
