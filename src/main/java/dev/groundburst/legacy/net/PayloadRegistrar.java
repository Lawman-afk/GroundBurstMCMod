package dev.groundburst.legacy.net;

import net.minecraft.network.FriendlyByteBuf;

/** NeoForge's payload registrar: each payload's codec and handler, kept for the mod's channel ({@link Network}). */
public final class PayloadRegistrar {
    private final String version;
    private final boolean optional;

    PayloadRegistrar(String version, boolean optional) {
        this.version = version;
        this.optional = optional;
    }

    public PayloadRegistrar optional() {
        return new PayloadRegistrar(this.version, true);
    }

    public PayloadRegistrar versioned(String version) {
        return new PayloadRegistrar(version, this.optional);
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        Network.register(type, codec, handler, Network.Direction.TO_CLIENT);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        Network.register(type, codec, handler, Network.Direction.TO_SERVER);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        Network.register(type, codec, handler, Network.Direction.BOTH);
        return this;
    }
}
