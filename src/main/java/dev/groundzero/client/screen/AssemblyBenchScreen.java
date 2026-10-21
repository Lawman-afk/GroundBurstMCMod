package dev.groundzero.client.screen;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.WarheadAssembly;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.registry.GZLang;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Ordnance Assembly Bench's screen (DESIGN.md §10): the machine screen with two buttons under the readout,
 * ASSEMBLE (lit when the parts on the bench fit) and TAKE APART (lit when a warhead waits and the part slots are clear).
 */
public class AssemblyBenchScreen extends MachineScreen {
    private static final String T_ASSEMBLE = GZLang.add(GroundZero.MODID + ".assembly.assemble", "Assemble");
    private static final String T_TAKE_APART = GZLang.add(GroundZero.MODID + ".assembly.take_apart", "Take Apart");
    /** Where the buttons sit on the panel (gui.py draws the bench layout around them). */
    public static final int BUTTONS_X = 31, BUTTONS_Y = 81, ASSEMBLE_W = 56, APART_W = 57, BUTTON_H = 18;

    private @Nullable Button assemble, takeApart;

    public AssemblyBenchScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.assemble = addRenderableWidget(Button.builder(Component.translatable(T_ASSEMBLE),
                b -> press(AssemblyBenchBlockEntity.BUTTON_ASSEMBLE)).bounds(this.leftPos + BUTTONS_X, this.topPos + BUTTONS_Y, ASSEMBLE_W, BUTTON_H).build());
        this.takeApart = addRenderableWidget(Button.builder(Component.translatable(T_TAKE_APART),
                b -> press(AssemblyBenchBlockEntity.BUTTON_TAKE_APART))
                .bounds(this.leftPos + BUTTONS_X + ASSEMBLE_W + 4, this.topPos + BUTTONS_Y, APART_W, BUTTON_H).build());
    }

    private void press(int button) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_MACHINE + button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.assemble == null || this.takeApart == null) {
            return;
        }
        var bench = this.menu.machine().inventory();
        boolean working = this.menu.extra(AssemblyBenchBlockEntity.DATA_TOTAL) > 0;
        this.assemble.active = !working && this.menu.extra(AssemblyBenchBlockEntity.DATA_PROBLEM) == WarheadAssembly.Problem.NONE.ordinal();
        ItemStack out = bench.getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
        this.takeApart.active = !working && !out.isEmpty() && bench.getItem(AssemblyBenchBlockEntity.SLOT_CORE).isEmpty()
                && bench.getItem(AssemblyBenchBlockEntity.SLOT_CASING).isEmpty() && bench.getItem(AssemblyBenchBlockEntity.SLOT_FUZE).isEmpty();
    }
}
