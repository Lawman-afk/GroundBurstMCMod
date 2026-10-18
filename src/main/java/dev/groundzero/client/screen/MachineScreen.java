package dev.groundzero.client.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import dev.groundzero.content.machine.MachineLayout;
import dev.groundzero.content.machine.MachineText;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.ProcessingBlockEntity;
import dev.groundzero.content.power.CombustionGeneratorBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen every machine uses, driven entirely by its {@link MachineLayout}: window, energy gauge, progress
 * arrow or fuel flame, status lamp, redstone button and (for generators) a CRT readout, all with tooltips.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int TEXT = 0xFFE6E2D2;
    private static final int CRT_TEXT = 0xFFFFB84A;
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final String KEY_ENERGY = MachineText.ENERGY;
    private static final String KEY_PROGRESS = MachineText.PROGRESS;
    private static final String KEY_BURN = MachineText.BURN;
    private static final String KEY_OUTPUT = MachineText.OUTPUT;
    private static final String KEY_STORED = MachineText.STORED;
    private static final String KEY_REDSTONE = MachineText.REDSTONE;

    private final MachineLayout layout;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.layout().width(), menu.layout().height());
        this.layout = menu.layout();
        this.titleLabelX = this.layout.titleX();
        this.titleLabelY = this.layout.titleY();
        this.inventoryLabelX = this.layout.inventoryX();
        this.inventoryLabelY = this.layout.inventoryY() - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = this.leftPos;
        int y = this.topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, this.layout.texture(), x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        // Energy gauge: fill bottom-up.
        MachineLayout.Rect e = this.layout.energy();
        int cap = Math.max(1, this.menu.capacity());
        int fill = (int) ((long) this.menu.energy() * (e.h() - 2) / cap);
        if (fill > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, this.layout.texture(), x + e.x() + 1, y + e.y() + 1 + (e.h() - 2 - fill),
                    this.layout.energyFillU(), this.layout.energyFillV() + (e.h() - 2 - fill), e.w() - 2, fill, 256, 256);
        }

        // Progress arrow: fill left-to-right.
        MachineLayout.Rect p = this.layout.progress();
        if (p != null) {
            int max = this.menu.extra(ProcessingBlockEntity.DATA_MAX_PROGRESS);
            int w = max > 0 ? this.menu.extra(ProcessingBlockEntity.DATA_PROGRESS) * p.w() / max : 0;
            if (w > 0) {
                g.blit(RenderPipelines.GUI_TEXTURED, this.layout.texture(), x + p.x(), y + p.y(),
                        this.layout.progressFillU(), this.layout.progressFillV(), w, p.h(), 256, 256);
            }
        }

        // Fuel flame: fill bottom-up with remaining burn.
        MachineLayout.Rect f = this.layout.flame();
        if (f != null) {
            int total = this.menu.extra(CombustionGeneratorBlockEntity.DATA_BURN_TOTAL);
            int h = total > 0 ? Math.max(1, this.menu.extra(CombustionGeneratorBlockEntity.DATA_BURN) * f.h() / total) : 0;
            if (this.menu.extra(CombustionGeneratorBlockEntity.DATA_BURN) > 0 && h > 0) {
                g.blit(RenderPipelines.GUI_TEXTURED, this.layout.texture(), x + f.x(), y + f.y() + (f.h() - h),
                        this.layout.flameFillU(), this.layout.flameFillV() + (f.h() - h), f.w(), h, 256, 256);
            }
        }

        // Status lamp with a 1-px glow highlight.
        MachineLayout.Rect s = this.layout.status();
        int color = this.menu.status().lampColor();
        g.fill(x + s.x(), y + s.y(), x + s.x() + s.w(), y + s.y() + s.h(), color);
        g.fill(x + s.x() + 1, y + s.y() + 1, x + s.x() + 3, y + s.y() + 3, 0xC0FFFFFF);

        // Redstone mode icon on its button.
        MachineLayout.Rect r = this.layout.redstone();
        g.blit(RenderPipelines.GUI_TEXTURED, this.layout.texture(), x + r.x() + 1, y + r.y() + 1,
                this.layout.redstoneU() + this.menu.redstoneMode().ordinal() * 16, this.layout.redstoneV(), 16, 16, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
        MachineLayout.Rect ro = this.layout.readout();
        if (ro != null) {
            int cap = Math.max(1, this.menu.capacity());
            boolean burning = this.menu.extra(CombustionGeneratorBlockEntity.DATA_BURN) > 0;
            g.text(this.font, Component.translatable(KEY_OUTPUT, burning ? CombustionGeneratorBlockEntity.RATE : 0), ro.x() + 4, ro.y() + 4, CRT_TEXT, false);
            g.text(this.font, Component.translatable(KEY_STORED, (int) ((long) this.menu.energy() * 100 / cap)), ro.x() + 4, ro.y() + 14, CRT_TEXT, false);
            g.text(this.font, Component.translatable(this.menu.status().translationKey()), ro.x() + 4, ro.y() + 24, CRT_TEXT, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        if (this.layout.energy().contains(mx, my)) {
            g.setTooltipForNextFrame(Component.translatable(KEY_ENERGY, NUM.format(this.menu.energy()), NUM.format(this.menu.capacity())), mouseX, mouseY);
        } else if (this.layout.status().contains(mx, my)) {
            g.setTooltipForNextFrame(Component.translatable(this.menu.status().translationKey()), mouseX, mouseY);
        } else if (this.layout.redstone().contains(mx, my)) {
            g.setTooltipForNextFrame(Component.translatable(KEY_REDSTONE, Component.translatable(this.menu.redstoneMode().translationKey())), mouseX, mouseY);
        } else if (this.layout.progress() != null && this.layout.progress().contains(mx, my)) {
            int max = this.menu.extra(ProcessingBlockEntity.DATA_MAX_PROGRESS);
            int pct = max > 0 ? this.menu.extra(ProcessingBlockEntity.DATA_PROGRESS) * 100 / max : 0;
            g.setTooltipForNextFrame(Component.translatable(KEY_PROGRESS, pct), mouseX, mouseY);
        } else if (this.layout.flame() != null && this.layout.flame().contains(mx, my)) {
            g.setTooltipForNextFrame(Component.translatable(KEY_BURN, this.menu.extra(CombustionGeneratorBlockEntity.DATA_BURN) / 20), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x() - this.leftPos;
        int my = (int) event.y() - this.topPos;
        if (this.layout.redstone().contains(mx, my) && this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_REDSTONE);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @SuppressWarnings("unused")
    private static List<Component> lines(Component... c) {
        return List.of(c);
    }
}
