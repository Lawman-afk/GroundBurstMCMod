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
    private static final String T_REMOTE = GZLang.add(GroundZero.MODID + ".charge.remote", "Remote");
    private static final String T_TIMER = GZLang.add(GroundZero.MODID + ".charge.timer", "Timer");
    private static final String T_FREQUENCY = GZLang.add(GroundZero.MODID + ".charge.frequency", "Frequency (0-9999)");
    private static final String T_SECONDS = GZLang.add(GroundZero.MODID + ".charge.seconds", "Timer, seconds (1-600)");
    private static final String T_DONE = GZLang.add(GroundZero.MODID + ".charge.done", "Done");
    private static final String T_STATE_SAFE = GZLang.add(GroundZero.MODID + ".charge.state_safe", "SAFE");
    private static final String T_STATE_ARMED = GZLang.add(GroundZero.MODID + ".charge.state_armed", "ARMED");
    private static final String T_LOCKED = GZLang.add(GroundZero.MODID + ".charge.locked", "Make it safe with the Arming Key to change its fuze");
    private static final String T_HINT = GZLang.add(GroundZero.MODID + ".charge.hint", "Sneak-use an Arming Key on it to arm it");
    private static final String T_DEFUSE = GZLang.add(GroundZero.MODID + ".charge.defuse", "Cut a wire");
    private static final String T_DEFUSE_HINT = GZLang.add(GroundZero.MODID + ".charge.defuse_hint",
            "One stops it. One sets it off. The others speed it up.");
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

    /** Fuze settings: Remote (frequency) or Timer (seconds); locked while armed. */
    static final class Settings extends Screen {
        private static final int W = 220, H = 150;
        private final BlockPos pos;
        private final ChargeKind kind;
        private final boolean armed;
        private boolean timer;
        private final int frequency, seconds;
        private @Nullable EditBox frequencyBox, secondsBox;
        private @Nullable Button remoteButton, timerButton;

        Settings(BlockPos pos, ChargeBlockEntity be) {
            super(be.getBlockState().getBlock().getName());
            this.pos = pos;
            this.kind = be.kind();
            this.armed = be.armed();
            this.timer = be.mode() == ChargeBlockEntity.Mode.TIMER;
            this.frequency = be.frequency();
            this.seconds = be.timer();
        }

        @Override
        protected void init() {
            int x = (this.width - W) / 2, y = (this.height - H) / 2;
            if (this.kind.fuzeChoice) {
                this.remoteButton = addRenderableWidget(Button.builder(Component.translatable(T_REMOTE), b -> setTimer(false))
                        .bounds(x + 10, y + 30, 97, 20).build());
                this.timerButton = addRenderableWidget(Button.builder(Component.translatable(T_TIMER), b -> setTimer(true))
                        .bounds(x + 113, y + 30, 97, 20).build());
            }
            this.frequencyBox = addRenderableWidget(new EditBox(this.font, x + 130, y + 60, 80, 18, Component.translatable(T_FREQUENCY)));
            this.frequencyBox.setValue(Integer.toString(this.frequency));
            this.frequencyBox.setMaxLength(4);
            this.secondsBox = addRenderableWidget(new EditBox(this.font, x + 130, y + 84, 80, 18, Component.translatable(T_SECONDS)));
            this.secondsBox.setValue(Integer.toString(this.seconds));
            this.secondsBox.setMaxLength(3);
            addRenderableWidget(Button.builder(Component.translatable(T_DONE), b -> onClose()).bounds(x + 60, y + H - 28, 100, 20).build());
            setTimer(this.timer || !this.kind.fuzeChoice);
        }

        private void setTimer(boolean on) {
            this.timer = on;
            if (this.remoteButton != null && this.timerButton != null) {
                this.remoteButton.active = !this.armed && on;
                this.timerButton.active = !this.armed && !on;
            }
            if (this.frequencyBox != null && this.secondsBox != null) {
                this.frequencyBox.active = !this.armed && !on;
                this.frequencyBox.visible = this.kind.fuzeChoice;
                this.secondsBox.active = !this.armed && on;
            }
        }

        @Override
        public void onClose() {
            if (!this.armed && this.frequencyBox != null && this.secondsBox != null) {
                ClientPacketDistributor.sendToServer(new ChargePayloads.Settings(this.pos, this.timer,
                        parse(this.frequencyBox.getValue(), this.frequency, 0, ChargeKind.MAX_FREQUENCY),
                        parse(this.secondsBox.getValue(), this.seconds, 1, ChargeKind.MAX_TIMER)));
            }
            super.onClose();
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractBackground(g, mouseX, mouseY, partial);
            int x = (this.width - W) / 2, y = (this.height - H) / 2;
            panel(g, x, y, W, H);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractRenderState(g, mouseX, mouseY, partial);
            int x = (this.width - W) / 2, y = (this.height - H) / 2;
            centred(g, this.font, this.title, this.width / 2, y + 8, TEXT);
            Component state = Component.translatable(this.armed ? T_STATE_ARMED : T_STATE_SAFE);
            g.text(this.font, state, x + W - 8 - this.font.width(state), y + 8, this.armed ? RED : GREEN);
            if (this.kind.fuzeChoice) {
                g.text(this.font, Component.translatable(T_FREQUENCY), x + 10, y + 65, this.timer ? DIM : TEXT);
            }
            g.text(this.font, Component.translatable(T_SECONDS), x + 10, y + 89, this.timer ? TEXT : DIM);
            centred(g, this.font, Component.translatable(this.armed ? T_LOCKED : T_HINT), this.width / 2, y + 108, DIM);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** The timed bomb counting down: four wires to cut. */
    static final class Defuse extends Screen {
        private static final int W = 230, H = 140;
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
                        .bounds(x + 12 + i * 52, y + 92, 50, 20).build());
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
                    g.fill(wx, y + 54, wx + 4, y + 90, WIRE_COLORS[i]);
                } else {                                                 // snipped: two loose ends
                    g.fill(wx, y + 54, wx + 4, y + 68, WIRE_COLORS[i]);
                    g.fill(wx + 2, y + 76, wx + 6, y + 90, WIRE_COLORS[i]);
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
            centred(g, this.font, Component.literal(clock), this.width / 2, y + 24, RED);
            centred(g, this.font, Component.translatable(T_DEFUSE_HINT), this.width / 2, y + 38, DIM);
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
