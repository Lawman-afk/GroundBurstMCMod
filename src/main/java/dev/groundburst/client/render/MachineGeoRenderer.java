package dev.groundburst.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.groundburst.content.launch.MissileCarrier;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineTank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * GeckoLib machine renderer whose culling box covers the whole model, including the upper half of tall machines. A
 * model whose texture has a {@code _glowmask} draws it lit, whatever the light round it ({@link GlowLayer}). A machine
 * holding a missile ({@link MissileCarrier}) has it drawn from the missile's own model, at its bone.
 * <p>
 * The 1.21.1 backport's, for GeckoLib 4 (main's is GeckoLib 5's, built on its render passes): a machine's bones are
 * moved, scaled, turned and hidden as each is drawn ({@link #renderRecursively}), after GeckoLib has posed them from
 * the animation; the tanks are drawn after the model, already turned with the block.
 */
public class MachineGeoRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T> implements dev.groundburst.legacy.client.RenderBox<T> {
    private final int height;
    private final int reach;
    private final int depth;
    /** Whether its model stands on its base pointing up, turned to point the way its block faces (any of six). */
    private boolean standing;

    // What the block entity being drawn asks of its bones this frame (one is drawn at a time).
    private Map<String, float[]> scales = Map.of(), offsets = Map.of(), rotations = Map.of();
    private Set<String> hidden = Set.of();
    private Map<String, List<ShownMissile>> missiles = Map.of();

    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height) {
        this(context, type, height, 0);
    }

    /** {@code reach}: how many blocks the model extends sideways beyond its own (multiblock structures). */
    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height, int reach) {
        this(context, type, height, reach, reach > 0 ? 1 : 0);
    }

    /** {@code depth}: how many blocks the model reaches down below its controller (the Missile Silo's shaft). */
    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height, int reach, int depth) {
        // (As GeckoLib's own model, but one that draws the bones' poly meshes too: PolyGeoModel.)
        super(new PolyGeoModel<>(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)));
        this.height = height;
        this.reach = reach;
        this.depth = depth;
        addRenderLayer(new GlowLayer<>(this));
    }

    /** With a model of its own (a {@link VariantGeoModel}: textures, or models, chosen by the block entity). */
    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, GeoModel<T> model, int height, int reach) {
        super(model);
        this.height = height;
        this.reach = reach;
        this.depth = reach > 0 ? 1 : 0;
        addRenderLayer(new GlowLayer<>(this));
    }

    /**
     * Its model is built standing on its base, pointing up (a Warning Beacon): turned about the block's middle to point
     * the way the block faces, on a floor, a ceiling or a wall.
     */
    public MachineGeoRenderer<T> standing() {
        this.standing = true;
        return this;
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack pose) {
        if (!this.standing) {
            super.rotateBlock(facing, pose);
            return;
        }
        pose.translate(0, 0.5, 0);
        switch (facing) {
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case EAST -> pose.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> pose.mulPose(Axis.ZP.rotationDegrees(90));
            default -> { }
        }
        pose.translate(0, -0.5, 0);
    }

    // --- this frame's bones --------------------------------------------------------------------------------------------
    @Override
    public void actuallyRender(PoseStack poseStack, T animatable, BakedGeoModel model, @Nullable RenderType renderType, MultiBufferSource bufferSource,
            @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green,
            float blue, float alpha) {
        if (!isReRender) {
            frame(animatable, partialTick);
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green,
                blue, alpha);
    }

    private void frame(T animatable, float partialTick) {
        Map<String, float[]> offsets = Map.of();
        if (((Object) animatable) instanceof AnimatedMachine machine) {
            this.hidden = machine.hiddenBones();
            this.scales = machine.boneScales();
            this.rotations = machine.boneRotations(partialTick);
            offsets = machine.boneOffsets(partialTick);
        } else {
            this.hidden = Set.of();
            this.scales = Map.of();
            this.rotations = Map.of();
        }
        this.missiles = Map.of();
        if (((Object) animatable) instanceof MissileCarrier carrier && animatable.getLevel() != null) {
            String held = carrier.gripsFor();
            if (held != null) {
                offsets = withGrips(offsets, carrier.grips(), MissileShapes.get(held));
            }
            List<MissileCarrier.Shown> shownAll = carrier.shownMissiles();
            if (!shownAll.isEmpty()) {
                Map<String, List<ShownMissile>> byBone = new LinkedHashMap<>();
                MissileCarrier.Cradles cradles = carrier.cradles();
                for (MissileCarrier.Shown shown : shownAll) {
                    byBone.computeIfAbsent(shown.bone(), k -> new ArrayList<>()).add(resolve(shown));
                    MissileShapes.Shape shape = MissileShapes.get(shown.warhead());
                    if (shown.lying() && cradles != null && shape != null) {
                        offsets = new HashMap<>(offsets);
                        offsets.put(shown.bone(), new float[] {0, onCradles(cradles, shape) - cradles.axisY(), 0});
                    }
                }
                this.missiles = byBone;
            }
        }
        this.offsets = offsets;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue,
            float alpha) {
        String name = bone.getName();
        if (this.hidden.contains(name)) {
            return;
        }
        float[] s = this.scales.get(name);
        if (s != null) {
            dev.groundburst.legacy.client.Geo.updateScale(bone, s[0], s[1], s[2]);
        }
        float[] o = this.offsets.get(name);
        if (o != null) {
            dev.groundburst.legacy.client.Geo.updatePosition(bone, o[0], o[1], o[2]);
        }
        float[] r = this.rotations.get(name);
        if (r != null) {                                   // over its bind pose, as GeckoLib 5's snapshots are set
            var bind = bone.getInitialSnapshot();
            dev.groundburst.legacy.client.Geo.updateRotation(bone, bind.getRotX() + r[0], bind.getRotY() + r[1], bind.getRotZ() + r[2]);
        }
        List<ShownMissile> here = this.missiles.get(name);
        if (here != null && !isReRender) {
            drawMissiles(poseStack, bone, here, bufferSource, packedLight, animatable);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red,
                green, blue, alpha);
    }

    // --- missiles -----------------------------------------------------------------------------------------------------------
    /**
     * A missile to draw this frame: its looks as items, how it lies, and how far its item model is lifted to stand its
     * tail at the bone (MissileShapes; the plain missile's tail is at its y 0).
     */
    record ShownMissile(List<ItemStack> items, boolean lying, float turn, float lift) {}

    /** The Missile item for a look (its custom model data), one per look. */
    private static final Map<String, ItemStack> LOOKS = new HashMap<>();

    private static ShownMissile resolve(MissileCarrier.Shown shown) {
        List<ItemStack> items = new ArrayList<>();
        for (String look : shown.looks()) {
            items.add(LOOKS.computeIfAbsent(look, key -> {
                var s = new ItemStack(dev.groundburst.content.assembly.GBAssembly.MISSILE.get());
                dev.groundburst.legacy.component.Comps.set(s, dev.groundburst.legacy.component.DataComponents.CUSTOM_MODEL_DATA,
                        new dev.groundburst.legacy.component.CustomModelData(dev.groundburst.port.Looks.id(key)));
                return s;
            }));
        }
        var shape = MissileShapes.get(shown.warhead());
        return new ShownMissile(items, shown.lying(), shown.turn(), shape == null ? 0.5f : shape.lift());
    }

    /** Every missile at a bone, in the bone's frame as it is animated (a rack raised), from its pivot. */
    private static void drawMissiles(PoseStack pose, GeoBone bone, List<ShownMissile> shownHere, MultiBufferSource buffers, int light, BlockEntity be) {
        pose.pushPose();
        RenderUtils.translateMatrixToBone(pose, bone);
        RenderUtils.translateToPivotPoint(pose, bone);
        RenderUtils.rotateMatrixAroundBone(pose, bone);
        RenderUtils.scaleMatrixForBone(pose, bone);
        for (ShownMissile shown : shownHere) {
            pose.pushPose();
            pose.scale(4, 4, 4);                               // the item model is a quarter of the world's size
            if (shown.lying()) {
                pose.mulPose(Axis.XP.rotationDegrees(-90));    // its nose toward the model's front
            }
            pose.mulPose(Axis.YP.rotationDegrees(shown.turn()));
            pose.translate(0, shown.lift(), 0);                // drawn about its middle: its tail at the bone
            for (ItemStack item : shown.items()) {
                Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        be.getLevel(), 0);
            }
            pose.popPose();
        }
        pose.popPose();
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
    private static Map<String, float[]> withGrips(Map<String, float[]> offsets, List<MissileCarrier.Grip> grips, MissileShapes.@Nullable Shape shape) {
        if (grips.isEmpty() || shape == null) {
            return offsets;
        }
        Map<String, float[]> out = new HashMap<>(offsets);
        for (MissileCarrier.Grip grip : grips) {
            float h = shape.holdHeight(grip.hold());
            float r = shape.skinRadius(h);
            out.put(grip.mount(), new float[] {0, grip.tailY() + h - grip.restY(), 0});
            out.put(grip.reach(), new float[] {grip.clampX() - r, 0, 0});          // out from the axis (x as GeckoLib has it: negated)
        }
        return out;
    }

    // --- tanks ------------------------------------------------------------------------------------------------------------
    /** Draws each tank's fluid (its own still texture, translucent) after the model, turned with the block. */
    @Override
    public void postRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
            boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.postRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue,
                alpha);
        if (isReRender || !(((Object) animatable) instanceof MachineBlockEntity machine) || !machine.showsTanks() || animatable.getLevel() == null) {
            return;
        }
        VertexConsumer out = null;
        for (MachineBlockEntity.TankRender box : machine.tankRenders()) {
            MachineTank tank = machine.tank(box.tank());
            if (tank.amount() <= 0) {
                continue;
            }
            Fluid fluid = tank.fluid().getFluid();
            float fill = Math.min(1f, tank.amount() / (float) tank.capacity());
            int tint = FluidVisuals.tintInWorld(fluid, animatable.getLevel(), animatable.getBlockPos());
            int c = 0xD0000000 | (tint & 0xFFFFFF);
            if (out == null) {
                out = bufferSource.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
            }
            // The pose is the model's here: GeckoLib has put it at the block's middle and turned it with the block.
            PoseStack.Pose pose = poseStack.last();
            TextureAtlasSprite sprite = FluidVisuals.still(fluid);
            float x0 = box.x0() / 16f, x1 = box.x1() / 16f, z0 = box.z0() / 16f, z1 = box.z1() / 16f;   // (centred on the block)
            float y0 = box.y0() / 16f, y1 = (box.y0() + (box.y1() - box.y0()) * fill) / 16f;
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            quad(out, pose, c, packedLight, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1);
            // Sides tiled in one-block pieces so tall tanks keep the texture's scale.
            for (float ya = y0; ya < y1 - 1e-4f; ya += 1f) {
                float yb = Math.min(y1, ya + 1f);
                float vb = v0 + (v1 - v0) * (yb - ya);
                quad(out, pose, c, packedLight, 0, 0, -1, x1, yb, z0, x1, ya, z0, x0, ya, z0, x0, yb, z0, u0, v0, u1, vb);
                quad(out, pose, c, packedLight, 0, 0, 1, x0, yb, z1, x0, ya, z1, x1, ya, z1, x1, yb, z1, u0, v0, u1, vb);
                quad(out, pose, c, packedLight, -1, 0, 0, x0, yb, z0, x0, ya, z0, x0, ya, z1, x0, yb, z1, u0, v0, u1, vb);
                quad(out, pose, c, packedLight, 1, 0, 0, x1, yb, z1, x1, ya, z1, x1, ya, z0, x1, yb, z0, u0, v0, u1, vb);
            }
        }
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int color, int light, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        buffer.vertex(dev.groundburst.legacy.Verts.matrix(pose), ax, ay, az).color(color).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(dev.groundburst.legacy.Verts.normal(pose), nx, ny, nz).endVertex();
        buffer.vertex(dev.groundburst.legacy.Verts.matrix(pose), bx, by, bz).color(color).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(dev.groundburst.legacy.Verts.normal(pose), nx, ny, nz).endVertex();
        buffer.vertex(dev.groundburst.legacy.Verts.matrix(pose), cx, cy, cz).color(color).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(dev.groundburst.legacy.Verts.normal(pose), nx, ny, nz).endVertex();
        buffer.vertex(dev.groundburst.legacy.Verts.matrix(pose), dx, dy, dz).color(color).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(dev.groundburst.legacy.Verts.normal(pose), nx, ny, nz).endVertex();
    }

    // --- the glowmask -------------------------------------------------------------------------------------------------------
    /**
     * The model's {@code <texture>_glowmask.png} drawn lit over it, as GeckoLib 5 draws a glow mask: the mask is its own
     * picture of what glows, in the colours it glows (a beacon's red lens, its beams). GeckoLib 4's own glow layer reads
     * a mask the other way (as which of the texture's pixels glow, in the texture's colours: a dark unlit lens, a beam
     * clear in the texture), and cuts those pixels out of the texture, so it is not used. Only for models whose mask has
     * a lit pixel (some are empty, for a look with its lights out).
     */
    private static final class GlowLayer<A extends GeoAnimatable> extends AutoGlowingGeoLayer<A> {
        private static final Map<ResourceLocation, Boolean> GLOWS = new ConcurrentHashMap<>();

        GlowLayer(GeoRenderer<A> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, A animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource,
                @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            ResourceLocation texture = getTextureResource(animatable);
            if (GLOWS.computeIfAbsent(texture, GlowLayer::glows)) {
                RenderType glow = RenderType.entityTranslucentEmissive(software.bernie.geckolib.cache.texture.GeoAbstractTexture.appendToPath(texture, "_glowmask"));
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                        net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, packedOverlay, 1f, 1f, 1f, 1f);
            }
        }

        /** Whether {@code texture}'s glow mask is there and lights at least one pixel (GeckoLib 4 reads any non-zero one). */
        private static boolean glows(ResourceLocation texture) {
            ResourceLocation mask = software.bernie.geckolib.cache.texture.GeoAbstractTexture.appendToPath(texture, "_glowmask");
            var resource = Minecraft.getInstance().getResourceManager().getResource(mask);
            if (resource.isEmpty()) {
                return false;
            }
            try (java.io.InputStream in = resource.get().open(); com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        if (image.getPixelRGBA(x, y) != 0) {
                            return true;
                        }
                    }
                }
            } catch (java.io.IOException e) {
                return false;
            }
            return false;
        }
    }

    // --- how far and how much -------------------------------------------------------------------------------------------
    /**
     * How far off it is drawn: vanilla's 64 blocks for a machine of one block, further the further its model reaches
     * (a Search Radar's reflector, a silo door's rails: a landmark of a site should not pop in at 64 blocks).
     */
    @Override
    public int getViewDistance() {
        return Math.min(192, 64 + 16 * this.reach);
    }

    public AABB getRenderBoundingBox(T blockEntity) {
        // Structures may also reach below the controller (the accelerator ring's plinth, a block; the silo's shaft).
        return new AABB(blockEntity.getBlockPos()).inflate(0.25 + this.reach, 0, 0.25 + this.reach).expandTowards(0, this.height - 1, 0)
                .expandTowards(0, -this.depth, 0);
    }
}
