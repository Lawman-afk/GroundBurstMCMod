package dev.groundzero.content.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The menu every machine uses: machine slots from its {@link MachineLayout}, the player inventory, and the
 * machine's {@link ContainerData} (energy, redstone mode, status, plus machine-specific values).
 */
public class MachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_REDSTONE = 0;

    private final MachineBlockEntity machine;
    private final MachineLayout layout;
    private final ContainerData data;
    private final int machineSlots;

    public MachineMenu(MenuType<?> type, int containerId, Inventory playerInventory, MachineBlockEntity machine, MachineLayout layout, ContainerData data) {
        super(type, containerId);
        this.machine = machine;
        this.layout = layout;
        this.data = data;
        Container container = machine.inventory();
        this.machineSlots = layout.slots().size();
        for (int i = 0; i < this.machineSlots; i++) {
            MachineLayout.Pos p = layout.slots().get(i);
            addSlot(new MachineSlot(container, i, p.x(), p.y()));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, layout.inventoryX() + col * 18, layout.inventoryY() + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, layout.inventoryX() + col * 18, layout.inventoryY() + 58));
        }
        addDataSlots(data);
    }

    /** Client-side constructor: the block entity is found from the position sent by the server. */
    public static MachineMenu client(MenuType<?> type, int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf, MachineLayout layout) {
        BlockPos pos = buf.readBlockPos();
        if (!(playerInventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            throw new IllegalStateException("No machine at " + pos);
        }
        return new MachineMenu(type, containerId, playerInventory, machine, layout, new SimpleContainerData(machine.data().getCount()));
    }

    public MachineBlockEntity machine() {
        return machine;
    }

    public MachineLayout layout() {
        return layout;
    }

    public int energy() {
        return (data.get(MachineBlockEntity.DATA_ENERGY_LO) & 0xFFFF) | (data.get(MachineBlockEntity.DATA_ENERGY_HI) << 16);
    }

    public int capacity() {
        return (data.get(MachineBlockEntity.DATA_CAPACITY_LO) & 0xFFFF) | (data.get(MachineBlockEntity.DATA_CAPACITY_HI) << 16);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(MachineBlockEntity.DATA_REDSTONE));
    }

    public MachineStatus status() {
        return MachineStatus.byId(data.get(MachineBlockEntity.DATA_STATUS));
    }

    /** Machine-specific value (see each block entity's getExtraData). */
    public int extra(int index) {
        return data.get(MachineBlockEntity.DATA_BASE_COUNT + index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            this.machine.cycleRedstoneMode();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = this.machineSlots;
        int playerEnd = this.slots.size();
        if (index < this.machineSlots) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, this.machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this.machine, player);
    }

    /** A slot that respects the machine's per-slot rules (vanilla slots accept anything). */
    private static class MachineSlot extends Slot {
        MachineSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
