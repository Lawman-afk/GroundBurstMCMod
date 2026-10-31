package dev.groundburst.content.launch;

/** A screen with a terrain map (the Launch Computer, the Launch Control Station): whether it maps now, and how far. */
public interface MapSource {
    /** Formed and powered: its map shows. */
    boolean mapping();

    /** Its map stops at the survey round it (a computer's); false: the whole dimension (a station's). */
    boolean boundedMap();
}
