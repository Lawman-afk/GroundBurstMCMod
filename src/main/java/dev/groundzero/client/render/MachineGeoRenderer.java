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

import dev.groundzero.content.launch.MissileCarrier;
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

/**
 * GeckoLib machine renderer whose culling box covers the whole model, including the upper half of tall machines. A
 * model whose texture has a {@code _glowmask} draws it lit, whatever the light round it ({@link GlowLayer}). A machine
 * holding a missile ({@link MissileCarrier}) has it drawn from the missile's own model ({@link MissileLayer}).
 */
public class MachineGeoRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T, BlockEntityRenderState> {
    private final int height;
    private final int reach;
    private final int depth;

    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height) {
        this(context, type, height, 0);
    }

    /** {@code reach}: how many blocks the model extends sideways beyond its own (multiblock structures). */
    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height, int reach) {
        this(context, type, height, reach, reach > 0 ? 1 : 0);
    }

    /** {@code depth}: how many blocks the model reaches down below its controller (the Missile Silo's shaft). */
    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height, int reach, int depth) {
        super(context, type);
        this.height = height;
        this.reach = reach;
        this.depth = depth;
        withGlow();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void withGlow() {
        // Raw: GeckoLib's layer wants a GeoRenderState, which this renderer's state only becomes at runtime (see below).
        withRenderLayer((com.geckolib.renderer.layer.GeoRenderLayer) new GlowLayer(this));
        withRenderLayer((com.geckolib.renderer.layer.GeoRenderLayer) new MissileLayer(this));
    }

    /**
     * The missile a launcher, an assembler or the hall holds ({@link MissileCarrier}): drawn from its own item model (one
     * per warhead, a quarter of the world's size) at the model's bone at its
     * tail, so the machines' models carry no missile of their own. The item states are resolved when the frame's state is
     * taken.
     */
    private static final class MissileLayer<A extends GeoAnimatable, O, R extends GeoRenderState> extends com.geckolib.renderer.layer.GeoRenderLayer<A, O, R> {
        MissileLayer(com.geckolib.renderer.base.GeoRenderer<A, O, R> renderer) {
            super(renderer);
        }

        @Override
        public void addPerBoneRender(RenderPassInfo<R> pass, java.util.function.BiConsumer<com.geckolib.cache.model.GeoBone, com.geckolib.renderer.base.PerBoneRender<R>> consumer) {
            List<ShownMissile> all = pass.getGeckolibData(MISSILE);
            if (all == null || all.isEmpty() || !pass.willRender()) {
                return;
            }
            // One render per bone, drawing every missile at it (in the bone's frame, as it is animated: a rack raised).
            java.util.Map<String, List<ShownMissile>> byBone = new java.util.LinkedHashMap<>();
            for (ShownMissile s : all) {
                byBone.computeIfAbsent(s.bone(), k -> new ArrayList<>()).add(s);
            }
            byBone.forEach((name, shownHere) -> pass.model().getBone(name).ifPresent(bone -> consumer.accept(bone, (info, b, collector) -> {
                for (ShownMissile shown : shownHere) {
                    PoseStack pose = info.poseStack();
                    pose.pushPose();
                    pose.scale(4, 4, 4);                               // the item model is a quarter of the world's size
                    if (shown.lying()) {
                        pose.rotate(com.mojang.math.Axis.XP.rotationDegrees(-90));      // its nose toward the model's front
                    }
                    pose.rotate(com.mojang.math.Axis.YP.rotationDegrees(shown.turn()));
                    pose.translate(0, shown.lift(), 0);                // drawn about its middle: its tail at the bone
                    for (net.minecraft.client.renderer.item.ItemStackRenderState item : shown.items()) {
                        item.submit(pose, collector, info.packedLight(), OverlayTexture.NO_OVERLAY, 0);
                    }
                    pose.popPose();
                }
            })));
        }
    }

    /**
     * A missile to draw this frame: its looks resolved to item states, where and how it lies, and how far its item model
     * is lifted to stand its tail at the bone (MissileShapes; the plain missile's tail is at its y 0).
     */
    record ShownMissile(List<net.minecraft.client.renderer.item.ItemStackRenderState> items, String bone, boolean lying, float turn, float lift) {}

    /** The Missile item for a look (its custom model data), one per look. */
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> LOOKS = new java.util.HashMap<>();

    private static ShownMissile resolve(MissileCarrier.Shown shown, net.minecraft.world.level.Level level) {
        var resolver = net.minecraft.client.Minecraft.getInstance().getItemModelResolver();
        List<net.minecraft.client.renderer.item.ItemStackRenderState> items = new ArrayList<>();
        for (String look : shown.looks()) {
            net.minecraft.world.item.ItemStack stack = LOOKS.computeIfAbsent(look, key -> {
                var s = new net.minecraft.world.item.ItemStack(dev.groundzero.content.assembly.GZAssembly.MISSILE.get());
                s.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                        List.of(), List.of(), List.of(key), List.of()));
                return s;
            });
            var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
            resolver.updateForTopItem(state, stack, net.minecraft.world.item.ItemDisplayContext.NONE, level instanceof net.minecraft.client.multiplayer.ClientLevel cl ? cl : null, null, 0);
            items.add(state);
        }
        var shape = MissileShapes.get(shown.warhead());
        return new ShownMissile(items, shown.bone(), shown.lying(), shown.turn(), shape == null ? 0.5f : shape.lift());
    }

    /**
     * GeckoLib's auto-glow layer (the model's {@code <texture>_glowmask.png} drawn lit), only for models that have one:
     * GeckoLib does not check, and a missing one would draw as the missing texture. (Until this, no renderer drew the
     * glowmasks the machine models were made with.)
     */
    private static final class GlowLayer<A extends GeoAnimatable, O, R extends com.geckolib.renderer.base.GeoRenderState>
            extends com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<A, O, R> {
        private static final java.util.Map<net.minecraft.resources.Identifier, Boolean> EXISTS = new java.util.concurrent.ConcurrentHashMap<>();

        GlowLayer(com.geckolib.renderer.base.GeoRenderer<A, O, R> renderer) {
            super(renderer);
        }

        @Override
        public void submitRenderTask(RenderPassInfo<R> pass, SubmitNodeCollector collector) {
            net.minecraft.resources.Identifier glow = getTextureResource(pass.renderState());
            if (EXISTS.computeIfAbsent(glow, id -> net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id).isPresent())) {
                super.submitRenderTask(pass, collector);
            }
        }
    }

    private static final DataTicket<Set<String>> HIDDEN_BONES = DataTicket.create("groundzero_hidden_bones", new TypeToken<Set<String>>() {});
    private static final DataTicket<java.util.Map<String, float[]>> BONE_SCALES = DataTicket.create("groundzero_bone_scales",
            new TypeToken<java.util.Map<String, float[]>>() {});
    private static final DataTicket<java.util.Map<String, float[]>> BONE_OFFSETS = DataTicket.create("groundzero_bone_offsets",
            new TypeToken<java.util.Map<String, float[]>>() {});
    private static final DataTicket<List<ShownMissile>> MISSILE = DataTicket.create("groundzero_missile", new TypeToken<List<ShownMissile>>() {});

    @Override
    public void addRenderData(T animatable, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        java.util.Map<String, float[]> offsets = java.util.Map.of();
        if (animatable instanceof AnimatedMachine machine) {
            ((GeoRenderState) renderState).addGeckolibData(HIDDEN_BONES, machine.hiddenBones());
            ((GeoRenderState) renderState).addGeckolibData(BONE_SCALES, machine.boneScales());
            offsets = machine.boneOffsets(partialTick);
        }
        if (animatable instanceof MissileCarrier carrier && animatable.getLevel() != null) {
            String held = carrier.gripsFor();
            if (held != null) {
                offsets = withGrips(offsets, carrier.grips(), MissileShapes.get(held));
            }
            List<MissileCarrier.Shown> shownAll = carrier.shownMissiles();
            if (!shownAll.isEmpty()) {
                List<ShownMissile> resolved = new ArrayList<>(shownAll.size());
                MissileCarrier.Cradles cradles = carrier.cradles();
                for (MissileCarrier.Shown shown : shownAll) {
                    resolved.add(resolve(shown, animatable.getLevel()));
                    MissileShapes.Shape shape = MissileShapes.get(shown.warhead());
                    if (shown.lying() && cradles != null && shape != null) {
                        offsets = new java.util.HashMap<>(offsets);
                        offsets.put(shown.bone(), new float[] {0, onCradles(cradles, shape) - cradles.axisY(), 0});
                    }
                }
                ((GeoRenderState) renderState).addGeckolibData(MISSILE, resolved);
            }
        }
        ((GeoRenderState) renderState).addGeckolibData(BONE_OFFSETS, offsets);
        captureTank(animatable, (GeoRenderState) renderState);
    }

    /** Where a lying missile's axis is, resting on its cradles' saddles by its own girth where they hold it (model px). */
    private static float onCradles(MissileCarrier.Cradles cradles, MissileShapes.Shape shape) {
        float r = 0;
        for (float h : cradles.at()) {
            r = Math.max(r, shape.skinRadius(h));
        }
        return cradles.topY() + r;
    }

    /**
     * The arms that hold a standing missile, fitted to it (DESIGN.md §10.4): each arm's collar slides to its hold on the
     * missile and its clamp moves in or out to the missile's skin there, the inner beam sliding in its sleeve.
     */
    private static java.util.Map<String, float[]> withGrips(java.util.Map<String, float[]> offsets, List<MissileCarrier.Grip> grips,
            MissileShapes.@Nullable Shape shape) {
        if (grips.isEmpty() || shape == null) {
            return offsets;
        }
        java.util.Map<String, float[]> out = new java.util.HashMap<>(offsets);
        for (MissileCarrier.Grip grip : grips) {
            float h = shape.holdHeight(grip.hold());
            float r = shape.skinRadius(h);
            out.put(grip.mount(), new float[] {0, grip.tailY() + h - grip.restY(), 0});
            out.put(grip.reach(), new float[] {grip.clampX() - r, 0, 0});          // out from the axis (x as GeckoLib has it: negated)
        }
        return out;
    }

    // Raw type: GeckoLib injects GeoRenderState into BlockEntityRenderState at runtime, which javac cannot see here.
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
        java.util.Map<String, float[]> scales = (java.util.Map<String, float[]>) renderPassInfo.getGeckolibData(BONE_SCALES);
        if (scales != null) {
            scales.forEach((bone, s) -> snapshots.ifPresent(bone, snapshot -> snapshot.setScale(s[0], s[1], s[2])));
        }
        java.util.Map<String, float[]> offsets = (java.util.Map<String, float[]>) renderPassInfo.getGeckolibData(BONE_OFFSETS);
        if (offsets != null) {
            offsets.forEach((bone, o) -> snapshots.ifPresent(bone, snapshot -> snapshot.setTranslation(o[0], o[1], o[2])));
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
        // Structures may also reach below the controller (the accelerator ring's plinth, a block; the silo's shaft).
        return new AABB(blockEntity.getBlockPos()).inflate(0.25 + this.reach, 0, 0.25 + this.reach).expandTowards(0, this.height - 1, 0)
                .expandTowards(0, -this.depth, 0);
    }
}
