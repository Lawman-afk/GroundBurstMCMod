package dev.groundzero.client.screen;

import dev.groundzero.content.machine.FissionCore;
import dev.groundzero.content.machine.FissionReactorBlockEntity;
import dev.groundzero.content.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Fission Reactor's screen: the machine screen plus its core map (DESIGN.md §7.2). The window has a 4x4 map of lead
 * casing with a tab for each core layer beside it; this draws the picked layer of the reactor's core on the map, as the
 * menu last synced it: a fuel channel (a rod slot) on each channel cell, coolant on the rest of the core, a red-ringed
 * slot where a rod is left on a cell that is no longer a channel (so it can be taken out). A smaller core sits in the
 * middle of the map, from its bottom layer up. While the reactor is not formed, a plan panel covers the map: the size
 * of the reactor the ghost preview shows, with buttons to change it.
 */
public class FissionReactorScreen extends MachineScreen {
    /** Mirrors tools/assets/gui.py: the map's top-left cell, the layer tabs and the cell sprites (texture x, y; 18 x 18). */
    private static final int MAP_X = 57, MAP_Y = 19, CELL = 18, TAB_X = 42, TAB_Y = 74, TAB_W = 11, TAB_H = 17;
    private static final int CHANNEL_U = 436, COOLANT_U = 456, STRAY_U = 476, SPRITE_V = 80;
    /** The plan panel's rows (W, H, D): the y of each row's buttons, and the buttons' x. */
    private static final int PLAN_ROW_Y = 37, PLAN_STEP = 18, MINUS_X = 74, PLUS_X = 112, BUTTON = 12;
    private static final int TEXT = 0xFFE6E2D2, DIM = 0xFF9AA0A8;

    public FissionReactorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private int size() {
        return this.menu.extra(FissionReactorBlockEntity.DATA_SIZE);
    }

    private boolean formed() {
        return size() != 0;
    }

    private int layer() {
        return this.menu.extra(FissionReactorBlockEntity.DATA_LAYER);
    }

    /** The core's height, from the synced size (0 while not formed). */
    private int coreHeight() {
        return formed() ? (size() >> 3 & 7) - 2 : 0;
    }

    /** Whether map cell {@code cell} of the shown layer lies in the core, from the synced size. */
    private boolean inCore(int cell) {
        if (!formed()) {
            return false;
        }
        int w = (size() & 7) - 2, d = (size() >> 6 & 7) - 2;
        int u = cell % FissionCore.GRID - (FissionCore.GRID - w) / 2, v = cell / FissionCore.GRID - (FissionCore.GRID - d) / 2;
        return layer() < coreHeight() && u >= 0 && u < w && v >= 0 && v < d;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x0 = this.leftPos, y0 = this.topPos;
        if (!formed()) {
            plan(g, x0, y0, mouseX, mouseY);
            return;
        }
        int base = layer() * FissionCore.LAYER;
        for (int cell = 0; cell < FissionCore.LAYER; cell++) {
            int slot = base + cell;
            int x = x0 + MAP_X + (cell % FissionCore.GRID) * CELL, y = y0 + MAP_Y + (cell / FissionCore.GRID) * CELL;
            boolean channel = FissionReactorBlockEntity.channelBit(this.menu, slot);
            if (channel || inCore(cell)) {
                blitCell(g, channel ? CHANNEL_U : COOLANT_U, x, y);
            }
            if (!channel && !this.menu.machine().inventory().getItem(slot).isEmpty()) {
                blitCell(g, STRAY_U, x, y);
            }
        }
        // The layer tabs: the one shown lit, those above the core dimmed.
        for (int y = 0; y < FissionCore.GRID; y++) {
            int tx = x0 + TAB_X, ty = y0 + TAB_Y - y * CELL;
            if (y == layer()) {
                g.fill(tx, ty, tx + TAB_W, ty + 1, 0xFFFFC864);
                g.fill(tx, ty + TAB_H - 1, tx + TAB_W, ty + TAB_H, 0xFFFFC864);
                g.fill(tx, ty, tx + 1, ty + TAB_H, 0xFFFFC864);
                g.fill(tx + TAB_W - 1, ty, tx + TAB_W, ty + TAB_H, 0xFFFFC864);
            } else if (y >= coreHeight()) {
                g.fill(tx, ty, tx + TAB_W, ty + TAB_H, 0xA0181C20);
            }
        }
    }

