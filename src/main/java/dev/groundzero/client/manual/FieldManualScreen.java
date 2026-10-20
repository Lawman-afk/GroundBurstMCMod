package dev.groundzero.client.manual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import dev.groundzero.GroundZero;
import dev.groundzero.content.manual.ManualStrings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The Field Manual (DESIGN.md §24.2): a two-page spread. Views: the home spread (title, search, chapters), a chapter
 * (its introduction and entries) and an entry (its pages). Arrows, the mouse wheel and the arrow keys turn pages; Back
 * returns to the last view; bookmarks sit on the book's right edge.
 */
public class FieldManualScreen extends Screen {
    static final Identifier TEXTURE = GroundZero.id("textures/gui/manual/book.png");
    static final int TEX_W = 512, TEX_H = 512;
    static final int BOOK_W = 320, BOOK_H = 200;
    /** Content origins of the two pages, relative to the book. */
    static final int LEFT_X = 22, RIGHT_X = 172, PAGE_Y = 24;

    private enum Kind { HOME, CHAPTER, ENTRY }

    private record View(Kind kind, @Nullable String id, int spread) {}

    private final @Nullable Screen parent;
    private View view;
    private final Deque<View> history = new ArrayDeque<>();
    private List<ManualPages.Page> pages = List.of();
    private int builtFor = -1;
    private @Nullable EditBox search;
    private String query = "";
    private int left, top;
    private int ticks;
    private @Nullable ItemStack hovered;

    public FieldManualScreen(@Nullable Screen parent, @Nullable String entry) {
        super(Component.translatable("item.groundzero.field_manual"));
        this.parent = parent;
        if (entry != null && Manual.entry(entry) != null) {
            this.view = new View(Kind.ENTRY, entry, 0);
        } else if (ManualProgress.lastEntry() != null && Manual.entry(ManualProgress.lastEntry()) != null) {
            this.view = new View(Kind.ENTRY, ManualProgress.lastEntry(), ManualProgress.lastSpread());
        } else {
            this.view = new View(Kind.HOME, null, 0);
        }
    }

    /** A book opened on a given view (for the development screenshots): an entry or chapter id, or null for home. */
    static FieldManualScreen at(@Nullable String target, int spread, @Nullable String query) {
        FieldManualScreen s = new FieldManualScreen(null, null);
        if (target == null) {
            s.view = new View(Kind.HOME, null, spread);
        } else {
            s.view = new View(Manual.chapter(target) != null ? Kind.CHAPTER : Kind.ENTRY, target, spread);
        }
        if (query != null) {
            s.query = query;
        }
        return s;
    }

