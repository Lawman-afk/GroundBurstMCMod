package dev.groundburst.client.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.HoloProjectorItem;
import dev.groundburst.content.multiblock.MultiblockClientHooks;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Multiblock ghost preview (DESIGN.md §23.1). Shows a translucent, gently pulsing copy of each missing block and a
 * red outline round each wrong one, for controllers the player toggled by right-clicking and, while a Holo-Projector
 * is held, for every unfinished structure in range. The projector steps through layers (all, then bottom-up).
 */
public final class MultiblockPreview {
    private static final ContextKey<List<Ghost>> GHOSTS = new ContextKey<>(GroundBurst.id("multiblock_preview"));
    private static final String LAYER_ALL = GBLang.add(GroundBurst.MODID + ".holo_projector.layer_all", "Projecting all layers");
    private static final String LAYER_ONE = GBLang.add(GroundBurst.MODID + ".holo_projector.layer", "Projecting layer %s of %s");
    private static final String NOTHING = GBLang.add(GroundBurst.MODID + ".holo_projector.nothing", "No unfinished structure in range");
    private static final int WRONG_COLOR = 0xFFFF4030;
    /** Ghosts: see-through, back faces culled (an open model such as a Fuel Channel's frame reads as its shape). */
    private static final RenderType GHOST = RenderTypes.itemTranslucent(TextureAtlas.LOCATION_BLOCKS);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final float INSET = 0.02f;

    private static final Set<BlockPos> PINNED = new HashSet<>();
    private static int layer;            // 0 = all layers, n = layer n (1-based)
    private static int rangeLayers;      // most layers among the structures shown last frame

    /** One block to draw: a ghost of {@code state}, or (if {@code wrong}) an outline. */
    private record Ghost(BlockPos pos, BlockState state, boolean wrong) {}

    private MultiblockPreview() {}

