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
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The far ground's map (DESIGN.md §20.1): where a gun reaches further than its crewman's game has loaded, its strategic
 * view asks the server for the ground round its aim, a tile at a time ({@link ArtilleryPayloads#TILE} blocks square,
 * sampled every {@link ArtilleryPayloads#STEP}), and draws it as solid ground where nothing else is: each sample a
 * column top in its map colour, its sides where the ground steps. Seen from above it reads as the land is, if not as a
 * loaded world does (no mobs, no block detail). The aim's height over it comes from it too.
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
            TILES.put(key, new Tile(t.ox(), t.oz(), t.heights(), t.colours(), mc.level == null ? 0 : mc.level.getGameTime()));
            ASKED.remove(key);
            if (TILES.size() > KEEP) {
                Vec3 c = ArtilleryClient.cursor();
                TILES.keySet().stream().max(java.util.Comparator.comparingDouble(k -> dist(TILES.get(k), c))).ifPresent(TILES::remove);
            }
        };
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

    /**
     * Each tick of the strategic view: asks for the tiles round the aim that the game has not loaded, or had last
     * {@link #FRESH} ticks ago (one a tick; the one in hand is drawn until the new one comes).
     */
    static void tick(Minecraft mc, GunBlockEntity gun) {
        BlockPos at = ArtilleryClient.gunPos();
        if (!ArtilleryClient.strategic() || mc.level == null || at == null) {
            return;
        }
        Vec3 c = ArtilleryClient.cursor();
        long now = mc.level.getGameTime();
        Vec3 base = gun.socket();
        int[] best = null;
        double nearest = Double.MAX_VALUE;
        for (int x = Math.floorDiv(Mth.floor(c.x) - AROUND, T) * T; x <= c.x + AROUND; x += T) {
            for (int z = Math.floorDiv(Mth.floor(c.z) - AROUND, T) * T; z <= c.z + AROUND; z += T) {
                if (Math.hypot(x + T / 2.0 - base.x, z + T / 2.0 - base.z) > gun.spec.maxRange() + T || loaded(mc.level, x, z)) {
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
        if (best != null) {                                    // the nearest to the aim first, one a tick (the gun queues them)
            ASKED.put(key(best[0], best[1]), now);
            PacketDistributor.sendToServer(new ArtilleryPayloads.TerrainRequest(at, best[0], best[1]));
        }
    }

    /** Whether the game has all of a tile's chunks loaded (its corners and middle, as a cheap test). */
    private static boolean loaded(ClientLevel level, int ox, int oz) {
        for (int[] p : new int[][] {{0, 0}, {T - 1, 0}, {0, T - 1}, {T - 1, T - 1}, {T / 2, T / 2}}) {
            if (!level.isLoaded(new BlockPos(ox + p[0], level.getMinBuildHeight(), oz + p[1]))) {
                return false;
            }
        }
        return true;
    }

    /** The mapped tiles drawn as solid ground where the game has none of its own. */
    static void submit(SubmitCustomGeometryEvent event, ClientLevel level, Vec3 camera) {
        if (TILES.isEmpty()) {
            return;
        }
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderType.entitySolid(ArtilleryClient.RETICLE), (pose, b) -> {
            for (Tile t : TILES.values()) {
                if (loaded(level, t.ox(), t.oz())) {
                    continue;
                }
                for (int j = 0; j < N; j++) {
                    for (int i = 0; i < N; i++) {
                        int x = t.ox() + i * S, z = t.oz() + j * S;
                        if (level.isLoaded(new BlockPos(x, level.getMinBuildHeight(), z))) {
                            continue;
                        }
                        short h = t.heights()[j * N + i];
                        if (h == ArtilleryPayloads.UNKNOWN) {
                            continue;
                        }
                        int col = 0xFF000000 | t.colours()[j * N + i];
                        top(b, pose, x, h, z, camera, col);
                        // Its sides where the ground beside it is lower (east and south; west and north from those).
                        side(b, pose, t, i + 1, j, x + S, z, x + S, z + S, h, camera, col, 1, 0);
                        side(b, pose, t, i - 1, j, x, z + S, x, z, h, camera, col, -1, 0);
                        side(b, pose, t, i, j + 1, x + S, z + S, x, z + S, h, camera, col, 0, 1);
                        side(b, pose, t, i, j - 1, x, z, x + S, z, h, camera, col, 0, -1);
                    }
                }
            }
        });
    }

    private static void top(VertexConsumer b, PoseStack.Pose pose, int x, int y, int z, Vec3 camera, int col) {
        v(b, pose, x, y, z, camera, col, 0, 1, 0);
        v(b, pose, x, y, z + S, camera, col, 0, 1, 0);
        v(b, pose, x + S, y, z + S, camera, col, 0, 1, 0);
        v(b, pose, x + S, y, z, camera, col, 0, 1, 0);
    }

    /** The wall from this sample's top down to its neighbour's ({@code ni}, {@code nj}) where that is lower. */
    private static void side(VertexConsumer b, PoseStack.Pose pose, Tile t, int ni, int nj, int x0, int z0, int x1, int z1, int h, Vec3 camera, int col,
            int nx, int nz) {
        int low;
        if (ni < 0 || nj < 0 || ni >= N || nj >= N) {
            low = h - 1;
        } else {
            short n = t.heights()[nj * N + ni];
            low = n == ArtilleryPayloads.UNKNOWN ? h - 1 : n;
        }
        if (low >= h) {
            return;
        }
        int dark = darker(col);
        v(b, pose, x0, h, z0, camera, dark, nx, 0, nz);
        v(b, pose, x0, low, z0, camera, dark, nx, 0, nz);
        v(b, pose, x1, low, z1, camera, dark, nx, 0, nz);
        v(b, pose, x1, h, z1, camera, dark, nx, 0, nz);
    }

    private static int darker(int col) {
        int r = (col >> 16 & 255) * 3 / 4, g = (col >> 8 & 255) * 3 / 4, bl = (col & 255) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static void v(VertexConsumer b, PoseStack.Pose pose, double x, double y, double z, Vec3 camera, int col, float nx, float ny, float nz) {
        b.addVertex(pose, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)).setColor(col).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }
}
