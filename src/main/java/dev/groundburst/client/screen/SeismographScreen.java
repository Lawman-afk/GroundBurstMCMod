package dev.groundburst.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.command.Seismic;
import dev.groundburst.content.command.SeismographBlock;
import dev.groundburst.content.command.SeismographBlockEntity;
import dev.groundburst.content.command.SeismographPayloads;
import dev.groundburst.content.command.Trace;
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
 * The Seismograph's screen (DESIGN.md §17.17): on the left the drum's paper, a line a minute, the last twelve as the pen
 * drew them (the ground's faint noise, each blast's first and second arrivals and its coda, a tremor's long band), each
 * line's time in the margin; on the right what it recorded (when, magnitude, distance, bearing, the yield worked back);
 * below its settings: what it records, its alarm and its channel.
 */
public final class SeismographScreen extends Screen {
    private static final String P = GroundBurst.MODID + ".seismograph.";
    private static final String T_RECORD = GBLang.add(P + "record", "Record");
    private static final String T_NOTHING = GBLang.add(P + "nothing", "Nothing recorded yet");
    private static final String T_LISTENING = GBLang.add(P + "listening", "LISTENING");
    private static final String T_DARK = GBLang.add(P + "dark", "NO POWER");
    private static final String T_NETWORK = GBLang.add(P + "network", "Network %s");
    private static final String T_NO_NETWORK = GBLang.add(P + "no_network", "No network (link it with Launch Codes)");
    private static final String T_CHANNEL = GBLang.add(P + "channel", "Channel");
    private static final String T_DONE = GBLang.add(P + "done", "Done");
    private static final String[] T_SENSITIVITY = {
            GBLang.add(P + "sensitivity.0", "Records: every tremor"),
            GBLang.add(P + "sensitivity.1", "Records: sensitive"),
            GBLang.add(P + "sensitivity.2", "Records: normal"),
            GBLang.add(P + "sensitivity.3", "Records: strong only")};
    private static final String[] T_ALARM = {
            GBLang.add(P + "alarm.0", "Alarm: off"),
            GBLang.add(P + "alarm.1", "Alarm: magnitude 2+"),
            GBLang.add(P + "alarm.2", "Alarm: magnitude 3+"),
            GBLang.add(P + "alarm.3", "Alarm: magnitude 4+"),
            GBLang.add(P + "alarm.4", "Alarm: magnitude 5+")};
    private static final String T_ROW = GBLang.add(P + "row", "M %s  %s  %s  %s");
    private static final String T_YIELD_T = GBLang.add(P + "yield_t", "~%s t");
    private static final String T_YIELD_KT = GBLang.add(P + "yield_kt", "~%s kt");
    private static final String T_YIELD_MT = GBLang.add(P + "yield_mt", "~%s Mt");
    private static final String T_TREMOR = GBLang.add(P + "tremor", "tremor %s s");
    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static final int W = 360, H = 222, PAPER_X = 8, PAPER_Y = 18, PAPER_W = 200, LINES = 12, LINE_H = 13;
    private static final int PAPER_H = LINES * LINE_H, LIST_X = PAPER_X + PAPER_W + 8, LIST_W = W - LIST_X - 8;
    /** The paper's cream, its faint blue rule, the pen's ink, the minute marks' red. */
    private static final int PAPER = 0xFFEFE6D0, RULE = 0x2A3A5A90, INK = 0xFF1B2444, MARK = 0xFFB0302A;
    private static final int PANEL = 0xF0161816, EDGE = 0xFF4A4E4A, TEXT = 0xFFE4E4DC, DIM = 0xFF8E928A, AMBER = 0xFFFFB040, GREEN = 0xFF60E070;

    private static SeismographPayloads.@Nullable View latest;

    public static void register() {
        SeismographBlock.screenHook = pos -> Minecraft.getInstance().setScreen(new SeismographScreen(pos));
        SeismographPayloads.viewHook = v -> latest = v;
    }

    private final BlockPos pos;
    private @Nullable EditBox channelBox;
    private @Nullable Button sensitivityButton, alarmButton;
    private int sensitivity = SeismographBlockEntity.DEFAULT_SENSITIVITY, alarmAt;
    private boolean settingsRead;
    private double partial;

    private SeismographScreen(BlockPos pos) {
        super(Component.translatable("block.groundburst.seismograph"));
        this.pos = pos;
    }

    private SeismographPayloads.@Nullable View view() {
        return latest != null && latest.pos().equals(this.pos) ? latest : null;
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, true));
        int x = left(), y = top() + PAPER_Y + PAPER_H + 8;
        this.sensitivityButton = addRenderableWidget(Button.builder(Component.translatable(T_SENSITIVITY[this.sensitivity]), b -> {
            this.sensitivity = (this.sensitivity + 1) % T_SENSITIVITY.length;
            b.setMessage(Component.translatable(T_SENSITIVITY[this.sensitivity]));
            send();
        }).bounds(x + 8, y, 126, 18).build());
        this.alarmButton = addRenderableWidget(Button.builder(Component.translatable(T_ALARM[this.alarmAt]), b -> {
            this.alarmAt = (this.alarmAt + 1) % T_ALARM.length;
            b.setMessage(Component.translatable(T_ALARM[this.alarmAt]));
            send();
        }).bounds(x + 138, y, 120, 18).build());
        this.channelBox = addRenderableWidget(new EditBox(this.font, x + 306, y, 46, 18, Component.translatable(T_CHANNEL)));
        this.channelBox.setMaxLength(4);
        this.channelBox.setResponder(t -> send());
        addRenderableWidget(Button.builder(Component.translatable(T_DONE), b -> onClose()).bounds(x + W - 58, top() + H - 24, 50, 18).build());
        this.settingsRead = false;
    }

    private int channel() {
        try {
            return this.channelBox == null ? 0 : Mth.clamp(Integer.parseInt(this.channelBox.getValue().trim()), 0, 9999);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void send() {
        if (this.settingsRead) {
            ClientPacketDistributor.sendToServer(new SeismographPayloads.Settings(this.pos, this.sensitivity, this.alarmAt, channel()));
        }
    }

    @Override
    public void tick() {
        SeismographPayloads.View v = view();
        if (v != null && !this.settingsRead) {
            // Its settings as it has them, the first time they arrive.
            this.sensitivity = v.sensitivity();
            this.alarmAt = v.alarmAt();
            if (this.sensitivityButton != null) {
                this.sensitivityButton.setMessage(Component.translatable(T_SENSITIVITY[Mth.clamp(this.sensitivity, 0, T_SENSITIVITY.length - 1)]));
            }
            if (this.alarmButton != null) {
                this.alarmButton.setMessage(Component.translatable(T_ALARM[Mth.clamp(this.alarmAt, 0, T_ALARM.length - 1)]));
            }
            if (this.channelBox != null) {
                this.channelBox.setValue(Integer.toString(v.channel()));
            }
            this.settingsRead = true;
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

    // --- drawing ------------------------------------------------------------------------------------------------------------
    /** The time of day a game time stood for (06:00 at 0), as on the station's clocks. */
    private static String clock(long gameTime, long nowGame, long nowDay) {
        long day = nowDay - (nowGame - gameTime);
        long t = Math.floorMod(day + 6000, 24000);
        return String.format(Locale.ROOT, "%02d:%02d", t / 1000, t % 1000 * 60 / 1000);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        this.partial = partialTick;
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, EDGE);
        drawPaper(g, x + PAPER_X, y + PAPER_Y);
        drawList(g, x + LIST_X, y + PAPER_Y);
    }

    /** The drum's paper: twelve lines, a minute each, oldest at the top; the pen's trace along each; a red mark each minute. */
    private void drawPaper(GuiGraphicsExtractor g, int x0, int y0) {
        g.fill(x0 - 1, y0 - 1, x0 + PAPER_W + 1, y0 + PAPER_H + 1, 0xFF3A3A34);
        g.fill(x0, y0, x0 + PAPER_W, y0 + PAPER_H, PAPER);
        for (int k = 0; k <= LINES; k++) {
            g.fill(x0, y0 + k * LINE_H, x0 + PAPER_W, y0 + k * LINE_H + 1, RULE);
        }
        SeismographPayloads.View v = view();
        Minecraft mc = Minecraft.getInstance();
        if (v == null || mc.level == null) {
            return;
        }
        double now = v.now() + 0;                                  // the server's time when it was sent
        long line = SeismographBlockEntity.DRUM_TICKS;
        long firstStart = (long) Math.floor(now / line) * line - (LINES - 1) * line;
        double perPixel = line / (double) PAPER_W;
        List<SeismographBlockEntity.Record> records = v.records();
        long hash = this.pos.asLong() * 0x9E3779B97F4A7C15L;
        for (int k = 0; k < LINES; k++) {
            long start = firstStart + k * line;
            int base = y0 + k * LINE_H + LINE_H / 2;
            for (int px = 0; px < PAPER_W; px++) {
                double t = start + px * perPixel;
                if (t > now) {
                    break;                                         // not drawn yet: the pen is here
                }
                double env = 0;
                for (SeismographBlockEntity.Record r : records) {
                    env = Math.max(env, Trace.envelope(t, r.arrival(), r.second(), r.felt(), r.duration()));
                }
                // The ground's faint noise: a wavering line; a blast, a band of wiggles as wide as it was strong.
                long h = mix(hash ^ (long) Math.floor(t));
                double noise = ((h & 0xFF) / 255.0 - 0.5) * 1.4 + Math.sin(t * 0.05) * 0.4;
                int half = (int) Math.round(Math.min(1.0, env) * (LINE_H - 2));
                int wiggle = (int) Math.round(noise);
                if (half > 0) {
                    int top = base - half + (int) ((h >>> 8 & 3) - 1), bottom = base + half - (int) ((h >>> 10 & 3) - 1);
                    g.fill(x0 + px, Math.max(y0 + 1, Math.min(top, bottom)), x0 + px + 1, Math.min(y0 + PAPER_H - 1, Math.max(top, bottom) + 1), INK);
                } else {
                    g.fill(x0 + px, base + wiggle, x0 + px + 1, base + wiggle + 1, INK);
                }
            }
            g.fill(x0, base - 3, x0 + 1, base + 4, MARK);           // the minute mark at the line's start
        }
        // The pen itself, where it is now on the last line.
        int penX = x0 + (int) Math.min(PAPER_W - 1, (now - (firstStart + (LINES - 1) * line)) / perPixel);
        int penY = y0 + (LINES - 1) * LINE_H + LINE_H / 2;
        g.fill(penX, penY - 5, penX + 1, penY - 1, MARK);
        // Each line's time in the margin, on the paper's own left, small.
        long day = mc.level.getOverworldClockTime();
        for (int k = 0; k < LINES; k += 2) {
            small(g, clock(firstStart + k * line, (long) now, day), x0 + 2, y0 + k * LINE_H + 1, 0xFF6A6A80);
        }
    }

    private void drawList(GuiGraphicsExtractor g, int x0, int y0) {
        g.fill(x0, y0, x0 + LIST_W, y0 + PAPER_H, 0xFF0C100C);
        g.outline(x0 - 1, y0 - 1, LIST_W + 2, PAPER_H + 2, EDGE);
        SeismographPayloads.View v = view();
        g.text(this.font, Component.translatable(T_RECORD), x0 + 3, y0 + 3, TEXT, false);
        if (v == null) {
            return;
        }
        List<SeismographBlockEntity.Record> records = new ArrayList<>(v.records());
        java.util.Collections.reverse(records);                       // newest first
        if (records.isEmpty()) {
            small(g, Component.translatable(T_NOTHING).getString(), x0 + 3, y0 + 16, DIM);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long day = mc.level == null ? 0 : mc.level.getOverworldClockTime();
        int y = y0 + 15;
        for (SeismographBlockEntity.Record r : records) {
            if (y > y0 + PAPER_H - 18) {
                break;
            }
            String when = clock(r.arrival(), v.now(), day);
            String dist = r.distance() >= 1000 ? String.format(Locale.ROOT, "%.1f km", r.distance() / 1000.0) : r.distance() + " m";
            int b = Math.round(r.bearing()) % 360;
            String bearing = String.format(Locale.ROOT, "%03d° %s", b, COMPASS[Math.floorMod(Math.round(b / 45f), 8)]);
            String row = Component.translatable(T_ROW, String.format(Locale.ROOT, "%.1f", r.magnitude()), dist, bearing, yieldOf(r)).getString();
            int col = r.magnitude() >= 4 ? AMBER : TEXT;
            small(g, when, x0 + 3, y, DIM);
            small(g, row, x0 + 3, y + 7, col);
            y += 16;
        }
    }

    /** The yield its magnitude stands for, rounded as a seismologist would give it (a tremor's: how long it lasted). */
    private static String yieldOf(SeismographBlockEntity.Record r) {
        if (r.duration() > 0) {
            return Component.translatable(T_TREMOR, Math.max(1, r.duration() / 20)).getString();
        }
        double t = Seismic.tonnes(r.magnitude());
        if (t >= 1_000_000) {
            return Component.translatable(T_YIELD_MT, round2(t / 1_000_000)).getString();
        }
        if (t >= 1_000) {
            return Component.translatable(T_YIELD_KT, round2(t / 1_000)).getString();
        }
        return Component.translatable(T_YIELD_T, round2(t)).getString();
    }

    /** Two significant figures. */
    private static String round2(double v) {
        if (v <= 0) {
            return "0";
        }
        double mag = Math.pow(10, Math.floor(Math.log10(v)) - 1);
        double r = Math.round(v / mag) * mag;
        return r >= 10 ? String.format(Locale.ROOT, "%.0f", r) : r >= 1 ? String.format(Locale.ROOT, "%.1f", r) : String.format(Locale.ROOT, "%.2f", r);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Small type (two thirds), on whole screen pixels. */
    private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        g.pose().pushMatrix();
        g.pose().translate(x, Math.round(y * gs) / (float) gs);
        g.pose().scale(scale, scale);
        g.text(this.font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        g.text(this.font, this.title, x + 8, y + 5, TEXT, false);
        SeismographPayloads.View v = view();
        if (v != null) {
            Component state = Component.translatable(v.powered() ? T_LISTENING : T_DARK);
            g.text(this.font, state, x + W - 8 - this.font.width(state), y + 5, v.powered() ? GREEN : 0xFFFF5040, false);
            Component net = v.network().isEmpty() ? Component.translatable(T_NO_NETWORK) : Component.translatable(T_NETWORK, v.network());
            small(g, net.getString(), x + 8, y + H - 18, v.network().isEmpty() ? DIM : AMBER);
        }
        small(g, Component.translatable(T_CHANNEL).getString(), x + 266, y + PAPER_Y + PAPER_H + 13, DIM);
    }
}
