package dev.groundzero.client.screen;

import static dev.groundzero.client.screen.Phosphor.AMBER;
import static dev.groundzero.client.screen.Phosphor.BRIGHT;
import static dev.groundzero.client.screen.Phosphor.DIM;
import static dev.groundzero.client.screen.Phosphor.EDGE;
import static dev.groundzero.client.screen.Phosphor.FAINT;
import static dev.groundzero.client.screen.Phosphor.LIST_BG;
import static dev.groundzero.client.screen.Phosphor.PANEL;
import static dev.groundzero.client.screen.Phosphor.RED;
import static dev.groundzero.client.screen.Phosphor.SCOPE_BG;
import static dev.groundzero.client.screen.Phosphor.SELECTED_BG;
import static dev.groundzero.client.screen.Phosphor.TEXT;
import static dev.groundzero.client.screen.Phosphor.WHITE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.launch.LaunchPayloads;
import dev.groundzero.content.launch.RadarArrayBlock;
import dev.groundzero.content.launch.RadarArrayBlockEntity;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Radar Array's screen (DESIGN.md §18.1, early): a sweeping green radar scope of the 2048 blocks round the array
 * (north up), one turn every 6 s as its dish turns. A missile shows as a blip only where the sweep last passed it,
 * fading until it comes round again; ones coming down within 512 blocks (the dashed red ring) are incoming, in red. On
 * the right, the tracks: how far off, how long until they come down, and for the picked one where and how high.
 */
