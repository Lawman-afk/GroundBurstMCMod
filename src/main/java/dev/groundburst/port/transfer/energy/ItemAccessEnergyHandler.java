package dev.groundburst.port.transfer.energy;

import dev.groundburst.port.transfer.access.ItemAccess;
import dev.groundburst.port.transfer.item.ItemResource;
import dev.groundburst.port.transfer.transaction.TransactionContext;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;

/** The energy an item holds in a data component, through its {@link ItemAccess} (so a change swaps the item where it is). */
public class ItemAccessEnergyHandler implements EnergyHandler {
    protected final ItemAccess itemAccess;
    protected final Item validItem;
    protected final DataComponentType<Integer> energyComponent;
    protected final int capacity;
    protected final int maxInsert;
    protected final int maxExtract;

    public ItemAccessEnergyHandler(ItemAccess itemAccess, DataComponentType<Integer> energyComponent, int capacity) {
        this(itemAccess, energyComponent, capacity, capacity);
    }

    public ItemAccessEnergyHandler(ItemAccess itemAccess, DataComponentType<Integer> energyComponent, int capacity, int maxTransfer) {
        this(itemAccess, energyComponent, capacity, maxTransfer, maxTransfer);
    }

    public ItemAccessEnergyHandler(ItemAccess itemAccess, DataComponentType<Integer> energyComponent, int capacity, int maxInsert, int maxExtract) {
        this.itemAccess = itemAccess;
        this.validItem = itemAccess.getResource().getItem();
        this.energyComponent = energyComponent;
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
    }

    protected int getAmountFrom(ItemResource accessResource) {
        Integer e = accessResource.toStack(1).get(this.energyComponent);
        return e == null ? 0 : Math.max(0, Math.min(this.capacity, e));
    }

    protected ItemResource update(ItemResource accessResource, int newAmount) {
        return accessResource.with(this.energyComponent, newAmount);
    }

    private boolean valid() {
        ItemResource r = this.itemAccess.getResource();
        return !r.isEmpty() && r.getItem() == this.validItem && this.itemAccess.getAmount() > 0;
    }

    @Override
    public long getAmountAsLong() {
        return valid() ? (long) getAmountFrom(this.itemAccess.getResource()) * this.itemAccess.getAmount() : 0;
    }

    @Override
    public long getCapacityAsLong() {
        return valid() ? (long) this.capacity * this.itemAccess.getAmount() : 0;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (!valid() || amount <= 0) {
            return 0;
        }
        ItemResource r = this.itemAccess.getResource();
        int now = getAmountFrom(r);
        int in = Math.min(Math.min(amount, this.maxInsert), this.capacity - now);
        if (in <= 0) {
            return 0;
        }
        return this.itemAccess.exchange(update(r, now + in), 1, transaction) == 1 ? in : 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (!valid() || amount <= 0) {
            return 0;
        }
        ItemResource r = this.itemAccess.getResource();
        int now = getAmountFrom(r);
        int out = Math.min(Math.min(amount, this.maxExtract), now);
        if (out <= 0) {
            return 0;
        }
        return this.itemAccess.exchange(update(r, now - out), 1, transaction) == 1 ? out : 0;
    }
}
