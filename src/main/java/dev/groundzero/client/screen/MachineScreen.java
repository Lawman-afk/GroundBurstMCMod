package dev.groundzero.client.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import dev.groundzero.content.machine.MachineLayout;
import dev.groundzero.client.render.FluidVisuals;
import dev.groundzero.content.machine.MachineTank;
import dev.groundzero.content.machine.MachineText;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.ProcessingBlockEntity;
import dev.groundzero.content.power.CombustionGeneratorBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
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
    private static final float CRT_SCALE = 0.75f;
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final String KEY_ENERGY = MachineText.ENERGY;
    private static final String KEY_PROGRESS = MachineText.PROGRESS;
    private static final String KEY_BURN = MachineText.BURN;
    private static final String KEY_REDSTONE = MachineText.REDSTONE;

    private final MachineLayout layout;
    private final SideConfigTab sideTab;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.layout().width(), menu.layout().height());
        this.layout = menu.layout();
        this.sideTab = new SideConfigTab(menu);
        this.titleLabelX = this.layout.titleX();
        this.titleLabelY = this.layout.titleY();
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

        // Tanks: each fluid's own still texture and tint, tiled bottom-up, graduation ticks drawn over it.
        List<MachineLayout.Gauge> gauges = this.layout.tanks();
        for (int i = 0; i < gauges.size() && i < this.menu.machine().tanks().size(); i++) {
            MachineLayout.Rect tank = gauges.get(i).rect();
            int amount = this.menu.tankAmount(i);
            if (amount <= 0) {
                continue;
            }
            Fluid fluid = this.menu.tankFluid(i);
            int h = Math.max(1, (int) ((long) amount * tank.h() / this.menu.machine().tank(i).capacity()));
            TextureAtlasSprite sprite = FluidVisuals.still(fluid);
            int color = FluidVisuals.tint(fluid);
            int bottom = y + tank.y() + tank.h();
            for (int drawn = 0; drawn < h; drawn += 16) {
                int piece = Math.min(16, h - drawn);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + tank.x(), bottom - drawn - piece, tank.w(), piece, color);
            }
            for (int yy = tank.h() - 1; yy > 0; yy -= 5) {
                g.fill(x + tank.x() + tank.w() - 3, y + tank.y() + yy, x + tank.x() + tank.w(), y + tank.y() + yy + 1, 0xA0E6E2D2);
            }
        }
        // Dump buttons light up under the mouse while Shift is held (they only work with Shift).
        for (MachineLayout.Gauge gauge : gauges) {
            MachineLayout.Rect d = gauge.dump();
            if (d != null && d.contains(mouseX - x, mouseY - y)) {
                g.fill(x + d.x() + 1, y + d.y() + 1, x + d.x() + d.w() - 1, y + d.y() + d.h() - 1, Minecraft.getInstance().hasShiftDown() ? 0x50FFFFFF : 0x20FFFFFF);
            }
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
        // Long names shrink to fit between the left edge and the status lamp rather than running under it.
        int room = this.layout.status().x() - 6 - this.titleLabelX;
        int width = this.font.width(this.title);
        if (width > room) {
            float scale = room / (float) width;
            g.pose().pushMatrix();
            g.pose().translate(this.titleLabelX, this.titleLabelY + (1 - scale) * this.font.lineHeight / 2f);
            g.pose().scale(scale, scale);
            g.text(this.font, this.title, 0, 0, TEXT, false);
            g.pose().popMatrix();
        } else {
            g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        }
        // No "Inventory" label: it collides with the machine widgets and the slots speak for themselves.
        MachineLayout.Rect ro = this.layout.readout();
        if (ro != null) {
            // The CRT uses a 3/4-scale font so long status lines fit inside the glass.
            g.pose().pushMatrix();
            g.pose().translate(ro.x() + 3, ro.y() + 3);
            g.pose().scale(CRT_SCALE, CRT_SCALE);
            int line = (int) Math.ceil(10 / CRT_SCALE);
            List<Component> lines = this.menu.machine().readout(this.menu);
            for (int i = 0; i < lines.size(); i++) {
                g.text(this.font, lines.get(i), 0, line * i, CRT_TEXT, false);
            }
            g.pose().popMatrix();
        }
    }

    /** Tank whose gauge is under the (window-relative) point, or -1. */
    private int tankAt(int mx, int my) {
        List<MachineLayout.Gauge> gauges = this.layout.tanks();
        for (int i = 0; i < gauges.size() && i < this.menu.machine().tanks().size(); i++) {
            if (gauges.get(i).rect().contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    /** Tank whose dump button is under the point, or -1. */
    private int dumpAt(int mx, int my) {
        List<MachineLayout.Gauge> gauges = this.layout.tanks();
        for (int i = 0; i < gauges.size() && i < this.menu.machine().tanks().size(); i++) {
            MachineLayout.Rect d = gauges.get(i).dump();
            if (d != null && d.contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    /** Screen areas outside the main window (the side-config tab), for JEI to keep clear of. */
    public List<Rect2i> extraAreas() {
        return List.of(this.sideTab.area(this.leftPos, this.topPos, this.imageWidth));
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !this.sideTab.area(xo, yo, this.imageWidth).contains((int) mx, (int) my);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        this.sideTab.render(g, this.font, this.leftPos, this.topPos, this.imageWidth, mouseX, mouseY);
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        if (this.layout.energy().contains(mx, my)) {
            g.setTooltipForNextFrame(Component.translatable(KEY_ENERGY, NUM.format(this.menu.energy()), NUM.format(this.menu.capacity())), mouseX, mouseY);
        } else if (tankAt(mx, my) >= 0) {
            int i = tankAt(mx, my);
            MachineTank tank = this.menu.machine().tank(i);
            int cap = tank.capacity();
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(this.menu.tankAmount(i) <= 0 ? Component.translatable(MachineText.TANK_EMPTY, NUM.format(cap))
                    : Component.translatable(MachineText.TANK, this.menu.tankFluid(i).getFluidType().getDescription(),
                    NUM.format(this.menu.tankAmount(i)), NUM.format(cap)));
            lines.add(Component.translatable(tank.isInput() ? MachineText.TANK_INPUT : MachineText.TANK_OUTPUT).withColor(0xFF9AA0A8));
            g.setTooltipForNextFrame(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
        } else if (dumpAt(mx, my) >= 0) {
            g.setTooltipForNextFrame(this.font, List.of(Component.translatable(MachineText.DUMP),
                    Component.translatable(MachineText.DUMP_HINT).withColor(0xFF9AA0A8)), java.util.Optional.empty(), mouseX, mouseY);
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
        if (this.sideTab.click(event.x(), event.y(), event.button(), this.leftPos, this.topPos, this.imageWidth)) {
            return true;
        }
        int mx = (int) event.x() - this.leftPos;
        int my = (int) event.y() - this.topPos;
        int dump = dumpAt(mx, my);
        if (dump >= 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            if (event.hasShiftDown()) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_DUMP + dump);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUCKET_EMPTY, 1.0f));
            }
            return true;
        }
        if (this.layout.redstone().contains(mx, my) && this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_REDSTONE);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

}
