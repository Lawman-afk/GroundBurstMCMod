package dev.groundburst.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.UpgradeChipItem;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The Upgrades tab on the left edge of a machine's or an assembler's screen (DESIGN.md §7.3), the side-configuration
 * tab's mirror. Collapsed it is a small tab showing the first fitted chip (or a chip glyph); expanded it holds the three
 * upgrade slots (real slots of the menu: {@link MachineMenu#UPGRADE_SLOT_X}) and says what the chips do together: how
 * much faster, how much more efficient (after the user's look: no lines on the power drawn a tick or a job).
 */
final class UpgradeTab {
    private static final Identifier TEXTURE = GroundBurst.id("textures/gui/machine/upgrades.png");
    // Sprite positions, mirrored from tools/assets/gui.py (upgrades_texture).
    private static final int TAB_W = 24, TAB_H = 26, PANEL_V = 32, PANEL_W = 112, PANEL_H = 64;
    /** The panel's first slot frame, from its corner (the item sits a pixel in: the menu's slot); the others beside it. */
    private static final int SLOT_X = 8, SLOT_Y = 22;
    /** Where its two lines start, under the slots. */
    private static final int LINES_X = 8, LINES_Y = 44;
    private static final int TEXT = 0xFFE6E2D2, TEXT_DIM = 0xFF9AA0A8, GOLD = 0xFFFFC04A, GREEN = 0xFF7CE08A;

    static final String T_TAB = GBLang.add(GroundBurst.MODID + ".upgrade.tab", "Upgrades");
    static final String T_SPEED = GBLang.add(GroundBurst.MODID + ".upgrade.tab_speed", "Speed: +%s%%");
    static final String T_SPEED_SAME = GBLang.add(GroundBurst.MODID + ".upgrade.tab_speed_same", "Speed: normal");
    static final String T_EFFICIENCY = GBLang.add(GroundBurst.MODID + ".upgrade.tab_efficiency", "Efficiency: +%s%%");
    static final String T_EFFICIENCY_SAME = GBLang.add(GroundBurst.MODID + ".upgrade.tab_efficiency_same", "Efficiency: normal");

    private final MachineMenu menu;

    UpgradeTab(MachineMenu menu) {
        this.menu = menu;
    }

    private static boolean open() {
        return MachineMenu.upgradeTabOpen;
    }

    /** Screen-space area the tab occupies (also reported to JEI so it moves out of the way). */
    Rect2i area(int left, int top) {
        return open() ? new Rect2i(left - PANEL_W + 2, top + 4, PANEL_W, PANEL_H) : new Rect2i(left - TAB_W + 2, top + 4, TAB_W, TAB_H);
    }

    /** The tab or its panel, drawn with the window, under the slots (the chip is drawn in its slot by the menu). */
    void renderBackground(GuiGraphicsExtractor g, int left, int top) {
        Rect2i a = area(left, top);
        if (open()) {
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, a.getX(), a.getY(), 0, PANEL_V, PANEL_W, PANEL_H, 256, 256);
        } else {
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, a.getX(), a.getY(), 0, 0, TAB_W, TAB_H, 256, 256);
        }
    }

    /** Its words, the first fitted chip on the closed tab, and its tooltip: drawn over the window. */
    void renderForeground(GuiGraphicsExtractor g, Font font, int left, int top, int mouseX, int mouseY) {
        Rect2i a = area(left, top);
        int x = a.getX(), y = a.getY();
        var slots = this.menu.machine().upgrades();
        List<ItemStack> fitted = new ArrayList<>();
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                fitted.add(slots.getItem(i));
            }
        }
        if (!open()) {
            if (!fitted.isEmpty()) {
                g.item(fitted.getFirst(), x + 4, y + 5);
            }
            if (a.contains(mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(T_TAB));
                fitted.forEach(chip -> lines.add(chip.getHoverName().copy().withColor(TEXT_DIM)));
                g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
            }
            return;
        }
        small(g, font, Component.translatable(T_TAB), x + 21, y + 7, TEXT);
        float speed = this.menu.machine().speedBonus(), saving = this.menu.machine().efficiency();
        int tx = x + LINES_X, ty = y + LINES_Y;
        small(g, font, speed > 0 ? Component.translatable(T_SPEED, UpgradeChipItem.percent(speed)) : Component.translatable(T_SPEED_SAME),
                tx, ty, speed > 0 ? GOLD : TEXT_DIM);
        small(g, font, saving > 0 ? Component.translatable(T_EFFICIENCY, UpgradeChipItem.percent(saving))
                : Component.translatable(T_EFFICIENCY_SAME), tx, ty + 9, saving > 0 ? GREEN : TEXT_DIM);
    }

    /** Handles a click; returns true if the tab consumed it (a click on its slot is left to the menu). */
    boolean click(double mx, double my, int left, int top) {
        Rect2i a = area(left, top);
        if (!a.contains((int) mx, (int) my)) {
            return false;
        }
        int x = a.getX(), y = a.getY();
        if (!open()) {
            MachineMenu.upgradeTabOpen = true;
            sound();
            return true;
        }
        if (inside(mx, my, x + SLOT_X, y + SLOT_Y, MachineMenu.UPGRADE_SLOT_STEP * this.menu.machine().upgrades().getContainerSize(), 18)) {
            return false;                                  // the slots: the menu's to handle
        }
        if (inside(mx, my, x, y, PANEL_W, 18)) {           // the header collapses it
            MachineMenu.upgradeTabOpen = false;
            sound();
        }
        return true;                                       // never falls through to "drop the item"
    }

    /** Whether a point is on the tab (so a click there is not a click outside the window). */
    boolean contains(double mx, double my, int left, int top) {
        return area(left, top).contains((int) mx, (int) my);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Text at 3/4 scale, the size the tabs' labels are laid out for. */
    private static void small(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static void sound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
