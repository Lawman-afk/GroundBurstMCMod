package dev.groundzero.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundzero.content.ordnance.ThrownExplosive;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a thrown explosive as its 3D item model, turned to its direction of flight and tumbling end over end at a
 * rate set by its speed (vanilla's thrown-item renderer would turn it to face the camera, like a snowball sprite).
 * Stuck or at rest it lies still.
 */
public class GrenadeRenderer extends EntityRenderer<ThrownExplosive, GrenadeRenderer.State> {
    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, spin;
    }

    private final ItemModelResolver items;

    public GrenadeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ThrownExplosive entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        this.items.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.GROUND, entity);
        Vec3 v = entity.getDeltaMovement();
        if (v.horizontalDistanceSqr() > 1e-4) {
            state.yaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        } else {
            state.yaw = (entity.getId() * 47) % 360;      // at rest: a fixed heading of its own
        }
        state.spin = entity.prevSpin + (entity.spin - entity.prevSpin) * partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.15, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw));
        poseStack.rotate(Axis.XP.rotationDegrees(state.spin));
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
