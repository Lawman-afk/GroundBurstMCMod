package dev.groundburst.legacy.client;

import dev.groundburst.legacy.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

/**
 * NeoForge's news that a HUD layer is about to be drawn (Pre, which may stop it) or was drawn (Post): posted on the game
 * bus from Forge 1.20.1's overlay events ({@link ClientLifecycle}).
 */
public abstract class RenderGuiLayerEvent extends Event {
    private final GuiGraphics graphics;
    private final DeltaTracker delta;
    private final ResourceLocation name;

    RenderGuiLayerEvent(GuiGraphics graphics, float partialTick, ResourceLocation name) {
        this.graphics = graphics;
        this.delta = DeltaTracker.of(partialTick);
        this.name = name;
    }

    public GuiGraphics getGuiGraphics() {
        return this.graphics;
    }

    public DeltaTracker getPartialTick() {
        return this.delta;
    }

    public ResourceLocation getName() {
        return this.name;
    }

    public static final class Pre extends RenderGuiLayerEvent {
        Pre(GuiGraphics graphics, float partialTick, ResourceLocation name) {
            super(graphics, partialTick, name);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    public static final class Post extends RenderGuiLayerEvent {
        Post(GuiGraphics graphics, float partialTick, ResourceLocation name) {
            super(graphics, partialTick, name);
        }
    }
}
