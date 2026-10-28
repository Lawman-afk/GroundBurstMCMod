package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A nuclear mushroom cloud (DESIGN.md §12.2), simulated on the client as a few thousand soft cloudlets.
 *
 * <p><b>Shape.</b> The cap is a toroidal vortex: a core circle of radius {@code R} at height {@code capY}, both growing
 * (the cap climbs to {@code H} = 2 craters above the burst, approached exponentially; the ring widens to 0.42 H). Each
 * cap cloudlet has its own shell, a fraction of the minor radius {@code r = 0.55 R}, and is pulled onto it while it is
 * carried round the core circle: up the inside, out over the top, down the outside, as rising fireballs roll. The roll
 * slows over a minute or so. The stem is fed from the ground under the burst: cloudlets drawn inward and up (a wide
 * skirt at the foot, narrowing above) until they reach the cap's underside and join its roll from the inside. The stem
 * stands on the crater's floor, and a ring of dust rolls slowly outward round its foot, its lower half down in the
 * crater bowl, so the cloud does not float over the hole it dug (after the in-game look). A white collar rings the stem
 * under the cap, and for the first seconds a white condensation shell (the Wilson cloud) spreads round the fireball and
 * evaporates.
 *
 * <p><b>Look.</b> Cloudlets are lit like clouds ({@link FxLight}): a normal out from the core circle (cap) or the axis
 * (stem), sunlight from the sun's direction, the sky's colour in the shade, undersides darker. Near the core the cap
 * glows: hot yellow-orange at first, cooling through red to nothing over about a minute. The cloud stands still over
 * ground zero all its life: no wind drift (after the in-game looks: drifting, it slid off its crater). It stands at full
 * strength until the blast has finished digging its crater (the server's end of the blast: a Tsar's takes minutes),
 * and only then does its countdown start: it lasts 2.5 minutes from there, thinning out over the last 45 seconds. It is
 * drawn with the far-cloud pipeline, so it shows above the horizon from well beyond the render distance.
 */
final class MushroomCloud {
    private enum Role { CAP, STEM, COLLAR, SHELL, BASE }

    private static final class Cloudlet {
        Role role;
        double x, y, z, px, py, pz;
        /** Cap: the fraction of the minor radius it rolls at. Stem: of the stem radius. Collar: of R. */
        float shell;
        /** Size relative to its role's scale, and its texture variant. */
        float scale;
        int variant;
        double birth;
        int life;
        /** Azimuth (collar, shell, base ring) and elevation (shell); the base ring's roll round its minor circle. */
        float phi, elev, jitter, roll;
        float rot;
    }

    private final Vec3 center;
    /** The ground's height under the burst, and the crater floor's (the stem stands on the floor). */
    private final double ground, floor;
    private final float crater;
    private final float fireball, height, r0, r1;
    /** How long it lasts once its countdown has started. */
    private final int life;
    /** When its countdown started (ticks after the burst): NaN while it waits for the crater to be finished. */
    private double countdown = Double.NaN;
    /** The longest it waits for the crater, should the blast's end never reach this client: a quarter of an hour. */
    private static final int MAX_HOLD = 20 * 60 * 15;
    private final float quality;
    private final RandomSource random;
    private final int earth;
    private final List<Cloudlet> cloudlets = new ArrayList<>();
    private int stemSent, capSent, collarSent, baseSent;
    /** Grey dust, barely glowing (a huge conventional blast), rather than a nuclear fire cloud. */
    private final boolean dusty;
    private boolean shellSent;
    private final int capCount, stemCount, collarCount, shellCount, baseCount;

