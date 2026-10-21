package dev.groundzero.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineText;
import dev.groundzero.content.machine.SideConfig;
import dev.groundzero.content.machine.SideConfig.Face;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * The side-configuration tab on the right edge of every machine screen (the convention of Thermal, Mekanism and
 * friends). Collapsed it is a small wrench tab; expanded it shows channel buttons stacked on the left (items or fuel,
 * energy, and fluids for machines with a tank) and the machine's six faces in an unfolded-cube layout on the right.
 * Items cycle None, Input, Output, Input & Output (right-click goes back; only modes the machine supports); energy
 * and fluids toggle. Auto-eject pushes products out of every Output face. A two-block-wide machine (the Assembly Bench)
 * shows both halves unfolded side by side: its top, front, bottom and back once per half, and its two end faces.
 */
final class SideConfigTab {
    private static final Identifier TEXTURE = GroundZero.id("textures/gui/machine/side_config.png");
    // Sprite positions, mirrored from tools/assets/gui.py.
    private static final int TAB_W = 24, TAB_H = 26, PANEL_V = 32, PANEL_W = 112, PANEL_H = 112;
    private static final int FRAME_U = 120, ICON_U = 140, FRONT_U = 160, CHANNEL_U = 120, CHANNEL_ON_V = 24, CHANNEL_OFF_V = 40;
    private static final int CHECK_V = 56, CHECK_OFF_U = 120, CHECK_ON_U = 132;
    private static final int ICON_ITEMS_U = 164, ICON_ENERGY_U = 174, ICON_V = 24, ICON_FLUID_U = 164, ICON_FLUID_V = 36;
    private static final int CHANNEL_X = 6, CHANNEL_Y = 22, CHANNEL_W = 40, CHANNEL_H = 12, CHANNEL_STEP = 14;
    private static final int TEXT = 0xFFE6E2D2, TEXT_DIM = 0xFF9AA0A8;
    private static final int GRID_X = 50, GRID_Y = 22, CELL = 20;
    /** A face square in the unfolded layout: which face, of which half (true: a wide machine's side half), where. */
    private record Cell(Face face, boolean half, int col, int row) {}

    /** Unfolded-cube layout of a one-block machine. */
    private static final List<Cell> CELLS = List.of(new Cell(Face.TOP, false, 1, 0), new Cell(Face.LEFT, false, 0, 1),
            new Cell(Face.FRONT, false, 1, 1), new Cell(Face.RIGHT, false, 2, 1), new Cell(Face.BOTTOM, false, 1, 2),
            new Cell(Face.BACK, false, 2, 2));
    /**
     * A two-block-wide machine unfolded: the main half on the left as you face its front, the side half on the right;
     * the backs unfold past the right end (the right half's back first).
     */
    private static final List<Cell> WIDE_CELLS = List.of(new Cell(Face.TOP, false, 1, 0), new Cell(Face.TOP, true, 2, 0),
            new Cell(Face.LEFT, false, 0, 1), new Cell(Face.FRONT, false, 1, 1), new Cell(Face.FRONT, true, 2, 1),
            new Cell(Face.RIGHT, true, 3, 1), new Cell(Face.BOTTOM, false, 1, 2), new Cell(Face.BOTTOM, true, 2, 2),
            new Cell(Face.BACK, true, 3, 2), new Cell(Face.BACK, false, 4, 2));
    /** The wide layout keeps the panel's size: channel buttons go in a row along the top, the faces in five columns under them. */
    private static final int WIDE_GRID_X = 6, WIDE_GRID_Y = 38, WIDE_CHANNEL_STEP = 44;
    private static final String T_LEFT_HALF = dev.groundzero.registry.GZLang.add(GroundZero.MODID + ".side.left_half", "%s, left half");
    private static final String T_RIGHT_HALF = dev.groundzero.registry.GZLang.add(GroundZero.MODID + ".side.right_half", "%s, right half");

    private enum Channel { ITEMS, ENERGY, FLUID }

    /** Remembered across screens, like the tabs in other tech mods. */
    private static boolean open;
    private static Channel selected = Channel.ITEMS;

    private final MachineMenu menu;

    SideConfigTab(MachineMenu menu) {
        this.menu = menu;
    }

    private List<Channel> channels() {
        List<Channel> list = new ArrayList<>(List.of(Channel.ITEMS, Channel.ENERGY));
        if (!this.menu.machine().tanks().isEmpty()) {
            list.add(Channel.FLUID);
        }
        return list;
    }

    private Channel current() {
        return channels().contains(selected) ? selected : Channel.ITEMS;
    }

    private boolean wide() {
        return this.menu.machine().wide();
    }

    private List<Cell> cells() {
        return wide() ? WIDE_CELLS : CELLS;
    }

    private int panelWidth() {
        return PANEL_W;
    }

