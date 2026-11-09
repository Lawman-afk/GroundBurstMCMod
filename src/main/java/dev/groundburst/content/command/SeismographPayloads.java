package dev.groundburst.content.command;

import java.util.List;
import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The Seismograph's screen (DESIGN.md §17.17): its record, sent while it is open, and its settings, sent back. */
public final class SeismographPayloads {
    private static final double REACH = 8;

    /** Client hook: its record arrives (set by the client). */
    public static Consumer<View> viewHook = v -> {};

    private SeismographPayloads() {}

    private static final StreamCodec<RegistryFriendlyByteBuf, SeismographBlockEntity.Record> RECORD = StreamCodec.of((buf, r) -> {
        buf.writeVarLong(r.event());
        buf.writeVarLong(r.arrival());
        buf.writeVarLong(r.second());
        buf.writeFloat(r.felt());
        buf.writeFloat(r.magnitude());
        buf.writeVarInt(r.distance());
        buf.writeFloat(r.bearing());
        buf.writeFloat(r.bearingSpread());
        buf.writeVarInt(r.distanceSpread());
        buf.writeVarInt(r.duration());
    }, buf -> new SeismographBlockEntity.Record(buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readFloat(), buf.readFloat(),
            buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));

    /** What its screen shows: whether it is listening, the time, its settings, its network's code (empty: none), its record. */
    public record View(BlockPos pos, boolean powered, long now, int sensitivity, int alarmAt, int channel, String network,
            List<SeismographBlockEntity.Record> records) implements CustomPacketPayload {
        public static final Type<View> TYPE = GBNetwork.type("seismograph_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeBlockPos(v.pos());
            buf.writeBoolean(v.powered());
            buf.writeVarLong(v.now());
            buf.writeVarInt(v.sensitivity());
            buf.writeVarInt(v.alarmAt());
            buf.writeVarInt(v.channel());
            buf.writeUtf(v.network());
            RECORD.apply(ByteBufCodecs.list(SeismographBlockEntity.KEEP)).encode(buf, v.records());
        }, buf -> new View(buf.readBlockPos(), buf.readBoolean(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(), RECORD.apply(ByteBufCodecs.list(SeismographBlockEntity.KEEP)).decode(buf)));

        @Override
        public Type<View> type() {
            return TYPE;
        }
    }

    /** Its settings: what it records, its alarm, its alarm channel. */
    public record Settings(BlockPos pos, int sensitivity, int alarmAt, int channel) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = GBNetwork.type("seismograph_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Settings::pos, ByteBufCodecs.VAR_INT, Settings::sensitivity, ByteBufCodecs.VAR_INT, Settings::alarmAt,
                ByteBufCodecs.VAR_INT, Settings::channel, Settings::new);

        @Override
        public Type<Settings> type() {
            return TYPE;
        }

        public static void handle(Settings p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && dev.groundburst.content.machine.Carried.near(player, p.pos(), REACH)
                    && dev.groundburst.content.machine.Carried.blockEntity(player.level(), p.pos(), SeismographBlockEntity.class)
                            instanceof SeismographBlockEntity s && player.level() instanceof ServerLevel level) {
                s.applySettings(level, p.sensitivity(), p.alarmAt(), p.channel());
            }
        }
    }
}
