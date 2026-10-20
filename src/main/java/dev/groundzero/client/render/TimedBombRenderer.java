package dev.groundzero.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeBlockEntity;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The timed bomb's clock face (DESIGN.md §14): the time left in glowing red digits while it counts down, its set time
 * in dim green while it is safe. Other charges draw nothing here.
 */
public class TimedBombRenderer implements BlockEntityRenderer<ChargeBlockEntity, TimedBombRenderer.State> {
    /** The display's face, 2.4 px in from the block's front, and its middle 4 px up (see tools/assets/charges.py). */
    private static final float FACE = 0.5f - 2.4f / 16f + 0.003f, MIDDLE_Y = 4f / 16f, SCALE = 0.016f;
    private static final int LIT = 15728880;

    public static class State extends BlockEntityRenderState {
        @Nullable String text;
        boolean counting;
        float yaw;
    }

    private final Font font;

    public TimedBombRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChargeBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.text = null;
        if (be.kind() != ChargeKind.TIMED_BOMB) {
            return;
        }
        state.counting = be.counting();
        int ticks = state.counting ? Math.max(0, be.countdown()) : be.timer() * 20;
        state.text = String.format("%d:%02d", ticks / 1200, ticks / 20 % 60);
        state.yaw = be.getBlockState().getValue(ChargeBlock.FACING).toYRot();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.text == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, MIDDLE_Y, 0.5);
        // Text faces +z (south, yaw 0); turn it to face the way the bomb does (Minecraft's yaw runs clockwise).
        poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.translate(0, 0, FACE);
        poseStack.scale(SCALE, -SCALE, SCALE);
        var line = Component.literal(state.text).getVisualOrderText();
        collector.submitText(poseStack, -this.font.width(line) / 2f, -4, line, false, Font.DisplayMode.POLYGON_OFFSET, LIT,
                state.counting ? 0xFFFF3A28 : 0xFF3A8A3A, 0, 0);
        poseStack.popPose();
    }
}
