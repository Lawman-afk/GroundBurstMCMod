package dev.groundburst.client.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import dev.groundburst.client.recipe.ClientRecipes;
import dev.groundburst.client.render.FluidVisuals;
import dev.groundburst.content.manual.ManualStrings;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The Field Manual's page kinds and how an entry's page specs are laid out onto pages: each spec draws its picture
 * or recipe at the top of a page and its text under it; text that does not fit runs on to plain text pages.
 */
final class ManualPages {
    /** Content area of one page, in GUI pixels. */
    static final int W = 126, H = 150;
    static final int LINE = 10, GAP = 5, TITLE_H = 16, BLOCK_GAP = 5;
    static final int FADED = 0xFF6B665A, STAMP = 0xFFA8322A, PANEL = 0xFF2A2D31, RULE = 0x40000000;

    private ManualPages() {}

    /** Everything a page needs while drawing or handling the mouse. */
    record Ctx(FieldManualScreen screen, GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, long time) {
        boolean over(int x, int y, int w, int h) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    /** One page of the book. */
    abstract static class Page {
        /** The entry title, shown on an entry's first page (one or two lines). */
        @Nullable String title;
        int titleH;

        void setTitle(Font font, String title) {
            this.title = title;
            this.titleH = titleHeight(font, title);
        }

        int top() {
            return this.title == null ? 0 : this.titleH;
        }

        final void render(Ctx c, int x, int y) {
            if (this.title != null) {
                List<FormattedCharSequence> lines = c.font().split(bold(this.title), W);
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    centered(c.g(), c.font(), lines.get(i), x + W / 2, y + 2 + i * LINE, ManualText.INK);
                }
                c.g().fill(x + 10, y + this.titleH - 4, x + W - 10, y + this.titleH - 3, RULE);
            }
            draw(c, x, y + top());
        }

        abstract void draw(Ctx c, int x, int y);

        /** A left click inside this page; x, y are the page's content origin. */
        boolean click(Ctx c, int x, int y, double mx, double my) {
            return false;
        }

        boolean drag(double dx, double dy, int x, int y, double mx, double my) {
            return false;
        }
    }

    // --- shared drawing -------------------------------------------------------------------------------------------------
    static Component bold(String text) {
        return Component.literal(text).withStyle(s -> s.withBold(true));
    }

    /** Height of an entry title: one line, or two when it is long. */
    static int titleHeight(Font font, String title) {
        return font.split(bold(title), W).size() > 1 ? TITLE_H + LINE : TITLE_H;
    }

