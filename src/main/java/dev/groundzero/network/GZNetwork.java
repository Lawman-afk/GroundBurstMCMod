package dev.groundzero.network;

import dev.groundzero.GroundZero;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Server-bound payloads register their handler here; client-bound payloads register
 * their codec here and their handler in {@code GroundZeroClient} so client classes never load on a server.
 * Bump {@link #PROTOCOL_VERSION} whenever a payload's wire format changes.
 */
public final class GZNetwork {
    public static final String PROTOCOL_VERSION = "1";

    private GZNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GZNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        // Each feature's payloads are added here as the feature lands (first: blast sync in Phase 5).
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(GroundZero.id(name));
    }
}
