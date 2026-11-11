package dev.groundburst.port;

/**
 * The dev test runs (the port check, the play bench, the Create check, the test range: build.gradle sets
 * {@code groundburst.keepMouse}) leave the person at the computer their mouse: the game's window opens behind their windows
 * without taking the focus, and the game never grabs the mouse (the runs turn the camera themselves). Players' games never
 * set it.
 */
public final class DevRuns {
    private DevRuns() {}

    public static final boolean KEEP_MOUSE = Boolean.getBoolean("groundburst.keepMouse");
}
