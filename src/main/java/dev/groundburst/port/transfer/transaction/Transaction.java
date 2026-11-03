package dev.groundburst.port.transfer.transaction;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A transaction: everything changed through it (each change's journal snapshotted the first time it is touched) is kept
 * when it {@link #commit commits} and undone when it {@link #close closes} without committing. Transactions nest; a root
 * one is opened with {@link #openRoot}, one inside another with {@link #open}. Each thread has its own (in one game the
 * client's and the server's run side by side), and only its innermost open transaction may be closed.
 */
public final class Transaction implements TransactionContext, AutoCloseable {
    private static final ThreadLocal<@Nullable Transaction> CURRENT = new ThreadLocal<>();

    private final @Nullable Transaction parent;
    private final int depth;
    private final List<SnapshotJournal<?>> journals = new ArrayList<>();
    private boolean committed;
    private boolean closed;

    private Transaction(@Nullable Transaction parent) {
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
        CURRENT.set(this);
    }

    /** Opens a root transaction (none may be open). */
    public static Transaction openRoot() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("A transaction is already open: open one inside it with Transaction.open(parent)");
        }
        return new Transaction(null);
    }

    /** Opens a transaction inside {@code parent} (a root one if there is none). */
    public static Transaction open(@Nullable TransactionContext parent) {
        if (parent == null) {
            return openRoot();
        }
        if (parent != CURRENT.get()) {
            throw new IllegalStateException("Transactions open inside the innermost open one");
        }
        return new Transaction((Transaction) parent);
    }

    /** The innermost open transaction (null: none). */
    public static @Nullable TransactionContext getCurrentOpenedTransaction() {
        return CURRENT.get();
    }

    @Override
    public int depth() {
        return this.depth;
    }

    /** Keeps its changes and closes it. */
    public void commit() {
        this.committed = true;
        close();
    }

    /** Closes it: its changes are undone unless it was committed. */
    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        if (CURRENT.get() != this) {
            throw new IllegalStateException("Close the transactions opened inside this one first");
        }
        this.closed = true;
        CURRENT.set(this.parent);
        if (this.committed) {
            for (SnapshotJournal<?> j : this.journals) {
                j.committed(this);
            }
        } else {
            for (int i = this.journals.size() - 1; i >= 0; i--) {
                this.journals.get(i).reverted(this);
            }
        }
        this.journals.clear();
    }

    @Nullable Transaction parent() {
        return this.parent;
    }

    void enlist(SnapshotJournal<?> journal) {
        this.journals.add(journal);
    }

    @Override
    public String toString() {
        return "Transaction[depth " + this.depth + (this.closed ? ", closed" : "") + "]";
    }
}
