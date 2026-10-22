package dev.groundzero.client.render;

import com.geckolib.animatable.GeoAnimatable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.google.common.reflect.TypeToken;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineTank;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

/** GeckoLib machine renderer whose culling box covers the whole model, including the upper half of tall machines. */
public class MachineGeoRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T, BlockEntityRenderState> {
    private final int height;
    private final int reach;

    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height) {
        this(context, type, height, 0);
    }

    /** {@code reach}: how many blocks the model extends sideways beyond its own (multiblock structures). */
    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height, int reach) {
        super(context, type);
        this.height = height;
        this.reach = reach;
    }

    /**
     * Draws the model's {@code <texture>_glowmask.png} lit, whatever the light round it (GeckoLib's auto-glow layer). Only
     * for models whose texture has one: GeckoLib does not check, and a missing one draws as the missing texture.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public MachineGeoRenderer<T> withGlow() {
        // Raw: GeckoLib's layer wants a GeoRenderState, which this renderer's state only becomes at runtime (see below).
        withRenderLayer((com.geckolib.renderer.layer.GeoRenderLayer) new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer(this));
        return this;
    }

    private static final DataTicket<Set<String>> HIDDEN_BONES = DataTicket.create("groundzero_hidden_bones", new TypeToken<Set<String>>() {});
    private static final DataTicket<java.util.Map<String, float[]>> BONE_SCALES = DataTicket.create("groundzero_bone_scales",
            new TypeToken<java.util.Map<String, float[]>>() {});

    @Override
    public void addRenderData(T animatable, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        if (animatable instanceof AnimatedMachine machine) {
            ((GeoRenderState) renderState).addGeckolibData(HIDDEN_BONES, machine.hiddenBones());
            ((GeoRenderState) renderState).addGeckolibData(BONE_SCALES, machine.boneScales());
        }
        captureTank(animatable, (GeoRenderState) renderState);
    }

    // Raw type: GeckoLib injects GeoRenderState into BlockEntityRenderState at runtime, which javac cannot see here.
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
        java.util.Map<String, float[]> scales = (java.util.Map<String, float[]>) renderPassInfo.getGeckolibData(BONE_SCALES);
        if (scales != null) {
            scales.forEach((bone, s) -> snapshots.ifPresent(bone, snapshot -> snapshot.setScale(s[0], s[1], s[2])));
        }
        Set<String> hidden = (Set<String>) renderPassInfo.getGeckolibData(HIDDEN_BONES);
        if (hidden != null) {
            for (String bone : hidden) {
                snapshots.ifPresent(bone, snapshot -> snapshot.skipRender(true).skipChildrenRender(true));
            }
        }
    }

    /** What one tank window shows this frame: fluid, fill fraction, tint and the box to fill. */
    private record TankSnapshot(Fluid fluid, float fill, int color, MachineBlockEntity.TankRender box) {}

    private static final DataTicket<List<TankSnapshot>> TANKS = DataTicket.create("groundzero_tanks", new TypeToken<List<TankSnapshot>>() {});

    private static void captureTank(BlockEntity be, GeoRenderState state) {
        if (!(be instanceof MachineBlockEntity machine) || !machine.showsTanks() || be.getLevel() == null) {
            return;
        }
        List<TankSnapshot> shown = new ArrayList<>();
        for (MachineBlockEntity.TankRender r : machine.tankRenders()) {
            MachineTank tank = machine.tank(r.tank());
            if (tank.amount() <= 0) {
                continue;
            }
            Fluid fluid = tank.fluid().getFluid();
            float fill = Math.min(1f, tank.amount() / (float) tank.capacity());
            int tint = FluidVisuals.tintInWorld(fluid, be.getLevel(), be.getBlockPos());
            shown.add(new TankSnapshot(fluid, fill, 0xD0000000 | (tint & 0xFFFFFF), r));
        }
        if (!shown.isEmpty()) {
            state.addGeckolibData(TANKS, shown);
        }
    }

    /** Draws each tank's fluid (its own still texture, translucent) after the model, turned with the block. */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void postRenderPass(RenderPassInfo renderPassInfo, SubmitNodeCollector renderTasks) {
        List<TankSnapshot> tanks = (List<TankSnapshot>) renderPassInfo.getGeckolibData(TANKS);
        if (tanks == null) {
            return;
        }
        PoseStack poseStack = renderPassInfo.poseStack();
        int light = ((BlockEntityRenderState) renderPassInfo.renderState()).lightCoords;
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        tryRotateByBlockstate(renderPassInfo, poseStack);
        for (TankSnapshot tank : tanks) {
            TextureAtlasSprite sprite = FluidVisuals.still(tank.fluid());
            MachineBlockEntity.TankRender b = tank.box();
            float x0 = b.x0() / 16f, x1 = b.x1() / 16f, z0 = b.z0() / 16f, z1 = b.z1() / 16f;
            float y0 = b.y0() / 16f, y1 = (b.y0() + (b.y1() - b.y0()) * tank.fill()) / 16f;
            int c = tank.color();
            renderTasks.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
                float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
                quad(buffer, pose, c, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1);
                // Sides tiled in one-block pieces so tall tanks keep the texture's scale.
                for (float ya = y0; ya < y1 - 1e-4f; ya += 1f) {
                    float yb = Math.min(y1, ya + 1f);
                    float vb = v0 + (v1 - v0) * (yb - ya);
                    quad(buffer, pose, c, light, 0, 0, -1, x1, yb, z0, x1, ya, z0, x0, ya, z0, x0, yb, z0, u0, v0, u1, vb);
                    quad(buffer, pose, c, light, 0, 0, 1, x0, yb, z1, x0, ya, z1, x1, ya, z1, x1, yb, z1, u0, v0, u1, vb);
                    quad(buffer, pose, c, light, -1, 0, 0, x0, yb, z0, x0, ya, z0, x0, ya, z1, x0, yb, z1, u0, v0, u1, vb);
                    quad(buffer, pose, c, light, 1, 0, 0, x1, yb, z1, x1, ya, z1, x1, ya, z0, x1, yb, z0, u0, v0, u1, vb);
                }
            });
        }
        poseStack.popPose();
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int color, int light, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        buffer.addVertex(pose, ax, ay, az).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, bx, by, bz).setColor(color).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, cx, cy, cz).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, dx, dy, dz).setColor(color).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(T blockEntity) {
        // Structures may also reach one block below the controller (the accelerator ring's plinth).
        return new AABB(blockEntity.getBlockPos()).inflate(0.25 + this.reach, 0, 0.25 + this.reach).expandTowards(0, this.height - 1, 0)
                .expandTowards(0, this.reach > 0 ? -1 : 0, 0);
    }
}
