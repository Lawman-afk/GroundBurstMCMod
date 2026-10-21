package dev.groundzero.client.screen;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.AssemblyPayloads;
import dev.groundzero.content.assembly.Fuze;
import dev.groundzero.content.assembly.WarheadAssembly;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Ordnance Assembly Bench's screen (DESIGN.md §10): the machine screen with the fitted fuze's setting under the
 * readout (its seconds, range, frequency or burst height; the warhead carries it into the missile), and two buttons,
 * ASSEMBLE (lit when the parts on the bench fit) and TAKE APART (lit when a warhead waits and the part slots are clear).
 */
public class AssemblyBenchScreen extends MachineScreen {
    private static final String T_ASSEMBLE = GZLang.add(GroundZero.MODID + ".assembly.assemble", "Assemble");
    private static final String T_TAKE_APART = GZLang.add(GroundZero.MODID + ".assembly.take_apart", "Take Apart");
    private static final String T_NO_FUZE = GZLang.add(GroundZero.MODID + ".assembly.no_fuze", "Fit a fuze to set it");
    private static final String T_NO_SETTING = GZLang.add(GroundZero.MODID + ".assembly.no_setting", "Impact fuze: nothing to set");
    /** Where the setting row and the buttons sit on the panel (gui.py draws the bench layout around them). */
    public static final int SETTING_Y = 79, FIELD_X = 118, FIELD_W = 50, FIELD_H = 14;
    public static final int BUTTONS_X = 31, BUTTONS_Y = 98, ASSEMBLE_W = 54, APART_W = 60, BUTTON_H = 18;
    private static final int LABEL = 0xFFD8D4C4, LABEL_DIM = 0xFF8A8E94;

    private @Nullable Button assemble, takeApart;
    private @Nullable EditBox field;
    private @Nullable Fuze shown;

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
                b -> press(AssemblyBenchBlockEntity.BUTTON_ASSEMBLE)).bounds(this.leftPos + BUTTONS_X, this.topPos + BUTTONS_Y, ASSEMBLE_W, BUTTON_H).build());
        this.takeApart = addRenderableWidget(Button.builder(Component.translatable(T_TAKE_APART),
                b -> press(AssemblyBenchBlockEntity.BUTTON_TAKE_APART))
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

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.assemble == null || this.takeApart == null || this.field == null) {
            return;
        }
        var bench = this.menu.machine().inventory();
        boolean working = this.menu.extra(AssemblyBenchBlockEntity.DATA_TOTAL) > 0;
        this.assemble.active = !working && this.menu.extra(AssemblyBenchBlockEntity.DATA_PROBLEM) == WarheadAssembly.Problem.NONE.ordinal();
        ItemStack out = bench.getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
        this.takeApart.active = !working && !out.isEmpty() && bench.getItem(AssemblyBenchBlockEntity.SLOT_CORE).isEmpty()
                && bench.getItem(AssemblyBenchBlockEntity.SLOT_CASING).isEmpty() && bench.getItem(AssemblyBenchBlockEntity.SLOT_FUZE).isEmpty();
        // The field follows the fitted fuze: shown only for a fuze with a setting, refreshed from the bench unless being typed in.
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
        boolean settable = fuze != null && fuze.setting != Fuze.Setting.NONE;
        Component label = fuze == null ? Component.translatable(T_NO_FUZE) : !settable ? Component.translatable(T_NO_SETTING)
                : Component.translatable(fuze.setting.label);
        // Small type, each font pixel a whole number of screen pixels on the screen-pixel grid (so no row is dropped).
        int gs = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        g.pose().pushMatrix();
        g.pose().translate(this.leftPos + 34, Math.round((this.topPos + SETTING_Y + (FIELD_H - 8 * scale) / 2f) * gs) / (float) gs);
        g.pose().scale(scale, scale);
        int room = (int) ((settable ? FIELD_X - 38 : 134) / scale);
        g.text(this.font, this.font.plainSubstrByWidth(label.getString(), room), 0, 0, settable ? LABEL : LABEL_DIM, false);
        g.pose().popMatrix();
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
