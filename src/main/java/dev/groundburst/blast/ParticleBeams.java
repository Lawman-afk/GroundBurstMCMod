package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.phys.Vec3;
import dev.groundburst.legacy.net.PacketDistributor;

/**
 * The Particle Uplink Cannon's beams running in each level (DESIGN.md §19.6; the cannon is
 * {@code ParticleCannonBlockEntity}): each goes up out of its cannon's pit to the satellite and, {@link #UPLINK} ticks
 * later, comes down from orbit on its target and burns there for {@link #BURN} ticks, turning toward wherever it is
 * aimed at {@link #SPEED} ({@link ParticleBeamJob}). Clients are told where each is ({@link ParticleBeamPayloads}).
 */
public final class ParticleBeams {
    /** Ticks from the beam leaving the cannon's pit until it comes down on the target (the game's two seconds). */
    public static final int UPLINK = 40;
    /** Ticks it burns on the ground: 48 seconds (the user's: four times the game's twelve). */
    public static final int BURN = 960;
    /** How fast it turns toward a new aim, blocks a tick (three a second). */
    public static final double SPEED = 0.15;
    /** Its core (everything in it is vaporised), how far round it the ground melts, how far it sets fires: blocks. */
    public static final float CORE = 2.5f, MELT = 4.5f, FIRE = 7f;
    /** Deepest its trench goes where it lingers, blocks. */
    public static final int MAX_DEPTH = 8;
    /** {@code groundburst:particle_beam}: what its core does to whatever is in it. */
    public static final ResourceKey<DamageType> PARTICLE_BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("particle_beam"));

    /** For GameTests: the cannons whose beams leave the ground and everything on it alone (they only move and are seen). */
    public static final java.util.Set<BlockPos> DRY = new java.util.HashSet<>();
    /** For GameTests: ticks a beam burns (0: its own). Read as it starts. */
    public static int burnOverride = 0;

    private static final Map<ServerLevel, List<ParticleBeamJob>> RUNNING = new WeakHashMap<>();
    private static final AtomicInteger NET_IDS = new AtomicInteger();

    private ParticleBeams() {}

    /**
     * A beam from the cannon at {@code cannon}: up out of its pit at {@code from}, then down on {@code aim}; steered by
     * {@code by}'s Laser Designator while they lase, or by a new aim of the cannon's. It burns while its cannon keeps
     * firing it. Returns its id.
     */
    public static int start(ServerLevel level, BlockPos cannon, Vec3 from, BlockPos aim, @Nullable UUID by) {
        return add(level, new ParticleBeamJob(NET_IDS.incrementAndGet(), level, cannon, from, aim, by, DRY.contains(cannon)));
    }

    /**
     * A beam with no cannon (GameTests, the photographers): it comes straight down on {@code aim} and burns its time;
     * a {@code dry} one leaves the ground and everything on it alone.
     */
    public static int strike(ServerLevel level, BlockPos aim, @Nullable UUID by, boolean dry) {
        return add(level, new ParticleBeamJob(NET_IDS.incrementAndGet(), level, null, null, aim, by, dry));
    }

    private static int add(ServerLevel level, ParticleBeamJob job) {
        RUNNING.computeIfAbsent(level, l -> new ArrayList<>()).add(job);
        PacketDistributor.sendToPlayersInDimension(level, job.startPayload());
        return job.netId;
    }

    /** Once a level tick: every beam in it burns on. */
    static void tick(ServerLevel level) {
        List<ParticleBeamJob> jobs = RUNNING.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        for (ParticleBeamJob job : List.copyOf(jobs)) {
            if (!job.step()) {
                jobs.remove(job);
            }
        }
    }

    /** A player arrived in this level: tell them about every beam burning. */
    static void syncTo(ServerPlayer player) {
        List<ParticleBeamJob> jobs = RUNNING.get((ServerLevel) player.level());
        if (jobs != null) {
            for (ParticleBeamJob job : jobs) {
                PacketDistributor.sendToPlayer(player, job.startPayload());
            }
        }
    }

    static void forget(ServerLevel level) {
        List<ParticleBeamJob> jobs = RUNNING.remove(level);
        if (jobs != null) {
            jobs.forEach(ParticleBeamJob::releaseAll);
        }
    }

    /** Whether beam {@code id} is still burning (or on its way down). */
    public static boolean running(ServerLevel level, int id) {
        List<ParticleBeamJob> jobs = RUNNING.get(level);
        return jobs != null && jobs.stream().anyMatch(j -> j.netId == id);
    }

    /** Where beam {@code id} is now (on the ground where it touches; null: it is gone). */
    public static @Nullable Vec3 position(ServerLevel level, int id) {
        List<ParticleBeamJob> jobs = RUNNING.get(level);
        if (jobs != null) {
            for (ParticleBeamJob j : jobs) {
                if (j.netId == id) {
                    return j.foot();
                }
            }
        }
        return null;
    }
}
