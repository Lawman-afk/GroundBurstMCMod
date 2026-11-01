package dev.groundburst.content.assembly;

import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.registry.GBDataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A MIRV bus opened (DESIGN.md §17.14): its mounts, two rows over the player's inventory, each taking one re-entry vehicle
 * the bus takes while its load allows; and its spread, set with its screen's buttons ({@link #clickMenuButton}). The
 * bus stays put while it is open; what it holds and its spread are written back to it as they change.
 */
public class MirvBusMenu extends AbstractContainerMenu {
    /** Where the bus's deck (the first of four columns of mounts, its top row), the inventory and the hotbar lie in the screen. */
    public static final int MOUNTS_X = 13, MOUNTS_Y = 27, INVENTORY_X = 8, INVENTORY_Y = 91;
    /** The screen's buttons: the next pattern, the spacing down or up a step, or a big step. */
    public static final int NEXT_PATTERN = 0, CLOSER = 1, WIDER = 2, MUCH_CLOSER = 3, MUCH_WIDER = 4;

    private final InteractionHand hand;
    private final ItemStack stack;
    private final SizeClass size;
    private final int mounts;
    private final SimpleContainer items;
    /** The spread, for the screen: the pattern's ordinal and the spacing. */
    private final ContainerData spread;
    private final boolean server;

    /** The server's: opened from the bus in {@code hand}. */
    public MirvBusMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, hand, classOf(inventory.player.getItemInHand(hand)), true);
    }

    /**
     * The client's, from the hand and the bus's class sent with it ({@link #write}): what the client holds may not be
     * the bus yet, and its mounts must match the server's slot for slot.
     */
    public MirvBusMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, data.readEnum(InteractionHand.class), data.readEnum(SizeClass.class), false);
    }

    /** What opening the menu sends its client: the hand the bus is in and its class. */
    public static void write(RegistryFriendlyByteBuf buf, InteractionHand hand, SizeClass size) {
        buf.writeEnum(hand);
        buf.writeEnum(size);
    }

    private static SizeClass classOf(ItemStack bus) {
        SizeClass s = Mirv.classOf(bus);
        return s == null ? SizeClass.L : s;
    }

    private MirvBusMenu(int id, Inventory inventory, InteractionHand hand, SizeClass size, boolean server) {
        super(GBAssembly.MIRV_BUS_MENU.get(), id);
        this.hand = hand;
        this.server = server;
        this.stack = inventory.player.getItemInHand(hand);
        this.size = size;
        this.mounts = Mirv.mounts(this.size);
        this.items = new SimpleContainer(this.mounts) {
            @Override
            public void setChanged() {
                super.setChanged();
                save();
            }
        };
        NonNullList<ItemStack> held = Mirv.mountsOf(this.stack);
        for (int i = 0; i < held.size() && i < this.mounts; i++) {
            this.items.setItem(i, held.get(i));
        }
        Mirv.Spread sp = Mirv.spread(this.stack);
        this.spread = new SimpleContainerData(2);
        this.spread.set(0, sp.pattern().ordinal());
        this.spread.set(1, sp.spacing());
        addDataSlots(this.spread);
        for (int i = 0; i < this.mounts; i++) {
            final int index = i;
            addSlot(new Slot(this.items, i, mountX(this.mounts, i), mountY(this.mounts, i)) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return fits(index, s);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
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

    /** Where mount {@code i} of a bus of {@code mounts} lies (two rows, centred on the deck's four columns). */
    public static int mountX(int mounts, int i) {
        int cols = mounts / 2;
        return MOUNTS_X + (4 - cols) * 9 + (i % cols) * 18;
    }

    public static int mountY(int mounts, int i) {
        return MOUNTS_Y + (i / (mounts / 2)) * 18;
    }

    public SizeClass size() {
        return this.size;
    }

    public int mounts() {
        return this.mounts;
    }

    /** How much of its load the bus carries now. */
    public int load() {
        int sum = 0;
        for (int i = 0; i < this.mounts; i++) {
            sum += Mirv.load(this.size, this.items.getItem(i));
        }
        return sum;
    }

    public LaunchNetwork.Pattern pattern() {
        LaunchNetwork.Pattern[] all = LaunchNetwork.Pattern.values();
        return all[Math.clamp(this.spread.get(0), 0, all.length - 1)];
    }

    public int spacing() {
        return this.spread.get(1);
    }

    /** Whether mount {@code index} takes {@code s}: an RV this bus takes, with room in its load for it (in place of what is there). */
    public boolean fits(int index, ItemStack s) {
        int cost = Mirv.load(this.size, s);
        return cost > 0 && load() - Mirv.load(this.size, this.items.getItem(index)) + cost <= Mirv.capacity(this.size);
    }

    private void save() {
        if (!this.server) {
            return;
        }
        NonNullList<ItemStack> list = NonNullList.withSize(this.mounts, ItemStack.EMPTY);
        for (int i = 0; i < this.mounts; i++) {
            list.set(i, this.items.getItem(i).copy());
        }
        this.stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        LaunchNetwork.Pattern pattern = pattern();
        int spacing = spacing();
        switch (id) {
            case NEXT_PATTERN -> pattern = LaunchNetwork.Pattern.values()[(pattern.ordinal() + 1) % LaunchNetwork.Pattern.values().length];
            case CLOSER -> spacing -= Mirv.STEP;
            case WIDER -> spacing += Mirv.STEP;
            case MUCH_CLOSER -> spacing -= Mirv.BIG_STEP;
            case MUCH_WIDER -> spacing += Mirv.BIG_STEP;
            default -> {
                return false;
            }
        }
        Mirv.Spread s = Mirv.Spread.of(pattern, spacing);
        this.spread.set(0, s.pattern().ordinal());
        this.spread.set(1, s.spacing());
        if (this.server) {
            this.stack.set(GBDataComponents.MIRV_SPREAD.get(), s);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moving = slot.getItem();
        ItemStack copy = moving.copy();
        if (index < this.mounts) {
            if (!moveItemStackTo(moving, this.mounts, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(moving, 0, this.mounts, false)) {
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
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS,
                    0.6f, 1.3f);
        }
    }

    /** The slot holding the open bus: it can be neither taken out nor swapped. */
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
