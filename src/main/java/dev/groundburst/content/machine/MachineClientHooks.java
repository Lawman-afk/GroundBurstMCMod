package dev.groundburst.content.machine;

import java.util.function.Consumer;

/**
 * Client-side behaviour that common code triggers without referencing client classes (installed by
 * {@code GroundBurstClient}; a no-op on dedicated servers).
 */
public final class MachineClientHooks {
    /** Called every client tick for each loaded machine (work-loop sounds, particles). */
    public static Consumer<MachineBlockEntity> clientTick = machine -> {};

    private MachineClientHooks() {}
}
