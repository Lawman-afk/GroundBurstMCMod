package dev.groundzero.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundzero.content.launch.GZLaunch;
import dev.groundzero.content.launch.MissileEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A missile in flight: the Basic Missile's 3D model (2 blocks tall as an item) drawn three times over, its tail at the
 * entity's position, pointed along its flight: upright on the pad and climbing, tipping over the top of its arc, nose
 * down at the end. Lit full bright (the exhaust glares off it).
 */
public class MissileRenderer extends EntityRenderer<MissileEntity, MissileRenderer.State> {
    /** The item model is 2 blocks tall; the missile is 6. */
    private static final float SCALE = 4;
    private static final int FULL_BRIGHT = 15728880;

    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, tilt;
    }

    private final ItemModelResolver items;
    /** Made on first use: item components are not bound yet when entity renderers are built. */
    private ItemStack look = ItemStack.EMPTY;

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MissileEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (this.look.isEmpty()) {
            this.look = new ItemStack(GZLaunch.BASIC_MISSILE.get());
        }
        this.items.updateForNonLiving(state.item, this.look, ItemDisplayContext.NONE, entity);
        state.yaw = entity.getYRot(partialTicks);
        state.tilt = entity.tilt(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw));
        poseStack.rotate(Axis.XP.rotationDegrees(state.tilt));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0, 0.5, 0);                 // the item model is drawn about its middle; stand it on its tail
        state.item.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected boolean affectedByCulling(MissileEntity entity) {
        return false;
    }
}
