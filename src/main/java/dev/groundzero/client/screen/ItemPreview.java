package dev.groundzero.client.screen;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A 3D item drawn large into a GUI area (the Assembly Bench's preview, DESIGN.md §10): its model as it is in the world
 * (no GUI pose), turned by {@code yaw}, tipped toward the viewer by {@code pitch}, stretched by {@code size} (across,
 * along its height) and raised by {@code lift} blocks so its middle sits in the middle of the area.
 */
public final class ItemPreview {
    private ItemPreview() {}

    public record State(ItemStack stack, float[] size, float lift, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
            @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State(ItemStack stack, float[] size, float lift, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
                @Nullable ScreenRectangle scissorArea) {
            this(stack, size, lift, yaw, pitch, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }

    public static final class Renderer extends PictureInPictureRenderer<State> {
        private final ItemStackRenderState item = new ItemStackRenderState();

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
            Minecraft mc = Minecraft.getInstance();
            mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            // Screen y runs down; turn it the right way up, then tip it toward the viewer and spin it.
            pose.rotateDegrees(Axis.ZP, 180);
            pose.rotateDegrees(Axis.XP, -state.pitch());
            pose.rotateDegrees(Axis.YP, state.yaw());
            pose.scale(state.size()[0], state.size()[1], state.size()[0]);
            pose.translate(0, state.lift(), 0);
            mc.getItemModelResolver().updateForTopItem(this.item, state.stack(), ItemDisplayContext.NONE, mc.level, null, 0);
            this.item.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }

        @Override
        protected String getTextureLabel() {
            return "groundzero item preview";
        }
    }
}
