package dev.groundzero.client.screen;

import static dev.groundzero.client.screen.Phosphor.AMBER;
import static dev.groundzero.client.screen.Phosphor.BRIGHT;
import static dev.groundzero.client.screen.Phosphor.DIM;
import static dev.groundzero.client.screen.Phosphor.DIM_RED;
import static dev.groundzero.client.screen.Phosphor.EDGE;
import static dev.groundzero.client.screen.Phosphor.LIST_BG;
import static dev.groundzero.client.screen.Phosphor.PANEL;
import static dev.groundzero.client.screen.Phosphor.RED;
import static dev.groundzero.client.screen.Phosphor.SELECTED_BG;
import static dev.groundzero.client.screen.Phosphor.TEXT;
import static dev.groundzero.client.screen.Phosphor.WHITE;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.launch.LaunchComputerBlock;
import dev.groundzero.content.launch.LaunchPayloads;
import dev.groundzero.content.launch.LaunchSiloBlockEntity;
import dev.groundzero.content.launch.MissileEntity;
import dev.groundzero.content.launch.TerrainSurvey;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Launch Computer's screen (DESIGN.md §17.3, basic version): on the left a green phosphor map of the ground round the
 * computer (its terrain survey, {@link TerrainMap}) with the linked silos, their targets and every missile in the air;
 * drag it to look round, scroll to zoom, click to aim the picked silo there. On the right a scrolling list of the
 * linked silos. Pick a silo, aim it (click the map, or type X and Z and press Set) and ARM it; each silo has its own
 * target and is armed on its own. LAUNCH fires every armed silo.
 */
