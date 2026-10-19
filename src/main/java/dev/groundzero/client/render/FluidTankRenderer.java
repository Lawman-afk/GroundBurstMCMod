package dev.groundzero.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundzero.content.fluid.FluidTankBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a fluid tank's contents: one translucent box filling the whole (possibly multi-block) tank inside its glass,
 * up to the fill level. Liquids fill from the bottom; gases, being lighter than air, collect from the top down.
 */
public class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderer.State> {
    /** Inside the 1 px recessed glass, clear of the frame. */
    private static final float INSET = 1.2f / 16f;

    public static class State extends BlockEntityRenderState {
        @Nullable Fluid fluid;
        float fill;
        int width, height, color;
        boolean gas;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.fluid = null;
        if (!be.isController() || be.getLevel() == null) {
            return;
        }
        var f = be.tank().fluid();
        if (f.isEmpty()) {
            return;
        }
        state.fluid = f.getFluid();
        state.fill = Math.min(1f, f.getAmount() / (float) be.tank().capacity());
        state.width = be.width();
        state.height = be.height();
        state.gas = f.getFluidType().isLighterThanAir();
        int tint = FluidVisuals.tintInWorld(f.getFluid(), be.getLevel(), be.getBlockPos());
        state.color = (state.gas ? 0xA0000000 : 0xD8000000) | (tint & 0xFFFFFF);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fluid == null) {
            return;
        }
        TextureAtlasSprite sprite = FluidVisuals.still(state.fluid);
        float x0 = INSET, z0 = INSET, x1 = state.width - INSET, z1 = state.width - INSET;
        float inner = state.height - 2 * INSET;
        float y0, y1;
        if (state.gas) {
            y1 = state.height - INSET;
            y0 = y1 - inner * state.fill;
        } else {
            y0 = INSET;
            y1 = y0 + inner * state.fill;
        }
        int c = state.color, light = state.lightCoords;
        float fy0 = y0, fy1 = y1;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            // Top and bottom surfaces, tiled one block at a time.
            for (float xa = x0; xa < x1 - 1e-4f; xa = Math.min(x1, (float) Math.floor(xa) + 1f)) {
                float xb = Math.min(x1, (float) Math.floor(xa) + 1f);
                for (float za = z0; za < z1 - 1e-4f; za = Math.min(z1, (float) Math.floor(za) + 1f)) {
                    float zb = Math.min(z1, (float) Math.floor(za) + 1f);
                    float ua = u0 + (u1 - u0) * (xa - (float) Math.floor(xa)), ub = u0 + (u1 - u0) * (xb - (float) Math.floor(xa));
                    float va = v0 + (v1 - v0) * (za - (float) Math.floor(za)), vb = v0 + (v1 - v0) * (zb - (float) Math.floor(za));
                    quad(buffer, pose, c, light, 0, 1, 0, xa, fy1, za, xa, fy1, zb, xb, fy1, zb, xb, fy1, za, ua, va, ub, vb);
                    quad(buffer, pose, c, light, 0, -1, 0, xa, fy0, zb, xa, fy0, za, xb, fy0, za, xb, fy0, zb, ua, va, ub, vb);
                }
            }
            // Four sides, tiled one block at a time along both axes.
            for (float ya = fy0; ya < fy1 - 1e-4f; ya = Math.min(fy1, (float) Math.floor(ya) + 1f)) {
                float yb = Math.min(fy1, (float) Math.floor(ya) + 1f);
                float va = v0 + (v1 - v0) * (1 - (yb - (float) Math.floor(ya))), vb = v0 + (v1 - v0) * (1 - (ya - (float) Math.floor(ya)));
                for (float a = x0; a < x1 - 1e-4f; a = Math.min(x1, (float) Math.floor(a) + 1f)) {
                    float b = Math.min(x1, (float) Math.floor(a) + 1f);
                    float ua = u0 + (u1 - u0) * (a - (float) Math.floor(a)), ub = u0 + (u1 - u0) * (b - (float) Math.floor(a));
                    quad(buffer, pose, c, light, 0, 0, -1, b, yb, z0, b, ya, z0, a, ya, z0, a, yb, z0, ua, va, ub, vb);
                    quad(buffer, pose, c, light, 0, 0, 1, a, yb, z1, a, ya, z1, b, ya, z1, b, yb, z1, ua, va, ub, vb);
                    quad(buffer, pose, c, light, -1, 0, 0, x0, yb, a, x0, ya, a, x0, ya, b, x0, yb, b, ua, va, ub, vb);
                    quad(buffer, pose, c, light, 1, 0, 0, x1, yb, b, x1, ya, b, x1, ya, a, x1, yb, a, ua, va, ub, vb);
                }
            }
        });
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int color, int light, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        buffer.addVertex(pose, ax, ay, az).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, bx, by, bz).setColor(color).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, cx, cy, cz).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, dx, dy, dz).setColor(color).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(FluidTankBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(be.width() - 1, be.height() - 1, be.width() - 1);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
