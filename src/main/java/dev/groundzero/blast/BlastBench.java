package dev.groundzero.blast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.groundzero.GroundZero;
import dev.groundzero.config.GZServerConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development aid (DESIGN.md §11.7): the {@code blastBench} Gradle run ({@code -Dgroundzero.blastBench=<profile>})
 * measures what a big blast costs on a normal world. The client half ({@code BlastBenchClient}) makes the world, takes
 * pictures and quits; this half, on the server thread, generates the ground round the spawn out past the blast's reach,
 * lets those chunks unload, sets the blast off at the spawn with the player watching from {@link #VIEW} blocks east,
 * and records every server tick until the blast has finished: its length, the blasts' share of it, the front, the
 * rays, the chunks held, the heap and the client's frame rate. {@link #SAVE_AT} ticks in it saves the world the way the
 * autosave does. The rows go to {@code run/bench/<profile>_<time>.csv}, a summary to the {@code .txt} beside it.
 */
public final class BlastBench {
    public enum Stage { WAITING, GENERATING, SETTLING, RUNNING, FINISHED }

    /** How far east of the burst the player watches from, and how high above the ground there. */
    static final int VIEW = 360, VIEW_HEIGHT = 60;
    /** Chunks generated at a time. */
    private static final int BATCH = 192;
    /** Ticks to let the generated chunks unload before the blast. */
    private static final int SETTLE = 300;
    /** Ticks the record runs on after the blast has finished. */
    private static final int TAIL = 40;
    /** Gives up this many ticks after the burst (20 minutes). */
    private static final int TIMEOUT = 24_000;
    /** A save like the autosave this many ticks after the burst. */
    static final int SAVE_AT = 600;
    private static final UUID OWNER = UUID.nameUUIDFromBytes("groundzero:blast_bench".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    public static volatile Stage stage = Stage.WAITING;
    /** The client's frames a second (the client half keeps it current). */
    public static volatile int fps;
    /** Game time the blast went off, or -1. */
    public static volatile long fired = -1;
    /** Where the summary was written. */
    public static volatile String report = "";

    private static final String profile = System.getProperty("groundzero.blastBench", "");
    private static int radius;
    private static long tickStart;
    private static List<ChunkPos> todo = List.of();
    private static int next;
    private static final List<ChunkPos> batch = new ArrayList<>();
    private static long genStart, genNanos;
    private static int settled;
    private static Vec3 burst = Vec3.ZERO;
    private static Blast job;
    private static int doneAt = -1;
    private static long saveNanos = -1;
    private static boolean timedOut;
    private static final StringBuilder csv = new StringBuilder();
    private static final LongArrayList tickNanos = new LongArrayList(), blastNanos = new LongArrayList();
    private static int peakRays, peakRoom, peakChunks, peakSections, peakRim, peakDeferred, peakLoaded, minFps = Integer.MAX_VALUE;
    private static long peakHeap, fpsSum;
    private static int fpsCount, terrainAt = -1, frontAt = -1;

    private BlastBench() {}

    /** Hooks the bench in when the run asks for it. */
    public static void register() {
        if (profile.isEmpty()) {
            return;
        }
        radius = Integer.getInteger("groundzero.blastBench.radius", 0);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> tickStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer(), System.nanoTime() - tickStart));
    }

    public static boolean enabled() {
        return !profile.isEmpty();
    }

    public static String profile() {
        return profile;
    }

    private static void tick(MinecraftServer server, long nanos) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().getFirst();
        switch (stage) {
            case WAITING -> {
                if (player != null) {
                    start(server, level, player);
                }
            }
            case GENERATING -> generate(level);
            case SETTLING -> settle(level, player);
            case RUNNING -> record(server, level, nanos);
            case FINISHED -> { }
        }
    }

    private static void start(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "time set noon");
        server.getCommands().performPrefixedCommand(source, "gamerule doDaylightCycle false");
        player.setGameMode(GameType.SPECTATOR);
        BlockPos spawn = level.getRespawnData().pos();
        burst = new Vec3(spawn.getX() + 0.5, 0, spawn.getZ() + 0.5);
        var key = Blasts.profile(profile);
        var found = level.registryAccess().lookupOrThrow(BlastProfile.REGISTRY).getOptional(key);
        if (found.isEmpty()) {
            GroundZero.LOGGER.error("Blast bench: no blast profile {}", key.identifier());
            finish(level, "no such profile");
            return;
        }
        BlastProfile p = found.get().scaled(GZServerConfig.RADIUS_MULTIPLIER.get().floatValue(), GZServerConfig.MAX_RADIUS.get());
        int r = radius > 0 ? radius : (int) Math.ceil(p.outerRadius()) + 32;
        int cx = spawn.getX() >> 4, cz = spawn.getZ() >> 4, cr = (r >> 4) + 1;
        List<ChunkPos> all = new ArrayList<>();
        for (int x = cx - cr; x <= cx + cr; x++) {
            for (int z = cz - cr; z <= cz + cr; z++) {
                double dx = (x * 16 + 8) - burst.x, dz = (z * 16 + 8) - burst.z;
                if (dx * dx + dz * dz <= (double) (r + 12) * (r + 12)) {
                    all.add(new ChunkPos(x, z));
                }
            }
        }
        all.sort(Comparator.comparingDouble(c -> Math.hypot(c.x() * 16 + 8 - burst.x, c.z() * 16 + 8 - burst.z)));
        todo = all;
        next = 0;
        radius = r;
        GroundZero.LOGGER.info("Blast bench: {} (outer radius {}), generating {} chunks out to {} round {}", profile, p.outerRadius(),
                all.size(), r, spawn.toShortString());
        player.teleportTo(level, burst.x + VIEW, 260, burst.z, java.util.Set.of(), 90, 10, true);
        genStart = System.nanoTime();
        stage = Stage.GENERATING;
    }

    private static void generate(ServerLevel level) {
        if (!batch.isEmpty()) {
            for (ChunkPos c : batch) {
                if (level.getChunkSource().getChunkNow(c.x(), c.z()) == null) {
                    return;                                // still generating
                }
            }
            for (ChunkPos c : batch) {
                BlastChunks.TICKETS.forceChunk(level, OWNER, c.x(), c.z(), false, false);
            }
            batch.clear();
            if (next * 10 / todo.size() != (next - BATCH) * 10 / todo.size()) {
                GroundZero.LOGGER.info("Blast bench: generated {} / {} chunks in {} s", next, todo.size(),
                        (System.nanoTime() - genStart) / 1_000_000_000L);
            }
        }
        if (next >= todo.size()) {
            genNanos = System.nanoTime() - genStart;
            settled = 0;
            stage = Stage.SETTLING;
            return;
        }
        for (int i = 0; i < BATCH && next < todo.size(); i++) {
            ChunkPos c = todo.get(next++);
            BlastChunks.TICKETS.forceChunk(level, OWNER, c.x(), c.z(), true, false);
            batch.add(c);
        }
    }

    private static void settle(ServerLevel level, ServerPlayer player) {
        settled++;
        if (settled == 1 && player != null) {
            int vx = (int) Math.floor(burst.x) + VIEW, vz = (int) Math.floor(burst.z);
            level.getChunk(vx >> 4, vz >> 4);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, vx, vz);
            player.teleportTo(level, vx + 0.5, ground + VIEW_HEIGHT, vz + 0.5, java.util.Set.of(), 90, 9, true);
        }
        if (settled < SETTLE) {
            return;
        }
        int bx = (int) Math.floor(burst.x), bz = (int) Math.floor(burst.z);
        level.getChunk(bx >> 4, bz >> 4);
        burst = new Vec3(burst.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + 0.5, burst.z);
        job = Blasts.detonate(level, burst, Blasts.profile(profile), null);
        if (job == null) {
            finish(level, "the blast did not start");
            return;
        }
        csv.append("tick,tick_ms,blast_ms,shock,terrain,blocks,phase,rays,ray_room,chunks_held,chunks_loaded,sections,rim,deferred,heap_mb,fps,note\n");
        fired = level.getGameTime();
        stage = Stage.RUNNING;
        GroundZero.LOGGER.info("Blast bench: {} set off at {}", profile, BlockPos.containing(burst).toShortString());
    }

    private static void record(MinecraftServer server, ServerLevel level, long nanos) {
        int t = (int) (level.getGameTime() - fired);
        Blast.Stats s = job.stats();
        long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int loaded = level.getChunkSource().getLoadedChunksCount();
        int f = fps;
        tickNanos.add(nanos);
        blastNanos.add(BlastManager.nanosUsed);
        peakRays = Math.max(peakRays, s.rays());
        peakRoom = Math.max(peakRoom, s.rayRoom());
        peakChunks = Math.max(peakChunks, s.chunks());
        peakSections = Math.max(peakSections, s.sections());
        peakRim = Math.max(peakRim, s.rim());
        peakDeferred = Math.max(peakDeferred, s.deferred());
        peakLoaded = Math.max(peakLoaded, loaded);
        peakHeap = Math.max(peakHeap, heap);
        if (f > 0) {
            minFps = Math.min(minFps, f);
            fpsSum += f;
            fpsCount++;
        }
        BlastProfile p = ((BlastJob) job).profile;
        if (terrainAt < 0 && s.terrain() > p.terrainRadius()) {
            terrainAt = t;
        }
        if (frontAt < 0 && !s.phase().equals("FRONT")) {
            frontAt = t;
        }
        String note = "";
        if (t == SAVE_AT) {
            long s0 = System.nanoTime();
            server.saveEverything(true, false, false);    // as the autosave does
            saveNanos = System.nanoTime() - s0;
            note = "save " + String.format(Locale.ROOT, "%.1f", saveNanos / 1e6) + " ms";
        }
        csv.append(t).append(',').append(ms(nanos)).append(',').append(ms(BlastManager.nanosUsed)).append(',').append(job.radius()).append(',')
                .append(s.terrain()).append(',')
                .append(job.blocksChanged()).append(',').append(s.phase()).append(',').append(s.rays()).append(',').append(s.rayRoom()).append(',')
                .append(s.chunks()).append(',').append(loaded).append(',').append(s.sections()).append(',').append(s.rim()).append(',')
                .append(s.deferred()).append(',').append(heap >> 20).append(',').append(f).append(',').append(note).append('\n');
        if (job.isDone() && doneAt < 0) {
            doneAt = t;
        }
        if (doneAt >= 0 && t >= doneAt + TAIL || t >= TIMEOUT) {
            timedOut = doneAt < 0;
            finish(level, timedOut ? "timed out" : "");
        }
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    private static void finish(ServerLevel level, String problem) {
        stage = Stage.FINISHED;
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dir = level.getServer().getServerDirectory().resolve("bench");
        String base = profile.replace('/', '_') + "_" + stamp;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(base + ".csv"), csv);
            Files.writeString(dir.resolve(base + ".txt"), summary(problem));
            report = dir.resolve(base + ".txt").toAbsolutePath().toString();
            GroundZero.LOGGER.info("Blast bench: report written to {}", report);
        } catch (IOException e) {
            GroundZero.LOGGER.error("Blast bench: could not write the report", e);
        }
    }

    private static String summary(String problem) {
        StringBuilder b = new StringBuilder();
        b.append("Blast bench: ").append(profile).append(problem.isEmpty() ? "" : " (" + problem + ")").append('\n');
        b.append(String.format(Locale.ROOT, "Generated %d chunks out to %d blocks in %.1f s%n", todo.size(), radius, genNanos / 1e9));
        if (job == null) {
            return b.toString();
        }
        BlastProfile p = ((BlastJob) job).profile;
        b.append(String.format(Locale.ROOT, "Profile (after the config): core %.0f, crater %.0f, structural %.0f, scorch %.0f, fire %.0f, lethal %.0f, "
                + "damage %.0f; terrain radius %.0f, outer radius %.0f; max radius setting %d%n", p.core(), p.crater(), p.structural(),
                p.scorch(), p.fire(), p.lethal(), p.damage(), p.terrainRadius(), p.outerRadius(), GZServerConfig.MAX_RADIUS.get()));
        int n = tickNanos.size();
        b.append(String.format(Locale.ROOT, "Ticks recorded %d; terrain front past the terrain radius at %d, fronts done at %d, "
                + "blast finished at %d (%.1f s)%n", n, terrainAt, frontAt, doneAt, doneAt / 20.0));
        b.append(String.format(Locale.ROOT, "Blocks changed %d%n", job.blocksChanged()));
        long[] ticks = tickNanos.toLongArray();
        long[] sorted = ticks.clone();
        java.util.Arrays.sort(sorted);
        long sum = 0;
        int over50 = 0, over100 = 0;
        for (long v : ticks) {
            sum += v;
            over50 += v > 50_000_000L ? 1 : 0;
            over100 += v > 100_000_000L ? 1 : 0;
        }
        b.append(String.format(Locale.ROOT, "Server tick ms: mean %.1f, median %.1f, p95 %.1f, p99 %.1f, max %.1f; over 50 ms %d ticks, over 100 ms %d%n",
                n == 0 ? 0 : sum / 1e6 / n, pct(sorted, 0.5), pct(sorted, 0.95), pct(sorted, 0.99), n == 0 ? 0 : sorted[n - 1] / 1e6, over50, over100));
        long[] bl = blastNanos.toLongArray();
        long bsum = 0, bmax = 0;
        for (long v : bl) {
            bsum += v;
            bmax = Math.max(bmax, v);
        }
        b.append(String.format(Locale.ROOT, "Blast share ms: mean %.1f, max %.1f (budget %d)%n", n == 0 ? 0 : bsum / 1e6 / n, bmax / 1e6,
                GZServerConfig.MS_BUDGET_PER_TICK.get()));
        b.append(String.format(Locale.ROOT, "Save like the autosave at tick %d: %.1f ms%n", SAVE_AT, saveNanos / 1e6));
        b.append(String.format(Locale.ROOT, "Peaks: rays %d (arrays room %d, ~%d MB), chunks held %d, chunks loaded %d, sections marked %d, rim %d, "
                + "fires and rubble waiting %d%n", peakRays, peakRoom, peakRoom * 34L >> 20, peakChunks, peakLoaded, peakSections, peakRim, peakDeferred));
        b.append(String.format(Locale.ROOT, "Heap: peak used %d MB of %d MB%n", peakHeap >> 20, Runtime.getRuntime().maxMemory() >> 20));
        b.append(String.format(Locale.ROOT, "Client fps: mean %.0f, min %d%n", fpsCount == 0 ? 0 : (double) fpsSum / fpsCount,
                minFps == Integer.MAX_VALUE ? 0 : minFps));
        return b.toString();
    }

    private static double pct(long[] sorted, double q) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.floor(q * (sorted.length - 1)))] / 1e6;
    }
}
