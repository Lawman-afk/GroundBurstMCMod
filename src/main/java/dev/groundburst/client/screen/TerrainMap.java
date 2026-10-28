package dev.groundburst.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.LaunchPayloads;
import dev.groundburst.content.launch.TerrainSurvey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Launch Computer's terrain map (DESIGN.md §17.3): the server's terrain survey ({@link TerrainSurvey}) in tiles,
 * cached here for the session, drawn into one texture as a green phosphor picture (brightness from the map colours,
 * water darker and banded, unknown ground black). The screen moves and zooms the view; this keeps the texture and asks
 * for the tiles in view.
 */
public final class TerrainMap {
    private record Key(int scale, int tx, int tz) {}

    private record Tile(byte[] colours, long time, boolean partial) {}

    private static final Map<Key, Tile> TILES = new HashMap<>();
    private static final Map<Key, Long> ASKED = new HashMap<>();
    private static @Nullable Level tilesFor;
    /** Ticks after which a tile in view is asked for again (the ground changes: craters), and between asking twice. */
    private static final long REFRESH = 200, RETRY = 40;
    private static int generation;

    public static void register() {
        LaunchPayloads.tileHook = TerrainMap::accept;
    }

    private static void accept(LaunchPayloads.MapTile t) {
        Level level = Minecraft.getInstance().level;
        sameLevel(level);
        TILES.put(new Key(t.scale(), t.tx(), t.tz()), new Tile(t.colours(), now(), t.partial()));
        generation++;
    }

    private static long now() {
        Level level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    private static void sameLevel(@Nullable Level level) {
        if (level != tilesFor) {
            TILES.clear();
            ASKED.clear();
            tilesFor = level;
        }
    }

    private final int w, h;
    private final DynamicTexture texture;
    private final Identifier id;
    private double drawnX = Double.NaN, drawnZ;
    private int drawnZoom, drawnGeneration = -1;

    public TerrainMap(int w, int h) {
        this.w = w;
        this.h = h;
        this.texture = new DynamicTexture(() -> "Ground Burst terrain map", w, h, true);
        this.id = GroundBurst.id("dynamic/terrain_map");
        Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(this.id);
    }

    /** Asks the computer for the tiles in a view centred on x/z at {@code zoom} blocks a pixel that it has not sent lately. */
    public void request(BlockPos computer, double x, double z, int zoom) {
        sameLevel(Minecraft.getInstance().level);
        int span = TerrainSurvey.TILE * zoom;
        int tx0 = Math.floorDiv((int) Math.floor(x - this.w * zoom / 2.0), span), tx1 = Math.floorDiv((int) Math.floor(x + this.w * zoom / 2.0), span);
        int tz0 = Math.floorDiv((int) Math.floor(z - this.h * zoom / 2.0), span), tz1 = Math.floorDiv((int) Math.floor(z + this.h * zoom / 2.0), span);
        long t = now();
        List<Long> want = new ArrayList<>();
        for (int tz = tz0; tz <= tz1; tz++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                double cx = tx * span + span / 2.0 - computer.getX(), cz = tz * span + span / 2.0 - computer.getZ();
                if (Math.abs(cx) > TerrainSurvey.RANGE + span || Math.abs(cz) > TerrainSurvey.RANGE + span) {
                    continue;                                // beyond the survey: nothing to ask for
                }
                Key k = new Key(zoom, tx, tz);
                Tile tile = TILES.get(k);
                Long asked = ASKED.get(k);
                long age = tile == null ? Long.MAX_VALUE : t - tile.time();
                if ((tile == null || age > (tile.partial() ? RETRY : REFRESH)) && (asked == null || t - asked > RETRY)
                        && want.size() < LaunchPayloads.MapRequest.MAX_TILES) {
                    ASKED.put(k, t);
                    want.add(ChunkPos.pack(tx, tz));
                }
            }
        }
        if (!want.isEmpty()) {
            ClientPacketDistributor.sendToServer(new LaunchPayloads.MapRequest(computer, zoom, want));
        }
    }

    /** Draws the view centred on x/z at {@code zoom} blocks a pixel at x0/y0 (redrawing the texture only when needed). */
    public void draw(GuiGraphicsExtractor g, int x0, int y0, BlockPos computer, double x, double z, int zoom) {
        if (x != this.drawnX || z != this.drawnZ || zoom != this.drawnZoom || generation != this.drawnGeneration) {
            redraw(computer, x, z, zoom);
        }
        g.blit(this.id, x0, y0, x0 + this.w, y0 + this.h, 0, 1, 0, 1);
    }

    private void redraw(BlockPos computer, double cx, double cz, int zoom) {
        this.drawnX = cx;
        this.drawnZ = cz;
        this.drawnZoom = zoom;
        this.drawnGeneration = generation;
        NativeImage img = this.texture.getPixels();
        if (img == null) {
            return;
        }
        int span = TerrainSurvey.TILE * zoom;
        for (int j = 0; j < this.h; j++) {
            for (int i = 0; i < this.w; i++) {
                int wx = (int) Math.floor(cx + (i - this.w / 2.0) * zoom), wz = (int) Math.floor(cz + (j - this.h / 2.0) * zoom);
                boolean inSurvey = Math.abs(wx - computer.getX()) <= TerrainSurvey.RANGE && Math.abs(wz - computer.getZ()) <= TerrainSurvey.RANGE;
                int packed = 0;
                Tile tile = TILES.get(new Key(zoom, Math.floorDiv(wx, span), Math.floorDiv(wz, span)));
                if (tile != null && tile.colours().length > 0) {
                    int si = Math.floorMod(wx, span) / zoom, sj = Math.floorMod(wz, span) / zoom;
                    packed = tile.colours()[sj * TerrainSurvey.TILE + si] & 0xFF;
                }
                img.setPixel(i, j, phosphor(packed, inSurvey, wx, wz, j));
            }
        }
        this.texture.upload();
    }

    /** A map colour as green phosphor: its brightness kept, its hue dropped; water darker with bands; unknown black. */
    private static int phosphor(int packed, boolean inSurvey, int wx, int wz, int row) {
        if (packed == 0) {
            boolean hatch = !inSurvey && Math.floorMod(wx + wz, 12) < 2;            // beyond the survey: hatched
            return hatch ? 0xFF0A2410 : 0xFF040C06;
        }
        int argb = MapColor.getColorFromPackedId(packed);
        int r = argb >> 16 & 0xFF, gr = argb >> 8 & 0xFF, b = argb & 0xFF;
        double lum = (0.3 * r + 0.59 * gr + 0.11 * b) / 255.0;
        boolean water = packed >> 2 == MapColor.WATER.id;
        // Dim greens, so the markers drawn over the ground stand out: bright ground (sand, snow) at about half.
        double v = water ? 0.06 + lum * 0.2 - (row % 3 == 0 ? 0.03 : 0) : 0.1 + lum * 0.5;
        if (Math.floorMod(wx, 128) == 0 || Math.floorMod(wz, 128) == 0) {
            v = Math.min(1, v + 0.08);                                          // the grid, every 128 blocks
        }
        int g = (int) (Math.min(1, v) * 255);
        return 0xFF000000 | (int) (g * 0.28) << 16 | g << 8 | (int) (g * 0.38);
    }
}
