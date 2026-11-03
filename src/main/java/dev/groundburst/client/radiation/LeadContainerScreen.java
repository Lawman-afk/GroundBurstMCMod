package dev.groundburst.client.radiation;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.radiation.LeadContainerMenu;
import dev.groundburst.port.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.groundburst.port.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Lead Container's screen (DESIGN.md §21.8): its nine places in a lead-lined well under a trefoil, over the
 * inventory ({@code tools/assets/radiation.py} draws it).
 */
public class LeadContainerScreen extends AbstractContainerScreen<LeadContainerMenu> {
    @Override
    protected void renderBg(net.minecraft.client.gui.GuiGraphics gg_, float partial_, int mouseX_, int mouseY_) {}

    private static final ResourceLocation TEXTURE = GroundBurst.id("textures/gui/lead_container.png");
    private static final int TEXT = 0xFFE6E2D2;

    public LeadContainerScreen(LeadContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 173);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics gg_, int mouseX, int mouseY, float partial) { GuiGraphicsExtractor g = new GuiGraphicsExtractor(gg_);
        super.renderBackground(g.unwrap(), mouseX, mouseY, partial);
        int x = (this.width - this.imageWidth) / 2, y = (this.height - this.imageHeight) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(net.minecraft.client.gui.GuiGraphics gg_, int mouseX, int mouseY) { GuiGraphicsExtractor g = new GuiGraphicsExtractor(gg_);
        // As the machine screens: no "Inventory" label (the slots speak for themselves).
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
    }
}
