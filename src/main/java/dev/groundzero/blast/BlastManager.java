package dev.groundzero.blast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.groundzero.config.GZServerConfig;
import net.minecraft.server.level.ServerLevel;

/**
 * Runs a level's blasts (DESIGN.md §11.3). Every server tick all blasts together get a time budget (default 12 ms)
 * and a block-change budget (default 60 000), shared out evenly between the running ones; a busy server simply sees
 * the fronts move more slowly, it never freezes. Further detonations past the concurrent limit wait in a queue.
 * Instant blasts (small charges) run to completion where they go off, outside the budget.
 */
public final class BlastManager {
    private static final Map<ServerLevel, BlastManager> BY_LEVEL = new WeakHashMap<>();
    /** What is left of this server tick's budget, across every level. */
    private static long nanosLeft;
    private static int blocksLeft;

    private final ServerLevel level;
    private final List<BlastJob> running = new ArrayList<>();
    private final ArrayDeque<BlastJob> queued = new ArrayDeque<>();

    private BlastManager(ServerLevel level) {
        this.level = level;
    }

    public static BlastManager of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, BlastManager::new);
    }

    /** Start of a server tick: a fresh budget. */
    static void resetBudget() {
        nanosLeft = GZServerConfig.MS_BUDGET_PER_TICK.get() * 1_000_000L;
        blocksLeft = GZServerConfig.MAX_BLOCK_CHANGES_PER_TICK.get();
    }

    static void forget(ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    /** Blasts running or waiting in this level. */
    public int active() {
        return this.running.size() + this.queued.size();
    }

    void add(BlastJob job) {
        if (job.profile.isInstant()) {
            while (job.phase() != BlastJob.Phase.DONE) {
                job.step(Long.MAX_VALUE, Integer.MAX_VALUE);
            }
            return;
        }
        this.queued.add(job);
    }

    void tick() {
        int limit = GZServerConfig.MAX_CONCURRENT_JOBS.get();
        while (this.running.size() < limit && !this.queued.isEmpty()) {
            this.running.add(this.queued.poll());
        }
        if (this.running.isEmpty() || nanosLeft <= 0 || blocksLeft <= 0) {
            return;
        }
        long start = System.nanoTime();
        long sliceNanos = nanosLeft / this.running.size();
        int sliceBlocks = Math.max(1, blocksLeft / this.running.size());
        for (BlastJob job : List.copyOf(this.running)) {
            long jobStart = System.nanoTime();
            blocksLeft -= job.step(jobStart + sliceNanos, sliceBlocks);
            if (job.phase() == BlastJob.Phase.DONE) {
                this.running.remove(job);
            }
        }
        nanosLeft -= System.nanoTime() - start;
    }
}
