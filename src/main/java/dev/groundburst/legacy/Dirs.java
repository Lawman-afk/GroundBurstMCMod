package dev.groundburst.legacy;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Minecraft 1.21's Direction.getNearest of a vector (1.20.1's takes the three numbers). */
public final class Dirs {
    private Dirs() {}

    public static Direction nearest(Vec3 v) {
        return Direction.getNearest(v.x, v.y, v.z);
    }
}
