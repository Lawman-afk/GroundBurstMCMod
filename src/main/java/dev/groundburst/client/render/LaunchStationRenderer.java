package dev.groundburst.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.network.chat.Component;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

/**
 * The Launch Control Station (DESIGN.md §17.12): its model, and on its big screen the network's state written in the
 * phosphor's green: LAUNCH CONTROL, the network's code, how many are linked and armed, and the soonest countdown (red,
 * flashing), the missiles in the air and the first to come down (amber), or STANDBY.
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

    /** What the screen says this frame (null lines: blank). */
    private record Text(Component title, Component code, Component counts, Component status, int statusColor) {}

    private static final DataTicket<Text> TEXT = DataTicket.create("groundburst_station_text", Text.class);

    public LaunchStationRenderer(BlockEntityRendererProvider.Context context) {
        super(context, GBLaunch.STATION_ENTITY.get(), 3, 1);
    }

    @Override
    public void addRenderData(LaunchStationBlockEntity be, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        super.addRenderData(be, relatedObject, renderState, partialTick);
        if (!be.isFormed() || !be.shownPowered() || be.getLevel() == null) {
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
        ((GeoRenderState) renderState).addGeckolibData(TEXT, new Text(Component.translatable(T_TITLE), Component.translatable(T_NET, be.shownCode()),
                Component.translatable(T_COUNTS, be.linked(), be.armedCount()), status, color));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void postRenderPass(RenderPassInfo renderPassInfo, SubmitNodeCollector renderTasks) {
        super.postRenderPass(renderPassInfo, renderTasks);
        Text text = (Text) renderPassInfo.getGeckolibData(TEXT);
        if (text == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        PoseStack pose = renderPassInfo.poseStack();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        tryRotateByBlockstate(renderPassInfo, pose);
        // Text faces +z; the screen faces the model's front (-z): turned round, then back to the screen's face.
        pose.rotate(Axis.YP.rotationDegrees(180));
        pose.translate(0, MIDDLE_Y, -FACE);
        pose.scale(SCALE, -SCALE, SCALE);
        float height = 9 * TITLE + 3 * 10 + 2;
        float y = -height / 2;
        pose.pushPose();
        pose.scale(TITLE, TITLE, 1);
        line(renderTasks, font, pose, text.title(), y / TITLE, AMBER);
        pose.popPose();
        y += 9 * TITLE + 3;
        line(renderTasks, font, pose, text.code(), y, GREEN);
        y += 10;
        line(renderTasks, font, pose, text.counts(), y, GREEN);
        y += 10;
        line(renderTasks, font, pose, text.status(), y, text.statusColor());
        pose.popPose();
    }

    private static void line(SubmitNodeCollector collector, Font font, PoseStack pose, Component text, float y, int color) {
        var seq = text.getVisualOrderText();
        collector.submitText(pose, -font.width(seq) / 2f, y, seq, false, Font.DisplayMode.POLYGON_OFFSET, LIT, color, 0, 0);
    }
}
