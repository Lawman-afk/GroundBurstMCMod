package dev.groundburst.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.command.DeadHandBlockEntity;
import dev.groundburst.content.command.GBCommand;
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
 * The Dead Hand (DESIGN.md §17.17): its model (the lamps, the keys and the tube's lid by its state, the block entity's),
 * and on its console's little screen what it is doing: SAFE, WATCHING, the countdown in alert (red, flashing), FIRED.
 */
public class DeadHandRenderer extends MachineGeoRenderer<DeadHandBlockEntity> {
    private static final String P = GroundBurst.MODID + ".dead_hand.screen.";
    private static final String T_SAFE = GBLang.add(P + "safe", "SAFE");
    private static final String T_WATCHING = GBLang.add(P + "watching", "WATCHING");
    private static final String T_ALERT = GBLang.add(P + "alert", "T-%s");
    private static final String T_FIRED = GBLang.add(P + "fired", "FIRED");
    private static final String T_NO_ROCKET = GBLang.add(P + "no_rocket", "NO ROCKET");
    /**
     * The console's screen (tools/assets/dead_hand.py's SCREEN: x -4.5 to 4.5, y 19 to 25, its front at z -9.8): its
     * middle's height, the plane just in front of it (model pixels from the controller's middle), and the type's size
     * (WATCHING about 8.3 pixels wide in the screen's 9).
     */
    private static final float SCREEN_Y = 22f / 16f, FACE = 9.86f / 16f, SCALE = 0.011f;
    private static final int LIT = 15728880, GREEN = 0xFF46E04A, AMBER = 0xFFFFB428, RED = 0xFFFF3A24, DARK_RED = 0xFF7A1A10;

    private record Text(Component text, int colour) {}

    private static final DataTicket<Text> TEXT = DataTicket.create("groundburst_dead_hand_text", Text.class);

    public DeadHandRenderer(BlockEntityRendererProvider.Context context) {
        super(context, GBCommand.DEAD_HAND_ENTITY.get(), 3, 1);
    }

    @Override
    public void addRenderData(DeadHandBlockEntity be, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        super.addRenderData(be, relatedObject, renderState, partialTick);
        if (!be.isFormed() || !be.powered() || be.getLevel() == null) {
            return;
        }
        long now = be.getLevel().getGameTime();
        Text t = switch (be.state()) {
            case SAFE -> be.rocket() ? new Text(Component.translatable(T_SAFE), GREEN) : new Text(Component.translatable(T_NO_ROCKET), AMBER);
            case WATCHING -> new Text(Component.translatable(T_WATCHING), AMBER);
            case ALERT -> new Text(Component.translatable(T_ALERT, (Math.max(0, be.alertUntil() - now) + 19) / 20), now % 20 < 12 ? RED : DARK_RED);
            case FIRING, SPENT -> new Text(Component.translatable(T_FIRED), RED);
        };
        ((GeoRenderState) renderState).addGeckolibData(TEXT, t);
    }

    @Override
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
        pose.rotate(Axis.YP.rotationDegrees(180));
        pose.translate(0, SCREEN_Y, FACE);
        pose.scale(SCALE, -SCALE, SCALE);
        var seq = text.text().getVisualOrderText();
        renderTasks.submitText(pose, -font.width(seq) / 2f, -4, seq, false, Font.DisplayMode.POLYGON_OFFSET, LIT, text.colour(), 0, 0);
        pose.popPose();
    }
}
