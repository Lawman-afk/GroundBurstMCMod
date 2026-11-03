package dev.groundburst.client.fx;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.artillery.ArtilleryShell;
import dev.groundburst.content.artillery.GBArtillery;
import dev.groundburst.content.artillery.Calibre;
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
    /** Each calibre's shell drawn this big (its item model is a block tall). */
    private static float scale(Calibre c) {
        return switch (c) {
            case MORTAR -> 0.8f;
            case HOWITZER -> 1.1f;
        };
    }

    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float yaw, pitch, scale = 1;
    }

    private final ItemModelResolver items;
    private final Map<Integer, ItemStack> looks = new HashMap<>();

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
        Calibre c = entity.calibre();
        ItemStack look = this.looks.computeIfAbsent(c.ordinal() * 16 + entity.type().ordinal(), k -> {
            var item = GBArtillery.shell(c, entity.type());
            return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
        });
        state.scale = scale(c);
        this.items.updateForNonLiving(state.item, look, ItemDisplayContext.NONE, entity);
        state.yaw = entity.getYRot(partialTicks);
        state.pitch = entity.getXRot(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw));
        poseStack.rotate(Axis.XP.rotationDegrees(90 - state.pitch));   // its nose (+y) laid along +z, raised by its pitch
        poseStack.scale(state.scale, state.scale, state.scale);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected boolean affectedByCulling(ArtilleryShell entity) {
        return false;
    }
}
