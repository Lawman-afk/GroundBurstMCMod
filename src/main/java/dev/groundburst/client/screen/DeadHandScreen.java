package dev.groundburst.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.command.DeadHandBlock;
import dev.groundburst.content.command.DeadHandBlockEntity;
import dev.groundburst.content.command.DeadHandPayloads;
import dev.groundburst.content.launch.LaunchPayloads;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Dead Hand's console (DESIGN.md §17.17): on its screen what it is doing (safe, watching, in alert with the countdown
 * and the strike, firing, fired) and why it cannot be armed; its keys (ARM twice: both keys), SAFE, HOLD and DRILL; its
 * settings (radius, the strike it answers, the countdown, when it fires, what at, its alarm channel); its log.
 */
public final class DeadHandScreen extends Screen {
    private static final String P = GroundBurst.MODID + ".dead_hand.";
    private static final String T_SAFE = GBLang.add(P + "state.safe", "SAFE");
    private static final String T_WATCHING = GBLang.add(P + "state.watching", "WATCHING");
    private static final String T_ALERT = GBLang.add(P + "state.alert", "ALERT  T-%s");
    private static final String T_DRILL = GBLang.add(P + "state.drill", "DRILL  T-%s");
    private static final String T_FIRING = GBLang.add(P + "state.firing", "FIRING");
    private static final String T_SPENT = GBLang.add(P + "state.spent", "FIRED: load a new rocket");
    private static final String T_DARK = GBLang.add(P + "state.dark", "NO POWER");
    private static final String T_WATCH_LINE = GBLang.add(P + "watch_line", "Watching %s blocks round for strikes of magnitude %s+");
    private static final String T_STRIKE = GBLang.add(P + "strike", "Strike: magnitude %s, %s m off, at %s, %s");
    private static final String T_FROM = GBLang.add(P + "from", "Fired from where it was launched: known");
    private static final String T_FROM_NONE = GBLang.add(P + "from_none", "Where it was launched from: unknown");
    private static final String T_HOLD_HINT = GBLang.add(P + "hold_hint", "HOLD (or redstone into it) stands it down");
    private static final String T_NETWORK = GBLang.add(P + "network", "Network %s");
    private static final String T_NO_NETWORK = GBLang.add(P + "no_network", "No network");
    private static final String T_ROCKET = GBLang.add(P + "rocket", "Command rocket loaded");
    private static final String T_NO_ROCKET = GBLang.add(P + "no_rocket", "No command rocket");
    private static final String T_CANNOT = GBLang.add(P + "cannot", "To arm it: %s");
    private static final String T_NEED_FORMED = GBLang.add(P + "need.formed", "finish its structure");
    private static final String T_NEED_POWER = GBLang.add(P + "need.power", "power it");
    private static final String T_NEED_ROCKET = GBLang.add(P + "need.rocket", "load a Command Rocket");
    private static final String T_NEED_NETWORK = GBLang.add(P + "need.network", "link it with Launch Codes");
    private static final String T_ARM = GBLang.add(P + "arm", "ARM");
    private static final String T_ARM_SECOND = GBLang.add(P + "arm_second", "SECOND KEY");
    private static final String T_SAFE_BUTTON = GBLang.add(P + "safe_button", "SAFE");
    private static final String T_HOLD = GBLang.add(P + "hold", "HOLD");
    private static final String T_DRILL_BUTTON = GBLang.add(P + "drill_button", "DRILL");
    private static final String T_CHANNEL = GBLang.add(P + "channel", "Alarm channel");
    private static final String T_DONE = GBLang.add(P + "done", "Done");
    private static final String T_RADIUS = GBLang.add(P + "radius", "Radius: %s blocks");
    private static final String T_THRESHOLD = GBLang.add(P + "threshold", "Strike: magnitude %s+");
    private static final String T_COUNTDOWN = GBLang.add(P + "countdown", "Countdown: %s s");
    private static final String[] T_COMMAND = {
            GBLang.add(P + "command.0", "Fires: if no station answers"),
            GBLang.add(P + "command.1", "Fires: on any strike")};
    private static final String[] T_TARGETS = {
            GBLang.add(P + "targets.0", "At: the war plan (each aim)"),
            GBLang.add(P + "targets.1", "At: where it came from")};
    private static final String[] T_LINE = new String[DeadHandBlockEntity.Line.values().length];

