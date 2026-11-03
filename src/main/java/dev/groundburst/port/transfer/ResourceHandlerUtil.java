package dev.groundburst.port.transfer;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.transfer.transaction.Transaction;
import dev.groundburst.port.transfer.transaction.TransactionContext;

/** Helpers over resource handlers: moving between two, and a few questions about one. */
public final class ResourceHandlerUtil {
    private ResourceHandlerUtil() {}

    public static boolean isEmpty(Resource resource, int amount) {
        return resource.isEmpty() || amount <= 0;
    }

    public static boolean isEmpty(ResourceHandler<? extends Resource> handler) {
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getAmountAsLong(i) > 0) {
                return false;
            }
        }
        return true;
    }

    public static <T extends Resource> boolean isFull(ResourceHandler<T> handler) {
        for (int i = 0; i < handler.size(); i++) {
            T r = handler.getResource(i);
            if (r.isEmpty() || handler.getAmountAsLong(i) < handler.getCapacityAsLong(i, r)) {
                return false;
            }
        }
        return true;
    }

    public static <T extends Resource> boolean contains(ResourceHandler<T> handler, T resource) {
        return indexOf(handler, resource) >= 0;
    }

    public static <T extends Resource> int indexOf(ResourceHandler<T> handler, T resource) {
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getAmountAsLong(i) > 0 && handler.getResource(i).equals(resource)) {
                return i;
            }
        }
        return -1;
    }

    /** Puts {@code resource} in where it already is first, then anywhere it fits; how much went in. */
    public static <T extends Resource> int insertStacking(ResourceHandler<T> handler, T resource, int amount, TransactionContext transaction) {
        int inserted = 0;
        for (int i = 0; i < handler.size() && inserted < amount; i++) {
            if (handler.getAmountAsLong(i) > 0 && handler.getResource(i).equals(resource)) {
                inserted += handler.insert(i, resource, amount - inserted, transaction);
            }
        }
        for (int i = 0; i < handler.size() && inserted < amount; i++) {
            if (handler.getAmountAsLong(i) <= 0) {
                inserted += handler.insert(i, resource, amount - inserted, transaction);
            }
        }
        return inserted;
    }

    /** Moves up to {@code amount} of what {@code filter} accepts from one handler to the other; how much moved. */
    public static <T extends Resource> int move(@Nullable ResourceHandler<T> from, @Nullable ResourceHandler<T> to, Predicate<T> filter,
            int amount, @Nullable TransactionContext transaction) {
        return moveInternal(from, to, filter, amount, false, transaction);
    }

    /** As {@link #move}, filling places that already hold the resource first. */
    public static <T extends Resource> int moveStacking(@Nullable ResourceHandler<T> from, @Nullable ResourceHandler<T> to, Predicate<T> filter,
            int amount, @Nullable TransactionContext transaction) {
        return moveInternal(from, to, filter, amount, true, transaction);
    }

    private static <T extends Resource> int moveInternal(@Nullable ResourceHandler<T> from, @Nullable ResourceHandler<T> to, Predicate<T> filter,
            int amount, boolean stacking, @Nullable TransactionContext transaction) {
        if (from == null || to == null || amount <= 0) {
            return 0;
        }
        int moved = 0;
        try (Transaction outer = Transaction.open(transaction)) {
            for (int i = 0; i < from.size() && moved < amount; i++) {
                T resource = from.getResource(i);
                if (resource.isEmpty() || !filter.test(resource)) {
                    continue;
                }
                int want = (int) Math.min(amount - moved, from.getAmountAsLong(i));
                int fits;
                try (Transaction trial = Transaction.open(outer)) {
                    int out = from.extract(i, resource, want, trial);
                    fits = stacking ? insertStacking(to, resource, out, trial) : to.insert(resource, out, trial);
                    if (fits == out && out > 0) {
                        trial.commit();
                        moved += out;
                        continue;
                    }
                }
                if (fits > 0) {                                  // only part fits: move just that much
                    try (Transaction exact = Transaction.open(outer)) {
                        int out = from.extract(i, resource, fits, exact);
                        int in = stacking ? insertStacking(to, resource, out, exact) : to.insert(resource, out, exact);
                        if (in == out && out > 0) {
                            exact.commit();
                            moved += out;
                        }
                    }
                }
            }
            outer.commit();
        }
        return moved;
    }
}
