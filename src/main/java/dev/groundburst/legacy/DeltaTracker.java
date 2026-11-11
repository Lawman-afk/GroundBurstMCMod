package dev.groundburst.legacy;

/**
 * Minecraft 1.21's frame timing, as the mod's drawing code asks it (1.20.1 hands the partial tick as a float).
 */
public interface DeltaTracker {
    DeltaTracker ZERO = of(0f);
    DeltaTracker ONE = of(1f);

    float getGameTimeDeltaTicks();

    float getGameTimeDeltaPartialTick(boolean ignoreFrozen);

    float getRealtimeDeltaTicks();

    /** A frame drawn a partial tick of {@code partialTick} into the tick. */
    static DeltaTracker of(float partialTick) {
        return new DeltaTracker() {
            @Override
            public float getGameTimeDeltaTicks() {
                return partialTick;
            }

            @Override
            public float getGameTimeDeltaPartialTick(boolean ignoreFrozen) {
                return partialTick;
            }

            @Override
            public float getRealtimeDeltaTicks() {
                return partialTick;
            }
        };
    }
}
