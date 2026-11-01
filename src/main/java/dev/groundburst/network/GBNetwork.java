package dev.groundburst.network;

import dev.groundburst.GroundBurst;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Server-bound payloads register their handler here; client-bound payloads register
 * their codec here and their handler in {@code GroundBurstClient} so client classes never load on a server.
 * Bump {@link #PROTOCOL_VERSION} whenever a payload's wire format changes.
 */
public final class GBNetwork {
    public static final String PROTOCOL_VERSION = "17";

    private GBNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GBNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        // Blast sync (DESIGN.md §12): handled on the client by BlastSounds (and the Spectacle effects).
        registrar.playToClient(dev.groundburst.blast.BlastPayloads.Start.TYPE, dev.groundburst.blast.BlastPayloads.Start.CODEC);
        registrar.playToClient(dev.groundburst.blast.BlastPayloads.Front.TYPE, dev.groundburst.blast.BlastPayloads.Front.CODEC);
        registrar.playToClient(dev.groundburst.blast.BlastPayloads.End.TYPE, dev.groundburst.blast.BlastPayloads.End.CODEC);
        // Annihilations (DESIGN.md §16.3): an antimatter warhead's sphere, drawn on the client by AnnihilationFx.
        registrar.playToClient(dev.groundburst.blast.AnnihilationPayloads.Start.TYPE, dev.groundburst.blast.AnnihilationPayloads.Start.CODEC,
                (payload, context) -> dev.groundburst.blast.AnnihilationPayloads.startHook.accept(payload));
        registrar.playToClient(dev.groundburst.blast.AnnihilationPayloads.End.TYPE, dev.groundburst.blast.AnnihilationPayloads.End.CODEC,
                (payload, context) -> dev.groundburst.blast.AnnihilationPayloads.endHook.accept(payload));
        // Singularities (DESIGN.md §16.2): a singularity warhead's black hole, drawn on the client by SingularityFx.
        registrar.playToClient(dev.groundburst.blast.SingularityPayloads.Start.TYPE, dev.groundburst.blast.SingularityPayloads.Start.CODEC,
                (payload, context) -> dev.groundburst.blast.SingularityPayloads.startHook.accept(payload));
        // Storms (DESIGN.md §16.14): a storm warhead's supercell, drawn on the client by StormFx.
        registrar.playToClient(dev.groundburst.blast.StormPayloads.Start.TYPE, dev.groundburst.blast.StormPayloads.Start.CODEC,
                (payload, context) -> dev.groundburst.blast.StormPayloads.startHook.accept(payload));
        registrar.playToClient(dev.groundburst.blast.StormPayloads.Strike.TYPE, dev.groundburst.blast.StormPayloads.Strike.CODEC,
                (payload, context) -> dev.groundburst.blast.StormPayloads.strikeHook.accept(payload));
        registrar.playToClient(dev.groundburst.blast.StormPayloads.End.TYPE, dev.groundburst.blast.StormPayloads.End.CODEC,
                (payload, context) -> dev.groundburst.blast.StormPayloads.endHook.accept(payload));
        registrar.playToClient(dev.groundburst.blast.SingularityPayloads.Grow.TYPE, dev.groundburst.blast.SingularityPayloads.Grow.CODEC,
                (payload, context) -> dev.groundburst.blast.SingularityPayloads.growHook.accept(payload));
        registrar.playToClient(dev.groundburst.blast.SingularityPayloads.Torn.TYPE, dev.groundburst.blast.SingularityPayloads.Torn.CODEC,
                (payload, context) -> dev.groundburst.blast.SingularityPayloads.tornHook.accept(payload));
        registrar.playToClient(dev.groundburst.blast.SingularityPayloads.Collapse.TYPE, dev.groundburst.blast.SingularityPayloads.Collapse.CODEC,
                (payload, context) -> dev.groundburst.blast.SingularityPayloads.collapseHook.accept(payload));
        // Placed charges (DESIGN.md §9.4): the fuze settings and defuse screens, and the Remote Detonator's frequency.
        registrar.playToServer(dev.groundburst.content.alarm.AlarmPayloads.Settings.TYPE, dev.groundburst.content.alarm.AlarmPayloads.Settings.CODEC,
                dev.groundburst.content.alarm.AlarmPayloads.Settings::handle);
        registrar.playToServer(dev.groundburst.content.alarm.AlarmPayloads.Panel.TYPE, dev.groundburst.content.alarm.AlarmPayloads.Panel.CODEC,
                dev.groundburst.content.alarm.AlarmPayloads.Panel::handle);
        registrar.playToServer(dev.groundburst.content.ordnance.charge.ChargePayloads.Settings.TYPE,
                dev.groundburst.content.ordnance.charge.ChargePayloads.Settings.CODEC, dev.groundburst.content.ordnance.charge.ChargePayloads.Settings::handle);
        registrar.playToServer(dev.groundburst.content.assembly.AssemblyPayloads.FuzeSetting.TYPE,
                dev.groundburst.content.assembly.AssemblyPayloads.FuzeSetting.CODEC, dev.groundburst.content.assembly.AssemblyPayloads.FuzeSetting::handle);
        registrar.playToServer(dev.groundburst.content.ordnance.charge.ChargePayloads.CutWire.TYPE,
                dev.groundburst.content.ordnance.charge.ChargePayloads.CutWire.CODEC, dev.groundburst.content.ordnance.charge.ChargePayloads.CutWire::handle);
        // The Launch Computer (DESIGN.md §17.3): its scope is handled on the client in GroundBurstClient.
        registrar.playToServer(dev.groundburst.content.launch.LaunchPayloads.Watch.TYPE, dev.groundburst.content.launch.LaunchPayloads.Watch.CODEC,
                dev.groundburst.content.launch.LaunchPayloads.Watch::handle);
        registrar.playToServer(dev.groundburst.content.launch.LaunchPayloads.Target.TYPE, dev.groundburst.content.launch.LaunchPayloads.Target.CODEC,
                dev.groundburst.content.launch.LaunchPayloads.Target::handle);
        registrar.playToServer(dev.groundburst.content.launch.LaunchPayloads.Arm.TYPE, dev.groundburst.content.launch.LaunchPayloads.Arm.CODEC,
                dev.groundburst.content.launch.LaunchPayloads.Arm::handle);
        registrar.playToServer(dev.groundburst.content.launch.LaunchPayloads.Launch.TYPE, dev.groundburst.content.launch.LaunchPayloads.Launch.CODEC,
                dev.groundburst.content.launch.LaunchPayloads.Launch::handle);
        registrar.playToServer(dev.groundburst.content.launch.StationPayloads.Aim.TYPE, dev.groundburst.content.launch.StationPayloads.Aim.CODEC,
                dev.groundburst.content.launch.StationPayloads.Aim::handle);
        registrar.playToServer(dev.groundburst.content.launch.StationPayloads.Arm.TYPE, dev.groundburst.content.launch.StationPayloads.Arm.CODEC,
                dev.groundburst.content.launch.StationPayloads.Arm::handle);
        registrar.playToServer(dev.groundburst.content.launch.StationPayloads.Fire.TYPE, dev.groundburst.content.launch.StationPayloads.Fire.CODEC,
                dev.groundburst.content.launch.StationPayloads.Fire::handle);
        registrar.playToServer(dev.groundburst.content.launch.StationPayloads.Unlink.TYPE, dev.groundburst.content.launch.StationPayloads.Unlink.CODEC,
                dev.groundburst.content.launch.StationPayloads.Unlink::handle);
        registrar.playToServer(dev.groundburst.content.launch.StationPayloads.NewCodes.TYPE, dev.groundburst.content.launch.StationPayloads.NewCodes.CODEC,
                dev.groundburst.content.launch.StationPayloads.NewCodes::handle);
        registrar.playToClient(dev.groundburst.content.launch.DesignatorPayloads.Status.TYPE, dev.groundburst.content.launch.DesignatorPayloads.Status.CODEC,
                (payload, context) -> dev.groundburst.content.launch.DesignatorPayloads.statusHook.accept(payload));
        registrar.playToClient(dev.groundburst.content.launch.DesignatorPayloads.Beam.TYPE, dev.groundburst.content.launch.DesignatorPayloads.Beam.CODEC,
                (payload, context) -> dev.groundburst.content.launch.DesignatorPayloads.beamHook.accept(payload));
        registrar.playToClient(dev.groundburst.content.launch.StationPayloads.Scope.TYPE, dev.groundburst.content.launch.StationPayloads.Scope.CODEC,
                (payload, context) -> dev.groundburst.content.launch.StationPayloads.scopeHook.accept(payload));
        registrar.playToServer(dev.groundburst.content.launch.LaunchPayloads.MapRequest.TYPE,
                dev.groundburst.content.launch.LaunchPayloads.MapRequest.CODEC, dev.groundburst.content.launch.LaunchPayloads.MapRequest::handle);
        registrar.playToClient(dev.groundburst.content.launch.LaunchPayloads.MapTile.TYPE, dev.groundburst.content.launch.LaunchPayloads.MapTile.CODEC,
                (payload, context) -> dev.groundburst.content.launch.LaunchPayloads.tileHook.accept(payload));
        registrar.playToClient(dev.groundburst.content.launch.LaunchPayloads.Radar.TYPE, dev.groundburst.content.launch.LaunchPayloads.Radar.CODEC,
                (payload, context) -> dev.groundburst.content.launch.LaunchPayloads.radarHook.accept(payload));
        registrar.playToClient(dev.groundburst.content.effect.GasPayloads.Cells.TYPE, dev.groundburst.content.effect.GasPayloads.Cells.CODEC,
                (payload, context) -> dev.groundburst.content.effect.GasPayloads.cellsHook.accept(payload));
        registrar.playToClient(dev.groundburst.content.effect.GasPayloads.Emp.TYPE, dev.groundburst.content.effect.GasPayloads.Emp.CODEC,
                (payload, context) -> dev.groundburst.content.effect.GasPayloads.empHook.accept(payload));
        registrar.playToClient(dev.groundburst.content.launch.LaunchPayloads.Scope.TYPE, dev.groundburst.content.launch.LaunchPayloads.Scope.CODEC,
                (payload, context) -> dev.groundburst.content.launch.LaunchPayloads.scopeHook.accept(payload));
        // Radiation sources (DESIGN.md §21.8): the fallout drawn over them, handled on the client by FalloutFx.
        registrar.playToClient(dev.groundburst.content.radiation.RadiationSources.Sync.TYPE,
                dev.groundburst.content.radiation.RadiationSources.Sync.CODEC,
                (payload, context) -> dev.groundburst.content.radiation.RadiationSources.Sync.hook.accept(payload));
        registrar.playToClient(dev.groundburst.content.fallout.Wind.Sync.TYPE, dev.groundburst.content.fallout.Wind.Sync.CODEC,
                (payload, context) -> dev.groundburst.content.fallout.Wind.Sync.hook.accept(payload));
        registrar.playToClient(dev.groundburst.content.fallout.Plumes.Sync.TYPE, dev.groundburst.content.fallout.Plumes.Sync.CODEC,
                (payload, context) -> dev.groundburst.content.fallout.Plumes.Sync.hook.accept(payload));
        registrar.playToServer(dev.groundburst.content.ordnance.charge.ChargePayloads.DetonatorFrequency.TYPE,
                dev.groundburst.content.ordnance.charge.ChargePayloads.DetonatorFrequency.CODEC,
                dev.groundburst.content.ordnance.charge.ChargePayloads.DetonatorFrequency::handle);
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(GroundBurst.id(name));
    }
}
