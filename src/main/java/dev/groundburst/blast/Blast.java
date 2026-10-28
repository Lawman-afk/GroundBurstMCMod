package dev.groundburst.blast;

/** A blast that has been set off: how far its front has got and whether it has finished. */
public interface Blast {
    /** The radius the front has reached, in blocks. */
    int radius();

    /** Whether the blast has finished (front past its outer radius, crater rim settled). */
    boolean isDone();

    /** Blocks changed so far. */
    int blocksChanged();

    /**
     * What the blast is holding, for benchmarks ({@link BlastBench}): its phase, how far its terrain work has got, its
     * live rays (and the arrays' room), the chunks it keeps loaded, the chunk sections it has marked, its crater rim,
     * and the fires and rubble waiting.
     */
    record Stats(String phase, int terrain, int rays, int rayRoom, int chunks, int sections, int rim, int deferred) {}

    Stats stats();
}
