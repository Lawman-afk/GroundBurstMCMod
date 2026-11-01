package dev.groundburst.content.launch;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What a lasing player's Laser Designator screen is sent (DESIGN.md §17.13): what only the server knows. */
public final class DesignatorPayloads {
    /** Client hook: a status arrives (set by the client). */
    public static Consumer<Status> statusHook = s -> {};

    private DesignatorPayloads() {}

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
