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

    /** A shape's edges as lines, in an ARGB colour (1.21.1's line width is the render system's). */
    public void submitShapeOutline(PoseStack poseStack, net.minecraft.world.phys.shapes.VoxelShape shape, RenderType type, int color,
            float lineWidth, boolean onlyVisible) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = this.buffers.getBuffer(type);
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
            float dx = (float) (x1 - x0), dy = (float) (y1 - y0), dz = (float) (z1 - z0);
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-6f) {
                return;
            }
            dx /= len;
            dy /= len;
            dz /= len;
            buffer.vertex(dev.groundburst.legacy.Verts.matrix(pose), (float) x0, (float) y0, (float) z0).color(color).normal(dev.groundburst.legacy.Verts.normal(pose), dx, dy, dz).endVertex();
            buffer.vertex(dev.groundburst.legacy.Verts.matrix(pose), (float) x1, (float) y1, (float) z1).color(color).normal(dev.groundburst.legacy.Verts.normal(pose), dx, dy, dz).endVertex();
        });
    }

    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow, Font.DisplayMode mode,
            int lightCoords, int color, int backgroundColor, int outlineColor) {
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, dropShadow, poseStack.last().pose(), this.buffers, mode, backgroundColor,
                lightCoords);
    }
}
