package dev.groundburst.content.power;

/** Cable grades (DESIGN.md §7). A network carries at most its weakest cable's rate per tick. */
public enum CableTier {
    COPPER("copper_cable", "Copper Cable", 256),
    INSULATED("insulated_cable", "Insulated Cable", 1_024),
    HIGH_VOLTAGE("hv_cable", "High-Voltage Cable", 8_192);

    private final String id;
    private final String english;
    private final int rate;

    CableTier(String id, String english, int rate) {
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

    /** FE per tick the network may carry. */
    public int rate() {
        return rate;
    }
}
