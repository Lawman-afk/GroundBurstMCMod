package dev.groundzero.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyStats;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.strategic.GZStrategic;
import dev.groundzero.content.strategic.StrategicAssembly;
import dev.groundzero.content.strategic.StrategicAssemblyBlockEntity;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Strategic Assembly Hall's screen (DESIGN.md §15.2): the warhead's parts (primary, secondary and an optional
 * cobalt jacket) over the missile's (booster, and an optional guidance unit and module), the arrow and the finished
 * missile; the readout under them; ASSEMBLE (lit when the parts are there) and TAKE APART (lit when a finished missile
 * waits and the part slots are clear). On the right, the bench's column ({@link AssemblyColumn}): the missile the parts
 * make, or the one waiting, in 3D over its figures.
 */
public class StrategicAssemblyScreen extends MachineScreen {
    private static final String T_EMPTY = GZLang.add(GroundZero.MODID + ".strategic.empty", "Fit a primary, a secondary and a booster to see the missile's figures");
    /** Where the buttons sit (gui.py's "strategic_assembly" layout leaves room for them). */
    public static final int BUTTONS_X = 31, BUTTONS_Y = 94, ASSEMBLE_W = 54, APART_W = 60, BUTTON_H = 18;

    private final AssemblyColumn column = new AssemblyColumn(183, 22, 94, 66, 183, 94, 94, 108);
    private @Nullable Button assemble, takeApart;

    public StrategicAssemblyScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.assemble = addRenderableWidget(Button.builder(Component.translatable(AssemblyBenchScreen.T_ASSEMBLE),
                b -> press(StrategicAssemblyBlockEntity.BUTTON_ASSEMBLE)).bounds(this.leftPos + BUTTONS_X, this.topPos + BUTTONS_Y, ASSEMBLE_W, BUTTON_H).build());
        this.takeApart = addRenderableWidget(Button.builder(Component.translatable(AssemblyBenchScreen.T_TAKE_APART),
                b -> press(StrategicAssemblyBlockEntity.BUTTON_TAKE_APART))
                .bounds(this.leftPos + BUTTONS_X + ASSEMBLE_W + 3, this.topPos + BUTTONS_Y, APART_W, BUTTON_H).build());
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
        boolean working = this.menu.extra(StrategicAssemblyBlockEntity.DATA_TOTAL) > 0;
        this.assemble.active = !working && this.menu.extra(StrategicAssemblyBlockEntity.DATA_PROBLEM) == StrategicAssembly.Problem.NONE.ordinal();
        boolean partsClear = true;
        for (int i = StrategicAssemblyBlockEntity.SLOT_PRIMARY; i <= StrategicAssemblyBlockEntity.SLOT_MODULE; i++) {
            partsClear &= slot(i).isEmpty();
        }
        this.takeApart.active = !working && partsClear && !slot(StrategicAssemblyBlockEntity.SLOT_OUTPUT).isEmpty();
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
        var problem = StrategicAssembly.check(slot(StrategicAssemblyBlockEntity.SLOT_PRIMARY), slot(StrategicAssemblyBlockEntity.SLOT_SECONDARY),
                slot(StrategicAssemblyBlockEntity.SLOT_JACKET), slot(StrategicAssemblyBlockEntity.SLOT_BOOSTER), ItemStack.EMPTY);
        boolean parts = problem == StrategicAssembly.Problem.NONE;
        return parts ? StrategicAssembly.assemble(slot(StrategicAssemblyBlockEntity.SLOT_JACKET), slot(StrategicAssemblyBlockEntity.SLOT_GUIDANCE),
                slot(StrategicAssemblyBlockEntity.SLOT_MODULE)) : slot(StrategicAssemblyBlockEntity.SLOT_OUTPUT);
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
