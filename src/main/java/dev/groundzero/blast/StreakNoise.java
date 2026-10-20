package dev.groundzero.blast;

/**
 * The fine noise over direction that gives a blast its character (DESIGN.md §11.2): neighbouring rays differ in
 * strength, and because each ray runs straight out, the difference draws radial lines, so craters come out
 * starburst-shaped, with fingers gouged past the rim, and the scorch and ejecta beyond them fall in spokes that line up
 * with those fingers. Value noise on a 3D lattice, three octaves (about 45, 95 and 195 features round a circle). It is
 * mirrored exactly in {@code tools/preview/crater_preview.py} (same hash and octaves), so previews match the game.
 * Clients use it with the blast's seed to throw debris and smoke along the same spokes.
 */
public final class StreakNoise {
    private static final float[][] OCTAVES = {{7f, 0.5f}, {15f, 0.3f}, {31f, 0.2f}};

    private StreakNoise() {}

    /** A blast's noise seed from its 64-bit seed. */
    public static int seed(long blastSeed) {
        return (int) blastSeed ^ (int) (blastSeed >>> 32);
    }

    /** Noise over the unit direction (dx, dy, dz), in about [-1, 1]. */
    public static float at(float dx, float dy, float dz, int seed) {
        float out = 0;
        for (int k = 0; k < OCTAVES.length; k++) {
            float f = OCTAVES[k][0];
            out += OCTAVES[k][1] * noise(dx * f, dy * f, dz * f, seed + 31 * k);
        }
        return out;
    }

    /**
     * A ray's energy and reach multiplier: strong directions carve fingers past the rim and spikes into the crater's
     * floor, weak ones stop short.
     */
    static float factor(float dx, float dy, float dz, int seed, float streaks) {
        return Math.max(0.45f, Math.min(1.8f, 1 + streaks * 1.9f * at(dx, dy, dz, seed)));
    }

    /** One octave of the value noise at a point, in about [-1, 1] (smooth, a feature per lattice unit). */
    public static float value(float x, float y, float z, int seed) {
        return noise(x, y, z, seed);
    }

    private static float noise(float x, float y, float z, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        float tx = x - ix, ty = y - iy, tz = z - iz;
        float wx = tx * tx * (3 - 2 * tx), wy = ty * ty * (3 - 2 * ty), wz = tz * tz * (3 - 2 * tz);
        float out = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    float v = hash(ix + dx, iy + dy, iz + dz, seed);
                    out += v * (dx == 1 ? wx : 1 - wx) * (dy == 1 ? wy : 1 - wy) * (dz == 1 ? wz : 1 - wz);
                }
            }
        }
        return out;
    }

    /** 32-bit lattice hash to [-1, 1). */
    private static float hash(int x, int y, int z, int seed) {
        int h = (x * 0x27D4EB2D) ^ (y * 0x165667B1) ^ (z * 0x9E3779B9) ^ seed;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x7FFFFF - 1;
    }
}
