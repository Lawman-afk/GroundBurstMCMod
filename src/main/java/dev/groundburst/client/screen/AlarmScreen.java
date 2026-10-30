package dev.groundburst.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.alarm.AlarmBlock;
import dev.groundburst.content.alarm.AlarmBlockEntity;
import dev.groundburst.content.alarm.AlarmDevice;
import dev.groundburst.content.alarm.AlarmPayloads;
import dev.groundburst.content.alarm.Alarms;
import dev.groundburst.registry.GBLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * An alarm device's settings (DESIGN.md §18.6): its channel, its own choices (each a button that steps through them),
 * an Alarm Panel's buttons, and what a receiver or a monitor reads now. Saved as it closes.
 */
public final class AlarmScreen extends Screen {
    private static final String T_CHANNEL = GBLang.add(GroundBurst.MODID + ".alarm.screen.channel", "Alarm channel (0-9999)");
    private static final String T_DONE = GBLang.add(GroundBurst.MODID + ".alarm.screen.done", "Done");
    private static final String T_SOUND = GBLang.add(GroundBurst.MODID + ".alarm.screen.sound", "Sound alarm");
    private static final String T_STOP = GBLang.add(GroundBurst.MODID + ".alarm.screen.stop", "Stop alarm");
    private static final String T_SILENCE = GBLang.add(GroundBurst.MODID + ".alarm.screen.silence", "Silence");
    private static final String T_TEST = GBLang.add(GroundBurst.MODID + ".alarm.screen.test", "Test");
    private static final String T_INCOMING = GBLang.add(GroundBurst.MODID + ".alarm.screen.incoming",
            "%s missiles coming within %s blocks");
    private static final String T_IMPACT = GBLang.add(GroundBurst.MODID + ".alarm.screen.impact", "First impact in %s");
    private static final String T_NONE = GBLang.add(GroundBurst.MODID + ".alarm.screen.none", "No missiles coming within %s blocks");
    private static final String T_RADAR = GBLang.add(GroundBurst.MODID + ".alarm.screen.radar",
            "It sees what a working Radar Array tracks");
    private static final String T_RADIATION = GBLang.add(GroundBurst.MODID + ".alarm.screen.radiation", "Radiation: %s RAD/h");
    private static final String T_GAS = GBLang.add(GroundBurst.MODID + ".alarm.screen.gas", "Poison gas: %s%%");
    private static final String T_NOW_ON = GBLang.add(GroundBurst.MODID + ".alarm.screen.now_on", "Now: going off");
    private static final String T_NOW_OFF = GBLang.add(GroundBurst.MODID + ".alarm.screen.now_off", "Now: quiet");
    private static final String T_PANEL_ON = GBLang.add(GroundBurst.MODID + ".alarm.screen.panel_on", "Its channel is raised");
    private static final String T_HINT = GBLang.add(GroundBurst.MODID + ".alarm.screen.hint",
            "Devices on the same channel work together");

    private static final int W = 236, ROW = 24;
    private static final int PANEL = 0xE0181A1C, EDGE = 0xFF5A5E62, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9EA2;

    private final BlockPos pos;
    private final AlarmDevice device;
    private final int[] options;
    private final int channel;
    private @Nullable EditBox channelBox;
    private final List<Button> optionButtons = new ArrayList<>();
    private @Nullable Button soundButton;
    private int panelHeight, readoutRow;
    private boolean sent;

    private AlarmScreen(BlockPos pos, AlarmBlockEntity be) {
        super(be.getBlockState().getBlock().getName());
        this.pos = pos;
        this.device = be.device();
        this.options = be.options();
        this.channel = be.channel();
    }

    public static void register() {
        AlarmBlock.screenHook = AlarmScreen::open;
    }

