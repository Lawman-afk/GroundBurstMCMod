package dev.groundburst.legacy.net;

/** NeoForge's payload handler: run on the game's main thread as the payload comes. */
@FunctionalInterface
public interface IPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext context);
}
