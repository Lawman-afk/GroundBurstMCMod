package dev.groundburst.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.strategic.SpecialWeapons;
import dev.groundburst.content.strategic.SpecialWeaponsBlockEntity;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Special Weapons Assembler's screen (DESIGN.md §16.15), the ICBM Assembly Hall's in its layout: the exotic core over
 * the missile's parts (booster, and an optional guidance unit and module), the arrow and the finished missile; the
 * readout under them; ASSEMBLE (lit when the parts are there) and TAKE APART (lit when a finished missile waits and the
 * part slots are clear). On the right, the bench's column ({@link AssemblyColumn}): the missile the parts make, or the one
 * waiting, in 3D over its figures.
 */
public class SpecialWeaponsScreen extends MachineScreen {
    private static final String T_EMPTY = GBLang.add(GroundBurst.MODID + ".special_weapons.empty", "Fit a core and a booster to see the missile's figures");
    /** Where the buttons sit (gui.py's "special_weapons" layout leaves room for them, as the hall's does). */
    public static final int BUTTONS_X = StrategicAssemblyScreen.BUTTONS_X, BUTTONS_Y = StrategicAssemblyScreen.BUTTONS_Y;

    private final AssemblyColumn column = new AssemblyColumn(183, 22, 94, 66, 183, 94, 94, 108);
    private @Nullable Button assemble, takeApart;

    public SpecialWeaponsScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int aw = StrategicAssemblyScreen.ASSEMBLE_W, pw = StrategicAssemblyScreen.APART_W, h = StrategicAssemblyScreen.BUTTON_H;
        this.assemble = addRenderableWidget(Button.builder(Component.translatable(AssemblyBenchScreen.T_ASSEMBLE),
                b -> press(SpecialWeaponsBlockEntity.BUTTON_ASSEMBLE)).bounds(this.leftPos + BUTTONS_X, this.topPos + BUTTONS_Y, aw, h).build());
        this.takeApart = addRenderableWidget(Button.builder(Component.translatable(AssemblyBenchScreen.T_TAKE_APART),
                b -> press(SpecialWeaponsBlockEntity.BUTTON_TAKE_APART))
                .bounds(this.leftPos + BUTTONS_X + aw + 3, this.topPos + BUTTONS_Y, pw, h).build());
    }

    private void press(int button) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_MACHINE + button);
        }
    }

    private ItemStack slot(int i) {
        return this.menu.machine().inventory().getItem(i);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.assemble == null || this.takeApart == null) {
            return;
        }
        boolean working = this.menu.extra(SpecialWeaponsBlockEntity.DATA_TOTAL) > 0;
        this.assemble.active = !working && this.menu.extra(SpecialWeaponsBlockEntity.DATA_PROBLEM) == SpecialWeapons.Problem.NONE.ordinal();
        boolean partsClear = true;
        for (int i = SpecialWeaponsBlockEntity.SLOT_CORE; i <= SpecialWeaponsBlockEntity.SLOT_MODULE; i++) {
            partsClear &= slot(i).isEmpty();
        }
        this.takeApart.active = !working && partsClear && !slot(SpecialWeaponsBlockEntity.SLOT_OUTPUT).isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        ItemStack subject = subject();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        List<AssemblyStats.Stat> stats = subject.isEmpty() || level == null ? List.of() : AssemblyStats.missile(subject, level.registryAccess());
        this.column.draw(g, this.font, this.leftPos, this.topPos, subject, stats, Component.translatable(T_EMPTY), mouseX, mouseY);
    }

    /** What the column shows: the missile the parts make once they are all there, otherwise whatever waits in the output. */
    private ItemStack subject() {
        boolean parts = SpecialWeapons.check(slot(SpecialWeaponsBlockEntity.SLOT_CORE), slot(SpecialWeaponsBlockEntity.SLOT_BOOSTER), ItemStack.EMPTY)
                == SpecialWeapons.Problem.NONE;
        return parts ? SpecialWeapons.assemble(slot(SpecialWeaponsBlockEntity.SLOT_CORE), slot(SpecialWeaponsBlockEntity.SLOT_GUIDANCE),
                slot(SpecialWeaponsBlockEntity.SLOT_MODULE)) : slot(SpecialWeaponsBlockEntity.SLOT_OUTPUT);
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
}
