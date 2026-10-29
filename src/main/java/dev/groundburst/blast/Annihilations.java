package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The annihilations under way in each level (DESIGN.md §16.3; an antimatter warhead's, {@code Antimatter}): each a
 * sphere erasing everything it reaches ({@link AnnihilationJob}). They run each tick ahead of the blasts, from the same
 * time and block budget, and clients are told where each is and when it is done ({@link AnnihilationPayloads}).
 */
public final class Annihilations {
    /** Ticks a sphere takes to reach its full size (1.5 s). */
    public static final int EXPAND = 30;
    /** {@code groundburst:annihilated}: what the sphere does to a creature in it. */
    public static final ResourceKey<DamageType> ANNIHILATED = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("annihilated"));

    private static final Map<ServerLevel, List<AnnihilationJob>> RUNNING = new WeakHashMap<>();
    private static final AtomicInteger NET_IDS = new AtomicInteger();

    private Annihilations() {}

    /** How far a sphere of full size {@code radius} has grown {@code age} ticks in: fast at first, slowing to its edge. */
    public static float reach(float radius, double age) {
        double u = Math.max(0, Math.min(1, age / EXPAND));
        return (float) (radius * (1 - (1 - u) * (1 - u)));
    }

    /** A sphere of {@code radius} blocks round the block at {@code at} begins to erase. */
    public static void start(ServerLevel level, Vec3 at, float radius, @Nullable Entity by) {
        AnnihilationJob job = new AnnihilationJob(NET_IDS.incrementAndGet(), level, BlockPos.containing(at), radius, by);
        RUNNING.computeIfAbsent(level, l -> new ArrayList<>()).add(job);
        PacketDistributor.sendToPlayersInDimension(level, new AnnihilationPayloads.Start(job.netId, job.center, radius, EXPAND, 0));
    }

    /** Once a level tick, before the blasts: every annihilation in it works within its share of the budget. */
    static void tick(ServerLevel level) {
        List<AnnihilationJob> jobs = RUNNING.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        int changed = 0;
        for (AnnihilationJob job : List.copyOf(jobs)) {
            long sliceNanos = Math.max(0, BlastManager.nanosLeft()) / jobs.size();
            int sliceBlocks = Math.max(1, BlastManager.blocksLeft() / jobs.size());
            boolean erasing = job.phase() == AnnihilationJob.Phase.ERASE;
            // An annihilation always takes a sliver, so a long blast elsewhere cannot hold it at the edge of its sphere.
            changed += job.step(System.nanoTime() + Math.max(sliceNanos, 1_000_000L), Math.max(sliceBlocks, 2_000));
            if (erasing && job.phase() != AnnihilationJob.Phase.ERASE) {
                PacketDistributor.sendToPlayersInDimension(level, new AnnihilationPayloads.End(job.netId));   // all erased: it fades
            }
            if (job.phase() == AnnihilationJob.Phase.DONE) {
                jobs.remove(job);
            }
        }
        BlastManager.spend(System.nanoTime() - start, changed);
    }

    /** A player arrived in this level: tell them about every sphere still there (and which have finished erasing). */
    static void syncTo(ServerPlayer player) {
        List<AnnihilationJob> jobs = RUNNING.get((ServerLevel) player.level());
        if (jobs == null) {
            return;
        }
        for (AnnihilationJob job : jobs) {
            PacketDistributor.sendToPlayer(player, new AnnihilationPayloads.Start(job.netId, job.center, job.radius, EXPAND, job.age()));
            if (job.phase() != AnnihilationJob.Phase.ERASE) {
                PacketDistributor.sendToPlayer(player, new AnnihilationPayloads.End(job.netId));
            }
        }
    }

    static void forget(ServerLevel level) {
        RUNNING.remove(level);
    }

    /** For GameTests: whether an annihilation is still under way in this level. */
    public static boolean runningIn(ServerLevel level) {
        List<AnnihilationJob> jobs = RUNNING.get(level);
        return jobs != null && !jobs.isEmpty();
    }

    /** For GameTests: blocks erased by the annihilations still under way in this level. */
    public static int erasedIn(ServerLevel level) {
        List<AnnihilationJob> jobs = RUNNING.get(level);
        return jobs == null ? 0 : jobs.stream().mapToInt(j -> j.erased).sum();
    }
}
