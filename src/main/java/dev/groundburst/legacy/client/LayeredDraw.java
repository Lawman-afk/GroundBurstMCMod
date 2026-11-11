package dev.groundburst.legacy.client;

import dev.groundburst.legacy.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Minecraft 1.21's HUD layer (drawn on 1.20.1 as a Forge overlay: {@link RegisterGuiLayersEvent}). */
public final class LayeredDraw {
    private LayeredDraw() {}

    @FunctionalInterface
    public interface Layer {
        void render(GuiGraphics graphics, DeltaTracker delta);
    }
}