    /**
     * The plan panel over the map: a dark plate, and minus and plus buttons on each row (the labels go on in
     * extractLabels). The layer tabs are dimmed: there is no core to page through yet.
     */
    private void plan(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY) {
        int px = x0 + MAP_X, py = y0 + MAP_Y, side = FissionCore.GRID * CELL;
        g.fill(px, py, px + side, py + side, 0xFF141A1E);
        g.fill(px, py, px + side, py + 1, 0xFF3C4A52);
        for (int y = 0; y < FissionCore.GRID; y++) {
            int tx = x0 + TAB_X, ty = y0 + TAB_Y - y * CELL;
            g.fill(tx, ty, tx + TAB_W, ty + TAB_H, 0xA0181C20);
        }
        for (int row = 0; row < 3; row++) {
            for (int bx : new int[] {MINUS_X, PLUS_X}) {
                int x = x0 + bx, y = y0 + PLAN_ROW_Y + row * PLAN_STEP;
                boolean hover = mouseX >= x && mouseY >= y && mouseX < x + BUTTON && mouseY < y + BUTTON;
                g.fill(x, y, x + BUTTON, y + BUTTON, 0xFF1A1E22);
                g.fill(x + 1, y + 1, x + BUTTON - 1, y + BUTTON - 1, hover ? 0xFF6A7680 : 0xFF4C565E);
                g.fill(x + 1, y + 1, x + BUTTON - 1, y + 2, 0xFF7E8A94);
                g.fill(x + 3, y + 5, x + BUTTON - 3, y + 7, 0xFFE6E2D2);                     // minus bar
                if (bx == PLUS_X) {
                    g.fill(x + 5, y + 3, x + 7, y + BUTTON - 3, 0xFFE6E2D2);                 // plus upright
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        if (formed()) {
            return;
        }
        int plan = this.menu.extra(FissionReactorBlockEntity.DATA_PLAN);
        int[] sizes = {plan & 7, plan >> 3 & 7, plan >> 6 & 7};
        String[] axes = {"W", "H", "D"};
        g.text(this.font, Component.literal("PLAN"), MAP_X + 3, MAP_Y + 4, TEXT, false);
        for (int row = 0; row < 3; row++) {
            int y = PLAN_ROW_Y + row * PLAN_STEP + 2;
            g.text(this.font, Component.literal(axes[row]), MAP_X + 4, y, DIM, false);
            String value = String.valueOf(sizes[row]);
            int mid = (MINUS_X + BUTTON + PLUS_X) / 2;
            g.text(this.font, Component.literal(value), mid - this.font.width(value) / 2, y, TEXT, false);
        }
    }

    private void blitCell(GuiGraphicsExtractor g, int u, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, pageLayout().texture(), x, y, u, SPRITE_V, CELL, CELL, pageLayout().textureWidth(), 256);
    }

    /** The plan button under a (window-relative) point: axis * 2 + (0 minus, 1 plus), or -1. */
    private int planButtonAt(int mx, int my) {
        if (formed()) {
            return -1;
        }
        for (int row = 0; row < 3; row++) {
            int y = PLAN_ROW_Y + row * PLAN_STEP;
            for (int k = 0; k < 2; k++) {
                int x = k == 0 ? MINUS_X : PLUS_X;
                if (mx >= x && my >= y && mx < x + BUTTON && my < y + BUTTON) {
                    return row * 2 + k;
                }
            }
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // Our tooltips first: the screen keeps the first one set in a frame (an item's replaces it).
        int mx = mouseX - this.leftPos, my = mouseY - this.topPos;
        Component tip = null;
        for (int y = 0; y < FissionCore.GRID && formed(); y++) {
            int ty = TAB_Y - y * CELL;
            if (mx >= TAB_X && mx < TAB_X + TAB_W && my >= ty && my < ty + TAB_H) {
                tip = Component.translatable(FissionReactorBlockEntity.LAYER_TIP, y + 1);
            }
        }
        int mapX = mx - MAP_X, mapY = my - MAP_Y, side = FissionCore.GRID * CELL;
        if (tip == null && mapX >= 0 && mapY >= 0 && mapX < side && mapY < side) {
            if (!formed()) {
                tip = planButtonAt(mx, my) >= 0 ? null : Component.translatable(FissionReactorBlockEntity.PLAN_TIP);
            } else {
                int cell = (mapY / CELL) * FissionCore.GRID + mapX / CELL;
                if (!this.menu.slotShown(layer() * FissionCore.LAYER + cell)) {
                    tip = Component.translatable(inCore(cell) ? FissionReactorBlockEntity.MAP_COOLANT : FissionReactorBlockEntity.MAP_CASING);
                }
            }
        }
        if (tip != null) {
            g.setTooltipForNextFrame(this.font, java.util.List.of(tip), java.util.Optional.empty(), mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = planButtonAt((int) event.x() - this.leftPos, (int) event.y() - this.topPos);
        if (button >= 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    MachineMenu.BUTTON_MACHINE + FissionReactorBlockEntity.BUTTON_PLAN + button);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
