package dev.groundzero.content.multiblock;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

/**
 * Client-side multiblock behaviour that common code triggers without referencing client classes (installed by
 * {@code GroundZeroClient}; a no-op on dedicated servers).
 */
public final class MultiblockClientHooks {
    /** Shows or hides the ghost preview of the controller at a position. */
    public static Consumer<BlockPos> togglePreview = pos -> {};
    /** Steps the Holo-Projector to the next layer (all layers, then each layer bottom-up). */
    public static Runnable stepLayer = () -> {};

    private MultiblockClientHooks() {}
}
