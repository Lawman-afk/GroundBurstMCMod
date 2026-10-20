package dev.groundzero.client.screen;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeBlockEntity;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import dev.groundzero.content.ordnance.charge.ChargePayloads;
import dev.groundzero.content.ordnance.charge.RemoteDetonatorItem;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The placed-charge screens (DESIGN.md §9.4): fuze settings (Remote frequency or Timer), the timed bomb's defuse panel
 * (four wires), and the Remote Detonator's frequency.
 */
public final class ChargeScreens {
    private static final String T_REMOTE = GZLang.add(GroundZero.MODID + ".charge.remote", "At once");
    private static final String T_TIMER = GZLang.add(GroundZero.MODID + ".charge.timer", "Timer");
    private static final String T_FREQUENCY = GZLang.add(GroundZero.MODID + ".charge.frequency", "Frequency (0-9999)");
    private static final String T_SECONDS = GZLang.add(GroundZero.MODID + ".charge.seconds", "Timer, seconds (1-600)");
    private static final String T_DONE = GZLang.add(GroundZero.MODID + ".charge.done", "Done");
    private static final String T_STATE_SAFE = GZLang.add(GroundZero.MODID + ".charge.state_safe", "SAFE");
    private static final String T_STATE_ARMED = GZLang.add(GroundZero.MODID + ".charge.state_armed", "ARMED");
    private static final String T_LOCKED = GZLang.add(GroundZero.MODID + ".charge.locked", "Make it safe to change it");
    private static final String T_HINT = GZLang.add(GroundZero.MODID + ".charge.hint", "Armed: a detonator or redstone sets it off");
    private static final String T_HINT_MINE = GZLang.add(GroundZero.MODID + ".charge.hint_mine", "Armed: it goes off when stepped on");
    private static final String T_HINT_CLAYMORE = GZLang.add(GroundZero.MODID + ".charge.hint_claymore", "Armed: its laser or a detonator sets it off");
    private static final String T_HINT_TIMER = GZLang.add(GroundZero.MODID + ".charge.hint_timer", "Armed: a detonator or redstone starts its timer");
    private static final String T_ARM = GZLang.add(GroundZero.MODID + ".charge.arm", "Arm");
    private static final String T_MAKE_SAFE = GZLang.add(GroundZero.MODID + ".charge.make_safe", "Make safe");
    private static final String T_DEFUSE = GZLang.add(GroundZero.MODID + ".charge.defuse", "Cut a wire");
    private static final String T_DEFUSE_HINT = GZLang.add(GroundZero.MODID + ".charge.defuse_hint",
            "One stops it, one sets it off, two speed it up");
    private static final String[] T_WIRES = {
            GZLang.add(GroundZero.MODID + ".charge.wire.red", "Red"), GZLang.add(GroundZero.MODID + ".charge.wire.blue", "Blue"),
            GZLang.add(GroundZero.MODID + ".charge.wire.yellow", "Yellow"), GZLang.add(GroundZero.MODID + ".charge.wire.green", "Green")};
    private static final int[] WIRE_COLORS = {0xFFD23A2E, 0xFF3A6AD8, 0xFFE8C83A, 0xFF3AAA4A};
    private static final String T_DETONATOR = GZLang.add(GroundZero.MODID + ".detonator.title", "Detonator frequency");

    private static final int PANEL = 0xE0181A1C, EDGE = 0xFF5A5E62, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9EA2, RED = 0xFFFF5040, GREEN = 0xFF60D060;

    private ChargeScreens() {}

    public static void register() {
        ChargeBlock.screenHook = ChargeScreens::open;
        RemoteDetonatorItem.frequencyHook = hand -> Minecraft.getInstance().gui.setScreen(new Frequency(hand));
    }

