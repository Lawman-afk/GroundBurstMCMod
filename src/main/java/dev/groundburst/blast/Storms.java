package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The storms raging in each level (DESIGN.md §16.14; the storm warhead's, {@code Storm}): each a supercell over a
 * whole region, gathering, raging and dying away over its life ({@link StormJob}). While it rages, lightning comes down
 * across the region, aimed at what stands in the open and at whatever stands tallest, each strike chaining through the
 * creatures round it; the wind drives everything round its eye; the strikes set fires, scorch the ground and knock out
 * machines. Clients are told where each is and of every strike ({@link StormPayloads}).
 */
public final class Storms {
    /** At bomb scale 1: the region's radius, and how long it rages (a minute and a half). */
    public static final float RADIUS = 160;
    public static final int LIFE = 1800;
    /** Ticks it takes to gather to full strength, and to die away. */
    public static final int GATHER = 300, FADE = 300;
    /** Strikes a second at full strength over a full-size region (a smaller storm strikes as much less as it is smaller, at least 0.5 s^-1). */
    public static final float STRIKES = 6;
    /** A strike: its damage (vanilla lightning's included), how far it jumps on to the next creature, how many jumps, what each keeps. */
    public static final float STRIKE_DAMAGE = 12, CHAIN_RANGE = 6, CHAIN_KEEP = 0.75f;
    public static final int CHAIN_HOPS = 4;
    /** What a strike does to machines round it: knocked out (an EMP field) this far, this long. */
    public static final float EMP_RADIUS = 4;
    public static final int EMP_TICKS = 100;
    /** How far a lightning rod draws a strike to itself (it takes it safely, and gives a redstone pulse). */
    public static final int ROD_REACH = 32;
    /**
     * Each strike's own blast where it comes down (after the user's look: lightning with an explosive impact, not too
     * strong, a small crater): {@code storm_strike}, a landmine's crater a little wider, its fires round the rim.
     */
    public static final net.minecraft.resources.ResourceKey<BlastProfile> STRIKE_BLAST = Blasts.profile("storm_strike");
    /** How high over where it struck its cloud deck hangs (at bomb scale 1). */
    public static final float CLOUD_HEIGHT = 60;
    /** The most its wind pushes a thing, blocks a tick each tick (at its eyewall, at full strength). */
    public static final double WIND = 0.035;
    /** Where its winds blow hardest, as a share of its radius (the eyewall: calm in the eye, dying away to its edge). */
    static final double EYEWALL = 0.4;

    /**
     * For GameTests: ticks a storm lasts (0: its own), strikes a second at full strength (0: its own rate), and whether
     * its strikes come down without their blasts (so a test can count them on its grass). Read as a storm starts.
     */
    public static int lifeOverride = 0;
    public static double rateOverride = 0;
    public static boolean quietOverride = false;

    private static final Map<ServerLevel, List<StormJob>> RUNNING = new WeakHashMap<>();
    private static final AtomicInteger NET_IDS = new AtomicInteger();

    private Storms() {}

    /** How strong it is {@code age} ticks into a life of {@code life}: gathering, at full strength, dying away (0 to 1). */
    public static float intensity(double age, int life) {
        double gather = Math.min(GATHER, life / 4.0), fade = Math.min(FADE, life / 4.0);
        double u = age < gather ? age / gather : age > life - fade ? (life - age) / fade : 1;
        u = Math.max(0, Math.min(1, u));
        return (float) (u * u * (3 - 2 * u));
    }

    /**
     * Its wind's push on something {@code from} its eye (the vector from the eye to it) in a storm of {@code radius} at
     * {@code intensity}: round the eye (anticlockwise seen from above) and a quarter as much in toward it, strongest at
     * the eyewall, calm in the eye, dying away to its edge.
     */
    public static Vec3 windOn(Vec3 from, float radius, float intensity) {
        double d = Math.sqrt(from.x * from.x + from.z * from.z);
        if (d < 1 || d > radius || intensity <= 0) {
            return Vec3.ZERO;
        }
        double u = d / radius;
        double shape = u < EYEWALL ? u / EYEWALL : (1 - u) / (1 - EYEWALL);
        double a = WIND * intensity * shape;
        double tx = -from.z / d, tz = from.x / d;
        return new Vec3((tx - 0.25 * from.x / d) * a, 0, (tz - 0.25 * from.z / d) * a);
    }

    /** Strikes a second at {@code intensity} over a region of {@code radius} ({@code override}: a GameTest's own rate, or 0). */
    static double strikeRate(float radius, float intensity, double override) {
        if (override > 0) {
            return override * intensity;
        }
        double area = Math.pow(radius / RADIUS, 2);
        return Math.max(0.5, STRIKES * area) * intensity;
    }

    /** A storm gathers over {@code at}: a region of {@code radius} for {@code life} ticks. */
    public static void start(ServerLevel level, Vec3 at, float radius, int life, @Nullable Entity by) {
        StormJob job = new StormJob(NET_IDS.incrementAndGet(), level, at, radius, lifeOverride > 0 ? lifeOverride : life, by, !quietOverride,
                rateOverride);
        RUNNING.computeIfAbsent(level, l -> new ArrayList<>()).add(job);
        PacketDistributor.sendToPlayersInDimension(level, job.startPayload());
    }

    /** Once a level tick: every storm in it rages on. */
    static void tick(ServerLevel level) {
        List<StormJob> jobs = RUNNING.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        for (StormJob job : List.copyOf(jobs)) {
            if (!job.step()) {
                jobs.remove(job);
            }
        }
    }

    /** A player arrived in this level: tell them about every storm raging. */
    static void syncTo(ServerPlayer player) {
        List<StormJob> jobs = RUNNING.get((ServerLevel) player.level());
        if (jobs != null) {
            for (StormJob job : jobs) {
                PacketDistributor.sendToPlayer(player, job.startPayload());
            }
        }
    }

    static void forget(ServerLevel level) {
        RUNNING.remove(level);
    }

    /** For GameTests: whether a storm rages at {@code at} (within a block of its eye). */
    public static boolean ragingAt(ServerLevel level, Vec3 at) {
        List<StormJob> jobs = RUNNING.get(level);
        return jobs != null && jobs.stream().anyMatch(j -> j.center.distanceTo(at) < 1);
    }

    /** For GameTests: the storm at {@code at} strikes {@code target} now (its chain and all). */
    public static void strikeForTest(ServerLevel level, Vec3 at, net.minecraft.world.entity.LivingEntity target) {
        List<StormJob> jobs = RUNNING.get(level);
        if (jobs != null) {
            jobs.stream().filter(j -> j.center.distanceTo(at) < 1).findFirst().ifPresent(j -> j.strikeForTest(target));
        }
    }

    /** For GameTests: the storm at {@code at} strikes the ground at {@code spot} (the air over its surface) now. */
    public static void strikeForTest(ServerLevel level, Vec3 at, net.minecraft.core.BlockPos spot) {
        List<StormJob> jobs = RUNNING.get(level);
        if (jobs != null) {
            jobs.stream().filter(j -> j.center.distanceTo(at) < 1).findFirst().ifPresent(j -> j.strikeForTest(spot));
        }
    }

    /** For GameTests: the strikes of the storm at {@code at} so far (-1: none raging there). */
    public static int strikesAt(ServerLevel level, Vec3 at) {
        List<StormJob> jobs = RUNNING.get(level);
        return jobs == null ? -1 : jobs.stream().filter(j -> j.center.distanceTo(at) < 1).mapToInt(StormJob::strikes).findFirst().orElse(-1);
    }
}
