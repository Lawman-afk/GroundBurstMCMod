package dev.groundburst.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.defence.GBDefence;
import dev.groundburst.content.defence.InterceptorEntity;
import dev.groundburst.port.render.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import dev.groundburst.port.render.EntityRenderState;
import dev.groundburst.port.render.ItemModelResolver;
import dev.groundburst.port.render.ItemStackRenderState;
import dev.groundburst.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * An interceptor in the air (DESIGN.md §18.2): the Interceptor Missile's 3D item model, twice over, its tail at the
 * entity, pointed along its flight. Lit full bright (its motor glares off it).
 */
public class InterceptorRenderer extends dev.groundburst.port.render.StateEntityRenderer<InterceptorEntity, InterceptorRenderer.State> {
    private static final float SCALE = 2.5f;
    private static final int FULL_BRIGHT = 15728880;

    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, tilt;
    }

    private final ItemModelResolver items;
    private ItemStack look = ItemStack.EMPTY;

    public InterceptorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = dev.groundburst.port.render.ItemModelResolver.INSTANCE;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(InterceptorEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (this.look.isEmpty()) {
            this.look = new ItemStack(GBDefence.INTERCEPTOR_MISSILE.get());   // (made on first use: items are bound late)
        }
        this.items.updateForNonLiving(state.item, this.look, ItemDisplayContext.NONE, entity);
        state.yaw = entity.getViewYRot(partialTicks);
        state.tilt = entity.tilt(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.tilt));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0, 0.5, 0);                       // the item model is drawn about its middle: its tail at the entity
        state.item.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected boolean affectedByCulling(InterceptorEntity entity) {
        return false;
    }
}
