package dev.groundburst.legacy.component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft 1.21's items of a container kept in an item, slot by slot (1.20.1's "Items" in its block entity data, each
 * with its "Slot").
 */
public final class ItemContainerContents {
    public static final ItemContainerContents EMPTY = new ItemContainerContents(List.of());

    private final List<ItemStack> items;

    private ItemContainerContents(List<ItemStack> items) {
        this.items = items;
    }

    public static ItemContainerContents fromItems(List<ItemStack> items) {
        int last = items.size() - 1;
        while (last >= 0 && items.get(last).isEmpty()) {
            last--;
        }
        if (last < 0) {
            return EMPTY;
        }
        List<ItemStack> copy = new ArrayList<>(last + 1);
        for (int i = 0; i <= last; i++) {
            copy.add(items.get(i).copy());
        }
        return new ItemContainerContents(copy);
    }

    public void copyInto(NonNullList<ItemStack> into) {
        for (int i = 0; i < into.size(); i++) {
            into.set(i, i < this.items.size() ? this.items.get(i).copy() : ItemStack.EMPTY);
        }
    }

    public ItemStack copyOne() {
        return this.items.isEmpty() ? ItemStack.EMPTY : this.items.get(0).copy();
    }

    public Stream<ItemStack> stream() {
        return this.items.stream().map(ItemStack::copy);
    }

    public Stream<ItemStack> nonEmptyStream() {
        return this.items.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy);
    }

    public Iterable<ItemStack> nonEmptyItems() {
        return () -> this.items.stream().filter(s -> !s.isEmpty()).iterator();
    }

    public Iterable<ItemStack> nonEmptyItemsCopy() {
        return () -> nonEmptyStream().iterator();
    }

    public int getSlots() {
        return this.items.size();
    }

    public ItemStack getStackInSlot(int slot) {
        return slot < this.items.size() ? this.items.get(slot).copy() : ItemStack.EMPTY;
    }

    ListTag save() {
        ListTag list = new ListTag();
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack s = this.items.get(i);
            if (!s.isEmpty()) {
                CompoundTag t = s.save(new CompoundTag());
                t.putByte("Slot", (byte) i);
                list.add(t);
            }
        }
        return list;
    }

    static ItemContainerContents load(ListTag list) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getByte("Slot") & 255;
            while (items.size() <= slot) {
                items.add(ItemStack.EMPTY);
            }
            items.set(slot, ItemStack.of(t));
        }
        return fromItems(items);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemContainerContents other) || other.items.size() != this.items.size()) {
            return false;
        }
        for (int i = 0; i < this.items.size(); i++) {
            if (!ItemStack.matches(this.items.get(i), other.items.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return this.items.size();
    }
}
