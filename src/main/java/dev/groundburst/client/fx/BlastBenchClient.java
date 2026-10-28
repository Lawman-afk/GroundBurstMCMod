package dev.groundburst.client.fx;

import java.util.List;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.BlastBench;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The client half of the blast bench ({@link BlastBench}): makes a normal creative world (a fixed seed, or
 * {@code -Dgroundburst.blastBench.seed}), keeps the bench told the frame rate, takes pictures at set moments after the
 * burst into {@code run/screenshots/bench_<profile>_<tick>.png}, and quits once the report is written.
 */
public final class BlastBenchClient {
    private static final List<Integer> MOMENTS = List.of(0, 20, 100, 300, 600, 1200, 2400, 4800);
    private static boolean worldMade;
    private static int shot;
    private static int finishedFor;

    private BlastBenchClient() {}

    public static void register() {
        if (!BlastBench.enabled()) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        // Another window taking the focus must not pause the game (the integrated server would stop with it).
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                long seed = Long.getLong("groundburst.blastBench.seed", 1_230_027_951L);    // the seed every recorded bench ran on
                var settings = new net.minecraft.world.level.LevelSettings("Blast bench", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = dev.groundburst.client.fx.DevWorlds.fresh(mc, "blast_bench");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.gui.screen());
            } catch (java.io.IOException e) {
                GroundBurst.LOGGER.error("Blast bench: could not make a world", e);
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.options.renderDistance().set(12);
        BlastBench.fps = mc.getFps();
        long fired = BlastBench.fired;
        if (fired >= 0 && shot < MOMENTS.size() && mc.level.getGameTime() - fired >= MOMENTS.get(shot)) {
            grab(mc, "bench_" + BlastBench.profile().replace('/', '_') + "_" + MOMENTS.get(shot) + ".png");
            shot++;
        }
        if (BlastBench.stage == BlastBench.Stage.FINISHED && ++finishedFor == 20) {
            grab(mc, "bench_" + BlastBench.profile().replace('/', '_') + "_end.png");
        }
        if (finishedFor == 40) {
            GroundBurst.LOGGER.info("Blast bench done: {}", BlastBench.report);
            mc.stop();
        }
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> GroundBurst.LOGGER.info("Bench shot: {}", msg.getString()));
    }
}