    /**
     * @param fireball the fireball's radius
     * @param crater   the crater radius (sets the cloud's height)
     * @param ground   the ground's height under the burst
     * @param floor    the crater floor's height (the ground's, for a burst that digs none)
     * @param earth    the colour of the ground (the stem is that dust)
     * @param dusty    a grey dust cloud that hardly glows (unused since the Massive Ordnance Warhead lost its mushroom), not a nuclear fire cloud
     */
    MushroomCloud(Vec3 center, double ground, double floor, float fireball, float crater, long seed, float quality, int earth, int life,
            boolean dusty) {
        this.dusty = dusty;
        this.center = center;
        this.ground = Math.min(ground, center.y);
        this.floor = Math.min(this.ground, floor);
        this.crater = crater;
        this.fireball = fireball;
        this.height = Math.max(40, Math.min(520, crater * 2));       // the Tsar's climbs to 500
        this.r0 = fireball * 0.55f;
        this.r1 = this.height * 0.42f;
        this.random = RandomSource.create(seed ^ 0x6C8E9CF570932BD5L);
        this.life = life;
        this.quality = quality;
        this.earth = earth;
        this.capCount = (int) (900 * quality);
        this.stemCount = (int) (900 * quality);
        this.collarCount = (int) (260 * quality);
        this.shellCount = (int) (220 * quality);
        this.baseCount = (int) (380 * quality);
    }

    // --- the shape over time ---------------------------------------------------------------------------------------------------
    private static final double RISE = 260, WIDEN = 420, SPIN_FADE = 1200, COOL = 550;
    /** The cap's own cloudlets last as long as the cloud (it may wait a long time for its crater): they go with it. */
    private static final int CAP_LIFE = Integer.MAX_VALUE / 2;

    double capY(double t) {
        return this.center.y + this.height * (1 - Math.exp(-t / RISE));
    }

    private double climb(double t) {
        return this.height / RISE * Math.exp(-t / RISE);
    }

    double ringRadius(double t) {
        return this.r0 + (this.r1 - this.r0) * (1 - Math.exp(-t / WIDEN));
    }

    private double widen(double t) {
        return (this.r1 - this.r0) / WIDEN * Math.exp(-t / WIDEN);
    }

    private double minor(double t) {
        return 0.55 * ringRadius(t);
    }

    private double spin(double t) {
        return 2 * Math.PI / 110 * Math.exp(-t / SPIN_FADE);
    }

    /** The cap's centre at {@code t}, straight over ground zero (the fireball rides it). */
    Vec3 capCentre(double t) {
        return new Vec3(this.center.x, capY(t), this.center.z);
    }

    /** How hot the cloud still is (1 at the burst, cooling over about a minute). */
    private double heat(double t) {
        return Math.exp(-t / COOL);
    }

    /** The blast has finished (its crater is dug): the countdown starts now, once. */
    void release(double t) {
        if (Double.isNaN(this.countdown)) {
            this.countdown = Math.min(t, MAX_HOLD);
        }
    }

    /** When it will be gone (ticks after the burst): a life after its countdown started; while it waits, always a life ahead. */
    private double end(double t) {
        return (Double.isNaN(this.countdown) ? Math.min(t, MAX_HOLD) : this.countdown) + this.life;
    }

    /** The whole cloud's opacity: thinning out over the last 30% of its countdown. */
    private double fade(double t) {
        return Math.max(0, Math.min(1, (end(t) - t) / (this.life * 0.3)));
    }

    /** The stem is fed from the ground until the cloud starts thinning out. */
    private double stemFeed(double t) {
        return end(t) - this.life * 0.3;
    }

    boolean done(double t) {
        return t >= end(t);
    }

    /** The base ring's major radius (round the axis) and minor radius, at {@code t}: it spreads out a little as it rolls. */
    private double baseRing(double t) {
        return this.crater * (0.55 + 0.35 * (1 - Math.exp(-t / 400)));
    }

    private double baseMinor() {
        return Math.max(4, this.crater * 0.3);
    }

    /** Stem radius at height fraction {@code h} (0 at the crater floor, 1 at the cap's underside): a wide skirt, then a column. */
    private double stemRadius(double t, double h) {
        double big = ringRadius(t);
        return big * (0.3 - 0.12 * h) + big * 0.35 * Math.pow(1 - h, 5);
    }

