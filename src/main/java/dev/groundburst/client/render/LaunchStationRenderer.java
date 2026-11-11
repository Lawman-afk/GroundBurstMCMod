package dev.groundburst.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * The Launch Control Station (DESIGN.md §17.12): its model, and on its big screen the network's state written in the
 * phosphor's green: LAUNCH CONTROL, the network's code, how many are linked and armed, and the soonest countdown (red,
 * flashing), the missiles in the air and the first to come down (amber), or STANDBY. (The 1.21.1 backport's: the text is
 * drawn after the model, the pose already turned with the block.)
 */
public class LaunchStationRenderer extends MachineGeoRenderer<LaunchStationBlockEntity> {
    private static final String P = GroundBurst.MODID + ".station.screen.";
    private static final String T_TITLE = GBLang.add(P + "title", "LAUNCH CONTROL");
    private static final String T_NET = GBLang.add(P + "net", "NET %s");
    private static final String T_COUNTS = GBLang.add(P + "counts", "%s LINKED  %s ARMED");
    private static final String T_COUNT = GBLang.add(P + "count", "LAUNCH T-%s");
    private static final String T_FLIGHT = GBLang.add(P + "flight", "%s IN FLIGHT %s");
    private static final String T_STANDBY = GBLang.add(P + "standby", "STANDBY");

    /** The big screen's face (model z 8, a hair in front: from the controller's middle) and its middle's height, blocks. */
    private static final float FACE = (8f - 0.06f) / 16f, MIDDLE_Y = 21f / 16f, SCALE = 0.0112f, TITLE = 1.15f;
    private static final int LIT = 15728880;
    private static final int GREEN = 0xFF46E04A, DIM_GREEN = 0xFF2C8A30, AMBER = 0xFFFFB428, RED = 0xFFFF3A24, DARK_RED = 0xFF8A1A10;

    public LaunchStationRenderer(BlockEntityRendererProvider.Context context) {
        super(context, GBLaunch.STATION_ENTITY.get(), 3, 1);
    }

    @Override
    public void postRender(PoseStack pose, LaunchStationBlockEntity be, BakedGeoModel model, MultiBufferSource buffers, @Nullable VertexConsumer buffer,
            boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green,
            float blue, float alpha) {
        super.postRender(pose, be, model, buffers, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        if (isReRender || !be.isFormed() || !be.shownPowered() || be.getLevel() == null) {
            return;
        }
        long now = be.getLevel().getGameTime();
        Component status;
        int color;
        if (be.soonestCount() >= 0) {
            status = Component.translatable(T_COUNT, (be.soonestCount() + 19) / 20);
            color = now % 20 < 12 ? RED : DARK_RED;
        } else if (be.flying() > 0) {
            int s = Math.max(0, (be.soonestImpact() + 19) / 20);
            status = Component.translatable(T_FLIGHT, be.flying(), s / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60));
            color = AMBER;
        } else {
            status = Component.translatable(T_STANDBY);
            color = DIM_GREEN;
        }
        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        // Text faces +z; the screen faces the model's front (-z): turned round, then back to the screen's face.
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(0, MIDDLE_Y, -FACE);
        pose.scale(SCALE, -SCALE, SCALE);
        float height = 9 * TITLE + 3 * 10 + 2;
        float y = -height / 2;
        pose.pushPose();
        pose.scale(TITLE, TITLE, 1);
        line(buffers, font, pose, Component.translatable(T_TITLE), y / TITLE, AMBER);
        pose.popPose();
        y += 9 * TITLE + 3;
        line(buffers, font, pose, Component.translatable(T_NET, be.shownCode()), y, GREEN);
        y += 10;
        line(buffers, font, pose, Component.translatable(T_COUNTS, be.linked(), be.armedCount()), y, GREEN);
        y += 10;
        line(buffers, font, pose, status, y, color);
        pose.popPose();
    }

    private static void line(MultiBufferSource buffers, Font font, PoseStack pose, Component text, float y, int color) {
        var seq = text.getVisualOrderText();
        font.drawInBatch(seq, -font.width(seq) / 2f, y, color, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIT);
    }
}
