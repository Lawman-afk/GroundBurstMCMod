package dev.groundburst.content.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.Flights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The Dead Hand's command rocket in flight (DESIGN.md §17.19), after Perimeter's 15A11 and the American ERCS: no warhead,
 * a transmitter, a course over its own launchers. Its signal is heard {@value #REACH_GROUND} blocks round it as it lifts
 * off and a block farther for each block it climbs, to {@value #REACH_TOP} (line of sight: the higher, the farther); every
 * launcher of its network it reaches takes the order to fire. Its course is planned over the network's launchers: of the
 * ways toward each one beyond its first reach and toward their middle, the one along which it reaches the most, flown in
 * advance a sample every few ticks; it ends {@value #PAST} blocks past the farthest launcher it reaches.
 */
public final class CommandRocket {
    /** Its signal's reach round it, blocks: at lift-off, more for each block it has climbed, and at most. */
    public static final double REACH_GROUND = 256, REACH_CLIMB = 1, REACH_TOP = 480;
    /** Its course: past the farthest launcher it reaches by this, never shorter or longer than these, blocks. */
    public static final double PAST = 160, SHORTEST = 256, LONGEST = 4096;
    /** Its tube: how deep (blocks), and how long its motor burns there before it climbs out, ticks (ejected, then lit). */
    public static final double TUBE_DEPTH = 2;
    public static final int TUBE_BURN = 8;
    /** Flying a course in advance: a look every this many ticks. */
    private static final int SAMPLE = 5;
    /** For GameTests: the reach of the signal of one Dead Hand's rocket (by the Dead Hand's place), as a test sets it. */
    public static final Map<BlockPos, Double> REACH_FOR_TEST = new ConcurrentHashMap<>();

    private CommandRocket() {}

    /** How far its signal carries round it, {@code climbed} blocks above where it was lit. */
    public static double reach(double climbed) {
        return Math.min(REACH_TOP, REACH_GROUND + REACH_CLIMB * Math.max(0, climbed));
    }

    /** The same for the rocket of the Dead Hand at {@code hand} (a GameTest may have set it). */
    public static double reach(BlockPos hand, double climbed) {
        Double r = REACH_FOR_TEST.get(hand);
        return r != null ? r : reach(climbed);
    }

    /** A course planned: where it comes down, the launchers it reaches (in the order it reaches them), and how many there were. */
    public record Course(Vec3 target, List<BlockPos> reached, int of) {}

    /**
     * Its course from {@code pad} (its tube's floor, in the world) over the launchers at {@code launchers} (each where it is
     * in the world): the way that reaches the most of them (the shorter course of two that reach as many), out past the
     * farthest of those; with none beyond its first reach, out over their middle (or along {@code away}, with none at all),
     * at its shortest.
     */
    public static Course plan(Vec3 pad, List<BlockPos> launchers, Direction away) {
        List<Vec3> beyond = new ArrayList<>();
        double sx = 0, sz = 0;
        for (BlockPos l : launchers) {
            Vec3 c = Vec3.atBottomCenterOf(l);
            sx += c.x;
            sz += c.z;
            if (flat(c, pad) > REACH_GROUND) {
                beyond.add(c);
            }
        }
        List<double[]> ways = new ArrayList<>();
        if (beyond.isEmpty()) {
            Vec3 middle = launchers.isEmpty() ? null : new Vec3(sx / launchers.size(), pad.y, sz / launchers.size());
            ways.add(middle != null && flat(middle, pad) > 2 ? unit(middle.x - pad.x, middle.z - pad.z)
                    : new double[] {away.getStepX(), away.getStepZ()});
        } else {
            double bx = 0, bz = 0;
            for (Vec3 b : beyond) {
                ways.add(unit(b.x - pad.x, b.z - pad.z));
                bx += b.x;
                bz += b.z;
            }
            Vec3 middle = new Vec3(bx / beyond.size(), pad.y, bz / beyond.size());
            if (flat(middle, pad) > 2) {
                ways.add(unit(middle.x - pad.x, middle.z - pad.z));
            }
        }
        Course best = null;
        for (double[] u : ways) {
            // As far out as the farthest launcher within its widest reach of this way's line, and past it.
            double far = 0;
            for (BlockPos l : launchers) {
                Vec3 c = Vec3.atBottomCenterOf(l);
                double along = (c.x - pad.x) * u[0] + (c.z - pad.z) * u[1];
                double across = Math.abs((c.x - pad.x) * u[1] - (c.z - pad.z) * u[0]);
                if (along > 0 && across <= REACH_TOP) {
                    far = Math.max(far, along);
                }
            }
            double length = Math.clamp(far + PAST, SHORTEST, LONGEST);
            Vec3 target = new Vec3(pad.x + u[0] * length, pad.y, pad.z + u[1] * length);
            Course c = fly(pad, target, launchers);
            if (best == null || c.reached().size() > best.reached().size()
                    || c.reached().size() == best.reached().size() && flat(c.target(), pad) < flat(best.target(), pad) - 1) {
                best = c;
            }
        }
        return best;
    }

    /** A course to {@code target} flown in advance: which of {@code launchers} its signal reaches, in order. */
    public static Course fly(Vec3 pad, Vec3 target, List<BlockPos> launchers) {
        Flights.Flight f = Flights.preview(pad, target, SizeClass.S, TUBE_DEPTH, TUBE_BURN);
        int end = f.ticksToImpact();
        List<BlockPos> reached = new ArrayList<>();
        boolean[] got = new boolean[launchers.size()];
        for (int t = 0; t <= end + SAMPLE - 1; t += SAMPLE) {
            Vec3 p = f.positionIn(Math.min(t, end));
            double r = reach(p.y - pad.y);
            for (int i = 0; i < launchers.size(); i++) {
                if (!got[i] && flat(Vec3.atBottomCenterOf(launchers.get(i)), p) <= r) {
                    got[i] = true;
                    reached.add(launchers.get(i));
                }
            }
        }
        return new Course(target, reached, launchers.size());
    }

    /** The distance between two points over the ground (blocks). */
    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static double[] unit(double x, double z) {
        double d = Math.hypot(x, z);
        return d < 1e-9 ? new double[] {0, 1} : new double[] {x / d, z / d};
    }
}
