package dev.groundburst.port.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Minecraft 26.x's quad instance (the colour, light and overlay a baked quad is put down with), over 1.21.1's bulk data. */
public final class QuadInstance {
    private int color = 0xFFFFFFFF;
    private int light = LightTexture.FULL_BRIGHT;
    private int overlay = OverlayTexture.NO_OVERLAY;

    public void setColor(int argb) {
        this.color = argb;
    }

    public void setLightCoords(int light) {
        this.light = light;
    }

    public void setOverlayCoords(int overlay) {
        this.overlay = overlay;
    }

    /** {@code buffer.putBakedQuad(pose, quad, instance)} in 26.x. */
    public static void put(VertexConsumer buffer, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        int c = instance.color;
        buffer.putBulkData(pose, quad, new float[] {1, 1, 1, 1}, (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f,
                (c >>> 24) / 255f, new int[] {instance.light, instance.light, instance.light, instance.light}, instance.overlay, false);
    }
}