    // --- simulation ------------------------------------------------------------------------------------------------------------
    /** One tick at {@code t} ticks after the burst: spawn what is due, move everything. */
    void tick(double t) {
        spawn(t);
        double ax = this.center.x, az = this.center.z, capY = capY(t), big = ringRadius(t), small = minor(t);
        double spin = spin(t), climb = climb(t), widen = widen(t);
        double underside = capY - small * 0.6;
        for (int i = 0; i < this.cloudlets.size(); i++) {
            Cloudlet c = this.cloudlets.get(i);
            c.px = c.x;
            c.py = c.y;
            c.pz = c.z;
            double rx = c.x - ax, rz = c.z - az, rho = Math.sqrt(rx * rx + rz * rz);
            double dx = rho > 1e-3 ? rx / rho : Math.cos(c.phi), dz = rho > 1e-3 ? rz / rho : Math.sin(c.phi);
            switch (c.role) {
                case CAP -> {
                    double dr = rho - big, dy = c.y - capY, dist = Math.sqrt(dr * dr + dy * dy) + 1e-4;
                    double pull = 0.035 * (c.shell * small - dist) / dist;
                    double vr = spin * dy + pull * dr + widen * rho / big;
                    double vy = -spin * dr + pull * dy + climb;
                    rho = Math.max(0.5, rho + vr + (this.random.nextFloat() - 0.5f) * 0.08);
                    c.y += vy + (this.random.nextFloat() - 0.5f) * 0.08;
                }
                case STEM -> {
                    double h = Math.max(0, Math.min(1, (c.y - this.floor) / Math.max(1, underside - this.floor)));
                    double up = 0.22 + 1.2 * climb;
                    rho += 0.05 * (c.shell * stemRadius(t, h) - rho);
                    c.y += up;
                    if (c.y >= underside && rho < big) {
                        // It reaches the cap's underside: it joins the roll, rising up the inside.
                        c.role = Role.CAP;
                        double dr = rho - big, dy = c.y - capY;
                        c.shell = (float) Math.max(0.25, Math.min(1, Math.sqrt(dr * dr + dy * dy) / small));
                    }
                }
                case COLLAR -> {
                    rho = big * c.shell;
                    c.y = capY - small * (0.95 + 0.15 * c.jitter);
                    c.phi += 0.0015f;
                    dx = Math.cos(c.phi);
                    dz = Math.sin(c.phi);
                }
                case SHELL -> {
                    double radius = this.fireball * (1.15 + 0.012 * t);
                    rho = radius * Math.cos(c.elev);
                    c.y = this.center.y + radius * Math.sin(c.elev);
                    dx = Math.cos(c.phi);
                    dz = Math.sin(c.phi);
                }
                case BASE -> {
                    // Rolling round its minor circle, out over the top and down into the crater, creeping round the axis.
                    c.roll += 0.004f;
                    c.phi += 0.0007f;
                    double ring = baseRing(t), minor = baseMinor() * c.shell;
                    rho = Math.max(1, ring + Math.cos(c.roll) * minor);
                    c.y = this.ground + 0.15 * baseMinor() + Math.sin(c.roll) * minor * 0.75;
                    dx = Math.cos(c.phi);
                    dz = Math.sin(c.phi);
                }
            }
            c.x = ax + dx * rho;
            c.z = az + dz * rho;
        }
        this.cloudlets.removeIf(c -> t - c.birth >= c.life);
    }

