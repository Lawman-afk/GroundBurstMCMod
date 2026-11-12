package dev.groundburst.blast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
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
 * Development aid (DESIGN.md §11.7): the {@code blastBench} Gradle run ({@code -Dgroundburst.blastBench=<profile>})
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
    private static final UUID OWNER = UUID.nameUUIDFromBytes("groundburst:blast_bench".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /**
     * A warhead set off by its own path instead of a bare blast profile ({@code -Dgroundburst.blastBench.kind=<ChargeKind>}):
     * its fallout and radiation, and the special weapons (antimatter, singularity, storm, cryo), which are not blasts.
     */
    private static final String KIND = System.getProperty("groundburst.blastBench.kind", "");
    /** How far east of the burst the player watches from ({@code .view}, blocks). */
    private static final int VIEW_DISTANCE = Integer.getInteger("groundburst.blastBench.view", VIEW);
    /**
     * Ticks the record runs on after the blast has finished ({@code .tail}, seconds): its fires, its fallout, its clouds.
     * A weapon that is not a blast is recorded that long after the burst.
     */
    private static final int TAIL_TICKS = Math.max(1, Integer.getInteger("groundburst.blastBench.tail", TAIL / 20)) * 20;

    /** The part of the run a frame falls in. */
    public enum Phase { BEFORE, BLAST, AFTER }

    /** The client's frame times by phase (nanoseconds): the client half adds them, the summary reads them. */
    public static final java.util.Map<Phase, LongArrayList> FRAMES = new java.util.EnumMap<>(Phase.class);
    /** The longest frame since the last tick's row (the client half raises it; each row takes it and starts it again). */
    public static volatile long worstFrame;

    /** The phase a frame now falls in: before the burst, while the blast runs (a weapon that is not one: throughout), after. */
    public static Phase phase() {
        return fired < 0 ? Phase.BEFORE : doneAt < 0 ? Phase.BLAST : Phase.AFTER;
    }

    public static volatile Stage stage = Stage.WAITING;
    /** The client's frames a second (the client half keeps it current). */
    public static volatile int fps;
    /** Game time the blast went off, or -1. */
    public static volatile long fired = -1;
    /** Where the summary was written. */
    public static volatile String report = "";

    private static final String profile = System.getProperty("groundburst.blastBench", "");
    /** With healing on ({@code -Dgroundburst.blastBench.heal=true}): the record runs on through the crater's healing. */
    private static final boolean HEAL = Boolean.getBoolean("groundburst.blastBench.heal");
    /** Gives up on the healing this many ticks after the blast has finished (20 minutes). */
    private static final int HEAL_TIMEOUT = 24_000;
    private static int healStart = -1, healEnd = -1, healBlocks;
    private static int radius;
    private static long tickStart;
    private static List<ChunkPos> todo = List.of();
    private static int next;
    private static final List<ChunkPos> batch = new ArrayList<>();
    private static long genStart, genNanos;
    private static int settled;
    private static Vec3 burst = Vec3.ZERO;
    private static Blast job;
    private static volatile int doneAt = -1;
    private static long saveNanos = -1, endSaveNanos = -1;
    private static int rememberedAtSave = -1, rememberedAtDone = -1;
    private static long doneSaveNanos = -1, regionsAtDone = -1;
    private static Path worldRoot;
    private static boolean timedOut;
    private static final StringBuilder csv = new StringBuilder();
    private static final LongArrayList tickNanos = new LongArrayList(), blastNanos = new LongArrayList();
    private static final it.unimi.dsi.fastutil.ints.IntArrayList fpsTicks = new it.unimi.dsi.fastutil.ints.IntArrayList();
    private static int peakRays, peakRoom, peakChunks, peakSections, peakRim, peakDeferred, peakLoaded, minFps = Integer.MAX_VALUE;
    private static long peakHeap, fpsSum;
    private static int fpsCount, terrainAt = -1, frontAt = -1;

    private BlastBench() {}

    /** Hooks the bench in when the run asks for it. */
    public static void register() {
        if (profile.isEmpty()) {
            return;
        }
        radius = Integer.getInteger("groundburst.blastBench.radius", 0);
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
        server.getCommands().performPrefixedCommand(source, "gamerule advance_time false");
        GBServerConfig.HEAL_ENABLED.set(HEAL);
        if (HEAL) {
            GBServerConfig.HEAL_SCHEDULE.set(GBServerConfig.HealSchedule.AFTER_WAIT);
            GBServerConfig.HEAL_WAIT.set("20s");
            GBServerConfig.HEAL_RANDOM_EXTRA.set("0s");
        }
        player.setGameMode(GameType.SPECTATOR);
        BlockPos spawn = level.getRespawnData().pos();
        burst = new Vec3(spawn.getX() + 0.5, 0, spawn.getZ() + 0.5);
        var key = Blasts.profile(profile);
        var found = level.registryAccess().lookupOrThrow(BlastProfile.REGISTRY).getOptional(key);
        if (found.isEmpty() && KIND.isEmpty()) {
            GroundBurst.LOGGER.error("Blast bench: no blast profile {}", key.identifier());
            finish(level, "no such profile");
            return;
        }
        // (A special weapon has no blast profile of its own: the ground out to 200 blocks, or as far as asked.)
        float outer = found.map(f -> BlastSettings.asSetOff(key.identifier(), f, 1).outerRadius())
                .orElse(168f);
        int r = radius > 0 ? radius : (int) Math.ceil(outer) + 32;
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
        GroundBurst.LOGGER.info("Blast bench: {}{} (outer radius {}), generating {} chunks out to {} round {}", profile,
                KIND.isEmpty() ? "" : " set off as " + KIND, outer, all.size(), r, spawn.toShortString());
        player.teleportTo(level, burst.x + VIEW_DISTANCE, 260, burst.z, java.util.Set.of(), 90, 10, true);
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
                GroundBurst.LOGGER.info("Blast bench: generated {} / {} chunks in {} s", next, todo.size(),
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
            int vx = (int) Math.floor(burst.x) + VIEW_DISTANCE, vz = (int) Math.floor(burst.z);
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
        if (!KIND.isEmpty()) {
            // The warhead's own path, as a missile's impact sets it off: its blast (if it has one), fallout, effects.
            dev.groundburst.content.ordnance.charge.Charges.impact(level, dev.groundburst.content.ordnance.charge.ChargeKind.valueOf(KIND),
                    BlockPos.containing(burst), null);
            job = BlastManager.of(level).newest();
        } else {
            job = Blasts.detonate(level, burst, Blasts.profile(profile), null);
            if (job == null) {
                finish(level, "the blast did not start");
                return;
            }
        }
        csv.append("tick,tick_ms,blast_ms,shock,terrain,blocks,phase,rays,ray_room,chunks_held,chunks_loaded,sections,rim,deferred,heap_mb,fps,"
                + "frame_worst_ms,note\n");
        fired = level.getGameTime();
        stage = Stage.RUNNING;
        GroundBurst.LOGGER.info("Blast bench: {} set off at {}", profile, BlockPos.containing(burst).toShortString());
    }

    private static void record(MinecraftServer server, ServerLevel level, long nanos) {
        int t = (int) (level.getGameTime() - fired);
        Blast.Stats s = job == null ? null : job.stats();
        long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int loaded = level.getChunkSource().getLoadedChunksCount();
        int f = fps;
        long frame = worstFrame;
        worstFrame = 0;
        tickNanos.add(nanos);
        fpsTicks.add(fps);
        blastNanos.add(BlastManager.nanosUsed);
        if (s != null) {
            peakRays = Math.max(peakRays, s.rays());
            peakRoom = Math.max(peakRoom, s.rayRoom());
            peakChunks = Math.max(peakChunks, s.chunks());
            peakSections = Math.max(peakSections, s.sections());
            peakRim = Math.max(peakRim, s.rim());
            peakDeferred = Math.max(peakDeferred, s.deferred());
            BlastProfile p = ((BlastJob) job).profile;
            if (terrainAt < 0 && s.terrain() > p.terrainRadius()) {
                terrainAt = t;
            }
            if (frontAt < 0 && !s.phase().equals("FRONT")) {
                frontAt = t;
            }
        }
        peakLoaded = Math.max(peakLoaded, loaded);
        peakHeap = Math.max(peakHeap, heap);
        if (f > 0) {
            minFps = Math.min(minFps, f);
            fpsSum += f;
            fpsCount++;
        }
        String note = "";
        if (t == SAVE_AT) {
            if (HEAL) {
                rememberedAtSave = dev.groundburst.heal.Healer.of(level).status(burst).blocks();
            }
            long s0 = System.nanoTime();
            server.saveEverything(true, false, false);    // as the autosave does
            saveNanos = System.nanoTime() - s0;
            note = "save " + String.format(Locale.ROOT, "%.1f", saveNanos / 1e6) + " ms";
        }
        csv.append(t).append(',').append(ms(nanos)).append(',').append(ms(BlastManager.nanosUsed)).append(',')
                .append(job == null ? 0 : job.radius()).append(',').append(s == null ? 0 : s.terrain()).append(',')
                .append(job == null ? 0 : job.blocksChanged()).append(',').append(s == null ? "-" : s.phase()).append(',')
                .append(s == null ? 0 : s.rays()).append(',').append(s == null ? 0 : s.rayRoom()).append(',')
                .append(s == null ? 0 : s.chunks()).append(',').append(loaded).append(',').append(s == null ? 0 : s.sections()).append(',')
                .append(s == null ? 0 : s.rim()).append(',').append(s == null ? 0 : s.deferred()).append(',').append(heap >> 20).append(',')
                .append(f).append(',').append(ms(frame)).append(',').append(note).append('\n');
        if (job == null) {
            if (t >= TAIL_TICKS) {
                finish(level, "");                         // (a weapon that is not a blast: recorded for the tail from its burst)
            }
            return;
        }
        if (job.isDone() && doneAt < 0) {
            doneAt = t;
            if (HEAL) {
                // The save as the blast finishes, every block it changed remembered for healing: the most a save has to write.
                rememberedAtDone = dev.groundburst.heal.Healer.of(level).status(burst).blocks();
                long s0 = System.nanoTime();
                server.saveEverything(true, false, false);
                doneSaveNanos = System.nanoTime() - s0;
                server.saveEverything(true, true, false);          // (written out, for the size on disk)
                regionsAtDone = regionBytes(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize());
                // The firestorm put out (healing waits for fires near the damage, and forests burn for many minutes),
                // so what follows is the healing itself.
                GroundBurst.LOGGER.info("Blast bench: blast finished; {} fires put out", putOutFires(level, BlockPos.containing(burst), (radius >> 4) + 2));
            }
        }
        if (HEAL && doneAt >= 0) {
            // On through the healing: from the first block back to the last.
            var status = dev.groundburst.heal.Healer.of(level).status(burst);
            if (healStart < 0 && status.healing() > 0) {
                healStart = t;
                healBlocks = status.blocks();
            }
            if (healStart >= 0 && healEnd < 0 && status.blocks() == 0 && status.chunks() == 0) {
                healEnd = t;
            }
            if (healEnd >= 0 && t >= healEnd + TAIL || t >= doneAt + HEAL_TIMEOUT) {
                finish(level, healEnd < 0 ? "the healing timed out" : "");
            }
            return;
        }
        if (doneAt >= 0 && t >= doneAt + TAIL_TICKS || t >= TIMEOUT) {
            timedOut = doneAt < 0;
            finish(level, timedOut ? "timed out" : "");
        }
    }

    /** Every fire in the loaded chunks within {@code chunkRadius} chunks of {@code centre} goes out; how many there were. */
    static int putOutFires(ServerLevel level, BlockPos centre, int chunkRadius) {
        int n = 0;
        int cx = centre.getX() >> 4, cz = centre.getZ() >> 4;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
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

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    private static void finish(ServerLevel level, String problem) {
        if (fired >= 0) {
            long s0 = System.nanoTime();
            level.getServer().saveEverything(true, false, false);   // as the autosave does, now with all the aftermath's records
            endSaveNanos = System.nanoTime() - s0;
            level.getServer().saveEverything(true, true, false);    // (and written out, for the sizes on disk)
        }
        worldRoot = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize();
        stage = Stage.FINISHED;
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dir = level.getServer().getServerDirectory().resolve("bench");
        String base = profile.replace('/', '_') + "_" + stamp;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(base + ".csv"), csv);
            Files.writeString(dir.resolve(base + ".txt"), summary(problem));
            report = dir.resolve(base + ".txt").toAbsolutePath().toString();
            GroundBurst.LOGGER.info("Blast bench: report written to {}", report);
        } catch (IOException e) {
            GroundBurst.LOGGER.error("Blast bench: could not write the report", e);
        }
    }

    private static String summary(String problem) {
        StringBuilder b = new StringBuilder();
        b.append("Blast bench: ").append(profile).append(KIND.isEmpty() ? "" : ", set off as " + KIND)
                .append(problem.isEmpty() ? "" : " (" + problem + ")").append('\n');
        b.append(String.format(Locale.ROOT, "Generated %d chunks out to %d blocks in %.1f s; watched from %d blocks east, %d up; recorded %d s "
                + "after %s%n", todo.size(), radius, genNanos / 1e9, VIEW_DISTANCE, VIEW_HEIGHT, TAIL_TICKS / 20,
                job == null ? "the burst (no blast of its own)" : "the blast finished"));
        if (fired < 0) {
            return b.toString();
        }
        int n = tickNanos.size();
        if (job != null) {
            BlastProfile p = ((BlastJob) job).profile;
            b.append(String.format(Locale.ROOT, "Profile (after the config): core %.0f, crater %.0f, structural %.0f, scorch %.0f, fire %.0f, lethal %.0f, "
                    + "damage %.0f; terrain radius %.0f, outer radius %.0f; max radius setting %d%n", p.core(), p.crater(), p.structural(),
                    p.scorch(), p.fire(), p.lethal(), p.damage(), p.terrainRadius(), p.outerRadius(), GBServerConfig.MAX_RADIUS.get()));
            b.append(String.format(Locale.ROOT, "Ticks recorded %d; terrain front past the terrain radius at %d, fronts done at %d, "
                    + "blast finished at %d (%.1f s)%n", n, terrainAt, frontAt, doneAt, doneAt / 20.0));
            b.append(String.format(Locale.ROOT, "Blocks changed %d%n", job.blocksChanged()));
        } else {
            b.append(String.format(Locale.ROOT, "Ticks recorded %d%n", n));
        }
        if (HEAL) {
            b.append("Healing on (wait 20 s after the last damage; the fires put out once the blast had finished)\n");
            if (healStart >= 0) {
                int end = healEnd >= 0 ? healEnd : n - 1;
                long[] heal = java.util.Arrays.copyOfRange(tickNanos.toLongArray(), healStart, Math.min(n, end + 1));
                long[] hs = heal.clone();
                java.util.Arrays.sort(hs);
                long hsum = 0;
                int h50 = 0;
                for (long v : heal) {
                    hsum += v;
                    h50 += v > 50_000_000L ? 1 : 0;
                }
                b.append(String.format(Locale.ROOT, "Healing: %d blocks from tick %d to %d (%.1f s, %.0f blocks a second); server tick ms mean %.1f, "
                        + "p95 %.1f, p99 %.1f, max %.1f; over 50 ms %d ticks%n", healBlocks, healStart, end, (end - healStart) / 20.0,
                        healBlocks / Math.max(0.05, (end - healStart) / 20.0), hs.length == 0 ? 0 : hsum / 1e6 / hs.length, pct(hs, 0.95),
                        pct(hs, 0.99), hs.length == 0 ? 0 : hs[hs.length - 1] / 1e6, h50));
                int fsum = 0, fn = 0, fmin = Integer.MAX_VALUE;
                for (int i = healStart; i <= end && i < fpsTicks.size(); i++) {
                    int f = fpsTicks.getInt(i);
                    if (f > 0) {
                        fsum += f;
                        fn++;
                        fmin = Math.min(fmin, f);
                    }
                }
                b.append(String.format(Locale.ROOT, "Healing: client fps mean %.0f, min %d%n", fn == 0 ? 0 : (double) fsum / fn, fn == 0 ? 0 : fmin));
            } else {
                b.append("Healing: never began\n");
            }
            n = Math.min(n, doneAt < 0 ? n : doneAt + TAIL);      // the blast's own figures below: up to its end
        }
        long[] ticks = java.util.Arrays.copyOf(tickNanos.toLongArray(), n);
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
                GBServerConfig.MS_BUDGET_PER_TICK.get()));
        b.append(String.format(Locale.ROOT, "Save like the autosave at tick %d: %.1f ms%n", SAVE_AT, saveNanos / 1e6));
        b.append(String.format(Locale.ROOT, "Peaks: rays %d (arrays room %d, ~%d MB), chunks held %d, chunks loaded %d, sections marked %d, rim %d, "
                + "fires and rubble waiting %d%n", peakRays, peakRoom, peakRoom * 34L >> 20, peakChunks, peakLoaded, peakSections, peakRim, peakDeferred));
        b.append(String.format(Locale.ROOT, "Heap: peak used %d MB of %d MB%n", peakHeap >> 20, Runtime.getRuntime().maxMemory() >> 20));
        b.append(String.format(Locale.ROOT, "Client fps: mean %.0f, min %d%n", fpsCount == 0 ? 0 : (double) fpsSum / fpsCount,
                minFps == Integer.MAX_VALUE ? 0 : minFps));
        synchronized (FRAMES) {
            for (Phase ph : Phase.values()) {
                LongArrayList f = FRAMES.get(ph);
                if (f == null || f.isEmpty()) {
                    continue;
                }
                long[] v = f.toLongArray(), fs = v.clone();
                java.util.Arrays.sort(fs);
                long fsum = 0;
                int o33 = 0, o50 = 0, o100 = 0;
                for (long x : v) {
                    fsum += x;
                    o33 += x > 33_333_333L ? 1 : 0;
                    o50 += x > 50_000_000L ? 1 : 0;
                    o100 += x > 100_000_000L ? 1 : 0;
                }
                b.append(String.format(Locale.ROOT, "Frames %s: %d, ms mean %.2f, median %.2f, p95 %.2f, p99 %.2f, p99.9 %.1f, max %.1f; "
                        + "over 33 ms %d, over 50 ms %d, over 100 ms %d%n", ph.name().toLowerCase(Locale.ROOT), v.length, fsum / 1e6 / v.length,
                        pct(fs, 0.5), pct(fs, 0.95), pct(fs, 0.99), pct(fs, 0.999), fs[fs.length - 1] / 1e6, o33, o50, o100));
            }
        }
        if (rememberedAtSave >= 0) {
            b.append(String.format(Locale.ROOT, "Blocks remembered for healing at the save %d ticks in: %d%n", SAVE_AT, rememberedAtSave));
        }
        if (doneSaveNanos >= 0) {
            b.append(String.format(Locale.ROOT, "Save as the blast finished, %d blocks remembered: %.1f ms; region files then %.1f MB%n",
                    rememberedAtDone, doneSaveNanos / 1e6, regionsAtDone / 1048576.0));
        }
        b.append(String.format(Locale.ROOT, "Save at the end: %.1f ms%n", endSaveNanos / 1e6));
        b.append(dataSizes());
        return b.toString();
    }

    /** The size of every region file under {@code root} (bytes; -1 if it cannot be read). */
    private static long regionBytes(Path root) {
        try (var walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".mca")).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return -1;
        }
    }

    /** What the world takes on disk once saved: the mod's own saved data file by file, and the chunks' region files. */
    private static String dataSizes() {
        if (job == null && fired < 0) {
            return "";
        }
        Path root = worldRoot;
        if (root == null || !Files.isDirectory(root)) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        long regions = 0;
        List<String> ours = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                long size = Files.size(p);
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.endsWith(".mca")) {
                    regions += size;
                } else if (rel.contains(GroundBurst.MODID)) {
                    ours.add(String.format(Locale.ROOT, "%s %.1f KB", rel, size / 1024.0));
                }
            }
        } catch (IOException e) {
            return "Sizes on disk: could not be read (" + e.getMessage() + ")\n";
        }
        b.append(String.format(Locale.ROOT, "On disk: region files %.1f MB; the mod's saved data: %s%n", regions / 1048576.0,
                ours.isEmpty() ? "none" : String.join(", ", ours)));
        return b.toString();
    }

    private static double pct(long[] sorted, double q) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.floor(q * (sorted.length - 1)))] / 1e6;
    }
}