public class LaunchComputerScreen extends Screen {
    private static final String T_TITLE = GZLang.add(GroundZero.MODID + ".computer.title", "Launch Computer");
    private static final String T_TARGET = GZLang.add(GroundZero.MODID + ".computer.target_of", "Target of silo %s");
    private static final String T_SET = GZLang.add(GroundZero.MODID + ".computer.set", "Set");
    private static final String T_SILOS = GZLang.add(GroundZero.MODID + ".computer.silo_count", "Silos (%s)");
    private static final String T_NO_SILOS = GZLang.add(GroundZero.MODID + ".computer.no_silos", "No silo within 256 blocks");
    private static final String T_NO_TARGET = GZLang.add(GroundZero.MODID + ".computer.no_target", "no target");
    private static final String T_ARM = GZLang.add(GroundZero.MODID + ".computer.arm", "ARM");
    private static final String T_DISARM = GZLang.add(GroundZero.MODID + ".computer.disarm", "DISARM");
    private static final String T_LAUNCH = GZLang.add(GroundZero.MODID + ".computer.launch_armed", "LAUNCH (%s)");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".computer.no_power", "NO POWER");
    private static final String T_RANGE = GZLang.add(GroundZero.MODID + ".computer.range", "Range %s m, flight %s s");
    private static final String T_TOO_FAR = GZLang.add(GroundZero.MODID + ".computer.too_far", "Out of range (max %s m)");
    private static final String T_CONTACTS = GZLang.add(GroundZero.MODID + ".computer.contacts", "%s in the air");
    private static final String T_CENTRE = GZLang.add(GroundZero.MODID + ".computer.centre", "Centre");
    private static final String T_CURSOR = GZLang.add(GroundZero.MODID + ".computer.cursor", "X %s  Z %s");
    private static final String T_ZOOM = GZLang.add(GroundZero.MODID + ".computer.zoom", "1:%s");
    private static final String T_ARMED = GZLang.add(GroundZero.MODID + ".computer.silo.armed", "ARMED");
    private static final String[] T_SILO_STATE = {
            GZLang.add(GroundZero.MODID + ".computer.silo.not_formed", "incomplete"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_missile", "no missile"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_warhead", "no warhead"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_power", "charging"),
            GZLang.add(GroundZero.MODID + ".computer.silo.ready", "READY"),
            GZLang.add(GroundZero.MODID + ".computer.silo.counting", "T-%s")};

    private static final int W = 336, H = 214, MAP = 184;
    /** The right-hand column: its left edge (from the panel's) and width; the silo list's rows. */
    private static final int COL = MAP + 22, CW = W - COL - 8, ROW = 12, ROWS = 5;
    private static final int LIST_Y = 29, TARGET_Y = 94, BOX_Y = 105, SET_Y = 123, RANGE_Y = 143, ARM_Y = 166, LAUNCH_Y = 186;
    /** Blocks a map pixel at each zoom step. */
    private static final int[] ZOOMS = {1, 2, 4, 8};

    /** The latest scope from the server (for whichever computer screen is open). */
    private static LaunchPayloads.@Nullable Scope latest;

    public static void register() {
        LaunchComputerBlock.screenHook = pos -> Minecraft.getInstance().gui.setScreen(new LaunchComputerScreen(pos));
        LaunchPayloads.scopeHook = scope -> latest = scope;
        TerrainMap.register();
    }

    private final BlockPos pos;
    private @Nullable EditBox xBox, zBox;
    private @Nullable Button setButton, armButton, launchButton;
    /** The picked silo (by position, so the pick survives the list changing), and the list's scroll. */
    private @Nullable BlockPos selected;
    private int scroll;
    /** The silo whose target the X/Z boxes show (they are refilled when the pick changes). */
    private @Nullable BlockPos boxesFor;
    /** The map: its picture, the point at its centre, its zoom step; a press on it (a drag pans, a click aims). */
    private @Nullable TerrainMap map;
    private double viewX, viewZ;
    private int zoomStep = 1;
    private boolean pressed, dragged;
    private double dragged2;

    public LaunchComputerScreen(BlockPos pos) {
        super(Component.translatable(T_TITLE));
        this.pos = pos;
        this.viewX = pos.getX() + 0.5;
        this.viewZ = pos.getZ() + 0.5;
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    private int col() {
        return left() + COL;
    }

    private int mapX() {
        return left() + 8;
    }

    private int mapY() {
        return top() + 14;
    }

    private int zoom() {
        return ZOOMS[this.zoomStep];
    }

    private LaunchPayloads.@Nullable Scope scope() {
        return latest != null && latest.pos().equals(this.pos) ? latest : null;
    }

    private boolean on() {
        LaunchPayloads.Scope s = scope();
        return s != null && s.formed() && s.powered();
    }

    private List<LaunchPayloads.Silo> silos() {
        LaunchPayloads.Scope s = scope();
        return s == null ? List.of() : s.silos();
    }

    private LaunchPayloads.@Nullable Silo selectedSilo() {
        for (LaunchPayloads.Silo silo : silos()) {
            if (silo.pos().equals(this.selected)) {
                return silo;
            }
        }
        return null;
    }

    private int index(LaunchPayloads.Silo silo) {
        return silos().indexOf(silo) + 1;
    }

    // --- map coordinates -------------------------------------------------------------------------------------------------
    private int screenX(double wx) {
        return mapX() + MAP / 2 + (int) Math.floor((wx - this.viewX) / zoom());
    }

    private int screenY(double wz) {
        return mapY() + MAP / 2 + (int) Math.floor((wz - this.viewZ) / zoom());
    }

    private double worldX(double sx) {
        return this.viewX + (sx - mapX() - MAP / 2.0) * zoom();
    }

    private double worldZ(double sy) {
        return this.viewZ + (sy - mapY() - MAP / 2.0) * zoom();
    }

    private boolean inMap(double x, double y) {
        return x >= mapX() && x < mapX() + MAP && y >= mapY() && y < mapY() + MAP;
    }

    /** Keeps the view over the surveyed ground. */
    private void clampView() {
        this.viewX = Mth.clamp(this.viewX, this.pos.getX() - TerrainSurvey.RANGE, this.pos.getX() + TerrainSurvey.RANGE);
        this.viewZ = Mth.clamp(this.viewZ, this.pos.getZ() - TerrainSurvey.RANGE, this.pos.getZ() + TerrainSurvey.RANGE);
    }

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, true));
        if (this.map == null) {
            this.map = new TerrainMap(MAP, MAP);
        }
        int x = col(), half = (CW - 4) / 2;
        this.xBox = addRenderableWidget(new EditBox(this.font, x, top() + BOX_Y, half, 16, Component.literal("X")));
        this.zBox = addRenderableWidget(new EditBox(this.font, x + CW - half, top() + BOX_Y, half, 16, Component.literal("Z")));
        this.xBox.setMaxLength(8);
        this.zBox.setMaxLength(8);
        this.boxesFor = null;
        this.setButton = addRenderableWidget(Button.builder(Component.translatable(T_SET), b -> sendTarget()).bounds(x, top() + SET_Y, CW, 16).build());
        this.armButton = addRenderableWidget(Button.builder(Component.translatable(T_ARM), b -> toggleArm()).bounds(x, top() + ARM_Y, CW, 16).build());
        this.launchButton = addRenderableWidget(Button.builder(Component.translatable(T_LAUNCH, 0),
                b -> ClientPacketDistributor.sendToServer(new LaunchPayloads.Launch(this.pos))).bounds(x, top() + LAUNCH_Y, CW, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(T_CENTRE), b -> {
            this.viewX = this.pos.getX() + 0.5;
            this.viewZ = this.pos.getZ() + 0.5;
        }).bounds(mapX() + MAP - 44, mapY() + MAP + 2, 44, 14).build());
    }

    private void sendTarget() {
        LaunchPayloads.Silo silo = selectedSilo();
        if (silo == null || this.xBox == null || this.zBox == null) {
            return;
        }
        try {
            int x = Integer.parseInt(this.xBox.getValue().trim()), z = Integer.parseInt(this.zBox.getValue().trim());
            ClientPacketDistributor.sendToServer(new LaunchPayloads.Target(this.pos, silo.pos(), x, z));
        } catch (NumberFormatException ignored) {
            // not numbers: nothing to set
        }
    }

    private void aimAt(int x, int z) {
        LaunchPayloads.Silo silo = selectedSilo();
        if (silo == null || silo.countdown() >= 0) {
            return;
        }
        if (this.xBox != null && this.zBox != null) {
            this.xBox.setValue(Integer.toString(x));
            this.zBox.setValue(Integer.toString(z));
        }
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Target(this.pos, silo.pos(), x, z));
    }

    private void toggleArm() {
        LaunchPayloads.Silo silo = selectedSilo();
        if (silo != null) {
            ClientPacketDistributor.sendToServer(new LaunchPayloads.Arm(this.pos, silo.pos(), !silo.armed()));
        }
    }

    private static boolean ready(LaunchPayloads.Silo silo) {
        return silo.readiness() == LaunchSiloBlockEntity.Readiness.READY.ordinal();
    }

    private static double range(LaunchPayloads.Silo silo) {
        return Math.sqrt(Mth.square(silo.targetX() - silo.pos().getX()) + Mth.square(silo.targetZ() - silo.pos().getZ()));
    }

    @Override
    public void tick() {
        List<LaunchPayloads.Silo> silos = silos();
        if (selectedSilo() == null) {
            this.selected = silos.isEmpty() ? null : silos.getFirst().pos();
        }
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, silos.size() - ROWS));
        LaunchPayloads.Silo silo = selectedSilo();
        // Fill the boxes with the picked silo's target when the pick changes (or its first scope arrives).
        if (this.xBox != null && this.zBox != null && silo != null && !silo.pos().equals(this.boxesFor)) {
            this.boxesFor = silo.pos();
            this.xBox.setValue(silo.hasTarget() ? Integer.toString(silo.targetX()) : "");
            this.zBox.setValue(silo.hasTarget() ? Integer.toString(silo.targetZ()) : "");
        }
        boolean on = on();
        if (on && this.map != null) {
            this.map.request(this.pos, this.viewX, this.viewZ, zoom());
        }
        int armed = (int) silos.stream().filter(LaunchPayloads.Silo::armed).count();
        if (this.setButton != null && this.armButton != null && this.launchButton != null) {
            boolean counting = silo != null && silo.countdown() >= 0;
            this.setButton.active = on && silo != null && !counting;
            boolean canArm = silo != null && ready(silo) && silo.hasTarget() && range(silo) <= MissileEntity.MAX_RANGE;
            this.armButton.active = on && silo != null && (silo.armed() || canArm);
            this.armButton.setMessage(Component.translatable(silo != null && silo.armed() ? T_DISARM : T_ARM));
            this.launchButton.active = on && armed > 0;
            this.launchButton.setMessage(Component.translatable(T_LAUNCH, armed));
        }
    }

    // --- input --------------------------------------------------------------------------------------------------------------
    private boolean inList(double x, double y) {
        return x >= col() && x < col() + CW && y >= top() + LIST_Y && y < top() + LIST_Y + ROWS * ROW;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (inList(event.x(), event.y())) {
            int row = this.scroll + (int) ((event.y() - top() - LIST_Y) / ROW);
            List<LaunchPayloads.Silo> silos = silos();
            if (row < silos.size()) {
                this.selected = silos.get(row).pos();
            }
            return true;
        }
        if (inMap(event.x(), event.y()) && event.button() == 0 && on()) {
            this.pressed = true;                             // a drag pans; a click (on release) aims
            this.dragged = false;
            this.dragged2 = 0;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.pressed) {
            this.dragged2 += dx * dx + dy * dy;
            if (this.dragged2 > 9) {
                this.dragged = true;
            }
            if (this.dragged) {
                this.viewX -= dx * zoom();
                this.viewZ -= dy * zoom();
                clampView();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.pressed) {
            this.pressed = false;
            if (!this.dragged && inMap(event.x(), event.y())) {
                aimAt((int) Math.floor(worldX(event.x())), (int) Math.floor(worldZ(event.y())));
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inList(x, y)) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, silos().size() - ROWS));
            return true;
        }
        if (inMap(x, y) && scrollY != 0) {
            // Zoom about the point under the cursor.
            double wx = worldX(x), wz = worldZ(y);
            this.zoomStep = Mth.clamp(this.zoomStep - (int) Math.signum(scrollY), 0, ZOOMS.length - 1);
            this.viewX = wx - (x - mapX() - MAP / 2.0) * zoom();
            this.viewZ = wz - (y - mapY() - MAP / 2.0) * zoom();
            clampView();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, false));
        super.onClose();
    }

    @Override
    public void removed() {
        if (this.map != null) {
            this.map.close();
            this.map = null;
        }
        super.removed();
    }

    // --- drawing ------------------------------------------------------------------------------------------------------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        g.fill(left(), top(), left() + W, top() + H, PANEL);
        g.outline(left(), top(), W, H, EDGE);
        drawMap(g, mouseX, mouseY);
        drawList(g);
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = mapX(), y0 = mapY(), x1 = x0 + MAP, y1 = y0 + MAP;
        g.outline(x0 - 1, y0 - 1, MAP + 2, MAP + 2, EDGE);
        LaunchPayloads.Scope s = scope();
        if (!on() || this.map == null) {
            g.fill(x0, y0, x1, y1, 0xFF040C06);
            Component off = Component.translatable(T_NO_POWER);
            g.text(this.font, off, x0 + MAP / 2 - this.font.width(off) / 2, y0 + MAP / 2 - 4, RED);
            return;
        }
        this.map.draw(g, x0, y0, this.pos, this.viewX, this.viewZ, zoom());
        g.enableScissor(x0, y0, x1, y1);
        // The edge of the surveyed ground.
        int ex0 = screenX(this.pos.getX() - TerrainSurvey.RANGE), ex1 = screenX(this.pos.getX() + TerrainSurvey.RANGE);
        int ez0 = screenY(this.pos.getZ() - TerrainSurvey.RANGE), ez1 = screenY(this.pos.getZ() + TerrainSurvey.RANGE);
        Phosphor.line(g, ex0, ez0, ex1, ez0, DIM, 3);
        Phosphor.line(g, ex0, ez1, ex1, ez1, DIM, 3);
        Phosphor.line(g, ex0, ez0, ex0, ez1, DIM, 3);
        Phosphor.line(g, ex1, ez0, ex1, ez1, DIM, 3);
        // The computer, each silo and its target (the picked silo's bright; armed silos joined to theirs by a red line).
        int cx = screenX(this.pos.getX() + 0.5), cz = screenY(this.pos.getZ() + 0.5);
        g.fill(cx - 2, cz - 2, cx + 3, cz + 3, WHITE);
        g.fill(cx - 1, cz - 1, cx + 2, cz + 2, 0xFF061A0C);
        for (LaunchPayloads.Silo silo : s.silos()) {
            boolean picked = silo.pos().equals(this.selected);
            int sx = screenX(silo.pos().getX() + 0.5), sz = screenY(silo.pos().getZ() + 0.5);
            if (silo.hasTarget()) {
                int tx = screenX(silo.targetX() + 0.5), tz = screenY(silo.targetZ() + 0.5);
                if (silo.armed() || picked) {
                    Phosphor.line(g, sx, sz, tx, tz, silo.armed() ? 0xCCFF4A3A : 0x88FF4A3A, 2);
                }
                Phosphor.cross(g, tx, tz, 3, picked ? RED : DIM_RED);
            }
            g.fill(sx - 2, sz - 2, sx + 3, sz + 3, silo.armed() ? RED : picked ? BRIGHT : DIM);
        }
        // Missiles in the air.
        for (LaunchPayloads.Contact c : s.contacts()) {
            int mx = screenX(c.x()), mz = screenY(c.z());
            Phosphor.line(g, mx, mz, screenX(c.targetX()), screenY(c.targetZ()), 0x88FFB040, 2);
            g.fill(mx - 1, mz - 1, mx + 2, mz + 2, AMBER);
            Phosphor.line(g, mx, mz, mx + (int) (Math.sin(c.heading()) * 5), mz + (int) (Math.cos(c.heading()) * 5), AMBER, 0);
        }
        // The cursor's crosshair.
        if (inMap(mouseX, mouseY) && !this.dragged) {
            g.fill(x0, mouseY, x1, mouseY + 1, 0x3340FF70);
            g.fill(mouseX, y0, mouseX + 1, y1, 0x3340FF70);
        }
        Phosphor.scanlines(g, x0, y0, x1, y1);
        g.disableScissor();
    }

    /** The silo list: one row a silo, the picked one lit, scrolled with the mouse wheel. */
    private void drawList(GuiGraphicsExtractor g) {
        int x = col(), y = top() + LIST_Y, h = ROWS * ROW;
        g.fill(x, y, x + CW, y + h, LIST_BG);
        g.outline(x - 1, y - 1, CW + 2, h + 2, EDGE);
        List<LaunchPayloads.Silo> silos = silos();
        if (silos.isEmpty()) {
            List<FormattedCharSequence> lines = this.font.split(Component.translatable(T_NO_SILOS), CW - 6);
            for (int i = 0; i < lines.size() && i < ROWS; i++) {
                g.text(this.font, lines.get(i), x + 3, y + 3 + i * 10, DIM, false);
            }
            return;
        }
        boolean bar = silos.size() > ROWS;
        int textW = CW - 6 - (bar ? 4 : 0);
        for (int i = 0; i < ROWS && this.scroll + i < silos.size(); i++) {
            LaunchPayloads.Silo silo = silos.get(this.scroll + i);
            int ry = y + i * ROW;
            if (silo.pos().equals(this.selected)) {
                g.fill(x, ry, x + CW - (bar ? 4 : 0), ry + ROW, SELECTED_BG);
            }
            int r = Mth.clamp(silo.readiness(), 0, T_SILO_STATE.length - 1);
            boolean counting = r == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal();
            Component state = counting ? Component.translatable(T_SILO_STATE[r], (silo.countdown() + 19) / 20)
                    : silo.armed() ? Component.translatable(T_ARMED) : Component.translatable(T_SILO_STATE[r]);
            int color = counting ? AMBER : silo.armed() ? RED : ready(silo) ? BRIGHT : DIM;
            // Right: how far its target is (or that it has none); left: its number and state, cut to what is left.
            String aim = silo.hasTarget() ? (int) range(silo) + "m" : Component.translatable(T_NO_TARGET).getString();
            int aimW = this.font.width(aim);
            g.text(this.font, aim, x + 3 + textW - aimW, ry + 2, silo.hasTarget() && range(silo) > MissileEntity.MAX_RANGE ? RED : DIM, false);
            String label = (this.scroll + i + 1) + ". " + state.getString();
            g.text(this.font, this.font.plainSubstrByWidth(label, textW - aimW - 4), x + 3, ry + 2, color, false);
        }
        if (bar) {                                                     // the scroll bar
            int track = h - 2, thumb = Math.max(6, track * ROWS / silos.size());
            int ty = y + 1 + (track - thumb) * this.scroll / Math.max(1, silos.size() - ROWS);
            g.fill(x + CW - 3, y + 1, x + CW - 1, y + h - 1, 0xFF0E2A16);
            g.fill(x + CW - 3, ty, x + CW - 1, ty + thumb, DIM);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = col();
        LaunchPayloads.Scope s = scope();
        // Header: the title over the map, the missiles in the air at the map's right, the power at the far right.
        g.text(this.font, this.title, left() + 8, top() + 4, TEXT);
        if (s != null && !s.contacts().isEmpty()) {
            Component c = Component.translatable(T_CONTACTS, s.contacts().size());
            g.text(this.font, c, mapX() + MAP - this.font.width(c), top() + 4, AMBER);
        }
        if (s != null) {
            Component energy = Component.literal(s.energy() + "%");
            g.text(this.font, energy, left() + W - 8 - this.font.width(energy), top() + 4, s.powered() ? BRIGHT : RED);
        }
        // Under the map: the point under the cursor (or the view's centre) and the zoom.
        if (on()) {
            boolean over = inMap(mouseX, mouseY);
            int cx = (int) Math.floor(over ? worldX(mouseX) : this.viewX), cz = (int) Math.floor(over ? worldZ(mouseY) : this.viewZ);
            g.text(this.font, Component.translatable(T_CURSOR, cx, cz), mapX(), mapY() + MAP + 5, over ? TEXT : DIM, false);
            Component zoom = Component.translatable(T_ZOOM, zoom());
            g.text(this.font, zoom, mapX() + MAP - 48 - this.font.width(zoom), mapY() + MAP + 5, DIM, false);
        }
        g.text(this.font, this.font.plainSubstrByWidth(Component.translatable(T_SILOS, silos().size()).getString(), CW - 30), x, top() + 16, TEXT);
        LaunchPayloads.Silo silo = selectedSilo();
        if (silo != null) {
            String label = Component.translatable(T_TARGET, index(silo)).getString();
            g.text(this.font, this.font.plainSubstrByWidth(label, CW), x, top() + TARGET_Y, TEXT);
            if (silo.hasTarget()) {
                double range = range(silo);
                Component line = range > MissileEntity.MAX_RANGE ? Component.translatable(T_TOO_FAR, MissileEntity.MAX_RANGE)
                        : Component.translatable(T_RANGE, (int) range, flightSeconds(range));
                List<FormattedCharSequence> lines = this.font.split(line, CW);
                for (int i = 0; i < lines.size() && i < 2; i++) {
                    g.text(this.font, lines.get(i), x, top() + RANGE_Y + i * 10, range > MissileEntity.MAX_RANGE ? RED : TEXT, false);
                }
            }
        }
    }

    /** About how long the missile takes, as its flight goes: ignition, then its path at its speed. */
    private static int flightSeconds(double range) {
        double apex = Mth.clamp(range * MissileEntity.APEX_SHARE, MissileEntity.MIN_APEX, MissileEntity.MAX_APEX);
        return (int) Math.round((MissileEntity.IGNITION + Mth.clamp((range + 2 * apex) / MissileEntity.SPEED, 60, 1200)) / 20);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
