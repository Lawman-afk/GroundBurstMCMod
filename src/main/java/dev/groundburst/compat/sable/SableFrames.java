package dev.groundburst.compat.sable;

import dev.groundburst.content.machine.Frame;
import dev.groundburst.content.machine.Frames;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.phys.Vec3;

/**
 * Sable's sub-levels (main's DESIGN.md §17.16): the blocks of a ship (Create Aeronautics' airships, planes, cars and boats)
 * stay real blocks in a plot of their own far off, and Sable shows them where the ship is. A block in a plot stands in its
 * ship's frame: its plot coordinates turned into the world's by the ship's pose as it is now. Through Sable Companion,
 * Sable's own small API (Sable carries it).
 */
public final class SableFrames {
    private SableFrames() {}

    public static void register() {
        Frames.addFinder((level, pos) -> {
            if (!SableCompanion.INSTANCE.isInPlotGrid(level, pos)) {
                return null;
            }
            SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, pos);
            return ship == null ? null : new ShipFrame(ship);
        });
    }

    /** A ship's frame: where its plot is now (its pose read afresh each time: the ship moves every tick). */
    record ShipFrame(SubLevelAccess ship) implements Frame {
        @Override
        public Vec3 toWorld(Vec3 local) {
            return this.ship.logicalPose().transformPosition(local);
        }

        @Override
        public Vec3 directionToWorld(Vec3 local) {
            return this.ship.logicalPose().transformNormal(local);
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            return this.ship.logicalPose().transformPositionInverse(world);
        }

        @Override
        public Vec3 directionToLocal(Vec3 world) {
            return this.ship.logicalPose().transformNormalInverse(world);
        }
    }
}
