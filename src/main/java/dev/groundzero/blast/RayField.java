package dev.groundzero.blast;

import java.util.Arrays;

/**
 * The rays of one blast (DESIGN.md §11.2), stored as parallel arrays so hundreds of thousands of rays stay compact.
 *
 * <p>Rays start on a cube-sphere grid: each of the 6 cube faces carries a 4x4 grid of cells in face coordinates
 * (u, v) in [-1, 1]; a ray points through its cell's centre. As the front moves out the gap between neighbouring rays
 * grows with the radius; when it would pass {@link #SPLIT_SPACING} blocks a ray splits into the 2x2 rays of its
 * quartered cell, each inheriting its energy, so every block of every shell is reached with no holes.
 *
 * <p>Each ray also has a grain: how strong its direction is ({@link StreakNoise}). It scales the ray's energy and how
 * far it may dig, and a child's energy is rescaled by its own grain when it splits, so the fine streaks sharpen as the
 * rays multiply instead of being smeared by the coarse starting grid.
 */
final class RayField {
    /** Largest gap between neighbouring rays, in blocks, before they split. */
    static final float SPLIT_SPACING = 0.7f;
    /** Rays stop splitting past this many (a far pressure band may then miss the odd block, but memory stays bounded). */
    static final int MAX_RAYS = 1_500_000;
    static final byte CRATER = 0, PRESSURE = 1, DEAD = 2;

    /** Face normals and the two tangents spanning each face. */
    private static final float[][] N = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final float[][] A = {{0, 1, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}};
    private static final float[][] B = {{0, 0, 1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 1, 0}};

    int size;
    byte[] face = new byte[0];
    float[] u = new float[0], v = new float[0], s = new float[0];
    float[] dx = new float[0], dy = new float[0], dz = new float[0];
    float[] energy = new float[0];
    /** Strength of the ray's direction (1 on average): multiplies its energy and its crater reach. */
    float[] grain = new float[0];
    byte[] mode = new byte[0];

