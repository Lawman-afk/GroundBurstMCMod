package dev.groundburst.port.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.eventbus.api.Event;

/** NeoForge 26.x's event for taking a frame's state of the level; posted by {@link RenderBridge} before the frame's geometry. */
public class ExtractLevelRenderStateEvent extends Event {
    private final ClientLevel level;
    private final LevelRenderState renderState;

    public ExtractLevelRenderStateEvent(ClientLevel level, LevelRenderState renderState) {
        this.level = level;
        this.renderState = renderState;
    }

    public ClientLevel getLevel() {
        return this.level;
    }

    public LevelRenderState getRenderState() {
        return this.renderState;
    }
}
