package dev.groundburst.blast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.groundburst.GroundBurst;
import dev.groundburst.config.GBServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Runs a level's blasts (DESIGN.md §11.3, §11.5). Every server tick all blasts together get a time budget (default
 * 12 ms) and a block-change budget (default 60 000), shared out evenly between the running ones; a busy server simply
 * sees the fronts move more slowly, it never freezes. Further detonations past the concurrent limit wait in a queue.
 * Instant blasts (small charges) run to completion where they go off, outside the budget.
 *
 * <p>It is the level's saved data ({@code data/groundburst/blasts.dat}): running and queued blasts are written with the
 * world and resume when it loads. It also keeps clients told ({@link BlastPayloads}).
 */
public final class BlastManager extends SavedData {
    static final SavedDataType<BlastManager> TYPE = new SavedDataType<>(GroundBurst.id("blasts"), BlastManager::new, BlastManager::codec);
    private static final Map<ServerLevel, BlastManager> BY_LEVEL = new WeakHashMap<>();
    private static final AtomicInteger NET_IDS = new AtomicInteger();
    /** Ticks between front updates to clients. */
    private static final int FRONT_INTERVAL = 4;
    /** What is left of this server tick's budget, across every level. */
    private static long nanosLeft;
    private static int blocksLeft;
    /** Time the blasts took this server tick, across every level (for {@link BlastBench}). */
    static long nanosUsed;

    private final @Nullable ServerLevel level;
    private final List<BlastJob> running = new ArrayList<>();
    private final ArrayDeque<BlastJob> queued = new ArrayDeque<>();

    private BlastManager(@Nullable ServerLevel level) {
        this.level = level;
    }

    private static Codec<BlastManager> codec(@Nullable ServerLevel level) {
        return BlastJob.State.CODEC.listOf().fieldOf("jobs").codec().xmap(states -> {
            BlastManager m = new BlastManager(level);
            if (level != null) {
                for (BlastJob.State state : states) {
                    BlastJob job = new BlastJob(level, state);
                    job.netId = NET_IDS.incrementAndGet();
                    m.queued.add(job);
                }
            }
            return m;
        }, m -> {
            List<BlastJob.State> out = new ArrayList<>();
            m.running.forEach(job -> out.add(job.save()));
            m.queued.forEach(job -> out.add(job.save()));
            return out;
        });
    }

    public static BlastManager of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(TYPE));
    }

    /** Start of a server tick: a fresh budget. */
    static void resetBudget() {
        nanosLeft = GBServerConfig.MS_BUDGET_PER_TICK.get() * 1_000_000L;
        blocksLeft = GBServerConfig.MAX_BLOCK_CHANGES_PER_TICK.get();
        nanosUsed = 0;
    }

    /** What is left of this server tick's time budget, ns (the annihilations draw on it too: {@link Annihilations}). */
    static long nanosLeft() {
        return nanosLeft;
    }

    /** What is left of this server tick's block-change budget. */
    static int blocksLeft() {
        return blocksLeft;
    }

    /** Takes what an annihilation used off this server tick's budget. */
    static void spend(long nanos, int blocks) {
        nanosLeft -= nanos;
        nanosUsed += nanos;
        blocksLeft -= blocks;
    }

    static void forget(ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    /** Blasts running or waiting in this level. */
    public int active() {
        return this.running.size() + this.queued.size();
    }

    /**
     * Whether (x, z) lies within the terrain work of a blast running or waiting here: its crater may still be dug out
     * under it (a plume's ash must not settle there yet, or it is left in the air).
     */
    public boolean forming(double x, double z) {
        for (BlastJob job : this.running) {
            if (job.covers(x, z)) {
                return true;
            }
        }
        for (BlastJob job : this.queued) {
            if (job.covers(x, z)) {
                return true;
            }
        }
        return false;
    }

    /** The blasts running now. */
    List<? extends Blast> running() {
        return List.copyOf(this.running);
    }

    void add(BlastJob job) {
        job.netId = NET_IDS.incrementAndGet();
        broadcast(BlastPayloads.start(job));
        if (job.profile.isInstant()) {
            while (job.phase() != BlastJob.Phase.DONE) {
                job.step(Long.MAX_VALUE, Integer.MAX_VALUE);
            }
            broadcast(new BlastPayloads.End(job.netId));
            return;
        }
        this.queued.add(job);
        setDirty();
    }

    void tick() {
        int limit = GBServerConfig.MAX_CONCURRENT_JOBS.get();
        while (this.running.size() < limit && !this.queued.isEmpty()) {
            this.running.add(this.queued.poll());
        }
        if (this.running.isEmpty() || this.level == null) {
            return;
        }
        setDirty();
        if (nanosLeft > 0 && blocksLeft > 0) {
            long start = System.nanoTime();
            long sliceNanos = nanosLeft / this.running.size();
            int sliceBlocks = Math.max(1, blocksLeft / this.running.size());
            for (BlastJob job : List.copyOf(this.running)) {
                blocksLeft -= job.step(System.nanoTime() + sliceNanos, sliceBlocks);
                if (job.phase() == BlastJob.Phase.DONE) {
                    this.running.remove(job);
                    broadcast(new BlastPayloads.End(job.netId));
                }
            }
            long used = System.nanoTime() - start;
            nanosLeft -= used;
            nanosUsed += used;
        }
        if (this.level.getGameTime() % FRONT_INTERVAL == 0) {
            for (BlastJob job : this.running) {
                broadcast(new BlastPayloads.Front(job.netId, job.radius()));
            }
        }
    }

    /**
     * What a reload would resume: this level's blasts written out the way the world saves them and read back (used by
     * the GameTests to check that saving keeps a blast's progress).
     */
    public List<Blast> savedCopy() {
        Codec<BlastManager> c = codec(this.level);
        net.minecraft.nbt.Tag tag = c.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, this).getOrThrow();
        BlastManager copy = c.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
        return List.copyOf(copy.queued);
    }

    /** A player arrived in this level: tell them about every blast still going. */
    void syncTo(ServerPlayer player) {
        for (BlastJob job : this.running) {
            PacketDistributor.sendToPlayer(player, BlastPayloads.start(job));
            PacketDistributor.sendToPlayer(player, new BlastPayloads.Front(job.netId, job.radius()));
        }
        for (BlastJob job : this.queued) {
            PacketDistributor.sendToPlayer(player, BlastPayloads.start(job));
        }
    }

    private void broadcast(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (this.level != null) {
            PacketDistributor.sendToPlayersInDimension(this.level, payload);
        }
    }
}
