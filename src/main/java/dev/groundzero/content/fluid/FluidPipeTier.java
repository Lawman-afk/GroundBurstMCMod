package dev.groundzero.content.fluid;

/**
 * Fluid pipe tiers (DESIGN.md §6.4), like the cable tiers: a run of pipe carries as much as its weakest pipe, and a
 * side set to pull draws up to one tick's worth of that rate every pull.
 */
public enum FluidPipeTier {
    COPPER("copper_fluid_pipe", "Copper Fluid Pipe", 1_000),
    STEEL("fluid_pipe", "Steel Fluid Pipe", 4_000),
    PRESSURIZED("pressurized_fluid_pipe", "Pressurized Fluid Pipe", 16_000);

    private final String id;
    private final String english;
    private final int rate;

    FluidPipeTier(String id, String english, int rate) {
        this.id = id;
        this.english = english;
        this.rate = rate;
    }

    public String id() {
        return id;
    }

    public String english() {
        return english;
    }

    /** mB per tick a run of these pipes may carry. */
    public int rate() {
        return rate;
    }
}
