package dev.groundburst.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraftforge.eventbus.api.Event;

/**
 * NeoForge 26.x's event for drawing custom geometry into the world; posted by {@link RenderBridge} after the translucent
 * terrain and the particles, with a pose stack at the camera's rotation only (the geometry is placed relative to the
 * camera, as the mod's code places it).
 */
public class SubmitCustomGeometryEvent extends Event {
    private final PoseStack poseStack;
    private final SubmitNodeCollector collector;
    private final LevelRenderState levelRenderState;

    public SubmitCustomGeometryEvent(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelRenderState) {
        this.poseStack = poseStack;
        this.collector = collector;
        this.levelRenderState = levelRenderState;
    }

    public PoseStack getPoseStack() {
        return this.poseStack;
    }

    public SubmitNodeCollector getSubmitNodeCollector() {
        return this.collector;
    }

    public LevelRenderState getLevelRenderState() {
        return this.levelRenderState;
    }
}
