package dev.groundburst.client.render;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.screen.TerrainMap;
import dev.groundburst.content.command.WarRoomDisplayBlockEntity;
import dev.groundburst.content.command.WarRoomPayloads;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.RadarArrayBlockEntity;
import dev.groundburst.content.launch.StationPayloads;
import dev.groundburst.content.launch.TerrainSurvey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * A War Room Display's picture on the client (DESIGN.md §17.17): its latest board from the server, and the texture its
 * renderer lays over the screen: the terrain in the station map's phosphor green ({@link TerrainMap#paintInto}), and over
 * it the radars' reach, the Dead Hand's radius, impacts fading, blasts, the members, every missile and its line to its
 * target, laser spots, scan lines. Redrawn at most every few ticks; the view fits everything in or is centred on the
 * display at its set scale. Its labels are the renderer's (lit type over the picture), placed by {@link #px}, {@link #py}.
 */
public final class WarRoomBoard {
    /** The picture's size: four texels a model pixel over the screen's 106 by 42. */
    public static final int W = 424, H = 168;
    private static final int REDRAW = 4;
    private static final Map<BlockPos, WarRoomBoard> BOARDS = new HashMap<>();
    private static @Nullable Level boardsFor;
    private static int counter;

    public static void register() {
        WarRoomPayloads.boardHook = WarRoomBoard::accept;
    }

    private static void sameLevel() {
        Level level = Minecraft.getInstance().level;
        if (level != boardsFor) {
            for (WarRoomBoard b : BOARDS.values()) {
                b.close();
            }
            BOARDS.clear();
            boardsFor = level;
        }
    }

    private static void accept(WarRoomPayloads.Board board) {
        sameLevel();
        BOARDS.computeIfAbsent(board.pos(), WarRoomBoard::new).board = board;
    }

    /** The board of the display at {@code pos} (null: none sent yet). */
    public static @Nullable WarRoomBoard of(BlockPos pos) {
        sameLevel();
        return BOARDS.get(pos);
    }

    private final DynamicTexture texture;
    private final Identifier id;
    private final TerrainMap terrain = new TerrainMap(W, H, false);
    private final NativeImage base = new NativeImage(W, H, false);
    private WarRoomPayloads.@Nullable Board board;
    private long drawnAt = Long.MIN_VALUE;
    private int baseGeneration = -1, baseZoom;
    private double baseX = Double.NaN, baseZ;
    /** The view now: its middle and scale (blocks a texel). */
    private double cx, cz;
    private int zoom = 8;

