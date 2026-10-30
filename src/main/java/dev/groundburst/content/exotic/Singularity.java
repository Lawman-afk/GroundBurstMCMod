package dev.groundburst.content.exotic;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Singularities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The singularity warhead (DESIGN.md §16.2, Phase 11): a singularity seed held in its containment cells. Delivered by a
 * missile, the field is let go as it arrives: a black hole opens and lives for {@link Singularities#LIFE} ticks,
 * feeding on everything it reaches and growing by it (on open ground to a reach of about
 * {@link Singularities#TYPICAL_REACH} blocks; more in a mountainside or the sea, less in the open air), drawing everything
 * in and tearing the ground loose, then collapses in a blast as big as it has grown ({@link Singularities}). Set off any
 * other way its cells fail one by one: a small singularity that lives four seconds and cannot feed.
 */
public final class Singularity {
    /** A breached core's singularity: its reach (it does not feed) and the ticks it lives. */
    public static final float BREACH_REACH = 6;
    public static final int BREACH_LIFE = 80;

    private Singularity() {}

    /** A missile's singularity warhead arriving at {@code at}: the full black hole (its size times {@code scale}). */
    public static void burst(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        Singularities.start(level, at, Math.max(1, Singularities.SEED * scale), Singularities.FEED, Math.max(2, Singularities.MAX_REACH * scale),
                Singularities.LIFE, by);
    }

    /** A singularity warhead set off any other way: its cells failing, a small one for four seconds. */
    public static void breach(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        float reach = Math.max(1.5f, BREACH_REACH * scale);
        Singularities.start(level, at, reach, 0, reach, BREACH_LIFE, by);
    }
}
