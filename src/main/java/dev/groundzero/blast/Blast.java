package dev.groundzero.blast;

/** A blast that has been set off: how far its front has got and whether it has finished. */
public interface Blast {
    /** The radius the front has reached, in blocks. */
    int radius();

    /** Whether the blast has finished (front past its outer radius, crater rim settled). */
    boolean isDone();

    /** Blocks changed so far. */
    int blocksChanged();
}
