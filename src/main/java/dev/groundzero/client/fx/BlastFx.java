package dev.groundzero.client.fx;

import java.util.ArrayList;
import java.util.List;

import dev.groundzero.blast.BlastPayloads;
import dev.groundzero.blast.StreakNoise;
import dev.groundzero.client.blast.ClientBlasts;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * One blast as the client shows it (DESIGN.md §12): the timeline of its fireball, flash, shock front, debris, sparks,
 * smoke and sky tint, all measured from the moment it went off. The shock front follows the server's (sent every 4
 * ticks), predicted forward between updates; an instant blast's front is drawn racing out at a visual speed.
 */
final class BlastFx {
    /** How fast an instant blast's front is drawn to move, in blocks a tick. */
    private static final float INSTANT_FRONT = 3f;

    final BlastPayloads.Start start;
    final FxPreset preset;
    final int noiseSeed;
    final RandomSource random;
    /** Client time (ticks) of the moment it went off. */
    final double born;
    /** Its distance from the camera when the client heard of it, and that distance in its own reaches. */
    final double distance, reaches;
    /** Peak flash strength here (after distance, line of sight and which way the camera faced). */
    float flash;
    float serverRadius;
    boolean ended;
    private boolean shook;
    private float dustRadius;
    private int smokeSent;
    private boolean fireballSmoked;
    /** Blocks sampled where the crater will be, released as debris when the front passes them. */
    private final List<Candidate> candidates = new ArrayList<>();

    private record Candidate(double x, double y, double z, float r, BlockState state, int tint) {}

    BlastFx(BlastPayloads.Start start, double now, ClientLevel level, Vec3 camera) {
        this.start = start;
        this.preset = FxPreset.byName(start.visual());
        this.noiseSeed = StreakNoise.seed(start.seed());
        this.random = RandomSource.create(start.seed() ^ 0x9E3779B97F4A7C15L);
        this.born = now - Math.max(0, level.getGameTime() - start.startTick());
        this.distance = camera.distanceTo(start.center());
        this.reaches = this.distance / Math.max(8, start.reach());
        double t = now - this.born;
        if (t > 20) {
            // Heard of late (logged in or arrived while it runs): show what is still going, not a replay of what passed.
            this.shook = true;
            this.dustRadius = frontRadius(t);
            this.fireballSmoked = t > this.preset.fireballLife() * 0.55;
            this.smokeSent = smokeDue(t);
        } else {
            sampleCrater(level);
        }
    }

    double age(double now) {
        return now - this.born;
    }

    float crater() {
        return Math.max(1, this.start.crater());
    }

    float fireballRadius() {
        return Math.max(1.5f, this.preset.fireball() * crater());
    }

    /** The shock front's radius at {@code t} ticks: the server's, moved on between its updates. */
    float frontRadius(double t) {
        float speed = this.start.frontSpeed() > 0 ? this.start.frontSpeed() : INSTANT_FRONT;
        float predicted = (float) Math.min(this.start.outer(), t * speed);
        if (this.start.frontSpeed() > 0 && !this.ended) {
            predicted = Math.min(predicted, this.serverRadius + speed * 5);
        }
        return Math.max(0, predicted);
    }

    /** How long it has anything left to show (puffs it made live on by themselves). */
    boolean finished(double now) {
        double t = age(now);
        FxPreset p = this.preset;
        return this.ended && t > p.fireballLife() + 5 && t > p.smokeTicks() && t > p.skyTicks() && this.candidates.isEmpty()
                && frontRadius(t) >= this.start.outer() - 0.5f && t > p.flashTicks() * 4 + 10;
    }

