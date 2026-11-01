package dev.groundburst.content.launch;

import java.util.function.Consumer;

import dev.groundburst.network.GBNetwork;
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
     * Its mode; its network's laser-guided launchers that could be armed on the spot and those armed; the missiles
     * homing on its spot; its network's code (empty: none); its charge, percent; and its last left click's result
     * ({@link LaserDesignatorItem.Result}) with how many launchers it took, for two seconds after it.
     */
    public record Status(int mode, int ready, int armed, int homing, String network, int charge, int result, int count)
            implements CustomPacketPayload {
        public static final Type<Status> TYPE = GBNetwork.type("designator_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Status::mode, ByteBufCodecs.VAR_INT, Status::ready, ByteBufCodecs.VAR_INT, Status::armed,
                ByteBufCodecs.VAR_INT, Status::homing, ByteBufCodecs.STRING_UTF8, Status::network, ByteBufCodecs.VAR_INT, Status::charge,
                ByteBufCodecs.VAR_INT, Status::result, ByteBufCodecs.VAR_INT, Status::count, Status::new);

        @Override
        public Type<Status> type() {
            return TYPE;
        }
    }
}
