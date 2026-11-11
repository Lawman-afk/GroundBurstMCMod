package dev.groundburst.client.screen;

import static dev.groundburst.client.screen.Phosphor.AMBER;
import static dev.groundburst.client.screen.Phosphor.BRIGHT;
import static dev.groundburst.client.screen.Phosphor.DIM;
import static dev.groundburst.client.screen.Phosphor.EDGE;
import static dev.groundburst.client.screen.Phosphor.LIST_BG;
import static dev.groundburst.client.screen.Phosphor.PANEL;
import static dev.groundburst.client.screen.Phosphor.RED;
import static dev.groundburst.client.screen.Phosphor.SELECTED_BG;
import static dev.groundburst.client.screen.Phosphor.TEXT;
import static dev.groundburst.client.screen.Phosphor.WHITE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.render.WarRoomBoard;
import dev.groundburst.client.render.WarRoomRenderer;
import dev.groundburst.content.command.WarRoomDisplayBlock;
import dev.groundburst.content.command.WarRoomDisplayBlockEntity;
import dev.groundburst.content.command.WarRoomPayloads;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.StationPayloads;
import dev.groundburst.content.launch.TerrainSurvey;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The War Room Display's screen (DESIGN.md §17.17, §17.19): its board as big as the window allows, to watch and move
 * about in as the Launch Control Station's map is (drag with either button to look round, scroll to zoom about the
 * cursor, 1:1 to 1:128): the terrain, the radars' reach, the Dead Hand's radius, impacts fading, the blasts its
 * seismographs felt, the members, laser spots, the missiles in the air with their lines and times, a command rocket's
 * signal round it. Across the top the network's code and counts, the alert condition and the soonest impact, the time;
 * at the side the members (a click centres on one), the log, and the wall's own settings (its view, its layers, which this
 * map follows too, and "Show on the wall": the wall takes this view).
 */
public final class WarRoomScreen extends Screen {
    private static final String P = GroundBurst.MODID + ".war_room.screen.";
    private static final String T_VIEW_FIT = GBLang.add(P + "view.fit", "Wall: everything in");
    private static final String T_VIEW_AT = GBLang.add(P + "view.at", "Wall: centred here, 1:%s");
    private static final String T_VIEW_SET = GBLang.add(P + "view.set", "Wall: as shown here, 1:%s");
    private static final String T_ON = GBLang.add(P + "on", "%s: on");
    private static final String T_OFF = GBLang.add(P + "off", "%s: off");
    private static final String[] T_LAYER = {
            GBLang.add(P + "layer.terrain", "Terrain"),
            GBLang.add(P + "layer.reach", "Radar reach"),
            GBLang.add(P + "layer.labels", "Labels"),
            GBLang.add(P + "layer.log", "Log"),
            GBLang.add(P + "layer.blasts", "Blasts")};
    private static final String T_SHOWS = GBLang.add(P + "shows", "Shows network %s");
    private static final String T_SHOWS_NONE = GBLang.add(P + "shows_none", "Shows no network: use Launch Codes on it");
    private static final String T_MEMBERS = GBLang.add(P + "members", "Network (%s)");
    private static final String T_LOG = GBLang.add(P + "log", "Log");
    private static final String T_WALL = GBLang.add(P + "wall", "Wall");
    private static final String T_FIT = GBLang.add(P + "fit", "Fit");
    private static final String T_DISPLAY = GBLang.add(P + "display", "Display");
    private static final String T_SHOW = GBLang.add(P + "show", "Show on the wall");
    private static final String T_SHOWN = GBLang.add(P + "shown", "On the wall");
    private static final String T_CURSOR = GBLang.add(P + "cursor", "X %s  Z %s");
    private static final String T_ZOOM = GBLang.add(P + "zoom", "1:%s");
    private static final String T_DARK = GBLang.add(P + "dark", "NO POWER");
    private static final String T_WAITING = GBLang.add(P + "waiting", "Waiting for the board");
    private static final String T_NO_MEMBERS = GBLang.add(P + "no_members", "Nothing on its network yet");
    private static final String T_NO_LOG = GBLang.add(P + "no_log", "Nothing to report");
    private static final String T_AGO = GBLang.add(P + "ago", "%s ago");
    private static final String T_HINT = GBLang.add(P + "hint", "Drag to look round, scroll to zoom; click a member to find it");

