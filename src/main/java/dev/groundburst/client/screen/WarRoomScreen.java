package dev.groundburst.client.screen;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.render.WarRoomBoard;
import dev.groundburst.content.command.WarRoomDisplayBlock;
import dev.groundburst.content.command.WarRoomDisplayBlockEntity;
import dev.groundburst.content.command.WarRoomPayloads;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The War Room Display's settings (DESIGN.md §17.17): its view (everything fitted in, or centred on it at a scale) and the
 * layers it draws (terrain, the radars' reach, labels, the log, blasts and impacts); which network it shows.
 */
public final class WarRoomScreen extends Screen {
    private static final String P = GroundBurst.MODID + ".war_room.screen.";
    private static final String T_VIEW_FIT = GBLang.add(P + "view.fit", "View: everything in");
    private static final String T_VIEW_AT = GBLang.add(P + "view.at", "View: centred here, 1:%s");
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
    private static final String T_DONE = GBLang.add(P + "done", "Done");
    private static final int W = 220, H = 196;
    private static final int PANEL = 0xF0101410, EDGE = 0xFF3A4A3A, TEXT = 0xFFC8F0C8, AMBER = 0xFFFFB040;

    private final BlockPos pos;
    private int view, layers = WarRoomDisplayBlockEntity.ALL_LAYERS;

    private WarRoomScreen(BlockPos pos, WarRoomDisplayBlockEntity be) {
        super(Component.translatable("block.groundburst.war_room_display"));
        this.pos = pos;
        WarRoomBoard board = WarRoomBoard.of(pos);
        WarRoomPayloads.Board b = board == null ? null : board.board();
        if (b != null) {
            this.view = b.view();
            this.layers = b.layers();
        }
    }

    public static void register() {
        WarRoomDisplayBlock.screenHook = pos -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getBlockEntity(pos) instanceof WarRoomDisplayBlockEntity be) {
                mc.setScreen(new WarRoomScreen(pos, be));
            }
        };
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    private Component viewLabel() {
        return this.view == 0 ? Component.translatable(T_VIEW_FIT)
                : Component.translatable(T_VIEW_AT, WarRoomDisplayBlockEntity.SCALES[Math.clamp(this.view, 1, WarRoomDisplayBlockEntity.SCALES.length - 1)]);
    }

    private Component layerLabel(int i) {
        boolean on = (this.layers & (1 << i)) != 0;
        return Component.translatable(on ? T_ON : T_OFF, Component.translatable(T_LAYER[i]));
    }

    @Override
    protected void init() {
        int x = left() + 10, y = top() + 24;                         // six rows of 18, 21 apart: to 147; the network's line at 154
        addRenderableWidget(Button.builder(viewLabel(), b -> {
            this.view = (this.view + 1) % WarRoomDisplayBlockEntity.SCALES.length;
            b.setMessage(viewLabel());
            send();
        }).bounds(x, y, W - 20, 18).build());
        for (int i = 0; i < T_LAYER.length; i++) {
            final int bit = i;
            addRenderableWidget(Button.builder(layerLabel(i), b -> {
                this.layers ^= 1 << bit;
                b.setMessage(layerLabel(bit));
                send();
            }).bounds(x, y + 21 * (i + 1), W - 20, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(T_DONE), b -> onClose()).bounds(left() + (W - 80) / 2, top() + H - 24, 80, 18).build());
    }

    private void send() {
        ClientPacketDistributor.sendToServer(new WarRoomPayloads.Settings(this.pos, this.view, this.layers));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(left(), top(), left() + W, top() + H, PANEL);
        g.outline(left(), top(), W, H, EDGE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(this.font, this.title, left() + 10, top() + 8, TEXT, false);
        WarRoomBoard board = WarRoomBoard.of(this.pos);
        WarRoomPayloads.@Nullable Board b = board == null ? null : board.board();
        Component shows = b == null || b.code().isEmpty() ? Component.translatable(T_SHOWS_NONE) : Component.translatable(T_SHOWS, b.code());
        g.text(this.font, this.font.plainSubstrByWidth(shows.getString(), W - 20), left() + 10, top() + 154, AMBER, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