    private int gridX() {
        return wide() ? WIDE_GRID_X : GRID_X;
    }

    private int gridY() {
        return wide() ? WIDE_GRID_Y : GRID_Y;
    }

    /** Where channel button {@code i} sits on the panel: stacked on the left, or in a row along the top (wide machines). */
    private int channelX(int i) {
        return CHANNEL_X + (wide() ? i * WIDE_CHANNEL_STEP : 0);
    }

    private int channelY(int i) {
        return CHANNEL_Y + (wide() ? 0 : i * CHANNEL_STEP);
    }

    private SideConfig sides(boolean half) {
        return half ? this.menu.sideHalf() : this.menu.sides();
    }

    /** A face's name: on a wide machine, which half (its ends are just Left and Right). */
    private Component faceName(Cell cell) {
        Component name = Component.translatable(cell.face().translationKey());
        if (!wide() || cell.face() == Face.LEFT || cell.face() == Face.RIGHT) {
            return name;
        }
        return Component.translatable(cell.half() ? T_RIGHT_HALF : T_LEFT_HALF, name);
    }

    /** Screen-space area the tab occupies (also reported to JEI so it moves out of the way). */
    Rect2i area(int left, int top, int imageWidth) {
        return open ? new Rect2i(left + imageWidth - 2, top + 4, panelWidth(), PANEL_H) : new Rect2i(left + imageWidth - 2, top + 4, TAB_W, TAB_H);
    }

    void render(GuiGraphicsExtractor g, Font font, int left, int top, int imageWidth, int mouseX, int mouseY) {
        Rect2i a = area(left, top, imageWidth);
        int x = a.getX(), y = a.getY();
        if (!open) {
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, TAB_W, TAB_H, 256, 256);
            if (a.contains(mouseX, mouseY)) {
                g.setTooltipForNextFrame(Component.translatable(MachineText.SIDES_TAB), mouseX, mouseY);
            }
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, PANEL_V, PANEL_W, PANEL_H, 256, 256);
        small(g, font, Component.translatable(MachineText.SIDES_TITLE), x + 20, y + 7, TEXT);

        Channel channel = current();
        List<Channel> channels = channels();
        for (int i = 0; i < channels.size(); i++) {
            Channel c = channels.get(i);
            channelButton(g, font, x + channelX(i), y + channelY(i), c == channel, c);
        }

