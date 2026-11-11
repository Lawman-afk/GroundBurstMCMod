package dev.groundburst.port.transfer.item;

import dev.groundburst.port.transfer.StacksResourceHandler;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Item stacks in slots, as an inventory holds them. */
public class ItemStacksResourceHandler extends StacksResourceHandler<ItemStack, ItemResource> {
    public ItemStacksResourceHandler(int size) {
        super(size, ItemStack.EMPTY, dev.groundburst.legacy.net.Codecs.OPTIONAL_ITEM_STACK_CODEC);
    }

    public ItemStacksResourceHandler(NonNullList<ItemStack> stacks) {
        super(stacks, ItemStack.EMPTY, dev.groundburst.legacy.net.Codecs.OPTIONAL_ITEM_STACK_CODEC);
    }

    @Override
    public ItemResource getResourceFrom(ItemStack stack) {
        return ItemResource.of(stack);
    }

    @Override
    public int getAmountFrom(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    protected ItemStack getStackFrom(ItemResource resource, int amount) {
        return resource.toStack(amount);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return resource.isEmpty() ? 99 : resource.getMaxStackSize();
    }

    @Override
    protected ItemStack copyOf(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public boolean matches(ItemStack stack, ItemResource resource) {
        return resource.matches(stack);
    }
}
