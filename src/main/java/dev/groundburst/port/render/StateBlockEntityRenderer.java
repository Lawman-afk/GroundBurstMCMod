package dev.groundburst.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A Minecraft 26.x block entity renderer (it takes the block entity's state for the frame, then submits what it draws)
 * as a 1.21.1 one: each frame its state is taken and submitted at once, into the buffers 1.21.1 hands it.
 */
public abstract class StateBlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> implements BlockEntityRenderer<T> {
    public abstract S createRenderState();

    public void extractRenderState(T blockEntity, S state, float partialTick, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        state.blockPos = blockEntity.getBlockPos();
        state.blockState = blockEntity.getBlockState();
        state.blockEntityType = blockEntity.getType();
        state.breakProgress = breakProgress;
    }

    public abstract void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);

    /** Whether it is drawn when its block is off screen (26.x asks the renderer, 1.21.1 the renderer with the block entity). */
    public boolean shouldRenderOffScreen() {
        return false;
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return shouldRenderOffScreen();
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        S state = createRenderState();
        CameraRenderState camera = CameraRenderState.current();
        extractRenderState(blockEntity, state, partialTick, camera.pos, null);
        state.lightCoords = light;
        submit(state, poseStack, new SubmitNodeCollector(buffers), camera);
    }
}
