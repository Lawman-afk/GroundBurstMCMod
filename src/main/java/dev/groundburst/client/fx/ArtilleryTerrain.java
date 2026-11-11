package dev.groundburst.client.fx;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.content.artillery.ArtilleryPayloads;
import dev.groundburst.content.artillery.GunBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import dev.groundburst.port.render.SubmitCustomGeometryEvent;
import dev.groundburst.legacy.net.PacketDistributor;

/**
 * The far ground's map (DESIGN.md §20.1): where a gun reaches further than its crewman's game draws, its strategic view
 * asks the server for the ground round its aim, a tile at a time ({@link ArtilleryPayloads#TILE} blocks square, sampled
 * every {@link ArtilleryPayloads#STEP}), and draws it as solid ground where the game draws none: each sample a column top
 * in its map colour, its sides where the ground steps. That is wherever the game has not loaded the ground, and also the
 * outer ring of what it has, which it never draws (a chunk is drawn once its neighbours are in), nor any loaded ground
 * beyond its render distance from the camera. Seen from above it reads as the land is, if not as a loaded world does (no
 * mobs, no block detail). The aim's height over it comes from it too.
 */
final class ArtilleryTerrain {
    private static final int T = ArtilleryPayloads.TILE, S = ArtilleryPayloads.STEP, N = ArtilleryPayloads.SAMPLES;
    /** How many tiles are kept (the furthest from the aim go first), how far round the aim they are wanted. */
    private static final int KEEP = 16, AROUND = 112;
    /** How old a tile may get before it is asked for again (so craters and new buildings show), ticks. */
    private static final int FRESH = 600;

    private record Tile(int ox, int oz, short[] heights, int[] colours, long got) {
        double height(int x, int z) {
            int i = (x - this.ox) / S, j = (z - this.oz) / S;
            short h = this.heights[Mth.clamp(j, 0, N - 1) * N + Mth.clamp(i, 0, N - 1)];
            return h == ArtilleryPayloads.UNKNOWN ? Double.NaN : h;
        }
    }

    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static final Map<Long, Long> ASKED = new HashMap<>();

    private ArtilleryTerrain() {}

    static void register() {
        ArtilleryPayloads.terrainHook = t -> {
            long key = key(t.ox(), t.oz());
            var mc = Minecraft.getInstance();
            TILES.put(key, new Tile(t.ox(), t.oz(), t.heights(), soften(t.colours()), mc.level == null ? 0 : mc.level.getGameTime()));
            ASKED.remove(key);
            if (TILES.size() > KEEP) {
                Vec3 c = ArtilleryClient.cursor();
                TILES.keySet().stream().max(java.util.Comparator.comparingDouble(k -> dist(TILES.get(k), c))).ifPresent(TILES::remove);
            }
        };
    }

    /**
     * Map colours are a map's vivid palette: softened toward the ground as the game draws it (a quarter of the way to grey,
     * a little darker), so the map meets the near ground without a seam of brighter colour (sand: 240,229,167 against
     * 219,212,173 drawn; softened, 222,214,171).
     */
    private static int[] soften(int[] colours) {
        int[] out = new int[colours.length];
        for (int i = 0; i < colours.length; i++) {
            int c = colours[i];
            float r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, l = 0.299f * r + 0.587f * g + 0.114f * b;
            out[i] = Math.round((r + (l - r) * 0.25f) * 0.94f) << 16 | Math.round((g + (l - g) * 0.25f) * 0.94f) << 8
                    | Math.round((b + (l - b) * 0.25f) * 0.94f);
        }
        return out;
    }

    static void clear() {
        TILES.clear();
        ASKED.clear();
    }

    private static long key(int ox, int oz) {
        return ((long) Math.floorDiv(ox, T) << 32) ^ (Math.floorDiv(oz, T) & 0xFFFFFFFFL);
    }

    private static double dist(Tile t, Vec3 c) {
        return Math.hypot(t.ox() + T / 2.0 - c.x, t.oz() + T / 2.0 - c.z);
    }

    /** The ground's top at (x, z) from the map (NaN: not mapped). */
    static double height(double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        Tile t = TILES.get(key(Math.floorDiv(bx, T) * T, Math.floorDiv(bz, T) * T));
        return t == null ? Double.NaN : t.height(bx, bz);
    }

    /** When a tile was last asked for, and where the aim was a moment ago (it asks only once the aim settles). */
    private static long lastAsked;
    private static Vec3 settling = Vec3.ZERO;
    private static int settled;

