package dev.groundburst.content.exotic;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Singularities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The singularity warhead (DESIGN.md §16.2, Phase 11): a singularity seed held in its containment cells. Delivered by a
 * missile, the field is let go as it arrives: a black hole opens and grows for {@link Singularities#GROW} ticks to an
 * event horizon of {@link Singularities#HORIZON} blocks, drawing in everything within {@link Singularities#PULL} and
 * tearing the ground loose out to {@link Singularities#RIP}, then collapses in a blast ({@link Singularities}). Set off
 * any other way its cells fail one by one: a small singularity that lives three seconds.
 */
public final class Singularity {
    /** A breached core's singularity, as a share of a full one's size, and the ticks it lives. */
    public static final float BREACH_SHARE = 0.15f;
    public static final int BREACH_GROW = 60;

    private Singularity() {}

    /** A missile's singularity warhead arriving at {@code at}: the full black hole (its size times {@code scale}). */
    public static void burst(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        Singularities.start(level, at, Math.max(1, Singularities.HORIZON * scale), Math.max(4, Singularities.PULL * scale),
                Math.max(2, Singularities.RIP * scale), Singularities.GROW, scale, by);
    }

    /** A singularity warhead set off any other way: its cells failing, a small one for three seconds. */
    public static void breach(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        float s = BREACH_SHARE * scale;
        Singularities.start(level, at, Math.max(0.6f, Singularities.HORIZON * s), Math.max(3, Singularities.PULL * s),
                Math.max(1.5f, Singularities.RIP * s), BREACH_GROW, s, by);
    }
}
