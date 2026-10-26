package dev.groundzero.client.radiation;

import dev.groundzero.GroundZero;
import dev.groundzero.content.radiation.LeadContainerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Lead Container's screen (DESIGN.md §21.8): its nine places in a lead-lined well under a trefoil, over the
 * inventory ({@code tools/assets/radiation.py} draws it).
 */
public class LeadContainerScreen extends AbstractContainerScreen<LeadContainerMenu> {
    private static final Identifier TEXTURE = GroundZero.id("textures/gui/lead_container.png");
    private static final int TEXT = 0xFFE6E2D2;

    public LeadContainerScreen(LeadContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = (this.width - this.imageWidth) / 2, y = (this.height - this.imageHeight) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // As the machine screens: no "Inventory" label (the slots speak for themselves).
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
    }
}
