package dev.groundburst.blast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.ThrownExplosive;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Development aid: the {@code playBench} Gradle run ({@code -Dgroundburst.playBench=true}) measures what the mod costs
 * in play, on a normal world seen far (the client half, {@code PlayBenchClient}, sets the view distance, keeps the frame
 * times and quits). On the server thread it finds a forest, waits for the ground round the player to load, then plays a
 * script: a quiet spell, molotovs thrown onto the trees and a thermobaric warhead set off among them, the fires burning,
 * the healing (on and fast, unless {@code -Dgroundburst.playBench.heal=false}) and a quiet spell after. Every server
 * tick is timed, phase by phase, and the summary goes to {@code run/bench/play_<time>.txt}.
 */
public final class PlayBench {
    public enum Phase { WAITING, LOADING, QUIET, ATTACK, BURNING, HEALING, AFTER, FINISHED }

    /** Ticks each timed phase lasts (HEALING lasts until the healing is done, or its time is up). */
    private static final int QUIET = 600, ATTACK = 200, BURNING = 1200, HEALING = 7200, AFTER = 400;
    /** The ground must have stopped loading for this long (or this long at most) before the script begins. */
    private static final int LOAD_STILL = 200, LOAD_MAX = 6000;
    private static final int MOLOTOVS = 10;

    public static volatile Phase phase = Phase.WAITING;
    /** Where the summary was written. */
    public static volatile String report = "";

    private static final boolean ENABLED = Boolean.getBoolean("groundburst.playBench");
    private static final boolean HEAL = Boolean.parseBoolean(System.getProperty("groundburst.playBench.heal", "true"));

    private static long tickStart;
    private static int inPhase, stillFor, lastLoaded, loadedPeak;
    private static BlockPos forest = BlockPos.ZERO;
    private static final Map<Phase, LongArrayList> TICKS = new EnumMap<>(Phase.class);
    private static final Map<Phase, int[]> PEAKS = new EnumMap<>(Phase.class);   // loaded chunks, entities
    private static final List<String> notes = new ArrayList<>();

    private PlayBench() {}

