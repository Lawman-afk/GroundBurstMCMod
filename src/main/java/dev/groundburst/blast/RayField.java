package dev.groundburst.blast;

import java.util.Arrays;

/**
 * The rays of one blast (DESIGN.md §11.2), stored as parallel arrays so hundreds of thousands of rays stay compact.
 *
 * <p>Rays start on a cube-sphere grid: each of the 6 cube faces carries a 4x4 grid of cells in face coordinates
 * (u, v) in [-1, 1]; a ray points through its cell's centre. As the front moves out the gap between neighbouring rays
 * grows with the radius; when it would pass {@link #SPLIT_SPACING} blocks a ray splits into the 2x2 rays of its
 * quartered cell, each inheriting its energy, so every block of every shell is reached with no holes. A ray splits
 * when the front reaches it at that radius ({@link #needsSplit}), inside the blast's budgeted loop: a whole generation
 * splitting at once used to stall a big blast's tick (slice 8d's benchmark).
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
    /** Past this many rays the arrays grow straight to their limit, so a big blast copies them once, not every doubling. */
    private static final int GROW_TO_LIMIT = 200_000;
    static final byte CRATER = 0, PRESSURE = 1, DEAD = 2;

    /** Face normals and the two tangents spanning each face. */
    private static final float[][] N = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final float[][] A = {{0, 1, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}};
    private static final float[][] B = {{0, 0, 1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 1, 0}};

    int size;
    /** Rays killed since the last {@link #compact()}. */
    int dead;
    byte[] face = new byte[0];
    float[] u = new float[0], v = new float[0], s = new float[0];
    float[] dx = new float[0], dy = new float[0], dz = new float[0];
    float[] energy = new float[0];
    /** Strength of the ray's direction (1 on average): multiplies its energy and its crater reach. */
    float[] grain = new float[0];
    /** The radius past which the ray is too far from its neighbours and must split. */
    float[] splitAt = new float[0];
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
        // Its gap to its neighbours at radius r is r * cell size * stretch: it must split once that passes the spacing.
        float d = 1 + cu * cu + cv * cv;
        float stretch = (float) Math.max(Math.sqrt(1 + cu * cu), Math.sqrt(1 + cv * cv)) / d;
        this.splitAt[k] = SPLIT_SPACING / (cs * stretch);
        return k;
    }

    private void ensure(int n) {
        if (n <= this.face.length) {
            return;
        }
        int cap = n > GROW_TO_LIMIT ? Math.max(n, MAX_RAYS + 8) : Math.max(n, this.face.length * 2 + 16);
        this.face = Arrays.copyOf(this.face, cap);
        this.u = Arrays.copyOf(this.u, cap);
        this.v = Arrays.copyOf(this.v, cap);
        this.s = Arrays.copyOf(this.s, cap);
        this.dx = Arrays.copyOf(this.dx, cap);
        this.dy = Arrays.copyOf(this.dy, cap);
        this.dz = Arrays.copyOf(this.dz, cap);
        this.energy = Arrays.copyOf(this.energy, cap);
        this.grain = Arrays.copyOf(this.grain, cap);
        this.splitAt = Arrays.copyOf(this.splitAt, cap);
        this.mode = Arrays.copyOf(this.mode, cap);
    }

    /** How many rays the arrays have room for. */
    int room() {
        return this.face.length;
    }

    /** Ends ray {@code k}. */
    void kill(int k) {
        if (this.mode[k] != DEAD) {
            this.mode[k] = DEAD;
            this.dead++;
        }
    }

    /** Whether live ray {@code k} is too far from its neighbours at radius {@code r} (and there is room to split it). */
    boolean needsSplit(int k, float r) {
        return r > this.splitAt[k] && this.size + 4 <= MAX_RAYS;
    }

    /**
     * Splits ray {@code k} into the four rays of its quartered cell, added at the end (the shell in hand reaches them
     * after the rest). A child takes its grain from {@code grain} and its parent's energy rescaled by the two grains.
     */
    void split(int k, DirectionalEnergy grain) {
        float q = this.s[k] / 4, half = this.s[k] / 2;
        int fc = this.face[k];
        float cu = this.u[k], cv = this.v[k], e = this.energy[k] / this.grain[k];
        byte m = this.mode[k];
        kill(k);                                           // replaced by its four children
        for (int c = 0; c < 4; c++) {
            int child = add(fc, cu + ((c & 1) == 0 ? -q : q), cv + ((c & 2) == 0 ? -q : q), half, 0, 1, m);
            float g = grain.at(this.dx[child], this.dy[child], this.dz[child]);
            this.grain[child] = g;
            this.energy[child] = e * g;
        }
    }

    /** Bytes per saved ray: face and mode, then u, v, cell size, energy and grain (directions are worked out again on load). */
    private static final int SAVED_BYTES = 2 + 5 * 4;
    /** Marks the current save layout (older saves, without grain, start with the ray count). */
    private static final int SAVED_VERSION = -2;

    /**
     * The live rays, packed for saving with the blast (a few tens of MB for the biggest). It leaves the arrays as they
     * are: a world saved part way through a shell must not move rays under the shell's cursor.
     */
    java.nio.ByteBuffer save() {
        int live = 0;
        for (int k = 0; k < this.size; k++) {
            live += this.mode[k] == DEAD ? 0 : 1;
        }
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8 + live * SAVED_BYTES);
        buf.putInt(SAVED_VERSION).putInt(live);
        for (int k = 0; k < this.size; k++) {
            if (this.mode[k] != DEAD) {
                buf.put(this.face[k]).put(this.mode[k]).putFloat(this.u[k]).putFloat(this.v[k]).putFloat(this.s[k]).putFloat(this.energy[k])
                        .putFloat(this.grain[k]);
            }
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
                this.splitAt[w] = this.splitAt[k];
                this.mode[w] = this.mode[k];
            }
            w++;
        }
        this.size = w;
        this.dead = 0;
    }
}
