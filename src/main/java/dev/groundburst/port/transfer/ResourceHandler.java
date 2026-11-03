package dev.groundburst.port.transfer;

import com.google.common.primitives.Ints;

import dev.groundburst.port.transfer.transaction.TransactionContext;

/**
 * Something that holds resources in numbered places (an inventory's slots, a machine's tanks) and lets them be put in and
 * taken out within a transaction. This branch's own, in the shape of NeoForge 26.x's that main uses (docs/BACKPORT.md);
 * {@code Capabilities} bridges it to 1.21.1's item and fluid handlers.
 */
public interface ResourceHandler<T extends Resource> {
    int size();

    T getResource(int index);

    long getAmountAsLong(int index);

    default int getAmountAsInt(int index) {
        return Ints.saturatedCast(getAmountAsLong(index));
    }

    long getCapacityAsLong(int index, T resource);

    default int getCapacityAsInt(int index, T resource) {
        return Ints.saturatedCast(getCapacityAsLong(index, resource));
    }

    boolean isValid(int index, T resource);

    /** Puts up to {@code amount} of {@code resource} into place {@code index}; how much went in. */
    int insert(int index, T resource, int amount, TransactionContext transaction);

    /** Puts up to {@code amount} of {@code resource} in wherever it fits, in order; how much went in. */
    default int insert(T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        int inserted = 0;
        for (int index = 0, n = size(); index < n && inserted < amount; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    /** Takes up to {@code amount} of {@code resource} out of place {@code index}; how much came out. */
    int extract(int index, T resource, int amount, TransactionContext transaction);

    /** Takes up to {@code amount} of {@code resource} out of wherever it is, in order; how much came out. */
    default int extract(T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        int extracted = 0;
        for (int index = 0, n = size(); index < n && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }

    @SuppressWarnings("unchecked")
    static <T extends Resource> Class<ResourceHandler<T>> asClass() {
        return (Class<ResourceHandler<T>>) (Object) ResourceHandler.class;
    }
}
