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
 * burst into {@code run/screenshots/bench_<profile>_<tick>.png}, and quits once the report is written. For the mod page's
 * pictures: {@code -Dgroundburst.blastBench.moments=0,40,...} the moments, {@code .render=<chunks>} how far it sees (12),
 * {@code .clean=true} no hand, hotbar or tutorial pop-ups.
 */
public final class BlastBenchClient {
    private static final List<Integer> MOMENTS = moments();
    private static final int RENDER = Integer.getInteger("groundburst.blastBench.render", 12);
    private static final boolean CLEAN = Boolean.getBoolean("groundburst.blastBench.clean");

    private static List<Integer> moments() {
        String m = System.getProperty("groundburst.blastBench.moments", "");
        if (m.isBlank()) {
            return List.of(0, 20, 100, 300, 600, 1200, 2400, 4800);
        }
        return java.util.Arrays.stream(m.split(",")).map(String::trim).map(Integer::parseInt).sorted().toList();
    }
    private static boolean worldMade;
    private static int shot;
    private static int finishedFor;

    private BlastBenchClient() {}

    public static void register() {
        if (!BlastBench.enabled()) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) -> frame());
    }

    private static long lastFrame;

    /** Every frame's length, by the bench's phase, once the world is being watched (from the ground's settling on). */
    private static void frame() {
        long now = System.nanoTime();
        BlastBench.Stage stage = BlastBench.stage;
        if (lastFrame != 0 && (stage == BlastBench.Stage.SETTLING || stage == BlastBench.Stage.RUNNING) && Minecraft.getInstance().level != null) {
            long dt = now - lastFrame;
            synchronized (BlastBench.FRAMES) {
                BlastBench.FRAMES.computeIfAbsent(BlastBench.phase(), k -> new it.unimi.dsi.fastutil.longs.LongArrayList()).add(dt);
            }
            if (dt > BlastBench.worstFrame) {
                BlastBench.worstFrame = dt;
            }
        }
        lastFrame = now;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        // Another window taking the focus must not pause the game (the integrated server would stop with it).
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        if (mc.level == null && !worldMade && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                long seed = Long.getLong("groundburst.blastBench.seed", 1_230_027_951L);    // the seed every recorded bench ran on
                var settings = new net.minecraft.world.level.LevelSettings("Blast bench", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = dev.groundburst.client.fx.DevWorlds.fresh(mc, "blast_bench");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.screen);
            } catch (java.io.IOException e) {
                GroundBurst.LOGGER.error("Blast bench: could not make a world", e);
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.options.renderDistance().set(RENDER);
        if (CLEAN) {
            if (!mc.options.hideGui) {
                mc.options.hideGui = !mc.options.hideGui;                       // no hand or hotbar in the pictures
            }
            if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            }
        }
        // Frames as fast as they come (the frame rates benches recorded before were the 30 a second the game slows to
        // when nobody has touched the keys for a minute).
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
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
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, msg -> GroundBurst.LOGGER.info("Bench shot: {}", msg.getString()));
    }
}