    private void spawn(double t) {
        // The cap: born inside the fireball over its first seconds.
        int capDue = (int) (this.capCount * Math.min(1, t / 40));
        while (this.capSent < capDue) {
            this.capSent++;
            Cloudlet c = cloudlet(Role.CAP, t, CAP_LIFE);
            double[] d = sphere();
            double at = this.fireball * Math.cbrt(this.random.nextDouble());
            c.x = this.center.x + d[0] * at;
            c.y = this.center.y + Math.abs(d[1]) * at * 0.8;
            c.z = this.center.z + d[2] * at;
            c.shell = (float) Math.sqrt(0.08 + 0.92 * this.random.nextDouble());
            c.scale = 0.7f + 0.45f * this.random.nextFloat();
            add(c);
        }
        // The stem: fed from the ground over ground zero, most at first, then a trickle that keeps it standing until the
        // cloud thins out.
        if (t > 12 && t < stemFeed(t)) {
            int stemDue = (int) (this.stemCount * (1 - Math.exp(-(t - 12) / 300)) + (t - 12) * 0.35 * this.quality);
            double ax = this.center.x, az = this.center.z;
            while (this.stemSent < stemDue) {
                this.stemSent++;
                Cloudlet c = cloudlet(Role.STEM, t, 1300 + this.random.nextInt(600));
                c.shell = (float) Math.sqrt(this.random.nextDouble());
                c.phi = this.random.nextFloat() * 6.283f;
                double rho = c.shell * stemRadius(t, 0);
                c.x = ax + Math.cos(c.phi) * rho;
                c.y = this.floor + 0.5 + this.random.nextFloat() * 3;
                c.z = az + Math.sin(c.phi) * rho;
                c.scale = 0.75f + 0.5f * this.random.nextFloat();
                add(c);
            }
        }
        // The ring of dust round the stem's foot, from a couple of seconds in.
        if (t > 30 && t < stemFeed(t)) {
            int baseDue = (int) (this.baseCount * Math.min(1, (t - 30) / 260));
            while (this.baseSent < baseDue) {
                this.baseSent++;
                add(baseCloudlet(t));
            }
        }
        // The collar under the cap, from the second few seconds.
        if (t > 50 && t < 450) {
            int collarDue = (int) (this.collarCount * Math.min(1, (t - 50) / 200));
            while (this.collarSent < collarDue) {
                this.collarSent++;
                Cloudlet c = cloudlet(Role.COLLAR, t, 500 + this.random.nextInt(500));
                c.phi = this.random.nextFloat() * 6.283f;
                c.shell = 0.35f + 0.4f * this.random.nextFloat();
                c.jitter = this.random.nextFloat() * 2 - 1;
                c.scale = 0.6f + 0.4f * this.random.nextFloat();
                add(c);
            }
        }
        // The Wilson condensation shell, all at once, briefly.
        if (!this.shellSent && t > 8) {
            this.shellSent = true;
            for (int i = 0; i < this.shellCount; i++) {
                Cloudlet c = cloudlet(Role.SHELL, t, 120 + this.random.nextInt(30));
                c.phi = this.random.nextFloat() * 6.283f;
                c.elev = (float) Math.asin(0.05 + 0.9 * this.random.nextFloat());
                c.scale = 0.7f + 0.5f * this.random.nextFloat();
                add(c);
            }
        }
    }

    private Cloudlet baseCloudlet(double t) {
        Cloudlet c = cloudlet(Role.BASE, t, 1700 + this.random.nextInt(900));
        c.phi = this.random.nextFloat() * 6.283f;
        c.roll = this.random.nextFloat() * 6.283f;
        c.shell = (float) Math.sqrt(0.15 + 0.85 * this.random.nextDouble());
        c.scale = 0.8f + 0.5f * this.random.nextFloat();
        double rho = baseRing(t) + Math.cos(c.roll) * baseMinor() * c.shell;
        c.x = this.center.x + Math.cos(c.phi) * rho;
        c.y = this.ground + Math.sin(c.roll) * baseMinor() * c.shell * 0.75;
        c.z = this.center.z + Math.sin(c.phi) * rho;
        return c;
    }

    private Cloudlet cloudlet(Role role, double t, int life) {
        Cloudlet c = new Cloudlet();
        c.role = role;
        c.birth = t;
        c.life = life;
        c.variant = this.random.nextInt(4);
        c.rot = (this.random.nextFloat() - 0.5f) * 0.6f;
        return c;
    }

    private void add(Cloudlet c) {
        c.px = c.x;
        c.py = c.y;
        c.pz = c.z;
        this.cloudlets.add(c);
    }

    private double[] sphere() {
        double gx = this.random.nextGaussian(), gy = this.random.nextGaussian(), gz = this.random.nextGaussian();
        double n = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-9;
        return new double[] {gx / n, gy / n, gz / n};
    }