    @Override
    protected void init() {
        this.left = (this.width - BOOK_W) / 2;
        this.top = (this.height - BOOK_H) / 2;
        this.search = new EditBox(this.font, this.left + LEFT_X + 14, this.top + PAGE_Y + 118, ManualPages.W - 16, 12, Component.translatable(ManualStrings.SEARCH));
        this.search.setBordered(false);
        this.search.setTextColor(ManualText.INK);
        this.search.setHint(Component.translatable(ManualStrings.SEARCH).withColor(ManualPages.FADED));
        this.search.setMaxLength(40);
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            if (this.view.kind() == Kind.HOME) {
                this.view = new View(Kind.HOME, null, 0);
            }
            this.builtFor = -1;
        });
        addRenderableWidget(this.search);
        this.builtFor = -1;
    }

    // --- views -------------------------------------------------------------------------------------------------------
    private void rebuild() {
        List<ManualPages.Page> out = new ArrayList<>();
        switch (this.view.kind()) {
            case HOME -> {
                out.add(new HomePage());
                if (this.query.isBlank()) {
                    out.add(new ManualPages.ChapterList(Manual.chapters()));
                } else {
                    out.addAll(ManualPages.entryLists(Component.translatable(ManualStrings.RESULTS).getString(), search(this.query), ManualPages.H));
                }
            }
            case CHAPTER -> {
                Manual.Chapter ch = Manual.chapter(this.view.id());
                if (ch == null) {
                    this.view = new View(Kind.HOME, null, 0);
                    rebuild();
                    return;
                }
                ManualPages.TextPage intro = new ManualPages.TextPage(ManualText.lines(this.font, ch.text(), ManualPages.W));
                intro.setTitle(this.font, ch.title());
                out.add(intro);
                // The introduction fills the left page; the entries start on the right one.
                out.addAll(ManualPages.entryLists(Component.translatable(ManualStrings.ENTRIES).getString(), Manual.entriesOf(ch.id()), ManualPages.H));
            }
            case ENTRY -> {
                Manual.Entry e = Manual.entry(this.view.id());
                if (e == null) {
                    this.view = new View(Kind.HOME, null, 0);
                    rebuild();
                    return;
                }
                out.addAll(ManualPages.entry(this.font, e));
                ManualProgress.markRead(e.id());
            }
        }
        this.pages = out;
        this.builtFor = Manual.generation();
        int spreads = spreads();
        if (this.view.spread() >= spreads) {
            this.view = new View(this.view.kind(), this.view.id(), spreads - 1);
        }
        if (this.search != null) {
            this.search.visible = this.view.kind() == Kind.HOME && this.view.spread() == 0;
        }
        ManualProgress.remember(this.view.kind() == Kind.ENTRY ? this.view.id() : null, this.view.spread());
    }

    private static List<Manual.Entry> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).strip();
        List<Manual.Entry> titles = new ArrayList<>(), rest = new ArrayList<>();
        for (Manual.Entry e : Manual.allEntries()) {
            if (e.title().toLowerCase(Locale.ROOT).contains(q)) {
                titles.add(e);
            } else if (e.items().stream().anyMatch(i -> new ItemStack(i).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q))
                    || e.pages().stream().anyMatch(p -> p.text().toLowerCase(Locale.ROOT).contains(q))) {
                rest.add(e);
            }
        }
        titles.sort(java.util.Comparator.comparing(Manual.Entry::title));
        rest.sort(java.util.Comparator.comparing(Manual.Entry::title));
        titles.addAll(rest);
        return titles;
    }

    private int spreads() {
        return Math.max(1, (this.pages.size() + 1) / 2);
    }

    private void go(View next) {
        this.history.push(this.view);
        this.view = next;
        this.builtFor = -1;
        click();
    }

    void openEntry(String id) {
        if (Manual.entry(id) != null && !(this.view.kind() == Kind.ENTRY && id.equals(this.view.id()))) {
            go(new View(Kind.ENTRY, id, 0));
        }
    }

    void openChapter(String id) {
        go(new View(Kind.CHAPTER, id, 0));
    }

    /** A text link: an entry id, or a chapter id. */
    void openLink(String target) {
        if (Manual.entry(target) != null) {
            openEntry(target);
        } else if (Manual.chapter(target) != null) {
            openChapter(target);
        }
    }

    private void back() {
        if (!this.history.isEmpty()) {
            this.view = this.history.pop();
            this.builtFor = -1;
            click();
        }
    }

    private void home() {
        if (this.view.kind() != Kind.HOME || this.view.spread() != 0) {
            go(new View(Kind.HOME, null, 0));
        }
    }

    private void turn(int by) {
        int to = Math.max(0, Math.min(spreads() - 1, this.view.spread() + by));
        if (to != this.view.spread()) {
            this.view = new View(this.view.kind(), this.view.id(), to);
            this.builtFor = -1;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
    }

    void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    void hoverItem(ItemStack stack) {
        this.hovered = stack;
    }

    // --- drawing -------------------------------------------------------------------------------------------------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.left, this.top, 0, 0, BOOK_W, BOOK_H, TEX_W, TEX_H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (this.builtFor != Manual.generation()) {
            rebuild();
        }
        this.hovered = null;
        super.extractRenderState(g, mouseX, mouseY, partial);
        ManualPages.Ctx c = ctx(g, mouseX, mouseY);
        int s = this.view.spread();
        for (int side = 0; side < 2; side++) {
            int i = s * 2 + side;
            if (i < this.pages.size()) {
                this.pages.get(i).render(c, this.left + (side == 0 ? LEFT_X : RIGHT_X), this.top + PAGE_Y);
            }
            if (this.view.kind() != Kind.HOME || i > 0) {
                String num = String.valueOf(i + 1);
                int px = this.left + (side == 0 ? LEFT_X : RIGHT_X) + ManualPages.W / 2;
                if (i < this.pages.size()) {
                    ManualPages.centered(g, this.font, num, px, this.top + 180, ManualPages.FADED);
                }
            }
        }
        // Turn, back, home.
        if (s > 0) {
            icon(g, this.left + 18, this.top + 178, 36, 208, 18, 10, mouseX, mouseY, ManualStrings.PREVIOUS);
        }
        if (s < spreads() - 1) {
            icon(g, this.left + BOOK_W - 36, this.top + 178, 0, 208, 18, 10, mouseX, mouseY, ManualStrings.NEXT);
        }
        if (this.view.kind() != Kind.HOME || s > 0) {
            icon(g, this.left + 14, this.top + 8, 72, 208, 11, 11, mouseX, mouseY, ManualStrings.CONTENTS);
        }
        if (!this.history.isEmpty()) {
            icon(g, this.left + 28, this.top + 8, 96, 208, 11, 11, mouseX, mouseY, ManualStrings.BACK);
        }
        bookmarks(g, mouseX, mouseY);
        if (this.hovered != null && !this.hovered.isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, this.hovered));
            Manual.Entry e = Manual.entryFor(this.hovered.getItem());
            if (e != null && !(this.view.kind() == Kind.ENTRY && e.id().equals(this.view.id()))) {
                lines.add(Component.translatable(ManualStrings.OPEN_ENTRY, e.title()).withColor(0xFF7FA6E0));
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            g.setTooltipForNextFrame(this.font, lines, this.hovered.getTooltipImage(), mouseX, mouseY);
        }
    }

    private ManualPages.Ctx ctx(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        return new ManualPages.Ctx(this, g, this.font, mouseX, mouseY, this.ticks);
    }

    private void icon(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h, int mx, int my, String tip) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u + (hover ? (u < 72 ? 18 : 12) : 0), v, w, h, TEX_W, TEX_H);
        if (hover) {
            g.requestCursor(CursorTypes.POINTING_HAND);
            g.setTooltipForNextFrame(this.font, Component.translatable(tip), mx, my);
        }
    }

    // --- bookmarks -----------------------------------------------------------------------------------------------------
    private static final int TAB_X = BOOK_W - 6, TAB_Y = 18, TAB_W = 24, TAB_H = 20, TAB_STEP = 22;

    private void bookmarks(GuiGraphicsExtractor g, int mx, int my) {
        List<String> marks = ManualProgress.bookmarks();
        int y = this.top + TAB_Y;
        for (String id : marks) {
            Manual.Entry e = Manual.entry(id);
            if (e == null) {
                continue;
            }
            boolean hover = overTab(mx, my, y);
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.left + TAB_X, y, hover ? 224 : 200, 208, TAB_W, TAB_H, TEX_W, TEX_H);
            g.item(new ItemStack(e.icon()), this.left + TAB_X + 5, y + 2);
            if (hover) {
                g.requestCursor(CursorTypes.POINTING_HAND);
                g.setTooltipForNextFrame(this.font, List.of(Component.literal(e.title()),
                        Component.translatable(ManualStrings.REMOVE_BOOKMARK).withColor(ManualPages.FADED)), java.util.Optional.empty(), mx, my);
            }
            y += TAB_STEP;
        }
        if (this.view.kind() == Kind.ENTRY && !ManualProgress.isBookmarked(this.view.id()) && marks.size() < ManualProgress.MAX_BOOKMARKS) {
            boolean hover = overTab(mx, my, y);
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.left + TAB_X, y, hover ? 272 : 248, 208, TAB_W, TAB_H, TEX_W, TEX_H);
            if (hover) {
                g.requestCursor(CursorTypes.POINTING_HAND);
                g.setTooltipForNextFrame(this.font, Component.translatable(ManualStrings.BOOKMARK), mx, my);
            }
        }
    }

    private boolean overTab(double mx, double my, int y) {
        return mx >= this.left + TAB_X + 4 && mx < this.left + TAB_X + TAB_W && my >= y && my < y + TAB_H;
    }

    private boolean clickBookmarks(double mx, double my, int button) {
        List<String> marks = ManualProgress.bookmarks();
        int y = this.top + TAB_Y;
        for (String id : marks) {
            if (Manual.entry(id) == null) {
                continue;
            }
            if (overTab(mx, my, y)) {
                if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                    ManualProgress.toggleBookmark(id);
                    click();
                } else {
                    openEntry(id);
                }
                return true;
            }
            y += TAB_STEP;
        }
        if (this.view.kind() == Kind.ENTRY && !ManualProgress.isBookmarked(this.view.id()) && overTab(mx, my, y)
                && marks.size() < ManualProgress.MAX_BOOKMARKS) {
            ManualProgress.toggleBookmark(this.view.id());
            click();
            return true;
        }
        return false;
    }

    // --- input ---------------------------------------------------------------------------------------------------------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (clickBookmarks(mx, my, event.button())) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int s = this.view.spread();
            if (s > 0 && in(mx, my, 18, 178, 18, 10)) {
                turn(-1);
                return true;
            }
            if (s < spreads() - 1 && in(mx, my, BOOK_W - 36, 178, 18, 10)) {
                turn(1);
                return true;
            }
            if ((this.view.kind() != Kind.HOME || s > 0) && in(mx, my, 14, 8, 11, 11)) {
                home();
                return true;
            }
            if (!this.history.isEmpty() && in(mx, my, 28, 8, 11, 11)) {
                back();
                return true;
            }
            ManualPages.Ctx c = ctx(null, (int) mx, (int) my);
            for (int side = 0; side < 2; side++) {
                int i = s * 2 + side;
                if (i < this.pages.size()) {
                    int px = this.left + (side == 0 ? LEFT_X : RIGHT_X), py = this.top + PAGE_Y;
                    if (mx >= px - 2 && mx < px + ManualPages.W + 2 && my >= py && my < py + ManualPages.H && this.pages.get(i).click(c, px, py, mx, my)) {
                        return true;
                    }
                }
            }
        } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && !this.history.isEmpty()) {
            back();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int s = this.view.spread();
        for (int side = 0; side < 2; side++) {
            int i = s * 2 + side;
            if (i < this.pages.size() && this.pages.get(i).drag(dx, dy, 0, 0, event.x(), event.y())) {
                return true;
            }
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (ManualPages.Page p : this.pages) {
            if (p instanceof ManualPages.FlowPage fp) {
                fp.release();
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = this.search != null && this.search.isFocused() && this.search.visible;
        if (!typing) {
            switch (event.key()) {
                case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> {
                    turn(-1);
                    return true;
                }
                case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> {
                    turn(1);
                    return true;
                }
                case InputConstants.KEY_BACKSPACE -> {
                    back();
                    return true;
                }
                default -> {
                }
            }
            if (this.hovered != null && ManualHotkey.matches(event)) {
                Manual.Entry e = Manual.entryFor(this.hovered.getItem());
                if (e != null) {
                    openEntry(e.id());
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= this.left + x && mx < this.left + x + w && my >= this.top + y && my < this.top + y + h;
    }

    @Override
    public void tick() {
        this.ticks++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    // --- the home page -------------------------------------------------------------------------------------------------
    /** Title, the cover emblem, and the search box (a widget, placed by the screen). */
    final class HomePage extends ManualPages.Page {
        @Override
        void draw(ManualPages.Ctx c, int x, int y) {
            int cx = x + ManualPages.W / 2;
            ManualPages.centered(c.g(), c.font(), Component.translatable(ManualStrings.TITLE).withStyle(s -> s.withBold(true)), cx, y + 4, ManualText.INK);
            ManualPages.centered(c.g(), c.font(), Component.translatable(ManualStrings.SERIAL), cx, y + 15, ManualPages.STAMP);
            c.g().blit(RenderPipelines.GUI_TEXTURED, TEXTURE, cx - 28, y + 28, 0, 256, 56, 56, TEX_W, TEX_H);
            List<net.minecraft.util.FormattedCharSequence> intro = c.font().split(
                    Component.translatable(ManualStrings.TAGLINE), ManualPages.W);
            int yy = y + 90;
            for (var line : intro) {
                ManualPages.centered(c.g(), c.font(), line, cx, yy, ManualPages.FADED);
                yy += 10;
            }
            // Search field frame (the EditBox draws its text inside it).
            c.g().blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + 114, 0, 320, ManualPages.W, 16, TEX_W, TEX_H);
        }
    }
}
