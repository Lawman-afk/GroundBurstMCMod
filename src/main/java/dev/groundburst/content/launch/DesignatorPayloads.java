package dev.groundburst.content.launch;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What a lasing player's Laser Designator screen is sent (DESIGN.md §17.13): what only the server knows. */
public final class DesignatorPayloads {
    /** Client hooks: a status arrives; a lasing player's beam arrives (set by the client). */
    public static Consumer<Status> statusHook = s -> {};
    public static Consumer<Beam> beamHook = b -> {};

    private DesignatorPayloads() {}

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
     * Its mode; how far a call-for-fire lock has come (0-1) and whether it has called fire; the armed launchers of its
     * network that reach the spot; the missiles homing on it; its network's code (empty: none); its charge, percent.
     */
    public record Status(int mode, float lock, boolean fired, int armed, int homing, String network, int charge) implements CustomPacketPayload {
        public static final Type<Status> TYPE = GBNetwork.type("designator_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Status::mode, ByteBufCodecs.FLOAT, Status::lock, ByteBufCodecs.BOOL, Status::fired,
                ByteBufCodecs.VAR_INT, Status::armed, ByteBufCodecs.VAR_INT, Status::homing, ByteBufCodecs.STRING_UTF8, Status::network,
                ByteBufCodecs.VAR_INT, Status::charge, Status::new);

        @Override
        public Type<Status> type() {
            return TYPE;
        }
    }
}