    // --- set-up ----------------------------------------------------------------------------------------------------------------
    /** Picks the blocks the debris will be made of, from where the crater will be (still standing on this client). */
    private void sampleCrater(ClientLevel level) {
        int want = (int) (this.preset.debris() * 1.6f);
        float r = Math.min(crater(), 40);
        Vec3 c = this.start.center();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < want * 4 && this.candidates.size() < want; i++) {
            double gx = this.random.nextGaussian(), gy = this.random.nextGaussian(), gz = this.random.nextGaussian();
            double n = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-6;
            float d = r * (float) Math.cbrt(this.random.nextFloat()) * 0.95f;
            double x = c.x + gx / n * d, y = c.y + gy / n * d, z = c.z + gz / n * d;
            pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) {
                continue;
            }
            this.candidates.add(new Candidate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, d, state, Debris.tint(state, level, pos)));
        }
    }

    /** Effects that start the moment it goes off: the fireball's body and its sparks. */
    void ignite(Puffs puffs, double now, float quality) {
        Vec3 c = this.start.center();
        float big = fireballRadius();
        int life = this.preset.fireballLife();
        float rise = this.preset.fireballRise() * crater() / 20;
        for (int i = 0; i < this.preset.glow() * quality; i++) {
            Puffs.Puff p = new Puffs.Puff();
            boolean hot = i % 4 == 0;                      // one in four adds light; the rest is the fire's body
            p.kind = hot ? Puffs.Kind.GLOW : Puffs.Kind.FIRE;
            float[] d = direction(0.2f);
            float reach = big * (0.25f + 0.55f * this.random.nextFloat());
            p.drag = 0.22f;
            p.x = c.x + d[0] * big * 0.1;
            p.y = c.y + d[1] * big * 0.1;
            p.z = c.z + d[2] * big * 0.1;
            p.vx = d[0] * reach * p.drag;
            p.vy = d[1] * reach * p.drag;
            p.vz = d[2] * reach * p.drag;
            p.rise = rise;
            p.birth = this.born;
            p.life = (int) (life * (0.7f + 0.6f * this.random.nextFloat()));
            p.size0 = big * 0.35f;
            p.size1 = big * (0.75f + 0.35f * this.random.nextFloat());
            p.color0 = hot ? 0xFFE8B0 : 0xFFD488;
            p.color1 = hot ? 0x6A2008 : 0x2E140A;
            p.alpha = hot ? 0.35f : 0.8f;
            p.variant = this.random.nextInt(4);
            p.rot = this.random.nextFloat() * 6.283f;
            p.spin = (this.random.nextFloat() - 0.5f) * 0.04f;
            puffs.add(p, quality);
        }
        float speed = (float) Math.sqrt(crater()) * 0.35f;
        for (int i = 0; i < this.preset.embers() * quality; i++) {
            Puffs.Puff p = new Puffs.Puff();
            p.kind = Puffs.Kind.SPARK;
            float[] d = direction(0.45f);
            float v = speed * (0.5f + this.random.nextFloat());
            p.drag = 0.03f;
            p.gravity = 0.03f;
            p.x = c.x;
            p.y = c.y;
            p.z = c.z;
            p.vx = d[0] * v;
            p.vy = d[1] * v;
            p.vz = d[2] * v;
            p.floor = c.y - crater();
            p.birth = this.born;
            p.life = 18 + this.random.nextInt(30) + (int) Math.min(40, crater());
            p.size0 = p.size1 = (0.18f + 0.2f * this.random.nextFloat()) * (float) Math.sqrt(Math.max(1, crater() / 4));
            p.color0 = 0xFFE8B0;
            p.color1 = 0xE04010;
            p.alpha = 1;
            puffs.add(p, quality);
        }
    }

    /** A random unit direction, leaning upward by {@code lift}. */
    private float[] direction(float lift) {
        double gx = this.random.nextGaussian(), gy = this.random.nextGaussian() + lift * 2, gz = this.random.nextGaussian();
        double n = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-6;
        return new float[] {(float) (gx / n), (float) (gy / n), (float) (gz / n)};
    }

    // --- each tick -------------------------------------------------------------------------------------------------------------
    void tick(ClientLevel level, Puffs puffs, Debris debris, double now, float quality, Vec3 camera) {
        double t = age(now);
        float front = frontRadius(t);
        releaseDebris(level, debris, front, quality);
        dust(level, puffs, front, now, quality);
        smoke(level, puffs, t, now, quality);
        if (!this.shook && t >= this.distance / ClientBlasts.SOUND_BLOCKS_PER_TICK) {
            this.shook = true;                             // the shock arrives with the sound
            Spectacle.addTrauma(this.preset.shake() / (1 + (float) (this.reaches * this.reaches) / 6));
        }
    }

    private void releaseDebris(ClientLevel level, Debris debris, float front, float quality) {
        Vec3 c = this.start.center();
        float speed = (float) Math.sqrt(crater()) * 0.32f;
        int keep = (int) (this.preset.debris() * quality);
        for (var it = this.candidates.iterator(); it.hasNext(); ) {
            Candidate k = it.next();
            if (k.r() > front + 0.5f) {
                continue;
            }
            it.remove();
            if (keep <= 0 || this.random.nextFloat() > 0.65f) {
                continue;
            }
            double dx = k.x() - c.x, dy = k.y() - c.y, dz = k.z() - c.z, n = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-3;
            // Thrown outward and upward, hardest along the crater's strong spokes.
            float spoke = 1 + 0.6f * StreakNoise.at((float) (dx / n), 0, (float) (dz / n), this.noiseSeed);
            float v = speed * spoke * (0.45f + 0.75f * this.random.nextFloat());
            double ux = dx / n * 0.7, uz = dz / n * 0.7, uy = 0.55 + Math.max(0, dy / n) * 0.4 + this.random.nextFloat() * 0.35;
            double un = Math.sqrt(ux * ux + uy * uy + uz * uz);
            float size = Math.min(0.85f, (0.2f + 0.3f * this.random.nextFloat()) * (1 + crater() / 40));
            debris.add(new Debris.Chunk(k.state(), k.tint(), this.random, k.x(), Math.max(k.y(), c.y), k.z(),
                    ux / un * v, uy / un * v, uz / un * v, size), quality);
        }
    }

    /** Dust kicked up where the shock front sweeps the ground. */
    private void dust(ClientLevel level, Puffs puffs, float front, double now, float quality) {
        float outer = this.start.outer();
        float step = Math.max(1.5f, outer / 40);
        Vec3 c = this.start.center();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (this.dustRadius + step <= front && this.dustRadius < outer) {
            this.dustRadius += step;
            float r = this.dustRadius;
            int count = (int) Math.min(48, Math.ceil(2 * Math.PI * r * this.preset.dust() * quality));
            float scale = 1 + crater() / 12;
            for (int i = 0; i < count; i++) {
                double az = (i + this.random.nextFloat()) / count * 2 * Math.PI;
                double x = c.x + Math.cos(az) * r, z = c.z + Math.sin(az) * r;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                if (Math.abs(ground - c.y) > crater() + outer * 0.35) {
                    continue;                              // a cliff or a deep valley: no ground to sweep here
                }
                pos.set(Math.floor(x), ground - 1, Math.floor(z));
                MapColor map = level.getBlockState(pos).getMapColor(level, pos);
                // Grass and plants are torn up: the dust is the soil under them.
                int earth = map == MapColor.GRASS || map == MapColor.PLANT || map == MapColor.NONE ? MapColor.DIRT.col : map.col;
                Puffs.Puff p = new Puffs.Puff();
                p.kind = Puffs.Kind.DUST;
                p.x = x;
                p.y = ground + 0.4 + scale * 0.3;
                p.z = z;
                float out = (0.12f + 0.2f * this.random.nextFloat()) * (float) Math.sqrt(scale);
                p.drag = 0.05f;
                p.vx = (float) Math.cos(az) * out;
                p.vz = (float) Math.sin(az) * out;
                p.vy = 0.02f + 0.04f * this.random.nextFloat();
                p.rise = 0.004f;
                p.birth = now;
                p.life = (int) ((40 + this.random.nextInt(50)) * Math.min(2.5f, 0.8f + crater() / 30));
                p.size0 = 0.8f * scale;
                p.size1 = (3f + 1.5f * this.random.nextFloat()) * scale * (1 - 0.45f * r / outer);
                p.color0 = Puffs.lerpRgb(earth, 0xB0A898, 0.35f);
                p.color1 = Puffs.lerpRgb(earth, 0xC8C2B8, 0.6f);
                p.alpha = 0.55f * (1 - 0.6f * r / outer);
                p.variant = this.random.nextInt(3);
                p.rot = this.random.nextFloat() * 6.283f;
                p.spin = (this.random.nextFloat() - 0.5f) * 0.02f;
                puffs.add(p, quality);
            }
        }
    }

    /** Smoke: the fireball turning into a dark cloud, then a plume rising off the crater. */
    private void smoke(ClientLevel level, Puffs puffs, double t, double now, float quality) {
        FxPreset pr = this.preset;
        Vec3 c = this.start.center();
        float big = fireballRadius();
        float windX = 0.012f + 0.01f * (this.noiseSeed & 7) / 7f, windZ = 0.006f * (((this.noiseSeed >> 3) & 7) - 3.5f) / 3.5f;
        if (!this.fireballSmoked && t >= pr.fireballLife() * 0.55) {
            this.fireballSmoked = true;
            double cy = c.y + pr.fireballRise() * crater() / 20 * t;
            for (int i = 0; i < pr.smoke() * 0.35f * quality; i++) {
                float[] d = direction(0.3f);
                float at = big * (float) Math.cbrt(this.random.nextFloat());
                Puffs.Puff p = smokePuff(now, pr, windX, windZ);
                p.x = c.x + d[0] * at;
                p.y = cy + d[1] * at * 0.8;
                p.z = c.z + d[2] * at;
                p.vx = d[0] * 0.05f;
                p.vz = d[2] * 0.05f;
                p.rise = pr.fireballRise() * crater() / 20 * 0.35f + 0.02f;
                p.size0 = big * 0.7f;
                p.size1 = big * (1.3f + 0.5f * this.random.nextFloat());
                p.color0 = 0x32261E;
                puffs.add(p, quality);
            }
        }
        // The plume off the crater, thinning out as it goes on.
        int due = smokeDue(t);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (this.smokeSent < due) {
            this.smokeSent++;
            if (this.random.nextFloat() > quality) {
                continue;
            }
            double a = this.random.nextFloat() * 2 * Math.PI, d = Math.sqrt(this.random.nextFloat()) * crater() * 0.75;
            double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
            pos.set(Math.floor(x), Math.floor(c.y), Math.floor(z));
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
            Puffs.Puff p = smokePuff(now, pr, windX, windZ);
            p.x = x;
            p.y = Math.min(c.y + crater() * 0.3, Math.max(ground + 0.5, c.y - crater() * 0.5));
            p.z = z;
            p.vy = 0.05f;
            p.rise = 0.03f + 0.06f * this.random.nextFloat() * (float) Math.sqrt(crater() / 6);
            p.size0 = Math.max(1.2f, crater() * 0.25f);
            p.size1 = Math.max(3f, crater() * (0.55f + 0.3f * this.random.nextFloat()));
            puffs.add(p, 1);
        }
    }

    /** How many plume puffs should have risen by {@code t} ticks (quickly at first, then thinning out). */
    private int smokeDue(double t) {
        FxPreset pr = this.preset;
        return (int) Math.min(pr.smoke(), pr.smoke() * Math.sqrt(Math.min(1, t / Math.max(1, pr.smokeTicks()))));
    }

    private Puffs.Puff smokePuff(double now, FxPreset pr, float windX, float windZ) {
        Puffs.Puff p = new Puffs.Puff();
        p.kind = Puffs.Kind.SMOKE;
        p.drag = 0.08f;
        p.windX = windX;
        p.windZ = windZ;
        p.birth = now;
        p.life = (int) (pr.smokeLife() * (0.6f + 0.6f * this.random.nextFloat()));
        p.color0 = 0x4E4842;
        p.color1 = 0xA29E98;
        p.alpha = 0.45f;
        p.variant = this.random.nextInt(8);
        p.rot = this.random.nextFloat() * 6.283f;
        p.spin = (this.random.nextFloat() - 0.5f) * 0.01f;
        return p;
    }

    // --- flash and sky ---------------------------------------------------------------------------------------------------------
    /** Flash brightness at {@code t} ticks (0 to 1). */
    float flashAt(double t) {
        if (this.flash <= 0 || t < 0) {
            return 0;
        }
        double hold = 1.5;
        return (float) (t < hold ? this.flash : this.flash * Math.exp(-(t - hold) / (this.preset.flashTicks() * 0.5)));
    }

    /** How far the sky is tinted by this blast at {@code t} ticks (0 to 1). */
    float skyAt(double t) {
        FxPreset p = this.preset;
        if (p.skyTint() <= 0) {
            return 0;
        }
        double near = 1 / (1 + Math.pow(this.distance / (Math.max(8, this.start.reach()) * 6), 2));
        double rise = Math.min(1, t / 100), fall = t < p.skyTicks() * 0.4 ? 1 : Math.max(0, 1 - (t - p.skyTicks() * 0.4) / (p.skyTicks() * 0.6));
        return (float) (p.skyTint() * near * rise * fall * fall);
    }
}
