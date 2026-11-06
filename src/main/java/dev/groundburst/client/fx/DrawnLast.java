package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4fStack;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.groundburst.port.render.SubmitNodeCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The blast effects drawn last (main's DESIGN.md §12.3), the 1.21.1 backport's: under a shader pack (Iris), which would
 * draw them with its own programs, and with Distant Horizons, which lays its far terrain over the level after them.
 * They are kept out of the level and drawn at NeoForge's after-level stage, which 1.21.1 fires from the game renderer
 * once the level's drawing has returned, so after Iris 1.8's last passes (run as the level's drawing returns) and before
 * the depth is cleared for the hand: into the finished picture, with the game's own shaders (Iris swaps none in once the
 * level is done), depth tested against what was drawn (1.21.1's depth runs near 0, far 1, and Iris keeps it so). The
 * view is the stage's (Iris has put the view's bobbing in it), the fog the level's, as its particles had it.
 */
final class DrawnLast {
    private record Pending(PoseStack.Pose pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static float fogStart, fogEnd;
    private static final float[] FOG_COLOR = new float[4];
    private static FogShape fogShape = FogShape.SPHERE;
    private static boolean fogTaken;

    private DrawnLast() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(DrawnLast::stage);
    }

    static void submit(PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        PENDING.add(new Pending(poseStack.last().copy(), type, renderer));
    }

    private static void stage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            fogStart = RenderSystem.getShaderFogStart();
            fogEnd = RenderSystem.getShaderFogEnd();
            System.arraycopy(RenderSystem.getShaderFogColor(), 0, FOG_COLOR, 0, 4);
            fogShape = RenderSystem.getShaderFogShape();
            fogTaken = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && !PENDING.isEmpty()) {
            draw(event);
        }
    }

    private static void draw(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        float start = RenderSystem.getShaderFogStart(), end = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderFogColor().clone();
        FogShape shape = RenderSystem.getShaderFogShape();
        if (fogTaken) {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogColor(FOG_COLOR[0], FOG_COLOR[1], FOG_COLOR[2], FOG_COLOR[3]);
            RenderSystem.setShaderFogShape(fogShape);
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        mc.getMainRenderTarget().bindWrite(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            for (Pending p : PENDING) {
                p.renderer().render(p.pose(), buffers.getBuffer(p.type()));
            }
            buffers.endBatch();
        } finally {
            PENDING.clear();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderFogStart(start);
            RenderSystem.setShaderFogEnd(end);
            RenderSystem.setShaderFogColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShaderFogShape(shape);
            fogTaken = false;
        }
    }
}
