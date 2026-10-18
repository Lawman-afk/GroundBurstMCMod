package dev.groundzero.datagen;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Data generation entry point ({@code runData}). Output goes to {@code src/generated/resources}.
 * Hand-made models and textures come from {@code tools/}, not from here.
 */
public final class GZDataGen {
    private GZDataGen() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GZDataGen::gather);
    }

    private static void gather(GatherDataEvent.Client event) {
        event.createProvider(GZLanguageProvider::new);
    }
}