    static {
        for (DeadHandBlockEntity.Line l : DeadHandBlockEntity.Line.values()) {
            T_LINE[l.ordinal()] = GBLang.add(P + "line." + l.name().toLowerCase(Locale.ROOT), switch (l) {
                case ARMED -> "Armed: watching %2$s blocks round";
                case SAFE -> "Made safe";
                case ALERT -> "ALERT: a strike of magnitude %1$s, %2$s m off";
                case STOOD_DOWN -> "Held: stood down";
                case ANSWERED -> "A station answered: stood down";
                case FIRED -> "FIRED: %2$s launchers ordered";
                case DRILL -> "Drill: %2$s s";
                case DRILL_ENDED -> "Drill over";
                case LOADED -> "Command rocket loaded";
                case NO_NETWORK -> "No network to order: stood down";
            });
        }
    }

    private static final int W = 320, H = 238;                   // fits a 720p window at GUI scale 3 (240)
    private static final int PANEL = 0xF0140E0C, EDGE = 0xFF5A3A30, SCREEN = 0xFF0C0806, TEXT = 0xFFF0D8C8, DIM = 0xFF8A6A5A,
            AMBER = 0xFFFFB040, RED = 0xFFFF4A3A, GREEN = 0xFF60E070;

    /**
     * Its rows (from its top): the CRT 16 to 80, the keys at 84, the settings at 106, 127 and 148 (18 high), the log's
     * panel from 171 to 212, Done at 214.
     */
    private static final int CRT_TOP = 16, CRT_BOTTOM = 80, KEYS = 84, SETTINGS = 106, LOG_TOP = 171;
    private static DeadHandPayloads.@Nullable Scope latest;

    public static void register() {
        DeadHandBlock.screenHook = pos -> Minecraft.getInstance().gui.setScreen(new DeadHandScreen(pos));
        DeadHandPayloads.scopeHook = s -> latest = s;
    }

    private final BlockPos pos;
    private @Nullable Button armButton, safeButton, holdButton, drillButton, radiusButton, thresholdButton, countdownButton, commandButton, targetsButton;
    private @Nullable EditBox channelBox;
    private boolean read;
    private long ticks;

    private DeadHandScreen(BlockPos pos) {
        super(Component.translatable("block.groundburst.dead_hand"));
        this.pos = pos;
    }

