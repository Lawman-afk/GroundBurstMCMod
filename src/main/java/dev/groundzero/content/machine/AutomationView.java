package dev.groundzero.content.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What pipes and hoppers see of a machine through one face: they may only insert into input slots and only extract
 * from output slots (like a furnace), and only as far as that face's {@link SideConfig.ItemMode} allows. Indexes
 * are remapped onto the listed machine slots.
 */
public class AutomationView implements ResourceHandler<ItemResource> {
    private final MachineInventory inventory;
    private final int[] slots;
    private final boolean[] insertable;
    private final boolean[] extractable;

    private final boolean allowInsert;
    private final boolean allowExtract;

    public AutomationView(MachineInventory inventory, int[] inputSlots, int[] outputSlots) {
        this(inventory, inputSlots, outputSlots, true, true);
    }

    public AutomationView(MachineInventory inventory, int[] inputSlots, int[] outputSlots, boolean allowInsert, boolean allowExtract) {
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
        this.inventory = inventory;
        this.slots = new int[inputSlots.length + outputSlots.length];
        this.insertable = new boolean[this.slots.length];
        this.extractable = new boolean[this.slots.length];
        int i = 0;
        for (int s : inputSlots) {
            this.slots[i] = s;
            this.insertable[i++] = true;
        }
        for (int s : outputSlots) {
            this.slots[i] = s;
            this.extractable[i++] = true;
        }
    }

    @Override
    public int size() {
        return this.slots.length;
    }

    @Override
    public ItemResource getResource(int index) {
        return this.inventory.getResource(this.slots[index]);
    }

    @Override
    public long getAmountAsLong(int index) {
        return this.inventory.getAmountAsLong(this.slots[index]);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return this.inventory.getCapacityAsLong(this.slots[index], resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return this.allowInsert && this.insertable[index] && this.inventory.isValid(this.slots[index], resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return this.allowInsert && this.insertable[index] ? this.inventory.insert(this.slots[index], resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return this.allowExtract && this.extractable[index] ? this.inventory.extract(this.slots[index], resource, amount, transaction) : 0;
    }
}
