package dev.groundzero.client.render;

import com.geckolib.animatable.GeoAnimatable;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

/** GeckoLib machine renderer whose culling box covers the whole model, including the upper half of tall machines. */
public class MachineGeoRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T, BlockEntityRenderState> {
    private final int height;

    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height) {
        super(context, type);
        this.height = height;
    }

    private static final DataTicket<Set<String>> HIDDEN_BONES = DataTicket.create("groundzero_hidden_bones", new TypeToken<Set<String>>() {});

    @Override
    public void addRenderData(T animatable, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        if (animatable instanceof AnimatedMachine machine) {
            ((GeoRenderState) renderState).addGeckolibData(HIDDEN_BONES, machine.hiddenBones());
        }
        captureTank(animatable, (GeoRenderState) renderState);
    }

    // Raw type: GeckoLib injects GeoRenderState into BlockEntityRenderState at runtime, which javac cannot see here.
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
        Set<String> hidden = (Set<String>) renderPassInfo.getGeckolibData(HIDDEN_BONES);
        if (hidden != null) {
            for (String bone : hidden) {
                snapshots.ifPresent(bone, snapshot -> snapshot.skipRender(true));
            }
        }
    }

    /** What the tank shows this frame: fluid, fill fraction, tint and the box to fill. */
    private record TankSnapshot(Fluid fluid, float fill, int color, MachineBlockEntity.TankRender box) {}

    private static final DataTicket<TankSnapshot> TANK = DataTicket.create("groundzero_tank", TankSnapshot.class);

    private static void captureTank(BlockEntity be, GeoRenderState state) {
        if (!(be instanceof MachineBlockEntity machine) || machine.tankRender() == null || machine.tank() == null
                || machine.tank().amount() <= 0 || be.getLevel() == null) {
            return;
        }
        Fluid fluid = machine.tank().fluid().getFluid();
        float fill = Math.min(1f, machine.tank().amount() / (float) machine.tank().capacity());
        int color = fluid.isSame(Fluids.WATER) ? 0xC0000000 | ((be.getLevel() instanceof net.minecraft.client.renderer.block.BlockAndTintGetter tint ? BiomeColors.getAverageWaterColor(tint, be.getBlockPos()) : 0x3F76E4) & 0xFFFFFF) : 0xE0FFFFFF;
        state.addGeckolibData(TANK, new TankSnapshot(fluid, fill, color, machine.tankRender()));
    }

    /** Draws the tank's fluid (its own still texture, translucent) after the model, turned with the block. */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void postRenderPass(RenderPassInfo renderPassInfo, SubmitNodeCollector renderTasks) {
        TankSnapshot tank = (TankSnapshot) renderPassInfo.getGeckolibData(TANK);
        if (tank == null) {
            return;
        }
        PoseStack poseStack = renderPassInfo.poseStack();
        int light = ((BlockEntityRenderState) renderPassInfo.renderState()).lightCoords;
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(tank.fluid().defaultFluidState()).stillMaterial().sprite();
        MachineBlockEntity.TankRender b = tank.box();
        float x0 = b.x0() / 16f, x1 = b.x1() / 16f, z0 = b.z0() / 16f, z1 = b.z1() / 16f;
        float y0 = b.y0() / 16f, y1 = (b.y0() + (b.y1() - b.y0()) * tank.fill()) / 16f;
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        tryRotateByBlockstate(renderPassInfo, poseStack);
        renderTasks.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            float vSide = v0 + (v1 - v0) * Math.min(1f, (y1 - y0) * 16f / 16f);
            int c = tank.color();
            // top
            quad(buffer, pose, c, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1);
            // sides (north, south, west, east)
            quad(buffer, pose, c, light, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, u0, v0, u1, vSide);
            quad(buffer, pose, c, light, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, u0, v0, u1, vSide);
            quad(buffer, pose, c, light, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, u0, v0, u1, vSide);
            quad(buffer, pose, c, light, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, u0, v0, u1, vSide);
        });
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
        return new AABB(blockEntity.getBlockPos()).inflate(0.25, 0, 0.25).expandTowards(0, this.height - 1, 0);
    }
}