    /** Its largest size (GUI units) and its margins; the side panel's width, a row's height, the header's and the foot's. */
    private static final int MAX_W = 640, MAX_H = 356, MARGIN = 6, SIDE = 138, ROW = 10, HEAD = 28, FOOT = 22;
    private static final int[] ZOOMS = TerrainSurvey.SCALES;
    private static final int TAB_NETWORK = 0, TAB_LOG = 1, TAB_WALL = 2;

    private final BlockPos pos;
    private @Nullable TerrainMap map;
    private int mapW, mapH;
    private double viewX, viewZ;
    private int zoomStep = 3;
    private boolean viewed, panning;
    private int tab, scroll, logScroll;
    private int view, layers = WarRoomDisplayBlockEntity.ALL_LAYERS;
    private boolean settingsRead;
    private long ticks;
    private final List<Button> wallButtons = new ArrayList<>();
    private @Nullable Button showButton;
    /** The view last put on the wall (to say so on its button). */
    private int shownX = Integer.MIN_VALUE, shownZ, shownZoom;

    private WarRoomScreen(BlockPos pos) {
        super(Component.translatable("block.groundburst.war_room_display"));
        this.pos = pos;
        this.viewX = pos.getX() + 0.5;
        this.viewZ = pos.getZ() + 0.5;
    }

