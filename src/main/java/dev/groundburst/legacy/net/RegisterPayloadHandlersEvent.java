package dev.groundburst.legacy.net;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** NeoForge's payload registration event: posted on the mod bus as the mod is made ({@link dev.groundburst.legacy.Lifecycle}). */
public final class RegisterPayloadHandlersEvent extends Event implements IModBusEvent {
    public RegisterPayloadHandlersEvent() {}

    public PayloadRegistrar registrar(String version) {
        Network.version(version);
        return new PayloadRegistrar(version, false);
    }
}