    private WarRoomBoard(BlockPos pos) {
        this.texture = new DynamicTexture(() -> "Ground Burst war room board", W, H, true);
        NativeImage img = this.texture.getPixels();
        if (img != null) {
            img.fillRect(0, 0, W, H, 0xFF040C06);                          // dark glass until the first board: never the
            this.texture.upload();                                         // GPU's leftover memory
        }
        this.id = GroundBurst.id("dynamic/war_room_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
    }

    private void close() {
        Minecraft.getInstance().getTextureManager().release(this.id);
        this.base.close();
    }

    public WarRoomPayloads.@Nullable Board board() {
        return this.board;
    }

    public int zoom() {
        return this.zoom;
    }

    /** A world x/z's place on the picture (texels from its top left). */
    public float px(double wx) {
        return (float) (W / 2.0 + (wx - this.cx) / this.zoom);
    }

    public float py(double wz) {
        return (float) (H / 2.0 + (wz - this.cz) / this.zoom);
    }

    /** The picture's texture, redrawn if it is due (called as it is drawn; {@code ticks}: the client's clock). */
    public Identifier texture(long ticks) {
        WarRoomPayloads.Board b = this.board;
        if (b != null && ticks >= this.drawnAt + REDRAW) {             // (never ticks - drawnAt: from MIN_VALUE it overflows)
            this.drawnAt = ticks;
            view(b);
            this.terrain.request(b.pos(), b.at(), this.cx, this.cz, this.zoom);
            redraw(b, ticks);
        }
        return this.id;
    }

    /** Where it looks: everything in (the members, the missiles and their targets, blasts, impacts) or centred on it. */
    private void view(WarRoomPayloads.Board b) {
        if (b.view() > 0) {
            this.cx = b.at().getX() + 0.5;
            this.cz = b.at().getZ() + 0.5;
            this.zoom = WarRoomDisplayBlockEntity.SCALES[Mth.clamp(b.view(), 1, WarRoomDisplayBlockEntity.SCALES.length - 1)];
            return;
        }
        double x0 = b.at().getX(), x1 = x0, z0 = b.at().getZ(), z1 = z0;
        for (StationPayloads.MemberView m : b.members()) {
            x0 = Math.min(x0, m.at().getX());
            x1 = Math.max(x1, m.at().getX());
            z0 = Math.min(z0, m.at().getZ());
            z1 = Math.max(z1, m.at().getZ());
        }
        for (StationPayloads.ContactView c : b.contacts()) {
            x0 = Math.min(x0, Math.min(c.x(), c.targetX()));
            x1 = Math.max(x1, Math.max(c.x(), c.targetX()));
            z0 = Math.min(z0, Math.min(c.z(), c.targetZ()));
            z1 = Math.max(z1, Math.max(c.z(), c.targetZ()));
        }
        for (StationPayloads.BlastView bl : b.blasts()) {
            x0 = Math.min(x0, bl.x());
            x1 = Math.max(x1, bl.x());
            z0 = Math.min(z0, bl.z());
            z1 = Math.max(z1, bl.z());
        }
        for (WarRoomPayloads.ImpactView i : b.impacts()) {
            x0 = Math.min(x0, i.x());
            x1 = Math.max(x1, i.x());
            z0 = Math.min(z0, i.z());
            z1 = Math.max(z1, i.z());
        }
        this.cx = (x0 + x1) / 2 + 0.5;
        this.cz = (z0 + z1) / 2 + 0.5;
        double need = Math.max((x1 - x0 + 96) / (W * 0.86), (z1 - z0 + 96) / (H * 0.8));
        int z = TerrainSurvey.SCALES[TerrainSurvey.SCALES.length - 1];
        for (int s : TerrainSurvey.SCALES) {
            if (s >= need) {
                z = s;
                break;
            }
        }
        this.zoom = Math.max(2, z);
    }

    private void redraw(WarRoomPayloads.Board b, long ticks) {
        NativeImage img = this.texture.getPixels();
        if (img == null) {
            return;
        }
        boolean terrain = (b.layers() & WarRoomDisplayBlockEntity.TERRAIN) != 0;
        if (this.cx != this.baseX || this.cz != this.baseZ || this.zoom != this.baseZoom || TerrainMap.generation() != this.baseGeneration) {
            this.baseX = this.cx;
            this.baseZ = this.cz;
            this.baseZoom = this.zoom;
            this.baseGeneration = TerrainMap.generation();
            this.terrain.paintInto(this.base, b.at(), this.cx, this.cz, this.zoom);
        }
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++) {
                img.setPixel(i, j, terrain ? this.base.getPixel(i, j) : 0xFF040C06);
            }
        }
        boolean blink = ticks % 20 < 12;
        // The radars' reach, the Dead Hand's radius.
        for (StationPayloads.MemberView m : b.members()) {
            double x = m.at().getX() + 0.5, z = m.at().getZ() + 0.5;
            if ((b.layers() & WarRoomDisplayBlockEntity.REACH) != 0 && m.radar() && !m.lost() && m.contact() && m.working()) {
                float x0 = px(x - RadarArrayBlockEntity.RANGE), x1 = px(x + RadarArrayBlockEntity.RANGE);
                float z0 = py(z - RadarArrayBlockEntity.RANGE), z1 = py(z + RadarArrayBlockEntity.RANGE);
                line(img, x0, z0, x1, z0, 0x8830A048, 5);
                line(img, x0, z1, x1, z1, 0x8830A048, 5);
                line(img, x0, z0, x0, z1, 0x8830A048, 5);
                line(img, x1, z0, x1, z1, 0x8830A048, 5);
            }
            if (m.deadHand() && m.armed() && m.range() > 0) {
                boolean alert = m.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
                ring(img, px(x), py(z), m.range() / (float) this.zoom, alert ? (blink ? 0xFFFF4A3A : 0xAA902A20) : 0x99FFB040, 4);
            }
        }
        // Impacts: rings widening and fading over two minutes; blasts its seismographs felt.
        for (WarRoomPayloads.ImpactView i : b.impacts()) {
            float u = Math.min(1, i.age() / (float) WarRoomDisplayBlockEntity.IMPACT_KEEP);
            int a = (int) (255 * (1 - u * 0.85f));
            ring(img, px(i.x() + 0.5), py(i.z() + 0.5), 2 + u * 10, a << 24 | (i.own() ? 0xFFB040 : 0xFF4A3A), 0);
            cross(img, px(i.x() + 0.5), py(i.z() + 0.5), 2, a << 24 | 0xF0F0F0);
        }
        if ((b.layers() & WarRoomDisplayBlockEntity.BLASTS) != 0) {
            for (StationPayloads.BlastView bl : b.blasts()) {
                blast(img, bl);
            }
        }
        // The members: launchers by their state, radars as rings, seismographs as diamonds, the Dead Hand as a crossed box.
        for (StationPayloads.MemberView m : b.members()) {
            float x = px(m.at().getX() + 0.5), z = py(m.at().getZ() + 0.5);
            int col = memberColour(m, blink);
            if (m.radar()) {
                ring(img, x, z, 3.5f, col, 0);
            } else if (m.seismograph()) {
                line(img, x - 3, z, x, z - 3, col, 0);
                line(img, x, z - 3, x + 3, z, col, 0);
                line(img, x + 3, z, x, z + 3, col, 0);
                line(img, x, z + 3, x - 3, z, col, 0);
            } else if (m.deadHand()) {
                box(img, x, z, 3, col);
                cross(img, x, z, 2, col);
            } else {
                fill(img, x - 2, z - 2, x + 2, z + 2, col);
                if (m.hasTarget() && (m.willBeArmed() || m.launchAt() >= 0)) {
                    line(img, x, z, px(m.targetX() + 0.5), py(m.targetZ() + 0.5), 0x99FF4A3A, 3);
                    cross(img, px(m.targetX() + 0.5), py(m.targetZ() + 0.5), 3, 0xCCFF4A3A);
                }
            }
        }
        // Where the display is: a small open square.
        box(img, px(b.at().getX() + 0.5), py(b.at().getZ() + 0.5), 2, 0xFFF0F0F0);
        // Laser spots.
        for (StationPayloads.SpotView s : b.spots()) {
            float x = px(s.x() + 0.5), z = py(s.z() + 0.5);
            int r = blink ? 4 : 3;
            line(img, x - r, z, x, z - r, 0xFFFF4A3A, 0);
            line(img, x, z - r, x + r, z, 0xFFFF4A3A, 0);
            line(img, x + r, z, x, z + r, 0xFFFF4A3A, 0);
            line(img, x, z + r, x - r, z, 0xFFFF4A3A, 0);
        }
        // Missiles in the air: the network's amber, those its radars track red, each with its line to its target.
        for (StationPayloads.ContactView c : b.contacts()) {
            float x = px(c.x() + 0.5), z = py(c.z() + 0.5), tx = px(c.targetX() + 0.5), tz = py(c.targetZ() + 0.5);
            int col = c.own() ? 0xFFFFB040 : 0xFFFF4A3A;
            line(img, x, z, tx, tz, c.own() ? 0x88FFB040 : 0x99FF4A3A, 3);
            if (!c.own()) {
                ring(img, tx, tz, blink ? 4 : 3, 0xFFFF4A3A, 0);
            } else {
                cross(img, tx, tz, 2, 0xAAFFB040);
            }
            fill(img, x - 1.5f, z - 1.5f, x + 1.5f, z + 1.5f, col);
            line(img, x, z, x + (float) Math.sin(c.heading()) * 6, z + (float) Math.cos(c.heading()) * 6, col, 0);
        }
        // Scan lines, as the station's map has.
        for (int j = 0; j < H; j += 2) {
            for (int i = 0; i < W; i++) {
                img.setPixel(i, j, darker(img.getPixel(i, j)));
            }
        }
        this.texture.upload();
    }

    private static int memberColour(StationPayloads.MemberView m, boolean blink) {
        if (m.lost()) {
            return blink ? 0xFFFF4A3A : 0xAA902A20;
        }
        if (m.radar() || m.seismograph()) {
            return m.contact() && m.working() ? 0xFF40FF70 : 0xFF1C6A30;
        }
        if (m.deadHand()) {
            boolean alert = m.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
            return alert ? (blink ? 0xFFFF4A3A : 0xFF902A20) : m.armed() ? 0xFFFFB040 : 0xFF40FF70;
        }
        boolean busy = m.launchAt() >= 0 || m.inFlight() >= 0 || m.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
        return busy ? 0xFFFFB040 : m.willBeArmed() ? 0xFFFF4A3A : m.readiness() == LaunchSiloBlockEntity.Readiness.READY.ordinal() ? 0xFF40FF70 : 0xFF1C6A30;
    }

    /** A blast: fixed, a ring as big as it was strong; from one station, the arc it may lie in. Fading over ten minutes. */
    private void blast(NativeImage img, StationPayloads.BlastView b) {
        int a = (int) Mth.clamp(255 - b.age() / 12000.0 * 200, 55, 255);
        int col = a << 24 | 0xFFB040;
        if (b.fixed()) {
            ring(img, px(b.x() + 0.5), py(b.z() + 0.5), Math.max(2, b.magnitude() * 1.6f), col, 0);
            return;
        }
        double near = Math.max(0, b.distance() - b.distanceSpread()), far = b.distance() + b.distanceSpread();
        double a0 = Math.toRadians(b.bearing() - b.bearingSpread()), a1 = Math.toRadians(b.bearing() + b.bearingSpread());
        float lnx = 0, lnz = 0, lfx = 0, lfz = 0;
        for (int i = 0; i <= 12; i++) {
            double t = a0 + (a1 - a0) * i / 12;
            float nx = px(b.fromX() + Math.sin(t) * near), nz = py(b.fromZ() - Math.cos(t) * near);
            float fx = px(b.fromX() + Math.sin(t) * far), fz = py(b.fromZ() - Math.cos(t) * far);
            if (i > 0) {
                line(img, lnx, lnz, nx, nz, col, 0);
                line(img, lfx, lfz, fx, fz, col, 0);
            }
            if (i == 0 || i == 12) {
                line(img, nx, nz, fx, fz, col, 2);
            }
            lnx = nx;
            lnz = nz;
            lfx = fx;
            lfz = fz;
        }
    }

    // --- drawing into the picture ----------------------------------------------------------------------------------------
    private static int blend(int under, int over) {
        int a = over >>> 24;
        if (a >= 255) {
            return over | 0xFF000000;
        }
        if (a == 0) {
            return under;
        }
        int r = ((over >> 16 & 0xFF) * a + (under >> 16 & 0xFF) * (255 - a)) / 255;
        int g = ((over >> 8 & 0xFF) * a + (under >> 8 & 0xFF) * (255 - a)) / 255;
        int bl = ((over & 0xFF) * a + (under & 0xFF) * (255 - a)) / 255;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 13 / 16, g = (argb >> 8 & 0xFF) * 13 / 16, b = (argb & 0xFF) * 13 / 16;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void dot(NativeImage img, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < W && y < H) {
            img.setPixel(x, y, blend(img.getPixel(x, y), argb));
        }
    }

    private static void line(NativeImage img, float x0, float y0, float x1, float y1, int argb, int dash) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len > 4 * (W + H)) {
            return;                                       // (far off the picture: not worth walking)
        }
        int n = Math.max(1, (int) Math.ceil(len));
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= n; i++) {
            if (dash > 0 && (i / dash) % 2 == 1) {
                continue;
            }
            int x = (int) Math.floor(x0 + (x1 - x0) * i / n), y = (int) Math.floor(y0 + (y1 - y0) * i / n);
            if (x != lastX || y != lastY) {
                dot(img, x, y, argb);
                lastX = x;
                lastY = y;
            }
        }
    }

    private static void ring(NativeImage img, float cx, float cy, float r, int argb, int dash) {
        if (r > 4 * (W + H) || cx + r < 0 || cy + r < 0 || cx - r > W || cy - r > H) {
            return;
        }
        int steps = Math.max(24, (int) (r * 6.3));
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i < steps; i++) {
            if (dash > 0 && (i * 2 / dash) % 2 == 1) {
                continue;
            }
            double a = i * Math.PI * 2 / steps;
            int x = (int) Math.floor(cx + Math.cos(a) * r), y = (int) Math.floor(cy + Math.sin(a) * r);
            if (x != lastX || y != lastY) {
                dot(img, x, y, argb);
                lastX = x;
                lastY = y;
            }
        }
    }

    private static void cross(NativeImage img, float x, float y, int r, int argb) {
        line(img, x - r, y - r, x + r, y + r, argb, 0);
        line(img, x - r, y + r, x + r, y - r, argb, 0);
    }

    private static void box(NativeImage img, float x, float y, int r, int argb) {
        line(img, x - r, y - r, x + r, y - r, argb, 0);
        line(img, x - r, y + r, x + r, y + r, argb, 0);
        line(img, x - r, y - r, x - r, y + r, argb, 0);
        line(img, x + r, y - r, x + r, y + r, argb, 0);
    }

    private static void fill(NativeImage img, float x0, float y0, float x1, float y1, int argb) {
        for (int y = (int) Math.floor(y0); y <= (int) Math.floor(y1); y++) {
            for (int x = (int) Math.floor(x0); x <= (int) Math.floor(x1); x++) {
                dot(img, x, y, argb);
            }
        }
    }
}
