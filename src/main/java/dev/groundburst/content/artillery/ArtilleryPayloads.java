package dev.groundburst.content.artillery;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.ByteBufCodecs;
import dev.groundburst.legacy.net.StreamCodec;
import dev.groundburst.legacy.net.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import dev.groundburst.legacy.net.IPayloadContext;

/**
 * A gun's crew and the gun (DESIGN.md §20.1): the crewman's aim, shots and shell picks to the server; the gun as it is
 * laid to everyone near it (its model, and its crew's reticle and readout).
 */
public final class ArtilleryPayloads {
    /** Client hooks: a gun's state arrives; a tile of the far ground's map arrives (set by the client). */
    public static Consumer<State> stateHook = s -> {};
    public static Consumer<Terrain> terrainHook = t -> {};

    /** The far ground's map: tiles {@link #TILE} blocks square, sampled every {@link #STEP} blocks. */
    public static final int TILE = 128, STEP = 2, SAMPLES = TILE / STEP;
    /** A sample with nothing known there. */
    public static final short UNKNOWN = Short.MIN_VALUE;

    /**
     * The crewman's strategic view wants the map of the tile at ({@code ox}, {@code oz}) (its corner, a multiple of
     * {@link #TILE}): ground their game has not loaded, within the gun's reach.
     */
    public record TerrainRequest(BlockPos gun, int ox, int oz) implements CustomPacketPayload {
        public static final Type<TerrainRequest> TYPE = GBNetwork.type("artillery_terrain_request");
        public static final StreamCodec<FriendlyByteBuf, TerrainRequest> CODEC = StreamCodec.composite(dev.groundburst.legacy.net.Codecs.BLOCK_POS,
                TerrainRequest::gun, ByteBufCodecs.VAR_INT, TerrainRequest::ox, ByteBufCodecs.VAR_INT, TerrainRequest::oz, TerrainRequest::new);

        @Override
        public Type<TerrainRequest> type() {
            return TYPE;
        }

        public static void handle(TerrainRequest r, IPayloadContext context) {
            GunBlockEntity m = mortarAt(context, r.gun());
            if (m != null) {
                m.mapTile((ServerPlayer) context.player(), r.ox(), r.oz());
            }
        }
    }

    /**
     * A tile of the far ground's map: its corner, the top of the ground at each sample (blocks; {@link #UNKNOWN}: not
     * known) and its colour there (as a map shows it, RGB), row by row from its corner.
     */
    public record Terrain(int ox, int oz, short[] heights, int[] colours) implements CustomPacketPayload {
        public static final Type<Terrain> TYPE = GBNetwork.type("artillery_terrain");
        public static final StreamCodec<FriendlyByteBuf, Terrain> CODEC = StreamCodec.of((buf, t) -> t.write(buf), Terrain::read);

        @Override
        public Type<Terrain> type() {
            return TYPE;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(this.ox);
            buf.writeVarInt(this.oz);
            for (int i = 0; i < SAMPLES * SAMPLES; i++) {
                buf.writeShort(this.heights[i]);
                buf.writeMedium(this.colours[i]);
            }
        }

        private static Terrain read(FriendlyByteBuf buf) {
            int ox = buf.readVarInt(), oz = buf.readVarInt();
            short[] h = new short[SAMPLES * SAMPLES];
            int[] c = new int[SAMPLES * SAMPLES];
            for (int i = 0; i < h.length; i++) {
                h[i] = buf.readShort();
                c[i] = buf.readUnsignedMedium();
            }
            return new Terrain(ox, oz, h, c);
        }
    }

    private ArtilleryPayloads() {}

    private static GunBlockEntity mortarAt(IPayloadContext context, BlockPos pos) {
        return ((Object) context.player()) instanceof ServerPlayer player && player.level().isLoaded(pos)
                && ((Object) player.level().getBlockEntity(pos)) instanceof GunBlockEntity m ? m : null;
    }

