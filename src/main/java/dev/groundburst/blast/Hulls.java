package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blocks other mods keep apart from where they are seen (DESIGN.md §17.16): a ship's, in a plot of its own far off
 * (Create Aeronautics, on Sable), or a moving contraption's, held by its entity (Create). A blast breaks, scorches, sets
 * alight and shoves them where they are seen ({@link HullBlast}), a particle beam eats down through them and a deep
 * freeze ices them over ({@link HullEffects}), a singularity draws them in, and a missile or a shell strikes them there
 * ({@link #clip}). Ground Burst knows none: another mod's support (the 1.21.1 build's) adds a {@link Finder} for the ones
 * it moves.
 */
public final class Hulls {
    /** One structure's blocks, in its own block coordinates, as a blast or a missile meets them. */
    public interface Hull {
        /** What it is: the same object while it lasts (a blast running over several ticks knows it again by it). */
        Object key();

        /** A point of its own for one in the world, where it is now. */
        Vec3 toLocal(Vec3 world);

        /** A point in the world for one of its own, where it is now. */
        Vec3 toWorld(Vec3 local);

        /** How much bigger its blocks are in the world than in its own coordinates (1: as they are). */
        default double scale() {
            return 1;
        }

        /** The box, in its own coordinates, its blocks are all within. */
        AABB bounds();

        /** Hands {@code each} its blocks (not air) within a box of its own coordinates. */
        void blocks(AABB local, BiConsumer<BlockPos, BlockState> each);

        /** Its block at a place of its own: air where it has none. */
        BlockState state(BlockPos local);

        /** The place in the level a block of its own really stands at, or null when it is held off the level (a contraption's). */
        @Nullable BlockPos inLevel(BlockPos local);

        /** Whether it will not let a blast break its block at a place of its own (a train's wheels), as bedrock is not broken. */
        default boolean holds(BlockPos local, BlockState state) {
            return false;
        }

        /** Breaks its block at a place of its own, as a blast breaks the world's: nothing dropped, nothing it held let out. */
        void destroy(BlockPos local);

        /**
         * Puts {@code state} at a place of its own, as a blast or a freeze changes the world's blocks (scorched, frozen,
         * set alight, snowed on); false if it cannot (a contraption takes no new blocks, nor changes one that holds
         * anything).
         */
        default boolean set(BlockPos local, BlockState state) {
            return false;
        }

        /** A shove: an impulse (a direction of its own, a block's mass times blocks a second) at a point of its own. Only a hull that moves freely takes it. */
        default void push(Vec3 localPoint, Vec3 localImpulse) {}

        /**
         * A pull on the whole of it at once, as gravity pulls every part alike: {@code pull} is the change in velocity
         * (blocks a tick, in the world) it makes this tick at a point in the world, taken at the hull's middle. Only a
         * hull that moves freely takes it.
         */
        default void pull(java.util.function.Function<Vec3, Vec3> pull) {}

        /** The blast is done with it for this tick: what changed goes to the players who see it. */
        default void flush() {}
    }

    /** Something that knows the hulls another mod moves. */
    @FunctionalInterface
    public interface Finder {
        /** Hands {@code out} each hull with blocks that may lie within {@code radius} of {@code center} (in the world). */
        void near(ServerLevel level, Vec3 center, double radius, Consumer<Hull> out);
    }

    private static final List<Finder> FINDERS = new CopyOnWriteArrayList<>();

    private Hulls() {}

    public static void addFinder(Finder finder) {
        FINDERS.add(finder);
    }

    public static void removeFinder(Finder finder) {
        FINDERS.remove(finder);
    }

    /** Whether any mod's hulls can be found at all (none in Ground Burst alone). */
    public static boolean any() {
        return !FINDERS.isEmpty();
    }

    /** The hulls with blocks that may lie within {@code radius} of {@code center} (in the world). */
    public static List<Hull> near(ServerLevel level, Vec3 center, double radius) {
        List<Hull> out = new ArrayList<>();
        for (Finder finder : FINDERS) {
            finder.near(level, center, radius, out::add);
        }
        return out;
    }

    /**
     * Where (in the world) the line from {@code from} to {@code to} first strikes a hull's block, or null if it strikes
     * none: what a missile or a shell on that stretch of its flight would hit.
     */
    public static @Nullable Vec3 clip(ServerLevel level, Vec3 from, Vec3 to) {
        if (FINDERS.isEmpty()) {
            return null;
        }
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (Hull hull : near(level, mid, from.distanceTo(to) / 2 + 1)) {
            Vec3 hit = clip(hull, from, to);
            if (hit != null && hit.distanceToSqr(from) < bestD) {
                best = hit;
                bestD = hit.distanceToSqr(from);
            }
        }
        return best;
    }

    /** Where (in the world) the line first strikes one hull's block, or null. */
    public static @Nullable Vec3 clip(Hull hull, Vec3 from, Vec3 to) {
        Vec3 a = hull.toLocal(from), b = hull.toLocal(to);
        AABB bounds = hull.bounds();
        if (!bounds.inflate(1e-3).contains(a) && bounds.clip(a, b).isEmpty()) {
            return null;                                    // the line passes it by
        }
        Vec3 hit = BlockGetter.traverseBlocks(a, b, hull, (h, at) -> {
            if (!bounds.intersects(new AABB(at))) {
                return null;
            }
            BlockState state = h.state(at);
            if (state.isAir()) {
                return null;
            }
            BlockHitResult r = state.getCollisionShape(EmptyBlockGetter.INSTANCE, at).clip(a, b, at);
            return r == null ? null : r.getLocation();
        }, h -> null);
        return hit == null ? null : hull.toWorld(hit);
    }

    /** The box, in a hull's own coordinates, that holds a box of the world (its corners taken over, as it may be turned). */
    public static AABB toLocal(Hull hull, AABB world) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE,
                z1 = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 c = hull.toLocal(new Vec3((i & 1) == 0 ? world.minX : world.maxX, (i & 2) == 0 ? world.minY : world.maxY,
                    (i & 4) == 0 ? world.minZ : world.maxZ));
            x0 = Math.min(x0, c.x);
            y0 = Math.min(y0, c.y);
            z0 = Math.min(z0, c.z);
            x1 = Math.max(x1, c.x);
            y1 = Math.max(y1, c.y);
            z1 = Math.max(z1, c.z);
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    /** Air, for a hull with nothing at a place. */
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
}
