package dev.groundzero.content.fluid;

/**
 * Fluid pipe tiers (DESIGN.md §6.4), like the cable tiers: a run of pipe carries as much as its weakest pipe, and a
 * side set to pull draws at that rate. Rates sit a little above Mekanism's and Pipez's matching tiers (about 100 /
 * 500 / 2,000 mB/t) and step up four times per tier, against machines that use 5-20 mB/t and pumps that make 50.
 */
public enum FluidPipeTier {
    COPPER("copper_fluid_pipe", "Copper Fluid Pipe", 150),
    STEEL("fluid_pipe", "Steel Fluid Pipe", 600),
    PRESSURIZED("pressurized_fluid_pipe", "Pressurized Fluid Pipe", 2_400);

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
