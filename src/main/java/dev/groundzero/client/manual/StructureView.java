package dev.groundzero.client.manual;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Field Manual's 3D structure view: draws a multiblock pattern's blocks (their real block models) into a GUI area,
 * turned by {@code yaw}, tipped by {@code pitch}, showing only layer {@code layer} (or all of them when it is -1).
 */
public final class StructureView {
    private StructureView() {}

    /** One block to draw, at pattern position (column, layer, row). */
    public record Block(BlockState state, int x, int y, int z) {}

    public record State(List<Block> blocks, int[] size, float yaw, float pitch, int layer, int x0, int y0, int x1, int y1, float scale,
            @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State(List<Block> blocks, int[] size, float yaw, float pitch, int layer, int x0, int y0, int x1, int y1, float scale,
                @Nullable ScreenRectangle scissorArea) {
            this(blocks, size, yaw, pitch, layer, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }

    /** The pattern's blocks as the spawner would place them (controller facing north). */
    public static List<Block> blocks(MultiblockDefinition def) {
        List<Block> out = new java.util.ArrayList<>();
        String[][] layers = def.layers();
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                String row = layers[y][z];
                for (int x = 0; x < row.length(); x++) {
                    char key = row.charAt(x);
                    MultiblockDefinition.Entry entry = def.legend().get(key);
                    if (key == ' ' || key == '.' || entry == null) {
                        continue;
                    }
                    BlockState state = entry.preview().get();
                    if (state.hasProperty(MachineBlock.FACING)) {
                        state = state.setValue(MachineBlock.FACING, Direction.NORTH);
                    }
                    out.add(new Block(state, x, y, z));
                }
            }
        }
        return out;
    }

    /** A scale that fits the whole structure, at any turn, into a {@code w} x {@code h} area. */
    public static float fitScale(int[] size, int w, int h) {
        // Seen from 30 degrees up: the footprint's diagonal across, the height foreshortened plus half the diagonal.
        double across = Math.sqrt((double) size[0] * size[0] + (double) size[2] * size[2]);
        double tall = size[1] * Math.cos(Math.toRadians(30)) + across * Math.sin(Math.toRadians(30));
        return (float) Math.min(w / (across + 0.3), h / (tall + 0.3));
    }

    public static final class Renderer extends PictureInPictureRenderer<State> {
        private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
        private final BlockModelRenderState block = new BlockModelRenderState();

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
            int[] size = state.size();
            // Screen y runs down; turn the world the right way up, then tip it toward the viewer and spin it.
            pose.rotateDegrees(Axis.ZP, 180);
            pose.rotateDegrees(Axis.XP, -state.pitch());
            pose.rotateDegrees(Axis.YP, state.yaw());
            pose.translate(-size[0] / 2.0F, -size[1] / 2.0F, -size[2] / 2.0F);
            for (Block b : state.blocks()) {
                if (state.layer() >= 0 && b.y() != state.layer()) {
                    continue;
                }
                pose.pushPose();
                pose.translate(b.x(), b.y(), b.z());
                mc.getBlockModelResolver().update(this.block, b.state(), CONTEXT);
                this.block.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        }

        @Override
        protected String getTextureLabel() {
            return "groundzero structure view";
        }
    }
}
