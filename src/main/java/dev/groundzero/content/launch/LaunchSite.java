package dev.groundzero.content.launch;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Anything a Launch Computer aims, arms and fires (DESIGN.md §17.3, §19.5): the launch silos and the superweapons. It
 * finds them through {@link Launches} and shows each in its list and on its map.
 */
public interface LaunchSite {
    /** What it is, for the computer's list and map (its ordinal goes over the wire). */
    enum Kind { LAUNCHER, SCUD_STORM, NUCLEAR_MISSILE }

    BlockPos getBlockPos();

    default Kind siteKind() {
        return Kind.LAUNCHER;
    }

    /** What it can do right now. */
    LaunchSiloBlockEntity.Readiness readiness();

    /**
     * Ticks to go: while it counts down to launch, until it fires; while a superweapon charges, until it is ready
     * (-1: neither).
     */
    int countdown();

    boolean armed();

    @Nullable BlockPos target();

    /** How far it reaches, blocks (0: nothing loaded). */
    int range();

    /** Aims it (not while it counts down). */
    boolean setTarget(int x, int z);

    /** Arms it (only when ready, with a target in range) or makes it safe. */
    boolean arm(boolean on);

    /** Fires it if armed. */
    boolean launchArmed(ServerLevel level, @Nullable Entity by);
}
