package dev.groundburst.content.launch;

import java.util.List;
import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What a lasing player's Laser Designator screen is sent (DESIGN.md §17.13): what only the server knows. */
public final class DesignatorPayloads {
    /** Client hooks: a status arrives; a lasing player's beam arrives (set by the client). */
    public static Consumer<Status> statusHook = s -> {};
    public static Consumer<Beam> beamHook = b -> {};

    private DesignatorPayloads() {}

    /** A left click while lasing (its holder's client): what it does is the designator's mode's; the server checks it. */
    public record Click() implements CustomPacketPayload {
        public static final Type<Click> TYPE = GBNetwork.type("designator_click");
        public static final StreamCodec<RegistryFriendlyByteBuf, Click> CODEC = StreamCodec.unit(new Click());

        @Override
        public Type<Click> type() {
            return TYPE;
        }

        public static void handle(Click click, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                LaserDesignatorItem.click(player);
            }
        }
    }

    /** The mouse wheel while lasing zoomed: pick the next ({@code step} 1) or the previous (-1) laser-guided launcher. */
    public record Scroll(int step) implements CustomPacketPayload {
        public static final Type<Scroll> TYPE = GBNetwork.type("designator_scroll");
        public static final StreamCodec<RegistryFriendlyByteBuf, Scroll> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Scroll::step, Scroll::new);

        @Override
        public Type<Scroll> type() {
            return TYPE;
        }

        public static void handle(Scroll scroll, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                LaserDesignatorItem.scroll(player, Integer.signum(scroll.step()));
            }
        }
    }

    /**
     * A lasing player's beam as the server traced it (where missiles home), for every player who can see the holder:
     * the holder (an entity id) and where the beam lands, if it lands ({@code lands} false: it reaches nothing). Their
     * clients draw the beam to it rather than trace it themselves from the holder's rotation, which reaches them rounded
     * to a degree and a half: far off, that would put the beam blocks from the real spot.
     */
    public record Beam(int holder, boolean lands, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Beam> TYPE = GBNetwork.type("designator_beam");
        public static final StreamCodec<RegistryFriendlyByteBuf, Beam> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Beam::holder, ByteBufCodecs.BOOL, Beam::lands, ByteBufCodecs.DOUBLE, Beam::x, ByteBufCodecs.DOUBLE, Beam::y,
                ByteBufCodecs.DOUBLE, Beam::z, Beam::new);

        @Override
        public Type<Beam> type() {
            return TYPE;
        }
    }

    /**
     * The launcher a designator works with: its number on the network (0: none), its kind (its block's id), its place
     * among the network's laser-guided launchers and how many there are, and its state ({@link LaserDesignatorItem.Pick}).
     */
    public record Pick(int number, String kind, int index, int total, int state) {
        public static final Pick NONE = new Pick(0, "", 0, 0, 0);
        public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Pick::number,
                ByteBufCodecs.STRING_UTF8, Pick::kind, ByteBufCodecs.VAR_INT, Pick::index, ByteBufCodecs.VAR_INT, Pick::total,
                ByteBufCodecs.VAR_INT, Pick::state, Pick::new);
    }

    /**
     * The launcher it works with; the missiles homing on its spot; its network's code (empty: none); its
     * charge, percent; its last left click's result ({@link LaserDesignatorItem.Result}) and the launcher's number, for
     * two seconds after it; where the network's armed launchers are aimed, and which of those is its launcher's (-1: none).
     */
    public record Status(Pick pick, int homing, String network, int charge, int result, int count, List<BlockPos> aims, int picked)
            implements CustomPacketPayload {
        public static final Type<Status> TYPE = GBNetwork.type("designator_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
                Pick.CODEC, Status::pick, ByteBufCodecs.VAR_INT, Status::homing,
                ByteBufCodecs.STRING_UTF8, Status::network, ByteBufCodecs.VAR_INT, Status::charge, ByteBufCodecs.VAR_INT, Status::result,
                ByteBufCodecs.VAR_INT, Status::count, BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), Status::aims,
                ByteBufCodecs.VAR_INT, Status::picked, Status::new);

        @Override
        public Type<Status> type() {
            return TYPE;
        }
    }
}