    /**
     * Each tick of the strategic view: asks for the tiles round the aim that the game has not loaded, or had last
     * {@link #FRESH} ticks ago: one every few ticks, and only once the aim has come to rest (each tile has its 64 chunks
     * loaded, or made, on the server: an aim swept across the map would ask for dozens; the one in hand is drawn until
     * the new one comes).
     */
    static void tick(Minecraft mc, GunBlockEntity gun) {
        BlockPos at = ArtilleryClient.gunPos();
        if (!ArtilleryClient.strategic() || mc.level == null || at == null) {
            return;
        }
        Vec3 c = ArtilleryClient.cursor();
        long now = mc.level.getGameTime();
        if (Math.hypot(c.x - settling.x, c.z - settling.z) > 24) {
            settling = c;
            settled = 0;
        } else {
            settled++;
        }
        if (settled < 6 || now - lastAsked < 4) {
            return;
        }
        Vec3 base = gun.socket();
        int[] best = null;
        double nearest = Double.MAX_VALUE;
        for (int x = Math.floorDiv(Mth.floor(c.x) - AROUND, T) * T; x <= c.x + AROUND; x += T) {
            for (int z = Math.floorDiv(Mth.floor(c.z) - AROUND, T) * T; z <= c.z + AROUND; z += T) {
                if (Math.hypot(x + T / 2.0 - base.x, z + T / 2.0 - base.z) > gun.spec.maxRange() + T || allDrawn(mc, x, z)) {
                    continue;
                }
                long key = key(x, z);
                Long asked = ASKED.get(key);
                Tile had = TILES.get(key);
                if (had != null && now - had.got() < FRESH || asked != null && now - asked < 400) {
                    continue;
                }
                double d = Math.hypot(x + T / 2.0 - c.x, z + T / 2.0 - c.z);
                if (d < nearest) {
                    nearest = d;
                    best = new int[] {x, z};
                }
            }
        }
        if (best != null) {                                    // the nearest to the aim first (the gun queues them)
            ASKED.put(key(best[0], best[1]), now);
            lastAsked = now;
            PacketDistributor.sendToServer(new ArtilleryPayloads.TerrainRequest(at, best[0], best[1]));
        }
    }

    /** Chunks along a tile's side. */
    private static final int CHUNKS = T / 16;

    /**
     * Whether the game draws the ground at (x, z): its chunk loaded and the section holding its surface built and on
     * show (the renderer's own test; false for the loaded ring it never draws, and for anything past its reach).
     */
    private static boolean drawn(Minecraft mc, int x, int z, int surfaceY) {
        ClientLevel level = mc.level;
        if (level == null || !level.isLoaded(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return false;
        }
        int y = surfaceY == Integer.MIN_VALUE ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) : surfaceY;
        return mc.levelRenderer.isChunkCompiled(new BlockPos(x, Math.max(level.getMinBuildHeight(), y - 1), z));
    }

