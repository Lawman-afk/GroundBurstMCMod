package dev.groundburst.content.launch;

import java.util.List;
import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The Launch Computer's screen: opening and closing it, aiming and arming each silo, launching; and the scope it is sent. */
public final class LaunchPayloads {
    private static final double REACH = 8;

    /** Client hooks: the scope arrives; a map tile arrives (set by the client). */
    public static Consumer<Scope> scopeHook = s -> {};
    public static Consumer<MapTile> tileHook = t -> {};
    public static Consumer<Radar> radarHook = r -> {};

    private LaunchPayloads() {}

    private static LaunchComputerBlockEntity computer(IPayloadContext context, BlockPos pos) {
        if (context.player() instanceof ServerPlayer player && player.position().distanceTo(Vec3.atCenterOf(pos)) <= REACH
                && player.level().getBlockEntity(pos) instanceof LaunchComputerBlockEntity c) {
            return c;
        }
        return null;
    }

    /** The screen of a computer or radar array opened (true) or closed (false). */
    public record Watch(BlockPos pos, boolean open) implements CustomPacketPayload {
        public static final Type<Watch> TYPE = GBNetwork.type("launch_watch");
        public static final StreamCodec<RegistryFriendlyByteBuf, Watch> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Watch::pos, ByteBufCodecs.BOOL, Watch::open, Watch::new);

        @Override
        public Type<Watch> type() {
            return TYPE;
        }

