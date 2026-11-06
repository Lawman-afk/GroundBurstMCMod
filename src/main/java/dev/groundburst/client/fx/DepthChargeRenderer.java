package dev.groundburst.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.naval.DepthCharge;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * A depth charge in the air or the sea (DESIGN.md §14.6): its drum, tumbling end over end as it goes; rolled off a rack,
 * lying on its side across the rack's front as it lay there and rolling on about its own axis.
 */
public class DepthChargeRenderer extends EntityRenderer<DepthCharge, DepthChargeRenderer.State> {
    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, spin;
        public boolean lying;
    }

    private final ItemModelResolver items;

    public DepthChargeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DepthCharge charge, State state, float partialTicks) {
        super.extractRenderState(charge, state, partialTicks);
        this.items.updateForNonLiving(state.item, charge.getItem(), ItemDisplayContext.NONE, charge);
        state.lying = charge.lying();
        state.yaw = state.lying ? charge.getYRot() : (charge.getId() * 47) % 360;
        state.spin = charge.prevSpin + (charge.spin - charge.prevSpin) * partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, DepthCharge.MIDDLE, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw));
        if (state.lying) {
            poseStack.rotate(Axis.XP.rotationDegrees(90));         // its axis (the drum stands on it) laid along its yaw
            poseStack.rotate(Axis.YP.rotationDegrees(state.spin)); // rolling forward about it
        } else {
            poseStack.rotate(Axis.XP.rotationDegrees(state.spin));
        }
        // (at its item's size, as the rack's drums: a charge rolled off one goes as it lay there)
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