public class RadarArrayScreen extends Screen {
    private static final String T_TITLE = GZLang.add(GroundZero.MODID + ".radar.title", "Radar Array");
    private static final String T_TRACKS = GZLang.add(GroundZero.MODID + ".radar.tracks", "Tracks (%s)");
    private static final String T_NONE = GZLang.add(GroundZero.MODID + ".radar.none", "Nothing in the air");
    private static final String T_INCOMING = GZLang.add(GroundZero.MODID + ".radar.incoming", "INCOMING: %s");
    private static final String T_ROW_IN = GZLang.add(GroundZero.MODID + ".radar.row_incoming", "%s. INBOUND");
    private static final String T_ROW = GZLang.add(GroundZero.MODID + ".radar.row", "%s. %s m");
    private static final String T_LANDS = GZLang.add(GroundZero.MODID + ".radar.lands", "Lands at %s, %s");
    private static final String T_ETA = GZLang.add(GroundZero.MODID + ".radar.eta", "in %s s, now at %s m up");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".radar.no_power", "NO POWER");
    private static final String T_RANGE = GZLang.add(GroundZero.MODID + ".radar.range", "%s m");

    private static final int W = 336, H = 214, SCOPE = 96, ROW = 12, ROWS = 9;
    private static final int COL = 2 * SCOPE + 20, CW = W - COL - 8, LIST_Y = 29;
    /** One turn of the sweep, ticks (the dish turns once every 6 s). */
    private static final double PERIOD = 120;

    private static LaunchPayloads.@Nullable Radar latest;

    public static void register() {
        RadarArrayBlock.screenHook = pos -> Minecraft.getInstance().gui.setScreen(new RadarArrayScreen(pos));
        LaunchPayloads.radarHook = radar -> latest = radar;
    }

    /** Where a blip was painted, when, and whether it was incoming then. */
    private record Painted(int x, int z, double time, boolean incoming) {}

    private final BlockPos pos;
    private final Map<Integer, Painted> painted = new HashMap<>();
    private double lastSweep = Double.NaN;
    private int scroll;
    private int selected = Integer.MIN_VALUE;

    public RadarArrayScreen(BlockPos pos) {
        super(Component.translatable(T_TITLE));
        this.pos = pos;
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    private int cx() {
        return left() + 8 + SCOPE;
    }

    private int cy() {
        return top() + 14 + SCOPE;
    }

    private int col() {
        return left() + COL;
    }

    private LaunchPayloads.@Nullable Radar radar() {
        return latest != null && latest.pos().equals(this.pos) ? latest : null;
    }

    private List<LaunchPayloads.Track> tracks() {
        LaunchPayloads.Radar r = radar();
        return r == null ? List.of() : r.tracks();
    }

    private LaunchPayloads.@Nullable Track selectedTrack() {
        for (LaunchPayloads.Track t : tracks()) {
            if (t.id() == this.selected) {
                return t;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, true));
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, false));
        super.onClose();
    }

    @Override
    public void tick() {
        List<LaunchPayloads.Track> tracks = tracks();
        if (selectedTrack() == null) {
            this.selected = tracks.isEmpty() ? Integer.MIN_VALUE : tracks.getFirst().id();
        }
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, tracks.size() - ROWS));
    }

    private boolean inList(double x, double y) {
        return x >= col() && x < col() + CW && y >= top() + LIST_Y && y < top() + LIST_Y + ROWS * ROW;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (inList(event.x(), event.y())) {
            int row = this.scroll + (int) ((event.y() - top() - LIST_Y) / ROW);
            List<LaunchPayloads.Track> tracks = tracks();
            if (row < tracks.size()) {
                this.selected = tracks.get(row).id();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inList(x, y)) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, tracks().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private double distance(LaunchPayloads.Track t) {
        return Math.hypot(t.x() - this.pos.getX(), t.z() - this.pos.getZ());
    }

    // --- drawing ------------------------------------------------------------------------------------------------------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        g.fill(left(), top(), left() + W, top() + H, PANEL);
        g.outline(left(), top(), W, H, EDGE);
        drawScope(g, partial);
        drawList(g);
    }

    private void drawScope(GuiGraphicsExtractor g, float partial) {
        int cx = cx(), cy = cy();
        Phosphor.disc(g, cx, cy, SCOPE, SCOPE_BG);
        LaunchPayloads.Radar r = radar();
        boolean on = r != null && r.formed() && r.powered();
        double k = SCOPE / (double) RadarArrayBlockEntity.RANGE;
        for (int ring = 1; ring <= 4; ring++) {
            Phosphor.ring(g, cx, cy, SCOPE * ring / 4, on ? DIM : 0xFF0E2A16);
        }
        Phosphor.line(g, cx - SCOPE, cy, cx + SCOPE, cy, on ? FAINT : 0x22104010, 0);
        Phosphor.line(g, cx, cy - SCOPE, cx, cy + SCOPE, on ? FAINT : 0x22104010, 0);
        if (!on) {
            Component off = Component.translatable(T_NO_POWER);
            g.text(this.font, off, cx - this.font.width(off) / 2, cy - 4, RED);
            return;
        }
        // The danger ring: what lands inside it is incoming.
        int danger = (int) (RadarArrayBlockEntity.INCOMING_RADIUS * k);
        for (int i = 0; i < 72; i += 2) {
            double a0 = i * Math.PI / 36, a1 = (i + 1) * Math.PI / 36;
            Phosphor.line(g, cx + (int) (Math.cos(a0) * danger), cy + (int) (Math.sin(a0) * danger),
                    cx + (int) (Math.cos(a1) * danger), cy + (int) (Math.sin(a1) * danger), 0x99FF4A3A, 0);
        }
        g.text(this.font, "N", cx - 2, cy - SCOPE + 3, DIM, false);
        Component outer = Component.translatable(T_RANGE, RadarArrayBlockEntity.RANGE);
        g.text(this.font, outer, cx + SCOPE - this.font.width(outer), cy + SCOPE - 9, DIM, false);
        // The sweep: blips are painted where it passes them.
        double now = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;
        double sweep = (now / PERIOD) * 2 * Math.PI;
        if (!Double.isNaN(this.lastSweep) && sweep > this.lastSweep) {
            for (LaunchPayloads.Track t : r.tracks()) {
                double a = Math.atan2(t.z() - this.pos.getZ(), t.x() - this.pos.getX());
                double from = this.lastSweep, rel = Math.floorMod((long) Math.floor((a - from) * 1e6), (long) Math.floor(2 * Math.PI * 1e6)) / 1e6;
                if (rel <= sweep - from) {
                    this.painted.put(t.id(), new Painted(t.x(), t.z(), now, t.incoming()));
                }
            }
        }
        this.lastSweep = sweep;
        for (int i = 0; i < 18; i++) {
            double a = sweep - i * 0.035;
            int alpha = (int) (200 * (1 - i / 18.0));
            Phosphor.line(g, cx, cy, cx + (int) (Math.cos(a) * SCOPE), cy + (int) (Math.sin(a) * SCOPE), alpha << 24 | 0x40FF70, 0);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, WHITE);                      // the array itself
        this.painted.values().removeIf(p -> now - p.time() > PERIOD * 1.5);
        for (var e : this.painted.entrySet()) {
            Painted p = e.getValue();
            int x = cx + (int) ((p.x() - this.pos.getX()) * k), y = cy + (int) ((p.z() - this.pos.getZ()) * k);
            double fade = Math.max(0, 1 - (now - p.time()) / PERIOD);
            int alpha = (int) (60 + 195 * fade);
            int colour = (p.incoming() ? 0xFF4A3A : 0xFFB040) | alpha << 24;
            int size = e.getKey() == this.selected ? 2 : 1;
            g.fill(x - size, y - size, x + size + 1, y + size + 1, colour);
        }
        // The picked track: where it will come down.
        LaunchPayloads.Track sel = selectedTrack();
        Painted p = sel == null ? null : this.painted.get(sel.id());
        if (sel != null && p != null) {
            int x = cx + (int) ((p.x() - this.pos.getX()) * k), y = cy + (int) ((p.z() - this.pos.getZ()) * k);
            int tx = cx + (int) ((sel.targetX() - this.pos.getX()) * k), ty = cy + (int) ((sel.targetZ() - this.pos.getZ()) * k);
            Phosphor.line(g, x, y, tx, ty, sel.incoming() ? 0x99FF4A3A : 0x88FFB040, 2);
            Phosphor.cross(g, tx, ty, 2, sel.incoming() ? RED : AMBER);
        }
    }

    private void drawList(GuiGraphicsExtractor g) {
        int x = col(), y = top() + LIST_Y, h = ROWS * ROW;
        g.fill(x, y, x + CW, y + h, LIST_BG);
        g.outline(x - 1, y - 1, CW + 2, h + 2, EDGE);
        List<LaunchPayloads.Track> tracks = tracks();
        if (tracks.isEmpty()) {
            g.text(this.font, this.font.plainSubstrByWidth(Component.translatable(T_NONE).getString(), CW - 6), x + 3, y + 3, DIM, false);
            return;
        }
        boolean bar = tracks.size() > ROWS;
        int textW = CW - 6 - (bar ? 4 : 0);
        for (int i = 0; i < ROWS && this.scroll + i < tracks.size(); i++) {
            LaunchPayloads.Track t = tracks.get(this.scroll + i);
            int ry = y + i * ROW;
            if (t.id() == this.selected) {
                g.fill(x, ry, x + CW - (bar ? 4 : 0), ry + ROW, SELECTED_BG);
            }
            String eta = (t.eta() + 19) / 20 + "s";
            int etaW = this.font.width(eta);
            g.text(this.font, eta, x + 3 + textW - etaW, ry + 2, t.incoming() ? RED : AMBER, false);
            String label = t.incoming() ? Component.translatable(T_ROW_IN, this.scroll + i + 1).getString()
                    : Component.translatable(T_ROW, this.scroll + i + 1, (int) distance(t)).getString();
            g.text(this.font, this.font.plainSubstrByWidth(label, textW - etaW - 4), x + 3, ry + 2, t.incoming() ? RED : AMBER, false);
        }
        if (bar) {
            int track = h - 2, thumb = Math.max(6, track * ROWS / tracks.size());
            int ty = y + 1 + (track - thumb) * this.scroll / Math.max(1, tracks.size() - ROWS);
            g.fill(x + CW - 3, y + 1, x + CW - 1, y + h - 1, 0xFF0E2A16);
            g.fill(x + CW - 3, ty, x + CW - 1, ty + thumb, DIM);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        LaunchPayloads.Radar r = radar();
        g.text(this.font, this.title, left() + 8, top() + 4, TEXT);
        if (r != null) {
            Component energy = Component.literal(r.energy() + "%");
            g.text(this.font, energy, left() + W - 8 - this.font.width(energy), top() + 4, r.powered() ? BRIGHT : RED);
        }
        int x = col();
        g.text(this.font, this.font.plainSubstrByWidth(Component.translatable(T_TRACKS, tracks().size()).getString(), CW), x, top() + 16, TEXT);
        long incoming = tracks().stream().filter(LaunchPayloads.Track::incoming).count();
        if (incoming > 0) {
            // A flashing warning over the scope.
            boolean flash = Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() / 8 % 2 == 0;
            Component warn = Component.translatable(T_INCOMING, incoming);
            g.text(this.font, warn, left() + 8 + 2 * SCOPE - this.font.width(warn), top() + 4, flash ? RED : 0xFF801810);
        }
        LaunchPayloads.Track t = selectedTrack();
        if (t != null) {
            int y = top() + LIST_Y + ROWS * ROW + 6;
            String lands = Component.translatable(T_LANDS, t.targetX(), t.targetZ()).getString();
            String eta = Component.translatable(T_ETA, (t.eta() + 19) / 20, t.y()).getString();
            int i = 0;
            for (var line : this.font.split(Component.literal(lands), CW)) {
                g.text(this.font, line, x, y + i++ * 10, t.incoming() ? RED : TEXT, false);
            }
            for (var line : this.font.split(Component.literal(eta), CW)) {
                if (i < 4) {
                    g.text(this.font, line, x, y + i++ * 10, DIM, false);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
