package dev.groundburst.client.fx;

import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The blast effects drawn last (DESIGN.md §12.3), when something else would draw over them or mangle them in the level:
 * a shader pack (Iris), which draws the level with its own programs, or Distant Horizons, which lays its far terrain over
 * the level's picture after the effects are drawn. They are kept out of the level as it is drawn and drawn at NeoForge's
 * after-level stage, into the finished picture, with the mod's own shaders, depth tested against what was drawn. That
 * stage comes after Iris's last pass and Distant Horizons' fade and before the depth is cleared for the hand; Iris swaps
 * in none of a pack's programs there and lays out no vertex differently, so the effects come out as they do without
 * either, in any pack.
 * <p>
 * The projection is still the level's then, and the depth left behind runs as vanilla's does (reversed: near 1, far 0;
 * Iris 1.11 keeps it so and only turns it round inside the pack's shaders), so the effects' own pipelines and depth test
 * serve as they are. The view is the stage's (Iris has put the view's bobbing in it), the fog the level's main pass's.
 */
final class DrawnLast {
    private static final SubmitNodeStorage STORAGE = new SubmitNodeStorage();
    private static @Nullable FeatureRenderDispatcher dispatcher;
    private static @Nullable GpuBufferSlice levelFog;
    private static boolean pending;

    private DrawnLast() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentParticles event) -> levelFog = RenderSystem.getShaderFog());
        NeoForge.EVENT_BUS.addListener(DrawnLast::draw);
    }

    static void submit(PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        STORAGE.order(0).afterTerrain.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), type, renderer));
        pending = true;
    }

    private static void draw(RenderLevelStageEvent.AfterLevel event) {
        if (!pending) {
            return;
        }
        pending = false;
        Minecraft mc = Minecraft.getInstance();
        if (dispatcher == null) {
            dispatcher = new FeatureRenderDispatcher(mc.gameRenderer.renderBuffers(), mc.getModelManager(), mc.getAtlasManager(), mc.font,
                    mc.gameRenderer.gameRenderState());
        }
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        if (levelFog != null) {
            RenderSystem.setShaderFog(levelFog);
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(STORAGE)) {
            if (!frame.isEmpty()) {
                RenderTarget main = mc.gameRenderer.mainRenderTarget();
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Ground Burst effects drawn last",
                        main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                }
            }
        } finally {
            modelView.popMatrix();
            if (fog != null) {
                RenderSystem.setShaderFog(fog);
            }
            levelFog = null;
        }
    }
}