    /** Centred text without the drop shadow vanilla adds (the page is paper, not a dark panel). */
    static void centered(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, false);
    }

    static void centered(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int color) {
        centered(g, font, text.getVisualOrderText(), cx, y, color);
    }

    static void centered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        centered(g, font, Component.literal(text), cx, y, color);
    }

    /** A bold heading wrapped to the page: one line, or two for a long name (a third would be cut). */
    static List<FormattedCharSequence> headingLines(Font font, Component name) {
        String text = name.getString();
        var style = net.minecraft.network.chat.Style.EMPTY.withBold(true);
        List<net.minecraft.network.chat.FormattedText> wrapped = font.getSplitter().splitLines(text, W, style);
        if (wrapped.size() <= 2) {
            return font.split(bold(text), W);
        }
        String first = wrapped.get(0).getString();
        String rest = text.substring(Math.min(text.length(), first.length())).strip();
        return List.of(bold(first.strip()).getVisualOrderText(), heading(font, Component.literal(rest), W).getVisualOrderText());
    }

    /** Height a wrapped heading takes, with the gap under it. */
    static int headingH(Font font, Component name) {
        return headingLines(font, name).size() * LINE + 4;
    }

    /** Draws a wrapped heading centred on the page; returns its height. */
    static int drawHeading(Ctx c, Component name, int x, int y) {
        List<FormattedCharSequence> lines = headingLines(c.font(), name);
        for (int i = 0; i < lines.size(); i++) {
            centered(c.g(), c.font(), lines.get(i), x + W / 2, y + i * LINE, ManualText.INK);
        }
        return lines.size() * LINE + 4;
    }

    /** A bold heading that fits {@code width}, cut with an ellipsis if it must be. */
    static Component heading(Font font, Component name, int width) {
        String s = name.getString();
        if (font.width(bold(s)) <= width) {
            return bold(s);
        }
        while (!s.isEmpty() && font.width(bold(s.stripTrailing() + "...")) > width) {
            s = s.substring(0, s.length() - 1);
        }
        return bold(s.stripTrailing() + "...");
    }

    static int linesHeight(List<@Nullable FormattedCharSequence> lines) {
        int h = 0;
        for (FormattedCharSequence l : lines) {
            h += l == null ? GAP : LINE;
        }
        return h;
    }

    /** Draws text lines; returns the link under the mouse, if any. */
    static @Nullable String drawLines(Ctx c, List<@Nullable FormattedCharSequence> lines, int x, int y) {
        String hovered = null;
        int yy = y;
        for (FormattedCharSequence line : lines) {
            if (line == null) {
                yy += GAP;
                continue;
            }
            c.g().text(c.font(), line, x, yy, ManualText.INK, false);
            if (c.mouseY() >= yy && c.mouseY() < yy + LINE - 1) {
                String link = ManualText.linkAt(c.font(), line, c.mouseX() - x);
                if (link != null) {
                    hovered = link;
                }
            }
            yy += LINE;
        }
        if (hovered != null) {
            Manual.Entry e = Manual.entry(hovered);
            c.g().requestCursor(CursorTypes.POINTING_HAND);
            if (e != null) {
                c.g().setTooltipForNextFrame(c.font(), Component.literal(e.title()), c.mouseX(), c.mouseY());
            }
        }
        return hovered;
    }

    static @Nullable String linkAt(Font font, List<@Nullable FormattedCharSequence> lines, int x, int y, double mx, double my) {
        int yy = y;
        for (FormattedCharSequence line : lines) {
            if (line == null) {
                yy += GAP;
                continue;
            }
            if (my >= yy && my < yy + LINE - 1) {
                return ManualText.linkAt(font, line, (int) (mx - x));
            }
            yy += LINE;
        }
        return null;
    }

    /** An item in a slot frame: tooltip on hover (with the manual hint), opens its entry when clicked. */
    static void slot(Ctx c, ItemStack stack, int x, int y, boolean frame) {
        if (frame) {
            c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x, y, 120, 208, 18, 18, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
        }
        if (stack.isEmpty()) {
            return;
        }
        c.g().item(stack, x + 1, y + 1);
        c.g().itemDecorations(c.font(), stack, x + 1, y + 1);
        if (c.over(x + 1, y + 1, 16, 16)) {
            c.g().fill(x + 1, y + 1, x + 17, y + 17, 0x40FFFFFF);
            c.screen().hoverItem(stack);
        }
    }

    static boolean slotClick(Ctx c, ItemStack stack, int x, int y, double mx, double my) {
        if (stack.isEmpty() || mx < x + 1 || mx >= x + 17 || my < y + 1 || my >= y + 17) {
            return false;
        }
        Manual.Entry e = Manual.entryFor(stack.getItem());
        if (e != null) {
            c.screen().openEntry(e.id());
            return true;
        }
        return false;
    }

    /** A fluid in a slot frame: its still texture, tinted, with name and amount on hover. */
    static void fluidSlot(Ctx c, Fluid fluid, int amount, int x, int y) {
        c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x, y, 120, 208, 18, 18, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
        TextureAtlasSprite sprite = FluidVisuals.still(fluid);
        c.g().blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + 1, y + 1, 16, 16, FluidVisuals.tint(fluid));
        if (c.over(x + 1, y + 1, 16, 16)) {
            c.g().fill(x + 1, y + 1, x + 17, y + 17, 0x40FFFFFF);
            c.g().setTooltipForNextFrame(c.font(), List.of(fluid.getFluidType().getDescription(),
                    Component.literal(String.format(java.util.Locale.ROOT, "%,d mB", amount)).withColor(0xFF9AA0A8)), java.util.Optional.empty(),
                    c.mouseX(), c.mouseY());
        }
    }

    /** {@code text} cut to {@code width} pixels, ending in an ellipsis when it had to be cut. */
    static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")).stripTrailing() + "...";
    }

    /** Cycles through an ingredient's alternatives once a second. */
    static ItemStack cycle(List<ItemStack> stacks, long time) {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get((int) ((time / 20) % stacks.size()));
    }

    // --- text ------------------------------------------------------------------------------------------------------------
    static final class TextPage extends Page {
        final List<@Nullable FormattedCharSequence> lines;

        TextPage(List<@Nullable FormattedCharSequence> lines) {
            this.lines = lines;
        }

        @Override
        void draw(Ctx c, int x, int y) {
            drawLines(c, this.lines, x, y);
        }

        @Override
        boolean click(Ctx c, int x, int y, double mx, double my) {
            String link = linkAt(c.font(), this.lines, x, y + top(), mx, my);
            if (link != null) {
                c.screen().openLink(link);
                return true;
            }
            return false;
        }
    }

    /** A picture or recipe of fixed height at the top, then text. */
    abstract static class GraphicPage extends Page {
        final int graphic;
        List<@Nullable FormattedCharSequence> lines = List.of();

        GraphicPage(int graphic) {
            this.graphic = graphic;
        }

        @Override
        void draw(Ctx c, int x, int y) {
            drawGraphic(c, x, y);
            drawLines(c, this.lines, x, y + this.graphic);
        }

        abstract void drawGraphic(Ctx c, int x, int y);

        boolean clickGraphic(Ctx c, int x, int y, double mx, double my) {
            return false;
        }

        @Override
        boolean click(Ctx c, int x, int y, double mx, double my) {
            int cy = y + top();
            if (clickGraphic(c, x, cy, mx, my)) {
                return true;
            }
            String link = linkAt(c.font(), this.lines, x, cy + this.graphic, mx, my);
            if (link != null) {
                c.screen().openLink(link);
                return true;
            }
            return false;
        }
    }

    // --- spotlight -------------------------------------------------------------------------------------------------------
    static final class SpotlightPage extends GraphicPage {
        final ItemStack stack;
        final @Nullable Component name;

        /** {@code hideName}: the entry's title already says what it is (a spotlight opening its own entry). */
        SpotlightPage(Font font, Item item, @Nullable String title, boolean hideName) {
            super(hideName ? 40 : 40 + headingH(font, title != null ? Component.literal(title) : new ItemStack(item).getHoverName()) - 2);
            this.stack = new ItemStack(item);
            Component name = title != null ? Component.literal(title) : this.stack.getHoverName();
            this.name = hideName ? null : name;
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            int cx = x + W / 2;
            c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, cx - 18, y, 140, 208, 36, 36, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
            c.g().pose().pushMatrix();
            c.g().pose().translate(cx - 16, y + 2);
            c.g().pose().scale(2, 2);
            c.g().item(this.stack, 0, 0);
            c.g().pose().popMatrix();
            if (c.over(cx - 16, y + 2, 32, 32)) {
                c.screen().hoverItem(this.stack);
            }
            if (this.name != null) {
                drawHeading(c, this.name, x, y + 39);
            }
        }
    }

    // --- crafting --------------------------------------------------------------------------------------------------------
    static final class CraftingPage extends GraphicPage {
        final RecipeDisplay display;
        final Component name;
        final int headH;

        CraftingPage(Font font, RecipeDisplay display, Component name) {
            super(headingH(font, name) + (display instanceof FurnaceRecipeDisplay ? 40 : 60));
            this.display = display;
            this.name = name;
            this.headH = headingH(font, name);
        }

        private static ContextMap context() {
            return SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        }

        private List<SlotDisplay> grid() {
            if (this.display instanceof ShapedCraftingRecipeDisplay shaped) {
                List<SlotDisplay> out = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    int col = i % 3, row = i / 3;
                    out.add(col < shaped.width() && row < shaped.height() ? shaped.ingredients().get(row * shaped.width() + col) : SlotDisplay.Empty.INSTANCE);
                }
                return out;
            }
            if (this.display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                List<SlotDisplay> out = new ArrayList<>(shapeless.ingredients());
                while (out.size() < 9) {
                    out.add(SlotDisplay.Empty.INSTANCE);
                }
                return out;
            }
            return List.of();
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            drawHeading(c, this.name, x, y);
            ContextMap ctx = context();
            int gy = y + this.headH;
            if (this.display instanceof FurnaceRecipeDisplay furnace) {
                slot(c, cycle(furnace.ingredient().resolveForStacks(ctx), c.time()), x + 24, gy, true);
                c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x + 46, gy + 2, 178, 208, 22, 15, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
                result(c, furnace.result().resolveForFirstStack(ctx), x + 74, gy - 4);
                centered(c.g(), c.font(), Component.literal(String.format(java.util.Locale.ROOT, "%d s", furnace.duration() / 20)), x + 57, gy + 22, FADED);
                return;
            }
            List<SlotDisplay> grid = grid();
            for (int i = 0; i < 9; i++) {
                slot(c, cycle(grid.get(i).resolveForStacks(ctx), c.time() + i * 7), x + 6 + (i % 3) * 18, gy + (i / 3) * 18, true);
            }
            c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x + 64, gy + 19, 178, 208, 22, 15, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
            SlotDisplay result = this.display.result();
            result(c, result.resolveForFirstStack(ctx), x + 92, gy + 14);
        }

        private static void result(Ctx c, ItemStack stack, int x, int y) {
            c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x, y, 140, 244, 26, 26, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
            slot(c, stack, x + 4, y + 4, false);
        }

        @Override
        boolean clickGraphic(Ctx c, int x, int y, double mx, double my) {
            ContextMap ctx = context();
            int gy = y + this.headH;
            if (this.display instanceof FurnaceRecipeDisplay furnace) {
                return slotClick(c, cycle(furnace.ingredient().resolveForStacks(ctx), c.time()), x + 24, gy, mx, my);
            }
            List<SlotDisplay> grid = grid();
            for (int i = 0; i < 9; i++) {
                if (slotClick(c, cycle(grid.get(i).resolveForStacks(ctx), c.time() + i * 7), x + 6 + (i % 3) * 18, gy + (i / 3) * 18, mx, my)) {
                    return true;
                }
            }
            return false;
        }
    }

    // --- machine recipes -------------------------------------------------------------------------------------------------
    /** One machine recipe, drawn as inputs, an arrow (with the die or membrane over it) and outputs. */
    record MachineRow(ProcessingRecipe recipe) {
        static final int IN_COLS = 3, OUT_COLS = 2;

        int inputs() {
            return this.recipe.inputs().size() + this.recipe.fluids().size();
        }

        int outputs() {
            return this.recipe.outputs().size() + this.recipe.fluidOutputs().size();
        }

        int height() {
            int rows = Math.max(1, Math.max((inputs() + IN_COLS - 1) / IN_COLS, (outputs() + OUT_COLS - 1) / OUT_COLS));
            if (this.recipe.catalyst().isPresent()) {
                rows = Math.max(rows, 2);
            }
            return rows * 18 + 10;
        }

        void draw(Ctx c, int x, int y) {
            int i = 0;
            for (var in : this.recipe.inputs()) {
                List<ItemStack> stacks = in.ingredient().items().map(h -> new ItemStack(h, in.count())).toList();
                slot(c, cycle(stacks, c.time() + i * 7), x + (i % IN_COLS) * 18, y + (i / IN_COLS) * 18, true);
                i++;
            }
            for (var fluid : this.recipe.fluids()) {
                List<Fluid> fluids = fluid.ingredient().fluids().stream().map(h -> h.value()).toList();
                fluidSlot(c, fluids.isEmpty() ? Fluids.WATER : fluids.get((int) ((c.time() / 20) % fluids.size())), fluid.amount(),
                        x + (i % IN_COLS) * 18, y + (i / IN_COLS) * 18);
                i++;
            }
            int ay = y + 2;
            if (this.recipe.catalyst().isPresent()) {
                List<ItemStack> cat = this.recipe.catalyst().get().items().map(ItemStack::new).toList();
                slot(c, cycle(cat, c.time()), x + 58, y, true);
                ay = y + 20;
            }
            c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x + 56, ay, 178, 208, 22, 15, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
            int o = 0;
            for (ProcessingRecipe.Output out : this.recipe.outputs()) {
                int ox = x + 84 + (o % OUT_COLS) * 18, oy = y + (o / OUT_COLS) * 18;
                slot(c, out.item().create(), ox, oy, true);
                if (out.chance() < 1f) {
                    String pct = Math.round(out.chance() * 100) + "%";
                    c.g().pose().pushMatrix();
                    c.g().pose().translate(ox + 1, oy + 1);
                    c.g().pose().scale(0.5f, 0.5f);
                    c.g().text(c.font(), pct, 0, 0, 0xFFFFE070, true);
                    c.g().pose().popMatrix();
                }
                o++;
            }
            for (var fluid : this.recipe.fluidOutputs()) {
                fluidSlot(c, fluid.fluid().value(), fluid.amount(), x + 84 + (o % OUT_COLS) * 18, y + (o / OUT_COLS) * 18);
                o++;
            }
            String info = String.format(java.util.Locale.ROOT, "%.1f s  ·  %,d FE/t", this.recipe.time() / 20f, this.recipe.energyPerTick());
            c.g().text(c.font(), info, x, y + height() - 9, FADED, false);
        }

        boolean click(Ctx c, int x, int y, double mx, double my) {
            int i = 0;
            for (var in : this.recipe.inputs()) {
                List<ItemStack> stacks = in.ingredient().items().map(h -> new ItemStack(h, in.count())).toList();
                if (slotClick(c, cycle(stacks, c.time() + i * 7), x + (i % IN_COLS) * 18, y + (i / IN_COLS) * 18, mx, my)) {
                    return true;
                }
                i++;
            }
            int o = 0;
            for (ProcessingRecipe.Output out : this.recipe.outputs()) {
                if (slotClick(c, out.item().create(), x + 84 + (o % OUT_COLS) * 18, y + (o / OUT_COLS) * 18, mx, my)) {
                    return true;
                }
                o++;
            }
            return false;
        }
    }

    static final class MachinePage extends GraphicPage {
        final Component heading;
        final List<MachineRow> rows;
        final int headH;

        MachinePage(Font font, Component heading, List<MachineRow> rows) {
            super(headingH(font, heading) + rows.stream().mapToInt(r -> r.height() + 4).sum());
            this.heading = heading;
            this.rows = rows;
            this.headH = headingH(font, heading);
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            drawHeading(c, this.heading, x, y);
            int yy = y + this.headH;
            for (MachineRow row : this.rows) {
                row.draw(c, x + 2, yy);
                yy += row.height() + 4;
            }
        }

        @Override
        boolean clickGraphic(Ctx c, int x, int y, double mx, double my) {
            int yy = y + this.headH;
            for (MachineRow row : this.rows) {
                if (row.click(c, x + 2, yy, mx, my)) {
                    return true;
                }
                yy += row.height() + 4;
            }
            return false;
        }
    }

    // --- structures ------------------------------------------------------------------------------------------------------
    static final class StructurePage extends GraphicPage {
        static final int VIEW_H = 96;
        final MultiblockDefinition def;
        final List<StructureView.Block> blocks;
        final int[] size;
        float yaw = 225, pitch = 30;
        int layer = -1;
        boolean dragging;
        long lastDrag, lastFrame = net.minecraft.util.Util.getMillis();

        StructurePage(MultiblockDefinition def) {
            super(VIEW_H + 18);
            this.def = def;
            this.blocks = StructureView.blocks(def);
            this.size = def.size();
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            c.g().fill(x, y, x + W, y + VIEW_H, PANEL);
            c.g().outline(x, y, W, VIEW_H, 0xFF14161A);
            long now = net.minecraft.util.Util.getMillis();
            if (!this.dragging && now - this.lastDrag > 3000) {
                this.yaw += (now - this.lastFrame) * 0.012f;       // a slow turn while it is left alone
            }
            this.lastFrame = now;
            float scale = StructureView.fitScale(this.size, W - 4, VIEW_H - 4);
            c.g().submitPictureInPictureRenderState(new StructureView.State(this.blocks, this.size, this.yaw, this.pitch, this.layer,
                    x + 1, y + 1, x + W - 1, y + VIEW_H - 1, scale, c.g().peekScissorStack()));
            if (c.over(x, y, W, VIEW_H)) {
                c.g().requestCursor(CursorTypes.RESIZE_ALL);
            }
            // Layer buttons: down, a label, up; the label shows all layers or the one shown.
            int by = y + VIEW_H + 3;
            button(c, x + 2, by, 296, 208);
            button(c, x + W - 13, by, 308, 208);
            Component label = this.layer < 0 ? Component.translatable(ManualStrings.ALL_LAYERS)
                    : Component.translatable(ManualStrings.LAYER, this.layer + 1, this.size[1]);
            centered(c.g(), c.font(), label, x + W / 2, by + 2, FADED);
            if (c.over(x + 2, by, 11, 11)) {
                c.g().setTooltipForNextFrame(c.font(), Component.translatable(ManualStrings.LAYER_DOWN), c.mouseX(), c.mouseY());
            } else if (c.over(x + W - 13, by, 11, 11)) {
                c.g().setTooltipForNextFrame(c.font(), Component.translatable(ManualStrings.LAYER_UP), c.mouseX(), c.mouseY());
            }
        }

        private static void button(Ctx c, int x, int y, int u, int v) {
            boolean hover = c.over(x, y, 11, 11);
            c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x, y, u, v + (hover ? 12 : 0), 11, 11, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
        }

        @Override
        boolean clickGraphic(Ctx c, int x, int y, double mx, double my) {
            int by = y + VIEW_H + 3;
            int layers = this.size[1];
            if (mx >= x + 2 && mx < x + 13 && my >= by && my < by + 11) {
                // Down from "all" goes to the top layer, then down to the bottom, then back to all.
                this.layer = this.layer < 0 ? layers - 1 : this.layer == 0 ? -1 : this.layer - 1;
                c.screen().click();
                return true;
            }
            if (mx >= x + W - 13 && mx < x + W - 2 && my >= by && my < by + 11) {
                this.layer = this.layer < 0 ? 0 : this.layer == layers - 1 ? -1 : this.layer + 1;
                c.screen().click();
                return true;
            }
            if (mx >= x && mx < x + W && my >= y && my < y + VIEW_H) {
                this.dragging = true;
                return true;
            }
            return false;
        }

        @Override
        boolean drag(double dx, double dy, int x, int y, double mx, double my) {
            if (!this.dragging) {
                return false;
            }
            this.yaw += (float) dx * 1.5f;
            this.pitch = (float) Math.max(5, Math.min(85, this.pitch + dy));
            this.lastDrag = net.minecraft.util.Util.getMillis();
            return true;
        }

        void release() {
            this.dragging = false;
        }
    }

    /** The blocks a structure needs, with counts. */
    static final class MaterialsPage extends GraphicPage {
        final List<Map.Entry<ItemStack, Integer>> rows;

        MaterialsPage(List<Map.Entry<ItemStack, Integer>> rows) {
            super(14 + rows.size() * 19);
            this.rows = rows;
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            centered(c.g(), c.font(), Component.translatable(ManualStrings.BLOCKS_NEEDED).withStyle(s -> s.withBold(true)), x + W / 2, y, ManualText.INK);
            int yy = y + 14;
            for (Map.Entry<ItemStack, Integer> row : this.rows) {
                slot(c, row.getKey(), x, yy, true);
                String count = "\u00d7" + row.getValue();
                int cw = c.font().width(count);
                String name = row.getKey().getHoverName().getString();
                int room = W - 24 - cw - 4;
                // A name too long for one line takes both lines of its row (only a third line's worth is cut short).
                List<String> lines = c.font().getSplitter().splitLines(name, room, net.minecraft.network.chat.Style.EMPTY).stream()
                        .map(net.minecraft.network.chat.FormattedText::getString).toList();
                if (lines.size() <= 1) {
                    c.g().text(c.font(), name, x + 22, yy + 5, ManualText.INK, false);
                } else {
                    c.g().text(c.font(), lines.get(0).strip(), x + 22, yy + 1, ManualText.INK, false);
                    String rest = String.join(" ", lines.subList(1, lines.size())).strip();
                    c.g().text(c.font(), ellipsize(c.font(), rest, room), x + 22, yy + 10, ManualText.INK, false);
                }
                c.g().text(c.font(), count, x + W - cw, yy + 5, FADED, false);
                yy += 19;
            }
        }

        @Override
        boolean clickGraphic(Ctx c, int x, int y, double mx, double my) {
            int yy = y + 14;
            for (Map.Entry<ItemStack, Integer> row : this.rows) {
                if (slotClick(c, row.getKey(), x, yy, mx, my)) {
                    return true;
                }
                yy += 19;
            }
            return false;
        }
    }

    /**
     * A page built by the flowing layout: pictures, recipes and runs of text stacked down the page in order, each
     * where the one before it ended.
     */
    static final class FlowPage extends Page {
        record Part(@Nullable GraphicPage graphic, List<@Nullable FormattedCharSequence> lines, int y) {}

        final List<Part> parts = new ArrayList<>();
        int used;

        int room() {
            return H - top() - this.used;
        }

        void add(@Nullable GraphicPage graphic, List<@Nullable FormattedCharSequence> lines, int height) {
            this.parts.add(new Part(graphic, lines, this.used));
            this.used += height + BLOCK_GAP;
        }

        @Override
        void draw(Ctx c, int x, int y) {
            for (Part p : this.parts) {
                if (p.graphic() != null) {
                    p.graphic().drawGraphic(c, x, y + p.y());
                } else {
                    drawLines(c, p.lines(), x, y + p.y());
                }
            }
        }

        @Override
        boolean click(Ctx c, int x, int y, double mx, double my) {
            int cy = y + top();
            for (Part p : this.parts) {
                if (p.graphic() != null) {
                    if (p.graphic().clickGraphic(c, x, cy + p.y(), mx, my)) {
                        return true;
                    }
                } else {
                    String link = linkAt(c.font(), p.lines(), x, cy + p.y(), mx, my);
                    if (link != null) {
                        c.screen().openLink(link);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        boolean drag(double dx, double dy, int x, int y, double mx, double my) {
            for (Part p : this.parts) {
                if (p.graphic() != null && p.graphic().drag(dx, dy, x, y, mx, my)) {
                    return true;
                }
            }
            return false;
        }

        void release() {
            for (Part p : this.parts) {
                if (p.graphic() instanceof StructurePage sp) {
                    sp.release();
                }
            }
        }
    }

    // --- image, table, links ---------------------------------------------------------------------------------------------
    static final class ImagePage extends GraphicPage {
        final Identifier image;
        final int iw, ih, dw, dh;

        /** Photos are at most this tall, so their caption fits under them. */
        static final int MAX_H = 96;

        ImagePage(Identifier image, int iw, int ih) {
            super(Math.round(ih * scale(iw, ih)) + 8);
            this.image = image;
            this.iw = iw;
            this.ih = ih;
            float s = scale(iw, ih);
            this.dw = Math.round(iw * s);
            this.dh = Math.round(ih * s);
        }

        private static float scale(int iw, int ih) {
            return Math.min(1f, Math.min((W - 4) / (float) iw, MAX_H / (float) ih));
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            int ix = x + (W - this.dw) / 2;
            c.g().fill(ix - 2, y, ix + this.dw + 2, y + this.dh + 4, 0xFFEFE6CF);
            c.g().outline(ix - 2, y, this.dw + 4, this.dh + 4, 0xFFB9AE90);
            c.g().blit(RenderPipelines.GUI_TEXTURED, this.image, ix, y + 2, 0, 0, this.dw, this.dh, this.iw, this.ih, this.iw, this.ih);
            // A paperclip over the photo's top edge.
            c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, ix + this.dw - 14, y - 4, 200, 232, 8, 16, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
        }
    }

    static final class TablePage extends GraphicPage {
        final @Nullable String heading;
        final List<List<String>> rows;

        TablePage(@Nullable String heading, List<List<String>> rows) {
            super((heading == null ? 0 : 14) + rows.size() * 11 + 4);
            this.heading = heading;
            this.rows = rows;
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            int yy = y;
            if (this.heading != null) {
                centered(c.g(), c.font(), Component.literal(this.heading).withStyle(s -> s.withBold(true)), x + W / 2, yy, ManualText.INK);
                yy += 14;
            }
            for (int i = 0; i < this.rows.size(); i++) {
                List<String> row = this.rows.get(i);
                if (i % 2 == 0) {
                    c.g().fill(x, yy - 1, x + W, yy + 10, 0x14000000);
                }
                String label = row.isEmpty() ? "" : row.get(0);
                String value = row.size() > 1 ? row.get(1) : "";
                int vw = c.font().width(value);
                c.g().text(c.font(), c.font().plainSubstrByWidth(label, W - vw - 6), x + 2, yy + 1, FADED, false);
                c.g().text(c.font(), value, x + W - vw - 2, yy + 1, ManualText.INK, false);
                yy += 11;
            }
        }
    }

    static final class LinksPage extends GraphicPage {
        final String heading;
        final List<Manual.Entry> links;

        LinksPage(@Nullable String heading, List<Manual.Entry> links) {
            super(14 + links.size() * 18 + 4);
            this.heading = heading == null ? Component.translatable(ManualStrings.SEE_ALSO).getString() : heading;
            this.links = links;
        }

        @Override
        void drawGraphic(Ctx c, int x, int y) {
            centered(c.g(), c.font(), Component.literal(this.heading).withStyle(s -> s.withBold(true)), x + W / 2, y, ManualText.INK);
            EntryList.drawRows(c, this.links, x, y + 14);
        }

        @Override
        boolean clickGraphic(Ctx c, int x, int y, double mx, double my) {
            return EntryList.clickRows(c, this.links, x, y + 14, mx, my);
        }
    }

    // --- entry lists (chapters, search results) --------------------------------------------------------------------------
    static final class EntryList extends Page {
        static final int ROW = 18;
        final @Nullable String heading;
        final List<Manual.Entry> rows;

        EntryList(@Nullable String heading, List<Manual.Entry> rows) {
            this.heading = heading;
            this.rows = rows;
        }

        int headingH() {
            return this.heading == null ? 0 : 14;
        }

        @Override
        void draw(Ctx c, int x, int y) {
            if (this.heading != null) {
                centered(c.g(), c.font(), Component.literal(this.heading).withStyle(s -> s.withBold(true)), x + W / 2, y, ManualText.INK);
            }
            drawRows(c, this.rows, x, y + headingH());
        }

        static void drawRows(Ctx c, List<Manual.Entry> rows, int x, int y) {
            int yy = y;
            for (Manual.Entry e : rows) {
                boolean hover = c.over(x, yy, W, ROW - 1);
                if (hover) {
                    c.g().fill(x, yy, x + W, yy + ROW - 1, 0x22000000);
                    c.g().requestCursor(CursorTypes.POINTING_HAND);
                }
                c.g().item(new ItemStack(e.icon()), x + 1, yy);
                c.g().text(c.font(), ellipsize(c.font(), e.title(), W - 31), x + 21, yy + 5, ManualText.INK, false);
                if (!ManualProgress.isRead(e.id())) {
                    c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x + W - 7, yy + 6, 178, 226, 5, 5, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
                    if (c.over(x + W - 8, yy + 5, 7, 7)) {
                        c.g().setTooltipForNextFrame(c.font(), Component.translatable(ManualStrings.UNREAD), c.mouseX(), c.mouseY());
                    }
                }
                yy += ROW;
            }
        }

        static boolean clickRows(Ctx c, List<Manual.Entry> rows, int x, int y, double mx, double my) {
            if (mx < x || mx >= x + W || my < y) {
                return false;
            }
            int i = (int) ((my - y) / ROW);
            if (i < rows.size()) {
                c.screen().openEntry(rows.get(i).id());
                return true;
            }
            return false;
        }

        @Override
        boolean click(Ctx c, int x, int y, double mx, double my) {
            return clickRows(c, this.rows, x, y + top() + headingH(), mx, my);
        }
    }

    /** The chapters on the home spread. */
    /** A page of the contents: the chapters it has room for, the first page under the heading ({@link #chapterLists}). */
    static final class ChapterList extends Page {
        static final int ROW = 20, HEADING = 16;
        final List<Manual.Chapter> chapters;
        final boolean heading;

        ChapterList(List<Manual.Chapter> chapters, boolean heading) {
            this.chapters = chapters;
            this.heading = heading;
        }

        @Override
        void draw(Ctx c, int x, int y) {
            if (this.heading) {
                centered(c.g(), c.font(), Component.translatable(ManualStrings.CONTENTS).withStyle(s -> s.withBold(true)), x + W / 2, y, ManualText.INK);
            }
            int yy = y + (this.heading ? HEADING : 0);
            for (Manual.Chapter ch : this.chapters) {
                boolean hover = c.over(x, yy, W, ROW - 1);
                if (hover) {
                    c.g().fill(x, yy, x + W, yy + ROW - 1, 0x22000000);
                    c.g().requestCursor(CursorTypes.POINTING_HAND);
                }
                c.g().item(new ItemStack(ch.icon()), x + 1, yy + 1);
                c.g().text(c.font(), Component.literal(ch.title()), x + 22, yy + 6, ManualText.INK, false);
                long unread = Manual.entriesOf(ch.id()).stream().filter(e -> !ManualProgress.isRead(e.id())).count();
                if (unread > 0) {
                    c.g().blit(RenderPipelines.GUI_TEXTURED, FieldManualScreen.TEXTURE, x + W - 7, yy + 7, 178, 226, 5, 5, FieldManualScreen.TEX_W, FieldManualScreen.TEX_H);
                    if (c.over(x + W - 8, yy + 6, 7, 7)) {
                        c.g().setTooltipForNextFrame(c.font(), Component.translatable(ManualStrings.UNREAD_COUNT, unread), c.mouseX(), c.mouseY());
                    }
                }
                yy += ROW;
            }
        }

        @Override
        boolean click(Ctx c, int x, int y, double mx, double my) {
            int top = y + (this.heading ? HEADING : 0);
            if (mx < x || mx >= x + W || my < top) {
                return false;
            }
            int i = (int) ((my - top) / ROW);
            if (i < this.chapters.size()) {
                c.screen().openChapter(this.chapters.get(i).id());
                return true;
            }
            return false;
        }
    }

    // --- layout ----------------------------------------------------------------------------------------------------------
    /** Lays out an entry's pages: each spec's picture or recipes, then its text, flowing down the pages. */
    static List<Page> entry(Font font, Manual.Entry entry) {
        List<FlowPage> pages = new ArrayList<>();
        FlowPage first = new FlowPage();
        first.setTitle(font, entry.title());
        pages.add(first);
        for (Manual.PageSpec spec : mergeMachines(entry.pages())) {
            boolean opening = first.parts.isEmpty();
            List<@Nullable FormattedCharSequence> text = ManualText.lines(font, spec.text(), W);
            List<GraphicPage> graphics = graphics(font, spec, opening ? entry.title() : null);
            for (int g = 0; g < graphics.size(); g++) {
                // The last picture keeps the start of its text with it (a picture whose words began the next page, beside
                // the next picture, read as that one's).
                put(pages, graphics.get(g), g == graphics.size() - 1 ? firstParagraph(text) : 0);
            }
            flow(pages, text);
            if (spec instanceof Manual.StructureSpec s) {
                MultiblockDefinition def = GBMultiblocks.byId(s.structure());
                if (def != null) {
                    for (GraphicPage m : materials(def)) {
                        put(pages, m);
                    }
                }
            }
        }
        return new ArrayList<>(pages);
    }

    /** Places a picture or recipe on the current page, or on a new one when it does not fit. */
    private static void put(List<FlowPage> pages, GraphicPage graphic) {
        put(pages, graphic, 0);
    }

    /** As {@link #put(List, GraphicPage)}, with room kept under it for {@code keep} more (its text's first paragraph). */
    private static void put(List<FlowPage> pages, GraphicPage graphic, int keep) {
        FlowPage page = pages.get(pages.size() - 1);
        int need = graphic.graphic + (graphic.graphic + keep <= H ? keep : 0);
        if (need > page.room() && !page.parts.isEmpty()) {
            page = new FlowPage();
            pages.add(page);
        }
        page.add(graphic, List.of(), graphic.graphic);
    }

    /** The height of a text's first paragraph (its lines up to the first gap marker). */
    private static int firstParagraph(List<@Nullable FormattedCharSequence> lines) {
        int n = 0;
        for (FormattedCharSequence line : lines) {
            if (line == null) {
                if (n > 0) {
                    break;
                }
                continue;
            }
            n++;
        }
        return n * LINE;
    }

    /** Flows text lines into the room left on the current page and on as many new pages as they need. */
    private static void flow(List<FlowPage> pages, List<@Nullable FormattedCharSequence> lines) {
        // Paragraphs (runs of lines between the gap markers) stay whole: one that does not fit in the room left moves
        // to the next page, leaving a gap; only a paragraph taller than a whole page is split.
        List<List<@Nullable FormattedCharSequence>> paragraphs = new ArrayList<>();
        List<@Nullable FormattedCharSequence> current = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            if (line == null) {
                if (!current.isEmpty()) {
                    paragraphs.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(line);
            }
        }
        if (!current.isEmpty()) {
            paragraphs.add(current);
        }
        for (List<@Nullable FormattedCharSequence> para : paragraphs) {
            FlowPage page = pages.get(pages.size() - 1);
            int h = para.size() * LINE;
            if (h > page.room() && !page.parts.isEmpty() && h <= H) {
                page = new FlowPage();
                pages.add(page);
            }
            int start = 0;
            while (start < para.size()) {
                int take = Math.max(start + (page.parts.isEmpty() ? 1 : 0), Math.min(para.size(), start + page.room() / LINE));
                if (take == start) {
                    page = new FlowPage();
                    pages.add(page);
                    continue;
                }
                List<@Nullable FormattedCharSequence> part = new ArrayList<>(para.subList(start, take));
                page.add(null, part, part.size() * LINE);
                start = take;
                if (start < para.size()) {
                    page = new FlowPage();
                    pages.add(page);
                }
            }
        }
    }

    /** Machine pages in a row for the same machine with no text of their own, shown as one list of recipes. */
    record MachineGroup(String kind, List<Manual.MachineSpec> filters, String text) implements Manual.PageSpec {}

    private static List<Manual.PageSpec> mergeMachines(List<Manual.PageSpec> specs) {
        List<Manual.PageSpec> out = new ArrayList<>();
        for (Manual.PageSpec spec : specs) {
            if (spec instanceof Manual.MachineSpec m && !out.isEmpty() && out.get(out.size() - 1) instanceof MachineGroup g
                    && g.kind().equals(m.kind()) && g.text().isBlank()) {
                List<Manual.MachineSpec> filters = new ArrayList<>(g.filters());
                filters.add(m);
                out.set(out.size() - 1, new MachineGroup(m.kind(), filters, m.text()));
            } else if (spec instanceof Manual.MachineSpec m) {
                out.add(new MachineGroup(m.kind(), List.of(m), m.text()));
            } else {
                out.add(spec);
            }
        }
        return out;
    }

    /** The picture or recipe pages a spec draws (several when there are many recipes). */
    private static List<GraphicPage> graphics(Font font, Manual.PageSpec spec, @Nullable String openingTitle) {
        if (spec instanceof Manual.SpotlightSpec s) {
            String name = s.title() != null ? s.title() : new ItemStack(s.item()).getHoverName().getString();
            return List.of(new SpotlightPage(font, s.item(), s.title(), name.equals(openingTitle)));
        }
        if (spec instanceof Manual.CraftingSpec s) {
            List<GraphicPage> out = new ArrayList<>();
            for (RecipeDisplay d : ClientRecipes.crafting(s.item(), s.smelting())) {
                out.add(new CraftingPage(font, d, new ItemStack(s.item()).getHoverName()));
            }
            if (out.isEmpty()) {
                out.add(new SpotlightPage(font, s.item(), null, false));
            }
            return out;
        }
        if (spec instanceof MachineGroup group) {
            ProcessingKind kind = kind(group.kind());
            List<MachineRow> rows = new ArrayList<>();
            if (kind != null) {
                for (Manual.MachineSpec s : group.filters()) {
                    for (RecipeHolder<ProcessingRecipe> holder : ClientRecipes.get(kind)) {
                        ProcessingRecipe r = holder.value();
                        boolean item = s.item() == null || r.outputs().stream().anyMatch(o -> o.item().item().value() == s.item());
                        boolean fluid = s.fluid() == null || r.fluidOutputs().stream()
                                .anyMatch(f -> BuiltInRegistries.FLUID.getKey(f.fluid().value()).equals(s.fluid()));
                        if ((s.item() != null && s.fluid() != null ? item || fluid : item && fluid)
                                && rows.stream().noneMatch(row -> row.recipe() == r)) {
                            rows.add(new MachineRow(r));
                        }
                    }
                }
            }
            Component heading = Component.literal(machineName(group.kind()));
            List<GraphicPage> out = new ArrayList<>();
            List<MachineRow> page = new ArrayList<>();
            int h = headingH(font, heading);
            for (MachineRow row : rows) {
                if (!page.isEmpty() && h + row.height() + 4 > H) {
                    out.add(new MachinePage(font, heading, page));
                    page = new ArrayList<>();
                    h = headingH(font, heading);
                }
                page.add(row);
                h += row.height() + 4;
            }
            if (!page.isEmpty() || out.isEmpty()) {
                out.add(new MachinePage(font, heading, page));
            }
            return out;
        }
        if (spec instanceof Manual.StructureSpec s) {
            MultiblockDefinition def = GBMultiblocks.byId(s.structure());
            return def == null ? List.of() : List.of(new StructurePage(def));
        }
        if (spec instanceof Manual.ImageSpec s) {
            return List.of(new ImagePage(s.image(), s.width(), s.height()));
        }
        if (spec instanceof Manual.TableSpec s) {
            return List.of(new TablePage(s.title(), s.rows()));
        }
        if (spec instanceof Manual.LinksSpec s) {
            List<Manual.Entry> links = s.entries().stream().map(Manual::entry).filter(java.util.Objects::nonNull).toList();
            return List.of(new LinksPage(s.title(), links));
        }
        return List.of();
    }

    private static List<GraphicPage> materials(MultiblockDefinition def) {
        Map<Item, Integer> counts = new java.util.LinkedHashMap<>();
        for (StructureView.Block b : StructureView.blocks(def)) {
            var state = b.state();
            if (state.getBlock() instanceof dev.groundburst.content.multiblock.SpreadPartBlock
                    && state.getValue(dev.groundburst.content.multiblock.SpreadPartBlock.PART) == dev.groundburst.content.multiblock.SpreadPartBlock.Part.EDGE) {
                continue;                                    // one item places a whole 3x3 (a Silo Hatch, a Large Radar Dish): its middle counts
            }
            counts.merge(state.getBlock().asItem(), 1, Integer::sum);
        }
        List<Map.Entry<ItemStack, Integer>> rows = counts.entrySet().stream()
                .map(e -> Map.entry(new ItemStack(e.getKey()), e.getValue())).toList();
        List<GraphicPage> out = new ArrayList<>();
        int per = (H - 14) / 19;
        for (int i = 0; i < rows.size(); i += per) {
            out.add(new MaterialsPage(rows.subList(i, Math.min(rows.size(), i + per))));
        }
        return out;
    }

    static @Nullable ProcessingKind kind(String id) {
        for (ProcessingKind k : ProcessingKind.values()) {
            if (k.id().equals(id)) {
                return k;
            }
        }
        return null;
    }

    private static String machineName(String kind) {
        return switch (kind) {
            case "crushing" -> "Ore Crusher";
            case "arc_smelting" -> "Arc Furnace";
            case "pressing" -> "Industrial Press";
            case "chemistry" -> "Chemical Plant";
            case "centrifuging" -> "Gas Centrifuge";
            case "electrolysis" -> "Electrolytic Separator";
            case "fusion_synthesis" -> "Fusion Fuel Synthesizer";
            case "cryo_condensing" -> "Cryogenic Condenser";
            case "fabricating" -> "Circuit Fabricator";
            case "exotic_fabricating" -> "Exotic Matter Fabricator";
            case "accelerating" -> "Particle Accelerator";
            case "canning" -> "Canning Machine";
            default -> kind;
        };
    }

    /** Splits a list of entries into list pages, the first with a heading. */
    /** The contents over as many pages as the chapters need (nine do not fit on one), the heading on the first. */
    static List<Page> chapterLists(List<Manual.Chapter> chapters) {
        List<Page> out = new ArrayList<>();
        int i = 0;
        boolean first = true;
        while (i < chapters.size() || first) {
            int n = Math.max(1, (H - (first ? ChapterList.HEADING : 0)) / ChapterList.ROW);
            out.add(new ChapterList(chapters.subList(i, Math.min(chapters.size(), i + n)), first));
            i += n;
            first = false;
        }
        return out;
    }

    static List<Page> entryLists(@Nullable String heading, List<Manual.Entry> entries, int firstRoom) {
        List<Page> out = new ArrayList<>();
        int i = 0;
        boolean first = true;
        while (i < entries.size() || first) {
            int room = (first ? firstRoom : H) - (first && heading != null ? 14 : 0);
            int n = Math.max(1, room / EntryList.ROW);
            out.add(new EntryList(first ? heading : null, entries.subList(i, Math.min(entries.size(), i + n))));
            i += n;
            first = false;
        }
        return out;
    }
}
