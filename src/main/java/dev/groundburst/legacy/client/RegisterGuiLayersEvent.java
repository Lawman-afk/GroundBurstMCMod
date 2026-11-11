package dev.groundburst.legacy.client;

import dev.groundburst.legacy.DeltaTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** NeoForge's HUD layer registration, posted on the mod bus as Forge 1.20.1 registers its overlays ({@link ClientLifecycle}). */
public final class RegisterGuiLayersEvent extends Event implements IModBusEvent {
    private final RegisterGuiOverlaysEvent forge;

    RegisterGuiLayersEvent(RegisterGuiOverlaysEvent forge) {
        this.forge = forge;
    }

    private static IGuiOverlay overlay(LayeredDraw.Layer layer) {
        return (gui, graphics, partialTick, width, height) -> layer.render(graphics, DeltaTracker.of(partialTick));
    }

    public void registerAbove(ResourceLocation other, ResourceLocation id, LayeredDraw.Layer layer) {
        this.forge.registerAbove(other, id.getPath(), overlay(layer));
    }

    public void registerBelow(ResourceLocation other, ResourceLocation id, LayeredDraw.Layer layer) {
        this.forge.registerBelow(other, id.getPath(), overlay(layer));
    }

    public void registerAboveAll(ResourceLocation id, LayeredDraw.Layer layer) {
        this.forge.registerAboveAll(id.getPath(), overlay(layer));
    }

    public void registerBelowAll(ResourceLocation id, LayeredDraw.Layer layer) {
        this.forge.registerBelowAll(id.getPath(), overlay(layer));
    }
}
