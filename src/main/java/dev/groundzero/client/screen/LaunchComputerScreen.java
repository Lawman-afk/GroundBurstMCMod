package dev.groundzero.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.launch.LaunchComputerBlock;
import dev.groundzero.content.launch.LaunchComputerBlockEntity;
import dev.groundzero.content.launch.LaunchPayloads;
import dev.groundzero.content.launch.LaunchSiloBlockEntity;
import dev.groundzero.content.launch.MissileEntity;
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
 * The Launch Computer's screen (DESIGN.md §17.3, §18.1, basic version): on the left a green phosphor radar scope of
 * the 512 blocks round the computer (north up) with its sweep, range rings, the linked silos and their targets, and
 * every missile in the air; on the right a scrolling list of the linked silos. Pick a silo, then aim it (click the scope,
 * or type X and Z and press Set) and ARM it; each silo has its own target and is armed on its own. LAUNCH fires every
 * armed silo.
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
    private static final String T_ARMED = GZLang.add(GroundZero.MODID + ".computer.silo.armed", "ARMED");
    private static final String[] T_SILO_STATE = {
            GZLang.add(GroundZero.MODID + ".computer.silo.not_formed", "incomplete"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_missile", "no missile"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_warhead", "no warhead"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_power", "charging"),
            GZLang.add(GroundZero.MODID + ".computer.silo.ready", "READY"),
            GZLang.add(GroundZero.MODID + ".computer.silo.counting", "T-%s")};

    private static final int W = 336, H = 214, SCOPE = 92;
    /** The right-hand column: its left edge (from the panel's) and width; the silo list's rows. */
    private static final int COL = 2 * SCOPE + 22, CW = W - COL - 8, ROW = 12, ROWS = 5;
    private static final int LIST_Y = 29, TARGET_Y = 94, BOX_Y = 105, SET_Y = 123, RANGE_Y = 143, ARM_Y = 166, LAUNCH_Y = 186;
    private static final int PANEL = 0xF0101410, EDGE = 0xFF3A4A3A, PHOSPHOR = 0xFF40FF70, DIM = 0xFF1C6A30, FAINT = 0x5530A048,
            TEXT = 0xFFC8F0C8, RED = 0xFFFF4A3A, DIM_RED = 0xAA902A20, AMBER = 0xFFFFB040, WHITE = 0xFFF0F0F0, LIST_BG = 0xFF061A0C,
            SELECTED_BG = 0xFF14401E;

    /** The latest scope from the server (for whichever computer screen is open). */
    private static LaunchPayloads.@Nullable Scope latest;

    public static void register() {
        LaunchComputerBlock.screenHook = pos -> Minecraft.getInstance().gui.setScreen(new LaunchComputerScreen(pos));
        LaunchPayloads.scopeHook = scope -> latest = scope;
    }

    private final BlockPos pos;
    private @Nullable EditBox xBox, zBox;
    private @Nullable Button setButton, armButton, launchButton;
    /** The picked silo (by position, so the pick survives the list changing), and the list's scroll. */
    private @Nullable BlockPos selected;
    private int scroll;
    /** The silo whose target the X/Z boxes show (they are refilled when the pick changes). */
    private @Nullable BlockPos boxesFor;

    public LaunchComputerScreen(BlockPos pos) {
        super(Component.translatable(T_TITLE));
        this.pos = pos;
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

    private int cx() {
        return left() + 8 + SCOPE;
    }

    private int cy() {
        return top() + 14 + SCOPE;
    }

    private LaunchPayloads.@Nullable Scope scope() {
        return latest != null && latest.pos().equals(this.pos) ? latest : null;
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

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, true));
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
        LaunchPayloads.Scope s = scope();
        boolean on = s != null && s.formed() && s.powered();
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
        double dx = event.x() - cx(), dy = event.y() - cy();
        LaunchPayloads.Silo silo = selectedSilo();
        if (dx * dx + dy * dy <= SCOPE * SCOPE && silo != null && silo.countdown() < 0) {
            double k = LaunchComputerBlockEntity.RADAR_RANGE / (double) SCOPE;
            int x = this.pos.getX() + (int) Math.round(dx * k), z = this.pos.getZ() + (int) Math.round(dy * k);
            if (this.xBox != null && this.zBox != null) {
                this.xBox.setValue(Integer.toString(x));
                this.zBox.setValue(Integer.toString(z));
            }
            ClientPacketDistributor.sendToServer(new LaunchPayloads.Target(this.pos, silo.pos(), x, z));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inList(x, y)) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, silos().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, false));
        super.onClose();
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

    /** A filled disc, row by row. */
    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 6);
        for (int i = 0; i < steps; i++) {
            double a = i * 2 * Math.PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, int dash) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            if (dash > 0 && (i / dash) % 2 == 1) {
                continue;
            }
            int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private boolean onScope(int x, int y) {
        return (x - cx()) * (x - cx()) + (y - cy()) * (y - cy()) <= SCOPE * SCOPE;
    }

    private void drawScope(GuiGraphicsExtractor g, float partial) {
        int cx = cx(), cy = cy();
        disc(g, cx, cy, SCOPE, 0xFF061A0C);
        LaunchPayloads.Scope s = scope();
        boolean on = s != null && s.formed() && s.powered();
        for (int k = 1; k <= 3; k++) {
            ring(g, cx, cy, SCOPE * k / 3, on ? DIM : 0xFF0E2A16);
        }
        line(g, cx - SCOPE, cy, cx + SCOPE, cy, on ? FAINT : 0x22104010, 0);
        line(g, cx, cy - SCOPE, cx, cy + SCOPE, on ? FAINT : 0x22104010, 0);
        if (!on) {
            Component off = Component.translatable(T_NO_POWER);
            g.text(this.font, off, cx - this.font.width(off) / 2, cy - 4, RED);
            return;
        }
        // The sweep: one turn every 4 s, with a fading afterglow behind it.
        double now = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;
        double sweep = (now / 80.0) * 2 * Math.PI;
        for (int k = 0; k < 18; k++) {
            double a = sweep - k * 0.035;
            int alpha = (int) (200 * (1 - k / 18.0));
            line(g, cx, cy, cx + (int) (Math.cos(a) * SCOPE), cy + (int) (Math.sin(a) * SCOPE), alpha << 24 | 0x40FF70, 0);
        }
        double k = SCOPE / (double) LaunchComputerBlockEntity.RADAR_RANGE;
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, WHITE);                       // the computer itself
        // Each silo, and its target: the picked silo's bright, armed silos joined to theirs by a red line.
        for (LaunchPayloads.Silo silo : s.silos()) {
            boolean picked = silo.pos().equals(this.selected);
            int x = cx + (int) ((silo.pos().getX() - this.pos.getX()) * k), y = cy + (int) ((silo.pos().getZ() - this.pos.getZ()) * k);
            if (silo.hasTarget()) {
                int tx = cx + (int) ((silo.targetX() - this.pos.getX()) * k), ty = cy + (int) ((silo.targetZ() - this.pos.getZ()) * k);
                if (onScope(tx, ty)) {
                    int c = picked ? RED : DIM_RED;
                    if (silo.armed() || picked) {
                        line(g, x, y, tx, ty, silo.armed() ? 0xAAFF4A3A : 0x66FF4A3A, 2);
                    }
                    line(g, tx - 3, ty - 3, tx + 3, ty + 3, c, 0);
                    line(g, tx - 3, ty + 3, tx + 3, ty - 3, c, 0);
                }
            }
            if (onScope(x, y)) {
                g.fill(x - 2, y - 2, x + 3, y + 3, silo.armed() ? RED : picked ? PHOSPHOR : DIM);
            }
        }
        for (LaunchPayloads.Contact c : s.contacts()) {
            int x = cx + (int) ((c.x() - this.pos.getX()) * k), y = cy + (int) ((c.z() - this.pos.getZ()) * k);
            int tx = cx + (int) ((c.targetX() - this.pos.getX()) * k), ty = cy + (int) ((c.targetZ() - this.pos.getZ()) * k);
            line(g, x, y, tx, ty, 0x88FFB040, 2);
            g.fill(x - 1, y - 1, x + 2, y + 2, AMBER);
            line(g, x, y, x + (int) (Math.sin(c.heading()) * 5), y + (int) (Math.cos(c.heading()) * 5), AMBER, 0);
        }
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
            int color = counting ? AMBER : silo.armed() ? RED : ready(silo) ? PHOSPHOR : DIM;
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
        // Header: the title over the scope, the missiles in the air at the scope's right, the power at the far right.
        g.text(this.font, this.title, left() + 8, top() + 4, TEXT);
        if (s != null && !s.contacts().isEmpty()) {
            Component c = Component.translatable(T_CONTACTS, s.contacts().size());
            g.text(this.font, c, left() + 2 * SCOPE + 16 - this.font.width(c), top() + 4, AMBER);
        }
        if (s != null) {
            Component energy = Component.literal(s.energy() + "%");
            g.text(this.font, energy, left() + W - 8 - this.font.width(energy), top() + 4, s.powered() ? PHOSPHOR : RED);
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

    /** About how long the missile takes, as MissileEntity flies it: ignition, then its path at its speed. */
    private static int flightSeconds(double range) {
        double apex = Mth.clamp(range * MissileEntity.APEX_SHARE, MissileEntity.MIN_APEX, MissileEntity.MAX_APEX);
        return (int) Math.round((MissileEntity.IGNITION + Mth.clamp((range + 2 * apex) / MissileEntity.SPEED, 60, 1200)) / 20);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
