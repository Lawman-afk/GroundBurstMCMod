package dev.groundburst.content.exotic;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Freezes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The cryogenic warhead (DESIGN.md §16.16): a heat sink of stabilised exotic matter held near absolute zero by
 * superconducting coils in a jacket of liquid nitrogen. Where its Cryo ICBM comes down it is let go, and drinks the heat
 * out of everything round it: a white front of frozen air rolls {@link Freezes#RADIUS} blocks out over the land,
 * freezing all it passes; within its heart nothing lives and spires of ice burst out of the ground; a cold snap holds
 * the region for three minutes ({@link Freezes}). Set off any other way (a warhead placed in creative) its coils let go
 * at once and only part of it is spent: a freeze a fifth the size, its cold snap 45 seconds.
 */
public final class Cryo {
    /** A breached warhead's freeze: its share of a full one's size, and the ticks its cold snap lasts. */
    public static final float BREACH_SHARE = 0.2f;
    public static final int BREACH_LIFE = 900;

    private Cryo() {}

    /** A missile's cryogenic warhead arriving at {@code at}: the full freeze (its size times {@code scale}). */
    public static void burst(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        Freezes.start(level, at, Math.max(6, Freezes.RADIUS * scale), Freezes.LIFE, by);
    }

    /** A cryogenic warhead set off any other way: its coils letting go at once, a small freeze. */
    public static void breach(ServerLevel level, Vec3 at, @Nullable Entity by, float scale) {
        Freezes.start(level, at, Math.max(6, Freezes.RADIUS * BREACH_SHARE * scale), BREACH_LIFE, by);
    }
}
