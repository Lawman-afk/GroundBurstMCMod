package dev.groundburst.port.transfer.transaction;

/**
 * An open transaction, as handlers are given one (docs/BACKPORT.md: this branch's own small transfer API, in the shape of
 * NeoForge 26.x's that main uses): changes made in it are kept if it commits and undone if it closes without.
 */
public interface TransactionContext {
    /** How deep it is: 0 for a root transaction, one more for each one opened inside another. */
    int depth();
}
