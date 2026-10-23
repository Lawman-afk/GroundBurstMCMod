package dev.groundzero.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundzero.client.render.FissionSections.Piece;
import dev.groundzero.content.machine.FissionCore;
import dev.groundzero.content.machine.FissionReactorBlockEntity;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineTank;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The formed Fission Reactor (DESIGN.md §7.2): while it is formed its walls' own blocks hide and this draws the vessel
 * over them, a section a cell ({@link FissionSections}): the base, pillars, riveted lead walls and head band, the roof
 * with a control-rod drive over every column that holds fuel (the rods rising as they are withdrawn), the glazing, the
 * ports' housings and the console. It fills the core round the Fuel Channels (ordinary blocks, seen through the
 * windows) with the coolant, from the floor up to the tank's level; while the core runs the water glows Cherenkov blue,
 * lit whatever the light round it. The panes go in one batch with the water, which is sorted far to near when drawn.
 */
public class FissionReactorRenderer implements BlockEntityRenderer<FissionReactorBlockEntity, FissionReactorRenderer.State> {
    /** Clear of the walls' inner faces (far enough not to fight them in the depth buffer from afar). */
    private static final float INSET = 0.01f;
    private static final int CHERENKOV = 0x5CD2FF;
    /** How far the drive rods sink as the control rods go in (blocks). */
    private static final float ROD_TRAVEL = 0.5f;
    /** The panes and the coolant: see-through, sorted far to near when drawn, and back faces culled (the coolant's far
     *  sides would otherwise lie on the walls' linings across the core and flicker against them). */
    private static final RenderType TRANSLUCENT = RenderTypes.itemTranslucent(TextureAtlas.LOCATION_BLOCKS);
    private static final Direction[] DIRECTIONS = Direction.values();

