package dev.groundburst.client.fx;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.BlastPayloads;
import dev.groundburst.content.naval.Water;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A charge going off under water (DESIGN.md §14.6), as the client shows it. Under the surface, a brief flash and a boil
 * of bubbles. The shock reaches the surface first, a white dome bulging up; then the column of spray stands up out of
 * it, jets streaking up, a head of spray spreading at its top, and falls back as a curtain of spray, the falling water
 * splashing over the sea round its foot, a ring of mist rolling out over the water after it. Its height is about five
 * craters, cut by its depth (under a fifth of that five craters down); a deep charge's dome and column come late, as its
 * gas bubble rises. Spray and mist are lit puffs (lit by the sun and the sky as smoke is), the bubbles and splashes
 * vanilla's particles.
 */
final class WaterColumn {
    private static final int SPRAY = 0xF4F7F8, SPRAY_SHADE = 0xC6D0D4, MIST = 0xE4EAEC;
    /** The spray's fall (blocks a tick, a tick) and the drag on its launch. */
    private static final float G = 0.05f, DRAG = 0.012f;

    private final Vec3 burst;
    private final double surface;
    private final float crater;
    /** How high the column stands over the surface, and how fast its spray leaves the surface to reach that. */
    final float height;
    private final float speed;
    /** When the shock (and after it the bubble) reaches the surface: ticks after the burst. */
    private final double rise;
    /** How long the column's spray is in the air (up and back down to the surface). */
    private final double flight;
    private final double born;
    private final RandomSource random;

    /** The column of a blast at {@code start}, or null if this client sees no water there. */
    static @Nullable WaterColumn of(ClientLevel level, BlastPayloads.Start start, double born, RandomSource random) {
        double surface = Water.surface(level, start.center());
        return Double.isNaN(surface) ? null : new WaterColumn(start.center(), surface, Math.max(1, start.crater()), born, random);
    }

    private WaterColumn(Vec3 burst, double surface, float crater, double born, RandomSource random) {
        this.burst = burst;
        this.surface = surface;
        this.crater = crater;
        this.born = born;
        this.random = random;
        double depth = Math.max(0, surface - burst.y);
        this.height = 6f * crater * (float) Mth.clamp(1 - depth / (5 * crater), 0.15, 1);
        this.speed = (float) Math.sqrt(2 * G * this.height) * 1.2f;     // (the drag takes a fifth of its rise)
        this.rise = depth * 0.6;
        this.flight = 2 * this.speed / G;
    }

    boolean done(double t) {
        return t > this.rise + this.flight + 200;
    }

