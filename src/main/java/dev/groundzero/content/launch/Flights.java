package dev.groundzero.content.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.Fuze;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.assembly.FuzeSettings;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.BombBlock;
import dev.groundzero.content.ordnance.charge.Charges;
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
 * the flight and put back wherever its chunk is loaded. Saved with the world ({@code data/groundzero/missile_flights.dat}).
 */
public final class Flights extends SavedData {
    /** How far ahead along its path a flight keeps chunks loaded (ticks; points every LOOKAHEAD_STEP ticks). */
    private static final int LOOKAHEAD = 30, LOOKAHEAD_STEP = 6;
    /** The whistle of the dive: this many ticks before impact. */
    private static final int WHISTLE_AT = 50;

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
            Codec.STRING.optionalFieldOf("body", "M").forGetter(f -> f.body.name())
    ).apply(i, (id, start, target, apex, ticks, age, dud, warhead, owner, hardened, body) -> {
        Flight f = new Flight(id, start, target, apex, ticks, warhead, owner.orElse(null));
        f.age = age;
        f.dud = dud;
        f.hardened = hardened;
        f.body = SizeClass.valueOf(body);
        return f;
    }));
    static final SavedDataType<Flights> TYPE = new SavedDataType<>(GroundZero.id("missile_flights"), Flights::new,
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
    public static Flight launch(ServerLevel level, Vec3 pad, Vec3 target, dev.groundzero.content.assembly.MissileSpec spec, @Nullable Entity owner) {
        return launch(level, pad, target, spec.warhead(), spec.body(), spec.hardened(), owner);
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
        return launch(level, pad, target, warhead, SizeClass.M, false, owner);
    }

    private static Flight launch(ServerLevel level, Vec3 pad, Vec3 target, ItemStack warhead, SizeClass body, boolean hardened, @Nullable Entity owner) {
        double range = Math.sqrt(Mth.square(target.x - pad.x) + Mth.square(target.z - pad.z));
        double apex = apex(range);
        int ticks = flightTicks(range);
        Flight f = new Flight(UUID.randomUUID(), pad, target, apex, ticks, warhead.copyWithCount(1), owner == null ? null : owner.getUUID());
        f.body = body;
        f.hardened = hardened;
        Flights fs = of(level);
        fs.flights.add(f);
        fs.setDirty();
        f.keepLoaded(level);
        f.proxy(level);
        level.playSound(null, pad.x, pad.y, pad.z, GZLaunch.MISSILE_LAUNCH.get(), SoundSource.BLOCKS, 8f, 1f);
        return f;
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
        private final LongOpenHashSet tickets = new LongOpenHashSet();
        private Vec3 pos;
        private float yaw, tilt;
        private boolean whistled;
        /** Flown through an EMP field: half the time its fuze is dead (checked once, the first time); never if hardened. */
        private boolean dud, empChecked;
        private boolean hardened;
        private SizeClass body = SizeClass.M;

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

        /** Its warhead's fuze (an impact fuze if it carries none). */
        FuzeSettings fuze() {
            return this.warhead.getOrDefault(dev.groundzero.registry.GZDataComponents.FUZE.get(), FuzeSettings.IMPACT);
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
            Charges.burst(level, bomb.kind, at, by);
            finish(level);
            return true;
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
            return Math.max(0, MissileEntity.IGNITION + this.flightTicks - this.age);
        }

        /** Which way it is heading over the ground (radians, 0 = south, as atan2(dx, dz)). */
        public float heading() {
            return (float) Math.toRadians(this.yaw);
        }

        /** Where it is {@code u} (0-1) of the way along its path: a cubic curve up off the pad and down onto the target. */
        private Vec3 path(double u) {
            Vec3 up = new Vec3(0, this.apex * 1.35, 0);
            Vec3 p1 = this.start.add(up);
            Vec3 back = new Vec3(this.start.x - this.target.x, 0, this.start.z - this.target.z).scale(0.12);
            Vec3 p2 = this.target.add(up).add(back);
            double v = 1 - u;
            return this.start.scale(v * v * v).add(p1.scale(3 * v * v * u)).add(p2.scale(3 * v * u * u)).add(this.target.scale(u * u * u));
        }

        /** Where it is at {@code age} ticks after lighting (on the pad through its ignition). */
        private Vec3 at(int age) {
            if (age < MissileEntity.IGNITION) {
                return this.start;
            }
            double s = Math.min(1, (age - MissileEntity.IGNITION) / (double) this.flightTicks);
            return path(Math.pow(s, 1.25));                 // slow off the pad, fastest coming down
        }

        /** Moves it on a tick; true once it has hit (and is done). */
        boolean tick(ServerLevel level) {
            this.age++;
            keepLoaded(level);
            if (this.age >= MissileEntity.IGNITION) {
                // The ground at the target, once its chunk is in (it is ticket-loaded from launch).
                BlockPos tc = BlockPos.containing(this.target);
                if (level.hasChunkAt(tc)) {
                    this.target = new Vec3(this.target.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, tc.getX(), tc.getZ()), this.target.z);
                }
                double s = Math.min(1, (this.age - MissileEntity.IGNITION) / (double) this.flightTicks);
                Vec3 from = this.pos, to = at(this.age);
                if (!this.empChecked && dev.groundzero.content.effect.EmpFields.inField(level, to)) {
                    this.empChecked = true;
                    this.dud = !this.hardened && level.getRandom().nextBoolean();
                }
                Vec3 v = to.subtract(from);
                // It can only hit what is loaded; past the first stretch of its climb (clear of its own gantry).
                if (s > 0.1 && level.hasChunkAt(BlockPos.containing(from)) && level.hasChunkAt(BlockPos.containing(to))) {
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
                // A barometric fuze bursts the warhead at its set height over the target on the way down; a cluster-type
                // warhead on an impact fuze still opens 30 blocks over its target. Otherwise it goes off where it hits.
                if (this.warhead.getItem() instanceof BlockItem b && b.getBlock() instanceof BombBlock bomb) {
                    FuzeSettings fuze = fuze();
                    boolean falling = s > 0.5 && v.y < 0;
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
                if (this.age % 40 == 1) {
                    level.playSound(null, proxy, GZLaunch.MISSILE_ENGINE.get(), SoundSource.BLOCKS, 6f, 1f);
                }
                int left = MissileEntity.IGNITION + this.flightTicks - this.age;
                if (!this.whistled && left <= WHISTLE_AT && left > 0) {
                    this.whistled = true;                     // the dive: it screams down on its target
                    level.playSound(null, proxy, GZOrdnance.BOMB_WHISTLE.get(), SoundSource.BLOCKS, 8f, 0.8f);
                }
            }
            return false;
        }

        /** The missile players see, where its chunk is loaded: found, made, or moved along. */
        private @Nullable MissileEntity proxy(ServerLevel level) {
            Entity e = level.getEntity(this.id);
            boolean loaded = level.hasChunkAt(BlockPos.containing(this.pos));
            if (e instanceof MissileEntity m && !m.isRemoved()) {
                if (!loaded) {
                    m.discard();                               // on into unloaded ground: put back when it is loaded
                    return null;
                }
                m.follow(this.pos, this.yaw, this.tilt, this.age);
                return m;
            }
            if (!loaded) {
                return null;
            }
            MissileEntity m = new MissileEntity(GZLaunch.MISSILE.get(), level);
            m.setUUID(this.id);
            m.setLook(this.warhead, this.body);
            m.setPos(this.pos);
            m.follow(this.pos, this.yaw, this.tilt, this.age);
            level.addFreshEntity(m);
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
                    GZLaunch.TICKETS.forceChunk(level, this.id, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
                    this.tickets.remove(c);
                }
            }
            for (long c : want) {
                if (this.tickets.add(c)) {
                    GZLaunch.TICKETS.forceChunk(level, this.id, ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
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
            if (this.dud) {
                // A dead fuze: it just slams into the ground.
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 20, 0.6, 0.4, 0.6, 0.05);
                level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2f, 0.5f);
            } else if (this.warhead.getItem() instanceof BlockItem b && b.getBlock() instanceof BombBlock bomb) {
                Charges.impact(level, bomb.kind, BlockPos.containing(at.add(0, 0.5, 0)), by);
            }
            finish(level);
        }

        /** Done: the missile players see goes, and so do its chunk tickets. */
        private void finish(ServerLevel level) {
            if (level.getEntity(this.id) instanceof MissileEntity m) {
                m.discard();
            }
            for (long c : this.tickets.toLongArray()) {
                GZLaunch.TICKETS.forceChunk(level, this.id, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
            }
            this.tickets.clear();
        }
    }

}
