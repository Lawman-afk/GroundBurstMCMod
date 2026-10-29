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
 * The singularities open in each level (DESIGN.md §16.2; the singularity warhead's, {@code Singularity}): each a black
 * hole growing, drawing everything in and tearing the ground loose, then collapsing ({@link SingularityJob}). They run
 * each tick ahead of the blasts, from the same budget; clients are told where each is, what it tears loose and when it
 * collapses ({@link SingularityPayloads}).
 */
public final class Singularities {
    /** At full size (times the bomb scale): its event horizon, how far it pulls, how far it tears blocks loose. */
    public static final float HORIZON = 10, PULL = 48, RIP = 32;
    /** Ticks it grows before it collapses (20 s). */
    public static final int GROW = 400;
    /** The most blocks a full-size one tears loose. */
    static final int MAX_TORN = 80_000;
    /** Its collapse's blast profile (crater 16 at full size). */
    public static final String COLLAPSE_PROFILE = "singularity_collapse";
    /** {@code groundburst:spaghettified}: what the horizon does to a creature. */
    public static final ResourceKey<DamageType> SPAGHETTIFIED = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("spaghettified"));
    /** For GameTests: ticks a singularity grows (0: its own {@link #GROW}, or a breach's). */
    public static int growOverride = 0;

    private static final Map<ServerLevel, List<SingularityJob>> RUNNING = new WeakHashMap<>();
    private static final AtomicInteger NET_IDS = new AtomicInteger();

    private Singularities() {}

    /**
     * The event horizon's radius {@code age} ticks into its {@code grow}: a pinpoint at first, growing fast and then
     * steadily to {@code horizon} (the server swallows by it and the client draws it by it).
     */
    public static float horizon(float horizon, double age, int grow) {
        double u = Math.max(0, Math.min(1, age / Math.max(1, grow)));
        return (float) (horizon * (0.08 + 0.92 * Math.pow(u, 0.75)));
    }

    /** How hard it pulls {@code u} of the way through its growth: weak as it opens, ten times as hard at the end. */
    public static double strength(double u) {
        return 0.6 + 5.4 * Math.pow(Math.max(0, Math.min(1, u)), 1.5);
    }

    /**
     * The pull on something {@code to} from it (the vector to the hole's middle) of a singularity pulling from
     * {@code pull} blocks at {@code strength}: an inverse-square pull toward it, at most 0.6 blocks a tick each tick, and a
     * third of that round it (the way its disc turns). The server pulls everything but players by it; each player's own
     * client pulls its player by it (a client moves its own player), so the two agree.
     */
    public static Vec3 pullOn(Vec3 to, float pull, double strength) {
        double d = to.length();
        if (d < 1e-3) {
            return Vec3.ZERO;
        }
        double k = 5.76 * Math.pow(pull / PULL, 3);
        double a = Math.min(0.6, k * strength / (d * d));
        Vec3 in = to.scale(1 / d);
        Vec3 swirl = new Vec3(-in.z, 0, in.x);
        double sl = swirl.length();
        return in.scale(a).add(sl > 1e-4 ? swirl.scale(0.35 * a / sl) : Vec3.ZERO);
    }

    /** A singularity opens at the block at {@code at}. */
    public static void start(ServerLevel level, Vec3 at, float horizon, float pull, float rip, int grow, float scale, @Nullable Entity by) {
        int ticks = growOverride > 0 ? growOverride : grow;
        SingularityJob job = new SingularityJob(NET_IDS.incrementAndGet(), level, BlockPos.containing(at), horizon, pull, rip, ticks, scale, by);
        RUNNING.computeIfAbsent(level, l -> new ArrayList<>()).add(job);
        PacketDistributor.sendToPlayersInDimension(level, new SingularityPayloads.Start(job.netId, job.center, horizon, pull, rip, job.grow, 0));
    }

    /** Once a level tick, before the blasts: every singularity in it works within its share of the budget. */
    static void tick(ServerLevel level) {
        List<SingularityJob> jobs = RUNNING.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        int changed = 0;
        for (SingularityJob job : List.copyOf(jobs)) {
            long slice = Math.max(1_000_000L, Math.max(0, BlastManager.nanosLeft()) / jobs.size());
            int blocks = Math.max(200, BlastManager.blocksLeft() / jobs.size());
            changed += job.step(System.nanoTime() + slice, blocks);
            if (job.phase() == SingularityJob.Phase.DONE) {
                jobs.remove(job);
            }
        }
        BlastManager.spend(System.nanoTime() - start, changed);
    }

    /** A player arrived in this level: tell them about every singularity still growing. */
    static void syncTo(ServerPlayer player) {
        List<SingularityJob> jobs = RUNNING.get((ServerLevel) player.level());
        if (jobs == null) {
            return;
        }
        for (SingularityJob job : jobs) {
            if (job.phase() == SingularityJob.Phase.GROW) {
                PacketDistributor.sendToPlayer(player, new SingularityPayloads.Start(job.netId, job.center, job.horizon, job.pull, job.rip,
                        job.grow, job.age()));
            }
        }
    }

    static void forget(ServerLevel level) {
        RUNNING.remove(level);
    }

    /** For GameTests: whether a singularity is still open (or letting its chunks go) in this level. */
    public static boolean runningIn(ServerLevel level) {
        List<SingularityJob> jobs = RUNNING.get(level);
        return jobs != null && !jobs.isEmpty();
    }

    /** For GameTests: blocks torn loose by the singularities still open in this level. */
    public static int tornIn(ServerLevel level) {
        List<SingularityJob> jobs = RUNNING.get(level);
        return jobs == null ? 0 : jobs.stream().mapToInt(SingularityJob::torn).sum();
    }
}
