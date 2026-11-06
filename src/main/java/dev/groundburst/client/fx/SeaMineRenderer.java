package dev.groundburst.client.fx;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.naval.GBNaval;
import dev.groundburst.content.naval.SeaMine;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A sea mine (DESIGN.md §14.6): the horned mine (the moored mine's item model's "body"), swaying a little on its chain
 * or rolling awash; a moored one's sinker (its "sinker") on the bottom, or under the mine while the two sink; and the
 * chain between them, rusty links on two crossed planes, a block of chain at a time, each lit where it hangs.
 */
public class SeaMineRenderer extends EntityRenderer<SeaMine, SeaMineRenderer.State> {
    static final Identifier CHAIN = GroundBurst.id("textures/entity/naval/mine_chain.png");
    /** The chain's half-width (it is three texels wide, as vanilla's). */
    private static final float HALF = 1.5f / 16;

    public static class State extends EntityRenderState {
        public final ItemStackRenderState body = new ItemStackRenderState();
        public final ItemStackRenderState sinker = new ItemStackRenderState();
        public boolean showSinker, showChain;
        /** The sinker's bottom, and the chain's two ends, from where the mine is drawn. */
        public Vec3 sinkerAt = Vec3.ZERO, chainFrom = Vec3.ZERO, chainTo = Vec3.ZERO;
        public int sinkerLight;
        public int[] chainLight = new int[0];
        public float yaw, pitch, roll;
    }

    private final ItemModelResolver items;
    /** The item stacks its parts are drawn from: made when first drawn (the renderer is made before items have their components). */
    private ItemStack body, sinker;

    public SeaMineRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
    }

    private static ItemStack part(String look) {
        ItemStack s = new ItemStack(GBNaval.MOORED_MINE.get());
        s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(look), List.of()));
        return s;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SeaMine mine, State s, float partialTicks) {
        super.extractRenderState(mine, s, partialTicks);
        if (this.body == null) {
            this.body = part("body");
            this.sinker = part("sinker");
        }
        this.items.updateForNonLiving(s.body, this.body, ItemDisplayContext.NONE, mine);
        Vec3 pos = mine.getPosition(partialTicks);
        SeaMine.Stage stage = mine.stage();
        float t = mine.tickCount + partialTicks;
        float sway = stage == SeaMine.Stage.ADRIFT ? 7f : stage == SeaMine.Stage.MOORED ? 3f : 1f;
        s.yaw = mine.getYRot();
        s.pitch = sway * (float) Math.sin(t * 0.045 + mine.getId());
        s.roll = sway * (float) Math.sin(t * 0.037 + mine.getId() * 1.7);
        s.showSinker = mine.moored() && stage != SeaMine.Stage.ADRIFT;
        s.showChain = false;
        if (!s.showSinker) {
            return;
        }
        this.items.updateForNonLiving(s.sinker, this.sinker, ItemDisplayContext.NONE, mine);
        Vec3 anchor = stage == SeaMine.Stage.SINKING ? pos.add(0, SeaMine.CENTRE - SeaMine.ON_SINKER, 0) : mine.anchor();
        s.sinkerAt = anchor.subtract(pos);
        s.sinkerLight = net.minecraft.client.renderer.LevelRenderer.getLightCoords(mine.level(), BlockPos.containing(anchor.add(0, 0.5, 0)));
        if (stage == SeaMine.Stage.SINKING) {
            return;
        }
        s.chainFrom = s.sinkerAt.add(0, SeaMine.SINKER_TOP, 0);
        s.chainTo = new Vec3(0, SeaMine.CENTRE - SeaMine.SHACKLE, 0);
        double length = s.chainFrom.distanceTo(s.chainTo);
        if (length < 0.05) {
            return;
        }
        s.showChain = true;
        int n = (int) Math.ceil(length);
        if (s.chainLight.length != n) {
            s.chainLight = new int[n];
        }
        Vec3 dir = s.chainTo.subtract(s.chainFrom).normalize();
        for (int k = 0; k < n; k++) {
            Vec3 mid = pos.add(s.chainFrom).add(dir.scale(Math.min(length, k + 0.5)));
            s.chainLight[k] = net.minecraft.client.renderer.LevelRenderer.getLightCoords(mine.level(), BlockPos.containing(mid));
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, SeaMine.CENTRE, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(s.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(s.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(s.roll));
        s.body.submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        poseStack.popPose();
        if (s.showSinker) {
            poseStack.pushPose();
            poseStack.translate(s.sinkerAt.x, s.sinkerAt.y + 0.5, s.sinkerAt.z);      // (an item model is drawn about its middle)
            poseStack.mulPose(Axis.YP.rotationDegrees(s.yaw));
            s.sinker.submit(poseStack, collector, s.sinkerLight, OverlayTexture.NO_OVERLAY, s.outlineColor);
            poseStack.popPose();
        }
        if (s.showChain) {
            Vec3 from = s.chainFrom, to = s.chainTo;
            int[] light = s.chainLight;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CHAIN), (pose, buffer) -> chain(buffer, pose, from, to, light));
        }
        super.submit(s, poseStack, collector, camera);
    }

    /** The chain from {@code from} to {@code to}: two crossed planes a block of chain at a time (the last one shorter). */
    private static void chain(VertexConsumer b, PoseStack.Pose pose, Vec3 from, Vec3 to, int[] light) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        Vec3 dir = d.scale(1 / length);
        Vec3 a = dir.cross(Math.abs(dir.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1)).normalize();
        Vec3 c = dir.cross(a).normalize();
        for (int k = 0; k < light.length; k++) {
            double s0 = k, s1 = Math.min(length, k + 1);
            Vec3 p0 = from.add(dir.scale(s0)), p1 = from.add(dir.scale(s1));
            float v1 = (float) (s1 - s0);
            plane(b, pose, p0, p1, a, c, 0f, 3f / 16, v1, light[k]);
            plane(b, pose, p0, p1, c, a, 3f / 16, 6f / 16, v1, light[k]);
        }
    }

    private static void plane(VertexConsumer b, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 side, Vec3 normal, float u0, float u1, float v1,
            int light) {
        Vec3 o = side.scale(HALF);
        vertex(b, pose, p0.subtract(o), u0, 0, light, normal);
        vertex(b, pose, p0.add(o), u1, 0, light, normal);
        vertex(b, pose, p1.add(o), u1, v1, light, normal);
        vertex(b, pose, p1.subtract(o), u0, v1, light, normal);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, Vec3 p, float u, float v, int light, Vec3 n) {
        b.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    /** Drawn wherever it is in sight or not: its chain and sinker reach far below its own box. */
    @Override
    protected boolean affectedByCulling(SeaMine mine) {
        return false;
    }
}
