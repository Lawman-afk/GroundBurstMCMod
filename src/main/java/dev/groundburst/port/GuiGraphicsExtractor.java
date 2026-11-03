package dev.groundburst.port;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft 26.x's {@code GuiGraphicsExtractor} (what a screen draws with) over 1.21.1's {@link GuiGraphics}, so the
 * mod's screens draw with main's own code (docs/BACKPORT.md). Tooltips are drawn at once (1.21.1 has no "next frame"
 * queue on the graphics), on top of what came before.
 */
public final class GuiGraphicsExtractor {
    private final GuiGraphics g;
    private final Matrix3x2fStack pose;

    public GuiGraphicsExtractor(GuiGraphics g) {
        this.g = g;
        this.pose = new Matrix3x2fStack(g.pose());
    }

    /** 1.21.1's graphics underneath (for what has no 26.x counterpart here). */
    public GuiGraphics unwrap() {
        return this.g;
    }

    public int guiWidth() {
        return this.g.guiWidth();
    }

    public int guiHeight() {
        return this.g.guiHeight();
    }

    public Matrix3x2fStack pose() {
        return this.pose;
    }

    public void nextStratum() {
        this.g.pose().translate(0, 0, 50);
    }

    public void requestCursor(Object cursorType) {}

    /** 26.x draws a 3D view into a texture and the texture into the screen; 1.21.1 draws the view straight in. */
    public void submitPictureInPictureRenderState(dev.groundburst.port.render.PictureInPicture view) {
        view.draw(this.g);
    }

    /** The area 26.x clips a picture-in-picture view to (1.21.1's views clip to their own bounds). */
    public net.minecraft.client.gui.navigation.@Nullable ScreenRectangle peekScissorStack() {
        return null;
    }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        this.g.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor() {
        this.g.disableScissor();
    }

    // --- fills ---------------------------------------------------------------------------------------------------------------
    public void fill(int x0, int y0, int x1, int y1, int col) {
        this.g.fill(x0, y0, x1, y1, col);
    }

    public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col) {
        this.g.fill(x0, y0, x1, y1, col);
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int col1, int col2) {
        this.g.fillGradient(x0, y0, x1, y1, col1, col2);
    }

    public void outline(int x, int y, int width, int height, int color) {
        this.g.renderOutline(x, y, width, height, color);
    }

    public void hLine(int x0, int x1, int y, int col) {
        this.g.hLine(x0, x1, y, col);
    }

    public void vLine(int x, int y0, int y1, int col) {
        this.g.vLine(x, y0, y1, col);
    }

    // --- text ----------------------------------------------------------------------------------------------------------------
    public void text(Font font, @Nullable String str, int x, int y, int color) {
        this.g.drawString(font, str, x, y, color);
    }

    public void text(Font font, @Nullable String str, int x, int y, int color, boolean dropShadow) {
        this.g.drawString(font, str, x, y, color, dropShadow);
    }

    public void text(Font font, FormattedCharSequence str, int x, int y, int color) {
        this.g.drawString(font, str, x, y, color);
    }

    public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow) {
        this.g.drawString(font, str, x, y, color, dropShadow);
    }

    public void text(Font font, Component str, int x, int y, int color) {
        this.g.drawString(font, str, x, y, color);
    }

    public void text(Font font, Component str, int x, int y, int color, boolean dropShadow) {
        this.g.drawString(font, str, x, y, color, dropShadow);
    }

    public void centeredText(Font font, String str, int x, int y, int color) {
        this.g.drawCenteredString(font, str, x, y, color);
    }

    public void centeredText(Font font, Component text, int x, int y, int color) {
        this.g.drawCenteredString(font, text, x, y, color);
    }

    public void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.g.drawCenteredString(font, text, x, y, color);
    }

    public int textWithWordWrap(Font font, FormattedText string, int x, int y, int width, int col) {
        this.g.drawWordWrap(font, string, x, y, width, col);
        return font.wordWrapHeight(string, width);
    }

    // --- textures --------------------------------------------------------------------------------------------------------------
    public void blit(RenderPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int textureWidth, int textureHeight) {
        this.g.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blit(RenderPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int textureWidth, int textureHeight, int color) {
        tinted(color, () -> this.g.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight));
    }

    public void blit(RenderPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        this.g.blit(texture, x, y, width, height, u, v, srcWidth, srcHeight, textureWidth, textureHeight);
    }

    public void blit(RenderPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color) {
        tinted(color, () -> this.g.blit(texture, x, y, width, height, u, v, srcWidth, srcHeight, textureWidth, textureHeight));
    }

    /** A texture's UV rectangle (as fractions of it) stretched over (x0, y0)-(x1, y1). */
    public void blit(ResourceLocation texture, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
        final int s = 1 << 16;   // 1.21.1's blit takes the texture's size in whole pixels: a large one keeps the fractions
        this.g.blit(texture, x0, y0, x1 - x0, y1 - y0, u0 * s, v0 * s, Math.round((u1 - u0) * s), Math.round((v1 - v0) * s), s, s);
    }

    public void blitSprite(RenderPipeline pipeline, ResourceLocation sprite, int x, int y, int width, int height) {
        this.g.blitSprite(sprite, x, y, width, height);
    }

    public void blitSprite(RenderPipeline pipeline, ResourceLocation sprite, int x, int y, int width, int height, int color) {
        tinted(color, () -> this.g.blitSprite(sprite, x, y, width, height));
    }

    public void blitSprite(RenderPipeline pipeline, TextureAtlasSprite sprite, int x, int y, int width, int height) {
        this.g.blit(x, y, 0, width, height, sprite);
    }

    public void blitSprite(RenderPipeline pipeline, TextureAtlasSprite sprite, int x, int y, int width, int height, int color) {
        tinted(color, () -> this.g.blit(x, y, 0, width, height, sprite));
    }

    private static void tinted(int argb, Runnable draw) {
        RenderSystem.setShaderColor((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        try {
            draw.run();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    // --- items -----------------------------------------------------------------------------------------------------------------
    public void item(ItemStack itemStack, int x, int y) {
        this.g.renderItem(itemStack, x, y);
    }

    public void item(ItemStack itemStack, int x, int y, int seed) {
        this.g.renderItem(itemStack, x, y, seed);
    }

    public void itemDecorations(Font font, ItemStack itemStack, int x, int y) {
        this.g.renderItemDecorations(font, itemStack, x, y);
    }

    public void itemDecorations(Font font, ItemStack itemStack, int x, int y, @Nullable String countText) {
        this.g.renderItemDecorations(font, itemStack, x, y, countText);
    }

    // --- tooltips (drawn at once) --------------------------------------------------------------------------------------------
    public void setTooltipForNextFrame(Component text, int x, int y) {
        this.g.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, text, x, y);
    }

    public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
        this.g.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, lines, x, y);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        this.g.renderTooltip(font, text, x, y);
    }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        this.g.renderTooltip(font, stack, x, y);
    }

    public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        this.g.renderTooltip(font, lines, x, y);
    }

    public void setTooltipForNextFrame(Font font, List<Component> texts, Optional<TooltipComponent> image, int x, int y) {
        this.g.renderTooltip(font, texts, image, x, y);
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y) {
        this.g.renderComponentTooltip(font, lines, x, y);
    }
}
