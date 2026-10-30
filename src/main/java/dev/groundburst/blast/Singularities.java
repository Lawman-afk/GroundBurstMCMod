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
 * hole that feeds and grows, drawing everything in and tearing the ground loose, then collapses ({@link SingularityJob}).
 * Everything about its size follows from one number, its <em>reach</em>: how far out it tears earth loose. The reach
 * grows two ways: the hole opening by itself over its life (to {@link #SEED} blocks by the end, fed nothing), and by
 * what it has swallowed ({@link #FEED} times the cube root of the blocks' worth it has eaten), so a hole on open
 * ground grows to about {@link #TYPICAL_REACH}, one in a mountainside or the sea well past that, one in the open air
 * hardly at all. At the end of its life it implodes ({@link #IMPLODE}): no blast, its pull at its strongest as its
 * horizon shrinks to a point. They run each tick ahead of the blasts, from the same budget; clients are told where each
 * is, how far it reaches as it grows, what it tears loose and when it implodes ({@link SingularityPayloads}).
 */
public final class Singularities {
    /** At bomb scale 1: the reach its opening alone gives it by the end of its life, and the most it can reach. */
    public static final float SEED = 26, MAX_REACH = 110;
    /** What its swallowing adds to its reach: this times the cube root of the blocks' worth it has eaten. */
    public static final float FEED = 0.56f;
    /** Its reach, at bomb scale 1, fed by open ground to the end (the figures the screens and the manual give). */
    public static final float TYPICAL_REACH = 74;
    /** Its event horizon (everything inside goes) and how far it pulls things in, as shares of its reach. */
    public static final float HORIZON_SHARE = 0.3f, PULL_SHARE = 1.5f;
    /** Ticks it lives before it collapses (a minute). */
    public static final int LIFE = 1200;
    /**
     * How steeply its pull on a block falls with distance (a tidal pull): a block at distance d is torn loose once
     * (reach / d)^TIDE beats its hold, so earth (hold 0.8) goes a little past the reach, stone (1.8) to nine tenths of it,
     * obsidian (50) only to half.
     */
    static final double TIDE = 6;
    /** Nothing on foot outruns it from within this share of its reach, where its pull reaches the pull a sprint cannot beat. */
    static final double ESCAPE_SHARE = 0.45, ESCAPE_PULL = 0.12;
    /**
     * Ticks its collapse takes (after the user's look, DESIGN.md §16.12: an implosion, not a blast): it stops eating and
     * pulls ever harder as its horizon shrinks to a point, then is gone.
     */
    public static final int IMPLODE = 40;
    /** {@code groundburst:spaghettified}: what the horizon does to a creature. */
    public static final ResourceKey<DamageType> SPAGHETTIFIED = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("spaghettified"));
    /** For GameTests: ticks a singularity lives (0: its own {@link #LIFE}, or a breach's). */
    public static int lifeOverride = 0;

    private static final Map<ServerLevel, List<SingularityJob>> RUNNING = new WeakHashMap<>();
    private static final AtomicInteger NET_IDS = new AtomicInteger();

    private Singularities() {}

    /**
     * Its reach {@code age} ticks into a life of {@code life}, having swallowed {@code mass} blocks' worth: its opening
     * ({@code seed} by the end, quickly at first) and what it has eaten ({@code feed} times the cube root), at most {@code max}.
     */
    public static float reach(float seed, float feed, double mass, double age, int life, float max) {
        double u = Math.max(0, Math.min(1, age / Math.max(1, life)));
        return (float) Math.min(max, seed * Math.sqrt(u) + feed * Math.cbrt(Math.max(0, mass)));
    }

    /** Its event horizon's radius at a reach: everything inside goes. */
    public static float horizon(float reach) {
        return HORIZON_SHARE * reach;
    }

    /** How much harder it pulls {@code u} of the way through its implosion (0 to 1): up to seven times. */
    public static double implodePull(double u) {
        return 1 + 6 * Math.max(0, Math.min(1, u));
    }

    /** Its horizon {@code u} of the way through its implosion: shrinking slowly, then all at once, to a point. */
    public static float implodeHorizon(float reach, double u) {
        double k = Math.max(0, Math.min(1, u));
        return (float) (horizon(reach) * (1 - k * k * k));
    }

    /** How far out it pulls things in at a reach. */
    public static float pullRadius(float reach) {
        return PULL_SHARE * reach + 2;
    }

    /** Its pull's strength at a reach: the pull at distance d is this over d squared (blocks a tick, each tick). */
    public static double gm(float reach) {
        double escape = ESCAPE_SHARE * reach;
        return ESCAPE_PULL * escape * escape;
    }

    /** The farthest a block of this hold is torn loose from it at a reach (the tidal pull beating the hold). */
    static double tearsTo(float reach, double hold) {
        return reach * Math.pow(Math.max(hold, 1e-3), -1 / TIDE);
    }

    /** How far it tears stone loose at a reach: the hole it digs in rock. */
    public static float stoneReach(float reach) {
        return (float) tearsTo(reach, 1.8);
    }

    /**
     * The pull on something {@code to} from it (the vector to the hole's middle) of a singularity of pull strength
     * {@code gm}: an inverse-square pull toward it, at most 0.6 blocks a tick each tick, and a third of that round it
     * (the way its disc turns). The server pulls everything but players by it; each player's own client pulls its player
     * by it (a client moves its own player), so the two agree.
     */
    public static Vec3 pullOn(Vec3 to, double gm) {
        double d = to.length();
        if (d < 1e-3) {
            return Vec3.ZERO;
        }
        double a = Math.min(0.6, gm / (d * d));
        Vec3 in = to.scale(1 / d);
        Vec3 swirl = new Vec3(-in.z, 0, in.x);
        double sl = swirl.length();
        return in.scale(a).add(sl > 1e-4 ? swirl.scale(0.35 * a / sl) : Vec3.ZERO);
    }

    /**
     * A singularity opens at the block at {@code at}: opening to {@code seed} over {@code life} ticks, growing by
     * {@code feed} times the cube root of what it eats, at most to {@code max}.
     */
    public static void start(ServerLevel level, Vec3 at, float seed, float feed, float max, int life, @Nullable Entity by) {
        int ticks = lifeOverride > 0 ? lifeOverride : life;
        SingularityJob job = new SingularityJob(NET_IDS.incrementAndGet(), level, BlockPos.containing(at), seed, feed, max, ticks, by);
        RUNNING.computeIfAbsent(level, l -> new ArrayList<>()).add(job);
        PacketDistributor.sendToPlayersInDimension(level, new SingularityPayloads.Start(job.netId, job.center, job.reach(), job.life, 0));
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
                PacketDistributor.sendToPlayer(player, new SingularityPayloads.Start(job.netId, job.center, job.reach(), job.life, job.age()));
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

    /** For GameTests: the reach of the singularity still growing at {@code at} (within a block of its middle; 0: none). */
    public static float reachAt(ServerLevel level, Vec3 at) {
        List<SingularityJob> jobs = RUNNING.get(level);
        return jobs == null ? 0 : (float) jobs.stream().filter(j -> j.phase() == SingularityJob.Phase.GROW && j.center.distanceTo(at) < 1)
                .mapToDouble(SingularityJob::reach).max().orElse(0);
    }
}