    private DeadHandPayloads.@Nullable Scope scope() {
        return latest != null && latest.pos().equals(this.pos) ? latest : null;
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    private void order(int action, int value) {
        ClientPacketDistributor.sendToServer(new DeadHandPayloads.Order(this.pos, action, value));
    }

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, true));
        int x = left() + 8, y = top() + KEYS;
        this.armButton = addRenderableWidget(Button.builder(Component.translatable(T_ARM), b -> order(DeadHandBlockEntity.ARM, 0))
                .bounds(x, y, 72, 18).build());
        this.safeButton = addRenderableWidget(Button.builder(Component.translatable(T_SAFE_BUTTON), b -> order(DeadHandBlockEntity.SAFE, 0))
                .bounds(x + 76, y, 72, 18).build());
        this.holdButton = addRenderableWidget(Button.builder(Component.translatable(T_HOLD), b -> order(DeadHandBlockEntity.HOLD, 0))
                .bounds(x + 152, y, 72, 18).build());
        this.drillButton = addRenderableWidget(Button.builder(Component.translatable(T_DRILL_BUTTON), b -> order(DeadHandBlockEntity.DRILL, 0))
                .bounds(x + 228, y, 76, 18).build());
        int sy = top() + SETTINGS, half = (W - 20) / 2;
        this.radiusButton = addRenderableWidget(Button.builder(Component.empty(), b -> step(DeadHandBlockEntity.SET_RADIUS))
                .bounds(x, sy, half, 18).build());
        this.thresholdButton = addRenderableWidget(Button.builder(Component.empty(), b -> step(DeadHandBlockEntity.SET_THRESHOLD))
                .bounds(x + half + 4, sy, half, 18).build());
        this.countdownButton = addRenderableWidget(Button.builder(Component.empty(), b -> step(DeadHandBlockEntity.SET_COUNTDOWN))
                .bounds(x, sy + 21, half, 18).build());
        this.commandButton = addRenderableWidget(Button.builder(Component.empty(), b -> step(DeadHandBlockEntity.SET_COMMAND))
                .bounds(x + half + 4, sy + 21, half, 18).build());
        this.targetsButton = addRenderableWidget(Button.builder(Component.empty(), b -> step(DeadHandBlockEntity.SET_TARGETS))
                .bounds(x, sy + 42, half, 18).build());
        this.channelBox = addRenderableWidget(new EditBox(this.font, x + half + 4 + 92, sy + 42, half - 92, 18, Component.translatable(T_CHANNEL)));
        this.channelBox.setMaxLength(4);
        this.channelBox.setResponder(t -> {
            if (this.read) {
                try {
                    order(DeadHandBlockEntity.SET_CHANNEL, Mth.clamp(Integer.parseInt(t.trim()), 0, 9999));
                } catch (NumberFormatException ignored) {
                    // not a number yet
                }
            }
        });
        addRenderableWidget(Button.builder(Component.translatable(T_DONE), b -> onClose()).bounds(left() + W - 58, top() + H - 23, 50, 18).build());
        this.read = false;
    }

    /** A setting stepped on to its next value. */
    private void step(int action) {
        DeadHandPayloads.Scope s = scope();
        if (s == null) {
            return;
        }
        int next = switch (action) {
            case DeadHandBlockEntity.SET_RADIUS -> (s.radius() + 1) % DeadHandBlockEntity.RADII.length;
            case DeadHandBlockEntity.SET_THRESHOLD -> (s.threshold() + 1) % DeadHandBlockEntity.THRESHOLDS.length;
            case DeadHandBlockEntity.SET_COUNTDOWN -> (s.countdown() + 1) % DeadHandBlockEntity.COUNTDOWNS.length;
            case DeadHandBlockEntity.SET_COMMAND -> (s.command() + 1) % 2;
            default -> (s.targets() + 1) % 2;
        };
        order(action, next);
    }

    @Override
    public void tick() {
        this.ticks++;
        DeadHandPayloads.Scope s = scope();
        if (s == null) {
            return;
        }
        DeadHandBlockEntity.State state = DeadHandBlockEntity.State.values()[Mth.clamp(s.state(), 0, DeadHandBlockEntity.State.values().length - 1)];
        boolean settable = state == DeadHandBlockEntity.State.SAFE || state == DeadHandBlockEntity.State.WATCHING;
        if (this.armButton != null) {
            this.armButton.active = state == DeadHandBlockEntity.State.SAFE && s.cannot().isEmpty();
            this.armButton.setMessage(Component.translatable(s.firstKey() ? T_ARM_SECOND : T_ARM));
        }
        if (this.safeButton != null) {
            this.safeButton.active = state == DeadHandBlockEntity.State.WATCHING || state == DeadHandBlockEntity.State.ALERT;
        }
        if (this.holdButton != null) {
            this.holdButton.active = state == DeadHandBlockEntity.State.ALERT;
        }
        if (this.drillButton != null) {
            this.drillButton.active = settable && s.powered();
        }
        if (this.radiusButton != null) {
            this.radiusButton.setMessage(Component.translatable(T_RADIUS, DeadHandBlockEntity.RADII[Mth.clamp(s.radius(), 0, DeadHandBlockEntity.RADII.length - 1)]));
            this.thresholdButton.setMessage(Component.translatable(T_THRESHOLD,
                    (int) DeadHandBlockEntity.THRESHOLDS[Mth.clamp(s.threshold(), 0, DeadHandBlockEntity.THRESHOLDS.length - 1)]));
            this.countdownButton.setMessage(Component.translatable(T_COUNTDOWN,
                    DeadHandBlockEntity.COUNTDOWNS[Mth.clamp(s.countdown(), 0, DeadHandBlockEntity.COUNTDOWNS.length - 1)] / 20));
            this.commandButton.setMessage(Component.translatable(T_COMMAND[Mth.clamp(s.command(), 0, 1)]));
            this.targetsButton.setMessage(Component.translatable(T_TARGETS[Mth.clamp(s.targets(), 0, 1)]));
            for (Button b : List.of(this.radiusButton, this.thresholdButton, this.countdownButton, this.commandButton, this.targetsButton)) {
                b.active = settable;
            }
        }
        if (!this.read && this.channelBox != null) {
            this.channelBox.setValue(Integer.toString(s.channel()));
            this.read = true;
        }
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, false));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, EDGE);
        g.fill(x + 8, y + CRT_TOP, x + W - 8, y + CRT_BOTTOM, SCREEN);
        g.outline(x + 7, y + CRT_TOP - 1, W - 14, CRT_BOTTOM - CRT_TOP + 2, EDGE);
        g.fill(x + 8, y + LOG_TOP, x + W - 8, y + H - 27, SCREEN);           // the log, under the settings' last row
        g.outline(x + 7, y + LOG_TOP - 1, W - 14, H - 26 - LOG_TOP, EDGE);
        for (int yy = y + CRT_TOP; yy < y + CRT_BOTTOM; yy += 2) {
            g.fill(x + 8, yy, x + W - 8, yy + 1, 0x22000000);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        g.text(this.font, this.title, x + 8, y + 5, TEXT, false);
        DeadHandPayloads.Scope s = scope();
        if (s == null) {
            return;
        }
        Component net = s.network().isEmpty() ? Component.translatable(T_NO_NETWORK) : Component.translatable(T_NETWORK, s.network());
        g.text(this.font, net, x + W - 8 - this.font.width(net), y + 5, s.network().isEmpty() ? DIM : AMBER, false);
        DeadHandBlockEntity.State state = DeadHandBlockEntity.State.values()[Mth.clamp(s.state(), 0, DeadHandBlockEntity.State.values().length - 1)];
        boolean blink = this.ticks % 20 < 12;
        long left = Math.max(0, s.alertUntil() - s.now());
        Component big;
        int col;
        if (!s.powered()) {
            big = Component.translatable(T_DARK);
            col = RED;
        } else {
            switch (state) {
                case SAFE -> {
                    big = Component.translatable(T_SAFE);
                    col = GREEN;
                }
                case WATCHING -> {
                    big = Component.translatable(T_WATCHING);
                    col = AMBER;
                }
                case ALERT -> {
                    big = Component.translatable(s.drill() ? T_DRILL : T_ALERT, (left + 19) / 20);
                    col = blink ? RED : 0xFF8A2A20;
                }
                case FIRING -> {
                    big = Component.translatable(T_FIRING);
                    col = RED;
                }
                default -> {
                    big = Component.translatable(T_SPENT);
                    col = RED;
                }
            }
        }
        g.pose().pushMatrix();
        g.pose().translate(x + 14, y + CRT_TOP + 5);
        g.pose().scale(2f, 2f);
        g.text(this.font, big, 0, 0, col, false);
        g.pose().popMatrix();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(T_WATCH_LINE, DeadHandBlockEntity.RADII[Mth.clamp(s.radius(), 0, DeadHandBlockEntity.RADII.length - 1)],
                (int) DeadHandBlockEntity.THRESHOLDS[Mth.clamp(s.threshold(), 0, DeadHandBlockEntity.THRESHOLDS.length - 1)]));
        if (state == DeadHandBlockEntity.State.ALERT && !s.drill()) {
            lines.add(Component.translatable(T_STRIKE, String.format(Locale.ROOT, "%.1f", s.strikeMagnitude()), s.strikeDistance(), s.strikeX(), s.strikeZ()));
            lines.add(Component.translatable(s.origin() ? T_FROM : T_FROM_NONE));
            lines.add(Component.translatable(T_HOLD_HINT));
        } else {
            lines.add(Component.translatable(s.rocket() ? T_ROCKET : T_NO_ROCKET));
            if (state == DeadHandBlockEntity.State.SAFE && !s.cannot().isEmpty()) {
                lines.add(Component.translatable(T_CANNOT, Component.translatable(switch (s.cannot()) {
                    case "formed" -> T_NEED_FORMED;
                    case "power" -> T_NEED_POWER;
                    case "rocket" -> T_NEED_ROCKET;
                    default -> T_NEED_NETWORK;
                })));
            }
        }
        int ly = y + CRT_TOP + 24;
        for (Component c : lines) {
            g.text(this.font, this.font.plainSubstrByWidth(c.getString(), W - 28), x + 14, ly, TEXT, false);
            ly += 10;
        }
        g.text(this.font, Component.translatable(T_CHANNEL), x + 8 + (W - 20) / 2 + 4, y + SETTINGS + 42 + 5, DIM, false);
        // The log, newest first.
        List<DeadHandBlockEntity.Entry> log = new ArrayList<>(s.log());
        java.util.Collections.reverse(log);
        int gy = y + LOG_TOP + 3;
        for (DeadHandBlockEntity.Entry e : log) {
            if (gy + 8 > y + H - 26) {
                break;
            }
            long ago = Math.max(0, s.now() - e.time()) / 20;
            String when = ago / 60 + ":" + String.format(Locale.ROOT, "%02d", ago % 60);
            Component text = Component.translatable(T_LINE[e.line().ordinal()], String.format(Locale.ROOT, "%.1f", e.a() / 10.0), e.b());
            int c = switch (e.line()) {
                case ALERT, FIRED -> RED;
                case ARMED, DRILL -> AMBER;
                default -> TEXT;
            };
            g.text(this.font, when, x + 12, gy, DIM, false);
            g.text(this.font, this.font.plainSubstrByWidth(text.getString(), W - 60), x + 46, gy, c, false);
            gy += 10;
        }
    }
}
