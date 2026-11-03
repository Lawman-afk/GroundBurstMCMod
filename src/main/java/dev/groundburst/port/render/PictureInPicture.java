package dev.groundburst.port.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * A 3D view in a screen (26.x's picture-in-picture render state), drawn straight into 1.21.1's screen: clipped to its
 * area, centred in it at {@code scale} pixels a block (z toward the viewer flipped, as 1.21.1 draws an entity in an
 * inventory), and its depth cleared after so the screen's own drawing stays on top of it.
 */
public interface PictureInPicture {
    int x0();

    int y0();

    int x1();

    int y1();

    float scale();

    /** Draws the view about the area's middle (the pose already centred and scaled). */
    void drawView(PoseStack pose, MultiBufferSource.BufferSource buffers);

    default void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        g.flush();
        g.enableScissor(x0(), y0(), x1(), y1());
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((x0() + x1()) / 2.0F, (y0() + y1()) / 2.0F, 400.0F);
        pose.scale(scale(), scale(), -scale());
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawView(pose, buffers);
        buffers.endBatch();
        pose.popPose();
        // Clear the view's depth (inside the clip only) so what the screen draws after it is not hidden behind it.
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.disableScissor();
    }
}
