package dev.groundburst.content.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Where a block really is (DESIGN.md §17.16). A block's coordinates are the world's, unless another mod carries it
 * about: a Sable sub-level (the ships of Create Aeronautics) keeps its blocks in a plot of their own far off and shows
 * them where the ship is, and a Create contraption takes its blocks out of the world and moves a copy
 * ({@link MachineBlockEntity#carried()}). A frame turns a block's own coordinates into the world's.
 */
public interface Frame {
    /** The world itself: nothing moves. */
    Frame WORLD = new Frame() {
        @Override
        public Vec3 toWorld(Vec3 local) {
            return local;
        }

        @Override
        public Vec3 directionToWorld(Vec3 local) {
            return local;
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            return world;
        }
    };

    /** A point in the block's own coordinates, in the world. */
    Vec3 toWorld(Vec3 local);

    /** A direction in the block's own coordinates, in the world (turned, not moved). */
    Vec3 directionToWorld(Vec3 local);

    /** A point in the world, in the block's own coordinates. */
    Vec3 toLocal(Vec3 world);

    /** A direction in the world, in the block's own coordinates. */
    default Vec3 directionToLocal(Vec3 world) {
        return toLocal(toWorld(Vec3.ZERO).add(world));
    }

    /** How fast a point of the frame moves, blocks a tick (the world's: not at all). */
    default Vec3 velocity(Vec3 local) {
        return Vec3.ZERO;
    }

    /** Whether this is the world itself. */
    default boolean isWorld() {
        return this == WORLD;
    }

    /** The block a block of the frame is over in the world (the one its middle is in). */
    default BlockPos toWorld(BlockPos local) {
        return BlockPos.containing(toWorld(Vec3.atCenterOf(local)));
    }

    /**
     * A heading over the ground in the frame (radians, as {@code Flights.Flight#heading}: 0 south, toward +z), as one
     * in the world: the frame's turn about the vertical.
     */
    default double heading(double local) {
        Vec3 d = directionToWorld(new Vec3(Math.sin(local), 0, Math.cos(local)));
        return d.horizontalDistanceSqr() < 1e-9 ? local : Math.atan2(d.x, d.z);
    }
}