    public static void register() {
        WarRoomDisplayBlock.screenHook = pos -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getBlockEntity(pos) instanceof WarRoomDisplayBlockEntity) {
                mc.gui.setScreen(new WarRoomScreen(pos));
            }
        };
    }

    private WarRoomPayloads.@Nullable Board board() {
        WarRoomBoard b = WarRoomBoard.of(this.pos);
        return b == null ? null : b.board();
    }

    // --- where things are -----------------------------------------------------------------------------------------------
    private int w() {
        return Math.min(MAX_W, this.width - 2 * MARGIN);
    }

    private int h() {
        return Math.min(MAX_H, this.height - 2 * MARGIN);
    }

    private int left() {
        return (this.width - w()) / 2;
    }

    private int top() {
        return (this.height - h()) / 2;
    }

    private int mapX() {
        return left() + 8;
    }

    private int mapY() {
        return top() + HEAD;
    }

    private int side() {
        return left() + w() - SIDE - 6;
    }

    private int zoom() {
        return ZOOMS[this.zoomStep];
    }

    private NetworkMap.View mapView() {
        return new NetworkMap.View(mapX(), mapY(), this.mapW, this.mapH, this.viewX, this.viewZ, zoom());
    }

    private boolean inMap(double x, double y) {
        return x >= mapX() && x < mapX() + this.mapW && y >= mapY() && y < mapY() + this.mapH;
    }

    private int listTop() {
        return mapY() + 14;
    }

    private int listRows() {
        return Math.max(1, (this.mapH - 14 - 2) / ROW);
    }

    private boolean inList(double x, double y) {
        return x >= side() && x < side() + SIDE && y >= listTop() && y < listTop() + listRows() * ROW;
    }

    @Override
    protected void init() {
        int mw = w() - 8 - SIDE - 14, mh = h() - HEAD - FOOT;
        if (this.map == null || mw != this.mapW || mh != this.mapH) {
            if (this.map != null) {
                this.map.close();
            }
            this.mapW = Math.max(64, mw);
            this.mapH = Math.max(64, mh);
            this.map = new TerrainMap(this.mapW, this.mapH, false, "dynamic/war_room_screen");
        }
        int fy = mapY() + this.mapH + 4, right = mapX() + this.mapW;
        this.showButton = addRenderableWidget(Button.builder(Component.translatable(T_SHOW), b -> showOnWall())
                .bounds(right - 92, fy, 92, 14).build());
        addRenderableWidget(Button.builder(Component.translatable(T_DISPLAY), b -> centreOnDisplay()).bounds(right - 92 - 46, fy, 44, 14).build());
        addRenderableWidget(Button.builder(Component.translatable(T_FIT), b -> fit()).bounds(right - 92 - 46 - 34, fy, 32, 14).build());
        // The wall's settings (its tab): its view, its layers.
        this.wallButtons.clear();
        int x = side() + 2, y = listTop() + 2, bw = SIDE - 4;
        this.wallButtons.add(addRenderableWidget(Button.builder(viewLabel(), b -> {
            this.view = (this.view + 1) % (WarRoomDisplayBlockEntity.VIEW_SET + 1);
            if (this.view == WarRoomDisplayBlockEntity.VIEW_SET) {
                this.view = 0;                                       // (a set view comes from Show on the wall)
            }
            b.setMessage(viewLabel());
            sendSettings(false);
        }).bounds(x, y, bw, 16).build()));
        for (int i = 0; i < T_LAYER.length; i++) {
            final int bit = i;
            this.wallButtons.add(addRenderableWidget(Button.builder(layerLabel(i), b -> {
                this.layers ^= 1 << bit;
                b.setMessage(layerLabel(bit));
                sendSettings(false);
            }).bounds(x, y + 19 * (i + 1), bw, 16).build()));
        }
        showTab();
    }

    private void showTab() {
        for (Button b : this.wallButtons) {
            b.visible = this.tab == TAB_WALL;
        }
    }

    private Component viewLabel() {
        if (this.view == WarRoomDisplayBlockEntity.VIEW_SET) {
            WarRoomPayloads.Board b = board();
            return Component.translatable(T_VIEW_SET, b == null ? zoom() : b.viewZoom());
        }
        return this.view == 0 ? Component.translatable(T_VIEW_FIT)
                : Component.translatable(T_VIEW_AT, WarRoomDisplayBlockEntity.SCALES[Math.clamp(this.view, 1, WarRoomDisplayBlockEntity.SCALES.length - 1)]);
    }

    private Component layerLabel(int i) {
        boolean on = (this.layers & (1 << i)) != 0;
        return Component.translatable(on ? T_ON : T_OFF, Component.translatable(T_LAYER[i]));
    }

    private boolean layer(int bit) {
        return (this.layers & bit) != 0;
    }

    /** The wall's settings sent: its view (this screen's, with {@code set}) and its layers. */
    private void sendSettings(boolean set) {
        int v = set ? WarRoomDisplayBlockEntity.VIEW_SET : this.view;
        ClientPacketDistributor.sendToServer(new WarRoomPayloads.Settings(this.pos, v, this.layers, Mth.floor(this.viewX), Mth.floor(this.viewZ), zoom()));
    }

    /** The wall takes this view. */
    private void showOnWall() {
        this.view = WarRoomDisplayBlockEntity.VIEW_SET;
        sendSettings(true);
        this.shownX = Mth.floor(this.viewX);
        this.shownZ = Mth.floor(this.viewZ);
        this.shownZoom = zoom();
        if (!this.wallButtons.isEmpty()) {
            this.wallButtons.getFirst().setMessage(viewLabel());
        }
    }

    private void centreOnDisplay() {
        WarRoomPayloads.Board b = board();
        BlockPos at = b == null ? this.pos : b.at();
        this.viewX = at.getX() + 0.5;
        this.viewZ = at.getZ() + 0.5;
    }

    /** Everything in: the members, the missiles and their targets, blasts and impacts (as the wall's fitted view). */
    private void fit() {
        WarRoomPayloads.Board b = board();
        if (b == null) {
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
        this.viewX = (x0 + x1) / 2 + 0.5;
        this.viewZ = (z0 + z1) / 2 + 0.5;
        double need = Math.max((x1 - x0 + 64) / (this.mapW * 0.9), (z1 - z0 + 64) / (this.mapH * 0.9));
        this.zoomStep = ZOOMS.length - 1;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (ZOOMS[i] >= need) {
                this.zoomStep = i;
                break;
            }
        }
    }

    private void clampView() {
        this.viewX = Mth.clamp(this.viewX, -30_000_000, 30_000_000);
        this.viewZ = Mth.clamp(this.viewZ, -30_000_000, 30_000_000);
    }

    @Override
    public void tick() {
        this.ticks++;
        WarRoomPayloads.Board b = board();
        if (b == null) {
            return;
        }
        if (!this.settingsRead) {
            this.settingsRead = true;
            this.view = b.view();
            this.layers = b.layers();
            for (int i = 0; i < this.wallButtons.size(); i++) {
                this.wallButtons.get(i).setMessage(i == 0 ? viewLabel() : layerLabel(i - 1));
            }
        }
        if (!this.viewed) {
            this.viewed = true;                                      // first opened: where the wall looks
            if (b.view() == WarRoomDisplayBlockEntity.VIEW_SET) {
                this.viewX = b.viewX() + 0.5;
                this.viewZ = b.viewZ() + 0.5;
                for (int i = 0; i < ZOOMS.length; i++) {
                    if (ZOOMS[i] == b.viewZoom()) {
                        this.zoomStep = i;
                    }
                }
            } else if (b.view() > 0) {
                centreOnDisplay();
                int z = WarRoomDisplayBlockEntity.SCALES[Math.clamp(b.view(), 1, WarRoomDisplayBlockEntity.SCALES.length - 1)];
                for (int i = 0; i < ZOOMS.length; i++) {
                    if (ZOOMS[i] == z) {
                        this.zoomStep = i;
                    }
                }
            } else {
                fit();
            }
        }
        if (b.powered() && this.map != null && layer(WarRoomDisplayBlockEntity.TERRAIN)) {
            this.map.request(this.pos, b.at(), this.viewX, this.viewZ, zoom());
        }
        int n = b.members().size();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, n - listRows()));
        this.logScroll = Mth.clamp(this.logScroll, 0, Math.max(0, b.log().size() - listRows()));
        if (this.showButton != null) {
            boolean shown = b.view() == WarRoomDisplayBlockEntity.VIEW_SET && b.viewX() == Mth.floor(this.viewX) && b.viewZ() == Mth.floor(this.viewZ)
                    && b.viewZoom() == zoom();
            this.showButton.setMessage(Component.translatable(shown ? T_SHOWN : T_SHOW));
            this.showButton.active = b.powered() && !shown;
        }
    }

    // --- input ------------------------------------------------------------------------------------------------------------
    /** The three tabs over the side panel: which one is under x/y (-1: none). */
    private int tabAt(double x, double y) {
        int ty = mapY();
        if (y < ty - 1 || y >= ty + 11) {
            return -1;
        }
        int tx = side();
        for (int i = 0; i < 3; i++) {
            int w = this.font.width(tabLabel(i)) + 8;
            if (x >= tx && x < tx + w) {
                return i;
            }
            tx += w + 2;
        }
        return -1;
    }

    private Component tabLabel(int i) {
        WarRoomPayloads.Board b = board();
        return switch (i) {
            case TAB_NETWORK -> Component.translatable(T_MEMBERS, b == null ? 0 : b.members().size());
            case TAB_LOG -> Component.translatable(T_LOG);
            default -> Component.translatable(T_WALL);
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int t = tabAt(event.x(), event.y());
        if (t >= 0) {
            this.tab = t;
            showTab();
            return true;
        }
        if (this.tab == TAB_NETWORK && inList(event.x(), event.y())) {
            WarRoomPayloads.Board b = board();
            int row = this.scroll + (int) ((event.y() - listTop()) / ROW);
            if (b != null && row < b.members().size()) {
                StationPayloads.MemberView m = b.members().get(row);
                this.viewX = m.at().getX() + 0.5;                    // a click centres on it
                this.viewZ = m.at().getZ() + 0.5;
            }
            return true;
        }
        if (inMap(event.x(), event.y()) && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            this.panning = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.panning) {
            this.viewX -= dx * zoom();
            this.viewZ -= dy * zoom();
            clampView();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.panning) {
            this.panning = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inList(x, y) && this.tab != TAB_WALL) {
            if (this.tab == TAB_LOG) {
                this.logScroll = Math.max(0, this.logScroll - (int) Math.signum(scrollY));
            } else {
                this.scroll = Math.max(0, this.scroll - (int) Math.signum(scrollY));
            }
            return true;
        }
        if (inMap(x, y) && scrollY != 0) {
            NetworkMap.View v = mapView();
            double wx = v.wx(x), wz = v.wz(y);
            this.zoomStep = Mth.clamp(this.zoomStep - (int) Math.signum(scrollY), 0, ZOOMS.length - 1);
            this.viewX = wx - (x - mapX() - this.mapW / 2.0) * zoom();
            this.viewZ = wz - (y - mapY() - this.mapH / 2.0) * zoom();
            clampView();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void removed() {
        if (this.map != null) {
            this.map.close();
            this.map = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- drawing ----------------------------------------------------------------------------------------------------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        g.fill(x, y, x + w(), y + h(), PANEL);
        g.outline(x, y, w(), h(), EDGE);
        drawMap(g, mouseX, mouseY);
        drawTabs(g);
        if (this.tab == TAB_NETWORK) {
            drawMembers(g);
        } else if (this.tab == TAB_LOG) {
            drawLog(g);
        } else {
            g.fill(side(), listTop(), side() + SIDE, mapY() + this.mapH, LIST_BG);
            g.outline(side() - 1, listTop() - 1, SIDE + 2, this.mapH - 14 + 2, EDGE);
        }
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = mapX(), y0 = mapY(), x1 = x0 + this.mapW, y1 = y0 + this.mapH;
        g.outline(x0 - 1, y0 - 1, this.mapW + 2, this.mapH + 2, EDGE);
        WarRoomPayloads.Board b = board();
        if (b == null || !b.powered() || this.map == null) {
            g.fill(x0, y0, x1, y1, 0xFF040C06);
            Component off = Component.translatable(b == null ? T_WAITING : T_DARK);
            g.text(this.font, off, x0 + this.mapW / 2 - this.font.width(off) / 2, y0 + this.mapH / 2 - 4, b == null ? DIM : RED);
            return;
        }
        if (layer(WarRoomDisplayBlockEntity.TERRAIN)) {
            this.map.draw(g, x0, y0, b.at(), this.viewX, this.viewZ, zoom());
        } else {
            g.fill(x0, y0, x1, y1, 0xFF040C06);
        }
        NetworkMap.View v = mapView();
        g.enableScissor(x0, y0, x1, y1);
        if (layer(WarRoomDisplayBlockEntity.REACH)) {
            NetworkMap.radarReach(g, v, b.members());
        }
        NetworkMap.deadHands(g, v, b.members(), this.ticks);
        // Impacts: rings widening and fading over two minutes, a white cross where each came down.
        for (WarRoomPayloads.ImpactView i : b.impacts()) {
            float u = Math.min(1, i.age() / (float) WarRoomDisplayBlockEntity.IMPACT_KEEP);
            int a = (int) (255 * (1 - u * 0.85f));
            int ix = v.sx(i.x() + 0.5), iz = v.sy(i.z() + 0.5);
            Phosphor.ring(g, ix, iz, Math.round(2 + u * 10), a << 24 | (i.own() ? 0xFFB040 : 0xFF4A3A));
            Phosphor.cross(g, ix, iz, 2, a << 24 | 0xF0F0F0);
        }
        if (layer(WarRoomDisplayBlockEntity.BLASTS)) {
            for (StationPayloads.BlastView bl : b.blasts()) {
                NetworkMap.blast(g, this.font, v, bl);
            }
        }
        boolean labels = layer(WarRoomDisplayBlockEntity.LABELS);
        List<StationPayloads.MemberView> ms = b.members();
        NetworkMap.members(g, this.font, v, ms, this.ticks, m -> LaunchStationScreen.stateColor(m, this.ticks),
                p -> labels ? number(ms, p) : 0, m -> false, m -> true);
        // Where the display is: a small open square.
        int dx = v.sx(b.at().getX() + 0.5), dz = v.sy(b.at().getZ() + 0.5);
        g.outline(dx - 2, dz - 2, 5, 5, WHITE);
        NetworkMap.spots(g, this.font, v, b.spots(), this.ticks);
        NetworkMap.contacts(g, this.font, v, b.contacts(), this.ticks, labels);
        if (inMap(mouseX, mouseY) && !this.panning) {
            g.fill(x0, mouseY, x1, mouseY + 1, 0x3340FF70);
            g.fill(mouseX, y0, mouseX + 1, y1, 0x3340FF70);
        }
        Phosphor.scanlines(g, x0, y0, x1, y1);
        g.disableScissor();
    }

    private static int number(List<StationPayloads.MemberView> ms, BlockPos p) {
        for (int i = 0; i < ms.size(); i++) {
            if (ms.get(i).pos().equals(p)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void drawTabs(GuiGraphicsExtractor g) {
        int x = side(), y = mapY();
        for (int i = 0; i < 3; i++) {
            Component c = tabLabel(i);
            int w = this.font.width(c) + 8;
            g.fill(x, y - 1, x + w, y + 11, this.tab == i ? SELECTED_BG : 0xFF0A200F);
            g.text(this.font, c, x + 4, y + 1, this.tab == i ? TEXT : DIM, false);
            x += w + 2;
        }
    }

    /** The members, each with what it is doing as the station lists it; a scroll bar when they do not all fit. */
    private void drawMembers(GuiGraphicsExtractor g) {
        int x = side(), y = listTop(), rows = listRows(), h = rows * ROW;
        g.fill(x, y, x + SIDE, y + h, LIST_BG);
        g.outline(x - 1, y - 1, SIDE + 2, h + 2, EDGE);
        WarRoomPayloads.Board b = board();
        List<StationPayloads.MemberView> ms = b == null ? List.of() : b.members();
        if (ms.isEmpty()) {
            g.text(this.font, this.font.plainSubstrByWidth(Component.translatable(T_NO_MEMBERS).getString(), SIDE - 6), x + 3, y + 2, DIM, false);
            return;
        }
        boolean bar = ms.size() > rows;
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        int room = (int) ((SIDE - 6 - (bar ? 4 : 0)) / scale);
        for (int i = 0; i < rows && this.scroll + i < ms.size(); i++) {
            StationPayloads.MemberView m = ms.get(this.scroll + i);
            int ry = y + i * ROW;
            String text = (this.scroll + i + 1) + ". " + LaunchStationScreen.kind(m.kind()).getString() + "  "
                    + LaunchStationScreen.state(m, b.contacts(), b.now()).getString();
            int colour = LaunchStationScreen.stateColor(m, this.ticks);
            if (!m.contact() && !m.lost() && colour != RED) {
                colour = NetworkMap.dimmer(colour);                  // asleep (not loaded): a little dimmer
            }
            g.pose().pushMatrix();
            g.pose().translate(x + 3, Math.round((ry + (ROW - 8 * scale) / 2f) * gs) / (float) gs);
            g.pose().scale(scale, scale);
            g.text(this.font, this.font.plainSubstrByWidth(text, room), 0, 0, colour, false);
            g.pose().popMatrix();
        }
        if (bar) {
            int track = h - 2, thumb = Math.max(6, track * rows / ms.size());
            int ty = y + 1 + (track - thumb) * this.scroll / Math.max(1, ms.size() - rows);
            g.fill(x + SIDE - 3, y + 1, x + SIDE - 1, y + h - 1, 0xFF0E2A16);
            g.fill(x + SIDE - 3, ty, x + SIDE - 1, ty + thumb, DIM);
        }
    }

    /** The network's log, newest first, each line with how long ago. */
    private void drawLog(GuiGraphicsExtractor g) {
        int x = side(), y = listTop(), rows = listRows(), h = rows * ROW;
        g.fill(x, y, x + SIDE, y + h, LIST_BG);
        g.outline(x - 1, y - 1, SIDE + 2, h + 2, EDGE);
        WarRoomPayloads.Board b = board();
        List<LaunchNetwork.Event> log = b == null ? List.of() : new ArrayList<>(b.log());
        if (log.isEmpty()) {
            g.text(this.font, Component.translatable(T_NO_LOG), x + 3, y + 2, DIM, false);
            return;
        }
        java.util.Collections.reverse(log);
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        int room = (int) ((SIDE - 6) / scale);
        // Two rows a line: what happened, then how long ago.
        for (int i = 0; 2 * i + 1 < rows && this.logScroll + i < log.size(); i++) {
            LaunchNetwork.Event e = log.get(this.logScroll + i);
            int ry = y + 2 * i * ROW;
            int colour = switch (e.kind()) {
                case LAUNCHED, BLAST, DEAD_HAND_ARMED, DEAD_HAND_ROCKET_DOWN -> AMBER;
                case IMPACT -> BRIGHT;
                case LOST, NO_CONTACT, FAILED, DEAD_HAND_ALERT, DEAD_HAND_FIRED -> RED;
                default -> TEXT;
            };
            String what = LaunchStationScreen.describe(e, b.members()).getString();
            String ago = Component.translatable(T_AGO, clock((int) Math.max(0, b.now() - e.time()))).getString();
            g.pose().pushMatrix();
            g.pose().translate(x + 3, Math.round((ry + (ROW - 8 * scale) / 2f) * gs) / (float) gs);
            g.pose().scale(scale, scale);
            g.text(this.font, this.font.plainSubstrByWidth(what, room), 0, 0, colour, false);
            g.text(this.font, ago, 8, Math.round(ROW / scale), DIM, false);
            g.pose().popMatrix();
        }
    }

    private static String clock(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        g.text(this.font, this.title, x + 8, y + 5, TEXT, false);
        WarRoomPayloads.Board b = board();
        if (b == null) {
            return;
        }
        boolean blink = this.ticks % 20 < 12;
        int titleEnd = x + 8 + this.font.width(this.title) + 10;
        if (b.code().isEmpty()) {
            g.text(this.font, Component.translatable(WarRoomRenderer.T_NO_NETWORK), titleEnd, y + 5, AMBER, false);
        } else {
            g.text(this.font, Component.translatable(WarRoomRenderer.T_NET, b.code()), titleEnd, y + 5, AMBER, false);
            // The counts under the title, the alert condition and the soonest impact to the right.
            int linked = 0, armed = 0, inFlight = 0, incoming = 0, soonest = -1;
            for (StationPayloads.MemberView m : b.members()) {
                if (m.launcher()) {
                    linked++;
                    armed += (m.willBeArmed() || m.standingBy()) && !m.lost() ? 1 : 0;
                }
            }
            for (StationPayloads.ContactView c : b.contacts()) {
                if (c.own()) {
                    inFlight++;
                } else {
                    incoming++;
                    soonest = soonest < 0 ? c.eta() : Math.min(soonest, c.eta());
                }
            }
            NetworkMap.label(g, this.font, Component.translatable(WarRoomRenderer.T_COUNTS, linked, armed, inFlight, incoming).getString(),
                    x + 8, y + 17, BRIGHT);
            int c = Math.clamp(b.condition(), 1, 5);
            Component cond = Component.translatable(WarRoomRenderer.T_CONDITION[c - 1]);
            int condColour = c <= 2 && !blink ? 0xFF7A1A10 : WarRoomRenderer.CONDITION_COLOUR[c - 1];
            int right = x + w() - 8;
            g.text(this.font, cond, right - this.font.width(cond), y + 5, condColour, false);
            if (soonest >= 0) {
                Component impact = Component.translatable(WarRoomRenderer.T_IMPACT, clock(soonest));
                g.text(this.font, impact, right - this.font.width(impact), y + 15, blink ? RED : 0xFF8A1A10, false);
            }
        }
        // The time of day, between the network and the condition.
        if (Minecraft.getInstance().level != null) {
            long day = (Minecraft.getInstance().level.getOverworldClockTime() + 6000) % 24000;
            String time = String.format(Locale.ROOT, "%02d:%02d", day / 1000, day % 1000 * 60 / 1000);
            g.text(this.font, time, x + w() / 2 - this.font.width(time) / 2, y + 5, DIM, false);
        }
        // The foot: where the cursor is (small, clear of the buttons at any size); the scale in the map's top right corner.
        if (b.powered()) {
            NetworkMap.View v = mapView();
            boolean over = inMap(mouseX, mouseY);
            int cx = Mth.floor(over ? v.wx(mouseX) : this.viewX), cz = Mth.floor(over ? v.wz(mouseY) : this.viewZ);
            NetworkMap.label(g, this.font, Component.translatable(T_CURSOR, cx, cz).getString(), mapX(), mapY() + this.mapH + 8, over ? TEXT : DIM);
            String zoomText = Component.translatable(T_ZOOM, zoom()).getString();
            int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
            float scale = Math.max(1, gs * 2 / 3) / (float) gs;
            NetworkMap.label(g, this.font, zoomText, mapX() + this.mapW - 4 - (int) Math.ceil(this.font.width(zoomText) * scale), mapY() + 4, DIM);
        }
        int sideX = side();
        if (this.tab == TAB_WALL) {
            Component shows = b.code().isEmpty() ? Component.translatable(T_SHOWS_NONE) : Component.translatable(T_SHOWS, b.code());
            int ly = listTop() + 2 + 19 * (T_LAYER.length + 1) + 4;
            for (var line : this.font.split(shows, SIDE - 6)) {
                g.text(this.font, line, sideX + 3, ly, AMBER, false);
                ly += 10;
            }
        }
        // For its first ten seconds open, how to look round: faint, in the map's bottom corner.
        if (this.ticks < 200 && b.powered()) {
            NetworkMap.label(g, this.font, this.font.plainSubstrByWidth(Component.translatable(T_HINT).getString(), (int) ((this.mapW - 8) * 1.4)),
                    mapX() + 4, mapY() + this.mapH - 10, this.ticks < 160 ? TEXT : DIM);
        }
    }
}
