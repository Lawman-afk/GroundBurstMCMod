package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The deep freezes running in each level (DESIGN.md §16.16; the cryogenic warhead's, {@code Cryo}): each a white front of
 * frozen air rolling out over the land from where its Cryo ICBM struck, freezing what it passes ({@link FreezeJob}),
 * then a cold snap over the region it froze. Clients are told where each is, how far its front has run, each spire of ice
 * it raises, and its end ({@link FreezePayloads}).
 */
public final class Freezes {
    /** At bomb scale 1: how far its front runs, blocks, and the share of that its heart is (40: nothing in it lives). */
    public static final float RADIUS = 128, CORE_SHARE = 0.3125f;
    /** Ticks its front takes to run out at full size (eight seconds: fast at first, slowing as it spreads). */
    public static final int FRONT_TICKS = 160;
    /** Ticks its cold snap holds the region, from the moment it struck (three minutes, the last third easing off). */
    public static final int LIFE = 3600;
    /** How far above where it struck its cold air climbs at full size (it pools in low ground: hilltops are spared). */
    public static final float CLIMB = 40;
    /** {@code groundburst:flash_frozen}: what its heart does to a creature in it. */
    public static final ResourceKey<DamageType> FLASH_FROZEN = ResourceKey.create(Registries.DAMAGE_TYPE, GroundBurst.id("flash_frozen"));

    /** For GameTests: ticks a cold snap lasts (0: its own). Read as a freeze starts. */
    public static int lifeOverride = 0;

    private static final Map<ServerLevel, List<FreezeJob>> RUNNING = new WeakHashMap<>();
    private static final AtomicInteger NET_IDS = new AtomicInteger();

    private Freezes() {}

    /** Its heart's radius for a front of {@code radius}. */
    public static float core(float radius) {
        return radius * CORE_SHARE;
    }

    /** Ticks a front of {@code radius} takes to run out (a smaller one is quicker, as the square root of its size). */
    public static int frontTicks(float radius) {
        return Math.max(20, Math.round(FRONT_TICKS * (float) Math.sqrt(radius / RADIUS)));
    }

    /** How far its front has run {@code age} ticks into a run of {@code ticks}: fast at first, slowing to its edge. */
    public static float front(float radius, int ticks, double age) {
        double u = Math.max(0, Math.min(1, age / ticks));
        return (float) (radius * (1 - (1 - u) * (1 - u)));
    }

    /** How far its cold air climbs over where it struck, for a front of {@code radius}. */
    public static float climb(float radius) {
        return Math.max(6, CLIMB * radius / RADIUS);
    }

    /** How hard its cold snap bites {@code age} ticks into a life of {@code life}: full for two thirds, then easing off (0 to 1). */
    public static float cold(double age, int life) {
        double ease = life / 3.0;
        double u = age < life - ease ? 1 : Math.max(0, (life - age) / ease);
        return (float) (u * u * (3 - 2 * u));
    }

    /** A deep freeze from {@code at}: its front {@code radius} round, its cold snap {@code life} ticks. */
    public static void start(ServerLevel level, Vec3 at, float radius, int life, @Nullable Entity by) {
        FreezeJob job = new FreezeJob(NET_IDS.incrementAndGet(), level, at, radius, lifeOverride > 0 ? lifeOverride : life, by);
        RUNNING.computeIfAbsent(level, l -> new ArrayList<>()).add(job);
        PacketDistributor.sendToPlayersInDimension(level, job.startPayload());
    }

    /** Once a level tick: every freeze in it runs on. */
    static void tick(ServerLevel level) {
        List<FreezeJob> jobs = RUNNING.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        for (FreezeJob job : List.copyOf(jobs)) {
            if (!job.step()) {
                jobs.remove(job);
            }
        }
    }

    /** A player arrived in this level: tell them about every freeze running. */
    static void syncTo(ServerPlayer player) {
        List<FreezeJob> jobs = RUNNING.get((ServerLevel) player.level());
        if (jobs != null) {
            for (FreezeJob job : jobs) {
                PacketDistributor.sendToPlayer(player, job.startPayload());
            }
        }
    }

    static void forget(ServerLevel level) {
        List<FreezeJob> jobs = RUNNING.remove(level);
        if (jobs != null) {
            jobs.forEach(FreezeJob::releaseAll);
        }
    }

    /** For GameTests: whether a freeze is running from {@code at} (within a block of where it struck). */
    public static boolean runningAt(ServerLevel level, Vec3 at) {
        List<FreezeJob> jobs = RUNNING.get(level);
        return jobs != null && jobs.stream().anyMatch(j -> j.center.distanceTo(at) < 1);
    }

    /** For GameTests: whether the front of the freeze from {@code at} has run all the way out and its spires have grown. */
    public static boolean settledAt(ServerLevel level, Vec3 at) {
        List<FreezeJob> jobs = RUNNING.get(level);
        return jobs != null && jobs.stream().anyMatch(j -> j.center.distanceTo(at) < 1 && j.settled());
    }
}
