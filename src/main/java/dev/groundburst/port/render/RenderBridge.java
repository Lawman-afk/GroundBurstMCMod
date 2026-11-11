package dev.groundburst.port.render;

import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Each frame, after the translucent terrain and the particles (where 26.x draws its custom geometry after terrain):
 * takes the frame's {@link LevelRenderState} ({@link ExtractLevelRenderStateEvent}), then has the mod's geometry drawn
 * ({@link SubmitCustomGeometryEvent}) into the game's buffers, and draws them.
 */
public final class RenderBridge {
    private RenderBridge() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(RenderBridge::stage);
    }

    private static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Camera camera = event.getCamera();
        float partial = event.getPartialTick();
        LevelRenderState state = new LevelRenderState(CameraRenderState.of(camera));
        Vec3 sky = level.getSkyColor(camera.getPosition(), partial);
        state.skyRenderState.skyColor = new Vector3f((float) sky.x, (float) sky.y, (float) sky.z);
        state.skyRenderState.sunAngle = level.getSunAngle(partial);
        float[] glow = level.effects().getSunriseColor(level.getTimeOfDay(partial), partial);
        state.skyRenderState.sunriseAndSunsetColor = glow == null ? null : new Vector4f(glow[0], glow[1], glow[2], glow[3]);
        Vec3 cloud = level.getCloudColor(partial);
        state.cloudColor = 0xFF000000 | (int) (cloud.x * 255) << 16 | (int) (cloud.y * 255) << 8 | (int) (cloud.z * 255);
        MinecraftForge.EVENT_BUS.post(new ExtractLevelRenderStateEvent(level, state));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        MinecraftForge.EVENT_BUS.post(new SubmitCustomGeometryEvent(poseStack, new SubmitNodeCollector(buffers), state));
        buffers.endBatch();
    }
}
