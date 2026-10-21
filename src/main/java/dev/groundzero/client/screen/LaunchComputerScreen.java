package dev.groundzero.client.screen;

import java.util.ArrayList;
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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Launch Computer's screen (DESIGN.md §17.3, §18.1, basic version): on the left a green phosphor radar scope of
 * the 512 blocks round the computer (north up) with its sweep, range rings, the linked silos, the target and every
 * missile in the air; on the right the target's coordinates, the silos and their state, and a guarded launch: ARM,
 * then LAUNCH. Click the scope to put the target there.
 */
public class LaunchComputerScreen extends Screen {
    private static final String T_TITLE = GZLang.add(GroundZero.MODID + ".computer.title", "Launch Computer");
    private static final String T_TARGET = GZLang.add(GroundZero.MODID + ".computer.target", "Target X / Z");
    private static final String T_SET = GZLang.add(GroundZero.MODID + ".computer.set", "Set");
    private static final String T_SILOS = GZLang.add(GroundZero.MODID + ".computer.silos", "Silos");
    private static final String T_NO_SILOS = GZLang.add(GroundZero.MODID + ".computer.no_silos", "No silo within 256 blocks");
    private static final String T_ARM = GZLang.add(GroundZero.MODID + ".computer.arm", "ARM");
    private static final String T_ARMED = GZLang.add(GroundZero.MODID + ".computer.armed", "ARMED");
    private static final String T_LAUNCH = GZLang.add(GroundZero.MODID + ".computer.launch", "LAUNCH");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".computer.no_power", "NO POWER");
    private static final String T_RANGE = GZLang.add(GroundZero.MODID + ".computer.range", "Range %s m, flight %s s");
    private static final String T_TOO_FAR = GZLang.add(GroundZero.MODID + ".computer.too_far", "Out of range (max %s m)");
    private static final String T_CONTACTS = GZLang.add(GroundZero.MODID + ".computer.contacts", "%s in the air");
    private static final String[] T_SILO_STATE = {
            GZLang.add(GroundZero.MODID + ".computer.silo.not_formed", "incomplete"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_missile", "no missile"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_warhead", "no warhead"),
            GZLang.add(GroundZero.MODID + ".computer.silo.no_power", "charging"),
            GZLang.add(GroundZero.MODID + ".computer.silo.ready", "READY"),
            GZLang.add(GroundZero.MODID + ".computer.silo.counting", "T-%s")};

    private static final int W = 316, H = 206, SCOPE = 92;
    private static final int PANEL = 0xF0101410, EDGE = 0xFF3A4A3A, PHOSPHOR = 0xFF40FF70, DIM = 0xFF1C6A30, FAINT = 0x5530A048,
            TEXT = 0xFFC8F0C8, RED = 0xFFFF4A3A, AMBER = 0xFFFFB040, WHITE = 0xFFF0F0F0;

    /** The latest scope from the server (for whichever computer screen is open). */
    private static LaunchPayloads.@Nullable Scope latest;

    public static void register() {
        LaunchComputerBlock.screenHook = pos -> Minecraft.getInstance().gui.setScreen(new LaunchComputerScreen(pos));
        LaunchPayloads.scopeHook = scope -> latest = scope;
    }

    private final BlockPos pos;
    /** The X/Z boxes show the computer's target once its first scope arrives (it is usually not here yet at init). */
    private boolean shownTarget;
    private @Nullable EditBox xBox, zBox;
    private final List<Button> siloButtons = new ArrayList<>();
    private @Nullable Button armButton, launchButton;
    private int selected;
    private boolean armed;

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

    private int cx() {
        return left() + 8 + SCOPE;
    }

    private int cy() {
        return top() + 14 + SCOPE;
    }

    private LaunchPayloads.@Nullable Scope scope() {
        return latest != null && latest.pos().equals(this.pos) ? latest : null;
    }

    private void showTarget() {
        LaunchPayloads.Scope s = scope();
        if (this.shownTarget || s == null || this.xBox == null || this.zBox == null) {
            return;
        }
        this.shownTarget = true;
        if (s.hasTarget() && this.xBox.getValue().isEmpty() && this.zBox.getValue().isEmpty()) {
            this.xBox.setValue(Integer.toString(s.targetX()));
            this.zBox.setValue(Integer.toString(s.targetZ()));
        }
    }

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, true));
        int x = left() + 2 * SCOPE + 22, y = top() + 26;
        this.xBox = addRenderableWidget(new EditBox(this.font, x, y, 44, 16, Component.literal("X")));
        this.zBox = addRenderableWidget(new EditBox(this.font, x + 46, y, 44, 16, Component.literal("Z")));
        this.xBox.setMaxLength(8);
        this.zBox.setMaxLength(8);
        this.shownTarget = false;
        showTarget();
        addRenderableWidget(Button.builder(Component.translatable(T_SET), b -> sendTarget()).bounds(x, y + 18, 90, 16).build());
        this.siloButtons.clear();
        for (int i = 0; i < 4; i++) {
            int index = i;
            Button b = addRenderableWidget(Button.builder(Component.empty(), btn -> this.selected = index).bounds(x, y + 54 + i * 18, 90, 16).build());
            b.visible = false;
            this.siloButtons.add(b);
        }
        this.armButton = addRenderableWidget(Button.builder(Component.translatable(T_ARM), b -> this.armed = !this.armed).bounds(x, top() + H - 44, 90, 16).build());
        this.launchButton = addRenderableWidget(Button.builder(Component.translatable(T_LAUNCH), b -> launch()).bounds(x, top() + H - 24, 90, 18).build());
    }

    private void sendTarget() {
        try {
            int x = Integer.parseInt(this.xBox.getValue().trim()), z = Integer.parseInt(this.zBox.getValue().trim());
            ClientPacketDistributor.sendToServer(new LaunchPayloads.Target(this.pos, x, z));
        } catch (NumberFormatException | NullPointerException ignored) {
            // not numbers: nothing to set
        }
    }

    private void launch() {
        LaunchPayloads.Scope s = scope();
        if (s != null && this.selected < s.silos().size()) {
            ClientPacketDistributor.sendToServer(new LaunchPayloads.Launch(this.pos, s.silos().get(this.selected).pos()));
            this.armed = false;
        }
    }

    @Override
    public void tick() {
        LaunchPayloads.Scope s = scope();
        List<LaunchPayloads.Silo> silos = s == null ? List.of() : s.silos();
        if (this.selected >= silos.size()) {
            this.selected = 0;
        }
        for (int i = 0; i < this.siloButtons.size(); i++) {
            Button b = this.siloButtons.get(i);
            b.visible = i < silos.size();
            if (b.visible) {
                LaunchPayloads.Silo silo = silos.get(i);
                int r = Mth.clamp(silo.readiness(), 0, T_SILO_STATE.length - 1);
                Component state = r == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal()
                        ? Component.translatable(T_SILO_STATE[r], (silo.countdown() + 19) / 20) : Component.translatable(T_SILO_STATE[r]);
                String mark = i == this.selected ? "> " : "";
                b.setMessage(Component.literal(mark + (i + 1) + ": ").append(state));
            }
        }
        boolean ready = s != null && s.powered() && s.hasTarget() && this.selected < silos.size()
                && silos.get(this.selected).readiness() == LaunchSiloBlockEntity.Readiness.READY.ordinal() && inRange(s, silos.get(this.selected));
        if (this.armButton != null && this.launchButton != null) {
            this.armButton.active = ready;
            this.armButton.setMessage(Component.translatable(this.armed && ready ? T_ARMED : T_ARM));
            this.launchButton.active = ready && this.armed;
        }
    }

    private static double range(LaunchPayloads.Scope s, LaunchPayloads.Silo silo) {
        return Math.sqrt(Mth.square(s.targetX() - silo.pos().getX()) + Mth.square(s.targetZ() - silo.pos().getZ()));
    }

    private static boolean inRange(LaunchPayloads.Scope s, LaunchPayloads.Silo silo) {
        return range(s, silo) <= MissileEntity.MAX_RANGE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double dx = event.x() - cx(), dy = event.y() - cy();
        if (dx * dx + dy * dy <= SCOPE * SCOPE) {
            double k = LaunchComputerBlockEntity.RADAR_RANGE / (double) SCOPE;
            int x = this.pos.getX() + (int) Math.round(dx * k), z = this.pos.getZ() + (int) Math.round(dy * k);
            if (this.xBox != null && this.zBox != null) {
                this.xBox.setValue(Integer.toString(x));
                this.zBox.setValue(Integer.toString(z));
            }
            ClientPacketDistributor.sendToServer(new LaunchPayloads.Target(this.pos, x, z));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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
        for (int i = 0; i < s.silos().size(); i++) {
            LaunchPayloads.Silo silo = s.silos().get(i);
            int x = cx + (int) ((silo.pos().getX() - this.pos.getX()) * k), y = cy + (int) ((silo.pos().getZ() - this.pos.getZ()) * k);
            g.fill(x - 2, y - 2, x + 3, y + 3, i == this.selected ? PHOSPHOR : DIM);
        }
        if (s.hasTarget()) {
            int x = cx + (int) ((s.targetX() - this.pos.getX()) * k), y = cy + (int) ((s.targetZ() - this.pos.getZ()) * k);
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= SCOPE * SCOPE) {
                line(g, x - 3, y - 3, x + 3, y + 3, RED, 0);
                line(g, x - 3, y + 3, x + 3, y - 3, RED, 0);
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        showTarget();
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = left() + 2 * SCOPE + 22;
        g.text(this.font, this.title, left() + 8, top() + 4, TEXT);
        LaunchPayloads.Scope s = scope();
        if (s != null) {
            Component energy = Component.literal(s.energy() + "%");
            g.text(this.font, energy, left() + W - 8 - this.font.width(energy), top() + 4, s.powered() ? PHOSPHOR : RED);
        }
        g.text(this.font, Component.translatable(T_TARGET), x, top() + 16, TEXT);
        g.text(this.font, Component.translatable(T_SILOS), x, top() + 70, TEXT);
        if (s == null || s.silos().isEmpty()) {
            g.text(this.font, Component.translatable(T_NO_SILOS), x, top() + 82, DIM);
        }
        if (s != null && s.hasTarget() && this.selected < s.silos().size()) {
            LaunchPayloads.Silo silo = s.silos().get(this.selected);
            double range = range(s, silo);
            Component line = range > MissileEntity.MAX_RANGE ? Component.translatable(T_TOO_FAR, MissileEntity.MAX_RANGE)
                    : Component.translatable(T_RANGE, (int) range, flightSeconds(range));
            g.text(this.font, line, left() + 8, top() + H - 12, range > MissileEntity.MAX_RANGE ? RED : TEXT);
        }
        if (s != null && !s.contacts().isEmpty()) {
            Component c = Component.translatable(T_CONTACTS, s.contacts().size());
            g.text(this.font, c, left() + 2 * SCOPE + 16 - this.font.width(c), top() + 4, AMBER);
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