    /** Where the crewman wants the gun laid: a point on the ground. */
    public record Aim(BlockPos gun, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Aim> TYPE = GBNetwork.type("artillery_aim");
        public static final StreamCodec<FriendlyByteBuf, Aim> CODEC = StreamCodec.composite(dev.groundburst.legacy.net.Codecs.BLOCK_POS, Aim::gun,
                ByteBufCodecs.DOUBLE, Aim::x, ByteBufCodecs.DOUBLE, Aim::y, ByteBufCodecs.DOUBLE, Aim::z, Aim::new);

        public Aim(BlockPos gun, Vec3 at) {
            this(gun, at.x, at.y, at.z);
        }

        @Override
        public Type<Aim> type() {
            return TYPE;
        }

        public static void handle(Aim aim, IPayloadContext context) {
            GunBlockEntity m = mortarAt(context, aim.gun());
            if (m != null && Double.isFinite(aim.x()) && Double.isFinite(aim.y()) && Double.isFinite(aim.z())) {
                m.aim((ServerPlayer) context.player(), new Vec3(aim.x(), aim.y(), aim.z()));
            }
        }
    }

    /** The crewman fires. */
    public record Fire(BlockPos gun) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = GBNetwork.type("artillery_fire");
        public static final StreamCodec<FriendlyByteBuf, Fire> CODEC = StreamCodec.composite(dev.groundburst.legacy.net.Codecs.BLOCK_POS, Fire::gun, Fire::new);

        @Override
        public Type<Fire> type() {
            return TYPE;
        }

        public static void handle(Fire fire, IPayloadContext context) {
            GunBlockEntity m = mortarAt(context, fire.gun());
            if (m != null) {
                m.fire((ServerPlayer) context.player());
            }
        }
    }

    /** The crewman picks the shell to fire next (its place in the gun's rack). */
    public record Select(BlockPos gun, int shell) implements CustomPacketPayload {
        public static final Type<Select> TYPE = GBNetwork.type("artillery_select");
        public static final StreamCodec<FriendlyByteBuf, Select> CODEC = StreamCodec.composite(dev.groundburst.legacy.net.Codecs.BLOCK_POS, Select::gun,
                ByteBufCodecs.VAR_INT, Select::shell, Select::new);

        @Override
        public Type<Select> type() {
            return TYPE;
        }

        public static void handle(Select select, IPayloadContext context) {
            GunBlockEntity m = mortarAt(context, select.gun());
            if (m != null) {
                m.select((ServerPlayer) context.player(), select.shell());
            }
        }
    }

    /**
     * A gun as it is laid: its tube's yaw and elevation, its charge, its spread (times its least), ticks until it is
     * loaded, the shell picked (its place in the rack), whether its aim is in reach, how many shots it has fired (the
     * client kicks the tube when this rises), its crewman (an entity id; -1: nobody), the shells of each kind it can
     * fire (its rack and its crewman's inventory; -1: without end, a creative crewman's) and the point it is laid on
     * ({@code laid} false: none).
     */
    public record State(BlockPos gun, float yaw, float elevation, int charge, float dispersion, int reload, int selected, boolean reachable,
                        int shots, int crew, int[] counts, boolean laid, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<State> TYPE = GBNetwork.type("artillery_state");
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((buf, s) -> s.write(buf), State::read);

        @Override
        public Type<State> type() {
            return TYPE;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(this.gun);
            buf.writeFloat(this.yaw);
            buf.writeFloat(this.elevation);
            buf.writeVarInt(this.charge);
            buf.writeFloat(this.dispersion);
            buf.writeVarInt(this.reload);
            buf.writeVarInt(this.selected);
            buf.writeBoolean(this.reachable);
            buf.writeVarInt(this.shots);
            buf.writeVarInt(this.crew);
            buf.writeVarInt(this.counts.length);
            for (int c : this.counts) {
                buf.writeVarInt(c);
            }
            buf.writeBoolean(this.laid);
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
        }

        private static State read(FriendlyByteBuf buf) {
            BlockPos gun = buf.readBlockPos();
            float yaw = buf.readFloat(), elevation = buf.readFloat();
            int charge = buf.readVarInt();
            float dispersion = buf.readFloat();
            int reload = buf.readVarInt(), selected = buf.readVarInt();
            boolean reachable = buf.readBoolean();
            int shots = buf.readVarInt(), crew = buf.readVarInt();
            int[] counts = new int[Math.min(16, buf.readVarInt())];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = buf.readVarInt();
            }
            boolean laid = buf.readBoolean();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            return new State(gun, yaw, elevation, charge, dispersion, reload, selected, reachable, shots, crew, counts, laid, x, y, z);
        }
    }
}
