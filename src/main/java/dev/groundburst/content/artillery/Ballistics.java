package dev.groundburst.content.artillery;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.machine.Frame;
import dev.groundburst.content.machine.Frames;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Artillery shells' flight and spread (DESIGN.md §20.1), worked out the same way on the server (where a shell goes)
 * and on the client (where the reticle is drawn).
 *
 * <p>A shell flies a true parabola: gravity {@link #G} a tick squared and no drag. Launched with the curve's velocity
 * plus half a tick of gravity, and moved velocity-first each tick, its position at every tick lies on the curve
 * {@code p(t) = p0 + u t - G t^2 / 2}, so an aim solved for the curve is exact. Yaws are as {@code atan2(dx, dz)} in
 * degrees (0 south, 90 east); elevations in degrees over level.
 */
public final class Ballistics {
    /** Gravity, blocks a tick squared (an arrow's). */
    public static final double G = 0.05;

    private Ballistics() {}

    /** The curve's launch velocity at {@code speed} blocks a tick, toward {@code yaw}, raised {@code elevation}. */
    public static Vec3 velocity(double speed, double yaw, double elevation) {
        double y = Math.toRadians(yaw), e = Math.toRadians(elevation);
        double h = Math.cos(e) * speed;
        return new Vec3(Math.sin(y) * h, Math.sin(e) * speed, Math.cos(y) * h);
    }

    /** Where the curve from {@code from} with launch velocity {@code u} is {@code t} ticks on. */
    public static Vec3 point(Vec3 from, Vec3 u, double t) {
        return new Vec3(from.x + u.x * t, from.y + u.y * t - G * t * t / 2, from.z + u.z * t);
    }

    /** The curve's velocity {@code t} ticks on. */
    public static Vec3 velocityAt(Vec3 u, double t) {
        return new Vec3(u.x, u.y - G * t, u.z);
    }

    /**
     * The high-arc elevation (degrees) at which a shell at {@code speed} comes down {@code horizontal} blocks off and
     * {@code dy} up, or NaN where that speed cannot get there.
     */
    public static double highAngle(double speed, double horizontal, double dy) {
        double v2 = speed * speed;
        double disc = v2 * v2 - G * (G * horizontal * horizontal + 2 * dy * v2);
        if (disc < 0 || horizontal < 1e-6) {
            return Double.NaN;
        }
        return Math.toDegrees(Math.atan((v2 + Math.sqrt(disc)) / (G * horizontal)));
    }

    /** The low-arc elevation (degrees) for the same, or NaN. */
    public static double lowAngle(double speed, double horizontal, double dy) {
        double v2 = speed * speed;
        double disc = v2 * v2 - G * (G * horizontal * horizontal + 2 * dy * v2);
        if (disc < 0 || horizontal < 1e-6) {
            return Double.NaN;
        }
        return Math.toDegrees(Math.atan((v2 - Math.sqrt(disc)) / (G * horizontal)));
    }

    /** The world yaw from {@code from} to {@code to}, degrees as {@code atan2(dx, dz)}. */
    public static float yaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(to.x - from.x, to.z - from.z));
    }

    /**
     * Where a shell comes down: the first block (or fluid) its curve meets, the tick it gets there and its velocity
     * then. {@code ticks} is when it got there (a fraction along the last tick's chord). {@code aboard}: the block is
     * one another mod moves (a ship's), met where it is seen, above the world's own ground.
     */
    public record Fall(Vec3 at, double ticks, Vec3 velocity, boolean aboard) {
        public Fall(Vec3 at, double ticks, Vec3 velocity) {
            this(at, ticks, velocity, false);
        }

        /** How much its spread is stretched along the line of fire on the ground: 1 / sin of its descent (at most 3). */
        public double stretch() {
            double h = Math.hypot(this.velocity.x, this.velocity.z);
            double s = Math.abs(this.velocity.y) / Math.max(1e-6, Math.hypot(h, this.velocity.y));
            return Math.min(3, 1 / Math.max(1 / 3.0, s));
        }
    }

    /**
     * The flight from {@code from} with launch velocity {@code u} worked out to {@code at} (on its curve, or near it), as if
     * it came down there: for ground not loaded, where {@link #fall} cannot trace it.
     */
    public static Fall flightTo(Vec3 from, Vec3 u, Vec3 at) {
        double hs = Math.hypot(u.x, u.z), t = hs < 1e-6 ? 0 : Math.hypot(at.x - from.x, at.z - from.z) / hs;
        return new Fall(at, t, velocityAt(u, t));
    }

    /**
     * Follows the curve from {@code from} (launch velocity {@code u}) tick by tick for up to {@code maxTicks}: where it
     * first meets a block or fluid. Null if it never does, or reaches ground that is not loaded first.
     */
    public static @Nullable Fall fall(Level level, Vec3 from, Vec3 u, @Nullable Entity except, int maxTicks) {
        CollisionContext ctx = except == null ? CollisionContext.empty() : CollisionContext.of(except);
        Vec3 prev = from;
        for (int n = 1; n <= maxTicks; n++) {
            Vec3 p = point(from, u, n);
            if (p.y < level.getMinY() - 16 || !level.isLoaded(BlockPos.containing(p))) {
                return null;
            }
            BlockHitResult hit = level.clip(new ClipContext(prev, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, ctx));
            if (hit.getType() != HitResult.Type.MISS) {
                // A ship's block is met where it is seen (another mod's clip reports it in the ship's plot, far off).
                Frame frame = Frames.at(level, hit.getBlockPos());
                Vec3 at = frame.toWorld(hit.getLocation());
                double along = Math.min(1, prev.distanceTo(at) / Math.max(1e-6, prev.distanceTo(p)));
                double t = n - 1 + along;
                return new Fall(at, t, velocityAt(u, t), !frame.isWorld());
            }
            prev = p;
        }
        return null;
    }

    /** The top of what stands at (x, z) (where a shell would come down on it), or {@code fallback} if not loaded. */
    public static double ground(Level level, double x, double z, double fallback) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!level.isLoaded(new BlockPos(bx, level.getMinY(), bz))) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    /**
     * Where in the spread round a laid point a shot falls, World of Tanks' way: normally distributed over the aiming
     * circle ({@code radius} is two sigma), a shot that would fall outside it rolled again evenly inside it; the circle
     * laid on the ground stretched {@code stretch} times along the line of fire ({@code yaw}). An offset (x, z).
     */
    public static Vec3 scatter(RandomSource random, double radius, double stretch, double yaw) {
        double sigma = radius / 2;
        double across = random.nextGaussian() * sigma, along = random.nextGaussian() * sigma;
        if (across * across + along * along > radius * radius) {
            double a = random.nextDouble() * Math.PI * 2, r = radius * Math.sqrt(random.nextDouble());
            across = r * Math.cos(a);
            along = r * Math.sin(a);
        }
        double y = Math.toRadians(yaw);
        double fx = Math.sin(y), fz = Math.cos(y);
        along *= stretch;
        return new Vec3(fz * across + fx * along, 0, -fx * across + fz * along);
    }
}
