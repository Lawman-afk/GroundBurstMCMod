package dev.groundburst.port.transfer.energy;

import com.google.common.primitives.Ints;

import dev.groundburst.port.transfer.transaction.TransactionContext;

/** Something that holds energy (FE) and lets it be put in and taken out within a transaction. */
public interface EnergyHandler {
    long getAmountAsLong();

    default int getAmountAsInt() {
        return Ints.saturatedCast(getAmountAsLong());
    }

    long getCapacityAsLong();

    default int getCapacityAsInt() {
        return Ints.saturatedCast(getCapacityAsLong());
    }

    /** Puts up to {@code amount} in; how much went in. */
    int insert(int amount, TransactionContext transaction);

    /** Takes up to {@code amount} out; how much came out. */
    int extract(int amount, TransactionContext transaction);
}
