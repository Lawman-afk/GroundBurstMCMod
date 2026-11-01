package dev.groundburst.content.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.Fuze;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.assembly.FuzeSettings;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.BombBlock;
import dev.groundburst.content.ordnance.charge.Charges;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The missiles in the air in a level (DESIGN.md §17.5, basic version). A flight is kept here and advanced every server
 * tick whatever is loaded, so a missile can never stall: it keeps the chunk it is in and the next few along its path
 * ticket-loaded (and its target's, from launch), and its {@link MissileEntity} is only what players see, moved along by
 * the flight and put back wherever its chunk is loaded. Saved with the world ({@code data/groundburst/missile_flights.dat}).
 */
public final class Flights extends SavedData {
    /** How far ahead along its path a flight keeps chunks loaded (ticks; points every LOOKAHEAD_STEP ticks). */
    private static final int LOOKAHEAD = 30, LOOKAHEAD_STEP = 6;
    /** The whistle of the dive: this many ticks before impact. */
    private static final int WHISTLE_AT = 50;
    /**
     * A MIRV (DESIGN.md §17.14): how far through its flight its bus starts letting its re-entry vehicles go (coming
     * down from the top of its arc), and the ticks between one and the next (fewer on a short flight).
     */
    public static final double MIRV_SPLIT = 0.6;
    public static final int MIRV_GAP = 4;

    static final Codec<Flight> FLIGHT_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(f -> f.id),
            Vec3.CODEC.fieldOf("start").forGetter(f -> f.start),
            Vec3.CODEC.fieldOf("target").forGetter(f -> f.target),
            Codec.DOUBLE.fieldOf("apex").forGetter(f -> f.apex),
            Codec.INT.fieldOf("flight").forGetter(f -> f.flightTicks),
            Codec.INT.fieldOf("age").forGetter(f -> f.age),
            Codec.BOOL.optionalFieldOf("dud", false).forGetter(f -> f.dud),
            ItemStack.OPTIONAL_CODEC.fieldOf("warhead").forGetter(f -> f.warhead),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(f -> Optional.ofNullable(f.owner)),
            Codec.BOOL.optionalFieldOf("hardened", false).forGetter(f -> f.hardened),
            Codec.STRING.optionalFieldOf("body", "M").forGetter(f -> f.body.name()),
            Codec.DOUBLE.optionalFieldOf("silo", 0.0).forGetter(f -> f.silo),
            // A cruise missile's plan (slice 9c): the heading it left its launcher on and its height along its path.
            Codec.DOUBLE.optionalFieldOf("cruise_heading").forGetter(f -> f.plan == null ? Optional.empty() : Optional.of(f.plan.heading)),
            Codec.FLOAT.listOf().optionalFieldOf("cruise_alt", List.of()).forGetter(f -> f.plan == null ? List.of() : f.plan.altList()),
            // A laser-guided missile's seeker (DESIGN.md §17.13).
            Seeker.CODEC.optionalFieldOf("seeker").forGetter(f -> Optional.ofNullable(f.seeker)),
            // A MIRV's (DESIGN.md §17.14): a bus's RVs let go so far, or an RV's path.
            MirvPart.CODEC.optionalFieldOf("mirv").forGetter(f -> f.released > 0 || f.lead != null
                    ? Optional.of(new MirvPart(f.released, Optional.ofNullable(f.lead), f.scale)) : Optional.empty())
    ).apply(i, (id, start, target, apex, ticks, age, dud, warhead, owner, hardened, body, silo, heading, alt, seeker, mirv) -> {
        Flight f = new Flight(id, start, target, apex, ticks, warhead, owner.orElse(null));
        f.age = age;
        f.dud = dud;
        f.hardened = hardened;
        f.body = SizeClass.valueOf(body);
        f.silo = silo;
        if (heading.isPresent() && !alt.isEmpty()) {
            f.plan = CruisePlan.of(start, heading.get(), target);
            f.plan.setAlt(alt);
        }
        f.seeker = seeker.orElse(null);
        mirv.ifPresent(m -> {
            f.released = m.released();
            f.lead = m.lead().orElse(null);
            f.scale = m.scale();
        });
        return f;
    }));

    /** A MIRV's part in a flight, as saved: its bus's RVs let go so far; an RV's path's lead; the blast scale they fly at. */
    record MirvPart(int released, Optional<Vec3> lead, float scale) {
        static final Codec<MirvPart> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("released", 0).forGetter(MirvPart::released),
                Vec3.CODEC.optionalFieldOf("lead").forGetter(MirvPart::lead),
                Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(MirvPart::scale)).apply(i, MirvPart::new));
    }
    static final SavedDataType<Flights> TYPE = new SavedDataType<>(GroundBurst.id("missile_flights"), Flights::new,
            FLIGHT_CODEC.listOf().fieldOf("flights").codec().xmap(list -> {
                Flights fs = new Flights();
                fs.flights.addAll(list);
                return fs;
            }, fs -> fs.flights));

    private final List<Flight> flights = new ArrayList<>();

    private Flights() {}

    public static Flights of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The missiles in the air. */
    public List<Flight> all() {
        return List.copyOf(this.flights);
    }

    /** For GameTests: ends a flight where it is, without its warhead going off. */
    public void cancelForTest(ServerLevel level, Flight f) {
        if (this.flights.remove(f)) {
            f.finish(level);
            setDirty();
        }
    }

    /** An assembled missile lit at {@code pad} (its tail), bound for {@code target}: its warhead, body and hardening go with it. */
    public static Flight launch(ServerLevel level, Vec3 pad, Vec3 target, dev.groundburst.content.assembly.MissileSpec spec, @Nullable Entity owner) {
        return launch(level, pad, target, spec, owner, 0);
    }

    /**
     * The same from the bottom of a silo's shaft {@code silo} blocks deep (to the ground at its mouth): it climbs
     * straight up the shaft and a little past its mouth before it turns onto its arc.
     */
    public static Flight launch(ServerLevel level, Vec3 pad, Vec3 target, dev.groundburst.content.assembly.MissileSpec spec, @Nullable Entity owner,
            double silo) {
        Flight f = launch(level, pad, target, spec.warhead(), spec.body(), spec.hardened(), owner, silo);
        if (spec.guidance() == dev.groundburst.content.assembly.MissileParts.Guidance.LASER) {
            double range = Math.sqrt(Mth.square(target.x - pad.x) + Mth.square(target.z - pad.z));
            f.seeker = new Seeker(0, target, Seeker.REACH + Seeker.SHARE * range);
            of(level).setDirty();
        }
        return f;
    }

    /** How high a flight of {@code range} blocks climbs above its pad. */
    public static double apex(double range) {
        return Mth.clamp(range * MissileEntity.APEX_SHARE, MissileEntity.MIN_APEX, MissileEntity.MAX_APEX);
    }

    /** How long a flight of {@code range} blocks takes, lighting to impact, in ticks. */
    public static int flightTicks(double range) {
        return (int) Mth.clamp((range + 2 * apex(range)) / MissileEntity.SPEED, 60, 1200);
    }

    /** A medium missile lit at {@code pad} (its tail), bound for {@code target}, carrying {@code warhead}. */
    public static Flight launch(ServerLevel level, Vec3 pad, Vec3 target, ItemStack warhead, @Nullable Entity owner) {
        return launch(level, pad, target, warhead, SizeClass.M, false, owner, 0);
    }

    /**
     * A missile lit at {@code pad} (its tail), bound for {@code target}: its warhead, airframe class (its stages), whether
     * it is hardened against EMP, and how deep in a silo's shaft it starts (0: on a pad). (A superweapon's missiles.)
     */
    public static Flight launch(ServerLevel level, Vec3 pad, Vec3 target, ItemStack warhead, SizeClass body, boolean hardened, @Nullable Entity owner,
            double silo) {
        double range = Math.sqrt(Mth.square(target.x - pad.x) + Mth.square(target.z - pad.z));
        double apex = apex(range);
        int ticks = flightTicks(range);
        Flight f = new Flight(UUID.randomUUID(), pad, target, apex, ticks, warhead.copyWithCount(1), owner == null ? null : owner.getUUID());
        f.body = body;
        f.hardened = hardened;
        f.silo = Math.max(0, silo);
        Flights fs = of(level);
        fs.flights.add(f);
        fs.setDirty();
        f.keepLoaded(level);
        f.proxy(level);
        level.playSound(null, pad.x, pad.y, pad.z, GBLaunch.MISSILE_LAUNCH.get(), SoundSource.BLOCKS, 8f, 1f);
        return f;
    }

    /**
     * A cruise missile (slice 9c, DESIGN.md §17.11) lit in its launcher's box at {@code pad} (its tail), the box pointing
     * along {@code heading} (radians, as {@link Flight#heading}) at 45 degrees: out of the box on its booster, a level turn
     * onto the target and low over the ground to it (see {@link CruisePlan}).
     */
    public static Flight launchCruise(ServerLevel level, Vec3 pad, double heading, Vec3 target, dev.groundburst.content.assembly.MissileSpec spec,
            @Nullable Entity owner) {
        CruisePlan plan = CruisePlan.of(pad, heading, target);
        plan.survey(level);
        Flight f = new Flight(UUID.randomUUID(), pad, target, 0, plan.ticks(), spec.warhead().copyWithCount(1), owner == null ? null : owner.getUUID());
        f.body = spec.body();
        f.hardened = spec.hardened();
        f.plan = plan;
        f.yaw = (float) Math.toDegrees(heading);              // lying along its box until it moves
        f.tilt = 45f;
        Flights fs = of(level);
        fs.flights.add(f);
        fs.setDirty();
        f.keepLoaded(level);
        f.proxy(level);
        level.playSound(null, pad.x, pad.y, pad.z, GBLaunch.CRUISE_LAUNCH.get(), SoundSource.BLOCKS, 6f, 1f);
        return f;
    }

    /**
     * A cruise missile's way to its target (DESIGN.md §17.11), planned at launch and fixed after (so where it is at any
     * tick is known, as for a ballistic flight, and the chunks ahead can be loaded): along the ground, its path is
     * {@value #BOOST} blocks straight out of its box along the launcher's heading, an arc of a circle turning it onto the
     * target's bearing, and a straight run to the target; over the ground, it climbs at 45 degrees on its booster, then
     * holds {@value #HEIGHT} blocks over the ground under it (sampled every {@value #STEP} blocks at launch: the real
     * ground where it is loaded, the generator's surface (and a tree's height) where not), climbing early for a ridge and
     * easing down after it, smoothed; {@value #POP} blocks short of the target it pops up and from {@value #DIVE} blocks
     * short it dives onto it (the dive's foot is the target's ground as the flight knows it then).
     */
    public static final class CruisePlan {
        /**
         * Ticks it burns in its box before it moves; ticks after that its wings stay stowed (until they are clear of the
         * box's mouth: 3 blocks out along the box); the boost (ticks, blocks along the ground); speed, blocks a tick.
         */
        public static final int IGNITION = 10, FOLDED = 6, BOOST_TICKS = 22;
        public static final double BOOST = 24, SPEED = 1.6;
        /** Its height over the ground; how the ground is sampled; the most it climbs and sinks per block along. */
        public static final double HEIGHT = 40, STEP = 4, CLIMB = 0.6, SINK = 0.5;
        /** The pop-up (how far short of the target it starts; how high over the target's ground at its top) and the dive. */
        public static final double POP = 50, POP_HEIGHT = 55, DIVE = 30;
        /** What an unloaded column's generated surface leaves out: its trees. */
        static final double TREES = 8;

        final Vec3 start;
        final double heading;
        private final double hx, hz;
        private final double cx, cz, r, theta0, sweep, qx, qz, tx, tz, arc, line;
        private final int side;
        /** Its height every STEP blocks along the ground (null until surveyed). */
        private float @Nullable [] alt;

        private CruisePlan(Vec3 start, double heading, double cx, double cz, double r, int side, double theta0, double sweep, double qx,
                double qz, double tx, double tz) {
            this.start = start;
            this.heading = heading;
            this.hx = Math.sin(heading);
            this.hz = Math.cos(heading);
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            this.side = side;
            this.theta0 = theta0;
            this.sweep = sweep;
            this.qx = qx;
            this.qz = qz;
            this.tx = tx;
            this.tz = tz;
            this.arc = r * sweep;
            this.line = Math.hypot(tx - qx, tz - qz);
        }

        /** The path along the ground from {@code start} on {@code heading} to {@code target} (its height comes from {@link #survey}). */
        static CruisePlan of(Vec3 start, double heading, Vec3 target) {
            double hx = Math.sin(heading), hz = Math.cos(heading);
            double ex = start.x + hx * BOOST, ez = start.z + hz * BOOST;
            double wx = target.x - ex, wz = target.z - ez;
            int side = hx * wz - hz * wx >= 0 ? 1 : -1;      // the target's side of its heading: the way it turns
            double px = -hz * side, pz = hx * side;            // toward the turn's middle
            double dist = Math.hypot(wx, wz);
            double r = Mth.clamp(dist / 4, 12, 48);
            for (int k = 0; k < 4; k++) {
                double cx = ex + px * r, cz = ez + pz * r;
                double rho = Math.hypot(target.x - cx, target.z - cz);
                if (rho > r * 1.05) {
                    double theta0 = Math.atan2(ez - cz, ex - cx);
                    double psi = Math.atan2(target.z - cz, target.x - cx), a = Math.acos(r / rho);
                    double best = Double.NaN;
                    for (double th : new double[] {psi + a, psi - a}) {
                        double vx = -Math.sin(th) * side, vz = Math.cos(th) * side;
                        double qx = cx + r * Math.cos(th), qz = cz + r * Math.sin(th);
                        if (vx * (target.x - qx) + vz * (target.z - qz) > 0) {
                            best = th;
                        }
                    }
                    if (!Double.isNaN(best)) {
                        double sweep = Mth.positiveModulo(side * (best - theta0), Math.PI * 2);
                        return new CruisePlan(start, heading, cx, cz, r, side, theta0, sweep, cx + r * Math.cos(best), cz + r * Math.sin(best),
                                target.x, target.z);
                    }
                }
                r /= 2;                                        // the target inside its turn: turn tighter
            }
            // Too close to turn onto: straight at it from the end of the boost.
            return new CruisePlan(start, heading, ex, ez, 0, side, 0, 0, ex, ez, target.x, target.z);
        }

        /** Along the ground from its launcher to the target, blocks. */
        double length() {
            return BOOST + this.arc + this.line;
        }

        /** Ticks from lift-off (the end of its ignition) to the target. */
        int ticks() {
            return BOOST_TICKS + (int) Math.ceil((length() - BOOST) / SPEED);
        }

        /** How far along the ground it is {@code t} ticks after lift-off: speeding up on the booster, then steady. */
        double along(double t) {
            if (t <= 0) {
                return 0;
            }
            if (t <= BOOST_TICKS) {
                return BOOST * Math.pow(t / BOOST_TICKS, 1.5);
            }
            return Math.min(length(), BOOST + SPEED * (t - BOOST_TICKS));
        }

        /** Where it is over the ground {@code d} blocks along its path (x, z). */
        double[] ground(double d) {
            if (d <= BOOST) {
                return new double[] {this.start.x + this.hx * d, this.start.z + this.hz * d};
            }
            d -= BOOST;
            if (this.r > 0 && d <= this.arc) {
                double th = this.theta0 + this.side * d / this.r;
                return new double[] {this.cx + this.r * Math.cos(th), this.cz + this.r * Math.sin(th)};
            }
            d -= this.arc;
            double u = this.line > 1e-6 ? Math.min(1, d / this.line) : 1;
            return new double[] {this.qx + (this.tx - this.qx) * u, this.qz + (this.tz - this.qz) * u};
        }

        /** Its height {@code d} blocks along, the target's ground {@code targetY} under the dive's foot. */
        double height(double d, double targetY) {
            if (d <= BOOST) {
                return this.start.y + d;
            }
            double length = length();
            double popStart = Math.max(BOOST, length - POP), diveStart = Math.max(popStart, length - DIVE);
            if (d < popStart) {
                return profile(d);
            }
            double base = profile(popStart);
            double top = Math.max(base, targetY + POP_HEIGHT);
            if (d < diveStart) {
                return Mth.lerp((d - popStart) / Math.max(1e-6, diveStart - popStart), base, top);
            }
            return Mth.lerp((d - diveStart) / Math.max(1e-6, length - diveStart), top, targetY);
        }

        /** Whether it is in its dive ({@code d} blocks along). */
        boolean diving(double d) {
            return d >= Math.max(BOOST, length() - DIVE);
        }

        private double profile(double d) {
            if (this.alt == null || this.alt.length == 0) {
                return this.start.y + BOOST;
            }
            double i = Math.clamp(d / STEP, 0, this.alt.length - 1);
            int a = (int) Math.floor(i), b = Math.min(this.alt.length - 1, a + 1);
            return Mth.lerp(i - a, this.alt[a], this.alt[b]);
        }

        /** Samples the ground along its path and plans its height over it (at launch). */
        void survey(ServerLevel level) {
            int n = (int) Math.ceil(length() / STEP) + 1;
            int boostEnd = (int) Math.floor(BOOST / STEP);
            double[] want = new double[n];
            for (int i = 0; i < n; i++) {
                double[] at = ground(Math.min(length(), i * STEP));
                want[i] = groundAt(level, at[0], at[1]) + HEIGHT;
            }
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                a[i] = i <= boostEnd ? this.start.y + i * STEP : want[i];
            }
            // Climb early enough for what is ahead, sink no faster than it may after what is behind (the boost included).
            for (int i = n - 2; i > boostEnd; i--) {
                a[i] = Math.max(a[i], a[i + 1] - CLIMB * STEP);
            }
            for (int i = boostEnd + 1; i < n; i++) {
                a[i] = Math.max(a[i], a[i - 1] - SINK * STEP);
            }
            // Smoothed (five samples), never far under what it wants.
            float[] out = new float[n];
            for (int i = 0; i < n; i++) {
                if (i <= boostEnd) {
                    out[i] = (float) a[i];
                    continue;
                }
                double sum = 0;
                int c = 0;
                for (int k = Math.max(boostEnd, i - 2); k <= Math.min(n - 1, i + 2); k++) {
                    sum += a[k];
                    c++;
                }
                out[i] = (float) Math.max(sum / c, want[i] - 3);
            }
            this.alt = out;
        }

        /** The top of the ground at (x, z): the real top where its chunk is loaded, the generator's surface (and trees) where not. */
        private static double groundAt(ServerLevel level, double x, double z) {
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (level.hasChunk(bx >> 4, bz >> 4)) {
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            }
            var chunks = level.getChunkSource();
            return chunks.getGenerator().getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, level, chunks.randomState()) + TREES;
        }

        List<Float> altList() {
            List<Float> out = new ArrayList<>();
            if (this.alt != null) {
                for (float v : this.alt) {
                    out.add(v);
                }
            }
            return out;
        }

        void setAlt(List<Float> list) {
            this.alt = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                this.alt[i] = list.get(i);
            }
        }
    }

    /**
     * Shot down by an interceptor (DESIGN.md §18.2): the flight ends where it is, its warhead never going off (the
     * interceptor's side draws the break-up). False if it was no longer in the air.
     */
    public boolean shootDown(ServerLevel level, Flight f) {
        if (!this.flights.remove(f)) {
            return false;
        }
        f.finish(level);
        setDirty();
        return true;
    }

    /** A server tick: every flight moves on (or hits). */
    void tick(ServerLevel level) {
        if (this.flights.isEmpty()) {
            return;
        }
        for (Flight f : List.copyOf(this.flights)) {
            if (f.tick(level)) {
                this.flights.remove(f);
            }
        }
        setDirty();
    }

    /**
     * A laser-guided missile's seeker (DESIGN.md §17.13): the network whose designators it answers (0: its launcher's
     * player's), where it was aimed, and how far round that it looks for a spot. Once clear of its climb it follows the
     * laser of the player who launched it (a designator's call for fire), wherever it points: that player leads it in.
     * Without that laser, coming down (from half way) it homes on a spot of its network's designators near its aim.
     */
    static final class Seeker {
        /** It looks this far round its aim, and this share of its range more. */
        static final double REACH = 48, SHARE = 0.05;
        /** It follows its launcher's laser from this far through its flight, any other spot from this far; its target moves at most this far a tick. */
        static final double LEAD_FROM = 0.12, FROM = 0.5, STEP = 1.5;
        static final Codec<Seeker> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("net").forGetter(s -> s.net), Vec3.CODEC.fieldOf("aim").forGetter(s -> s.aim),
                Codec.DOUBLE.fieldOf("reach").forGetter(s -> s.reach)).apply(i, Seeker::new));

        long net;
        final Vec3 aim;
        final double reach;
        /** Homing on a spot now (not saved: it looks again next tick). */
        boolean locked;

        Seeker(long net, Vec3 aim, double reach) {
            this.net = net;
            this.aim = aim;
            this.reach = reach;
        }
    }

    /** One missile's flight: a fixed curve from its pad to its target (see {@link #path}). */
    public static final class Flight {
        final UUID id;
        final Vec3 start;
        Vec3 target;
        final double apex;
        final int flightTicks;
        int age;
        final ItemStack warhead;
        final @Nullable UUID owner;
        /** The bomb scale it was launched at (GameTests' shrunken blasts; 1 in play, and after a reload; an RV its bus's). */
        float scale = dev.groundburst.content.ordnance.charge.Charges.bombScale;
        private final LongOpenHashSet tickets = new LongOpenHashSet();
        private Vec3 pos;
        private float yaw, tilt;
        private boolean whistled;
        /** Flown through an EMP field: half the time its fuze is dead (checked once, the first time); never if hardened. */
        private boolean dud, empChecked;
        private boolean hardened;
        private SizeClass body = SizeClass.M;
        /** Launched from a silo: how far below the ground at the shaft's mouth its pad is (0: a pad on the surface). */
        private double silo;
        /** A cruise missile's plan (null: a ballistic missile on its arc). */
        private @Nullable CruisePlan plan;
        /** A laser-guided missile's seeker (null: none). */
        private @Nullable Seeker seeker;
        /** A MIRV's bus (DESIGN.md §17.14): how many of its re-entry vehicles it has let go. */
        private int released;
        /**
         * A re-entry vehicle let go by a MIRV's bus (null: not one): the first bend of its path, on from where it left
         * along the bus's heading; its path then comes steeply down onto its point.
         */
        private @Nullable Vec3 lead;
        /**
         * The missile it put in the world last (not saved). The level cannot find an entity in a chunk whose entities are
         * not active yet, and its id then stops a new one being added; held here, it is moved along all the same.
         */
        private @Nullable MissileEntity shown;

        Flight(UUID id, Vec3 start, Vec3 target, double apex, int flightTicks, ItemStack warhead, @Nullable UUID owner) {
            this.id = id;
            this.start = start;
            this.target = target;
            this.apex = apex;
            this.flightTicks = flightTicks;
            this.warhead = warhead;
            this.owner = owner;
            this.pos = start;
        }

        public Vec3 position() {
            return this.pos;
        }

        public Vec3 target() {
            return this.target;
        }

        /** Its id (its entity's, and what interceptors sent after it know it by). */
        public UUID id() {
            return this.id;
        }

        /** Its body's size class (how hard it is to kill: DESIGN.md §18.2). */
        public SizeClass body() {
            return this.body;
        }

        /** The warhead it carries. */
        public ItemStack warhead() {
            return this.warhead;
        }

        /** Where it will be {@code ticks} from now, on its path (at its target once it would have come down). */
        public Vec3 positionIn(int ticks) {
            return at(Math.min(this.age + Math.max(0, ticks), ignition() + this.flightTicks));
        }

        /** Its warhead's fuze (an impact fuze if it carries none). */
        FuzeSettings fuze() {
            return this.warhead.getOrDefault(dev.groundburst.registry.GBDataComponents.FUZE.get(), FuzeSettings.IMPACT);
        }

        /**
         * The warhead bursts in the air at {@code at} (not when it flew through an EMP and its fuze died). True if it
         * burst (the flight is then over).
         */
        boolean burst(ServerLevel level, Vec3 at) {
            if (this.dud || !(this.warhead.getItem() instanceof BlockItem b) || !(b.getBlock() instanceof BombBlock bomb)) {
                return false;
            }
            Entity by = this.owner == null ? null : level.getEntity(this.owner);
            Charges.scaled(this.scale, () -> Charges.burst(level, bomb.kind, at, by));
            finish(level);
            return true;
        }

        /** A cruise missile (slice 9c): low over the ground on its plan. */
        public boolean cruise() {
            return this.plan != null;
        }

        /** A MIRV's bus (DESIGN.md §17.14): it carries re-entry vehicles, and lets them go coming down. */
        public boolean bus() {
            return this.plan == null && dev.groundburst.content.assembly.Mirv.isBus(this.warhead);
        }

        /** A re-entry vehicle a MIRV's bus let go: a warhead (or a decoy) falling on its own. */
        public boolean rv() {
            return this.lead != null;
        }

        /** A decoy: an RV that carries nothing (radar and interceptors take it for a warhead). */
        public boolean decoy() {
            return rv() && this.warhead.getItem() instanceof dev.groundburst.content.assembly.MirvDecoyItem;
        }

        /** Ticks it burns where it was lit before it moves: a cruise missile's booster less than a rocket's ignition; an RV none. */
        int ignition() {
            return this.lead != null ? 0 : this.plan != null ? CruisePlan.IGNITION : MissileEntity.IGNITION;
        }

        /** How far through its flight it is: 0 on the pad, 1 at the target. */
        public float progress() {
            return this.age < ignition() ? 0 : Math.min(1, (this.age - ignition()) / (float) this.flightTicks);
        }

        /** Spent stages it has dropped so far (a cruise missile's booster, once its boost is done; an RV has none). */
        public int dropped() {
            if (this.lead != null) {
                return 0;
            }
            if (this.plan != null) {
                return this.age >= ignition() + CruisePlan.BOOST_TICKS ? 1 : 0;
            }
            float s = progress();
            int n = 0;
            for (float drop : dev.groundburst.content.assembly.MissileParts.stageDrops(this.body)) {
                n += s >= drop ? 1 : 0;
            }
            return n;
        }

        /** Whether an engine is burning: on the pad, and climbing until its last stage burns out (a cruise missile's jet: always). */
        public boolean burning() {
            return this.lead == null && (this.plan != null || this.age < MissileEntity.IGNITION
                    || progress() < dev.groundburst.content.assembly.MissileParts.burnout(this.body));
        }

        /**
         * How hot its nose glows coming back down: from past the top of its arc, brighter the higher it came from (a
         * short flight barely warms; a cruise missile never).
         */
        public float reentry() {
            if (this.lead != null) {
                return Math.min(1f, 0.55f + this.age / 10f);             // an RV: white-hot from the moment it parts
            }
            if (this.plan != null) {
                return 0;
            }
            float s = progress();
            if (s < 0.55f || s >= 1) {
                return 0;
            }
            float height = (float) Math.clamp((this.apex - 60) / 160, 0.25, 1);
            float t = Math.min(1, (s - 0.55f) / 0.25f);
            return height * t * t * (3 - 2 * t);
        }

        /**
         * The seeker's step (DESIGN.md §17.13): the spot it answers (its network's designators', or its launcher's
         * player's) nearest its target within its reach of where it was aimed, and the target moved toward it, a block a
         * tick at most, so the missile turns smoothly onto it and follows it as it moves. False with no spot: it flies on
         * as it was aimed.
         */
        private boolean home(ServerLevel level, double s) {
            Designators.Spot spot = this.owner == null ? null : Designators.spotOf(level, this.owner);    // led in, wherever it points
            if (spot == null && s >= Seeker.FROM) {
                spot = Designators.spotFor(level, this.seeker.net, this.owner, this.seeker.aim, this.seeker.reach, this.target);
            }
            if (spot == null) {
                this.seeker.locked = false;
                return false;
            }
            this.seeker.locked = true;
            Designators.homing(level, spot.player());
            Vec3 d = spot.pos().subtract(this.target);
            double len = d.length();
            this.target = len <= Seeker.STEP ? spot.pos() : this.target.add(d.scale(Seeker.STEP / len));
            return true;
        }

        /** A laser-guided missile (it has a seeker). */
        public boolean laserGuided() {
            return this.seeker != null;
        }

        /** Homing on a laser spot right now. */
        public boolean homing() {
            return this.seeker != null && this.seeker.locked;
        }

        /** Its seeker answers this network's designators (from its launcher, if that is on one). */
        public void seekerNetwork(long net) {
            if (this.seeker != null) {
                this.seeker.net = net;
            }
        }

        /** A short id for radar screens to follow it by. */
        public int trackId() {
            return (int) (this.id.getMostSignificantBits() ^ this.id.getLeastSignificantBits());
        }

        /** For pictures and tests: jump ahead along its flight. */
        public void skipForTest(int ticks) {
            this.age += Math.max(0, ticks);
            this.pos = at(this.age);
        }

        /** Ticks until it comes down (if nothing is in its way). */
        public int ticksToImpact() {
            return Math.max(0, ignition() + this.flightTicks - this.age);
        }

        /** Which way it is heading over the ground (radians, 0 = south, as atan2(dx, dz)). */
        public float heading() {
            return (float) Math.toRadians(this.yaw);
        }

        /** How far a missile climbs past a silo's mouth before it turns (so its body clears the mouth as it tilts). */
        public static final double CLEAR_MOUTH = 1.5;

        /**
         * Where it is {@code u} (0-1) of the way along its path: a cubic curve up off the pad and down onto the target.
         * From a silo the path first runs straight up the shaft to just past its mouth, then the curve takes over from
         * there; the straight part takes the share of {@code u} that makes the missile's speed the same on both sides of
         * the join (the curve starts straight up, so the turn is smooth too).
         */
        private Vec3 path(double u) {
            Vec3 from = this.start;
            if (this.silo > 0) {
                double rise = this.silo + CLEAR_MOUTH;
                double u0 = rise / (rise + 3 * this.apex * 1.35);    // the curve sets off at 3 * |p1 - p0| per unit of u
                if (u < u0) {
                    return this.start.add(0, rise * u / u0, 0);
                }
                from = this.start.add(0, rise, 0);
                u = (u - u0) / (1 - u0);
            }
            Vec3 up = new Vec3(0, this.apex * 1.35, 0);
            Vec3 p1 = from.add(up);
            Vec3 back = new Vec3(from.x - this.target.x, 0, from.z - this.target.z).scale(0.12);
            Vec3 p2 = this.target.add(up).add(back);
            double v = 1 - u;
            return from.scale(v * v * v).add(p1.scale(3 * v * v * u)).add(p2.scale(3 * v * u * u)).add(this.target.scale(u * u * u));
        }

        /**
         * An RV's way down, {@code u} (0-1) along it: a cubic curve from where it left its bus, bending on along the bus's
         * heading ({@link #lead}), then steeply down onto its point.
         */
        private Vec3 rvPath(double u) {
            Vec3 p0 = this.start, p1 = this.lead, p3 = this.target;
            Vec3 p2 = p3.add(0, Math.max(8, (p0.y - p3.y) * 0.45), 0);
            double v = 1 - u;
            return p0.scale(v * v * v).add(p1.scale(3 * v * v * u)).add(p2.scale(3 * v * u * u)).add(p3.scale(u * u * u));
        }

        /** Launched from a silo: the ground at the shaft's mouth, over its pad (null: launched from a pad). */
        public @Nullable Vec3 siloMouth() {
            return this.silo > 0 ? this.start.add(0, this.silo, 0) : null;
        }

        /** Still in its silo's shaft (its tail below the mouth). */
        public boolean inShaft() {
            return this.silo > 0 && this.pos.y < this.start.y + this.silo;
        }

        /** Where it is at {@code age} ticks after lighting (on the pad through its ignition). */
        private Vec3 at(int age) {
            if (this.lead != null) {
                return rvPath(Math.pow(Math.min(1, Math.max(0, age) / (double) this.flightTicks), 1.15));
            }
            if (age < ignition()) {
                return this.start;
            }
            if (this.plan != null) {
                double d = this.plan.along(age - ignition());
                double[] g = this.plan.ground(d);
                return new Vec3(g[0], this.plan.height(d, this.target.y), g[1]);
            }
            double s = Math.min(1, (age - MissileEntity.IGNITION) / (double) this.flightTicks);
            return path(Math.pow(s, 1.25));                 // slow off the pad, fastest coming down
        }

        /** Moves it on a tick; true once it has hit (and is done). */
        boolean tick(ServerLevel level) {
            this.age++;
            keepLoaded(level);
            if (this.age >= ignition()) {
                double s = Math.min(1, (this.age - ignition()) / (double) this.flightTicks);
                // Coming down, a laser-guided missile homes on its spot; otherwise the ground at the target, once its
                // chunk is in (it is ticket-loaded from launch).
                boolean homing = this.seeker != null && this.plan == null && this.released == 0 && s >= Seeker.LEAD_FROM && home(level, s);
                BlockPos tc = BlockPos.containing(this.target);
                if (!homing && level.hasChunkAt(tc)) {
                    this.target = new Vec3(this.target.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, tc.getX(), tc.getZ()), this.target.z);
                }
                Vec3 from = this.pos, to = at(this.age);
                if (!this.empChecked && dev.groundburst.content.effect.EmpFields.inField(level, to)) {
                    this.empChecked = true;
                    this.dud = !this.hardened && level.getRandom().nextBoolean();
                }
                Vec3 v = to.subtract(from);
                // It can only hit what is loaded; past the first stretch of its climb (clear of its own gantry, or of its
                // silo's shaft and mouth: straight up the middle of the open shaft, it cannot touch it; a cruise missile
                // once its booster has carried it clear of its box).
                boolean climbingOut = this.silo > 0 && to.y < this.start.y + this.silo + CLEAR_MOUTH;
                boolean clear = this.lead != null || (this.plan != null ? this.age > ignition() + CruisePlan.BOOST_TICKS : s > 0.1);
                if (clear && !climbingOut && level.hasChunkAt(BlockPos.containing(from)) && level.hasChunkAt(BlockPos.containing(to))) {
                    BlockHitResult hit = level.clip(new ClipContext(from, to.add(v.normalize().scale(0.5)), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
                    if (hit.getType() != HitResult.Type.MISS) {
                        impact(level, hit.getLocation());
                        return true;
                    }
                }
                if (v.lengthSqr() > 1e-6) {
                    this.yaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));
                    this.tilt = (float) Math.toDegrees(Math.atan2(Math.sqrt(v.x * v.x + v.z * v.z), v.y));
                }
                this.pos = to;
                if (bus() && release(level, v)) {
                    return true;                              // its last RV gone: the spent bus breaks up
                }
                // A barometric fuze bursts the warhead at its set height over the target on the way down; a cluster-type
                // warhead on an impact fuze still opens 30 blocks over its target. Otherwise it goes off where it hits.
                if (this.warhead.getItem() instanceof BlockItem b && b.getBlock() instanceof BombBlock bomb) {
                    FuzeSettings fuze = fuze();
                    // (a cruise missile sinks over falling ground too: only its dive counts)
                    boolean falling = this.plan != null ? this.plan.diving(this.plan.along(this.age - ignition()))
                            : (this.lead != null || s > 0.5) && v.y < 0;
                    double over = to.y - this.target.y;
                    double height = fuze.fuze() == Fuze.BAROMETRIC ? fuze.value() : bomb.kind.airburst() ? Charges.AIRBURST_HEIGHT : -1;
                    boolean burst = falling && height >= 0 && over <= height;
                    if (burst && burst(level, to)) {
                        return true;
                    }
                }
                if (s >= 1) {
                    impact(level, this.target);
                    return true;
                }
            }
            MissileEntity proxy = proxy(level);
            if (proxy != null) {
                // (Its engine's roar is the client's own, following it: MissileSounds.)
                int left = ignition() + this.flightTicks - this.age;
                if (!this.whistled && left <= WHISTLE_AT && left > 0) {
                    this.whistled = true;                     // the dive: it screams down on its target
                    level.playSound(null, proxy, GBOrdnance.BOMB_WHISTLE.get(), SoundSource.BLOCKS, 8f, 0.8f);
                }
            }
            return false;
        }

        /**
         * The missile players see, where its chunk is loaded: found, made, or moved along. It is only made where entities
         * are active (one added where they are not would wait there unseen, and its id would stop another being added).
         */
        private @Nullable MissileEntity proxy(ServerLevel level) {
            Entity e = level.getEntity(this.id);
            if (!(e instanceof MissileEntity) && this.shown != null && !this.shown.isRemoved()) {
                e = this.shown;                                // (where the level cannot see it: moving it brings it out)
            }
            BlockPos at = BlockPos.containing(this.pos);
            boolean loaded = level.hasChunkAt(at);
            if (e instanceof MissileEntity m && !m.isRemoved()) {
                if (!loaded) {
                    m.discard();                               // on into unloaded ground: put back when it is loaded
                    this.shown = null;
                    return null;
                }
                m.follow(this.pos, this.yaw, this.tilt, this.age, dropped(), burning(), reentry());
                return m;
            }
            if (!loaded || !level.isPositionEntityTicking(at)) {
                return null;
            }
            MissileEntity m = new MissileEntity(GBLaunch.MISSILE.get(), level);
            m.setUUID(this.id);
            if (this.lead != null) {
                m.setRvLook(this.body);
            } else {
                m.setLook(this.warhead, this.body, this.plan != null);
            }
            m.setSilo(siloMouth());
            m.setPos(this.pos);
            m.follow(this.pos, this.yaw, this.tilt, this.age, dropped(), burning(), reentry());
            if (!level.addFreshEntity(m)) {
                return null;
            }
            this.shown = m;
            return m;
        }

        /** Keeps the target's chunk, the chunk it is in and the next few along its path loaded (tickets). */
        void keepLoaded(ServerLevel level) {
            LongOpenHashSet want = new LongOpenHashSet();
            want.add(chunk(this.target));
            for (int k = 0; k <= LOOKAHEAD; k += LOOKAHEAD_STEP) {
                want.add(chunk(at(this.age + k)));
            }
            for (long c : this.tickets.toLongArray()) {
                if (!want.contains(c)) {
                    GBLaunch.TICKETS.forceChunk(level, this.id, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
                    this.tickets.remove(c);
                }
            }
            for (long c : want) {
                if (this.tickets.add(c)) {
                    GBLaunch.TICKETS.forceChunk(level, this.id, ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
                }
            }
        }

        private static long chunk(Vec3 p) {
            BlockPos b = BlockPos.containing(p);
            return ChunkPos.pack(b.getX() >> 4, b.getZ() >> 4);
        }

        /** It hits: its warhead goes off where it hit (a bunker buster bores in first; an empty one does nothing). */
        private void impact(ServerLevel level, Vec3 at) {
            Entity by = this.owner == null ? null : level.getEntity(this.owner);
            if (this.dud || decoy()) {
                // A dead fuze (or a decoy): it just slams into the ground.
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 20, 0.6, 0.4, 0.6, 0.05);
                level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2f, 0.5f);
            } else if (this.warhead.getItem() instanceof BlockItem b && b.getBlock() instanceof BombBlock bomb) {
                Charges.scaled(this.scale, () -> Charges.impact(level, bomb.kind, BlockPos.containing(at.add(0, 0.5, 0)), by));
            }
            finish(level);
        }

        /**
         * A MIRV's bus, coming down (DESIGN.md §17.14): from {@link #MIRV_SPLIT} of its flight it lets its RVs go, one every
         * {@link #MIRV_GAP} ticks (closer together on a short flight, so the last leaves well before it would come down),
         * each to its point of the bus's spread round where the bus is aimed. True once the last has gone: the spent bus
         * breaks up.
         */
        private boolean release(ServerLevel level, Vec3 v) {
            int split = ignition() + (int) Math.ceil(MIRV_SPLIT * this.flightTicks);
            if (this.age < split) {
                return false;
            }
            List<ItemStack> rvs = dev.groundburst.content.assembly.Mirv.rvs(this.warhead);
            int n = rvs.size();
            double gap = n <= 1 ? 0 : Math.min(MIRV_GAP, (ignition() + this.flightTicks - split) * 0.5 / (n - 1));
            List<int[]> points = null;
            while (this.released < n && this.age >= split + Math.round(gap * this.released)) {
                if (points == null) {
                    points = dev.groundburst.content.assembly.Mirv.points(this.warhead, BlockPos.containing(this.start),
                            Mth.floor(this.target.x), Mth.floor(this.target.z));
                }
                letGo(level, rvs.get(this.released), points.get(this.released), v);
                this.released++;
            }
            if (this.released < n) {
                return false;
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, true, true, this.pos.x, this.pos.y, this.pos.z, 12, 0.6, 0.6, 0.6, 0.05);
            finish(level);
            return true;
        }

        /**
         * One RV leaves the bus for {@code point}: a little to that side of the bus, moving as it does, then on its own way
         * down; it flies with the bus's owner, hardening, fuze state and blast scale.
         */
        private void letGo(ServerLevel level, ItemStack rv, int[] point, Vec3 v) {
            Vec3 aim = new Vec3(point[0] + 0.5, this.target.y, point[1] + 0.5);
            Vec3 side = new Vec3(aim.x - this.target.x, 0, aim.z - this.target.z);
            Vec3 from = this.pos.add(side.lengthSqr() > 1e-6 ? side.normalize().scale(0.6) : Vec3.ZERO);
            double speed = Math.max(2.5, v.length());
            Vec3 heading = v.lengthSqr() > 1e-6 ? v.normalize() : new Vec3(0, -1, 0);
            double dist = from.distanceTo(aim);
            int ticks = Math.max(12, (int) Math.ceil(dist * 1.2 / (speed * 1.1)));
            Flight f = new Flight(UUID.randomUUID(), from, aim, 0, ticks, rv.copyWithCount(1), this.owner);
            f.lead = from.add(heading.scale(dist * 0.3));
            f.body = this.body;
            f.hardened = this.hardened;
            f.scale = this.scale;
            f.dud = this.dud;
            f.empChecked = this.empChecked;
            f.yaw = this.yaw;
            f.tilt = this.tilt;
            Flights fs = of(level);
            fs.flights.add(f);
            fs.setDirty();
            f.keepLoaded(level);
            f.proxy(level);
            level.playSound(null, from.x, from.y, from.z, GBLaunch.RV_RELEASE.get(), SoundSource.BLOCKS, 6f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        }

        /** Done: the missile players see goes, and so do its chunk tickets. */
        private void finish(ServerLevel level) {
            if (level.getEntity(this.id) instanceof MissileEntity m) {
                m.discard();
            }
            if (this.shown != null) {
                this.shown.discard();
                this.shown = null;
            }
            for (long c : this.tickets.toLongArray()) {
                GBLaunch.TICKETS.forceChunk(level, this.id, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
            }
            this.tickets.clear();
        }
    }

}
