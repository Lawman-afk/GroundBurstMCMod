package dev.groundzero.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.AssemblyPayloads;
import dev.groundzero.content.assembly.AssemblyStats;
import dev.groundzero.content.assembly.ComponentItems;
import dev.groundzero.content.assembly.Fuze;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.WarheadAssembly;
import dev.groundzero.content.machine.MachineLayout;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Ordnance Assembly Bench's screen (DESIGN.md §10). Two pages, picked by the tabs on its left edge:
 * <ul>
 *   <li>Warheads: core, casing, fuze; the fitted fuze's setting under the readout (a barometric fuze's burst height).</li>
 *   <li>Missiles: warhead, body, motor, guidance, module; under the readout, how close the guidance lands it.</li>
 * </ul>
 * On both, ASSEMBLE (lit when the parts fit) and TAKE APART (lit when something finished waits and the part slots are
 * clear). The right-hand column (slice 7c, {@link AssemblyColumn}) shows what the parts make, or what waits in the output
 * slot: a 3D preview (turning by itself, or dragged round) over a stats screen ({@link AssemblyStats}).
 */
public class AssemblyBenchScreen extends MachineScreen {
    static final String T_ASSEMBLE = GZLang.add(GroundZero.MODID + ".assembly.assemble", "Assemble");
    static final String T_TAKE_APART = GZLang.add(GroundZero.MODID + ".assembly.take_apart", "Take Apart");
    private static final String T_NO_FUZE = GZLang.add(GroundZero.MODID + ".assembly.no_fuze", "Fit a fuze to set it");
    private static final String T_NO_SETTING = GZLang.add(GroundZero.MODID + ".assembly.no_setting", "Impact fuze: nothing to set");
    private static final String T_PAGE_WARHEADS = GZLang.add(GroundZero.MODID + ".assembly.page.warheads", "Warheads");
    private static final String T_PAGE_MISSILES = GZLang.add(GroundZero.MODID + ".assembly.page.missiles", "Missiles");
    private static final String T_UNGUIDED = GZLang.add(GroundZero.MODID + ".assembly.unguided", "Unguided: within 25% of the distance");
    /** Where the setting row and the buttons sit on the panel (gui.py draws the bench layout around them). */
    public static final int SETTING_Y = 79, FIELD_X = 118, FIELD_W = 50, FIELD_H = 14;
    public static final int BUTTONS_X = 31, BUTTONS_Y = 98, ASSEMBLE_W = 54, APART_W = 60, BUTTON_H = 18;
    /**
     * The page tabs on the window's left edge (drawn by gui.py's page_tabs_texture): the shown page's tab is 28 wide and
     * covers the window's 2-pixel border, so it grows out of the window; another page's is 24 wide, darker, and ends at
     * the border, so it sits behind.
     */
    private static final Identifier TABS = GroundZero.id("textures/gui/machine/page_tabs.png");
    private static final int TAB_W = 26, TAB_H = 24, TAB_Y = 20, TAB_STEP = 26, TAB_OFF_W = 24, TAB_OFF_V = 32;
    private static final int LABEL = 0xFFD8D4C4, LABEL_DIM = 0xFF8A8E94;


    private @Nullable Button assemble, takeApart;
    private @Nullable EditBox field;
    private @Nullable Fuze shown;
    /** The preview window over the stats screen (gui.py draws them: the layouts' "preview" and "stats"). */
    private final AssemblyColumn column = new AssemblyColumn(183, 22, 94, 80, 183, 110, 94, 92);