    private static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
            mc.gui.setScreen(new AlarmScreen(pos, be));
        }
    }

    private int top() {
        return (this.height - this.panelHeight) / 2;
    }

    private @Nullable AlarmBlockEntity entity() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(this.pos) instanceof AlarmBlockEntity be ? be : null;
    }

    @Override
    protected void init() {
        this.optionButtons.clear();
        int y = 24;
        int channelRow = y;
        y += ROW;
        int firstOption = y;
        y += ROW * this.device.options.size();
        int panelRow = -1;
        if (this.device == AlarmDevice.PANEL) {
            panelRow = y;
            y += ROW;
        }
        this.readoutRow = y + 2;
        y += 12 * readoutLines() + 8;
        int doneRow = y;
        this.panelHeight = y + 28;
        int x = (this.width - W) / 2, top = top();
        this.channelBox = addRenderableWidget(new EditBox(this.font, x + 150, top + channelRow, 76, 18, Component.translatable(T_CHANNEL)));
        this.channelBox.setValue(Integer.toString(this.channel));
        this.channelBox.setMaxLength(4);
        for (int i = 0; i < this.device.options.size(); i++) {
            final int index = i;
            Button b = addRenderableWidget(Button.builder(optionLabel(i), button -> step(index))
                    .bounds(x + 10, top + firstOption + i * ROW, W - 20, 20).build());
            this.optionButtons.add(b);
        }
        if (panelRow >= 0) {
            AlarmBlockEntity be = entity();
            boolean on = be != null && be.manual();
            this.soundButton = addRenderableWidget(Button.builder(Component.translatable(on ? T_STOP : T_SOUND),
                    b -> panel(this.soundButton != null && isStop() ? AlarmBlockEntity.STOP : AlarmBlockEntity.SOUND))
                    .bounds(x + 10, top + panelRow, 92, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(T_SILENCE), b -> panel(AlarmBlockEntity.SILENCE))
                    .bounds(x + 106, top + panelRow, 62, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(T_TEST), b -> panel(AlarmBlockEntity.TEST))
                    .bounds(x + 172, top + panelRow, 54, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(T_DONE), b -> onClose()).bounds(x + (W - 100) / 2, top + doneRow, 100, 20).build());
    }

    private boolean isStop() {
        AlarmBlockEntity be = entity();
        return be != null && be.manual();
    }

    private int readoutLines() {
        return switch (this.device) {
            case RECEIVER -> 3;
            case MONITOR -> 2;
            default -> 1;
        };
    }

    private Component optionLabel(int i) {
        AlarmDevice.Option o = this.device.options.get(i);
        return Component.translatable(o.labelKey()).append(": ").append(Component.translatable(o.valueKey(this.options[i])).withStyle(ChatFormatting.YELLOW));
    }

    private void step(int i) {
        this.options[i] = (this.options[i] + 1) % this.device.options.get(i).values().size();
        this.optionButtons.get(i).setMessage(optionLabel(i));
        send();                                                          // seen at once (a beacon's colour, a siren's signal)
    }

    private void panel(int action) {
        send();
        ClientPacketDistributor.sendToServer(new AlarmPayloads.Panel(this.pos, action));
        if (this.soundButton != null && (action == AlarmBlockEntity.SOUND || action == AlarmBlockEntity.STOP)) {
            this.soundButton.setMessage(Component.translatable(action == AlarmBlockEntity.SOUND ? T_STOP : T_SOUND));
        }
    }

    private int channelValue() {
        if (this.channelBox == null) {
            return this.channel;
        }
        try {
            return Math.clamp(Integer.parseInt(this.channelBox.getValue().trim()), 0, Alarms.MAX_CHANNEL);
        } catch (NumberFormatException e) {
            return this.channel;
        }
    }

    private void send() {
        List<Integer> opts = new ArrayList<>();
        for (int v : this.options) {
            opts.add(v);
        }
        ClientPacketDistributor.sendToServer(new AlarmPayloads.Settings(this.pos, channelValue(), opts));
    }

    @Override
    public void onClose() {
        if (!this.sent) {
            this.sent = true;
            send();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = (this.width - W) / 2, top = top();
        g.fill(x, top, x + W, top + this.panelHeight, PANEL);
        g.outline(x, top, W, this.panelHeight, EDGE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = (this.width - W) / 2, top = top();
        g.text(this.font, this.title, x + (W - this.font.width(this.title)) / 2, top + 8, TEXT);
        g.text(this.font, Component.translatable(T_CHANNEL), x + 10, top + 24 + 5, DIM);
        int y = top + this.readoutRow;
        for (Component line : readout()) {
            g.text(this.font, line, x + 10, y, TEXT);
            y += 12;
        }
    }

    /** What it reads now: a receiver's missiles, a monitor's readings, anything else whether it is going off. */
    private List<Component> readout() {
        AlarmBlockEntity be = entity();
        Minecraft mc = Minecraft.getInstance();
        if (be == null || mc.level == null) {
            return List.of();
        }
        List<Component> out = new ArrayList<>();
        switch (this.device) {
            case RECEIVER -> {
                int radius = AlarmBlockEntity.RADII[Math.clamp(this.options[this.device.option("radius")], 0, AlarmBlockEntity.RADII.length - 1)];
                if (be.count() > 0) {
                    long left = Math.max(0, be.impactAt() - mc.level.getGameTime());
                    out.add(Component.translatable(T_INCOMING, be.count(), radius).withStyle(ChatFormatting.RED));
                    out.add(Component.translatable(T_IMPACT, String.format("%d:%02d", left / 1200, left / 20 % 60)).withStyle(ChatFormatting.RED));
                } else {
                    out.add(Component.translatable(T_NONE, radius).withStyle(ChatFormatting.GREEN));
                    out.add(Component.empty());
                }
                out.add(Component.translatable(T_RADAR).withStyle(ChatFormatting.GRAY));
            }
            case MONITOR -> {
                out.add(Component.translatable(T_RADIATION, String.format("%.1f", be.reading())).withStyle(be.kind() == Alarms.Kind.RADIATION.ordinal()
                        ? ChatFormatting.RED : ChatFormatting.GREEN));
                out.add(Component.translatable(T_GAS, String.format("%.0f", be.gas())).withStyle(be.kind() == Alarms.Kind.GAS.ordinal()
                        ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
            case PANEL -> out.add(be.active() ? Component.translatable(T_PANEL_ON).withStyle(ChatFormatting.RED) : Component.translatable(T_HINT).withStyle(ChatFormatting.GRAY));
            default -> out.add(be.active() ? Component.translatable(T_NOW_ON).withStyle(ChatFormatting.RED) : Component.translatable(T_NOW_OFF).withStyle(ChatFormatting.GREEN));
        }
        return out;
    }
}
