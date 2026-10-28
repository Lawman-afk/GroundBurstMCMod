package dev.groundburst.content.launch;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.MissileSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Anything a Launch Computer aims, arms and fires (DESIGN.md §17.3, §19.5): the launch silos and the superweapons. It
 * finds them through {@link Launches} and shows each in its list and on its map.
 */
public interface LaunchSite {
    /** What it is, for the computer's list and map (its ordinal goes over the wire). */
    enum Kind { LAUNCHER, SCUD_STORM, NUCLEAR_MISSILE }

    BlockPos getBlockPos();

    default Kind siteKind() {
        return Kind.LAUNCHER;
    }

    /** What it can do right now. */
    LaunchSiloBlockEntity.Readiness readiness();

    /**
     * Ticks to go: while it counts down to launch, until it fires; while a superweapon charges, until it is ready
     * (-1: neither).
     */
    int countdown();

    boolean armed();

    @Nullable BlockPos target();

    /** How far it reaches, blocks (0: nothing loaded). */
    int range();

    /** Aims it (not while it counts down). */
    boolean setTarget(int x, int z);

    /** Arms it (only when ready, with a target in range) or makes it safe. */
    boolean arm(boolean on);

    /** Fires it if armed. */
    boolean launchArmed(ServerLevel level, @Nullable Entity by);

    /** What its strike would do round its target now (the computer's map; {@link Strike#NONE}: nothing loaded). */
    default Strike strike(ServerLevel level) {
        return Strike.NONE;
    }

    /**
     * A strike's reach, blocks: its warhead's lethal and outer radii ({@link AssemblyStats.Area}) and how far from the
     * target it may come down, along its flight ({@code along}) and across it ({@code across}).
     */
    record Strike(float lethal, float reach, float along, float across) {
        public static final Strike NONE = new Strike(0, 0, 0, 0);

        /** A missile's strike at {@code target} from {@code from} (no target: at its full range). */
        public static Strike of(ServerLevel level, MissileSpec spec, BlockPos from, @Nullable BlockPos target) {
            AssemblyStats.Area area = AssemblyStats.area(spec.warhead(), level.registryAccess());
            double distance = target == null ? spec.range()
                    : Math.sqrt(Math.pow(target.getX() - from.getX(), 2) + Math.pow(target.getZ() - from.getZ(), 2));
            float scatter = (float) spec.guidance().scatter(distance);
            return new Strike(area.lethal(), area.reach(), scatter, scatter / 2);
        }

        /** The larger of two strikes, figure by figure (two missiles fired together). */
        public Strike max(Strike o) {
            return new Strike(Math.max(this.lethal, o.lethal), Math.max(this.reach, o.reach), Math.max(this.along, o.along),
                    Math.max(this.across, o.across));
        }
    }
}