    /** The starting 96 rays, each with grain {@code grain.at(d)} and energy {@code energy.at(d) * grain}. */
    static RayField start(DirectionalEnergy energy, DirectionalEnergy grain) {
        RayField f = new RayField();
        f.ensure(96);
        for (int fc = 0; fc < 6; fc++) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    float cu = -0.75f + 0.5f * i, cv = -0.75f + 0.5f * j;
                    int k = f.add(fc, cu, cv, 0.5f, 0, 1, CRATER);
                    f.grain[k] = grain.at(f.dx[k], f.dy[k], f.dz[k]);
                    f.energy[k] = energy.at(f.dx[k], f.dy[k], f.dz[k]) * f.grain[k];
                }
            }
        }
        return f;
    }

    /** A value by direction (starting energy: downward rays weaker, lobes from smooth noise; or grain). */
    interface DirectionalEnergy {
        float at(float dx, float dy, float dz);
    }

    private int add(int fc, float cu, float cv, float cs, float e, float g, byte m) {
        ensure(this.size + 1);
        int k = this.size++;
        this.face[k] = (byte) fc;
        this.u[k] = cu;
        this.v[k] = cv;
        this.s[k] = cs;
        float x = N[fc][0] + cu * A[fc][0] + cv * B[fc][0];
        float y = N[fc][1] + cu * A[fc][1] + cv * B[fc][1];
        float z = N[fc][2] + cu * A[fc][2] + cv * B[fc][2];
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        this.dx[k] = x / len;
        this.dy[k] = y / len;
        this.dz[k] = z / len;
        this.energy[k] = e;
        this.grain[k] = g;
        this.mode[k] = m;
        return k;
    }

    private void ensure(int n) {
        if (n <= this.face.length) {
            return;
        }
        int cap = Math.max(n, this.face.length * 2 + 16);
        this.face = Arrays.copyOf(this.face, cap);
        this.u = Arrays.copyOf(this.u, cap);
        this.v = Arrays.copyOf(this.v, cap);
        this.s = Arrays.copyOf(this.s, cap);
        this.dx = Arrays.copyOf(this.dx, cap);
        this.dy = Arrays.copyOf(this.dy, cap);
        this.dz = Arrays.copyOf(this.dz, cap);
        this.energy = Arrays.copyOf(this.energy, cap);
        this.grain = Arrays.copyOf(this.grain, cap);
        this.mode = Arrays.copyOf(this.mode, cap);
    }

    /** Gap in blocks between ray {@code k} and its neighbours at radius {@code r}. */
    private float spacing(int k, float r) {
        float cu = this.u[k], cv = this.v[k];
        float d = 1 + cu * cu + cv * cv;
        float stretch = (float) Math.max(Math.sqrt(1 + cu * cu), Math.sqrt(1 + cv * cv)) / d;
        return r * this.s[k] * stretch;
    }

    /**
     * Splits every live ray whose gap at radius {@code r} is too wide (repeatedly, until none is). A child takes its
     * grain from {@code grain} and its parent's energy rescaled by the two grains.
     */
    void splitFor(float r, DirectionalEnergy grain) {
        boolean again = true;
        while (again && this.size < MAX_RAYS) {
            again = false;
            int n = this.size;
            for (int k = 0; k < n && this.size < MAX_RAYS; k++) {
                if (this.mode[k] == DEAD || spacing(k, r) <= SPLIT_SPACING) {
                    continue;
                }
                float q = this.s[k] / 4, half = this.s[k] / 2;
                int fc = this.face[k];
                float cu = this.u[k], cv = this.v[k], e = this.energy[k] / this.grain[k];
                byte m = this.mode[k];
                this.mode[k] = DEAD;                       // replaced by its four children
                for (int c = 0; c < 4; c++) {
                    int child = add(fc, cu + ((c & 1) == 0 ? -q : q), cv + ((c & 2) == 0 ? -q : q), half, 0, 1, m);
                    float g = grain.at(this.dx[child], this.dy[child], this.dz[child]);
                    this.grain[child] = g;
                    this.energy[child] = e * g;
                }
                again = true;
            }
            compact();
        }
    }

    /** Bytes per saved ray: face and mode, then u, v, cell size, energy and grain (directions are worked out again on load). */
    private static final int SAVED_BYTES = 2 + 5 * 4;
    /** Marks the current save layout (older saves, without grain, start with the ray count). */
    private static final int SAVED_VERSION = -2;

    /** The live rays, packed for saving with the blast (a few MB for the biggest blasts). */
    java.nio.ByteBuffer save() {
        compact();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8 + this.size * SAVED_BYTES);
        buf.putInt(SAVED_VERSION).putInt(this.size);
        for (int k = 0; k < this.size; k++) {
            buf.put(this.face[k]).put(this.mode[k]).putFloat(this.u[k]).putFloat(this.v[k]).putFloat(this.s[k]).putFloat(this.energy[k])
                    .putFloat(this.grain[k]);
        }
        return buf.flip();
    }

    /** Rays saved by {@link #save()}. */
    static RayField load(java.nio.ByteBuffer buf) {
        RayField f = new RayField();
        java.nio.ByteBuffer b = buf.duplicate();
        int n = b.remaining() >= 4 ? b.getInt() : 0;
        boolean grained = n == SAVED_VERSION;
        if (grained) {
            n = b.remaining() >= 4 ? b.getInt() : 0;
        }
        int bytes = grained ? SAVED_BYTES : SAVED_BYTES - 4;
        f.ensure(Math.max(0, n));
        for (int i = 0; i < n && b.remaining() >= bytes; i++) {
            byte fc = b.get(), m = b.get();
            float cu = b.getFloat(), cv = b.getFloat(), cs = b.getFloat(), e = b.getFloat();
            f.add(fc, cu, cv, cs, e, grained ? b.getFloat() : 1, m);
        }
        return f;
    }

    /** Drops dead rays, keeping the rest in order. */
    void compact() {
        int w = 0;
        for (int k = 0; k < this.size; k++) {
            if (this.mode[k] == DEAD) {
                continue;
            }
            if (w != k) {
                this.face[w] = this.face[k];
                this.u[w] = this.u[k];
                this.v[w] = this.v[k];
                this.s[w] = this.s[k];
                this.dx[w] = this.dx[k];
                this.dy[w] = this.dy[k];
                this.dz[w] = this.dz[k];
                this.energy[w] = this.energy[k];
                this.grain[w] = this.grain[k];
                this.mode[w] = this.mode[k];
            }
            w++;
        }
        this.size = w;
    }
}