    public static void register() {
        MultiblockClientHooks.togglePreview = MultiblockPreview::toggle;
        MultiblockClientHooks.stepLayer = MultiblockPreview::stepLayer;
        NeoForge.EVENT_BUS.addListener(MultiblockPreview::extract);
        NeoForge.EVENT_BUS.addListener(MultiblockPreview::submit);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            PINNED.clear();
            layer = 0;
        });
    }

    private static void toggle(BlockPos pos) {
        BlockPos key = pos.immutable();
        if (!PINNED.remove(key)) {
            PINNED.add(key);
        }
    }

    private static void stepLayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (rangeLayers == 0) {
            layer = 0;
            player.sendOverlayMessage(Component.translatable(NOTHING));
            return;
        }
        layer = (layer + 1) % (rangeLayers + 1);
        player.sendOverlayMessage(layer == 0 ? Component.translatable(LAYER_ALL) : Component.translatable(LAYER_ONE, layer, rangeLayers));
    }

    private static boolean holdingProjector(LocalPlayer player) {
        return player.getMainHandItem().is(GBMultiblocks.HOLO_PROJECTOR.get()) || player.getOffhandItem().is(GBMultiblocks.HOLO_PROJECTOR.get());
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        List<MultiblockController> shown = new ArrayList<>();
        PINNED.removeIf(pos -> !(level.getBlockEntity(pos) instanceof MultiblockController c) || c.isFormed());
        for (BlockPos pos : PINNED) {
            shown.add((MultiblockController) level.getBlockEntity(pos));
        }
        boolean projecting = holdingProjector(player);
        if (projecting) {
            ChunkPos center = ChunkPos.containing(player.blockPosition());
            int r = HoloProjectorItem.RANGE / 16 + 1;
            double range2 = (double) HoloProjectorItem.RANGE * HoloProjectorItem.RANGE;
            for (int cx = -r; cx <= r; cx++) {
                for (int cz = -r; cz <= r; cz++) {
                    if (!level.hasChunk(center.x() + cx, center.z() + cz)) {
                        continue;
                    }
                    for (BlockEntity be : level.getChunk(center.x() + cx, center.z() + cz).getBlockEntities().values()) {
                        if (be instanceof MultiblockController c && !c.isFormed() && !PINNED.contains(be.getBlockPos())
                                && be.getBlockPos().distToCenterSqr(player.position()) <= range2) {
                            shown.add(c);
                        }
                    }
                }
            }
        }
        rangeLayers = 0;
        List<Ghost> ghosts = new ArrayList<>();
        for (MultiblockController c : shown) {
            int layers = c.previewLayers();
            rangeLayers = Math.max(rangeLayers, layers);
            int only = projecting && layer > 0 ? Math.min(layer, layers) - 1 : -1;
            for (MultiblockDefinition.Mismatch m : c.mismatches(level)) {
                if (only >= 0 && m.layer() != only) {
                    continue;
                }
                if (!m.empty()) {
                    ghosts.add(new Ghost(m.pos(), level.getBlockState(m.pos()), true));
                } else if (m.key() != '.') {
                    ghosts.add(new Ghost(m.pos(), c.ghostState(m), false));
                }
            }
        }
        if (!ghosts.isEmpty()) {
            event.getRenderState().setRenderData(GHOSTS, ghosts);
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        List<Ghost> ghosts = event.getLevelRenderState().getRenderData(GHOSTS);
        if (ghosts == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        float lineWidth = mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        // Pulse between faint and half-visible over two seconds, tinted a cool hologram blue.
        double t = (System.currentTimeMillis() % 2000L) / 2000.0;
        int alpha = (int) (90 + 70 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2)));
        int tint = alpha << 24 | 0xC8E6FF;
        for (Ghost ghost : ghosts) {
            Vec3 offset = Vec3.atLowerCornerOf(ghost.pos()).subtract(camera);
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            if (ghost.wrong()) {
                event.getSubmitNodeCollector().submitShapeOutline(poseStack, Shapes.block(), RenderTypes.lines(), WRONG_COLOR, lineWidth * 2, false);
            } else {
                BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(ghost.state());
                List<BlockStateModelPart> parts = new ArrayList<>();
                model.collectParts(RandomSource.create(ghost.pos().asLong()), parts);
                TextureAtlasSprite sprite = model.particleMaterial().sprite();
                event.getSubmitNodeCollector().submitCustomGeometry(poseStack, GHOST, (pose, buffer) -> ghost(buffer, pose, parts, sprite, tint));
            }
            poseStack.popPose();
        }
    }

    /** A block-sized cube, inset slightly so it never fights the faces of real neighbours. Vanilla face winding. */
    /**
     * A ghost of the block's own model, tinted, a little smaller than the block (clear of its neighbours' faces); a plain
     * cube in the block's particle texture if its model draws nothing.
     */
    private static void ghost(VertexConsumer buffer, PoseStack.Pose base, List<BlockStateModelPart> parts, TextureAtlasSprite sprite, int tint) {
        PoseStack.Pose pose = base.copy();
        float k = 1 - 2 * INSET;
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.scale(k, k, k);
        pose.translate(-0.5f, -0.5f, -0.5f);
        QuadInstance instance = new QuadInstance();
        instance.setColor(tint);
        instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        boolean drawn = false;
        for (BlockStateModelPart part : parts) {
            for (Direction d : DIRECTIONS) {
                for (BakedQuad quad : part.getQuads(d)) {
                    buffer.putBakedQuad(pose, quad, instance);
                    drawn = true;
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                buffer.putBakedQuad(pose, quad, instance);
                drawn = true;
            }
        }
        if (!drawn) {
            cube(buffer, base, sprite, tint);
        }
    }

    private static void cube(VertexConsumer b, PoseStack.Pose pose, TextureAtlasSprite s, int color) {
        float a = INSET, z = 1 - INSET;
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        quad(b, pose, color, 0, 1, 0, a, z, a, a, z, z, z, z, z, z, z, a, u0, v0, u1, v1);
        quad(b, pose, color, 0, -1, 0, a, a, z, a, a, a, z, a, a, z, a, z, u0, v0, u1, v1);
        quad(b, pose, color, 0, 0, -1, z, z, a, z, a, a, a, a, a, a, z, a, u0, v0, u1, v1);
        quad(b, pose, color, 0, 0, 1, a, z, z, a, a, z, z, a, z, z, z, z, u0, v0, u1, v1);
        quad(b, pose, color, -1, 0, 0, a, z, a, a, a, a, a, a, z, a, z, z, u0, v0, u1, v1);
        quad(b, pose, color, 1, 0, 0, z, z, z, z, a, z, z, a, a, z, z, a, u0, v0, u1, v1);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int color, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        int light = LightCoordsUtil.FULL_BRIGHT;
        buffer.addVertex(pose, ax, ay, az).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, bx, by, bz).setColor(color).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, cx, cy, cz).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, dx, dy, dz).setColor(color).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