    /**
     * For a client that hears of the blast late: lays the cloud out as it would be at {@code t} ticks (cap cloudlets on
     * their shells, the stem along its column) instead of replaying it.
     */
    void prefill(double t) {
        double ax = this.center.x, az = this.center.z, capY = capY(t), big = ringRadius(t), small = minor(t);
        this.capSent = this.capCount;
        this.shellSent = true;
        this.collarSent = t < 450 ? this.collarCount : this.collarSent;
        for (int i = 0; i < this.capCount + this.stemCount / 2; i++) {
            Cloudlet c = cloudlet(Role.CAP, t - this.random.nextInt(200), CAP_LIFE);
            c.shell = (float) Math.sqrt(0.08 + 0.92 * this.random.nextDouble());
            c.scale = 0.7f + 0.45f * this.random.nextFloat();
            double phi = this.random.nextDouble() * 2 * Math.PI, roll = this.random.nextDouble() * 2 * Math.PI;
            double rho = big + Math.cos(roll) * c.shell * small;
            c.x = ax + Math.cos(phi) * rho;
            c.y = capY + Math.sin(roll) * c.shell * small;
            c.z = az + Math.sin(phi) * rho;
            add(c);
        }
        if (t < stemFeed(t)) {
            this.stemSent = this.stemCount;
            double underside = capY - small * 0.6;
            for (int i = 0; i < this.stemCount / 2; i++) {
                Cloudlet c = cloudlet(Role.STEM, t - this.random.nextInt(100), 1300);
                c.shell = (float) Math.sqrt(this.random.nextDouble());
                c.phi = this.random.nextFloat() * 6.283f;
                double h = this.random.nextDouble();
                double rho = c.shell * stemRadius(t, h);
                c.y = this.floor + h * (underside - this.floor);
                c.x = ax + Math.cos(c.phi) * rho;
                c.z = az + Math.sin(c.phi) * rho;
                c.scale = 0.75f + 0.5f * this.random.nextFloat();
                add(c);
            }
            this.baseSent = this.baseCount;
            for (int i = 0; i < this.baseCount; i++) {
                add(baseCloudlet(t - this.random.nextInt(200)));
            }
        }
    }

    // --- drawing ---------------------------------------------------------------------------------------------------------------
    private static final int CAP_ALBEDO = 0xCCC5BE, CAP_BROWN = 0xC4A68C, COLLAR_ALBEDO = 0xF0F0F2, DUST_ALBEDO = 0x9A948C;

