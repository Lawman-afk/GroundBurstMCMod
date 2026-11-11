package dev.groundburst.legacy.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's one Forge channel, carrying every payload the mod registers (NeoForge gives each its own): a payload goes as
 * its type's id and then its own codec's bytes, and its handler runs on the receiving side's main thread, as NeoForge's
 * handlers do.
 */
public final class Network {
    private Network() {}

    enum Direction {
        TO_CLIENT, TO_SERVER, BOTH
    }

    private record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler, Direction direction) {
    }

    /** One message on the channel: any payload. */
    record Wrapped(CustomPacketPayload payload) {
    }

    private static final Map<ResourceLocation, Entry<?>> BY_ID = new ConcurrentHashMap<>();
    private static @Nullable SimpleChannel channel;
    /**
     * The mod's payload protocol: every version its registrars were asked for, in turn (GBNetwork.PROTOCOL_VERSION, then the
     * Create payloads' own), so a client and a server that differ in any of them are told apart, as NeoForge's would be.
     */
    private static final java.util.Set<String> VERSIONS = new java.util.LinkedHashSet<>();
    private static String version = "1";
    /** The client's own player (set on the client only, so this class never names a client class). */
    public static Supplier<@Nullable Player> clientPlayer = () -> null;

    static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler, Direction direction) {
        BY_ID.put(type.id(), new Entry<>(type, codec, handler, direction));
    }

    /** A payload of this branch's own (not the mod's): to the client. */
    public static <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        register(type, codec, handler, Direction.TO_CLIENT);
    }

    static synchronized void version(String v) {
        VERSIONS.add(v);
        version = String.join("+", VERSIONS);
    }

    public static String version() {
        return version;
    }

    /** Makes the channel (once the payloads are registered: {@link dev.groundburst.legacy.Lifecycle}). */
    public static void open(String version) {
        if (channel != null) {
            return;
        }
        SimpleChannel c = NetworkRegistry.newSimpleChannel(new ResourceLocation(GroundBurst.MODID, "main"), () -> version, version::equals,
                version::equals);
        c.messageBuilder(Wrapped.class, 0).encoder(Network::encode).decoder(Network::decode).consumerMainThread(Network::handle).add();
        channel = c;
    }

    static SimpleChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("The mod's network channel is not open yet");
        }
        return channel;
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void encode(Wrapped message, FriendlyByteBuf buf) {
        T payload = (T) message.payload();
        Entry<T> entry = (Entry<T>) BY_ID.get(payload.type().id());
        if (entry == null) {
            throw new IllegalStateException("Payload not registered: " + payload.type().id());
        }
        buf.writeResourceLocation(entry.type().id());
        entry.codec().encode(buf, payload);
    }

    private static Wrapped decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        Entry<?> entry = BY_ID.get(id);
        if (entry == null) {
            throw new IllegalStateException("Payload not registered: " + id);
        }
        return new Wrapped(entry.codec().decode(buf));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void handle(Wrapped message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        T payload = (T) message.payload();
        Entry<T> entry = (Entry<T>) BY_ID.get(payload.type().id());
        if (entry == null) {
            return;
        }
        boolean toServer = ctx.getDirection().getReceptionSide().isServer();
        if (entry.direction() == (toServer ? Direction.TO_CLIENT : Direction.TO_SERVER)) {
            GroundBurst.LOGGER.warn("Payload {} came the wrong way", payload.type().id());
            return;
        }
        IPayloadContext context = new IPayloadContext() {
            @Override
            public Player player() {
                return toServer ? ctx.getSender() : clientPlayer.get();
            }

            @Override
            public void reply(CustomPacketPayload reply) {
                channel().reply(new Wrapped(reply), ctx);
            }
        };
        try {
            entry.handler().handle(payload, context);
        } catch (RuntimeException e) {
            GroundBurst.LOGGER.error("Payload {} failed", payload.type().id(), e);
        }
    }
}
