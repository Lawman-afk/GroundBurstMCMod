package dev.groundburst.port.transfer.energy;

import dev.groundburst.port.ValueInput;
import dev.groundburst.port.ValueOutput;
import dev.groundburst.port.transfer.transaction.SnapshotJournal;
import dev.groundburst.port.transfer.transaction.TransactionContext;

/** An energy store of a set capacity, taking and giving at most so much each time; {@link #onEnergyChanged} hears of changes. */
public class SimpleEnergyHandler implements EnergyHandler {
    protected int energy;
    protected int capacity;
    protected int maxInsert;
    protected int maxExtract;
    private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
        @Override
        protected Integer createSnapshot() {
            return SimpleEnergyHandler.this.energy;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            SimpleEnergyHandler.this.energy = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            if (originalState != SimpleEnergyHandler.this.energy) {
                onEnergyChanged(originalState);
            }
        }
    };

    public SimpleEnergyHandler(int capacity) {
        this(capacity, capacity);
    }

    public SimpleEnergyHandler(int capacity, int maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract) {
        this(capacity, maxInsert, maxExtract, 0);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract, int energy) {
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
        this.energy = Math.max(0, Math.min(capacity, energy));
    }

    public void serialize(ValueOutput output) {
        output.putInt("energy", this.energy);
    }

    public void deserialize(ValueInput input) {
        this.energy = Math.max(0, Math.min(this.capacity, input.getIntOr("energy", 0)));
    }

    /** Sets what it holds at once, outside any transaction. */
    public void set(int amount) {
        int previous = this.energy;
        this.energy = Math.max(0, Math.min(this.capacity, amount));
        if (previous != this.energy) {
            onEnergyChanged(previous);
        }
    }

    protected void onEnergyChanged(int previousAmount) {}

    @Override
    public long getAmountAsLong() {
        return this.energy;
    }

    @Override
    public long getCapacityAsLong() {
        return this.capacity;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int inserted = Math.min(Math.min(amount, this.maxInsert), this.capacity - this.energy);
        if (inserted > 0) {
            this.journal.updateSnapshots(transaction);
            this.energy += inserted;
            return inserted;
        }
        return 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        int extracted = Math.min(Math.min(amount, this.maxExtract), this.energy);
        if (extracted > 0) {
            this.journal.updateSnapshots(transaction);
            this.energy -= extracted;
            return extracted;
        }
        return 0;
    }
}
