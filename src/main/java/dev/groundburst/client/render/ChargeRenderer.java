package dev.groundburst.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.content.ordnance.charge.ChargeBlock;
import dev.groundburst.content.ordnance.charge.ChargeBlockEntity;
import dev.groundburst.content.ordnance.charge.ChargeKind;
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
 * What the charges draw beyond their models (DESIGN.md §14): the timed bomb's clock face (the time left in glowing red
 * digits while it counts down, its set time in dim green otherwise), and an armed claymore's red laser tripwire.
 */
public class ChargeRenderer implements BlockEntityRenderer<ChargeBlockEntity, ChargeRenderer.State> {
    /** The display's face, 2.4 px in from the block's front, and its middle 4 px up (see tools/assets/charges.py). */
    private static final float FACE = 0.5f - 2.4f / 16f + 0.003f, MIDDLE_Y = 4f / 16f, SCALE = 0.016f;
    private static final int LIT = 15728880;

    public static class State extends BlockEntityRenderState {
        @Nullable String text;
        boolean counting;
        float yaw;
        /** The laser, relative to the block's corner (null: none). */
        @Nullable Vec3 laserFrom, laserTo;
    }

    /** The laser's half-thickness, blocks, and colour. */
    private static final float BEAM = 0.016f;
    private static final int LASER_R = 255, LASER_G = 24, LASER_B = 16, LASER_A = 190;

    private final Font font;

    public ChargeRenderer(BlockEntityRendererProvider.Context context) {
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
        state.laserFrom = null;
        if (be.kind() == ChargeKind.CLAYMORE && be.armed() && be.getLevel() != null) {
            var bs = be.getBlockState();
            Vec3 corner = Vec3.atLowerCornerOf(be.getBlockPos());
            Vec3 from = dev.groundburst.content.ordnance.charge.Charges.laserStart(be.getBlockPos(), bs);
            Vec3 dir = Vec3.atLowerCornerOf(bs.getValue(ChargeBlock.FACING).getUnitVec3i());
            double len = dev.groundburst.content.ordnance.charge.Charges.laserLength(be.getLevel(), be.getBlockPos(), bs);
            state.laserFrom = from.subtract(corner);
            state.laserTo = from.add(dir.scale(len)).subtract(corner);
            return;
        }
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
        if (state.laserFrom != null && state.laserTo != null) {
            laser(state.laserFrom, state.laserTo, poseStack, collector);
        }
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

    /** A thin red beam along an axis, as a square tube of flat colour (no texture; translucent, so it reads on any ground). */
    private static void laser(Vec3 a, Vec3 b, PoseStack poseStack, SubmitNodeCollector collector) {
        float x0 = (float) Math.min(a.x, b.x) - BEAM, x1 = (float) Math.max(a.x, b.x) + BEAM;
        float y0 = (float) Math.min(a.y, b.y) - BEAM, y1 = (float) Math.max(a.y, b.y) + BEAM;
        float z0 = (float) Math.min(a.z, b.z) - BEAM, z1 = (float) Math.max(a.z, b.z) + BEAM;
        collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads(), (pose, buffer) -> {
            float[][] faces = {
                    {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                    {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                    {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
            for (float[] f : faces) {
                for (int i = 0; i < 12; i += 3) {
                    buffer.addVertex(pose, f[i], f[i + 1], f[i + 2]).setColor(LASER_R, LASER_G, LASER_B, LASER_A);
                }
            }
        });
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ChargeBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(dev.groundburst.content.ordnance.charge.Charges.LASER_RANGE);
    }
}
