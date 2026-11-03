package dev.groundburst.client.screen;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.port.render.PictureInPicture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A 3D item drawn large into a GUI area (the Assembly Bench's preview, DESIGN.md §10): its model as it is in the world
 * (no GUI pose), stretched by {@code size} (across, along its height), turned by {@code yaw} and tipped toward the
 * viewer by {@code pitch} about {@code centre} (a point of the model, in blocks), which sits in the middle of the area.
 */
public final class ItemPreview {
    private ItemPreview() {}

    public record State(ItemStack stack, float[] size, float[] centre, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
            @Nullable ScreenRectangle scissorArea) implements PictureInPicture {
        @Override
        public void drawView(PoseStack pose, MultiBufferSource.BufferSource buffers) {
            Minecraft mc = Minecraft.getInstance();
            // Screen y runs down; turn it the right way up, then tip it toward the viewer and spin it.
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.mulPose(Axis.XP.rotationDegrees(-this.pitch));
            pose.mulPose(Axis.YP.rotationDegrees(this.yaw));
            pose.scale(this.size[0], this.size[1], this.size[0]);
            pose.translate(-this.centre[0], -this.centre[1], -this.centre[2]);
            mc.getItemRenderer().renderStatic(this.stack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                    buffers, mc.level, 0);
        }
    }

    /**
     * Pixels per block that fit a model (its bounds {@code box}, stretched by {@code size}) into a {@code w} x {@code h}
     * area at any turn and any tilt from {@code minPitch} to {@code maxPitch} degrees, so the zoom holds still while it
     * turns or is dragged.
     */
    public static float fit(net.minecraft.world.phys.AABB box, float[] size, int w, int h, float minPitch, float maxPitch) {
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        double r = 0;
        for (double x : new double[] {box.minX, box.maxX}) {
            for (double z : new double[] {box.minZ, box.maxZ}) {
                r = Math.max(r, Math.hypot(x - cx, z - cz));
            }
        }
        r = Math.max(0.05, r * size[0]);                       // how far it reaches from its axis as it turns
        double half = Math.max(0.05, (box.maxY - box.minY) / 2 * size[1]);
        double tall = 0;
        for (float p = minPitch; p <= maxPitch + 0.01f; p += 2.5f) {
            double a = Math.toRadians(p);
            tall = Math.max(tall, 2 * half * Math.cos(a) + 2 * r * Math.abs(Math.sin(a)));
        }
        return (float) Math.min(w / (2 * r), h / tall);
    }
}
