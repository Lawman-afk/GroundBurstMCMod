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
    public static final String PROTOCOL_VERSION = "4";

    private GZNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GZNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        // Blast sync (DESIGN.md §12): handled on the client by BlastSounds (and the Spectacle effects).
        registrar.playToClient(dev.groundzero.blast.BlastPayloads.Start.TYPE, dev.groundzero.blast.BlastPayloads.Start.CODEC);
        registrar.playToClient(dev.groundzero.blast.BlastPayloads.Front.TYPE, dev.groundzero.blast.BlastPayloads.Front.CODEC);
        registrar.playToClient(dev.groundzero.blast.BlastPayloads.End.TYPE, dev.groundzero.blast.BlastPayloads.End.CODEC);
        // Placed charges (DESIGN.md §9.4): the fuze settings and defuse screens, and the Remote Detonator's frequency.
        registrar.playToServer(dev.groundzero.content.ordnance.charge.ChargePayloads.Settings.TYPE,
                dev.groundzero.content.ordnance.charge.ChargePayloads.Settings.CODEC, dev.groundzero.content.ordnance.charge.ChargePayloads.Settings::handle);
        registrar.playToServer(dev.groundzero.content.ordnance.charge.ChargePayloads.CutWire.TYPE,
                dev.groundzero.content.ordnance.charge.ChargePayloads.CutWire.CODEC, dev.groundzero.content.ordnance.charge.ChargePayloads.CutWire::handle);
        // The Launch Computer (DESIGN.md §17.3): its scope is handled on the client in GroundZeroClient.
        registrar.playToServer(dev.groundzero.content.launch.LaunchPayloads.Watch.TYPE, dev.groundzero.content.launch.LaunchPayloads.Watch.CODEC,
                dev.groundzero.content.launch.LaunchPayloads.Watch::handle);
        registrar.playToServer(dev.groundzero.content.launch.LaunchPayloads.Target.TYPE, dev.groundzero.content.launch.LaunchPayloads.Target.CODEC,
                dev.groundzero.content.launch.LaunchPayloads.Target::handle);
        registrar.playToServer(dev.groundzero.content.launch.LaunchPayloads.Launch.TYPE, dev.groundzero.content.launch.LaunchPayloads.Launch.CODEC,
                dev.groundzero.content.launch.LaunchPayloads.Launch::handle);
        registrar.playToClient(dev.groundzero.content.launch.LaunchPayloads.Scope.TYPE, dev.groundzero.content.launch.LaunchPayloads.Scope.CODEC,
                (payload, context) -> dev.groundzero.content.launch.LaunchPayloads.scopeHook.accept(payload));
        registrar.playToServer(dev.groundzero.content.ordnance.charge.ChargePayloads.DetonatorFrequency.TYPE,
                dev.groundzero.content.ordnance.charge.ChargePayloads.DetonatorFrequency.CODEC,
                dev.groundzero.content.ordnance.charge.ChargePayloads.DetonatorFrequency::handle);
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(GroundZero.id(name));
    }
}
