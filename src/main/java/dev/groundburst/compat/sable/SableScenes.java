package dev.groundburst.compat.sable;

import org.jspecify.annotations.Nullable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The parts of the in-game check (dev.groundburst.compat.CreateCheck) that touch Sable itself, kept apart so nothing
 * loads Sable's classes unless the check runs (with Sable installed).
 */
public final class SableScenes {
    private SableScenes() {}

    /** The first ship whose blocks are in a box of the world. */
    public static @Nullable Object shipIn(ServerLevel level, AABB box) {
        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(box))) {
            if (ship instanceof SubLevel sub && !sub.isRemoved()) {
                return sub;
            }
        }
        return null;
    }

    /** How many blocks a ship has (its plot's, not air). */
    public static int blocks(ServerLevel level, Object ship) {
        SableHulls.Ship hull = new SableHulls.Ship(level, (SubLevel) ship);
        int[] n = {0};
        hull.blocks(hull.bounds(), (pos, state) -> n[0]++);
        return n[0];
    }

    /** Where a ship is (its pose's position). */
    public static Vec3 position(Object ship) {
        var p = ((SubLevel) ship).logicalPose().position();
        return new Vec3(p.x(), p.y(), p.z());
    }

    /** The box of the world a ship's blocks are in now. */
    public static AABB box(Object ship) {
        BoundingBox3dc b = ((SubLevel) ship).boundingBox();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    /** Whether a ship is gone (every block broken, or removed). */
    public static boolean gone(Object ship) {
        return ((SubLevel) ship).isRemoved();
    }

    /** A ship, for the log. */
    public static String describe(Object ship) {
        SubLevel s = (SubLevel) ship;
        return s.getName() + " at " + position(ship) + ", plot " + s.getPlot().getBoundingBox();
    }
}
