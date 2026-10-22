package dev.groundzero.client.screen;

import dev.groundzero.content.machine.FissionCore;
import dev.groundzero.content.machine.FissionReactorBlockEntity;
import dev.groundzero.content.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Fission Reactor's screen: the machine screen plus its core map (DESIGN.md §23.3). The window has a 4x4 map of
 * lead casing; this draws the reactor's own core on it, as the menu last synced it: a fuel channel (a rod slot) on
 * each channel cell, coolant on the rest of the core, a red-ringed slot where a rod is left on a cell that is no longer
 * a channel (so it can be taken out). A smaller core sits in the middle of the map.
 */
public class FissionReactorScreen extends MachineScreen {
    /** Mirrors tools/assets/gui.py: the map's top-left cell and the cell sprites (texture x, y; 18 x 18 each). */
    private static final int MAP_X = 43, MAP_Y = 19, CELL = 18;
    private static final int CHANNEL_U = 436, COOLANT_U = 456, STRAY_U = 476, SPRITE_V = 80;

    public FissionReactorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private int channels() {
        return this.menu.extra(FissionReactorBlockEntity.DATA_CHANNELS) & 0xFFFF;
    }

    /** Whether map cell {@code slot} lies in the core, from the synced size (false while not formed). */
    private boolean inCore(int slot) {
        int size = this.menu.extra(FissionReactorBlockEntity.DATA_SIZE);
        if (size == 0) {
            return false;
        }
        int w = (size & 7) - 2, d = (size >> 6 & 7) - 2;
        int u = slot % FissionCore.GRID - (FissionCore.GRID - w) / 2, v = slot / FissionCore.GRID - (FissionCore.GRID - d) / 2;
        return u >= 0 && u < w && v >= 0 && v < d;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int channels = channels();
        for (int slot = 0; slot < FissionReactorBlockEntity.CHANNELS; slot++) {
            int x = this.leftPos + MAP_X + (slot % FissionCore.GRID) * CELL, y = this.topPos + MAP_Y + (slot / FissionCore.GRID) * CELL;
            boolean channel = (channels >> slot & 1) != 0;
            if (channel || inCore(slot)) {
                blitCell(g, channel ? CHANNEL_U : COOLANT_U, x, y);
            }
            if (!channel && !this.menu.machine().inventory().getItem(slot).isEmpty()) {
                blitCell(g, STRAY_U, x, y);
            }
        }
    }

    private void blitCell(GuiGraphicsExtractor g, int u, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, pageLayout().texture(), x, y, u, SPRITE_V, CELL, CELL, pageLayout().textureWidth(), 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int mx = mouseX - this.leftPos - MAP_X, my = mouseY - this.topPos - MAP_Y;
        if (mx < 0 || my < 0 || mx >= FissionCore.GRID * CELL || my >= FissionCore.GRID * CELL) {
            return;
        }
        int slot = (my / CELL) * FissionCore.GRID + mx / CELL;
        if (this.menu.slotShown(slot)) {
            return;                                    // a rod slot: the item (or the empty slot) speaks for itself
        }
        String key = this.menu.extra(FissionReactorBlockEntity.DATA_SIZE) == 0 ? FissionReactorBlockEntity.MAP_UNFORMED
                : inCore(slot) ? FissionReactorBlockEntity.MAP_COOLANT : FissionReactorBlockEntity.MAP_CASING;
        g.setTooltipForNextFrame(Component.translatable(key), mouseX, mouseY);
    }
}
