package dev.groundburst.content.exotic;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Storms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The storm warhead (DESIGN.md §16.14, Phase 11): a Tesla storm generator, its capacitor banks charged and its coils
 * wound, that seeds a supercell over the region where its ICBM comes down: {@link Storms#RADIUS} blocks round for a
 * minute and a half, lightning chaining through whatever stands in the open, its winds driving everything round its eye
 * ({@link Storms}). Set off any other way (a warhead placed in creative) its banks discharge at once: a small storm, a
 * fifth the size, for twenty seconds.
 */
public final class Storm {
    /** A discharged warhead's storm: its share of a full one's size, and the ticks it lasts. */
    public static final float BREACH_SHARE = 0.2f;
    public static final int BREACH_LIFE = 400;

    private Storm() {}

    /** A missile's storm warhead arriving at {@code at}: the full storm (its size times {@code scale}). */
    public static void burst(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        Storms.start(level, at, Math.max(4, Storms.RADIUS * scale), Storms.LIFE, by);
    }

    /** A storm warhead set off any other way: its banks discharging at once, a small storm. */
    public static void breach(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        Storms.start(level, at, Math.max(4, Storms.RADIUS * BREACH_SHARE * scale), BREACH_LIFE, by);
    }
}
