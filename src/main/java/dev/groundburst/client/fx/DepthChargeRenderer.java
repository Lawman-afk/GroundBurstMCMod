package dev.groundburst.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.naval.DepthCharge;
import dev.groundburst.port.render.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import dev.groundburst.port.render.EntityRenderState;
import dev.groundburst.port.render.ItemModelResolver;
import dev.groundburst.port.render.ItemStackRenderState;
import dev.groundburst.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** A depth charge in the air or the sea (DESIGN.md §14.6): its drum, tumbling end over end as it goes. */
public class DepthChargeRenderer extends dev.groundburst.port.render.StateEntityRenderer<DepthCharge, DepthChargeRenderer.State> {
    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, spin;
    }

    private final ItemModelResolver items;

    public DepthChargeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = dev.groundburst.port.render.ItemModelResolver.INSTANCE;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DepthCharge charge, State state, float partialTicks) {
        super.extractRenderState(charge, state, partialTicks);
        this.items.updateForNonLiving(state.item, charge.getItem(), ItemDisplayContext.NONE, charge);
        state.yaw = (charge.getId() * 47) % 360;
        state.spin = charge.prevSpin + (charge.spin - charge.prevSpin) * partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, DepthCharge.MIDDLE, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.spin));
        poseStack.scale(0.75f, 0.75f, 0.75f);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
