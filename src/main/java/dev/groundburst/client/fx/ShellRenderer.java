package dev.groundburst.client.fx;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.artillery.ArtilleryShell;
import dev.groundburst.content.artillery.GBArtillery;
import dev.groundburst.content.artillery.ShellType;
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
 * An artillery shell in flight (DESIGN.md §20.1): its item's 3D model (built standing on its tail fins, nose up),
 * pointed along its flight about its middle.
 */
public class ShellRenderer extends EntityRenderer<ArtilleryShell, ShellRenderer.State> {
    private static final float SCALE = 0.8f;

    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, pitch;
    }

    private final ItemModelResolver items;
    private final Map<ShellType, ItemStack> looks = new EnumMap<>(ShellType.class);

    public ShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArtilleryShell entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ItemStack look = this.looks.computeIfAbsent(entity.type(), t -> new ItemStack(GBArtillery.MORTAR_SHELLS.get(t).get()));
        this.items.updateForNonLiving(state.item, look, ItemDisplayContext.NONE, entity);
        state.yaw = entity.getYRot(partialTicks);
        state.pitch = entity.getXRot(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw));
        poseStack.rotate(Axis.XP.rotationDegrees(90 - state.pitch));   // its nose (+y) laid along +z, raised by its pitch
        poseStack.scale(SCALE, SCALE, SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected boolean affectedByCulling(ArtilleryShell entity) {
        return false;
    }
}
