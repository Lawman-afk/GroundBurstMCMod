package dev.groundburst.port.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;

/**
 * Minecraft 26.x's {@code SubmitNodeCollector} (what a renderer hands its geometry to) over 1.21.1's buffers: what is
 * submitted is drawn into them at once (docs/BACKPORT.md).
 */
public final class SubmitNodeCollector {
    /** Draws custom geometry into a buffer, in a pose. */
    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }

    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    /** 1.21.1's buffers underneath. */
    public MultiBufferSource buffers() {
        return this.buffers;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType type, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), this.buffers.getBuffer(type));
    }

    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow, Font.DisplayMode mode,
            int lightCoords, int color, int backgroundColor, int outlineColor) {
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, dropShadow, poseStack.last().pose(), this.buffers, mode, backgroundColor,
                lightCoords);
    }
}
