package dev.groundburst.content.launch;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.MissileSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Anything a Launch Computer aims, arms and fires (DESIGN.md §17.3, §19.5): the launch silos and the superweapons. It
 * finds them through {@link Launches} and shows each in its list and on its map.
 */
public interface LaunchSite {
    /** What it is, for the computer's list and map (its ordinal goes over the wire: new kinds go at the end). */
    enum Kind { LAUNCHER, SCUD_STORM, NUCLEAR_MISSILE, PARTICLE_CANNON }

    /** Its key: where its controller stands (on a Sable ship, in the ship's plot; carried by a contraption, where it was taken from). */
    BlockPos getBlockPos();

    /** Where it is in the world now (DESIGN.md §17.16): its block, unless a ship or a contraption carries it about. */
    BlockPos worldBlockPos();

    /**
     * It loaded somewhere else than it was saved (a Create contraption set it down, a Sable ship took it aboard): its
     * network and the computers follow it to its new place.
     */
    static void moved(ServerLevel level, BlockPos from, LaunchSite site) {
        Launches.untrack(level, from);
        Launches.track(level, site.getBlockPos());
        LaunchNetwork.of(level).moved(level, from, site.getBlockPos());
    }

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

    /**
     * Fires it if armed, standing by (DESIGN.md §17.19): its countdown runs as ever and holds at its last tick until it is
     * {@link #release released} (the Dead Hand's command rocket reaching it) or {@link #scrub scrubbed}.
     */
    default boolean launchHeld(ServerLevel level, @Nullable Entity by) {
        return launchArmed(level, by);
    }

    /** Its countdown is let run out: held at its last tick, it fires at once; still counting, at its end. */
    default void release() {}

    /** Its countdown scrubbed, its launchers stood down, what it holds kept, made safe (a Dead Hand stood down). */
    default void scrub() {}

    /** Whether its countdown waits for an order ({@link #launchHeld}, not yet released). */
    default boolean standingBy() {
        return false;
    }

    /** What its strike would do round its target now (the computer's map; {@link Strike#NONE}: nothing loaded). */
    default Strike strike(ServerLevel level) {
        return Strike.NONE;
    }

    /** What it would fire, for the Launch Control Station's list: its missile, or a superweapon's ammunition (empty: none). */
    default ItemStack shownStack() {
        return ItemStack.EMPTY;
    }

    /** How long its strike takes to arrive (the station's time on target, DESIGN.md §17.12). */
    default Timing timing() {
        BlockPos at = worldBlockPos();
        return new Timing(LaunchSiloBlockEntity.COUNTDOWN + MissileEntity.IGNITION, false, at.getX() + 0.5, at.getZ() + 0.5, 0);
    }

    /**
     * When a launch arrives: {@code delay} ticks from LAUNCH until its first missile leaves (its countdown and the
     * missile's ignition), then its flight from x/z (where it lifts off), on its arc or, for a cruise missile, out
     * along {@code heading} (radians, as {@link Flights.Flight#heading}) and round onto the target. A {@code direct}
     * strike has no flight: it arrives {@code delay} ticks after LAUNCH wherever it is aimed (a particle beam from orbit).
     */
    record Timing(int delay, boolean cruise, double x, double z, double heading, boolean direct) {
        public Timing(int delay, boolean cruise, double x, double z, double heading) {
            this(delay, cruise, x, z, heading, false);
        }

        /** A strike that comes down {@code delay} ticks after LAUNCH wherever it is aimed. */
        public static Timing direct(int delay) {
            return new Timing(delay, false, 0, 0, 0, true);
        }

        /** Ticks from LAUNCH until its first warhead comes down on tx/tz. */
        public int toImpact(int tx, int tz) {
            if (this.direct) {
                return this.delay;
            }
            if (this.cruise) {
                return this.delay + Flights.CruisePlan.of(new Vec3(this.x, 0, this.z), this.heading, new Vec3(tx + 0.5, 0, tz + 0.5)).ticks();
            }
            return this.delay + Flights.flightTicks(Math.hypot(tx + 0.5 - this.x, tz + 0.5 - this.z));
        }
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