    public static void register() {
        if (!ENABLED) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> { if (e.phase != net.minecraftforge.event.TickEvent.Phase.START) return; tickStart = System.nanoTime(); });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> { if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return; tick(e.getServer(), System.nanoTime() - tickStart); });
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static boolean healing() {
        return HEAL;
    }

    private static void tick(MinecraftServer server, long nanos) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : dev.groundburst.legacy.J21.first(server.getPlayerList().getPlayers());
        if (phase != Phase.WAITING && phase != Phase.FINISHED) {
            TICKS.computeIfAbsent(phase, k -> new LongArrayList()).add(nanos);
            int[] peaks = PEAKS.computeIfAbsent(phase, k -> new int[2]);
            peaks[0] = Math.max(peaks[0], level.getChunkSource().getLoadedChunksCount());
            if ((level.getGameTime() & 19) == 0) {
                int entities = 0;
                for (var ignored : level.getAllEntities()) {
                    entities++;
                }
                peaks[1] = Math.max(peaks[1], entities);
            }
        }
        inPhase++;
        switch (phase) {
            case WAITING -> {
                if (player != null) {
                    start(server, level, player);
                }
            }
            case LOADING -> {
                int loaded = level.getChunkSource().getLoadedChunksCount();
                loadedPeak = Math.max(loadedPeak, loaded);
                stillFor = loaded == lastLoaded ? stillFor + 1 : 0;
                lastLoaded = loaded;
                if (stillFor >= LOAD_STILL || inPhase >= LOAD_MAX) {
                    note(String.format(Locale.ROOT, "Ground loaded: %d chunks after %.0f s", loaded, inPhase / 20.0));
                    next(Phase.QUIET);
                }
            }
            case QUIET -> {
                if (inPhase >= QUIET) {
                    attack(level);
                    next(Phase.ATTACK);
                }
            }
            case ATTACK -> {
                if (inPhase >= ATTACK) {
                    next(Phase.BURNING);
                }
            }
            case BURNING -> {
                if (inPhase >= BURNING) {
                    // The forest is put out (a forest fire may burn for many minutes, and healing waits for it), so
                    // the healing that follows is the healing of a burnt and blasted forest, at the server's pace.
                    note(String.format(Locale.ROOT, "Fires put out: %d", putOut(level)));
                    next(Phase.HEALING);
                }
            }
            case HEALING -> {
                var status = dev.groundburst.heal.Healer.of(level).status(Vec3.atCenterOf(forest));
                if (inPhase % 200 == 0) {
                    note(String.format(Locale.ROOT, "Healing at %.0f s: %d blocks in %d chunks waiting, %d healing", inPhase / 20.0,
                            status.blocks(), status.chunks(), status.healing()));
                }
                if (!HEAL || status.blocks() == 0 && status.chunks() == 0 || inPhase >= HEALING) {
                    note(String.format(Locale.ROOT, "Healing phase ended after %.0f s: %d blocks in %d chunks still waiting",
                            inPhase / 20.0, status.blocks(), status.chunks()));
                    next(Phase.AFTER);
                }
            }
            case AFTER -> {
                if (inPhase >= AFTER) {
                    finish(level);
                }
            }
            case FINISHED -> { }
        }
    }

    private static void next(Phase p) {
        phase = p;
        inPhase = 0;
    }

    private static void note(String s) {
        notes.add(s);
        GroundBurst.LOGGER.info("Play bench: {}", s);
    }

    private static void start(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "time set noon");
        server.getCommands().performPrefixedCommand(source, "gamerule doDaylightCycle false");
        server.getCommands().performPrefixedCommand(source, "weather clear 1000000");
        if (HEAL) {
            GBServerConfig.HEAL_ENABLED.set(true);
            GBServerConfig.HEAL_SCHEDULE.set(GBServerConfig.HealSchedule.AFTER_WAIT);
            GBServerConfig.HEAL_WAIT.set("20s");
            GBServerConfig.HEAL_RANDOM_EXTRA.set("6s");
            GBServerConfig.HEAL_BLOCKS_PER_SECOND.set(150);
            GBServerConfig.HEAL_LONGEST.set("30s");
        } else {
            GBServerConfig.HEAL_ENABLED.set(false);
        }
        BlockPos spawn = level.getSharedSpawnPos();
        var found = level.findClosestBiome3d(b -> b.is(BiomeTags.IS_FOREST), spawn, 4000, 32, 64);
        forest = found == null ? spawn : found.getFirst();
        note("Forest at " + forest.toShortString() + (found == null ? " (none found: the spawn)" : "") + "; healing " + (HEAL ? "on" : "off"));
        level.getChunk(forest.getX() >> 4, forest.getZ() >> 4);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, forest.getX(), forest.getZ());
        player.setGameMode(GameType.SPECTATOR);
        // Watching from the south-east, above the trees.
        player.teleportTo(level, forest.getX() + 40.5, ground + 22, forest.getZ() + 40.5, 135, 20);
        next(Phase.LOADING);
    }

    /** Molotovs dropped onto the trees round the forest's middle, and a thermobaric warhead among them a little off. */
    private static void attack(ServerLevel level) {
        var random = level.getRandom();
        int trees = 0;
        for (int i = 0; i < MOLOTOVS; i++) {
            int x = forest.getX() + random.nextInt(25) - 12, z = forest.getZ() + random.nextInt(25) - 12;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (level.getBlockState(new BlockPos(x, top - 1, z)).is(BlockTags.LEAVES)) {
                trees++;
            }
            ThrownExplosive.canister(level, new Vec3(x + 0.5, top + 8, z + 0.5), new Vec3(0, -0.6, 0), null,
                    new ItemStack(GBOrdnance.MOLOTOV_COCKTAIL.get()), 200);
        }
        int bx = forest.getX() - 30, bz = forest.getZ() + 6;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        Blasts.detonate(level, new Vec3(bx + 0.5, ground + 0.5, bz + 0.5), Blasts.profile("thermobaric_warhead"), null);
        note(MOLOTOVS + " molotovs dropped (" + trees + " onto leaves), a thermobaric warhead set off at " + bx + " " + ground + " " + bz);
    }

    /** Every fire within 12 chunks of the forest goes out; how many there were. */
    private static int putOut(ServerLevel level) {
        int n = 0;
        int cx = forest.getX() >> 4, cz = forest.getZ() >> 4;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = cx - 12; x <= cx + 12; x++) {
            for (int z = cz - 12; z <= cz + 12; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (int i = 0; i < chunk.getSectionsCount(); i++) {
                    var section = chunk.getSection(i);
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> s.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)) {
                        continue;
                    }
                    int sy = chunk.getSectionYFromSectionIndex(i);
                    for (int b = 0; b < 4096; b++) {
                        if (section.getBlockState(b & 15, b >> 8, (b >> 4) & 15).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
                            level.removeBlock(p.set((x << 4) + (b & 15), (sy << 4) + (b >> 8), (z << 4) + ((b >> 4) & 15)), false);
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    private static void finish(ServerLevel level) {
        phase = Phase.FINISHED;
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dir = level.getServer().getServerDirectory().toPath().resolve("bench");
        Path file = dir.resolve("play_" + (HEAL ? "heal_" : "noheal_") + stamp + ".txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, summary());
            report = file.toAbsolutePath().toString();
            GroundBurst.LOGGER.info("Play bench: report written to {}", report);
        } catch (IOException e) {
            GroundBurst.LOGGER.error("Play bench: could not write the report", e);
        }
    }

    /** Frame times the client half kept, by phase (nanoseconds). */
    public static final Map<Phase, LongArrayList> FRAMES = new java.util.concurrent.ConcurrentHashMap<>();
    /** The view and simulation distances the client half set. */
    public static volatile int viewDistance, simulationDistance;

    private static String summary() {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "Play bench: healing %s, view distance %d, simulation distance %d%n", HEAL ? "on (fast)" : "off",
                viewDistance, simulationDistance));
        for (String n : notes) {
            b.append(n).append('\n');
        }
        b.append(String.format(Locale.ROOT, "%-8s %6s | %22s | %22s | %7s | %8s%n", "phase", "ticks", "server tick ms mean/p95/max",
                "frame ms mean/p95/max", "fps", "chunks/ents"));
        for (Phase p : Phase.values()) {
            LongArrayList t = TICKS.get(p);
            if (t == null || t.isEmpty()) {
                continue;
            }
            double[] ts = stats(t);
            LongArrayList f;
            synchronized (FRAMES) {
                f = FRAMES.get(p);
                f = f == null ? new LongArrayList() : new LongArrayList(f);
            }
            double[] fs = stats(f);
            int[] peaks = PEAKS.getOrDefault(p, new int[2]);
            b.append(String.format(Locale.ROOT, "%-8s %6d | %6.1f %6.1f %8.1f | %6.1f %6.1f %8.1f | %7.0f | %d/%d%n", p, t.size(), ts[0], ts[1], ts[2],
                    fs[0], fs[1], fs[2], fs[0] > 0 ? 1000 / fs[0] : 0, peaks[0], peaks[1]));
        }
        return b.toString();
    }

    /** Mean, 95th percentile and largest of a list of nanoseconds, in milliseconds. */
    private static double[] stats(LongArrayList v) {
        if (v.isEmpty()) {
            return new double[3];
        }
        long[] s = v.toLongArray();
        java.util.Arrays.sort(s);
        double sum = 0;
        for (long x : s) {
            sum += x;
        }
        return new double[] {sum / s.length / 1e6, s[Math.min(s.length - 1, (int) (0.95 * (s.length - 1)))] / 1e6, s[s.length - 1] / 1e6};
    }
}
