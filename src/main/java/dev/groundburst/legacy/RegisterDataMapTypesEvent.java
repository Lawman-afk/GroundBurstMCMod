package dev.groundburst.legacy;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** NeoForge's data map registration: posted on the mod bus as the mod is made ({@link Lifecycle}). */
public final class RegisterDataMapTypesEvent extends Event implements IModBusEvent {
    public RegisterDataMapTypesEvent() {}

    public void register(DataMapType<?, ?> type) {
        DataMaps.add(type);
    }
}
