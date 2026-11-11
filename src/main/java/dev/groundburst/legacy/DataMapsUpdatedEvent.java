package dev.groundburst.legacy;

import net.minecraftforge.eventbus.api.Event;

/** NeoForge's news that the data maps were read again (posted on the game bus by {@link DataMaps}). */
public final class DataMapsUpdatedEvent extends Event {
    private final boolean client;

    public DataMapsUpdatedEvent(boolean client) {
        this.client = client;
    }

    public boolean isClient() {
        return this.client;
    }
}