    public AssemblyBenchScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.field = addRenderableWidget(new EditBox(this.font, this.leftPos + FIELD_X, this.topPos + SETTING_Y, FIELD_W, FIELD_H, Component.empty()));
        this.field.setMaxLength(4);
        this.field.setResponder(this::changed);
        this.field.visible = false;
        this.assemble = addRenderableWidget(Button.builder(Component.translatable(T_ASSEMBLE),
                b -> press(page() == AssemblyBenchBlockEntity.JOB_MISSILE ? AssemblyBenchBlockEntity.BUTTON_ASSEMBLE_MISSILE : AssemblyBenchBlockEntity.BUTTON_ASSEMBLE))
                .bounds(this.leftPos + BUTTONS_X, this.topPos + BUTTONS_Y, ASSEMBLE_W, BUTTON_H).build());
        this.takeApart = addRenderableWidget(Button.builder(Component.translatable(T_TAKE_APART),
                b -> press(page() == AssemblyBenchBlockEntity.JOB_MISSILE ? AssemblyBenchBlockEntity.BUTTON_TAKE_APART_MISSILE : AssemblyBenchBlockEntity.BUTTON_TAKE_APART))
                .bounds(this.leftPos + BUTTONS_X + ASSEMBLE_W + 3, this.topPos + BUTTONS_Y, APART_W, BUTTON_H).build());
    }

    private AssemblyBenchBlockEntity bench() {
        return (AssemblyBenchBlockEntity) this.menu.machine();
    }

    /** The page shown: the bench's own (saved with it), as the menu has it. */
    private int page() {
        return this.menu.extra(AssemblyBenchBlockEntity.DATA_PAGE);
    }

    /**
     * Turns the bench to a page: the server is told (it keeps the page, and its pipes follow it) and the menu's copy of
     * the page is set at once, so the slots and texture change without waiting a tick for the server's answer.
     */
    private void showPage(int p) {
        this.menu.setData(dev.groundzero.content.machine.MachineBlockEntity.DATA_BASE_COUNT + AssemblyBenchBlockEntity.DATA_PAGE, p);
        press(AssemblyBenchBlockEntity.BUTTON_PAGE + p);
    }

    @Override
    public MachineLayout pageLayout() {
        return page() == AssemblyBenchBlockEntity.JOB_MISSILE ? MachineLayouts.ASSEMBLY_BENCH_MISSILE : super.pageLayout();
    }

    private void press(int button) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_MACHINE + button);
        }
    }

    /** A number typed into the setting: sent as it is typed (the bench keeps it within the fuze's range). */
    private void changed(String text) {
        if (!text.isEmpty() && text.chars().allMatch(Character::isDigit) && this.field != null && this.field.isFocused()) {
            ClientPacketDistributor.sendToServer(new AssemblyPayloads.FuzeSetting(bench().getBlockPos(), Integer.parseInt(text)));
        }
    }

    private boolean empty(int... slots) {
        for (int s : slots) {
            if (!this.menu.machine().inventory().getItem(s).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.assemble == null || this.takeApart == null || this.field == null) {
            return;
        }
        var inv = this.menu.machine().inventory();
        boolean working = this.menu.extra(AssemblyBenchBlockEntity.DATA_TOTAL) > 0;
        boolean missiles = page() == AssemblyBenchBlockEntity.JOB_MISSILE;
        if (missiles) {
            this.assemble.active = !working && this.menu.extra(AssemblyBenchBlockEntity.DATA_MISSILE_PROBLEM) == MissileAssembly.Problem.NONE.ordinal();
            this.takeApart.active = !working && !inv.getItem(AssemblyBenchBlockEntity.SLOT_MISSILE).isEmpty()
                    && empty(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD, AssemblyBenchBlockEntity.SLOT_BODY, AssemblyBenchBlockEntity.SLOT_MOTOR,
                    AssemblyBenchBlockEntity.SLOT_GUIDANCE, AssemblyBenchBlockEntity.SLOT_MODULE);
        } else {
            this.assemble.active = !working && this.menu.extra(AssemblyBenchBlockEntity.DATA_PROBLEM) == WarheadAssembly.Problem.NONE.ordinal();
            ItemStack out = inv.getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
            this.takeApart.active = !working && !out.isEmpty()
                    && empty(AssemblyBenchBlockEntity.SLOT_CORE, AssemblyBenchBlockEntity.SLOT_CASING, AssemblyBenchBlockEntity.SLOT_FUZE);
        }
        // The field follows the fitted fuze (warhead page): shown only for a fuze with a setting, refreshed from the bench
        // unless being typed in.
        Fuze fuze = bench().fittedFuze();
        boolean settable = !missiles && fuze != null && fuze.setting != Fuze.Setting.NONE;
        this.field.visible = settable;
        if (!settable && this.field.isFocused()) {
            this.field.setFocused(false);
        }
        String value = Integer.toString(this.menu.extra(AssemblyBenchBlockEntity.DATA_FUZE_VALUE));
        if (settable && (fuze != this.shown || !this.field.isFocused() && !this.field.getValue().equals(value))) {
            this.field.setValue(value);
        }
        this.shown = fuze;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        boolean missiles = page() == AssemblyBenchBlockEntity.JOB_MISSILE;
        Component label;
        boolean bright;
        if (missiles) {
            ItemStack guidance = this.menu.machine().inventory().getItem(AssemblyBenchBlockEntity.SLOT_GUIDANCE);
            label = guidance.getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance.accuracy() : Component.translatable(T_UNGUIDED);
            bright = guidance.getItem() instanceof MissileParts.GuidanceItem;
        } else {
            Fuze fuze = bench().fittedFuze();
            bright = fuze != null && fuze.setting != Fuze.Setting.NONE;
            label = fuze == null ? Component.translatable(T_NO_FUZE) : !bright ? Component.translatable(T_NO_SETTING) : Component.translatable(fuze.setting.label);
        }
        boolean field = !missiles && bright;
        // Small type, each font pixel a whole number of screen pixels on the screen-pixel grid (so no row is dropped).
        int gs = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        g.pose().pushMatrix();
        g.pose().translate(this.leftPos + 34, Math.round((this.topPos + SETTING_Y + (FIELD_H - 8 * scale) / 2f) * gs) / (float) gs);
        g.pose().scale(scale, scale);
        int room = (int) ((field ? FIELD_X - 38 : 134) / scale);
        g.text(this.font, this.font.plainSubstrByWidth(label.getString(), room), 0, 0, bright ? LABEL : LABEL_DIM, false);
        g.pose().popMatrix();
        ItemStack subject = subject();
        var registries = net.minecraft.client.Minecraft.getInstance().level == null ? null : net.minecraft.client.Minecraft.getInstance().level.registryAccess();
        List<AssemblyStats.Stat> stats = subject.isEmpty() || registries == null ? List.of()
                : missiles ? AssemblyStats.missile(subject, registries) : AssemblyStats.warhead(subject, registries);
        this.column.draw(g, this.font, this.leftPos, this.topPos, subject, stats,
                Component.translatable(missiles ? AssemblyStats.EMPTY_MISSILE : AssemblyStats.EMPTY_WARHEAD), mouseX, mouseY);
        drawTabs(g, mouseX, mouseY);
    }

    /**
     * What the column shows: on the warhead page the warhead the core makes (with the fitted fuze and its setting), on
     * the missile page the missile the parts make once there is a warhead, a body and a motor; otherwise whatever waits in
     * the page's output slot (empty: nothing).
     */
    private ItemStack subject() {
        var inv = this.menu.machine().inventory();
        if (page() == AssemblyBenchBlockEntity.JOB_MISSILE) {
            ItemStack warhead = inv.getItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD);
            if (WarheadAssembly.isWarhead(warhead) && inv.getItem(AssemblyBenchBlockEntity.SLOT_BODY).getItem() instanceof MissileParts.Body body
                    && inv.getItem(AssemblyBenchBlockEntity.SLOT_MOTOR).getItem() instanceof MissileParts.MotorItem motor) {
                return MissileAssembly.assemble(warhead, body, motor, inv.getItem(AssemblyBenchBlockEntity.SLOT_GUIDANCE),
                        inv.getItem(AssemblyBenchBlockEntity.SLOT_MODULE));
            }
            return inv.getItem(AssemblyBenchBlockEntity.SLOT_MISSILE);
        }
        if (inv.getItem(AssemblyBenchBlockEntity.SLOT_CORE).getItem() instanceof ComponentItems.PayloadCore core) {
            return inv.getItem(AssemblyBenchBlockEntity.SLOT_FUZE).getItem() instanceof ComponentItems.FuzeItem fuze
                    ? WarheadAssembly.assemble(core, fuze, this.menu.extra(AssemblyBenchBlockEntity.DATA_FUZE_VALUE)) : new ItemStack(core.warhead());
        }
        return inv.getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
    }

    /** The two page tabs on the left edge: the shown page's tab joins the window. */
    private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (int p = 0; p < 2; p++) {
            int x = this.leftPos - TAB_W, y = this.topPos + TAB_Y + p * TAB_STEP;
            boolean on = p == page();
            if (on) {
                g.blit(RenderPipelines.GUI_TEXTURED, TABS, x, y, 0, 0, TAB_W + 2, TAB_H, 64, 64);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, TABS, this.leftPos - TAB_OFF_W, y, 0, TAB_OFF_V, TAB_OFF_W, TAB_H, 64, 64);
            }
            ItemStack icon = p == AssemblyBenchBlockEntity.JOB_MISSILE ? new ItemStack(GZAssembly.MISSILE.get())
                    : new ItemStack(GZOrdnance.HE_WARHEAD.item().get());
            g.item(icon, on ? x + 6 : this.leftPos - TAB_OFF_W + 5, y + 4);
            if (mouseX >= x && mouseX < this.leftPos && mouseY >= y && mouseY < y + TAB_H) {
                g.setTooltipForNextFrame(Component.translatable(p == AssemblyBenchBlockEntity.JOB_MISSILE ? T_PAGE_MISSILES : T_PAGE_WARHEADS), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.column.mouseClicked(this.leftPos, this.topPos, event.x(), event.y(), this.menu.getCarried().isEmpty())) {
            return true;
        }
        for (int p = 0; p < 2; p++) {
            int x = this.leftPos - TAB_W, y = this.topPos + TAB_Y + p * TAB_STEP;
            if (event.x() >= x && event.x() < this.leftPos && event.y() >= y && event.y() < y + TAB_H) {
                if (p != page()) {
                    showPage(p);
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.column.mouseDragged(dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.column.mouseReleased() || super.mouseReleased(event);
    }

    /** The page tabs, so JEI keeps clear of them. */
    @Override
    public List<Rect2i> extraAreas() {
        List<Rect2i> areas = new ArrayList<>(super.extraAreas());
        areas.add(new Rect2i(this.leftPos - TAB_W, this.topPos + TAB_Y, TAB_W, TAB_STEP + TAB_H));
        return areas;
    }

    /** While the setting is being typed in, keys go to it (so "E" does not close the screen). */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.field != null && this.field.visible && this.field.isFocused() && !event.isEscape()) {
            return this.field.keyPressed(event) || this.field.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }
}
