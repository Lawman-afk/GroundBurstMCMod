package dev.groundburst.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.command.GBCommand;
import dev.groundburst.content.command.SeismographBlockEntity;
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
 * The Seismograph (DESIGN.md §17.17): its model (the drum turned, the pen swung, the carriage stepped along by the block
 * entity), and on its front panel's amber readout the last blast's magnitude ("--.-" before any), lit while it listens.
 */
public class SeismographRenderer extends MachineGeoRenderer<SeismographBlockEntity> {
    /**
     * The readout (tools/assets/seismograph.py's READOUT: x 1.0 to 5.8, y 5.2 to 6.8, its glass's front at z -6.76): the
     * text's middle, the plane just in front of the glass (model pixels from the block's middle), and the type's size (a
     * line of four characters about 3.5 pixels wide and 1.3 high, in the glass's 4.8 by 1.6).
     */
    private static final float READOUT_X = 3.4f / 16f, READOUT_Y = 6.0f / 16f, FACE = 6.82f / 16f, SCALE = 0.012f;
    private static final int LIT = 15728880, AMBER = 0xFFFFA030;
    private static final DataTicket<String> TEXT = DataTicket.create("groundburst_seismograph_text", String.class);

    public SeismographRenderer(BlockEntityRendererProvider.Context context) {
        super(context, GBCommand.SEISMOGRAPH_ENTITY.get(), 1);
    }

    @Override
    public void addRenderData(SeismographBlockEntity be, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        super.addRenderData(be, relatedObject, renderState, partialTick);
        if (!be.shownPowered()) {
            return;
        }
        float m = be.shownMagnitude();
        String text = m <= -5 ? "--.-" : String.format(java.util.Locale.ROOT, "%4.1f", m);
        ((GeoRenderState) renderState).addGeckolibData(TEXT, text);
    }

    @Override
    public void postRenderPass(RenderPassInfo renderPassInfo, SubmitNodeCollector renderTasks) {
        super.postRenderPass(renderPassInfo, renderTasks);
        String text = (String) renderPassInfo.getGeckolibData(TEXT);
        if (text == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        PoseStack pose = renderPassInfo.poseStack();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        tryRotateByBlockstate(renderPassInfo, pose);
        // Text faces +z; the panel faces the model's front (-z): turned round (its +x then the model's -x, its +z the model's
        // -z), then out to the readout's face.
        pose.rotate(Axis.YP.rotationDegrees(180));
        pose.translate(-READOUT_X, READOUT_Y, FACE);
        pose.scale(SCALE, -SCALE, SCALE);
        var seq = Component.literal(text).getVisualOrderText();
        renderTasks.submitText(pose, -font.width(seq) / 2f, -4, seq, false, Font.DisplayMode.POLYGON_OFFSET, LIT, AMBER, 0, 0);
        pose.popPose();
    }
}
