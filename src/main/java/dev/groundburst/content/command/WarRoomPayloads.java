package dev.groundburst.content.command;

import java.util.List;
import java.util.function.Consumer;

import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.StationPayloads;
import dev.groundburst.network.GBNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The War Room Display (DESIGN.md §17.17): its board, sent to the room every half second, and its settings, sent back. */
public final class WarRoomPayloads {
    private static final double REACH = 8;

    /** Client hook: a board arrives (set by the client). */
    public static Consumer<Board> boardHook = b -> {};

    private WarRoomPayloads() {}

    /** Where a missile came down lately, how long ago (ticks), and whether it was the network's own. */
    public record ImpactView(int x, int z, int age, boolean own) {
        static final StreamCodec<RegistryFriendlyByteBuf, ImpactView> CODEC = StreamCodec.of((buf, i) -> {
            buf.writeVarInt(i.x());
            buf.writeVarInt(i.z());
            buf.writeVarInt(i.age());
            buf.writeBoolean(i.own());
        }, buf -> new ImpactView(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    }

    /**
     * Its whole picture: where it is in the world, the time, whether it is lit, its network's code (empty: none linked),
     * the alert condition (5 normal to 1 attack), the network's members, missiles in the air, blasts, impacts, laser
     * spots and the last lines of its log, and how it is set to draw them (its view and layers).
     */
    public record Board(BlockPos pos, BlockPos at, long now, boolean powered, String code, int condition, List<StationPayloads.MemberView> members,
            List<StationPayloads.ContactView> contacts, List<StationPayloads.BlastView> blasts, List<ImpactView> impacts, List<StationPayloads.SpotView> spots,
            List<LaunchNetwork.Event> log, int view, int layers) implements CustomPacketPayload {
        public static final Type<Board> TYPE = GBNetwork.type("war_room_board");
        public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = StreamCodec.of((buf, b) -> {
            buf.writeBlockPos(b.pos());
            buf.writeBlockPos(b.at());
            buf.writeVarLong(b.now());
            buf.writeBoolean(b.powered());
            buf.writeUtf(b.code());
            buf.writeVarInt(b.condition());
            StationPayloads.MemberView.CODEC.apply(ByteBufCodecs.list()).encode(buf, b.members());
            StationPayloads.ContactView.CODEC.apply(ByteBufCodecs.list()).encode(buf, b.contacts());
            StationPayloads.BlastView.CODEC.apply(ByteBufCodecs.list()).encode(buf, b.blasts());
            ImpactView.CODEC.apply(ByteBufCodecs.list()).encode(buf, b.impacts());
            StationPayloads.SpotView.CODEC.apply(ByteBufCodecs.list()).encode(buf, b.spots());
            StationPayloads.EVENT.apply(ByteBufCodecs.list()).encode(buf, b.log());
            buf.writeVarInt(b.view());
            buf.writeVarInt(b.layers());
        }, buf -> new Board(buf.readBlockPos(), buf.readBlockPos(), buf.readVarLong(), buf.readBoolean(), buf.readUtf(), buf.readVarInt(),
                StationPayloads.MemberView.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                StationPayloads.ContactView.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                StationPayloads.BlastView.CODEC.apply(ByteBufCodecs.list()).decode(buf), ImpactView.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                StationPayloads.SpotView.CODEC.apply(ByteBufCodecs.list()).decode(buf), StationPayloads.EVENT.apply(ByteBufCodecs.list()).decode(buf),
                buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<Board> type() {
            return TYPE;
        }
    }

    /** Its view (0: fit everything in; 1 on: centred on it at a scale) and the layers it draws. */
    public record Settings(BlockPos pos, int view, int layers) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = GBNetwork.type("war_room_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Settings::pos, ByteBufCodecs.VAR_INT, Settings::view, ByteBufCodecs.VAR_INT, Settings::layers, Settings::new);

        @Override
        public Type<Settings> type() {
            return TYPE;
        }

        public static void handle(Settings p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && dev.groundburst.content.machine.Carried.near(player, p.pos(), REACH)
                    && dev.groundburst.content.machine.Carried.blockEntity(player.level(), p.pos(), WarRoomDisplayBlockEntity.class)
                            instanceof WarRoomDisplayBlockEntity d && player.level() instanceof ServerLevel level) {
                d.applySettings(level, p.view(), p.layers());
            }
        }
    }
}
