package dev.groundzero.client.screen;

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
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * The side-configuration tab on the right edge of every machine screen (the convention of Thermal, Mekanism and
 * friends). Collapsed it is a small wrench tab; expanded it shows the machine's six faces in an unfolded-cube
 * layout. Pick a channel (items/fuel or energy), then click a face: items cycle None, Input, Output, Input &
 * Output (right-click goes back); energy toggles. Auto-eject pushes products out of every Output face.
 */
final class SideConfigTab {
    private static final Identifier TEXTURE = GroundZero.id("textures/gui/machine/side_config.png");
    // Sprite positions, mirrored from tools/assets/gui.py.
    private static final int TAB_W = 24, TAB_H = 26, PANEL_V = 32, PANEL_W = 82, PANEL_H = 112;
    private static final int FRAME_U = 100, ICON_U = 120, FRONT_U = 140, CHANNEL_ON_V = 24, CHANNEL_OFF_V = 40;
    private static final int CHECK_V = 56, CHECK_OFF_U = 100, CHECK_ON_U = 112;
    private static final int ICON_ITEMS_U = 140, ICON_ENERGY_U = 150, ICON_V = 24;
    private static final int TEXT = 0xFFE6E2D2, TEXT_DIM = 0xFF9AA0A8;
    private static final int GRID_X = 11, GRID_Y = 34, CELL = 20;
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

    /** Remembered across screens, like the tabs in other tech mods. */
    private static boolean open;
    private static boolean energyChannel;

    private final MachineMenu menu;

    SideConfigTab(MachineMenu menu) {
        this.menu = menu;
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

        boolean fuel = this.menu.machine().itemsAreFuel();
        channelButton(g, font, x + 6, y + 20, !energyChannel, ICON_ITEMS_U, Component.translatable(fuel ? MachineText.CHANNEL_FUEL : MachineText.CHANNEL_ITEMS));
        channelButton(g, font, x + 42, y + 20, energyChannel, ICON_ENERGY_U, Component.translatable(MachineText.CHANNEL_ENERGY));

        SideConfig sides = this.menu.sides();
        @Nullable Face hovered = null;
        for (Face face : Face.values()) {
            int fx = x + GRID_X + CELLS[face.ordinal()][0] * CELL, fy = y + GRID_Y + CELLS[face.ordinal()][1] * CELL;
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx, fy, FRAME_U, 0, 18, 18, 256, 256);
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx + 1, fy + 1, face == Face.FRONT ? FRONT_U : ICON_U, 0, 16, 16, 256, 256);
            if (energyChannel) {
                if (sides.energy(face)) {
                    g.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0x90FFD23C);
                    g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx + 5, fy + 5, ICON_ENERGY_U, ICON_V, 8, 8, 256, 256);
                } else {
                    g.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0xA0101214);
                }
            } else {
                SideConfig.ItemMode mode = sides.items(face);
                g.fill(fx + 1, fy + 1, fx + 17, fy + 17, mode == SideConfig.ItemMode.NONE ? 0xA0101214 : mode.color());
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
            Component value = energyChannel
                    ? Component.translatable(sides.energy(hovered) ? MachineText.ENERGY_ON : MachineText.ENERGY_OFF)
                    : Component.translatable(sides.items(hovered).translationKey());
            g.setTooltipForNextFrame(font, List.of(
                    Component.translatable(MachineText.FACE_MODE, Component.translatable(hovered.translationKey()), value),
                    Component.translatable(energyChannel ? MachineText.HINT_TOGGLE : MachineText.HINT_CYCLE).withColor(TEXT_DIM)), Optional.empty(), mouseX, mouseY);
        } else if (eject && inside(mouseX, mouseY, x + 6, y + 95, 70, 14)) {
            g.setTooltipForNextFrame(Component.translatable(MachineText.AUTO_EJECT_TIP), mouseX, mouseY);
        }
    }

    private static void channelButton(GuiGraphicsExtractor g, Font font, int x, int y, boolean selected, int iconU, Component label) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, FRAME_U, selected ? CHANNEL_ON_V : CHANNEL_OFF_V, 36, 12, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 2, y + 2, iconU, ICON_V, 8, 8, 256, 256);
        small(g, font, label, x + 11, y + 3, selected ? TEXT : TEXT_DIM);
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
        if (inside(mx, my, x + 6, y + 20, 36, 12)) {
            energyChannel = false;
            sound();
            return true;
        }
        if (inside(mx, my, x + 42, y + 20, 36, 12)) {
            energyChannel = true;
            sound();
            return true;
        }
        for (Face face : Face.values()) {
            int fx = x + GRID_X + CELLS[face.ordinal()][0] * CELL, fy = y + GRID_Y + CELLS[face.ordinal()][1] * CELL;
            if (inside(mx, my, fx, fy, 18, 18)) {
                int id = energyChannel ? MachineMenu.BUTTON_ENERGY : button == 1 ? MachineMenu.BUTTON_ITEMS_PREV : MachineMenu.BUTTON_ITEMS_NEXT;
                send(id + face.ordinal());
                return true;
            }
        }
        if (this.menu.machine().hasOutputs() && inside(mx, my, x + 6, y + 95, 70, 14)) {
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
