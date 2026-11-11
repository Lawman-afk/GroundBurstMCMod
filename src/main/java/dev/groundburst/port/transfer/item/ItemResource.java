package dev.groundburst.port.transfer.item;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.transfer.Resource;
import net.minecraft.core.Holder;
import dev.groundburst.legacy.component.DataComponentMap;
import dev.groundburst.legacy.component.DataComponentPatch;
import dev.groundburst.legacy.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** An item with its components, without a count (an {@link ItemStack} without its count). */
public final class ItemResource implements Resource {
    public static final ItemResource EMPTY = new ItemResource(Items.AIR.builtInRegistryHolder(), DataComponentPatch.EMPTY);

    private final Holder<Item> item;
    private final DataComponentPatch patch;

    private ItemResource(Holder<Item> item, DataComponentPatch patch) {
        this.item = item;
        this.patch = patch;
    }

    public static ItemResource of(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : new ItemResource(stack.getItemHolder(), dev.groundburst.legacy.component.DataComponentPatch.of(stack));
    }

    public static ItemResource of(ItemLike item) {
        return of(item, DataComponentPatch.EMPTY);
    }

    @SuppressWarnings("deprecation")
    public static ItemResource of(ItemLike item, DataComponentPatch patch) {
        Item i = item.asItem();
        return i == Items.AIR ? EMPTY : new ItemResource(i.builtInRegistryHolder(), patch);
    }

    public static ItemResource of(Holder<Item> holder) {
        return of(holder, DataComponentPatch.EMPTY);
    }

    public static ItemResource of(Holder<Item> holder, DataComponentPatch patch) {
        return holder.value() == Items.AIR ? EMPTY : new ItemResource(holder, patch);
    }

    public Item value() {
        return this.item.value();
    }

    public Item getItem() {
        return this.item.value();
    }

    public Holder<Item> typeHolder() {
        return this.item;
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY || this.item.value() == Items.AIR;
    }

    public boolean matches(ItemStack stack) {
        return isEmpty() ? stack.isEmpty() : !stack.isEmpty() && stack.getItem() == getItem() && dev.groundburst.legacy.component.DataComponentPatch.of(stack).equals(this.patch);
    }

    public boolean is(ItemLike item) {
        return getItem() == item.asItem();
    }

    public boolean test(Predicate<ItemStack> predicate) {
        return predicate.test(toStack(1));
    }

    public boolean isComponentsPatchEmpty() {
        return this.patch.isEmpty();
    }

    public <D> ItemResource with(DataComponentType<D> type, @Nullable D data) {
        ItemStack s = toStack(1);
        dev.groundburst.legacy.component.Comps.set(s, type, data);
        return of(s);
    }

    public <D> ItemResource with(Supplier<? extends DataComponentType<D>> type, @Nullable D data) {
        return with(type.get(), data);
    }

    public ItemResource without(DataComponentType<?> type) {
        ItemStack s = toStack(1);
        dev.groundburst.legacy.component.Comps.remove(s, type);
        return of(s);
    }

    public DataComponentMap getComponents() {
        return dev.groundburst.legacy.component.DataComponentMap.of(toStack(1));
    }

    public DataComponentPatch getComponentsPatch() {
        return this.patch;
    }

    public ItemStack toStack(int count) {
        return isEmpty() || count <= 0 ? ItemStack.EMPTY : dev.groundburst.legacy.component.DataComponentPatch.stack(this.item, count, this.patch);
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public int getMaxStackSize() {
        return isEmpty() ? 0 : toStack(1).getMaxStackSize();
    }

    public Component getHoverName() {
        return toStack(1).getHoverName();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return ((Object) obj) instanceof ItemResource r && r.getItem() == getItem() && r.patch.equals(this.patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getItem(), this.patch);
    }

    @Override
    public String toString() {
        return "ItemResource[" + dev.groundburst.legacy.Game.registeredName(this.item) + (this.patch.isEmpty() ? "" : " " + this.patch) + "]";
    }
}