    /** Everything it throws up, laid out at once (each puff born when its part of the column comes). */
    void ignite(Puffs puffs, float quality) {
        double up = this.born + this.rise;
        float s = (float) Math.sqrt(this.crater / 4);
        // Under water: a brief flash at the burst.
        Puffs.Puff flash = new Puffs.Puff();
        flash.kind = Puffs.Kind.GLOW;
        flash.x = this.burst.x;
        flash.y = this.burst.y;
        flash.z = this.burst.z;
        flash.drag = 0;
        flash.birth = this.born;
        flash.life = 6;
        flash.size0 = this.crater * 1.5f;
        flash.size1 = this.crater * 3f;
        flash.color0 = 0xFFF2D8;
        flash.color1 = 0xFF9A50;
        flash.alpha = 0.55f;
        puffs.add(flash, 1);
        // The dome: the surface bulging up white where the shock breaks it.
        for (int i = 0; i < 40 * quality; i++) {
            Puffs.Puff p = spray(up, Puffs.Kind.SMOKE);
            double a = this.random.nextDouble() * 2 * Math.PI, r = Math.sqrt(this.random.nextDouble()) * this.crater * 0.9;
            p.x = this.burst.x + Math.cos(a) * r;
            p.z = this.burst.z + Math.sin(a) * r;
            p.vx = (float) Math.cos(a) * (0.05f + 0.1f * this.random.nextFloat()) * s;
            p.vz = (float) Math.sin(a) * (0.05f + 0.1f * this.random.nextFloat()) * s;
            p.vy = (0.25f + 0.2f * this.random.nextFloat()) * s;
            p.drag = 0.08f;
            p.life = 18 + this.random.nextInt(14);
            p.size0 = this.crater * 0.6f;
            p.size1 = this.crater * 1.3f;
            p.alpha = 0.9f;
            puffs.add(p, quality);
        }
        // The jets: streaks of spray thrown up out of the dome, each a line of puffs along one path; the outer ones fan
        // out, so the column's edges are feathered with them.
        for (int i = 0; i < 44 * quality; i++) {
            double a = this.random.nextDouble() * 2 * Math.PI, out = Math.sqrt(this.random.nextDouble()), r = out * this.crater * 0.7;
            float v = this.speed * (0.7f + 0.35f * this.random.nextFloat());
            float fan = this.speed * (0.03f + 0.11f * (float) out);
            float hx = (float) (Math.cos(a) * fan + this.random.nextGaussian() * this.speed * 0.02);
            float hz = (float) (Math.sin(a) * fan + this.random.nextGaussian() * this.speed * 0.02);
            int parts = 4 + this.random.nextInt(3);
            for (int k = 0; k < parts; k++) {
                Puffs.Puff p = spray(up + 1 + k * 0.8, Puffs.Kind.DUST);
                p.x = this.burst.x + Math.cos(a) * r;
                p.z = this.burst.z + Math.sin(a) * r;
                float f = 1 - 0.1f * k;
                p.vx = hx * f;
                p.vy = v * f;
                p.vz = hz * f;
                p.life = (int) (2 * v * f / G * (0.85f + 0.2f * this.random.nextFloat()));
                p.size0 = 0.6f * s;
                p.size1 = (1.5f + 0.7f * this.random.nextFloat()) * s * (1 + 0.2f * k);
                p.stretch = 2.2f;
                p.alpha = 0.95f - 0.12f * k;
                p.variant = this.random.nextInt(3);
                puffs.add(p, quality);
            }
        }
        // The column's body: spray at every height up it, growing as it goes.
        for (int i = 0; i < 90 * quality; i++) {
            Puffs.Puff p = spray(up + 1 + this.random.nextFloat() * 3, Puffs.Kind.SMOKE);
            double a = this.random.nextDouble() * 2 * Math.PI, r = Math.sqrt(this.random.nextDouble()) * this.crater * 0.6;
            p.x = this.burst.x + Math.cos(a) * r;
            p.z = this.burst.z + Math.sin(a) * r;
            float v = this.speed * (0.35f + 0.65f * this.random.nextFloat());
            p.vx = (float) this.random.nextGaussian() * this.speed * 0.04f;
            p.vz = (float) this.random.nextGaussian() * this.speed * 0.04f;
            p.vy = v;
            p.drag = 0.015f;
            p.life = (int) (2 * v / G * (0.9f + 0.3f * this.random.nextFloat()));
            p.size0 = this.crater * 0.5f;
            p.size1 = this.crater * (1.0f + 0.6f * this.random.nextFloat());
            p.alpha = 0.8f;
            puffs.add(p, quality);
        }
        // Its head: spray reaching the top, spreading out there before it falls.
        for (int i = 0; i < 30 * quality; i++) {
            Puffs.Puff p = spray(up + 2 + this.random.nextFloat() * 2, Puffs.Kind.SMOKE);
            p.x = this.burst.x + this.random.nextGaussian() * this.crater * 0.2;
            p.z = this.burst.z + this.random.nextGaussian() * this.crater * 0.2;
            p.vx = (float) this.random.nextGaussian() * this.speed * 0.12f;
            p.vz = (float) this.random.nextGaussian() * this.speed * 0.12f;
            p.vy = this.speed * (0.95f + 0.1f * this.random.nextFloat());
            p.drag = 0.02f;
            p.life = (int) (this.flight * (1 + 0.15f * this.random.nextFloat()));
            p.size0 = this.crater * 0.8f;
            p.size1 = this.crater * (2.0f + 0.8f * this.random.nextFloat());
            p.alpha = 0.7f;
            puffs.add(p, quality);
        }
        // The curtain: the water coming back down in streaks round the column as it falls.
        for (int i = 0; i < 50 * quality; i++) {
            Puffs.Puff p = spray(up + this.flight * (0.45 + 0.35 * this.random.nextDouble()), Puffs.Kind.DUST);
            double a = this.random.nextDouble() * 2 * Math.PI, r = this.crater * (0.4 + 0.9 * this.random.nextDouble());
            p.x = this.burst.x + Math.cos(a) * r;
            p.z = this.burst.z + Math.sin(a) * r;
            p.y = this.surface + this.height * (0.4 + 0.6 * this.random.nextDouble());
            p.vy = -0.1f - 0.2f * this.random.nextFloat();
            p.life = 50;
            p.size0 = p.size1 = (0.4f + 0.4f * this.random.nextFloat()) * s;
            p.stretch = 2f;
            p.alpha = 0.5f;
            p.variant = this.random.nextInt(3);
            puffs.add(p, quality);
        }
        // The base surge: a ring of mist rolling out over the water as the column comes down.
        for (int i = 0; i < 56 * quality; i++) {
            Puffs.Puff p = spray(up + this.flight * (0.7 + 0.3 * this.random.nextDouble()), Puffs.Kind.SMOKE);
            double a = this.random.nextDouble() * 2 * Math.PI, r = this.crater * (0.4 + 0.7 * this.random.nextDouble());
            p.x = this.burst.x + Math.cos(a) * r;
            p.z = this.burst.z + Math.sin(a) * r;
            p.y = this.surface + 0.5 * s;
            float out = (0.16f + 0.22f * this.random.nextFloat()) * s;
            p.vx = (float) Math.cos(a) * out;
            p.vz = (float) Math.sin(a) * out;
            p.vy = 0;
            p.gravity = 0;
            p.rise = 0.003f;
            p.drag = 0.04f;
            p.life = 130 + this.random.nextInt(70);
            p.size0 = this.crater * 1.1f;
            p.size1 = this.crater * (2.8f + 1.2f * this.random.nextFloat());
            p.color0 = MIST;
            p.color1 = MIST;
            p.alpha = 0.5f;
            puffs.add(p, quality);
        }
    }

