package dev.groundburst.content.fluid;

import net.neoforged.neoforge.fluids.FluidStack;

/** What a tank's renderer draws: its fluid as one box filling a {@code width x width x height} tank up to the fill level. */
public interface TankDisplay {
    /** False for the blocks of a tank that are not its controller (the controller draws the whole box). */
    boolean drawsContents();

    FluidStack shownFluid();

    int shownCapacity();

    int boxWidth();

    int boxHeight();
}
