package dev.groundburst.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.alarm.AlarmBlock;
import dev.groundburst.content.alarm.AlarmBlockEntity;
import dev.groundburst.content.alarm.Alarms;
import dev.groundburst.registry.GBLang;
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
 * The Alert Display's screen (DESIGN.md §18.6): ALL CLEAR in green with its channel; or the alert, its title flashing:
 * an attack's missiles and the time to the first impact, the radiation's dose rate, the gas, or an alarm. Drawn lit on
 * the screen's face, 3 px out from the wall.
 */
public class AlertDisplayRenderer implements BlockEntityRenderer<AlarmBlockEntity, AlertDisplayRenderer.State> {
    private static final String T_CLEAR = GBLang.add(GroundBurst.MODID + ".display.clear", "ALL CLEAR");
    private static final String T_CHANNEL = GBLang.add(GroundBurst.MODID + ".display.channel", "CHANNEL %s");
    private static final String T_ATTACK = GBLang.add(GroundBurst.MODID + ".display.attack", "ATTACK");
    private static final String T_INBOUND = GBLang.add(GroundBurst.MODID + ".display.inbound", "%s INBOUND");
    private static final String T_IMPACT = GBLang.add(GroundBurst.MODID + ".display.impact", "IMPACT %s");
    private static final String T_RADIATION = GBLang.add(GroundBurst.MODID + ".display.radiation", "RADIATION");
    private static final String T_RATE = GBLang.add(GroundBurst.MODID + ".display.rate", "%s RAD/H");
    private static final String T_GAS = GBLang.add(GroundBurst.MODID + ".display.gas", "POISON GAS");
    private static final String T_GAS_LEVEL = GBLang.add(GroundBurst.MODID + ".display.gas_level", "%s%% IN AIR");
    private static final String T_ALARM = GBLang.add(GroundBurst.MODID + ".display.alarm", "ALARM");
    private static final String T_COVER = GBLang.add(GroundBurst.MODID + ".display.cover", "TAKE COVER");

    /** The screen's face (3 px out from the wall: 5 px from the block's middle) and its middle (8 px up); text scales. */
    private static final float FACE = -(5f - 0.06f) / 16f, MIDDLE_Y = 0.5f, SCALE = 0.0105f, TITLE = 1.3f;
    private static final int LIT = 15728880;
    private static final int GREEN = 0xFF46E04A, DIM_GREEN = 0xFF2C8A30, RED = 0xFFFF3A24, DARK_RED = 0xFF8A1A10, AMBER = 0xFFFFB428,
            GAS_GREEN = 0xFFCDF04A, WHITE = 0xFFE8E8E8;

    public static class State extends BlockEntityRenderState {
        float yaw;
        @Nullable Component title, line1, line2;
        int titleColor, lineColor;
    }

    private final Font font;

    public AlertDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AlarmBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.yaw = ((AlarmBlock) be.getBlockState().getBlock()).facing(be.getBlockState()).toYRot();
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        boolean bright = now % 20 < 12;                                // the title flashes in an alert
        int kind = be.kind();
        state.line1 = state.line2 = null;
        state.lineColor = WHITE;
        if (kind < 0) {
            state.title = Component.translatable(T_CLEAR);
            state.titleColor = GREEN;
            state.line1 = Component.translatable(T_CHANNEL, be.channel());
            state.lineColor = DIM_GREEN;
            return;
        }
        Alarms.Kind k = Alarms.Kind.values()[kind];
        switch (k) {
            case ATTACK -> {
                state.title = Component.translatable(T_ATTACK);
                state.titleColor = bright ? RED : DARK_RED;
                if (be.count() > 0) {
                    state.line1 = Component.translatable(T_INBOUND, be.count());
                }
                if (be.impactAt() >= 0) {
                    long left = Math.max(0, be.impactAt() - now);
                    state.line2 = Component.translatable(T_IMPACT, String.format("%d:%02d", left / 1200, left / 20 % 60));
                }
            }
            case RADIATION -> {
                state.title = Component.translatable(T_RADIATION);
                state.titleColor = bright ? AMBER : 0xFF8A5A10;
                state.line1 = Component.translatable(T_RATE, String.format("%.0f", be.reading()));
                state.lineColor = AMBER;
            }
            case GAS -> {
                state.title = Component.translatable(T_GAS);
                state.titleColor = bright ? GAS_GREEN : 0xFF6A8020;
                state.line1 = Component.translatable(T_GAS_LEVEL, String.format("%.0f", be.reading()));
                state.lineColor = GAS_GREEN;
            }
            default -> {
                state.title = Component.translatable(T_ALARM);
                state.titleColor = bright ? RED : DARK_RED;
                state.line1 = Component.translatable(T_COVER);
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.title == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, MIDDLE_Y, 0.5);
        // Text faces +z (south, yaw 0); turn it to face the way the display does (Minecraft's yaw runs clockwise).
        poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.translate(0, 0, FACE);                            // back to the screen, a hair in front of it
        int lines = 1 + (state.line1 != null ? 1 : 0) + (state.line2 != null ? 1 : 0);
        float height = 9 * TITLE + (lines - 1) * 10;                  // text pixels, top to bottom
        poseStack.scale(SCALE, -SCALE, SCALE);
        float y = -height / 2;
        poseStack.pushPose();
        poseStack.scale(TITLE, TITLE, 1);
        line(collector, poseStack, state.title, y / TITLE, state.titleColor);
        poseStack.popPose();
        y += 9 * TITLE + 1;
        if (state.line1 != null) {
            line(collector, poseStack, state.line1, y, state.lineColor);
            y += 10;
        }
        if (state.line2 != null) {
            line(collector, poseStack, state.line2, y, state.lineColor);
        }
        poseStack.popPose();
    }

    private void line(SubmitNodeCollector collector, PoseStack pose, Component text, float y, int color) {
        var seq = text.getVisualOrderText();
        collector.submitText(pose, -this.font.width(seq) / 2f, y, seq, false, Font.DisplayMode.POLYGON_OFFSET, LIT, color, 0, 0);
    }
}
