package dev.groundburst.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.AssemblyBenchBlockEntity;
import dev.groundburst.content.assembly.AssemblyPayloads;
import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.ComponentItems;
import dev.groundburst.content.assembly.Fuze;
import dev.groundburst.content.assembly.WarheadAssembly;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Ordnance Assembly Bench's screen (DESIGN.md §10): core, casing, fuze; the fitted fuze's setting under the readout
 * (a barometric fuze's burst height); ASSEMBLE (lit when the parts fit) and TAKE APART (lit when a finished warhead
 * waits and the part slots are clear). The right-hand column (slice 7c, {@link AssemblyColumn}) shows the warhead the
 * parts make, or the one waiting in the output slot: a 3D preview (turning by itself, or dragged round) over a stats
 * screen ({@link AssemblyStats}). (Its missile page went to the missile assemblers, §10.4.)
 */
public class AssemblyBenchScreen extends MachineScreen {
    static final String T_ASSEMBLE = GBLang.add(GroundBurst.MODID + ".assembly.assemble", "Assemble");
    static final String T_TAKE_APART = GBLang.add(GroundBurst.MODID + ".assembly.take_apart", "Take Apart");
    private static final String T_NO_FUZE = GBLang.add(GroundBurst.MODID + ".assembly.no_fuze", "Fit a fuze to set it");
    private static final String T_NO_SETTING = GBLang.add(GroundBurst.MODID + ".assembly.no_setting", "Impact fuze: nothing to set");
    /** Where the setting row and the buttons sit on the panel (gui.py draws the bench layout around them). */
    public static final int SETTING_Y = 79, FIELD_X = 118, FIELD_W = 50, FIELD_H = 14;
    public static final int BUTTONS_X = 31, BUTTONS_Y = 98, ASSEMBLE_W = 54, APART_W = 60, BUTTON_H = 18;
    static final int LABEL = 0xFFD8D4C4, LABEL_DIM = 0xFF8A8E94;

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
        this.assemble = addRenderableWidget(Button.builder(Component.translatable(T_ASSEMBLE), b -> press(AssemblyBenchBlockEntity.BUTTON_ASSEMBLE))
                .bounds(this.leftPos + BUTTONS_X, this.topPos + BUTTONS_Y, ASSEMBLE_W, BUTTON_H).build());
        this.takeApart = addRenderableWidget(Button.builder(Component.translatable(T_TAKE_APART), b -> press(AssemblyBenchBlockEntity.BUTTON_TAKE_APART))
                .bounds(this.leftPos + BUTTONS_X + ASSEMBLE_W + 3, this.topPos + BUTTONS_Y, APART_W, BUTTON_H).build());
    }

    private AssemblyBenchBlockEntity bench() {
        return (AssemblyBenchBlockEntity) this.menu.machine();
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
        this.assemble.active = !working && this.menu.extra(AssemblyBenchBlockEntity.DATA_PROBLEM) == WarheadAssembly.Problem.NONE.ordinal();
        ItemStack out = inv.getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
        this.takeApart.active = !working && !out.isEmpty()
                && empty(AssemblyBenchBlockEntity.SLOT_CORE, AssemblyBenchBlockEntity.SLOT_CASING, AssemblyBenchBlockEntity.SLOT_FUZE);
        // The field follows the fitted fuze: shown only for a fuze with a setting, refreshed from the bench unless being
        // typed in.
        Fuze fuze = bench().fittedFuze();
        boolean settable = fuze != null && fuze.setting != Fuze.Setting.NONE;
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
        Fuze fuze = bench().fittedFuze();
        boolean bright = fuze != null && fuze.setting != Fuze.Setting.NONE;
        Component label = fuze == null ? Component.translatable(T_NO_FUZE) : !bright ? Component.translatable(T_NO_SETTING)
                : Component.translatable(fuze.setting.label);
        smallLabel(g, this.font, this.leftPos, this.topPos, label, bright, bright ? FIELD_X - 38 : 134);
        ItemStack subject = subject();
        var registries = net.minecraft.client.Minecraft.getInstance().level == null ? null : net.minecraft.client.Minecraft.getInstance().level.registryAccess();
        List<AssemblyStats.Stat> stats = subject.isEmpty() || registries == null ? List.of() : AssemblyStats.warhead(subject, registries);
        this.column.draw(g, this.font, this.leftPos, this.topPos, subject, stats, Component.translatable(AssemblyStats.EMPTY_WARHEAD), mouseX, mouseY);
    }

    /**
     * The small label on the setting row (the bench's fuze setting, the assemblers' guidance): small type, each font
     * pixel a whole number of screen pixels on the screen-pixel grid (so no row is dropped), cut to {@code width}.
     */
    static void smallLabel(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int left, int top, Component label, boolean bright, int width) {
        int gs = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        g.pose().pushMatrix();
        g.pose().translate(left + 34, Math.round((top + SETTING_Y + (FIELD_H - 8 * scale) / 2f) * gs) / (float) gs);
        g.pose().scale(scale, scale);
        int room = (int) (width / scale);
        g.text(font, font.plainSubstrByWidth(label.getString(), room), 0, 0, bright ? LABEL : LABEL_DIM, false);
        g.pose().popMatrix();
    }

    /**
     * What the column shows: the warhead the core makes (with the fitted fuze and its setting), otherwise whatever waits
     * in the output slot (empty: nothing).
     */
    private ItemStack subject() {
        var inv = this.menu.machine().inventory();
        if (inv.getItem(AssemblyBenchBlockEntity.SLOT_CORE).getItem() instanceof ComponentItems.PayloadCore core) {
            return inv.getItem(AssemblyBenchBlockEntity.SLOT_FUZE).getItem() instanceof ComponentItems.FuzeItem fuze
                    ? WarheadAssembly.assemble(core, fuze, this.menu.extra(AssemblyBenchBlockEntity.DATA_FUZE_VALUE)) : new ItemStack(core.warhead());
        }
        return inv.getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.column.mouseClicked(this.leftPos, this.topPos, event.x(), event.y(), this.menu.getCarried().isEmpty())
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.column.mouseDragged(dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.column.mouseReleased() || super.mouseReleased(event);
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