    private static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        ChargeBlockEntity be = mc.level == null ? null : ChargeBlock.at(mc.level, pos);
        if (be == null) {
            return;
        }
        mc.gui.setScreen(be.kind() == ChargeKind.TIMED_BOMB && be.counting() ? new Defuse(pos) : new Settings(pos, be));
    }

    private static Component chosen(String key, boolean chosen) {
        Component label = Component.translatable(key);
        return chosen ? Component.literal("> ").append(label).append(" <").withStyle(net.minecraft.ChatFormatting.YELLOW) : label;
    }

    private static int parse(String s, int fallback, int min, int max) {
        try {
            return Math.clamp(Integer.parseInt(s.trim()), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.outline(x, y, w, h, EDGE);
    }

    private static void centred(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Component text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color);
    }

    /**
     * Fuze settings and arming: its frequency, At once or Timer (if the charge has the choice) and the time, and Arm
     * (or Make safe). Only the rows the charge has are shown; the settings are locked while it is armed.
     */
    static final class Settings extends Screen {
        private static final int W = 244, ROW = 24;
        private final BlockPos pos;
        private final ChargeKind kind;
        private final boolean armed;
        private boolean timer;
        private final int frequency, seconds;
        private @Nullable EditBox frequencyBox, secondsBox;
        private @Nullable Button remoteButton, timerButton;
        private int frequencyRow = -1, secondsRow = -1, hintRow, height;
        private boolean sent;

        Settings(BlockPos pos, ChargeBlockEntity be) {
            super(be.getBlockState().getBlock().getName());
            this.pos = pos;
            this.kind = be.kind();
            this.armed = be.armed();
            this.timer = be.mode() == ChargeBlockEntity.Mode.TIMER;
            this.frequency = be.frequency();
            this.seconds = be.timer();
        }

        private int top() {
            return (this.height == 0 ? 0 : (super.height - this.height) / 2);
        }

        @Override
        protected void init() {
            // Lay the rows out first (only those this charge has), so the panel is just as tall as it needs to be.
            int y = 24;
            int choiceRow = -1;
            if (this.kind.fuzeChoice()) {
                choiceRow = y;
                y += ROW + 2;
            }
            if (this.kind.remote) {
                this.frequencyRow = y;
                y += ROW;
            }
            if (this.kind.timer) {
                this.secondsRow = y;
                y += ROW;
            }
            this.hintRow = y + 2;
            y += 16;
            int buttonsRow = y;
            this.height = y + 28;
            int x = (this.width - W) / 2, top = top();
            if (choiceRow >= 0) {
                this.remoteButton = addRenderableWidget(Button.builder(Component.translatable(T_REMOTE), b -> setTimer(false))
                        .bounds(x + 10, top + choiceRow, 109, 20).build());
                this.timerButton = addRenderableWidget(Button.builder(Component.translatable(T_TIMER), b -> setTimer(true))
                        .bounds(x + 125, top + choiceRow, 109, 20).build());
            }
            if (this.frequencyRow >= 0) {
                this.frequencyBox = addRenderableWidget(new EditBox(this.font, x + 150, top + this.frequencyRow, 84, 18, Component.translatable(T_FREQUENCY)));
                this.frequencyBox.setValue(Integer.toString(this.frequency));
                this.frequencyBox.setMaxLength(4);
            }
            if (this.secondsRow >= 0) {
                this.secondsBox = addRenderableWidget(new EditBox(this.font, x + 150, top + this.secondsRow, 84, 18, Component.translatable(T_SECONDS)));
                this.secondsBox.setValue(Integer.toString(this.seconds));
                this.secondsBox.setMaxLength(3);
            }
            Component act = Component.translatable(this.armed ? T_MAKE_SAFE : T_ARM);
            addRenderableWidget(Button.builder(act, b -> send(this.armed ? ChargePayloads.SAFE : ChargePayloads.ARM))
                    .bounds(x + 10, top + buttonsRow, 109, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(T_DONE), b -> onClose()).bounds(x + 125, top + buttonsRow, 109, 20).build());
            setTimer(this.kind.fuzeChoice() ? this.timer : this.kind.defaultMode() == ChargeBlockEntity.Mode.TIMER);
        }

        private void setTimer(boolean on) {
            this.timer = on;
            boolean choice = this.kind.fuzeChoice();
            if (this.remoteButton != null && this.timerButton != null) {
                // Both stay clickable; the chosen one is marked (a greyed-out button reads as unavailable).
                this.remoteButton.active = this.timerButton.active = !this.armed;
                this.remoteButton.setMessage(chosen(T_REMOTE, !on));
                this.timerButton.setMessage(chosen(T_TIMER, on));
            }
            if (this.frequencyBox != null) {
                this.frequencyBox.active = !this.armed;
            }
            if (this.secondsBox != null) {
                this.secondsBox.active = !this.armed && (!choice || on);
            }
        }

        /** Sends the settings (and arms it or makes it safe), then closes. */
        private void send(int action) {
            if (!this.sent) {
                this.sent = true;
                ClientPacketDistributor.sendToServer(new ChargePayloads.Settings(this.pos, this.timer,
                        this.frequencyBox == null ? this.frequency : parse(this.frequencyBox.getValue(), this.frequency, 0, ChargeKind.MAX_FREQUENCY),
                        this.secondsBox == null ? this.seconds : parse(this.secondsBox.getValue(), this.seconds, 1, ChargeKind.MAX_TIMER), action));
            }
            super.onClose();
        }

        @Override
        public void onClose() {
            send(ChargePayloads.SAVE);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractBackground(g, mouseX, mouseY, partial);
            panel(g, (this.width - W) / 2, top(), W, this.height);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractRenderState(g, mouseX, mouseY, partial);
            int x = (this.width - W) / 2, top = top();
            centred(g, this.font, this.title, this.width / 2, top + 8, TEXT);
            Component state = Component.translatable(this.armed ? T_STATE_ARMED : T_STATE_SAFE);
            g.text(this.font, state, x + W - 8 - this.font.width(state), top + 8, this.armed ? RED : GREEN);
            boolean choice = this.kind.fuzeChoice();
            if (this.frequencyRow >= 0) {
                g.text(this.font, Component.translatable(T_FREQUENCY), x + 10, top + this.frequencyRow + 5, TEXT);
            }
            if (this.secondsRow >= 0) {
                g.text(this.font, Component.translatable(T_SECONDS), x + 10, top + this.secondsRow + 5, choice && !this.timer ? DIM : TEXT);
            }
            String key = this.armed ? T_LOCKED : switch (this.kind.sensor) {
                case PRESSURE -> T_HINT_MINE;
                case TRIPWIRE -> T_HINT_CLAYMORE;
                case NONE -> this.timer ? T_HINT_TIMER : T_HINT;
            };
            Component hint = Component.translatable(key);
            centred(g, this.font, hint, this.width / 2, top + this.hintRow, DIM);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** The timed bomb counting down: four wires to cut. */
    static final class Defuse extends Screen {
        private static final int W = 230, H = 116;
        private final BlockPos pos;
        private final Button[] wires = new Button[ChargeBlockEntity.WIRES];

        Defuse(BlockPos pos) {
            super(Component.translatable(T_DEFUSE));
            this.pos = pos;
        }

        private @Nullable ChargeBlockEntity bomb() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : ChargeBlock.at(mc.level, this.pos);
        }

        @Override
        protected void init() {
            int x = (this.width - W) / 2, y = (this.height - H) / 2;
            for (int i = 0; i < this.wires.length; i++) {
                int wire = i;
                this.wires[i] = addRenderableWidget(Button.builder(Component.translatable(T_WIRES[i]),
                        b -> ClientPacketDistributor.sendToServer(new ChargePayloads.CutWire(this.pos, wire)))
                        .bounds(x + 12 + i * 52, y + 88, 50, 20).build());
            }
        }

        @Override
        public void tick() {
            ChargeBlockEntity be = bomb();
            if (be == null || !be.counting()) {
                onClose();
                return;
            }
            for (int i = 0; i < this.wires.length; i++) {
                this.wires[i].active = (be.cutWires() & 1 << i) == 0;
            }
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractBackground(g, mouseX, mouseY, partial);
            int x = (this.width - W) / 2, y = (this.height - H) / 2;
            panel(g, x, y, W, H);
            ChargeBlockEntity be = bomb();
            int cut = be == null ? 0 : be.cutWires();
            for (int i = 0; i < ChargeBlockEntity.WIRES; i++) {
                int wx = x + 12 + i * 52 + 23;
                if ((cut & 1 << i) == 0) {
                    g.fill(wx, y + 50, wx + 4, y + 86, WIRE_COLORS[i]);
                } else {                                                 // snipped: two loose ends
                    g.fill(wx, y + 50, wx + 4, y + 64, WIRE_COLORS[i]);
                    g.fill(wx + 2, y + 72, wx + 6, y + 86, WIRE_COLORS[i]);
                }
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractRenderState(g, mouseX, mouseY, partial);
            int y = (this.height - H) / 2;
            ChargeBlockEntity be = bomb();
            int ticks = be == null ? 0 : Math.max(0, be.countdown());
            String clock = String.format("%d:%02d", ticks / 1200, ticks / 20 % 60);
            centred(g, this.font, this.title, this.width / 2, y + 8, TEXT);
            centred(g, this.font, Component.literal(clock), this.width / 2, y + 22, RED);
            centred(g, this.font, Component.translatable(T_DEFUSE_HINT), this.width / 2, y + 35, DIM);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** A Remote Detonator's frequency. */
    static final class Frequency extends Screen {
        private static final int W = 180, H = 84;
        private final InteractionHand hand;
        private @Nullable EditBox box;

        Frequency(InteractionHand hand) {
            super(Component.translatable(T_DETONATOR));
            this.hand = hand;
        }

        @Override
        protected void init() {
            int x = (this.width - W) / 2, y = (this.height - H) / 2;
            this.box = addRenderableWidget(new EditBox(this.font, x + 50, y + 26, 80, 18, this.title));
            this.box.setMaxLength(4);
            var player = Minecraft.getInstance().player;
            this.box.setValue(Integer.toString(player == null ? 0 : RemoteDetonatorItem.frequency(player.getItemInHand(this.hand))));
            setInitialFocus(this.box);
            addRenderableWidget(Button.builder(Component.translatable(T_DONE), b -> onClose()).bounds(x + 40, y + 54, 100, 20).build());
        }

        @Override
        public void onClose() {
            if (this.box != null) {
                ClientPacketDistributor.sendToServer(new ChargePayloads.DetonatorFrequency(this.hand == InteractionHand.MAIN_HAND,
                        parse(this.box.getValue(), 0, 0, ChargeKind.MAX_FREQUENCY)));
            }
            super.onClose();
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractBackground(g, mouseX, mouseY, partial);
            panel(g, (this.width - W) / 2, (this.height - H) / 2, W, H);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractRenderState(g, mouseX, mouseY, partial);
            centred(g, this.font, this.title, this.width / 2, (this.height - H) / 2 + 8, TEXT);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
