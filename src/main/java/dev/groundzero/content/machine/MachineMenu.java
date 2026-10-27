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
    public static final int BUTTON_AUTO_EJECT = 1;
    /** + face ordinal: next / previous item mode; toggle energy. */
    public static final int BUTTON_ITEMS_NEXT = 10, BUTTON_ITEMS_PREV = 20, BUTTON_ENERGY = 30, BUTTON_FLUID = 40;
    /** + tank index: empty that tank. */
    public static final int BUTTON_DUMP = 50;
    /** + n (up to 29): a machine's own control button n (e.g. reactor control rods), see MachineBlockEntity#handleButton. */
    public static final int BUTTON_MACHINE = 70;
    /** Added to a face button (items, energy, fluid) to mean a two-block machine's second half (side or upper). */
    public static final int BUTTON_SIDE_HALF = 100;

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
            addSlot(new MachineSlot(container, i, p.x(), p.y(), this));
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

    public int tankAmount(int tank) {
        return data.get(MachineBlockEntity.DATA_TANKS + tank * 2);
    }

    /** A tank's fluid as last synced (empty fluid when the tank is empty). */
    public net.minecraft.world.level.material.Fluid tankFluid(int tank) {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(data.get(MachineBlockEntity.DATA_TANKS + tank * 2 + 1));
    }

    /** Machine-specific value (see each block entity's getExtraData). */
    public int extra(int index) {
        return data.get(MachineBlockEntity.DATA_BASE_COUNT + index);
    }

    /** The side configuration as last synced (client) or live (server). */
    public SideConfig sides() {
        SideConfig config = new SideConfig();
        config.unpack(data.get(MachineBlockEntity.DATA_SIDE_ITEMS), data.get(MachineBlockEntity.DATA_SIDE_FLAGS));
        return config;
    }

    /** A two-block machine's second half's faces (a wide one's side half, a tall one's top), as last synced or live. */
    public SideConfig sideHalf() {
        SideConfig config = new SideConfig();
        config.unpack(data.get(MachineBlockEntity.DATA_SIDE2_ITEMS), data.get(MachineBlockEntity.DATA_SIDE2_FLAGS));
        return config;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            this.machine.cycleRedstoneMode();
            return true;
        }
        if (id == BUTTON_AUTO_EJECT) {
            this.machine.changeSides(SideConfig::toggleAutoEject);
            return true;
        }
        if (id >= BUTTON_MACHINE && id < BUTTON_SIDE_HALF) {
            return this.machine.handleButton(id - BUTTON_MACHINE);
        }
        if (id >= BUTTON_DUMP && id < BUTTON_DUMP + MachineBlockEntity.MAX_TANKS) {
            this.machine.dumpTank(id - BUTTON_DUMP);
            return true;
        }
        int faces = SideConfig.Face.values().length;
        boolean half = id >= BUTTON_SIDE_HALF && this.machine.twoBlock();
        if (half) {
            id -= BUTTON_SIDE_HALF;
        }
        for (int base : new int[] {BUTTON_ITEMS_NEXT, BUTTON_ITEMS_PREV, BUTTON_ENERGY, BUTTON_FLUID}) {
            if (id >= base && id < base + faces) {
                SideConfig.Face face = SideConfig.Face.values()[id - base];
                if (!this.machine.faceOpen(face, half)) {
                    return false;                      // covered by the machine's own structure
                }
                this.machine.changeSides(half, c -> {
                    if (base == BUTTON_ENERGY) {
                        c.toggleEnergy(face);
                    } else if (base == BUTTON_FLUID) {
                        c.toggleFluid(face);
                    } else {
                        c.cycleItems(face, base == BUTTON_ITEMS_NEXT, this.machine.allowedItemModes());
                    }
                });
                return true;
            }
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

    /**
     * Which machine slots the menu offers, on both sides: a machine with pages (the Assembly Bench) hides the other page's
     * slots. Hidden slots are neither drawn nor clickable, and shift-clicks pass them by.
     */
    public boolean slotShown(int slot) {
        return this.machine.slotShown(slot, this);
    }

    /** A slot that respects the machine's per-slot rules (vanilla slots accept anything). */
    private static class MachineSlot extends Slot {
        private final MachineMenu menu;

        MachineSlot(Container container, int index, int x, int y, MachineMenu menu) {
            super(container, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return this.menu.slotShown(getContainerSlot());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return (isActive() || this.menu.machine().slotTakesHidden(getContainerSlot())) && !locked()
                    && this.container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked() && super.mayPickup(player);
        }

        private boolean locked() {
            return this.menu.machine().slotLocked(getContainerSlot(), this.menu);
        }

        @Override
        public int getMaxStackSize() {
            return this.container instanceof MachineInventory inv ? Math.min(super.getMaxStackSize(), inv.slotLimit(getContainerSlot()))
                    : super.getMaxStackSize();
        }
    }
}
