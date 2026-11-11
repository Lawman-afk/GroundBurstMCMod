package dev.groundburst.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.MissileEntity;
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
 * A missile in flight: the Basic Missile's 3D model (2 blocks tall as an item) drawn three times over, its tail at the
 * entity's position, pointed along its flight: upright on the pad and climbing, tipping over the top of its arc, nose
 * down at the end. Lit full bright (the exhaust glares off it).
 */
public class MissileRenderer extends dev.groundburst.port.render.StateEntityRenderer<MissileEntity, MissileRenderer.State> {
    /** The item model is 2 blocks tall; the missile is 6. */
    private static final float SCALE = 4;
    private static final int FULL_BRIGHT = 15728880;

    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, tilt;
        /** How far (blocks, before scaling) to lift the item model to stand it on its tail. */
        public float lift = 0.5f;
    }

    private final ItemModelResolver items;
    /**
     * The missile as an item, one per warhead (its id as custom model data picks the nose's model). Made on first use:
     * item components are not bound yet when entity renderers are built.
     */
    private final java.util.Map<String, ItemStack> looks = new java.util.HashMap<>();

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = dev.groundburst.port.render.ItemModelResolver.INSTANCE;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MissileEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // What is left of it, drawn by its own model (the one on its launcher, its spent stages gone).
        String id = entity.folded() ? dev.groundburst.content.assembly.MissileParts.folded(entity.look())
                : dev.groundburst.content.assembly.MissileParts.flight(entity.look(), entity.dropped());
        ItemStack look = this.looks.computeIfAbsent(id, key -> {
            ItemStack s = new ItemStack(dev.groundburst.content.assembly.GBAssembly.MISSILE.get());
            dev.groundburst.legacy.component.Comps.set(s, dev.groundburst.legacy.component.DataComponents.CUSTOM_MODEL_DATA,
                    new dev.groundburst.legacy.component.CustomModelData(dev.groundburst.port.Looks.id(key)));
            return s;
        });
        var shape = dev.groundburst.client.render.MissileShapes.get(entity.look());
        state.lift = shape == null ? 0.5f : shape.lift();          // (the plain missile with no warhead: its tail at its y 0)
        this.items.updateForNonLiving(state.item, look, ItemDisplayContext.NONE, entity);
        state.yaw = entity.getViewYRot(partialTicks);
        state.tilt = entity.tilt(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.tilt));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0, state.lift, 0);          // the item model is drawn about its middle; stand it on its tail
        state.item.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected boolean affectedByCulling(MissileEntity entity) {
        return false;
    }
}
