package dev.groundburst.content.launch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * The Launch Control Stations' networks (DESIGN.md §17.12), saved with each dimension ({@code launch_networks.dat}):
 * each with its code, its stations and its members (launchers and Radar Arrays, anywhere in the dimension). A member's
 * record is what it last reported, kept while it is unloaded (nothing changes there while nothing ticks). Orders wait in
 * the record until the member is there to take them; a launch order wakes it (a chunk ticket on its structure's
 * chunks) and keeps it awake through its launch.
 */
public final class LaunchNetwork extends SavedData {
    public static final int MAX_MEMBERS = 32, LOG = 12;
    /** A loaded member reports this often, ticks. */
    static final int REPORT = 10;
    /** A woken member stays loaded this long after its last order or its countdown, ticks (doors shut, boxes down). */
    static final int GRACE = 300;
    /** Time-on-target launches are planned at least this far ahead (the far members are woken in it), ticks. */
    public static final int LEAD = 100;
    /** A member whose report is older than this is out of contact (unloaded), ticks. */
    public static final int CONTACT = 40;
    /** A missile this close to coming down when it was last seen, ticks, came down (otherwise it was lost). */
    private static final int LANDED = 20;
    /** A woken member that is still not there after this long does not answer, ticks. */
    private static final int NO_ANSWER = 600;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public enum Role { LAUNCHER, RADAR }

    /** Aim points round the aim for several picked members (DESIGN.md §17.12). */
    public enum Pattern { POINT, LINE, RING, GRID }

    public enum EventKind { LAUNCHED, IMPACT, LOST, NO_CONTACT, FAILED, LINKED, UNLINKED, NEW_CODES, FIRE_CALLED, DESIGNATED }

