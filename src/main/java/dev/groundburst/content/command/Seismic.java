package dev.groundburst.content.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.BlastProfile;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.launch.LaunchNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import dev.groundburst.port.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * The blasts the ground carries (DESIGN.md §17.17): every blast and tremor in a dimension, saved with it
 * ({@code seismic_events.dat}, the last {@value #KEEP}), for the Seismographs and the Dead Hand to feel. Each has where and
 * when, its magnitude (from its energy in tonnes of TNT: {@link #magnitude}), how long it shakes the ground (a blast at
 * once, a tremor over seconds), and, when a missile delivered it, the network that fired it and where from. Seismographs
 * read the events after the last one they took ({@link #after}), so one that was unloaded catches up when it loads.
 */
public final class Seismic extends SavedData {
    /** Events kept (a dimension's last; a seismograph unloaded for longer misses the older ones). */
    public static final int KEEP = 128;
    /** P waves (the first arrival): 6 km/s, blocks a tick; S waves (the bigger second one): 3.5 km/s. */
    public static final double P_SPEED = 300, S_SPEED = 175;
    /** A blast this near (blocks) and this soon (ticks) after a missile came down is that missile's. */
    static final double ORIGIN_REACH = 48;
    static final int ORIGIN_TICKS = 200;
    /** Felt magnitude is the magnitude less this much for each tenfold distance past 100 blocks (no nearer than 30). */
    public static final double FALL = 2.0, NEAR = 30, REFERENCE = 100;
    /** An airburst (higher over the ground than its crater is wide, and at least this high) couples this much less. */
    static final double AIRBURST_HEIGHT = 4;
    static final float AIRBURST_LOSS = 1.5f;

    /**
     * One blast or tremor: its id (rising), when it went off, where, its magnitude, how many ticks it shakes the ground
     * (0: a blast), the network whose missile delivered it (0: none known), and where that missile was fired from.
     */
    public record Event(long id, long time, double x, double y, double z, float magnitude, int duration, long network,
            Optional<Vec3> origin) {
        static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Event::id), Codec.LONG.fieldOf("time").forGetter(Event::time),
                Codec.DOUBLE.fieldOf("x").forGetter(Event::x), Codec.DOUBLE.fieldOf("y").forGetter(Event::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Event::z), Codec.FLOAT.fieldOf("magnitude").forGetter(Event::magnitude),
                Codec.INT.fieldOf("duration").forGetter(Event::duration), Codec.LONG.optionalFieldOf("network", 0L).forGetter(Event::network),
                Vec3.CODEC.optionalFieldOf("origin").forGetter(Event::origin)).apply(i, Event::new));

        public Vec3 at() {
            return new Vec3(this.x, this.y, this.z);
        }

        /** Its distance from {@code p}, blocks (through the ground: straight). */
        public double distance(Vec3 p) {
            return Math.sqrt((this.x - p.x) * (this.x - p.x) + (this.y - p.y) * (this.y - p.y) + (this.z - p.z) * (this.z - p.z));
        }

        /** Its first wave's arrival at {@code p} (game time). */
        public long arrival(Vec3 p) {
            return this.time + (long) Math.ceil(distance(p) / P_SPEED);
        }
    }

    /** A missile that came down lately: where, when, its network (0: none) and where it was fired from. */
    private record Impact(Vec3 at, long time, long network, Vec3 from) {}

    static final SavedDataType<Seismic> TYPE = new SavedDataType<>(GroundBurst.id("seismic_events"), Seismic::new,
            RecordCodecBuilder.<Seismic>create(i -> i.group(
                    Codec.LONG.fieldOf("next").forGetter(s -> s.nextId),
                    Event.CODEC.listOf().fieldOf("events").forGetter(s -> List.copyOf(s.events))).apply(i, (next, events) -> {
                        Seismic s = new Seismic();
                        s.nextId = next;
                        s.events.addAll(events);
                        return s;
                    })));

    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long nextId = 1;
    /** Missiles that came down in the last {@value #ORIGIN_TICKS} ticks (not saved). */
    private final List<Impact> impacts = new ArrayList<>();

    private Seismic() {}

    public static Seismic of(ServerLevel level) {
        return TYPE.get(level);
    }

    /** The events after {@code id}, oldest first (all kept if {@code id} is older than the oldest). */
    public List<Event> after(long id) {
        List<Event> out = new ArrayList<>();
        for (Event e : this.events) {
            if (e.id() > id) {
                out.add(e);
            }
        }
        return out;
    }

    /** The id of the newest event (0: none yet): a seismograph set down now starts after it. */
    public long latest() {
        Event e = this.events.peekLast();
        return e == null ? 0 : e.id();
    }

    /** The event with {@code id} (null: none kept). */
    public @Nullable Event get(long id) {
        for (Event e : this.events) {
            if (e.id() == id) {
                return e;
            }
        }
        return null;
    }

    // --- magnitude ---------------------------------------------------------------------------------------------------------
    /** The magnitude of a blast of {@code tonnes} of TNT: mb = 4.45 + 0.75 log10(kilotons), so 2.2 + 0.75 log10(tonnes). */
    public static float magnitude(double tonnes) {
        return (float) (2.2 + 0.75 * Math.log10(Math.max(1e-6, tonnes)));
    }

    /** The tonnes of TNT a magnitude stands for (the yield worked back from it). */
    public static double tonnes(float magnitude) {
        return Math.pow(10, (magnitude - 2.2) / 0.75);
    }

    /** How strongly a blast of magnitude {@code m} is felt {@code d} blocks away. */
    public static float felt(float m, double d) {
        return (float) (m - FALL * Math.log10(Math.max(NEAR, d) / REFERENCE));
    }

    /** The farthest a blast of magnitude {@code m} is felt at {@code atLeast} or more, blocks. */
    public static double reach(float m, float atLeast) {
        return REFERENCE * Math.pow(10, (m - atLeast) / FALL);
    }

    /** The nuclear warheads' design yields, tonnes (DESIGN.md §15; the stats screen's figures), by their profile's path. */
    private static final Map<String, Double> NUCLEAR = Map.of(
            "tactical_nuclear_warhead", 300.0, "fission_warhead", 15_000.0, "boosted_fission_warhead", 45_000.0,
            "neutron_warhead", 1_000.0, "thermonuclear_warhead", 1_200_000.0, "cobalt_warhead", 400_000.0,
            "tsar_warhead", 50_000_000.0, "nuclear_missile_warhead", 20_000.0);

    /** A blast's TNT figure, tonnes: half a kilogram times its crater radius cubed (the stats screen's), or a nuke's yield. */
    static double tonnes(ResourceKey<BlastProfile> key, BlastProfile base, BlastProfile scaled) {
        Double nuclear = NUCLEAR.get(key.location().getPath());
        if (nuclear != null) {
            double k = base.crater() > 0 ? scaled.crater() / base.crater() : 1;
            return nuclear * k * k * k;
        }
        double c = scaled.crater();
        return 0.0005 * c * c * c;
    }

    // --- what goes in ----------------------------------------------------------------------------------------------------
    /** A profile blast set off ({@code Blasts.detonate}): its profile as written and as scaled. */
    public static void blast(ServerLevel level, Vec3 at, ResourceKey<BlastProfile> key, BlastProfile base, BlastProfile scaled) {
        float m = magnitude(tonnes(key, base, scaled));
        if (airburst(level, at, scaled.crater())) {
            m -= AIRBURST_LOSS;
        }
        of(level).add(level, at, m, 0);
    }

    /** The game's own explosion or another mod's (NeoForge's explosion event): its TNT figure from its radius. */
    public static void explosion(ServerLevel level, Vec3 at, float radius) {
        double tonnes = 0.0005 * radius * radius * radius;
        float m = magnitude(tonnes);
        if (airburst(level, at, radius)) {
            m -= AIRBURST_LOSS;
        }
        of(level).add(level, at, m, 0);
    }

    /** A tremor (the exotic weapons, the particle beam): magnitude {@code m}, shaking the ground {@code ticks}. */
    public static void tremor(ServerLevel level, Vec3 at, float m, int ticks) {
        of(level).add(level, at, m, Math.max(0, ticks));
    }

    /** A missile coming down (a hit or an airburst), just before its warhead goes off: the blasts it makes are its. */
    public static void impact(ServerLevel level, Vec3 at, Flights.Flight flight) {
        Seismic s = of(level);
        long now = level.getGameTime();
        s.impacts.removeIf(i -> now - i.time() > ORIGIN_TICKS);
        LaunchNetwork nets = LaunchNetwork.of(level);
        long net = nets.networkOfFlight(flight.id());
        s.impacts.add(new Impact(at, now, net, flight.start()));
    }

    /** Whether a burst at {@code at} is high enough over the ground to couple little into it (only where it is loaded). */
    private static boolean airburst(ServerLevel level, Vec3 at, double crater) {
        BlockPos p = BlockPos.containing(at);
        if (!level.hasChunkAt(p)) {
            return false;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
        return at.y - ground > Math.max(AIRBURST_HEIGHT, crater);
    }

    private void add(ServerLevel level, Vec3 at, float m, int duration) {
        long now = level.getGameTime();
        Impact from = null;
        for (Impact i : this.impacts) {
            if (now - i.time() <= ORIGIN_TICKS && i.at().distanceTo(at) <= ORIGIN_REACH
                    && (from == null || i.at().distanceTo(at) < from.at().distanceTo(at))) {
                from = i;
            }
        }
        Event e = new Event(this.nextId++, now, at.x, at.y, at.z, m, duration, from == null ? 0 : from.network(),
                from == null ? Optional.empty() : Optional.of(from.from()));
        this.events.addLast(e);
        while (this.events.size() > KEEP) {
            this.events.removeFirst();
        }
        setDirty();
    }

    /**
     * For GameTests: a blast of magnitude {@code m} at {@code at} now, delivered by network {@code network}'s missile (0:
     * none known) fired from {@code origin} (null: not known).
     */
    public Event addForTest(ServerLevel level, Vec3 at, float m, long network, @Nullable Vec3 origin) {
        Event e = new Event(this.nextId++, level.getGameTime(), at.x, at.y, at.z, m, 0, network, Optional.ofNullable(origin));
        this.events.addLast(e);
        while (this.events.size() > KEEP) {
            this.events.removeFirst();
        }
        setDirty();
        return e;
    }

    /** For GameTests: forgets every event (each test's seismograph starts after the newest anyway). */
    public void clearForTest() {
        this.events.clear();
        this.impacts.clear();
        setDirty();
    }

    @Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag_, net.minecraft.core.HolderLookup.Provider registries_) {
        return TYPE.save(this, tag_, registries_);
    }
}
