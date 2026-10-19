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
 * and fluids toggle. Auto-eject pushes products out of every Output face.
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
    /** Unfolded-cube layout: column, row of each face. */
    private static final int[][] CELLS = new int[Face.values().length][];

    static {
        CELLS[Face.TOP.ordinal()] = new int[] {1, 0};
        CELLS[Face.LEFT.ordinal()] = new int[] {0, 1};
        CELLS[Face.FRONT.ordinal()] = new int[] {1, 1};
        CELLS[Face.RIGHT.ordinal()] = new int[] {2, 1};
        CELLS[Face.BOTTOM.ordinal()] = new int[] {1, 2};
        CELLS[Face.BACK.ordinal()] = new int[] {2, 2};
    }

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
        if (this.menu.machine().tank() != null) {
            list.add(Channel.FLUID);
        }
        return list;
    }

    private Channel current() {
        return channels().contains(selected) ? selected : Channel.ITEMS;
    }

    /** Screen-space area the tab occupies (also reported to JEI so it moves out of the way). */
    Rect2i area(int left, int top, int imageWidth) {
        return open ? new Rect2i(left + imageWidth - 2, top + 4, PANEL_W, PANEL_H) : new Rect2i(left + imageWidth - 2, top + 4, TAB_W, TAB_H);
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
            channelButton(g, font, x + CHANNEL_X, y + CHANNEL_Y + i * CHANNEL_STEP, c == channel, c);
        }

        SideConfig sides = this.menu.sides();
        @Nullable Face hovered = null;
        for (Face face : Face.values()) {
            int fx = x + GRID_X + CELLS[face.ordinal()][0] * CELL, fy = y + GRID_Y + CELLS[face.ordinal()][1] * CELL;
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx, fy, FRAME_U, 0, 18, 18, 256, 256);
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx + 1, fy + 1, face == Face.FRONT ? FRONT_U : ICON_U, 0, 16, 16, 256, 256);
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
                hovered = face;
            }
        }

        boolean eject = this.menu.machine().hasOutputs();
        if (eject) {
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 8, y + 97, sides.autoEject() ? CHECK_ON_U : CHECK_OFF_U, CHECK_V, 10, 10, 256, 256);
            small(g, font, Component.translatable(MachineText.AUTO_EJECT), x + 21, y + 99, TEXT);
        }

        if (hovered != null) {
            Component value = switch (channel) {
                case ENERGY -> Component.translatable(sides.energy(hovered) ? MachineText.ENERGY_ON : MachineText.ENERGY_OFF);
                case FLUID -> Component.translatable(sides.fluid(hovered) ? MachineText.FLUID_ON : MachineText.FLUID_OFF);
                case ITEMS -> Component.translatable(sides.items(hovered).translationKey());
            };
            g.setTooltipForNextFrame(font, List.of(
                    Component.translatable(MachineText.FACE_MODE, Component.translatable(hovered.translationKey()), value),
                    Component.translatable(channel == Channel.ITEMS ? MachineText.HINT_CYCLE : MachineText.HINT_TOGGLE).withColor(TEXT_DIM)),
                    Optional.empty(), mouseX, mouseY);
        } else if (eject && inside(mouseX, mouseY, x + 6, y + 95, 90, 14)) {
            g.setTooltipForNextFrame(Component.translatable(MachineText.AUTO_EJECT_TIP), mouseX, mouseY);
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
            if (inside(mx, my, x + CHANNEL_X, y + CHANNEL_Y + i * CHANNEL_STEP, CHANNEL_W, CHANNEL_H)) {
                selected = channels.get(i);
                sound();
                return true;
            }
        }
        Channel channel = current();
        for (Face face : Face.values()) {
            int fx = x + GRID_X + CELLS[face.ordinal()][0] * CELL, fy = y + GRID_Y + CELLS[face.ordinal()][1] * CELL;
            if (inside(mx, my, fx, fy, 18, 18)) {
                int id = switch (channel) {
                    case ENERGY -> MachineMenu.BUTTON_ENERGY;
                    case FLUID -> MachineMenu.BUTTON_FLUID;
                    case ITEMS -> button == 1 ? MachineMenu.BUTTON_ITEMS_PREV : MachineMenu.BUTTON_ITEMS_NEXT;
                };
                send(id + face.ordinal());
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