    /** Whether the game draws all of a tile's ground (each chunk's middle), so it needs no map. */
    private static boolean allDrawn(Minecraft mc, int ox, int oz) {
        for (int i = 0; i < CHUNKS; i++) {
            for (int j = 0; j < CHUNKS; j++) {
                if (!drawn(mc, ox + i * 16 + 8, oz + j * 16 + 8, Integer.MIN_VALUE)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Which of a tile's chunks the game draws this frame (tested once a chunk, at its middle's mapped height). */
    private static boolean[] drawnChunks(Minecraft mc, Tile t) {
        boolean[] out = new boolean[CHUNKS * CHUNKS];
        int per = 16 / S;
        for (int i = 0; i < CHUNKS; i++) {
            for (int j = 0; j < CHUNKS; j++) {
                short h = t.heights()[Math.min(N - 1, j * per + per / 2) * N + Math.min(N - 1, i * per + per / 2)];
                out[j * CHUNKS + i] = drawn(mc, t.ox() + i * 16 + 8, t.oz() + j * 16 + 8, h == ArtilleryPayloads.UNKNOWN ? Integer.MIN_VALUE : h);
            }
        }
        return out;
    }

    /**
     * How many samples a side one cell of a tile takes, drawn from {@code distance} blocks off: every sample near the
     * camera, coarser further off (a strategic view looks down on a dozen tiles at once; drawn whole, every frame, they
     * cost half a million vertices). A cell never runs over a chunk's edge, so a drawn chunk still hides it.
     */
    static int cellSamples(double distance) {
        return distance < 96 ? 1 : distance < 192 ? 2 : distance < 384 ? 4 : 8;
    }

    /** The mapped tiles drawn as solid ground where the game draws none of its own. */
    static void submit(SubmitCustomGeometryEvent event, ClientLevel level, Vec3 camera) {
        if (TILES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderType.entitySolid(ArtilleryClient.RETICLE), (pose, b) -> {
            for (Tile t : TILES.values()) {
                boolean[] shown = drawnChunks(mc, t);
                boolean all = true;
                for (boolean d : shown) {
                    all &= d;
                }
                if (all) {
                    continue;
                }
                double mid = t.height(t.ox() + T / 2, t.oz() + T / 2);
                double dy = Double.isNaN(mid) ? 0 : camera.y - mid;
                int k = cellSamples(Math.sqrt(Math.pow(t.ox() + T / 2.0 - camera.x, 2) + Math.pow(t.oz() + T / 2.0 - camera.z, 2) + dy * dy));
                int cells = N / k, size = S * k;
                for (int j = 0; j < cells; j++) {
                    for (int i = 0; i < cells; i++) {
                        int x = t.ox() + i * size, z = t.oz() + j * size;
                        if (shown[((j * size) >> 4) * CHUNKS + ((i * size) >> 4)]) {
                            continue;
                        }
                        int h = cellHeight(t, i, j, k);
                        if (h == ArtilleryPayloads.UNKNOWN) {
                            continue;
                        }
                        int col = 0xFF000000 | cellColour(t, i, j, k);
                        top(b, pose, x, h, z, size, camera, col);
                        // Its sides where the ground beside it is lower (east and south; west and north from those).
                        side(b, pose, t, i + 1, j, k, x + size, z, x + size, z + size, h, camera, col, 1, 0);
                        side(b, pose, t, i - 1, j, k, x, z + size, x, z, h, camera, col, -1, 0);
                        side(b, pose, t, i, j + 1, k, x + size, z + size, x, z + size, h, camera, col, 0, 1);
                        side(b, pose, t, i, j - 1, k, x, z, x + size, z, h, camera, col, 0, -1);
                    }
                }
            }
        });
    }

    /** A cell's height: its middle sample's (UNKNOWN if that one is). */
    private static int cellHeight(Tile t, int i, int j, int k) {
        int si = Math.min(N - 1, i * k + k / 2), sj = Math.min(N - 1, j * k + k / 2);
        return t.heights()[sj * N + si];
    }

    private static int cellColour(Tile t, int i, int j, int k) {
        int si = Math.min(N - 1, i * k + k / 2), sj = Math.min(N - 1, j * k + k / 2);
        return t.colours()[sj * N + si];
    }

    private static void top(VertexConsumer b, PoseStack.Pose pose, int x, int y, int z, int size, Vec3 camera, int col) {
        v(b, pose, x, y, z, camera, col, 0, 1, 0);
        v(b, pose, x, y, z + size, camera, col, 0, 1, 0);
        v(b, pose, x + size, y, z + size, camera, col, 0, 1, 0);
        v(b, pose, x + size, y, z, camera, col, 0, 1, 0);
    }

    /** The wall from this cell's top down to its neighbour's (cell {@code ni}, {@code nj}) where that is lower. */
    private static void side(VertexConsumer b, PoseStack.Pose pose, Tile t, int ni, int nj, int k, int x0, int z0, int x1, int z1, int h, Vec3 camera,
            int col, int nx, int nz) {
        int cells = N / k;
        int low;
        if (ni < 0 || nj < 0 || ni >= cells || nj >= cells) {
            low = h - 2 * k;                                   // a skirt at the tile's edge, deeper where its cells are coarser
        } else {
            int n = cellHeight(t, ni, nj, k);
            low = n == ArtilleryPayloads.UNKNOWN ? h - 1 : n;
        }
        if (low >= h) {
            return;
        }
        int dark = darker(col);
        v(b, pose, x0, h, z0, camera, dark, nx, 0, nz);
        v(b, pose, x1, h, z1, camera, dark, nx, 0, nz);
        v(b, pose, x1, low, z1, camera, dark, nx, 0, nz);
        v(b, pose, x0, low, z0, camera, dark, nx, 0, nz);
    }

    private static int darker(int col) {
        int r = (col >> 16 & 255) * 3 / 4, g = (col >> 8 & 255) * 3 / 4, bl = (col & 255) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static void v(VertexConsumer b, PoseStack.Pose pose, double x, double y, double z, Vec3 camera, int col, float nx, float ny, float nz) {
        b.vertex(dev.groundburst.legacy.Verts.matrix(pose), (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)).color(col).uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(dev.groundburst.legacy.Verts.normal(pose), nx, ny, nz).endVertex();
    }
}