    public static class State extends BlockEntityRenderState {
        @Nullable Fluid fluid;
        float fill;
        int color;
        boolean glowing;
        /** The core's inside, relative to the controller block. */
        float x0, y0, z0, x1, y1, z1;
        /** The vessel's sections, and how far down the drive rods sit. */
        final List<Piece> pieces = new ArrayList<>();
        float rodDrop;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FissionReactorBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.fluid = null;
        state.pieces.clear();
        FissionCore core = be.core();
        if (core == null || !be.isFormed() || be.getLevel() == null) {
            return;
        }
        FissionSections.layout(core, be.getLevel(), be.getBlockPos(), state.pieces);
        state.rodDrop = (1 - be.flux()) * ROD_TRAVEL;
        MachineTank tank = be.tank(0);
        if (tank.amount() <= 0) {
            return;
        }
        state.fluid = tank.fluid().getFluid();
        state.fill = Math.min(1f, tank.amount() / (float) Math.max(1, tank.capacity()));
        state.glowing = be.getBlockState().hasProperty(MachineBlock.ACTIVE) && be.getBlockState().getValue(MachineBlock.ACTIVE);
        int tint = FluidVisuals.tintInWorld(state.fluid, be.getLevel(), be.getBlockPos()) & 0xFFFFFF;
        if (state.glowing) {
            tint = mix(tint, CHERENKOV, 0.85f);
        }
        state.color = 0xC8000000 | tint;
        AABB inside = core.bounds().deflate(1);
        double ox = be.getBlockPos().getX(), oy = be.getBlockPos().getY(), oz = be.getBlockPos().getZ();
        state.x0 = (float) (inside.minX - ox) + INSET;
        state.y0 = (float) (inside.minY - oy) + INSET;
        state.z0 = (float) (inside.minZ - oz) + INSET;
        state.x1 = (float) (inside.maxX - ox) - INSET;
        state.y1 = (float) (inside.maxY - oy) - INSET;
        state.z1 = (float) (inside.maxZ - oz) - INSET;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.pieces.isEmpty()) {
            return;
        }
        List<Piece> pieces = List.copyOf(state.pieces);                        // drawn later, after this state may be reused
        float drop = state.rodDrop;
        collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockItemSheet(), (pose, buffer) -> sections(pose, buffer, pieces, drop, false));
        Fluid fluid = state.fluid;
        TextureAtlasSprite sprite = fluid != null ? FluidVisuals.still(fluid) : null;
        float x0 = state.x0, x1 = state.x1, z0 = state.z0, z1 = state.z1, y0 = state.y0;
        float y1 = y0 + (state.y1 - state.y0) * state.fill;
        int c = state.color, light = state.glowing ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        collector.submitCustomGeometry(poseStack, TRANSLUCENT, (pose, buffer) -> {
            sections(pose, buffer, pieces, drop, true);
            if (sprite == null) {
                return;
            }
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            // The surface, a block-sized tile at a time.
            for (float xa = x0; xa < x1 - 1e-4f; xa = next(xa, x1)) {
                float xb = next(xa, x1);
                for (float za = z0; za < z1 - 1e-4f; za = next(za, z1)) {
                    float zb = next(za, z1);
                    float ua = lerp(u0, u1, frac(xa)), ub = lerp(u0, u1, frac(xa) + (xb - xa));
                    float va = lerp(v0, v1, frac(za)), vb = lerp(v0, v1, frac(za) + (zb - za));
                    quad(buffer, pose, c, light, 0, 1, 0, xa, y1, za, xa, y1, zb, xb, y1, zb, xb, y1, za, ua, va, ub, vb);
                }
            }
            // The four sides against the walls, tiled the same way.
            for (float ya = y0; ya < y1 - 1e-4f; ya = next(ya, y1)) {
                float yb = next(ya, y1);
                float va = lerp(v0, v1, 1 - (frac(ya) + (yb - ya))), vb = lerp(v0, v1, 1 - frac(ya));
                for (float a = x0; a < x1 - 1e-4f; a = next(a, x1)) {
                    float b = next(a, x1);
                    float ua = lerp(u0, u1, frac(a)), ub = lerp(u0, u1, frac(a) + (b - a));
                    quad(buffer, pose, c, light, 0, 0, -1, b, yb, z0, b, ya, z0, a, ya, z0, a, yb, z0, ua, va, ub, vb);
                    quad(buffer, pose, c, light, 0, 0, 1, a, yb, z1, a, ya, z1, b, ya, z1, b, yb, z1, ua, va, ub, vb);
                }
                for (float a = z0; a < z1 - 1e-4f; a = next(a, z1)) {
                    float b = next(a, z1);
                    float ua = lerp(u0, u1, frac(a)), ub = lerp(u0, u1, frac(a) + (b - a));
                    quad(buffer, pose, c, light, -1, 0, 0, x0, yb, a, x0, ya, a, x0, ya, b, x0, yb, b, ua, va, ub, vb);
                    quad(buffer, pose, c, light, 1, 0, 0, x1, yb, b, x1, ya, b, x1, ya, a, x1, yb, a, ua, va, ub, vb);
                }
            }
        });
    }

    /** The vessel's opaque sections, or its panes, each moved into its cell. */
    private static void sections(PoseStack.Pose base, VertexConsumer buffer, List<Piece> pieces, float drop, boolean glass) {
        PoseStack.Pose pose = base.copy();
        QuadInstance instance = new QuadInstance();
        for (Piece piece : pieces) {
            BlockStateModelPart part = piece.section().glass() == glass ? piece.section().part() : null;
            if (part == null) {
                continue;
            }
            pose.set(base);
            piece.place(pose, drop);
            instance.setLightCoords(piece.light());
            for (Direction d : DIRECTIONS) {
                for (BakedQuad quad : part.getQuads(d)) {
                    buffer.putBakedQuad(pose, quad, instance);
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                buffer.putBakedQuad(pose, quad, instance);
            }
        }
    }

    /** The next block boundary after {@code a}, but no further than {@code end}. */
    private static float next(float a, float end) {
        return Math.min(end, (float) Math.floor(a + 1e-4f) + 1f);
    }

    private static float frac(float a) {
        return a - (float) Math.floor(a + 1e-4f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.min(1f, Math.max(0f, t));
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
    public AABB getRenderBoundingBox(FissionReactorBlockEntity be) {
        FissionCore core = be.core();
        return core != null ? core.bounds().inflate(1) : new AABB(be.getBlockPos());
    }

    /** The vessel stands in for its walls' blocks, so it is seen from further off than most block entities. */
    @Override
    public int getViewDistance() {
        return 192;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
