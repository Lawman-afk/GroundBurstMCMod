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

/** The Dead Hand's console (DESIGN.md §17.17): its state, sent while it is open, and its orders, sent back. */
public final class DeadHandPayloads {
    private static final double REACH = 8;

    /** Client hook: its state arrives (set by the client). */
    public static Consumer<Scope> scopeHook = s -> {};

    private DeadHandPayloads() {}

    private static final StreamCodec<RegistryFriendlyByteBuf, DeadHandBlockEntity.Entry> ENTRY = StreamCodec.of((buf, e) -> {
        buf.writeVarLong(e.time());
        buf.writeVarInt(e.line().ordinal());
        buf.writeVarInt(e.a());
        buf.writeVarInt(e.b());
    }, buf -> new DeadHandBlockEntity.Entry(buf.readVarLong(), DeadHandBlockEntity.Line.values()[Math.clamp(buf.readVarInt(), 0,
            DeadHandBlockEntity.Line.values().length - 1)], buf.readVarInt(), buf.readVarInt()));

    /**
     * Its state: the time; formed and powered; what it is doing ({@link DeadHandBlockEntity.State} ordinal), whether it has
     * a rocket and is drilling; in alert, when it fires and the strike (where, how big, how far, whether it is known where
     * it was fired from); its network's code (empty: none); why it cannot be armed (empty: it can); whether the first key
     * is turned; its settings; the launchers standing by for it, how many of its network's launchers its rocket's course
     * would reach (of how many), how many have taken the order from its rocket in the air (DESIGN.md §17.19); its log.
     */
    public record Scope(BlockPos pos, long now, boolean formed, boolean powered, int state, boolean rocket, boolean drill, long alertUntil, int strikeX,
            int strikeZ, float strikeMagnitude, int strikeDistance, boolean origin, String network, String cannot, boolean firstKey, int radius,
            int threshold, int countdown, int command, int targets, int channel, int standing, int reaches, int launchers, int sent,
            List<DeadHandBlockEntity.Entry> log) implements CustomPacketPayload {
        public static final Type<Scope> TYPE = GBNetwork.type("dead_hand_scope");
        public static final StreamCodec<RegistryFriendlyByteBuf, Scope> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos());
            buf.writeVarLong(s.now());
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.powered());
            buf.writeVarInt(s.state());
            buf.writeBoolean(s.rocket());
            buf.writeBoolean(s.drill());
            buf.writeVarLong(s.alertUntil() + 1);
            buf.writeVarInt(s.strikeX());
            buf.writeVarInt(s.strikeZ());
            buf.writeFloat(s.strikeMagnitude());
            buf.writeVarInt(s.strikeDistance());
            buf.writeBoolean(s.origin());
            buf.writeUtf(s.network());
            buf.writeUtf(s.cannot());
            buf.writeBoolean(s.firstKey());
            buf.writeVarInt(s.radius());
            buf.writeVarInt(s.threshold());
            buf.writeVarInt(s.countdown());
            buf.writeVarInt(s.command());
            buf.writeVarInt(s.targets());
            buf.writeVarInt(s.channel());
            buf.writeVarInt(s.standing());
            buf.writeVarInt(s.reaches());
            buf.writeVarInt(s.launchers());
            buf.writeVarInt(s.sent());
            ENTRY.apply(ByteBufCodecs.list()).encode(buf, s.log());
        }, buf -> new Scope(buf.readBlockPos(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean(), buf.readVarLong() - 1, buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(),
                buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                ENTRY.apply(ByteBufCodecs.list()).decode(buf)));

        @Override
        public Type<Scope> type() {
            return TYPE;
        }
    }

    /** An order from its console: {@link DeadHandBlockEntity#ARM} and the rest, with a setting's new value. */
    public record Order(BlockPos pos, int action, int value) implements CustomPacketPayload {
        public static final Type<Order> TYPE = GBNetwork.type("dead_hand_order");
        public static final StreamCodec<RegistryFriendlyByteBuf, Order> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Order::pos, ByteBufCodecs.VAR_INT, Order::action, ByteBufCodecs.VAR_INT, Order::value, Order::new);

        @Override
        public Type<Order> type() {
            return TYPE;
        }

        public static void handle(Order p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && dev.groundburst.content.machine.Carried.near(player, p.pos(), REACH)
                    && dev.groundburst.content.machine.Carried.blockEntity(player.level(), p.pos(), DeadHandBlockEntity.class)
                            instanceof DeadHandBlockEntity d && player.level() instanceof ServerLevel level) {
                d.order(level, p.action(), p.value());
            }
        }
    }
}
