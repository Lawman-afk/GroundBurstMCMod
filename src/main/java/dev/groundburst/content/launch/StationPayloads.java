package dev.groundburst.content.launch;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The Launch Control Station's screen (DESIGN.md §17.12): its orders, and the scope it is sent while open. */
public final class StationPayloads {
    private static final double REACH = 8;
    /** At most this many members picked in one order. */
    private static final int MAX_PICKED = LaunchNetwork.MAX_MEMBERS;

    /** Client hook: the scope arrives (set by the client). */
    public static Consumer<Scope> scopeHook = s -> {};

    private StationPayloads() {}

    private static @Nullable LaunchStationBlockEntity station(IPayloadContext context, BlockPos pos) {
        if (context.player() instanceof ServerPlayer player && dev.groundburst.content.machine.Carried.near(player, pos, REACH)
                && dev.groundburst.content.machine.Carried.blockEntity(player.level(), pos, LaunchStationBlockEntity.class) instanceof LaunchStationBlockEntity s) {
            return s;
        }
        return null;
    }

    private static final StreamCodec<io.netty.buffer.ByteBuf, List<BlockPos>> PICKED = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PICKED));

    /** Aim the picked members at x/z (several: in a pattern round it, {@code spacing} apart). */
    public record Aim(BlockPos pos, List<BlockPos> picked, int x, int z, int pattern, int spacing) implements CustomPacketPayload {
        public static final Type<Aim> TYPE = GBNetwork.type("station_aim");
        public static final StreamCodec<RegistryFriendlyByteBuf, Aim> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Aim::pos, PICKED, Aim::picked, ByteBufCodecs.VAR_INT, Aim::x, ByteBufCodecs.VAR_INT, Aim::z,
                ByteBufCodecs.VAR_INT, Aim::pattern, ByteBufCodecs.VAR_INT, Aim::spacing, Aim::new);

        @Override
        public Type<Aim> type() {
            return TYPE;
        }

        public static void handle(Aim p, IPayloadContext context) {
            LaunchStationBlockEntity s = station(context, p.pos());
            if (s != null && context.player().level() instanceof ServerLevel level) {
                LaunchNetwork.Pattern[] patterns = LaunchNetwork.Pattern.values();
                s.aim(level, p.picked(), p.x(), p.z(), patterns[Math.clamp(p.pattern(), 0, patterns.length - 1)], Math.clamp(p.spacing(), 0, 256));
            }
        }
    }

    /** Arm the picked members (true) or make them safe. */
    public record Arm(BlockPos pos, List<BlockPos> picked, boolean on) implements CustomPacketPayload {
        public static final Type<Arm> TYPE = GBNetwork.type("station_arm");
        public static final StreamCodec<RegistryFriendlyByteBuf, Arm> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Arm::pos, PICKED, Arm::picked, ByteBufCodecs.BOOL, Arm::on, Arm::new);

        @Override
        public Type<Arm> type() {
            return TYPE;
        }

        public static void handle(Arm p, IPayloadContext context) {
            LaunchStationBlockEntity s = station(context, p.pos());
            if (s != null && context.player().level() instanceof ServerLevel level) {
                s.arm(level, p.picked(), p.on());
            }
        }
    }

    /** LAUNCH: every armed member fires, at once or timed to arrive together. */
    public record Fire(BlockPos pos, boolean together) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = GBNetwork.type("station_fire");
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Fire::pos, ByteBufCodecs.BOOL, Fire::together, Fire::new);

        @Override
        public Type<Fire> type() {
            return TYPE;
        }

        public static void handle(Fire p, IPayloadContext context) {
            LaunchStationBlockEntity s = station(context, p.pos());
            if (s != null && context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
                s.launch(level, p.together(), player);
            }
        }
    }

    /** Take a member off the network. */
    public record Unlink(BlockPos pos, BlockPos member) implements CustomPacketPayload {
        public static final Type<Unlink> TYPE = GBNetwork.type("station_unlink");
        public static final StreamCodec<RegistryFriendlyByteBuf, Unlink> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Unlink::pos, BlockPos.STREAM_CODEC, Unlink::member, Unlink::new);

        @Override
        public Type<Unlink> type() {
            return TYPE;
        }

        public static void handle(Unlink p, IPayloadContext context) {
            LaunchStationBlockEntity s = station(context, p.pos());
            if (s != null && context.player().level() instanceof ServerLevel level) {
                s.unlink(level, p.member());
            }
        }
    }

    /** NEW CODES: the network's cards made so far are void. */
    public record NewCodes(BlockPos pos) implements CustomPacketPayload {
        public static final Type<NewCodes> TYPE = GBNetwork.type("station_new_codes");
        public static final StreamCodec<RegistryFriendlyByteBuf, NewCodes> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, NewCodes::pos, NewCodes::new);

        @Override
        public Type<NewCodes> type() {
            return TYPE;
        }

        public static void handle(NewCodes p, IPayloadContext context) {
            LaunchStationBlockEntity s = station(context, p.pos());
            if (s != null && context.player().level() instanceof ServerLevel level) {
                s.newCodes(level);
            }
        }
    }

    // --- the scope ---------------------------------------------------------------------------------------------------------
    /**
     * A member as the screen sees it: its key ({@code pos}) and where it is in the world ({@code at}: a ship or a
     * contraption may carry it, DESIGN.md §17.16); launcher or radar; its controller's block id; what it last reported
     * (readiness, countdown, armed, target, range, strike, what it holds, its timing); its orders still waiting (arm -1:
     * none; aim pending; launch at, game time or -1); whether it reported lately, is out of contact, a radar is working;
     * ticks until its soonest missile in the air comes down (-1: none).
     */
    public record MemberView(BlockPos pos, BlockPos at, int role, String kind, int readiness, int countdown, boolean armed, int pendingArm, boolean hasTarget,
            int targetX, int targetZ, boolean pendingAim, int range, LaunchSite.Strike strike, ItemStack shown, LaunchSite.Timing timing,
            boolean contact, boolean lost, long launchAt, boolean working, int inFlight) {
        public static MemberView of(LaunchNetwork.Member m, long now, int inFlight) {
            LaunchNetwork.Report r = m.report();
            BlockPos aim = m.aim().orElse(null);
            return new MemberView(m.pos, m.at(), m.role.ordinal(), m.kind, r.readiness(), r.countdown(), r.armed(), m.orders().arm(), aim != null,
                    aim == null ? 0 : aim.getX(), aim == null ? 0 : aim.getZ(), m.orders().aim().isPresent(), r.range(), r.strike(), r.shown(),
                    r.timing(), m.inContact(now), m.lost(), m.orders().launchAt(), r.working(), inFlight);
        }

        public boolean radar() {
            return this.role == LaunchNetwork.Role.RADAR.ordinal();
        }

        /** A launcher (aimed, armed and launched from the station); the others are radars, seismographs, the Dead Hand. */
        public boolean launcher() {
            return this.role == LaunchNetwork.Role.LAUNCHER.ordinal();
        }

        public boolean seismograph() {
            return this.role == LaunchNetwork.Role.SEISMOGRAPH.ordinal();
        }

        public boolean deadHand() {
            return this.role == LaunchNetwork.Role.DEAD_HAND.ordinal();
        }

        /** Armed, or about to be. */
        public boolean willBeArmed() {
            return this.pendingArm >= 0 ? this.pendingArm == 1 : this.armed;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, MemberView> CODEC = StreamCodec.of((buf, m) -> {
            buf.writeBlockPos(m.pos());
            buf.writeBlockPos(m.at());
            buf.writeVarInt(m.role());
            buf.writeUtf(m.kind());
            buf.writeVarInt(m.readiness());
            buf.writeVarInt(m.countdown() + 1);
            buf.writeBoolean(m.armed());
            buf.writeVarInt(m.pendingArm() + 1);
            buf.writeBoolean(m.hasTarget());
            buf.writeVarInt(m.targetX());
            buf.writeVarInt(m.targetZ());
            buf.writeBoolean(m.pendingAim());
            buf.writeVarInt(m.range());
            buf.writeFloat(m.strike().lethal());
            buf.writeFloat(m.strike().reach());
            buf.writeFloat(m.strike().along());
            buf.writeFloat(m.strike().across());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, m.shown());
            buf.writeVarInt(m.timing().delay());
            buf.writeBoolean(m.timing().cruise());
            buf.writeDouble(m.timing().x());
            buf.writeDouble(m.timing().z());
            buf.writeDouble(m.timing().heading());
            buf.writeBoolean(m.timing().direct());
            buf.writeBoolean(m.contact());
            buf.writeBoolean(m.lost());
            buf.writeVarLong(m.launchAt() + 1);
            buf.writeBoolean(m.working());
            buf.writeVarInt(m.inFlight() + 1);
        }, buf -> new MemberView(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean(),
                buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                new LaunchSite.Strike(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                new LaunchSite.Timing(buf.readVarInt(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()),
                buf.readBoolean(), buf.readBoolean(), buf.readVarLong() - 1, buf.readBoolean(), buf.readVarInt() - 1));
    }

    /** A missile in the air: its id, where, which way (radians, 0 = south), where it is coming down, when, and whether it is the network's. */
    public record ContactView(int id, int x, int y, int z, float heading, int targetX, int targetZ, int eta, boolean own) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ContactView> CODEC = StreamCodec.of((buf, c) -> {
            buf.writeInt(c.id());
            buf.writeVarInt(c.x());
            buf.writeVarInt(c.y());
            buf.writeVarInt(c.z());
            buf.writeFloat(c.heading());
            buf.writeVarInt(c.targetX());
            buf.writeVarInt(c.targetZ());
            buf.writeVarInt(c.eta());
            buf.writeBoolean(c.own());
        }, buf -> new ContactView(buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    }

    /** A laser spot one of the network's designators lases now (DESIGN.md §17.13): where, and who lases it. */
    public record SpotView(int x, int y, int z, String who) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SpotView> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.x());
            buf.writeVarInt(s.y());
            buf.writeVarInt(s.z());
            buf.writeUtf(s.who());
        }, buf -> new SpotView(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
    }

    /**
     * A blast the network's seismographs felt (DESIGN.md §17.17): where they make it out to be, its magnitude, whether two
     * or more fixed it there; else the one station's place, its bearing (degrees clockwise from north) and distance and how
     * far each may be off (the arc it may be in); how long ago it went off (ticks).
     */
    public record BlastView(int x, int z, float magnitude, boolean fixed, int fromX, int fromZ, float bearing, float bearingSpread, int distance,
            int distanceSpread, int age) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BlastView> CODEC = StreamCodec.of((buf, b) -> {
            buf.writeVarInt(b.x());
            buf.writeVarInt(b.z());
            buf.writeFloat(b.magnitude());
            buf.writeBoolean(b.fixed());
            buf.writeVarInt(b.fromX());
            buf.writeVarInt(b.fromZ());
            buf.writeFloat(b.bearing());
            buf.writeFloat(b.bearingSpread());
            buf.writeVarInt(b.distance());
            buf.writeVarInt(b.distanceSpread());
            buf.writeVarInt(b.age());
        }, buf -> new BlastView(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        static BlastView of(LaunchNetwork.Blast b, long now) {
            return new BlastView((int) Math.floor(b.x()), (int) Math.floor(b.z()), b.magnitude(), b.fixed(), (int) Math.floor(b.fromX()),
                    (int) Math.floor(b.fromZ()), (float) b.bearing(), (float) b.bearingSpread(), (int) Math.round(b.distance()),
                    (int) Math.round(b.distanceSpread()), (int) Math.max(0, now - b.time));
        }
    }

    /**
     * What a network's screens show (the station's, the War Room Display's): its members, every missile its launchers
     * fired and every one its working radars track, its designators' laser spots, the blasts its seismographs felt.
     * Unpowered, only the members.
     */
    public record Picture(List<MemberView> members, List<ContactView> contacts, List<SpotView> spots, List<BlastView> blasts) {}

    /** {@code n}'s picture now ({@code powered}: what only a working screen sees). */
    public static Picture picture(ServerLevel level, LaunchNetwork.Network n, boolean powered) {
        long now = level.getGameTime();
        List<MemberView> members = new java.util.ArrayList<>();
        List<ContactView> contacts = new java.util.ArrayList<>();
        List<SpotView> spots = new java.util.ArrayList<>();
        List<BlastView> blasts = new java.util.ArrayList<>();
        if (powered) {                                          // its designators' laser spots
            for (Designators.Spot s : Designators.spots(level, n.id)) {
                var who = level.getPlayerByUUID(s.player());
                spots.add(new SpotView((int) Math.floor(s.pos().x), (int) Math.floor(s.pos().y), (int) Math.floor(s.pos().z),
                        who == null ? "" : who.getName().getString()));
            }
            for (LaunchNetwork.Blast b : n.blasts()) {
                blasts.add(BlastView.of(b, now));
            }
        }
        java.util.Map<java.util.UUID, Flights.Flight> flying = new java.util.HashMap<>();
        for (Flights.Flight f : Flights.of(level).all()) {
            flying.put(f.id(), f);
        }
        java.util.Set<java.util.UUID> shown = new java.util.HashSet<>();
        for (LaunchNetwork.Member m : n.members()) {
            int soonest = -1;
            for (java.util.UUID id : m.flights()) {
                Flights.Flight f = flying.get(id);
                if (f != null) {
                    soonest = soonest < 0 ? f.ticksToImpact() : Math.min(soonest, f.ticksToImpact());
                    if (powered && shown.add(id)) {
                        contacts.add(contact(f, true));
                    }
                }
            }
            members.add(MemberView.of(m, now, soonest));
        }
        // What its working radars track (anyone's missiles).
        if (powered) {
            for (LaunchNetwork.Member m : n.members()) {
                if (m.role != LaunchNetwork.Role.RADAR || m.lost() || !m.inContact(now) || !m.report().working()) {
                    continue;
                }
                for (Flights.Flight f : flying.values()) {
                    net.minecraft.world.phys.Vec3 p = f.position();
                    if (Math.abs(p.x - m.at().getX() - 0.5) <= RadarArrayBlockEntity.RANGE && Math.abs(p.z - m.at().getZ() - 0.5) <= RadarArrayBlockEntity.RANGE
                            && shown.add(f.id())) {
                        contacts.add(contact(f, false));
                    }
                }
            }
        }
        return new Picture(members, contacts, spots, blasts);
    }

    private static ContactView contact(Flights.Flight f, boolean own) {
        net.minecraft.world.phys.Vec3 p = f.position();
        return new ContactView(f.trackId(), (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), f.heading(),
                (int) Math.floor(f.target().x), (int) Math.floor(f.target().z), f.ticksToImpact(), own);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchNetwork.Event> EVENT = StreamCodec.of((buf, e) -> {
        buf.writeVarLong(e.time());
        buf.writeVarInt(e.kind().ordinal());
        buf.writeBlockPos(e.member());
        buf.writeUtf(e.memberKind());
        buf.writeVarInt(e.x());
        buf.writeVarInt(e.z());
        buf.writeVarInt(e.detail() + 1);
    }, buf -> new LaunchNetwork.Event(buf.readVarLong(), LaunchNetwork.EventKind.values()[Math.clamp(buf.readVarInt(), 0,
            LaunchNetwork.EventKind.values().length - 1)], buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1));

    /** The station's state for its screen, sent while it is open: its network's members, the missiles in the air, its log. */
    public record Scope(BlockPos pos, BlockPos at, boolean formed, boolean powered, int energy, String code, long now, List<MemberView> members,
            List<ContactView> contacts, List<LaunchNetwork.Event> log, List<SpotView> spots, List<BlastView> blasts) implements CustomPacketPayload {
        public static final Type<Scope> TYPE = GBNetwork.type("station_scope");
        public static final StreamCodec<RegistryFriendlyByteBuf, Scope> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos());
            buf.writeBlockPos(s.at());
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.powered());
            buf.writeVarInt(s.energy());
            buf.writeUtf(s.code());
            buf.writeVarLong(s.now());
            MemberView.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.members());
            ContactView.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.contacts());
            EVENT.apply(ByteBufCodecs.list()).encode(buf, s.log());
            SpotView.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.spots());
            BlastView.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.blasts());
        }, buf -> new Scope(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(), buf.readVarLong(),
                MemberView.CODEC.apply(ByteBufCodecs.list()).decode(buf), ContactView.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                EVENT.apply(ByteBufCodecs.list()).decode(buf), SpotView.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                BlastView.CODEC.apply(ByteBufCodecs.list()).decode(buf)));

        @Override
        public Type<Scope> type() {
            return TYPE;
        }
    }
}
