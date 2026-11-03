package dev.groundburst.port.transfer.transaction;

import java.util.ArrayList;

/**
 * Some state a transaction can undo: before it is first changed in a transaction, {@link #updateSnapshots} keeps a
 * {@link #createSnapshot snapshot} of it for that transaction. If the transaction closes without committing, the state
 * is {@link #revertToSnapshot put back}; if it commits, the snapshot passes to the transaction it was opened in, and when
 * a root transaction commits, {@link #onRootCommit} is told what the state was before (to mark changes, send updates).
 */
public abstract class SnapshotJournal<T> {
    /** The snapshot kept for each open transaction's depth (null: none kept at that depth). */
    private final ArrayList<Object> snapshots = new ArrayList<>();
    private static final Object NONE = new Object();

    protected abstract T createSnapshot();

    protected abstract void revertToSnapshot(T snapshot);

    protected void releaseSnapshot(T snapshot) {}

    protected void onRootCommit(T originalState) {}

    /** Whether a snapshot of it is held by some open transaction. */
    public final boolean isInTransaction() {
        for (Object s : this.snapshots) {
            if (s != NONE) {
                return true;
            }
        }
        return false;
    }

    /** Called before it is changed in {@code transaction}: keeps a snapshot for it if it has none yet. */
    public void updateSnapshots(TransactionContext transaction) {
        int d = transaction.depth();
        while (this.snapshots.size() <= d) {
            this.snapshots.add(NONE);
        }
        if (this.snapshots.get(d) == NONE) {
            this.snapshots.set(d, createSnapshot());
            ((Transaction) transaction).enlist(this);
        }
    }

    @SuppressWarnings("unchecked")
    private T take(int depth) {
        Object s = this.snapshots.set(depth, NONE);
        return (T) s;
    }

    final void reverted(Transaction transaction) {
        T s = take(transaction.depth());
        revertToSnapshot(s);
        releaseSnapshot(s);
    }

    final void committed(Transaction transaction) {
        int d = transaction.depth();
        T s = take(d);
        Transaction parent = transaction.parent();
        if (parent == null) {
            onRootCommit(s);
            releaseSnapshot(s);
        } else if (this.snapshots.get(d - 1) == NONE) {
            this.snapshots.set(d - 1, s);                     // the parent now holds what it was before both
            parent.enlist(this);
        } else {
            releaseSnapshot(s);
        }
    }
}