    /** A spray puff born at {@code birth}: white, falling, stopping on the surface, shaded about the column. */
    private Puffs.Puff spray(double birth, Puffs.Kind kind) {
        Puffs.Puff p = new Puffs.Puff();
        p.kind = kind;
        p.y = this.surface;
        p.birth = birth;
        p.gravity = G;
        p.drag = DRAG;
        p.floor = this.surface - 0.2;
        p.color0 = SPRAY;
        p.color1 = SPRAY_SHADE;
        p.variant = this.random.nextInt(8);
        p.rot = this.random.nextFloat() * 6.283f;
        p.spin = (this.random.nextFloat() - 0.5f) * 0.02f;
        p.axisX = this.burst.x;
        p.axisZ = this.burst.z;
        return p;
    }

    /** Each tick: bubbles boiling up from the burst, the water's foam and the splashes of the spray falling back. */
    void tick(ClientLevel level, double t, float quality) {
        double boil = this.rise + 60;
        if (t >= 0 && t < boil) {
            int n = (int) (5 * quality * (1 - t / boil)) + 1;
            for (int i = 0; i < n; i++) {
                double a = this.random.nextDouble() * 2 * Math.PI, r = Math.sqrt(this.random.nextDouble()) * this.crater * 0.8;
                double x = this.burst.x + Math.cos(a) * r, z = this.burst.z + Math.sin(a) * r;
                double y = this.burst.y + (this.surface - this.burst.y) * this.random.nextDouble();
                if (y < this.surface - 0.2) {
                    level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 0, 0.1, 0);
                }
            }
        }
        double after = t - this.rise;
        if (after >= 0 && after < this.flight + 60) {
            boolean falling = after > this.flight * 0.5;
            int n = (int) ((falling ? 7 : 3) * quality) + 1;
            for (int i = 0; i < n; i++) {
                double a = this.random.nextDouble() * 2 * Math.PI;
                double r = Math.sqrt(this.random.nextDouble()) * this.crater * (falling ? 1.7 : 1.1);
                double x = this.burst.x + Math.cos(a) * r, z = this.burst.z + Math.sin(a) * r;
                level.addParticle(falling ? ParticleTypes.SPLASH : ParticleTypes.FISHING, x, this.surface + 0.05, z, 0, 0.1, 0);
            }
        }
    }
}
