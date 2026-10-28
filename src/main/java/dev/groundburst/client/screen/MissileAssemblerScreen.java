package dev.groundburst.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembler.MissileAssemblerBlockEntity;
import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A missile assembler's screen (DESIGN.md §10.4; the Ordnance Assembly Bench's missile page as was): warhead, body,
 * motor, guidance, module, the arrow and the missile; the readout; how close the guidance lands it; ASSEMBLE (lit when
 * the parts fit) and TAKE APART (lit when a finished missile waits and the part slots are clear). On the right, the
 * bench's column ({@link AssemblyColumn}): the missile the parts make, or the one waiting, in 3D over its figures.
 */
public class MissileAssemblerScreen extends MachineScreen {
    private static final String T_UNGUIDED = GBLang.add(GroundBurst.MODID + ".assembler.unguided", "Unguided: within 25% of the distance");

    private final AssemblyColumn column = new AssemblyColumn(183, 22, 94, 80, 183, 110, 94, 92);
    private @Nullable Button assemble, takeApart;

    public MissileAssemblerScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + AssemblyBenchScreen.BUTTONS_X, y = this.topPos + AssemblyBenchScreen.BUTTONS_Y;
        this.assemble = addRenderableWidget(Button.builder(Component.translatable(AssemblyBenchScreen.T_ASSEMBLE),
                b -> press(MissileAssemblerBlockEntity.BUTTON_ASSEMBLE)).bounds(x, y, AssemblyBenchScreen.ASSEMBLE_W, AssemblyBenchScreen.BUTTON_H).build());
        this.takeApart = addRenderableWidget(Button.builder(Component.translatable(AssemblyBenchScreen.T_TAKE_APART),
                b -> press(MissileAssemblerBlockEntity.BUTTON_TAKE_APART))
                .bounds(x + AssemblyBenchScreen.ASSEMBLE_W + 3, y, AssemblyBenchScreen.APART_W, AssemblyBenchScreen.BUTTON_H).build());
    }

    private void press(int button) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_MACHINE + button);
        }
    }

    private MissileAssemblerBlockEntity assembler() {
        return (MissileAssemblerBlockEntity) this.menu.machine();
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
        boolean working = this.menu.extra(MissileAssemblerBlockEntity.DATA_TOTAL) > 0;
        this.assemble.active = !working && this.menu.extra(MissileAssemblerBlockEntity.DATA_PROBLEM) == MissileAssembly.Problem.NONE.ordinal();
        boolean partsClear = true;
        for (int i = MissileAssemblerBlockEntity.SLOT_WARHEAD; i <= MissileAssemblerBlockEntity.SLOT_MODULE; i++) {
            partsClear &= slot(i).isEmpty();
        }
        this.takeApart.active = !working && partsClear && !slot(MissileAssemblerBlockEntity.SLOT_OUTPUT).isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        ItemStack guidance = slot(MissileAssemblerBlockEntity.SLOT_GUIDANCE);
        boolean guided = guidance.getItem() instanceof MissileParts.GuidanceItem;
        Component label = guidance.getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance.accuracy() : Component.translatable(T_UNGUIDED);
        AssemblyBenchScreen.smallLabel(g, this.font, this.leftPos, this.topPos, label, guided, 134);
        ItemStack subject = subject();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        List<AssemblyStats.Stat> stats = subject.isEmpty() || level == null ? List.of() : AssemblyStats.missile(subject, level.registryAccess());
        this.column.draw(g, this.font, this.leftPos, this.topPos, subject, stats, Component.translatable(AssemblyStats.EMPTY_MISSILE), mouseX, mouseY);
    }

    /** What the column shows: the missile the parts make once there is a warhead, a body and a motor; otherwise what waits in the output. */
    private ItemStack subject() {
        ItemStack makes = assembler().makes();
        return makes != null ? makes : slot(MissileAssemblerBlockEntity.SLOT_OUTPUT);
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
