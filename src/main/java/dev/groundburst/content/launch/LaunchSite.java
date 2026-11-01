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

    /** What it would fire, for the Launch Control Station's list: its missile, or a superweapon's ammunition (empty: none). */
    default ItemStack shownStack() {
        return ItemStack.EMPTY;
    }

    /** How long its strike takes to arrive (the station's time on target, DESIGN.md §17.12). */
    default Timing timing() {
        return new Timing(LaunchSiloBlockEntity.COUNTDOWN + MissileEntity.IGNITION, false, getBlockPos().getX() + 0.5,
                getBlockPos().getZ() + 0.5, 0);
    }

    /**
     * When a launch arrives: {@code delay} ticks from LAUNCH until its first missile leaves (its countdown and the
     * missile's ignition), then its flight from x/z (where it lifts off), on its arc or, for a cruise missile, out
     * along {@code heading} (radians, as {@link Flights.Flight#heading}) and round onto the target.
     */
    record Timing(int delay, boolean cruise, double x, double z, double heading) {
        /** Ticks from LAUNCH until its first warhead comes down on tx/tz. */
        public int toImpact(int tx, int tz) {
            if (this.cruise) {
                return this.delay + Flights.CruisePlan.of(new Vec3(this.x, 0, this.z), this.heading, new Vec3(tx + 0.5, 0, tz + 0.5)).ticks();
            }
            return this.delay + Flights.flightTicks(Math.hypot(tx + 0.5 - this.x, tz + 0.5 - this.z));
        }
    }

    /**
     * A strike's reach, blocks: its warhead's lethal and outer radii ({@link AssemblyStats.Area}) and how far from the
     * target it may come down, along its flight ({@code along}) and across it ({@code across}). A MIRV's (DESIGN.md
     * §17.14): each of its RVs' own circle round its point ({@code rvs}), its lethal radius their biggest and its reach
     * the farthest any of them harms from the aim.
     */
    record Strike(float lethal, float reach, float along, float across, java.util.List<Rv> rvs) {
        public static final Strike NONE = new Strike(0, 0, 0, 0);

        /** One RV's circle: where it comes down from the aim, its warhead's lethal and outer radii (a decoy's none). */
        public record Rv(int dx, int dz, float lethal, float reach) {
            public static final com.mojang.serialization.Codec<Rv> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                    com.mojang.serialization.Codec.INT.fieldOf("dx").forGetter(Rv::dx), com.mojang.serialization.Codec.INT.fieldOf("dz").forGetter(Rv::dz),
                    com.mojang.serialization.Codec.FLOAT.fieldOf("lethal").forGetter(Rv::lethal),
                    com.mojang.serialization.Codec.FLOAT.fieldOf("reach").forGetter(Rv::reach)).apply(i, Rv::new));
        }

        public Strike(float lethal, float reach, float along, float across) {
            this(lethal, reach, along, across, java.util.List.of());
        }

        /** A missile's strike at {@code target} from {@code from} (no target: at its full range). */
        public static Strike of(ServerLevel level, MissileSpec spec, BlockPos from, @Nullable BlockPos target) {
            double distance = target == null ? spec.range()
                    : Math.sqrt(Math.pow(target.getX() - from.getX(), 2) + Math.pow(target.getZ() - from.getZ(), 2));
            float scatter = (float) spec.guidance().scatter(distance);
            if (spec.mirv()) {
                BlockPos aim = target != null ? target : from.offset(spec.range(), 0, 0);
                java.util.List<ItemStack> rvs = dev.groundburst.content.assembly.Mirv.rvs(spec.warhead());
                java.util.List<int[]> points = dev.groundburst.content.assembly.Mirv.points(spec.warhead(), from, aim.getX(), aim.getZ());
                java.util.List<Rv> circles = new java.util.ArrayList<>();
                float lethal = 0, reach = 0;
                for (int i = 0; i < rvs.size(); i++) {
                    AssemblyStats.Area a = AssemblyStats.area(rvs.get(i), level.registryAccess());
                    int dx = points.get(i)[0] - aim.getX(), dz = points.get(i)[1] - aim.getZ();
                    circles.add(new Rv(dx, dz, a.lethal(), a.reach()));
                    lethal = Math.max(lethal, a.lethal());
                    reach = Math.max(reach, (float) Math.hypot(dx, dz) + a.reach());
                }
                return new Strike(lethal, reach, scatter, scatter / 2, java.util.List.copyOf(circles));
            }
            AssemblyStats.Area area = AssemblyStats.area(spec.warhead(), level.registryAccess());
            return new Strike(area.lethal(), area.reach(), scatter, scatter / 2);
        }

        /** The larger of two strikes, figure by figure (two missiles fired together). */
        public Strike max(Strike o) {
            return new Strike(Math.max(this.lethal, o.lethal), Math.max(this.reach, o.reach), Math.max(this.along, o.along),
                    Math.max(this.across, o.across), this.rvs.isEmpty() ? o.rvs : this.rvs);
        }

        /** Over the network (the station's and the computer's maps). */
        public void write(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeFloat(this.lethal);
            buf.writeFloat(this.reach);
            buf.writeFloat(this.along);
            buf.writeFloat(this.across);
            buf.writeVarInt(this.rvs.size());
            for (Rv r : this.rvs) {
                buf.writeVarInt(r.dx());
                buf.writeVarInt(r.dz());
                buf.writeFloat(r.lethal());
                buf.writeFloat(r.reach());
            }
        }

        public static Strike read(net.minecraft.network.FriendlyByteBuf buf) {
            float lethal = buf.readFloat(), reach = buf.readFloat(), along = buf.readFloat(), across = buf.readFloat();
            int n = Math.min(64, buf.readVarInt());
            java.util.List<Rv> rvs = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rvs.add(new Rv(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat()));
            }
            return new Strike(lethal, reach, along, across, java.util.List.copyOf(rvs));
        }
    }
}
