package dev.groundburst.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * A Minecraft 26.x entity renderer (it takes the entity's state for the frame, then submits what it draws) as a 1.21.1
 * one: each frame its state is taken and submitted at once, into the buffers 1.21.1 hands it, the pose stack already at
 * the entity as 26.x has it.
 */
public abstract class StateEntityRenderer<T extends Entity, S extends EntityRenderState> extends EntityRenderer<T> {
    protected StateEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public abstract S createRenderState();

    public void extractRenderState(T entity, S state, float partialTick) {
        state.x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        state.y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        state.z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        state.ageInTicks = entity.tickCount + partialTick;
        state.boundingBoxWidth = entity.getBbWidth();
        state.boundingBoxHeight = entity.getBbHeight();
        state.isInvisible = entity.isInvisible();
        state.partialTick = partialTick;
    }

    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {}

    /** Whether it is left out when its box is off screen (26.x's name). */
    protected boolean affectedByCulling(T entity) {
        return true;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        return !affectedByCulling(entity) || super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        S state = createRenderState();
        extractRenderState(entity, state, partialTick);
        state.lightCoords = light;
        submit(state, poseStack, new SubmitNodeCollector(buffers), CameraRenderState.current());
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
