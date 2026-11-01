package dev.groundburst.client.screen;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.Mirv;
import dev.groundburst.content.assembly.MirvBusMenu;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * A MIRV bus's screen (DESIGN.md §17.14): its mounts on the bus deck on the left, its load and its spread on the right
 * (the pattern, and how far apart: shift moves it 20 at a time), over the inventory. {@code tools/assets/mirv.py} draws it.
 */
public class MirvBusScreen extends AbstractContainerScreen<MirvBusMenu> {
    private static final Identifier TEXTURE = GroundBurst.id("textures/gui/mirv_bus.png");
    private static final int TEXT = 0xFFE6E2D2, DIM = 0xFF9AA0A8, AMBER = 0xFFF0B040, FULL = 0xFFE05A3A;
    /** The right-hand panel: where it starts, its width; the load bar's and the spread's rows. */
    public static final int PANEL_X = 98, PANEL_W = 70, LOAD_Y = 19, BAR_Y = 30, SPREAD_Y = 41, PATTERN_Y = 51, SPACING_Y = 69;
    private static final String P = GroundBurst.MODID + ".mirv_screen.";
    private static final String T_LOAD = GBLang.add(P + "load", "LOAD");
    private static final String T_SPREAD = GBLang.add(P + "spread", "SPREAD");
    private static final String T_BLOCKS = GBLang.add(P + "blocks", "%s blocks");
    private static final String T_ON_AIM = GBLang.add(P + "on_aim", "on the aim");
    private static final String[] T_PATTERN = {GBLang.add(P + "pattern.point", "Point"), GBLang.add(P + "pattern.line", "Line"),
            GBLang.add(P + "pattern.ring", "Ring"), GBLang.add(P + "pattern.grid", "Grid")};

    private Button patternButton;

    public MirvBusScreen(MirvBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 173);
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + PANEL_X, y = this.topPos;
        this.patternButton = addRenderableWidget(Button.builder(Component.translatable(T_PATTERN[this.menu.pattern().ordinal()]),
                b -> press(MirvBusMenu.NEXT_PATTERN)).bounds(x, y + PATTERN_Y, PANEL_W, 14).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> press(Minecraft.getInstance().hasShiftDown() ? MirvBusMenu.MUCH_CLOSER
                : MirvBusMenu.CLOSER)).bounds(x, y + SPACING_Y, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> press(Minecraft.getInstance().hasShiftDown() ? MirvBusMenu.MUCH_WIDER
                : MirvBusMenu.WIDER)).bounds(x + PANEL_W - 14, y + SPACING_Y, 14, 14).build());
    }

    private void press(int id) {
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.gameMode != null) {
            this.menu.clickMenuButton(this.minecraft.player, id);          // at once here; the server's word follows
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = this.leftPos, y = this.topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        // The bus's deck: the mounts it has (a Heavy bus six, a Strategic eight), each a cell from the texture's right.
        int n = this.menu.mounts();
        for (int i = 0; i < n; i++) {
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + MirvBusMenu.mountX(n, i) - 1, y + MirvBusMenu.mountY(n, i) - 1, 176, 0, 18, 18, 256, 256);
        }
        // The load bar: amber as it fills, red once full.
        int load = this.menu.load(), cap = Mirv.capacity(this.menu.size());
        int w = Math.round((PANEL_W - 2) * Math.min(1f, load / (float) cap));
        if (w > 0) {
            g.fill(x + PANEL_X + 1, y + BAR_Y + 1, x + PANEL_X + 1 + w, y + BAR_Y + 6, load >= cap ? FULL : AMBER);
        }
        if (this.patternButton != null) {
            this.patternButton.setMessage(Component.translatable(T_PATTERN[this.menu.pattern().ordinal()]));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // As the Lead Container's: no "Inventory" label.
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        g.text(this.font, Component.translatable(T_LOAD), PANEL_X, LOAD_Y, DIM, false);
        String figures = this.menu.load() + "/" + Mirv.capacity(this.menu.size());
        g.text(this.font, figures, PANEL_X + PANEL_W - this.font.width(figures), LOAD_Y, TEXT, false);
        g.text(this.font, Component.translatable(T_SPREAD), PANEL_X, SPREAD_Y, DIM, false);
        boolean onAim = this.menu.pattern() == LaunchNetwork.Pattern.POINT || this.menu.spacing() == 0;
        Component spacing = onAim ? Component.translatable(T_ON_AIM) : Component.translatable(T_BLOCKS, this.menu.spacing());
        small(g, spacing, Component.literal(onAim ? "0" : Integer.toString(this.menu.spacing())), PANEL_X + PANEL_W / 2, SPACING_Y + 7, TEXT);
    }

    /**
     * Between the - and + buttons, in the station's small type (about two thirds, each font pixel a whole number of
     * screen pixels), centred on cx and the row's middle at {@code midY}; the number alone where even that would not fit.
     */
    private void small(GuiGraphicsExtractor g, Component text, Component shorter, int cx, int midY, int color) {
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        Component shown = this.font.width(text) * scale <= PANEL_W - 30 ? text : shorter;
        float w = this.font.width(shown) * scale;
        g.pose().pushMatrix();
        g.pose().translate(Math.round((cx - w / 2) * gs) / (float) gs, Math.round((midY - 4 * scale) * gs) / (float) gs);
        g.pose().scale(scale, scale);
        g.text(this.font, shown, 0, 0, color, false);
        g.pose().popMatrix();
    }
}
