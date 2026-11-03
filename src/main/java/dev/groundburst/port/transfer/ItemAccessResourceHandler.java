package dev.groundburst.port.transfer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.transfer.access.ItemAccess;
import dev.groundburst.port.transfer.item.ItemResource;
import dev.groundburst.port.transfer.transaction.TransactionContext;

/**
 * A handler living in an item (a canister's fluid): what it holds is read from the item, and each change swaps the item
 * for its changed self through its {@link ItemAccess}. With several items there, each holds the same and changes alike.
 */
public abstract class ItemAccessResourceHandler<T extends Resource> implements ResourceHandler<T> {
    protected final ItemAccess itemAccess;
    protected final int size;

    protected ItemAccessResourceHandler(ItemAccess itemAccess, int size) {
        this.itemAccess = itemAccess;
        this.size = size;
    }

    protected abstract T getResourceFrom(ItemResource accessResource, int index);

    protected abstract int getAmountFrom(ItemResource accessResource, int index);

    /** The item changed to hold {@code newAmount} of {@code newResource} in place {@code index} (null: it cannot). */
    protected abstract @Nullable ItemResource update(ItemResource accessResource, int index, T newResource, int newAmount);

    @Override
    public boolean isValid(int index, T resource) {
        return true;
    }

    protected abstract int getCapacity(int index, T resource);

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public T getResource(int index) {
        return getResourceFrom(this.itemAccess.getResource(), index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return (long) getAmountFrom(this.itemAccess.getResource(), index) * this.itemAccess.getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        return (long) getCapacity(index, resource) * this.itemAccess.getAmount();
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        ItemResource here = this.itemAccess.getResource();
        int count = this.itemAccess.getAmount();
        if (here.isEmpty() || count <= 0 || resource.isEmpty() || amount <= 0 || !isValid(index, resource)) {
            return 0;
        }
        T current = getResourceFrom(here, index);
        int currentAmount = getAmountFrom(here, index);
        if (currentAmount > 0 && !current.equals(resource)) {
            return 0;
        }
        int each = Math.min(getCapacity(index, resource) - currentAmount, amount / count);
        if (each <= 0) {
            return 0;
        }
        ItemResource changed = update(here, index, resource, currentAmount + each);
        if (changed == null) {
            return 0;
        }
        return this.itemAccess.exchange(changed, count, transaction) == count ? each * count : 0;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        ItemResource here = this.itemAccess.getResource();
        int count = this.itemAccess.getAmount();
        if (here.isEmpty() || count <= 0 || resource.isEmpty() || amount <= 0) {
            return 0;
        }
        T current = getResourceFrom(here, index);
        int currentAmount = getAmountFrom(here, index);
        if (currentAmount <= 0 || !current.equals(resource)) {
            return 0;
        }
        int each = Math.min(currentAmount, amount / count);
        if (each <= 0) {
            return 0;
        }
        ItemResource changed = update(here, index, resource, currentAmount - each);
        if (changed == null) {
            return 0;
        }
        return this.itemAccess.exchange(changed, count, transaction) == count ? each * count : 0;
    }
}
