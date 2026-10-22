package dev.groundzero.content.machine;

import java.util.function.BiPredicate;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A machine's item slots. It is both a NeoForge {@code ResourceHandler} (automation, capabilities) and a vanilla
 * {@link Container} (menu slots, loot contexts for fuel, dropping contents when broken), backed by one list.
 */
public class MachineInventory extends ItemStacksResourceHandler implements Container {
    private final BiPredicate<Integer, ItemStack> validator;
    private final IntConsumer onChanged;
    /** Most items a slot holds (a reactor's fuel channels take one rod each), on top of the item's own stack size. */
    private final IntUnaryOperator limit;

    /** {@code onChanged} receives the slot that changed, or -1 when any slot may have. */
    public MachineInventory(int size, BiPredicate<Integer, ItemStack> validator, IntConsumer onChanged) {
        this(size, validator, onChanged, slot -> Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    public MachineInventory(int size, BiPredicate<Integer, ItemStack> validator, IntConsumer onChanged, IntUnaryOperator limit) {
        super(size);
        this.validator = validator;
        this.onChanged = onChanged;
        this.limit = limit;
    }

    /** Most items slot {@code index} holds, whatever the item. */
    public int slotLimit(int index) {
        return this.limit.applyAsInt(index);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return Math.min(super.getCapacity(index, resource), slotLimit(index));
    }

    /**
     * Loads saved contents but keeps this machine's slot count: NeoForge's handler would adopt the saved list's size,
     * so a machine saved before its slot layout changed would come back with the wrong number of slots.
     */
    @Override
    public void deserialize(net.minecraft.world.level.storage.ValueInput input) {
        int size = this.stacks.size();
        super.deserialize(input);
        if (this.stacks.size() != size) {
            net.minecraft.core.NonNullList<ItemStack> fixed = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(size, this.stacks.size()); i++) {
                fixed.set(i, this.stacks.get(i));
            }
            setStacks(fixed);
        }
    }

    // --- ResourceHandler side ----------------------------------------------------------------------------------
    @Override
    public boolean isValid(int index, ItemResource resource) {
        return this.validator.test(index, resource.toStack(1));
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        this.onChanged.accept(index);
    }

    // --- direct access for machine logic -----------------------------------------------------------------------------
    /** The live stack in a slot. Do not mutate; use {@link #setItem}. */
    @Override
    public ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.stacks.set(index, stack);
        this.onChanged.accept(index);
    }

    /** Inserts into a slot as far as stacking allows; returns what did not fit. */
    public ItemStack insertDirect(int index, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = this.stacks.get(index);
        int limit = Math.min(stack.getMaxStackSize(), Math.min(getMaxStackSize(), slotLimit(index)));
        if (current.isEmpty()) {
            int moved = Math.min(limit, stack.getCount());
            if (!simulate) {
                setItem(index, stack.copyWithCount(moved));
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }
        if (!ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }
        int moved = Math.min(limit - current.getCount(), stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (!simulate) {
            setItem(index, current.copyWithCount(current.getCount() + moved));
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    // --- Container side -------------------------------------------------------------------------------------------------
    @Override
    public int getContainerSize() {
        return this.stacks.size();
    }

    @Override
    public boolean isEmpty() {
        return this.stacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack current = this.stacks.get(index);
        if (current.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = current.copyWithCount(Math.min(count, current.getCount()));
        setItem(index, current.getCount() - taken.getCount() <= 0 ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - taken.getCount()));
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack current = this.stacks.get(index);
        this.stacks.set(index, ItemStack.EMPTY);
        return current;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return this.validator.test(index, stack);
    }

    @Override
    public void setChanged() {
        this.onChanged.accept(-1);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < this.stacks.size(); i++) {
            this.stacks.set(i, ItemStack.EMPTY);
        }
        this.onChanged.accept(-1);
    }
}
