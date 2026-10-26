package dev.groundzero.content.radiation;

import dev.groundzero.registry.GZSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The Lead Container's inside (DESIGN.md §21.8): nine places for radioactive items in a 3x3 grid over the player's
 * inventory. The container itself stays put while it is open; its contents are written back to it as they change.
 */
public class LeadContainerMenu extends AbstractContainerMenu {
    /** Where the grid (under the window's header strip) and the inventory lie in the screen. */
    public static final int GRID_X = 62, GRID_Y = 19, INVENTORY_X = 8, INVENTORY_Y = 84;

    private final InteractionHand hand;
    private final ItemStack stack;
    private final SimpleContainer items;
    private final boolean server;

    /** The server's: opened from the container in {@code hand}. */
    public LeadContainerMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, hand, true);
    }

    /** The client's, from the hand sent with it. */
    public LeadContainerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, data.readEnum(InteractionHand.class), false);
    }

    private LeadContainerMenu(int id, Inventory inventory, InteractionHand hand, boolean server) {
        super(RadiationItems.LEAD_CONTAINER_MENU.get(), id);
        this.hand = hand;
        this.server = server;
        this.stack = inventory.player.getItemInHand(hand);
        this.items = new SimpleContainer(LeadContainerItem.SLOTS) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack s) {
                return holds(s);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                save();
            }
        };
        NonNullList<ItemStack> held = NonNullList.withSize(LeadContainerItem.SLOTS, ItemStack.EMPTY);
        this.stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(held);
        for (int i = 0; i < held.size(); i++) {
            this.items.setItem(i, held.get(i));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(this.items, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack s) {
                        return holds(s);
                    }
                });
            }
        }
        int locked = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(col == locked ? new LockedSlot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58)
                    : new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    /** Whether the container takes {@code s}: radioactive items only. */
    public static boolean holds(ItemStack s) {
        return !s.isEmpty() && Emitters.radioactivity(s.getItem()) > 0;
    }

    private void save() {
        if (!this.server) {
            return;
        }
        NonNullList<ItemStack> list = NonNullList.withSize(LeadContainerItem.SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < LeadContainerItem.SLOTS; i++) {
            list.set(i, this.items.getItem(i).copy());
        }
        this.stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moving = slot.getItem();
        ItemStack copy = moving.copy();
        int grid = LeadContainerItem.SLOTS;
        if (index < grid) {
            if (!moveItemStackTo(moving, grid, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!holds(moving) || !moveItemStackTo(moving, 0, grid, false)) {
            return ItemStack.EMPTY;
        }
        if (moving.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.server || player.getItemInHand(this.hand) == this.stack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.server) {
            save();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), GZSounds.LEAD_CONTAINER_CLOSE.get(),
                    SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    /** The slot holding the open container: it can be neither taken out nor swapped. */
    private static final class LockedSlot extends Slot {
        LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