        @Nullable Cell hovered = null;
        for (Cell cell : cells()) {
            Face face = cell.face();
            SideConfig sides = sides(cell.half());
            int fx = x + gridX() + cell.col() * CELL, fy = y + gridY() + cell.row() * CELL;
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx, fy, FRAME_U, 0, 18, 18, 256, 256);
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx + 1, fy + 1, face == Face.FRONT ? FRONT_U : ICON_U, 0, 16, 16, 256, 256);
            if (!this.menu.machine().faceOpen(face, cell.half()) || channel == Channel.FLUID && !this.menu.machine().fluidFaceAllowed(face)) {
                blocked(g, fx, fy);
                if (mouseX >= fx && mouseX < fx + 18 && mouseY >= fy && mouseY < fy + 18) {
                    g.setTooltipForNextFrame(Component.translatable(MachineText.FACE_COVERED, faceName(cell)), mouseX, mouseY);
                }
                continue;
            }
            switch (channel) {
                case ENERGY -> toggleOverlay(g, fx, fy, sides.energy(face), 0x90FFD23C, ICON_ENERGY_U, ICON_V);
                case FLUID -> toggleOverlay(g, fx, fy, sides.fluid(face), 0x903C78FF, ICON_FLUID_U, ICON_FLUID_V);
                case ITEMS -> {
                    SideConfig.ItemMode mode = sides.items(face);
                    g.fill(fx + 1, fy + 1, fx + 17, fy + 17, mode == SideConfig.ItemMode.NONE ? 0xA0101214 : mode.color());
                }
            }
            if (mouseX >= fx && mouseX < fx + 18 && mouseY >= fy && mouseY < fy + 18) {
                g.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0x40FFFFFF);
                hovered = cell;
            }
        }
        SideConfig sides = this.menu.sides();

        boolean eject = this.menu.machine().hasOutputs();
        if (eject) {
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 8, y + 97, sides.autoEject() ? CHECK_ON_U : CHECK_OFF_U, CHECK_V, 10, 10, 256, 256);
            small(g, font, Component.translatable(MachineText.AUTO_EJECT), x + 21, y + 99, TEXT);
        }

        if (hovered != null) {
            SideConfig hs = sides(hovered.half());
            Face hf = hovered.face();
            Component value = switch (channel) {
                case ENERGY -> Component.translatable(hs.energy(hf) ? MachineText.ENERGY_ON : MachineText.ENERGY_OFF);
                case FLUID -> Component.translatable(hs.fluid(hf) ? MachineText.FLUID_ON : MachineText.FLUID_OFF);
                case ITEMS -> Component.translatable(hs.items(hf).translationKey());
            };
            g.setTooltipForNextFrame(font, List.of(
                    Component.translatable(MachineText.FACE_MODE, faceName(hovered), value),
                    Component.translatable(channel == Channel.ITEMS ? MachineText.HINT_CYCLE : MachineText.HINT_TOGGLE).withColor(TEXT_DIM)),
                    Optional.empty(), mouseX, mouseY);
        } else if (eject && inside(mouseX, mouseY, x + 6, y + 95, 90, 14)) {
            g.setTooltipForNextFrame(Component.translatable(MachineText.AUTO_EJECT_TIP), mouseX, mouseY);
        }
    }

    /** A face against the machine's own structure: dark, with a cross through it. */
    private static void blocked(GuiGraphicsExtractor g, int fx, int fy) {
        g.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0xE00A0C0E);
        for (int i = 0; i < 10; i++) {
            g.fill(fx + 4 + i, fy + 4 + i, fx + 5 + i, fy + 5 + i, 0xFF5A3030);
            g.fill(fx + 13 - i, fy + 4 + i, fx + 14 - i, fy + 5 + i, 0xFF5A3030);
        }
    }

    private static void toggleOverlay(GuiGraphicsExtractor g, int fx, int fy, boolean on, int color, int iconU, int iconV) {
        if (on) {
            g.fill(fx + 1, fy + 1, fx + 17, fy + 17, color);
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx + 5, fy + 5, iconU, iconV, 8, 8, 256, 256);
        } else {
            g.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0xA0101214);
        }
    }

    private void channelButton(GuiGraphicsExtractor g, Font font, int x, int y, boolean selected, Channel channel) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, CHANNEL_U, selected ? CHANNEL_ON_V : CHANNEL_OFF_V, CHANNEL_W, CHANNEL_H, 256, 256);
        int iconU = channel == Channel.ITEMS ? ICON_ITEMS_U : channel == Channel.ENERGY ? ICON_ENERGY_U : ICON_FLUID_U;
        int iconV = channel == Channel.FLUID ? ICON_FLUID_V : ICON_V;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 2, y + 2, iconU, iconV, 8, 8, 256, 256);
        String key = switch (channel) {
            case ITEMS -> this.menu.machine().itemsAreFuel() ? MachineText.CHANNEL_FUEL : MachineText.CHANNEL_ITEMS;
            case ENERGY -> MachineText.CHANNEL_ENERGY;
            case FLUID -> MachineText.CHANNEL_FLUID;
        };
        small(g, font, Component.translatable(key), x + 11, y + 3, selected ? TEXT : TEXT_DIM);
    }

    /** Text at 3/4 scale, the size the tab's labels are laid out for. */
    private static void small(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Handles a click; returns true if the tab consumed it. */
    boolean click(double mx, double my, int button, int left, int top, int imageWidth) {
        Rect2i a = area(left, top, imageWidth);
        if (!a.contains((int) mx, (int) my)) {
            return false;
        }
        int x = a.getX(), y = a.getY();
        if (!open) {
            open = true;
            sound();
            return true;
        }
        if (inside(mx, my, x, y, 20, 18)) {                       // wrench header collapses the tab
            open = false;
            sound();
            return true;
        }
        List<Channel> channels = channels();
        for (int i = 0; i < channels.size(); i++) {
            if (inside(mx, my, x + channelX(i), y + channelY(i), CHANNEL_W, CHANNEL_H)) {
                selected = channels.get(i);
                sound();
                return true;
            }
        }
        Channel channel = current();
        for (Cell cell : cells()) {
            Face face = cell.face();
            int fx = x + gridX() + cell.col() * CELL, fy = y + gridY() + cell.row() * CELL;
            if (inside(mx, my, fx, fy, 18, 18)) {
                if (!this.menu.machine().faceOpen(face, cell.half()) || channel == Channel.FLUID && !this.menu.machine().fluidFaceAllowed(face)) {
                    return true;
                }
                int id = switch (channel) {
                    case ENERGY -> MachineMenu.BUTTON_ENERGY;
                    case FLUID -> MachineMenu.BUTTON_FLUID;
                    case ITEMS -> button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT ? MachineMenu.BUTTON_ITEMS_PREV : MachineMenu.BUTTON_ITEMS_NEXT;
                };
                send(id + face.ordinal() + (cell.half() ? MachineMenu.BUTTON_SIDE_HALF : 0));
                return true;
            }
        }
        if (this.menu.machine().hasOutputs() && inside(mx, my, x + 6, y + 95, 90, 14)) {
            send(MachineMenu.BUTTON_AUTO_EJECT);
            return true;
        }
        return true;   // clicks on the panel background never fall through to "drop item"
    }

    private void send(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
        sound();
    }

    private static void sound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
