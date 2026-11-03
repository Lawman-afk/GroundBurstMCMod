package dev.groundburst.port.transfer.energy;

import java.util.function.Supplier;

import dev.groundburst.port.transfer.transaction.TransactionContext;

/** Another energy handler seen through limits: at most so much in and out each time (0: none that way). */
public class LimitingEnergyHandler implements EnergyHandler {
    protected final Supplier<EnergyHandler> delegate;
    protected int maxInsert, maxExtract;

    public LimitingEnergyHandler(EnergyHandler delegate, int maxInsert, int maxExtract) {
        this(() -> delegate, maxInsert, maxExtract);
    }

    public LimitingEnergyHandler(Supplier<EnergyHandler> delegate, int maxInsert, int maxExtract) {
        this.delegate = delegate;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
    }

    @Override
    public long getAmountAsLong() {
        return this.delegate.get().getAmountAsLong();
    }

    @Override
    public long getCapacityAsLong() {
        return this.delegate.get().getCapacityAsLong();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        return this.maxInsert <= 0 ? 0 : this.delegate.get().insert(Math.min(amount, this.maxInsert), transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return this.maxExtract <= 0 ? 0 : this.delegate.get().extract(Math.min(amount, this.maxExtract), transaction);
    }
}