    void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, Quaternionf orientation, double t, float partial) {
        if (this.cloudlets.isEmpty()) {
            return;
        }
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        FxLight light = FxLight.current;
        Vector3f shade = new Vector3f();
        double ax = this.center.x, az = this.center.z, capY = capY(t), big = ringRadius(t), small = minor(t), fade = fade(t);
        double heat = this.dusty ? heat(t) * 0.15 : heat(t);
        double underside = capY - small * 0.6;
        int stemAlbedo = Puffs.lerpRgb(this.earth, 0x8E8680, 0.55f);
        List<float[]> quads = new ArrayList<>(this.cloudlets.size());
        for (Cloudlet c : this.cloudlets) {
            double age = t - c.birth;
            double x = c.px + (c.x - c.px) * partial, y = c.py + (c.y - c.py) * partial, z = c.pz + (c.z - c.pz) * partial;
            double rx = x - ax, rz = z - az, rho = Math.sqrt(rx * rx + rz * rz) + 1e-4;
            double dx = rx / rho, dz = rz / rho;
            float nx, ny, nz, shadow, size, alpha;
            int albedo;
            double glow = 0;
            switch (c.role) {
                case CAP -> {
                    double dr = rho - big, dy = y - capY, dist = Math.sqrt(dr * dr + dy * dy);
                    nx = (float) (dx * dr);
                    ny = (float) dy;
                    nz = (float) (dz * dr);
                    shadow = (float) (0.68 + 0.32 * Math.max(0, Math.min(1, (dy + small) / (2 * small))));
                    size = (float) (c.scale * small * 0.95);
                    alpha = 0.72f;
                    // Reddish-brown while the cap still holds its hot gases, greying as it ages.
                    albedo = this.dusty ? Puffs.lerpRgb(this.earth, DUST_ALBEDO, 0.6f)
                            : Puffs.lerpRgb(CAP_BROWN, CAP_ALBEDO, (float) Math.max(0, Math.min(1, (t - 200) / 800)));
                    // The core glows, hottest at first; for the first seconds the whole cap is the fireball.
                    glow = heat * (Math.exp(-Math.pow(dist / (0.8 * small), 2)) + Math.max(0, 1 - t / 90) * 0.8);
                }
                case STEM -> {
                    double h = Math.max(0, Math.min(1, (y - this.floor) / Math.max(1, underside - this.floor)));
                    nx = (float) dx;
                    ny = 0.3f;
                    nz = (float) dz;
                    shadow = (float) (0.85 - 0.2 * h);           // the cap shades the top of the stem
                    size = (float) (c.scale * Math.max(5, stemRadius(t, h) * 1.25));
                    alpha = 0.6f;
                    albedo = stemAlbedo;
                    glow = heat * 0.35 * h * h;
                }
                case BASE -> {
                    // Dust lit from above, darker down in the crater; it fades in as it builds and out as it ages.
                    double low = Math.max(0, Math.min(1, (this.ground - y) / Math.max(1, this.ground - this.floor)));
                    nx = (float) dx;
                    ny = 0.5f;
                    nz = (float) dz;
                    shadow = (float) (0.8 - 0.25 * low);
                    size = (float) (c.scale * baseMinor() * 0.95);
                    alpha = 0.55f * (float) Math.min(1, (c.life - age) / 300);
                    albedo = Puffs.lerpRgb(stemAlbedo, DUST_ALBEDO, 0.35f);
                }
                case COLLAR -> {
                    nx = (float) dx;
                    ny = -0.2f;
                    nz = (float) dz;
                    shadow = 0.95f;
                    size = (float) (c.scale * small * 0.7);
                    alpha = 0.32f * (float) Math.min(1, (c.life - age) / 150);
                    albedo = COLLAR_ALBEDO;
                }
                default -> {
                    nx = (float) dx;
                    ny = (float) Math.sin(c.elev) * 2;
                    nz = (float) dz;
                    shadow = 1;
                    size = this.fireball * 0.6f * c.scale;
                    alpha = (float) (0.33 * Math.min(1, age / 15) * Math.max(0, 1 - age / c.life));
                    albedo = COLLAR_ALBEDO;
                }
            }
            alpha *= (float) (fade * Math.min(1, age / 8));
            if (alpha < 0.01f) {
                continue;
            }
            light.shade(nx, ny, nz, shadow, shade);
            int color = Puffs.multiply(albedo, shade);
            if (glow > 0.01) {
                int hot = heatColor((float) Math.max(0, 1 - glow));
                float g = (float) Math.min(1.4, glow * 1.3);
                color = add(color, hot, g);
            }
            float half = size / 2, cs = (float) Math.cos(c.rot) * half, sn = (float) Math.sin(c.rot) * half;
            quads.add(new float[] {(float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z),
                    right.x * cs + up.x * sn, right.y * cs + up.y * sn, right.z * cs + up.z * sn,
                    up.x * cs - right.x * sn, up.y * cs - right.y * sn, up.z * cs - right.z * sn,
                    Float.intBitsToFloat((int) (Math.min(1, alpha) * 255) << 24 | color), Puffs.CLOUDLET + c.variant});
        }
        if (quads.isEmpty()) {
            return;
        }
        FxRenderTypes.submit(collector, poseStack, FxRenderTypes.cloud(Puffs.SHEET), (pose, buffer) -> Puffs.quads(buffer, pose, quads));
    }

    /** The colour of fire at {@code u} = 0 (white-hot) to 1 (dull red). */
    static int heatColor(float u) {
        int[] ramp = {0xFFF0C0, 0xFFC050, 0xFF7A20, 0xC03A10, 0x5A1808};
        float x = Math.max(0, Math.min(0.999f, u)) * (ramp.length - 1);
        int i = (int) x;
        return Puffs.lerpRgb(ramp[i], ramp[i + 1], x - i);
    }

    /** {@code base} plus {@code glow} times {@code amount}, clamped. */
    private static int add(int base, int glow, float amount) {
        int r = (int) Math.min(255, ((base >> 16) & 255) + ((glow >> 16) & 255) * amount);
        int g = (int) Math.min(255, ((base >> 8) & 255) + ((glow >> 8) & 255) * amount);
        int b = (int) Math.min(255, (base & 255) + (glow & 255) * amount);
        return r << 16 | g << 8 | b;
    }
}
