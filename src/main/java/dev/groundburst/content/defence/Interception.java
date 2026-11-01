package dev.groundburst.content.defence;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.RadarArrayBlockEntity;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Missile defence's reckoning (DESIGN.md §18.2, §18.5): which missiles a Radar Array is tracking, where an interceptor
 * can meet a missile in time, and how likely it is to kill it there; the Interceptor Battery's figures and the
 * Interceptor Complex's (two and a half times the reach, surer interceptors, bigger volleys).
 */
public final class Interception {
    /** An interceptor's speed (blocks a tick), and how much longer than the straight line its climbing path runs. */
    public static final double SPEED = 5.0, PATH_FACTOR = 1.15;
    /** How far a battery engages: a missile a Radar Array tracks, or one only its own seeker sees. */
    public static final double RADAR_RANGE = 256, SEEKER_RANGE = 96;
    /** How far the Interceptor Complex engages (two and a half times a battery): a missile a Radar Array tracks, or one its own radar alone sees. */
    public static final double COMPLEX_RADAR_RANGE = RADAR_RANGE * 2.5, COMPLEX_OWN_RANGE = SEEKER_RANGE * 2.5;
    /** An interceptor's best chance of a kill: a battery's, and the complex's (its fire-control radar guides them all the way in). */
    public static final double BATTERY_KILL = 0.8, COMPLEX_KILL = 0.95;
    /** Never meet a missile less than this high over the battery (a burst that low is no defence). */
    public static final double MIN_HEIGHT = 12;
    /** Ticks since a Radar Array last tracked for it still to count as tracking. */
    private static final long RADAR_GRACE = 40;
    /** For GameTests: the kill chance of every interceptor launched while it is set (negative: their own). Read at launch. */
    public static double killOverride = -1;

    /** The Radar Arrays working in each level, and the game time each last tracked. */
    private static final Map<ServerLevel, Map<BlockPos, Long>> RADARS = new WeakHashMap<>();

    private Interception() {}

    /** A Radar Array tracking this tick (from its tick, while it is formed and powered). */
    public static void radarWorking(ServerLevel level, BlockPos pos) {
        RADARS.computeIfAbsent(level, l -> new HashMap<>()).put(pos.immutable(), level.getGameTime());
    }

    /** A Radar Array gone dark (unformed, unpowered or broken). */
    public static void radarStopped(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Long> m = RADARS.get(level);
        if (m != null) {
            m.remove(pos);
        }
    }

    static void forget(ServerLevel level) {
        RADARS.remove(level);
    }

    /** Whether a working Radar Array tracks a missile at {@code at} (it is within the array's range). */
    public static boolean tracked(ServerLevel level, Vec3 at) {
        Map<BlockPos, Long> m = RADARS.get(level);
        if (m == null) {
            return false;
        }
        long now = level.getGameTime();
        for (Map.Entry<BlockPos, Long> e : m.entrySet()) {
            BlockPos p = e.getKey();
            if (now - e.getValue() <= RADAR_GRACE && Math.abs(p.getX() + 0.5 - at.x) <= RadarArrayBlockEntity.RANGE
                    && Math.abs(p.getZ() + 0.5 - at.z) <= RadarArrayBlockEntity.RANGE) {
                return true;
            }
        }
        return false;
    }

    /** How a missile's class weighs on the kill chance: the bigger, the harder to kill. */
    public static double classFactor(SizeClass body) {
        return switch (body) {
            case S -> 1.0;
            case M -> 0.9;
            case L -> 0.75;
            case XL -> 0.6;
            default -> 0.5;                                    // intercontinental
        };
    }

    /**
     * The chance one of a battery's interceptors kills the missile it meets: 0.8, times its class's factor, times 0.6 if
     * no Radar Array tracks it (the battery's own seeker alone), times 0.85 if it is coming back down hot from high up,
     * times 0.5 for a superweapon's missile (a SCUD, China's Nuclear Missile).
     */
    public static double killChance(SizeClass body, boolean tracked, boolean reentering, boolean superweapon) {
        return killChance(BATTERY_KILL, body, tracked, reentering, superweapon);
    }

    /** As {@link #killChance(SizeClass, boolean, boolean, boolean)} from a best chance of {@code best}. */
    public static double killChance(double best, SizeClass body, boolean tracked, boolean reentering, boolean superweapon) {
        return best * classFactor(body) * (tracked ? 1 : 0.6) * (reentering ? 0.85 : 1) * (superweapon ? 0.5 : 1);
    }

    /** A battery's kill chance against a flight as it is now. */
    public static double killChance(Flights.Flight flight, boolean tracked) {
        return killChance(BATTERY_KILL, flight, tracked);
    }

    /** A MIRV's re-entry vehicle's weight on the kill chance (DESIGN.md §17.14): small, hot and fast, the hardest of all. */
    public static final double RV_FACTOR = 0.45;

    /** The kill chance against a flight as it is now, from a best chance of {@code best}: an RV (or a decoy) at {@link #RV_FACTOR}. */
    public static double killChance(double best, Flights.Flight flight, boolean tracked) {
        ChargeKind kind = AssemblyStats.kindOf(flight.warhead());
        boolean superweapon = kind == ChargeKind.SCUD_STORM_WARHEAD || kind == ChargeKind.NUCLEAR_MISSILE_WARHEAD;
        double factor = flight.rv() ? RV_FACTOR : classFactor(flight.body());
        return best * factor * (tracked ? 1 : 0.6) * (flight.reentry() > 0.3f ? 0.85 : 1) * (superweapon ? 0.5 : 1);
    }

    /** How many interceptors a battery sends at a missile: one, or two at once for the big ones (class L and up). */
    public static int salvo(SizeClass body) {
        return body.ordinal() >= SizeClass.L.ordinal() ? 2 : 1;
    }

    /** How many the Interceptor Complex sends: twice a battery's, two or four. */
    public static int volley(SizeClass body) {
        return 2 * salvo(body);
    }

    /** Where and how soon an interceptor meets a missile: the point, and its flight time in ticks. */
    public record Solution(Vec3 at, int ticks) {}

    /**
     * Where an interceptor fired from {@code from} now meets {@code flight}: the earliest tick of the missile's flight
     * it can reach in time, within {@code range} of {@code from} and at least {@link #MIN_HEIGHT} above it; null if it
     * cannot (the missile comes down first, or never passes within reach).
     */
    public static @Nullable Solution solve(Flights.Flight flight, Vec3 from, double range) {
        int left = flight.ticksToImpact();
        for (int k = 3; k < left - 1; k++) {
            Vec3 p = flight.positionIn(k);
            double d = p.distanceTo(from);
            if (d > range || p.y < from.y + MIN_HEIGHT) {
                continue;
            }
            if (d * PATH_FACTOR / SPEED <= k) {
                return new Solution(p, k);
            }
        }
        return null;
    }
}
