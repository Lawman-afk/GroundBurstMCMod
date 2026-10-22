package dev.groundzero.client.screen;

import java.util.List;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyStats;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The right-hand column of an assembly screen (the Ordnance Assembly Bench's, slice 7c; the Strategic Assembly Hall's,
 * slice 8c): a 3D preview of what the parts make, turning by itself or dragged round, over a stats screen
 * ({@link AssemblyStats}). The preview and stats windows are drawn into the screen's texture by gui.py.
 */
final class AssemblyColumn {
    /** The preview's tilt limits (degrees) and its margin round the model (pixels). */
    private static final float MIN_PITCH = -10, MAX_PITCH = 70;
    private static final int MARGIN = 4;
    private static final int STAT_HEADER = 0xFFFFE2A8, STAT_LABEL = 0xFFC88A34, STAT_VALUE = 0xFFFFB84A, STAT_NOTE = 0xFFE8A54A,
            STAT_EMPTY = 0xFF9A7440;
    private static final String T_DRAG = GZLang.add(GroundZero.MODID + ".assembly.drag", "Drag to Turn It");

    private final int viewX, viewY, viewW, viewH, statsX, statsY, statsW, statsH;
    /** The preview's turn and tilt; it turns by itself unless dragged in the last 3 s. */
    private float yaw = 30, pitch = 20;
    private boolean dragging;
    private long lastDrag, lastFrame = Util.getMillis();
    /** The last subject measured, and its model's bounds (blocks, as drawn with no GUI pose). */
    private final net.minecraft.client.renderer.item.ItemStackRenderState measure = new net.minecraft.client.renderer.item.ItemStackRenderState();
    private ItemStack measured = ItemStack.EMPTY;
    private net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0);

    /** The preview window and the stats screen, relative to the screen's window. */
    AssemblyColumn(int viewX, int viewY, int viewW, int viewH, int statsX, int statsY, int statsW, int statsH) {
        this.viewX = viewX;
        this.viewY = viewY;
        this.viewW = viewW;
        this.viewH = viewH;
        this.statsX = statsX;
        this.statsY = statsY;
        this.statsW = statsW;
        this.statsH = statsH;
    }

    /** Draws {@code subject} in the preview and {@code stats} under it ({@code emptyHint} with nothing to show). */
    void draw(GuiGraphicsExtractor g, Font font, int left, int top, ItemStack subject, List<AssemblyStats.Stat> stats, Component emptyHint,
            int mouseX, int mouseY) {
        drawPreview(g, left, top, subject, mouseX, mouseY);
        drawStats(g, font, left, top, stats, emptyHint);
    }

    /** The subject in 3D on the blueprint: a missile at its body's size, so a Tactical one stands smaller than a Heavy. */
    private void drawPreview(GuiGraphicsExtractor g, int left, int top, ItemStack subject, int mouseX, int mouseY) {
        long now = Util.getMillis();
        if (!this.dragging && now - this.lastDrag > 3000) {
            this.yaw += (now - this.lastFrame) * 0.03f;           // a slow turn while it is left alone
        }
        this.lastFrame = now;
        int x = left + this.viewX, y = top + this.viewY;
        if (!subject.isEmpty()) {
            MissileSpec spec = subject.get(GZDataComponents.MISSILE.get());
            float[] size = spec == null ? new float[] {1, 1} : MissileParts.scale(spec.body());
            net.minecraft.world.phys.AABB box = bounds(subject);
            // Zoomed to fit the model; a bench missile as if it had a Heavy body, so a smaller body stands smaller (a
            // Strategic or intercontinental missile is its own model, fitted as it is).
            boolean ownModel = spec == null || spec.body() == SizeClass.XL || spec.body() == SizeClass.XXL;
            float[] fitSize = ownModel ? size : MissileParts.scale(SizeClass.L);
            float scale = ItemPreview.fit(box, fitSize, this.viewW - 2 * MARGIN, this.viewH - 2 * MARGIN, MIN_PITCH, MAX_PITCH);
            float[] centre = {(float) (box.minX + box.maxX) / 2, (float) (box.minY + box.maxY) / 2, (float) (box.minZ + box.maxZ) / 2};
            g.submitPictureInPictureRenderState(new ItemPreview.State(subject, size, centre, this.yaw, this.pitch, x, y, x + this.viewW,
                    y + this.viewH, scale, g.peekScissorStack()));
        }
        if (overPreview(left, top, mouseX, mouseY) && !subject.isEmpty()) {
            g.requestCursor(CursorTypes.RESIZE_ALL);
            if (!this.dragging) {
                g.setTooltipForNextFrame(Component.translatable(T_DRAG), mouseX, mouseY);
            }
        }
    }

    /** The subject's model bounds (measured again only when the subject changes). */
    private net.minecraft.world.phys.AABB bounds(ItemStack subject) {
        if (!ItemStack.isSameItemSameComponents(subject, this.measured)) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            mc.getItemModelResolver().updateForTopItem(this.measure, subject, net.minecraft.world.item.ItemDisplayContext.NONE, mc.level, null, 0);
            this.bounds = this.measure.getModelBoundingBox();
            this.measured = subject.copy();
        }
        return this.bounds;
    }

    private boolean overPreview(int left, int top, double mx, double my) {
        int x = left + this.viewX, y = top + this.viewY;
        return mx >= x && mx < x + this.viewW && my >= y && my < y + this.viewH;
    }

    /**
     * The stats screen: headers and notes wrap, rows put the label on the left and the value on the right. Small type,
     * each font pixel a whole number of screen pixels on the screen-pixel grid, like the screens' setting rows.
     */
    private void drawStats(GuiGraphicsExtractor g, Font font, int leftPos, int topPos, List<AssemblyStats.Stat> stats, Component emptyHint) {
        int gs = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        float step = (float) Math.ceil(font.lineHeight * scale * gs) / gs;
        float left = leftPos + this.statsX + 3, right = leftPos + this.statsX + this.statsW - 3;
        float top = topPos + this.statsY + 3, bottom = topPos + this.statsY + this.statsH - 2;
        int room = (int) ((right - left) / scale);
        float y = top;
        if (stats.isEmpty()) {
            for (FormattedCharSequence line : font.split(emptyHint, room)) {
                smallText(g, font, line, left, y, scale, gs, STAT_EMPTY);
                y += step;
            }
            return;
        }
        for (int i = 0; i < stats.size(); i++) {
            AssemblyStats.Stat s = stats.get(i);
            if (s.kind() != AssemblyStats.Kind.ROW && i > 0 && stats.get(i - 1).kind() == AssemblyStats.Kind.ROW
                    || s.kind() == AssemblyStats.Kind.ROW && i > 0 && stats.get(i - 1).kind() == AssemblyStats.Kind.NOTE) {
                y += Math.round(step / 2 * gs) / (float) gs;         // a little air round headers and notes after rows
            }
            if (s.kind() == AssemblyStats.Kind.ROW) {
                if (y + font.lineHeight * scale > bottom) {
                    break;
                }
                int valueWidth = font.width(s.value());
                String label = font.plainSubstrByWidth(s.label().getString(), room - valueWidth - 4);
                smallText(g, font, Component.literal(label).getVisualOrderText(), left, y, scale, gs, STAT_LABEL);
                smallText(g, font, s.value().getVisualOrderText(), right - valueWidth * scale, y, scale, gs, STAT_VALUE);
                y += step;
                continue;
            }
            for (FormattedCharSequence line : font.split(s.label(), room)) {
                if (y + font.lineHeight * scale > bottom) {
                    break;
                }
                smallText(g, font, line, left, y, scale, gs, s.kind() == AssemblyStats.Kind.HEADER ? STAT_HEADER : STAT_NOTE);
                y += step;
            }
        }
    }

    private static void smallText(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, float x, float y, float scale, int gs, int color) {
        g.pose().pushMatrix();
        g.pose().translate(Math.round(x * gs) / (float) gs, Math.round(y * gs) / (float) gs);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** A click on the preview starts a drag (with nothing carried); true if it took the click. */
    boolean mouseClicked(int left, int top, double x, double y, boolean carriedEmpty) {
        if (overPreview(left, top, x, y) && carriedEmpty) {
            this.dragging = true;
            return true;
        }
        return false;
    }

    boolean mouseDragged(double dx, double dy) {
        if (this.dragging) {
            this.yaw += (float) dx * 1.5f;
            this.pitch = (float) Math.max(MIN_PITCH, Math.min(MAX_PITCH, this.pitch + dy));
            this.lastDrag = Util.getMillis();
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        if (this.dragging) {
            this.dragging = false;
            this.lastDrag = Util.getMillis();
            return true;
        }
        return false;
    }
}