        public static void handle(Watch p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && player.position().distanceTo(Vec3.atCenterOf(p.pos())) <= REACH
                    && player.level().getBlockEntity(p.pos()) instanceof ScreenWatched c) {
                if (p.open()) {
                    c.addViewer(player);
                } else {
                    c.removeViewer(player);
                }
            }
        }
    }

    /** Aim one silo of this computer's. */
    public record Target(BlockPos pos, BlockPos silo, int x, int z) implements CustomPacketPayload {
        public static final Type<Target> TYPE = GBNetwork.type("launch_target");
        public static final StreamCodec<RegistryFriendlyByteBuf, Target> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Target::pos, BlockPos.STREAM_CODEC, Target::silo, ByteBufCodecs.VAR_INT, Target::x,
                ByteBufCodecs.VAR_INT, Target::z, Target::new);

        @Override
        public Type<Target> type() {
            return TYPE;
        }

        public static void handle(Target p, IPayloadContext context) {
            LaunchComputerBlockEntity c = computer(context, p.pos());
            if (c != null && context.player() instanceof ServerPlayer player) {
                c.setTarget((ServerLevel) player.level(), p.silo(), p.x(), p.z());
            }
        }
    }

    /** Arm one silo of this computer's (true), or make it safe (false). */
    public record Arm(BlockPos pos, BlockPos silo, boolean on) implements CustomPacketPayload {
        public static final Type<Arm> TYPE = GBNetwork.type("launch_arm");
        public static final StreamCodec<RegistryFriendlyByteBuf, Arm> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Arm::pos, BlockPos.STREAM_CODEC, Arm::silo, ByteBufCodecs.BOOL, Arm::on, Arm::new);

        @Override
        public Type<Arm> type() {
            return TYPE;
        }

        public static void handle(Arm p, IPayloadContext context) {
            LaunchComputerBlockEntity c = computer(context, p.pos());
            if (c != null && context.player() instanceof ServerPlayer player) {
                c.arm((ServerLevel) player.level(), p.silo(), p.on());
            }
        }
    }

    /** LAUNCH: fire every armed silo this computer links. */
    public record Launch(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Launch> TYPE = GBNetwork.type("launch_fire");
        public static final StreamCodec<RegistryFriendlyByteBuf, Launch> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Launch::pos, Launch::new);

        @Override
        public Type<Launch> type() {
            return TYPE;
        }

        public static void handle(Launch p, IPayloadContext context) {
            LaunchComputerBlockEntity c = computer(context, p.pos());
            if (c != null && context.player() instanceof ServerPlayer player) {
                c.launchArmed((ServerLevel) player.level(), player);
            }
        }
    }

    /**
     * The screen asks for map tiles round the computer (see {@link TerrainSurvey#tile}): at {@code scale} (one of
     * {@link TerrainSurvey#SCALES} blocks a sample), each tile packed as ChunkPos.pack(tx, tz). Only a formed, powered computer maps, and only within
     * its survey range; a few dozen tiles at a time.
     */
    public record MapRequest(BlockPos pos, int scale, List<Long> tiles) implements CustomPacketPayload {
        public static final int MAX_TILES = 32;
        public static final Type<MapRequest> TYPE = GBNetwork.type("launch_map_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, MapRequest> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MapRequest::pos, ByteBufCodecs.VAR_INT, MapRequest::scale,
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MAX_TILES)), MapRequest::tiles, MapRequest::new);

        @Override
        public Type<MapRequest> type() {
            return TYPE;
        }

        public static void handle(MapRequest p, IPayloadContext context) {
            int scale = p.scale();
            if (!(context.player() instanceof ServerPlayer player) || player.position().distanceTo(Vec3.atCenterOf(p.pos())) > REACH
                    || !(player.level().getBlockEntity(p.pos()) instanceof MapSource c) || !c.mapping() || !TerrainSurvey.validScale(scale)) {
                return;
            }
            boolean bounded = c.boundedMap();
            ServerLevel level = (ServerLevel) player.level();
            TerrainSurvey survey = TerrainSurvey.of(level);
            int span = TerrainSurvey.TILE * scale;
            for (long t : p.tiles()) {
                int tx = net.minecraft.world.level.ChunkPos.getX(t), tz = net.minecraft.world.level.ChunkPos.getZ(t);
                double cx = tx * span + span / 2.0 - p.pos().getX(), cz = tz * span + span / 2.0 - p.pos().getZ();
                if (bounded && (Math.abs(cx) > TerrainSurvey.RANGE + span || Math.abs(cz) > TerrainSurvey.RANGE + span)) {
                    continue;
                }
                TerrainSurvey.Tile tile = survey.tile(level, scale, tx, tz);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new MapTile(p.pos(), scale, tx, tz, tile.colours(), tile.partial()));
            }
        }
    }

    /** A map tile for the screen: {@link TerrainSurvey#TILE} squared map colours (empty: nothing known there); partial: ask again soon. */
    public record MapTile(BlockPos pos, int scale, int tx, int tz, byte[] colours, boolean partial) implements CustomPacketPayload {
        public static final Type<MapTile> TYPE = GBNetwork.type("launch_map_tile");
        public static final StreamCodec<RegistryFriendlyByteBuf, MapTile> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MapTile::pos, ByteBufCodecs.VAR_INT, MapTile::scale, ByteBufCodecs.VAR_INT, MapTile::tx,
                ByteBufCodecs.VAR_INT, MapTile::tz, ByteBufCodecs.BYTE_ARRAY, MapTile::colours, ByteBufCodecs.BOOL, MapTile::partial, MapTile::new);

        @Override
        public Type<MapTile> type() {
            return TYPE;
        }
    }

    /**
     * A linked silo: where, its readiness ({@link LaunchSiloBlockEntity.Readiness} ordinal), its countdown (-1: none),
     * whether it is armed, and its target if it has one.
     */
    public record Silo(BlockPos pos, int readiness, int countdown, boolean armed, boolean hasTarget, int targetX, int targetZ, int range,
            int kind, LaunchSite.Strike strike) {
        public static final StreamCodec<FriendlyByteBuf, Silo> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos());
            buf.writeVarInt(s.readiness());
            buf.writeVarInt(s.countdown() + 1);
            buf.writeBoolean(s.armed());
            buf.writeBoolean(s.hasTarget());
            buf.writeVarInt(s.targetX());
            buf.writeVarInt(s.targetZ());
            buf.writeVarInt(s.range());
            buf.writeVarInt(s.kind());
            s.strike().write(buf);
        }, buf -> new Silo(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), LaunchSite.Strike.read(buf)));
    }

    /**
     * A missile on the Radar Array: an id to follow it by, where it is, which way it heads (radians, 0 = south), where
     * it is coming down, ticks until it does, and whether that is near the array.
     */
    public record Track(int id, int x, int y, int z, float heading, int targetX, int targetZ, int eta, boolean incoming) {
        public static final StreamCodec<FriendlyByteBuf, Track> CODEC = StreamCodec.of((buf, t) -> {
            buf.writeInt(t.id());
            buf.writeVarInt(t.x());
            buf.writeVarInt(t.y());
            buf.writeVarInt(t.z());
            buf.writeFloat(t.heading());
            buf.writeVarInt(t.targetX());
            buf.writeVarInt(t.targetZ());
            buf.writeVarInt(t.eta());
            buf.writeBoolean(t.incoming());
        }, buf -> new Track(buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    }

    /** The Radar Array's state for its screen, sent while it is open. */
    public record Radar(BlockPos pos, boolean formed, boolean powered, int energy, List<Track> tracks) implements CustomPacketPayload {
        public static final Type<Radar> TYPE = GBNetwork.type("radar_tracks");
        public static final StreamCodec<RegistryFriendlyByteBuf, Radar> CODEC = StreamCodec.of((buf, r) -> {
            buf.writeBlockPos(r.pos());
            buf.writeBoolean(r.formed());
            buf.writeBoolean(r.powered());
            buf.writeVarInt(r.energy());
            Track.CODEC.apply(ByteBufCodecs.list()).encode(buf, r.tracks());
        }, buf -> new Radar(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                Track.CODEC.apply(ByteBufCodecs.list()).decode(buf)));

        @Override
        public Type<Radar> type() {
            return TYPE;
        }
    }

    /** A missile on the radar: where, which way it is heading (radians, 0 = south), where it is going. */
    public record Contact(int x, int y, int z, float heading, int targetX, int targetZ) {
        public static final StreamCodec<FriendlyByteBuf, Contact> CODEC = StreamCodec.of((buf, c) -> {
            buf.writeVarInt(c.x());
            buf.writeVarInt(c.y());
            buf.writeVarInt(c.z());
            buf.writeFloat(c.heading());
            buf.writeVarInt(c.targetX());
            buf.writeVarInt(c.targetZ());
        }, buf -> new Contact(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));
    }

    /** The computer's state for its screen, sent while it is open. */
    public record Scope(BlockPos pos, boolean formed, boolean powered, int energy, List<Silo> silos, List<Contact> contacts)
            implements CustomPacketPayload {
        public static final Type<Scope> TYPE = GBNetwork.type("launch_scope");
        public static final StreamCodec<RegistryFriendlyByteBuf, Scope> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos());
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.powered());
            buf.writeVarInt(s.energy());
            Silo.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.silos());
            Contact.CODEC.apply(ByteBufCodecs.list()).encode(buf, s.contacts());
        }, buf -> new Scope(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                Silo.CODEC.apply(ByteBufCodecs.list()).decode(buf), Contact.CODEC.apply(ByteBufCodecs.list()).decode(buf)));

        @Override
        public Type<Scope> type() {
            return TYPE;
        }
    }
}