    // --- records -----------------------------------------------------------------------------------------------------------
    private static final Codec<LaunchSite.Strike> STRIKE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("lethal").forGetter(LaunchSite.Strike::lethal), Codec.FLOAT.fieldOf("reach").forGetter(LaunchSite.Strike::reach),
            Codec.FLOAT.fieldOf("along").forGetter(LaunchSite.Strike::along), Codec.FLOAT.fieldOf("across").forGetter(LaunchSite.Strike::across))
            .apply(i, LaunchSite.Strike::new));
    private static final Codec<LaunchSite.Timing> TIMING_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("delay").forGetter(LaunchSite.Timing::delay), Codec.BOOL.fieldOf("cruise").forGetter(LaunchSite.Timing::cruise),
            Codec.DOUBLE.fieldOf("x").forGetter(LaunchSite.Timing::x), Codec.DOUBLE.fieldOf("z").forGetter(LaunchSite.Timing::z),
            Codec.DOUBLE.fieldOf("heading").forGetter(LaunchSite.Timing::heading)).apply(i, LaunchSite.Timing::new));

    /** What a member last reported. */
    public record Report(int readiness, int countdown, boolean armed, Optional<BlockPos> target, int range, LaunchSite.Strike strike,
            ItemStack shown, LaunchSite.Timing timing, long seen, boolean working) {
        static final Report NONE = new Report(LaunchSiloBlockEntity.Readiness.NOT_FORMED.ordinal(), -1, false, Optional.empty(), 0,
                LaunchSite.Strike.NONE, ItemStack.EMPTY, new LaunchSite.Timing(0, false, 0, 0, 0), Long.MIN_VALUE / 4, false);
        static final Codec<Report> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("readiness").forGetter(Report::readiness), Codec.INT.fieldOf("countdown").forGetter(Report::countdown),
                Codec.BOOL.fieldOf("armed").forGetter(Report::armed), BlockPos.CODEC.optionalFieldOf("target").forGetter(Report::target),
                Codec.INT.fieldOf("range").forGetter(Report::range), STRIKE_CODEC.fieldOf("strike").forGetter(Report::strike),
                ItemStack.OPTIONAL_CODEC.fieldOf("shown").forGetter(Report::shown), TIMING_CODEC.fieldOf("timing").forGetter(Report::timing),
                Codec.LONG.fieldOf("seen").forGetter(Report::seen), Codec.BOOL.optionalFieldOf("working", false).forGetter(Report::working))
                .apply(i, Report::new));
    }

    /** Where a Laser Designator designated a launcher: the point it was lased on, and the face there. */
    public record Mark(Vec3 at, net.minecraft.core.Direction face) {
        static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(Vec3.CODEC.fieldOf("at").forGetter(Mark::at),
                net.minecraft.core.Direction.CODEC.fieldOf("face").forGetter(Mark::face)).apply(i, Mark::new));
    }

    /** What the network has told a member to do and it has not done yet (-1: nothing). */
    public record Orders(Optional<BlockPos> aim, int arm, long launchAt, Optional<UUID> by) {
        static final Orders NONE = new Orders(Optional.empty(), -1, -1, Optional.empty());
        static final Codec<Orders> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("aim").forGetter(Orders::aim), Codec.INT.fieldOf("arm").forGetter(Orders::arm),
                Codec.LONG.fieldOf("launch_at").forGetter(Orders::launchAt), UUIDUtil.CODEC.optionalFieldOf("by").forGetter(Orders::by))
                .apply(i, Orders::new));

        boolean any() {
            return this.aim.isPresent() || this.arm >= 0 || this.launchAt >= 0;
        }
    }

    /** One launcher or radar on a network. */
    public static final class Member {
        static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(m -> m.pos), Codec.STRING.fieldOf("role").forGetter(m -> m.role.name()),
                Codec.STRING.fieldOf("kind").forGetter(m -> m.kind), Report.CODEC.fieldOf("report").forGetter(m -> m.report),
                Orders.CODEC.fieldOf("orders").forGetter(m -> m.orders), Codec.BOOL.fieldOf("lost").forGetter(m -> m.lost),
                Codec.INT.listOf().fieldOf("box").forGetter(m -> List.of(m.minCX, m.minCZ, m.maxCX, m.maxCZ)),
                UUIDUtil.CODEC.listOf().fieldOf("flights").forGetter(m -> List.copyOf(m.flights)),
                Mark.CODEC.optionalFieldOf("mark").forGetter(m -> Optional.ofNullable(m.mark)))
                .apply(i, (pos, role, kind, report, orders, lost, box, flights, mark) -> {
                    Member m = new Member(pos, Role.valueOf(role), kind);
                    m.report = report;
                    m.orders = orders;
                    m.lost = lost;
                    m.mark = mark.orElse(null);
                    if (box.size() == 4) {
                        m.minCX = box.get(0);
                        m.minCZ = box.get(1);
                        m.maxCX = box.get(2);
                        m.maxCZ = box.get(3);
                    }
                    m.flights.addAll(flights);
                    return m;
                }));

        public final BlockPos pos;
        public final Role role;
        /** Its controller's block id (its name on the station). */
        public final String kind;
        Report report = Report.NONE;
        Orders orders = Orders.NONE;
        /** Its chunk was loaded and no launcher (or radar) was there. */
        boolean lost;
        /** The chunks its structure covers: all woken together. */
        int minCX, minCZ, maxCX, maxCZ;
        /** Its missiles in the air. */
        final List<UUID> flights = new ArrayList<>();
        /** Where a designator designated it (null: it was aimed some other way). */
        @Nullable Mark mark;
        // Not saved: chunk tickets are dropped as the world loads and set again for orders still waiting.
        boolean awake;
        long keepUntil, wokeAt;

        Member(BlockPos pos, Role role, String kind) {
            this.pos = pos.immutable();
            this.role = role;
            this.kind = kind;
            this.minCX = this.maxCX = pos.getX() >> 4;
            this.minCZ = this.maxCZ = pos.getZ() >> 4;
        }

        public Report report() {
            return this.report;
        }

        /** The point it was designated on and the face there (a designator's sight shows it), while it is still aimed there. */
        public @Nullable Mark mark() {
            Optional<BlockPos> aim = aim();
            return this.mark != null && aim.isPresent() && Mth.floor(this.mark.at().x) == aim.get().getX()
                    && Mth.floor(this.mark.at().z) == aim.get().getZ() ? this.mark : null;
        }

        public Orders orders() {
            return this.orders;
        }

        public boolean lost() {
            return this.lost;
        }

        public List<UUID> flights() {
            return List.copyOf(this.flights);
        }

        /** Its target as it will be once its orders are done. */
        public Optional<BlockPos> aim() {
            return this.orders.aim.isPresent() ? this.orders.aim : this.report.target;
        }

        /** Whether it will be armed once its orders are done. */
        public boolean willBeArmed() {
            return this.orders.arm >= 0 ? this.orders.arm == 1 : this.report.armed;
        }

        /** Whether it reported lately (it is loaded). */
        public boolean inContact(long now) {
            return now - this.report.seen <= CONTACT;
        }

        /** Whether it could be armed at {@code aim}: ready (as it last reported) and the aim within its reach. */
        public boolean canArm(@Nullable BlockPos aim) {
            return this.role == Role.LAUNCHER && !this.lost && aim != null
                    && this.report.readiness == LaunchSiloBlockEntity.Readiness.READY.ordinal()
                    && Math.hypot(aim.getX() - this.pos.getX(), aim.getZ() - this.pos.getZ()) <= this.report.range;
        }
    }

    /** A line of a network's log. {@code detail}: an order that failed, the readiness that stopped it. */
    public record Event(long time, EventKind kind, BlockPos member, String memberKind, int x, int z, int detail) {
        static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("time").forGetter(Event::time), Codec.STRING.fieldOf("kind").forGetter(e -> e.kind.name()),
                BlockPos.CODEC.fieldOf("member").forGetter(Event::member), Codec.STRING.fieldOf("member_kind").forGetter(Event::memberKind),
                Codec.INT.fieldOf("x").forGetter(Event::x), Codec.INT.fieldOf("z").forGetter(Event::z), Codec.INT.fieldOf("detail").forGetter(Event::detail))
                .apply(i, (t, k, m, mk, x, z, d) -> new Event(t, EventKind.valueOf(k), m, mk, x, z, d)));
    }

    /** A network: its code (what Launch Codes cards carry), its stations and its members, in the order they were linked. */
    public static final class Network {
        static final Codec<Network> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(n -> n.id), Codec.STRING.fieldOf("code").forGetter(n -> n.code),
                Codec.LONG.listOf().fieldOf("stations").forGetter(n -> new LongArrayList(n.stations)),
                Member.CODEC.listOf().fieldOf("members").forGetter(n -> List.copyOf(n.members.values())),
                Event.CODEC.listOf().fieldOf("log").forGetter(n -> List.copyOf(n.log)))
                .apply(i, (id, code, stations, members, log) -> {
                    Network n = new Network(id, code);
                    n.stations.addAll(stations);
                    for (Member m : members) {
                        n.members.put(m.pos.asLong(), m);
                    }
                    n.log.addAll(log);
                    return n;
                }));

        public final long id;
        String code;
        final LongOpenHashSet stations = new LongOpenHashSet();
        final Long2ObjectLinkedOpenHashMap<Member> members = new Long2ObjectLinkedOpenHashMap<>();
        final ArrayDeque<Event> log = new ArrayDeque<>();

        Network(long id, String code) {
            this.id = id;
            this.code = code;
        }

        public String code() {
            return this.code;
        }

        public List<Member> members() {
            return List.copyOf(this.members.values());
        }

        public @Nullable Member member(BlockPos pos) {
            return this.members.get(pos.asLong());
        }

        public List<Event> log() {
            return List.copyOf(this.log);
        }

        public int stationCount() {
            return this.stations.size();
        }
    }

    static final SavedDataType<LaunchNetwork> TYPE = new SavedDataType<>(GroundBurst.id("launch_networks"), LaunchNetwork::new,
            Network.CODEC.listOf().fieldOf("networks").codec().xmap(list -> {
                LaunchNetwork n = new LaunchNetwork();
                for (Network net : list) {
                    n.networks.put(net.id, net);
                    for (Member m : net.members.values()) {
                        n.memberOf.put(m.pos.asLong(), net.id);
                    }
                }
                return n;
            }, n -> List.copyOf(n.networks.values())));

    private final Map<Long, Network> networks = new HashMap<>();
    /** Each member's network (by its position). */
    private final Long2LongOpenHashMap memberOf = new Long2LongOpenHashMap();
    /** Where each of the network's missiles was last seen, and how long it then had to go (not saved). */
    private final Map<UUID, Vec3> lastSeen = new HashMap<>();
    private final Map<UUID, Integer> lastEta = new HashMap<>();

    private LaunchNetwork() {}

    public static LaunchNetwork of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** A dimension's networks if it has any (null: none, and none are made). */
    static @Nullable LaunchNetwork ifAny(ServerLevel level) {
        return level.getDataStorage().get(TYPE);
    }

    // --- networks and stations -------------------------------------------------------------------------------------------
    public @Nullable Network network(long id) {
        return this.networks.get(id);
    }

    /** The network a Launch Codes card's code belongs to (null: void, or blank). */
    public @Nullable Network byCode(@Nullable String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (Network n : this.networks.values()) {
            if (n.code.equals(code)) {
                return n;
            }
        }
        return null;
    }

    /** A new network, for a station that has none yet: returns its id. */
    public long create(ServerLevel level, BlockPos station) {
        RandomSource random = level.getRandom();
        long id;
        do {
            id = random.nextLong();
        } while (id == 0 || this.networks.containsKey(id));
        Network n = new Network(id, freshCode(random));
        n.stations.add(station.asLong());
        this.networks.put(id, n);
        setDirty();
        return id;
    }

    private String freshCode(RandomSource random) {
        while (true) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                if (i == 3) {
                    b.append('-');
                }
                b.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String code = b.toString();
            if (byCode(code) == null) {
                return code;
            }
        }
    }

    /** A station on network {@code id} (it formed, loaded, or joined it). */
    public void stationOn(long id, BlockPos station) {
        Network n = this.networks.get(id);
        if (n != null && n.stations.add(station.asLong())) {
            setDirty();
        }
    }

    /** A station gone from network {@code id} (broken, or joined another): an empty network with no station left goes. */
    public void stationOff(long id, BlockPos station) {
        Network n = this.networks.get(id);
        if (n == null) {
            return;
        }
        if (n.stations.remove(station.asLong())) {
            setDirty();
        }
        if (n.stations.isEmpty() && n.members.isEmpty()) {
            this.networks.remove(id);
            setDirty();
        }
    }

    /** New codes for a network: every card made before is void; its stations and members stay. */
    public @Nullable String newCodes(ServerLevel level, long id) {
        Network n = this.networks.get(id);
        if (n == null) {
            return null;
        }
        n.code = freshCode(level.getRandom());
        log(level, n, EventKind.NEW_CODES, BlockPos.ZERO, "", 0, 0, 0);
        setDirty();
        return n.code;
    }

    // --- members ---------------------------------------------------------------------------------------------------------
    public enum LinkResult { LINKED, MOVED, ALREADY, FULL, NOT_A_MEMBER }

    /** The network a member is on (null: none). */
    public @Nullable Network networkOf(BlockPos member) {
        long id = this.memberOf.getOrDefault(member.asLong(), 0L);
        return id == 0 ? null : this.networks.get(id);
    }

    /**
     * Links a launcher (a {@link LaunchSite}) or a Radar Array at {@code pos} to network {@code id}, taking it off any
     * other network it was on.
     */
    public LinkResult link(ServerLevel level, long id, BlockEntity be) {
        Network n = this.networks.get(id);
        Role role = be instanceof LaunchSite ? Role.LAUNCHER : be instanceof RadarArrayBlockEntity ? Role.RADAR : null;
        if (n == null || role == null) {
            return LinkResult.NOT_A_MEMBER;
        }
        BlockPos pos = be.getBlockPos();
        Network old = networkOf(pos);
        if (old == n) {
            return LinkResult.ALREADY;
        }
        if (n.members.size() >= MAX_MEMBERS) {
            return LinkResult.FULL;
        }
        if (old != null) {
            unlink(level, pos);
        }
        Member m = new Member(pos, role, BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString());
        if (be instanceof MultiblockController c) {
            box(m, c);
        }
        n.members.put(pos.asLong(), m);
        this.memberOf.put(pos.asLong(), id);
        report(level, m, be);
        log(level, n, EventKind.LINKED, pos, m.kind, pos.getX(), pos.getZ(), 0);
        setDirty();
        return old != null ? LinkResult.MOVED : LinkResult.LINKED;
    }

    /** The chunks a structure covers. */
    private static void box(Member m, MultiblockController c) {
        MultiblockDefinition def = c.definition();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (MultiblockDefinition.Cell cell : def.cells(c.controllerPos(), c.controllerFacing())) {
            minX = Math.min(minX, cell.pos().getX());
            minZ = Math.min(minZ, cell.pos().getZ());
            maxX = Math.max(maxX, cell.pos().getX());
            maxZ = Math.max(maxZ, cell.pos().getZ());
        }
        if (minX <= maxX) {
            m.minCX = minX >> 4;
            m.minCZ = minZ >> 4;
            m.maxCX = maxX >> 4;
            m.maxCZ = maxZ >> 4;
        }
    }

    /** Takes a member off its network (true if it was on one). */
    public boolean unlink(ServerLevel level, BlockPos pos) {
        Network n = networkOf(pos);
        if (n == null) {
            return false;
        }
        Member m = n.members.remove(pos.asLong());
        this.memberOf.remove(pos.asLong());
        if (m != null) {
            sleep(level, m);
            log(level, n, EventKind.UNLINKED, pos, m.kind, pos.getX(), pos.getZ(), 0);
            for (UUID f : m.flights) {
                this.lastSeen.remove(f);
                this.lastEta.remove(f);
            }
        }
        if (n.stations.isEmpty() && n.members.isEmpty()) {
            this.networks.remove(n.id);
        }
        setDirty();
        return m != null;
    }

    /** Whether any member of any network is within {@code range} blocks (square) of x/z: the terrain survey maps round them. */
    boolean memberNear(int x, int z, int range) {
        for (Network n : this.networks.values()) {
            for (Member m : n.members.values()) {
                if (Math.abs(m.pos.getX() - x) <= range && Math.abs(m.pos.getZ() - z) <= range) {
                    return true;
                }
            }
            for (long s : n.stations) {
                BlockPos p = BlockPos.of(s);
                if (Math.abs(p.getX() - x) <= range && Math.abs(p.getZ() - z) <= range) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- reports -----------------------------------------------------------------------------------------------------------
    private @Nullable Member memberAt(BlockPos pos) {
        Network n = networkOf(pos);
        return n == null ? null : n.members.get(pos.asLong());
    }

    /** A launcher's tick (from {@link Launches#track(ServerLevel, LaunchSite)}): it takes its orders and reports. */
    void tickSite(ServerLevel level, LaunchSite site) {
        Member m = memberAt(site.getBlockPos());
        if (m == null || !(site instanceof BlockEntity be)) {
            return;
        }
        if (m.orders.any()) {
            apply(level, m, site);
        } else if ((level.getGameTime() + site.getBlockPos().hashCode()) % REPORT == 0 || m.lost) {
            report(level, m, be);
        }
    }

    /** A Radar Array's tick: it reports whether it is working (the station shows what working ones track). */
    public void tickRadar(ServerLevel level, RadarArrayBlockEntity radar, boolean working) {
        Member m = memberAt(radar.getBlockPos());
        if (m != null && ((level.getGameTime() + radar.getBlockPos().hashCode()) % REPORT == 0 || m.lost || m.report.working != working)) {
            report(level, m, radar);
        }
    }

    private void report(ServerLevel level, Member m, BlockEntity be) {
        Report r;
        if (be instanceof LaunchSite s) {
            BlockPos t = s.target();
            r = new Report(s.readiness().ordinal(), s.countdown(), s.armed(), Optional.ofNullable(t == null ? null : t.immutable()), s.range(),
                    s.strike(level), s.shownStack(), s.timing(), level.getGameTime(), true);
        } else if (be instanceof RadarArrayBlockEntity radar) {
            r = new Report(radar.isFormed() ? LaunchSiloBlockEntity.Readiness.READY.ordinal() : LaunchSiloBlockEntity.Readiness.NOT_FORMED.ordinal(),
                    -1, false, Optional.empty(), RadarArrayBlockEntity.RANGE, LaunchSite.Strike.NONE, ItemStack.EMPTY,
                    Report.NONE.timing(), level.getGameTime(), radar.working());
        } else {
            return;
        }
        boolean changed = !sameState(r, m.report) || m.lost;
        m.report = r;
        m.lost = false;
        if (changed) {
            setDirty();                                    // the seen time alone is not worth a save
        }
    }

    private static boolean sameState(Report a, Report b) {
        return a.readiness == b.readiness && a.countdown == b.countdown && a.armed == b.armed && a.target.equals(b.target) && a.range == b.range
                && a.working == b.working && ItemStack.isSameItemSameComponents(a.shown, b.shown) && a.shown.getCount() == b.shown.getCount();
    }

    /** A member's missile left (from the launchers, as each one lights): the station follows it to the ground. */
    public static void launched(ServerLevel level, BlockPos launcher, Flights.Flight flight) {
        LaunchNetwork net = of(level);
        Network n = net.networkOf(launcher);
        Member m = n == null ? null : n.members.get(launcher.asLong());
        if (m == null) {
            return;
        }
        m.flights.add(flight.id());
        flight.seekerNetwork(n.id);                      // a laser-guided missile answers its network's designators
        net.lastSeen.put(flight.id(), flight.position());
        net.lastEta.put(flight.id(), flight.ticksToImpact());
        net.log(level, n, EventKind.LAUNCHED, launcher, m.kind, (int) Math.floor(flight.target().x), (int) Math.floor(flight.target().z), 0);
        net.setDirty();
    }

    // --- orders ------------------------------------------------------------------------------------------------------------
    /**
     * Aims the picked members at {@code x}/{@code z}: one picked, there; several, each at its point of {@code pattern}
     * round it, {@code spacing} blocks apart. A loaded member takes it at once, an unloaded one when it is next there.
     */
    public void aim(ServerLevel level, long id, List<BlockPos> picked, int x, int z, Pattern pattern, int spacing) {
        Network n = this.networks.get(id);
        if (n == null) {
            return;
        }
        List<Member> ms = new ArrayList<>();
        for (BlockPos p : picked) {
            Member m = n.members.get(p.asLong());
            if (m != null && m.role == Role.LAUNCHER && !m.lost && !ms.contains(m)) {
                ms.add(m);
            }
        }
        if (ms.isEmpty()) {
            return;
        }
        List<BlockPos> from = ms.stream().map(m -> m.pos).toList();
        List<int[]> points = points(from, x, z, pattern, spacing);
        for (int i = 0; i < ms.size(); i++) {
            Member m = ms.get(i);
            if (m.report.countdown >= 0 && m.report.readiness == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal()) {
                continue;                                  // counting down: its target is set
            }
            int[] p = points.get(i);
            m.orders = new Orders(Optional.of(new BlockPos(p[0], m.pos.getY(), p[1])), m.orders.arm, m.orders.launchAt, m.orders.by);
            // Out of reach of its new aim: it will not stay armed.
            if (m.willBeArmed() && !m.canArm(m.orders.aim.get())) {
                m.orders = new Orders(m.orders.aim, 0, m.orders.launchAt, m.orders.by);
            }
            orderNow(level, m);
        }
        setDirty();
    }

    /**
     * The aim points of a pattern for launchers at {@code from}, centred on x/z: a point (all at it); a line across the
     * line of attack (square to the way from the launchers' middle to the aim), {@code spacing} apart; a ring of that
     * radius; a square grid that far apart, along the world's axes. In the order of {@code from}.
     */
    public static List<int[]> points(List<BlockPos> from, int x, int z, Pattern pattern, int spacing) {
        int n = from.size();
        List<int[]> out = new ArrayList<>(n);
        double s = Math.max(0, spacing);
        if (n <= 1 || pattern == Pattern.POINT || s == 0) {
            for (int i = 0; i < n; i++) {
                out.add(new int[] {x, z});
            }
            return out;
        }
        switch (pattern) {
            case LINE -> {
                double mx = 0, mz = 0;
                for (BlockPos p : from) {
                    mx += p.getX() + 0.5;
                    mz += p.getZ() + 0.5;
                }
                mx /= n;
                mz /= n;
                double dx = x + 0.5 - mx, dz = z + 0.5 - mz, d = Math.hypot(dx, dz);
                double ax = d < 1e-6 ? 1 : -dz / d, az = d < 1e-6 ? 0 : dx / d;   // across the line of attack
                for (int i = 0; i < n; i++) {
                    double o = (i - (n - 1) / 2.0) * s;
                    out.add(new int[] {(int) Math.floor(x + 0.5 + ax * o), (int) Math.floor(z + 0.5 + az * o)});
                }
            }
            case RING -> {
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    out.add(new int[] {(int) Math.floor(x + 0.5 + Math.sin(a) * s), (int) Math.floor(z + 0.5 - Math.cos(a) * s)});
                }
            }
            default -> {
                int cols = (int) Math.ceil(Math.sqrt(n)), rows = (n + cols - 1) / cols;
                for (int i = 0; i < n; i++) {
                    int c = i % cols, r = i / cols;
                    double ox = (c - (cols - 1) / 2.0) * s, oz = (r - (rows - 1) / 2.0) * s;
                    out.add(new int[] {(int) Math.floor(x + 0.5 + ox), (int) Math.floor(z + 0.5 + oz)});
                }
            }
        }
        return out;
    }

    /** Arms the picked members (each that can be: ready, with an aim in reach) or makes them safe. */
    public int arm(ServerLevel level, long id, List<BlockPos> picked, boolean on) {
        Network n = this.networks.get(id);
        if (n == null) {
            return 0;
        }
        int done = 0;
        for (BlockPos p : picked) {
            Member m = n.members.get(p.asLong());
            if (m == null || m.role != Role.LAUNCHER || m.lost) {
                continue;
            }
            if (on && !m.canArm(m.aim().orElse(null))) {
                continue;
            }
            m.orders = new Orders(m.orders.aim, on ? 1 : 0, on ? m.orders.launchAt : -1, m.orders.by);
            orderNow(level, m);
            done++;
        }
        setDirty();
        return done;
    }

    /**
     * LAUNCH: every armed member of the network fires: at once, or {@code together} (time on target: each launch held
     * so the first warheads all arrive at the same moment, worked out from each one's countdown, ignition and flight to
     * its own target). Unloaded members are woken to take the order. Returns how many were ordered to fire.
     */
    public int launch(ServerLevel level, long id, boolean together, @Nullable ServerPlayer by) {
        Network n = this.networks.get(id);
        if (n == null) {
            return 0;
        }
        long now = level.getGameTime();
        List<Member> armed = new ArrayList<>();
        for (Member m : n.members.values()) {
            if (m.role == Role.LAUNCHER && !m.lost && m.willBeArmed() && m.aim().isPresent() && m.orders.launchAt < 0) {
                armed.add(m);
            }
        }
        if (armed.isEmpty()) {
            return 0;
        }
        Map<Member, Integer> eta = new HashMap<>();
        int longest = 0;
        for (Member m : armed) {
            BlockPos t = m.aim().get();
            int e = m.report.timing.toImpact(t.getX(), t.getZ());
            eta.put(m, e);
            longest = Math.max(longest, e);
        }
        // Together: all planned a little ahead, so the far members can be woken in time; at once: now.
        boolean anyAsleep = armed.stream().anyMatch(m -> !m.inContact(now) || !level.shouldTickBlocksAt(ChunkPos.pack(m.pos.getX() >> 4, m.pos.getZ() >> 4)));
        long base = now + (together && anyAsleep ? LEAD : 0);
        Optional<UUID> who = Optional.ofNullable(by == null ? null : by.getUUID());
        for (Member m : armed) {
            long at = together ? base + (longest - eta.get(m)) : now;
            m.orders = new Orders(m.orders.aim, m.orders.arm, at, who);
            orderNow(level, m);
        }
        setDirty();
        return armed.size();
    }

    // --- the Laser Designator (DESIGN.md §17.13) ---------------------------------------------------------------------------
    /** Whether a member is a launcher loaded with a laser-guided missile (as it last reported). */
    private static boolean laserGuided(Member m) {
        var spec = dev.groundburst.content.assembly.MissileItem.spec(m.report.shown);
        return m.role == Role.LAUNCHER && !m.lost && spec != null && spec.guidance() == dev.groundburst.content.assembly.MissileParts.Guidance.LASER;
    }

    /** Whether it is counting down or ordered to fire (its aim and its arming are settled). */
    private static boolean firing(Member m) {
        return m.orders.launchAt >= 0 || m.report.readiness == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
    }

    /** The network's launchers loaded with a laser-guided missile and not firing, in the order they were linked: a designator's choice. */
    public List<Member> laserLaunchers(long id) {
        Network n = this.networks.get(id);
        List<Member> out = new ArrayList<>();
        if (n != null) {
            for (Member m : n.members.values()) {
                if (laserGuided(m) && !firing(m)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /**
     * A designator's launcher: the one at {@code picked} while it is one of them, else the next of them linked after it,
     * else the first (null: none).
     */
    public @Nullable Member laserSelection(long id, @Nullable BlockPos picked) {
        Network n = this.networks.get(id);
        List<Member> choice = laserLaunchers(id);
        if (n == null || choice.isEmpty()) {
            return null;
        }
        if (picked != null) {
            List<Member> all = new ArrayList<>(n.members.values());
            for (int at = 0; at < all.size(); at++) {
                if (all.get(at).pos.equals(picked)) {
                    for (int i = 0; i < all.size(); i++) {
                        Member m = all.get((at + i) % all.size());
                        if (choice.contains(m)) {
                            return m;
                        }
                    }
                }
            }
        }
        return choice.getFirst();
    }

    /** The launcher {@code step} on from a designator's (round the choice). */
    public @Nullable Member laserStep(long id, @Nullable BlockPos picked, int step) {
        List<Member> choice = laserLaunchers(id);
        Member at = laserSelection(id, picked);
        return at == null ? null : choice.get(Math.floorMod(choice.indexOf(at) + step, choice.size()));
    }

    /**
     * Designate (a Laser Designator's left click): {@code m}, one of the network's laser-guided launchers, is aimed at
     * {@code spot} and armed (a far one when it is next loaded). False if it cannot be: not ready, or out of reach.
     */
    public boolean designate(ServerLevel level, long id, Member m, BlockPos spot, @Nullable Mark mark) {
        Network n = this.networks.get(id);
        BlockPos aim = new BlockPos(spot.getX(), m.pos.getY(), spot.getZ());
        if (n == null || !laserGuided(m) || firing(m) || !m.canArm(aim)) {
            return false;
        }
        m.mark = mark;
        m.orders = new Orders(Optional.of(aim), 1, -1, m.orders.by);
        orderNow(level, m);
        log(level, n, EventKind.DESIGNATED, m.pos, m.kind, spot.getX(), spot.getZ(), 1);
        setDirty();
        return true;
    }

    /**
     * Call for fire (a Laser Designator's left click): {@code m}, armed, fires now at its aim (woken if it is far); its
     * missile then follows {@code by}'s laser (the flight's owner is who ordered the launch). False if it is not armed.
     */
    public boolean fireLaser(ServerLevel level, long id, Member m, @Nullable ServerPlayer by) {
        Network n = this.networks.get(id);
        if (n == null || !laserGuided(m) || firing(m) || !m.willBeArmed() || m.aim().isEmpty()) {
            return false;
        }
        m.orders = new Orders(m.orders.aim, m.orders.arm, level.getGameTime(), Optional.ofNullable(by == null ? null : by.getUUID()));
        orderNow(level, m);
        BlockPos at = m.aim().get();
        log(level, n, EventKind.FIRE_CALLED, m.pos, m.kind, at.getX(), at.getZ(), 1);
        setDirty();
        return true;
    }

    /** The network's armed launchers, armed or counting down, any missile (a designator's sight shows where they are aimed). */
    public List<Member> armed(long id) {
        Network n = this.networks.get(id);
        List<Member> out = new ArrayList<>();
        if (n != null) {
            for (Member m : n.members.values()) {
                boolean counting = m.report.readiness == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
                if (m.role == Role.LAUNCHER && !m.lost && m.aim().isPresent() && (m.willBeArmed() || counting)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** A member's number on its network, as the station lists it (1: the first linked; 0: not a member). */
    public int number(long id, Member m) {
        Network n = this.networks.get(id);
        int i = 0;
        if (n != null) {
            for (Member x : n.members.values()) {
                i++;
                if (x == m) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** Whether a member's launcher is there and ticking (its chunk loaded and running). */
    private static @Nullable BlockEntity present(ServerLevel level, Member m) {
        if (!level.isLoaded(m.pos) || !level.shouldTickBlocksAt(ChunkPos.pack(m.pos.getX() >> 4, m.pos.getZ() >> 4))) {
            return null;
        }
        return level.getBlockEntity(m.pos);
    }

    /** An order to a member: done at once if it is there, otherwise it waits (a launch wakes it). */
    private void orderNow(ServerLevel level, Member m) {
        if (present(level, m) instanceof LaunchSite site) {
            apply(level, m, site);
        } else {
            wakeIfDue(level, m);
        }
    }

    /** A member takes its orders: its aim, arming, and (when its time comes) launching. */
    private void apply(ServerLevel level, Member m, LaunchSite site) {
        Network n = networkOf(m.pos);
        Orders o = m.orders;
        Optional<BlockPos> aim = o.aim;
        int arm = o.arm;
        long launchAt = o.launchAt;
        if (aim.isPresent()) {
            if (!site.setTarget(aim.get().getX(), aim.get().getZ()) && n != null) {
                log(level, n, EventKind.FAILED, m.pos, m.kind, aim.get().getX(), aim.get().getZ(), site.readiness().ordinal());
            }
            aim = Optional.empty();
        }
        if (arm >= 0) {
            boolean ok = site.arm(arm == 1);
            if (!ok && arm == 1 && n != null) {
                BlockPos t = site.target();
                log(level, n, EventKind.FAILED, m.pos, m.kind, t == null ? 0 : t.getX(), t == null ? 0 : t.getZ(), site.readiness().ordinal());
                launchAt = -1;                             // it cannot fire unarmed
            }
            arm = -1;
        }
        if (launchAt >= 0 && level.getGameTime() >= launchAt) {
            Entity by = o.by.map(level::getEntity).orElse(null);
            if (!site.launchArmed(level, by) && n != null) {
                BlockPos t = site.target();
                log(level, n, EventKind.FAILED, m.pos, m.kind, t == null ? 0 : t.getX(), t == null ? 0 : t.getZ(), site.readiness().ordinal());
            }
            launchAt = -1;
            m.keepUntil = level.getGameTime() + GRACE;
        }
        m.orders = new Orders(aim, arm, launchAt, launchAt >= 0 ? o.by : Optional.empty());
        if (site instanceof BlockEntity be) {
            report(level, m, be);
        }
        setDirty();
    }

    // --- waking ------------------------------------------------------------------------------------------------------------
    /** A launch order due soon wakes its member (its chunks ticket-loaded) so it is there to take it. */
    private void wakeIfDue(ServerLevel level, Member m) {
        if (m.orders.launchAt >= 0 && m.orders.launchAt - LEAD <= level.getGameTime()) {
            wake(level, m);
        }
    }

    private void wake(ServerLevel level, Member m) {
        m.keepUntil = Math.max(m.keepUntil, level.getGameTime() + GRACE);
        if (!m.awake) {
            m.awake = true;
            m.wokeAt = level.getGameTime();
            for (int cx = m.minCX; cx <= m.maxCX; cx++) {
                for (int cz = m.minCZ; cz <= m.maxCZ; cz++) {
                    GBLaunch.NETWORK_TICKETS.forceChunk(level, m.pos, cx, cz, true, true);
                }
            }
        }
    }

    private static void sleep(ServerLevel level, Member m) {
        if (m.awake) {
            m.awake = false;
            for (int cx = m.minCX; cx <= m.maxCX; cx++) {
                for (int cz = m.minCZ; cz <= m.maxCZ; cz++) {
                    GBLaunch.NETWORK_TICKETS.forceChunk(level, m.pos, cx, cz, false, true);
                }
            }
        }
    }

    // --- the tick ----------------------------------------------------------------------------------------------------------
    /** Every server tick: orders due, members woken and let sleep, contact lost, missiles followed down. */
    public void tick(ServerLevel level) {
        if (this.networks.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean check = now % 20 == 0, follow = now % REPORT == 0;
        Map<UUID, Flights.Flight> flying = null;
        for (Network n : List.copyOf(this.networks.values())) {
            for (Member m : List.copyOf(n.members.values())) {
                BlockEntity be = present(level, m);
                if (m.orders.any()) {
                    if (be instanceof LaunchSite site) {
                        apply(level, m, site);
                    } else if (m.awake && be == null && now - m.wokeAt > NO_ANSWER && m.orders.launchAt >= 0 && now > m.orders.launchAt) {
                        // Woken and never there: it does not answer (its orders fail, it is let go).
                        log(level, n, EventKind.FAILED, m.pos, m.kind, m.pos.getX(), m.pos.getZ(), -1);
                        m.orders = Orders.NONE;
                        sleep(level, m);
                    } else {
                        wakeIfDue(level, m);
                    }
                }
                // Contact lost: its chunk is loaded and it is not there.
                if (check && level.isLoaded(m.pos) && !m.lost) {
                    BlockEntity here = level.getBlockEntity(m.pos);
                    boolean there = m.role == Role.LAUNCHER ? here instanceof LaunchSite : here instanceof RadarArrayBlockEntity;
                    if (!there) {
                        m.lost = true;
                        m.orders = Orders.NONE;
                        log(level, n, EventKind.NO_CONTACT, m.pos, m.kind, m.pos.getX(), m.pos.getZ(), 0);
                        setDirty();
                    }
                }
                // Awake: kept so while it counts down, launches and settles, then let go.
                if (m.awake) {
                    boolean busy = m.orders.any() || m.report.readiness == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
                    if (busy && be != null) {
                        m.keepUntil = Math.max(m.keepUntil, now + GRACE);
                    }
                    if (!busy && now > m.keepUntil) {
                        sleep(level, m);
                    }
                }
                // Its missiles: followed until they are gone, then reported down (or lost).
                if (follow && !m.flights.isEmpty()) {
                    if (flying == null) {
                        flying = new HashMap<>();
                        for (Flights.Flight f : Flights.of(level).all()) {
                            flying.put(f.id(), f);
                        }
                    }
                    for (UUID id : List.copyOf(m.flights)) {
                        Flights.Flight f = flying.get(id);
                        if (f != null) {
                            this.lastSeen.put(id, f.position());
                            this.lastEta.put(id, f.ticksToImpact());
                            continue;
                        }
                        m.flights.remove(id);
                        Vec3 at = this.lastSeen.remove(id);
                        Integer eta = this.lastEta.remove(id);
                        boolean down = eta == null || eta <= LANDED;
                        int x = at == null ? m.pos.getX() : Mth.floor(at.x), z = at == null ? m.pos.getZ() : Mth.floor(at.z);
                        log(level, n, down ? EventKind.IMPACT : EventKind.LOST, m.pos, m.kind, x, z, 0);
                        setDirty();
                    }
                }
            }
        }
    }

    /** Reports of one kind from one member this close together are one line (a SCUD Storm's nine missiles), ticks. */
    private static final int SALVO = 200;

    private void log(ServerLevel level, Network n, EventKind kind, BlockPos member, String memberKind, int x, int z, int detail) {
        Event last = n.log.peekLast();
        if (last != null && last.kind() == kind && last.member().equals(member) && level.getGameTime() - last.time() < SALVO
                && (kind == EventKind.LAUNCHED || kind == EventKind.IMPACT || kind == EventKind.LOST)) {
            return;                                        // the same salvo: already reported
        }
        n.log.addLast(new Event(level.getGameTime(), kind, member.immutable(), memberKind, x, z, detail));
        while (n.log.size() > LOG) {
            n.log.removeFirst();
        }
        heard(level, n, kind);
        setDirty();
    }

    /** The world is loading: chunk tickets from before are dropped (orders still waiting wake their members again). */
    static void dropTickets(net.neoforged.neoforge.common.world.chunk.TicketHelper tickets) {
        for (BlockPos owner : List.copyOf(tickets.getBlockTickets().keySet())) {
            tickets.removeAllTickets(owner);
        }
    }

    /** A report comes in: each of the network's stations that is loaded prints it (its teletype). */
    private static void heard(ServerLevel level, Network n, EventKind kind) {
        if (kind == EventKind.LAUNCHED || kind == EventKind.IMPACT || kind == EventKind.LOST || kind == EventKind.NO_CONTACT
                || kind == EventKind.FAILED) {
            for (long s : n.stations) {
                BlockPos p = BlockPos.of(s);
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof LaunchStationBlockEntity station) {
                    station.heard(level, kind);
                }
            }
        }
    }
}
