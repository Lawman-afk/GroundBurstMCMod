package dev.groundburst.port;

/** Minecraft 26.x's packed light helpers (block light in the low half, sky light in the high). */
public final class Lights {
    private Lights() {}

    /** The brighter of two packed lights, block and sky each. */
    public static int max(int a, int b) {
        return Math.max(a & 0xFFFF, b & 0xFFFF) | Math.max(a >>> 16, b >>> 16) << 16;
    }
}
