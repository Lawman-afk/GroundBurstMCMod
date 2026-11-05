package dev.groundburst.blast;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * An entity a blast's shock does something to of its own instead of hurting it (DESIGN.md §14.6): a sea mine goes off
 * close by (sympathetic detonation) or has its mooring parted a little further off, a sinking depth charge goes off.
 */
public interface ShockReceiver {
    /** The shock of a blast of {@code profile} at {@code center} has reached it, {@code distance} blocks away. */
    void shocked(ServerLevel level, Vec3 center, double distance, BlastProfile profile);
}
