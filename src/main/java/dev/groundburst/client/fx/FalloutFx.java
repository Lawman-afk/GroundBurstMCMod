package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.groundburst.content.radiation.RadiationSource;
import dev.groundburst.content.radiation.RadiationSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Fallout over the radiation sources (DESIGN.md §15.1, §21.8, after the in-game look at the nukes): a broken layer of
 * dark grey and sickly green cloud 50 to 80 blocks over each source (a plume lays a string of them downwind, slice 12b),
 * drifting with the level's wind and thinning as the source decays, and, inside one, a green cast to the sky, the fog and the clouds, stronger
 * toward its middle. The server sends every source in the level; each one's clouds are laid out from a seed and moved
 * by the level's clock, so every player sees the same sky.
 */
final class FalloutFx {
    /** The sources in {@link #zonesLevel} (the server sends the new level's on a dimension change). */
    private static List<RadiationSource> zones = List.of();
    private static ClientLevel zonesLevel;
    /** Cloud albedos: dark grey, grey, olive, sickly green. */
    private static final int[] COLOURS = {0x55585A, 0x6E716E, 0x5F7A2E, 0x7A9C3A};
    /** The share of the wind's carry the layer drifts by (the puffs wrap round inside their zone, so slower reads calmer). */
    private static final double DRIFT = 0.4;
    /** How high the layer's base and how deep it is, over the zone's centre. */
    private static final double BASE = 50, DEPTH = 30;
    /** How far the sky turns toward fallout green at a zone's middle while it is fresh. */
    private static final float TINT = 0.4f;
    /** Puffs are drawn wider than tall (a layer, not a heap). */
    private static final float WIDE = 1.4f, TALL = 0.8f;

    private FalloutFx() {}

    /**
     * A zone's puffs as its seed lays them out (where each sits in the zone, as shares of its radius; its height in the
     * layer, size, colour, cell, brightness, turn and phase), drawn from the seed once and kept rather than drawn again
     * every frame (DESIGN.md §11.10): the same puffs as ever, the same numbers in the same order.
     */
    private record Layout(int count, double[] ox, double[] oz, double[] y, float[] scale, int[] albedo, int[] variant, float[] bright,
            float[] cos, float[] sin, float[] phase) {
        static Layout of(long seed, int n) {
            RandomSource random = RandomSource.create(seed);
            random.nextFloat();                                    // (it drew its own wind before slice 12b: the same puffs as then)
            Layout l = new Layout(n, new double[n], new double[n], new double[n], new float[n], new int[n], new int[n], new float[n],
                    new float[n], new float[n], new float[n]);
            for (int i = 0; i < n; i++) {
                l.ox[i] = random.nextDouble() * 2 - 1;
                l.oz[i] = random.nextDouble() * 2 - 1;
                l.y[i] = random.nextDouble() * DEPTH;
                l.scale[i] = 0.7f + 0.6f * random.nextFloat();
                l.albedo[i] = COLOURS[random.nextInt(COLOURS.length)];
                l.variant[i] = random.nextInt(8);                  // the smoke cells: lumpy, shaded
                l.bright[i] = 0.88f + 0.24f * random.nextFloat();
                float rot = (random.nextFloat() - 0.5f) * 0.3f;        // nearly level: the stretch stays a layer
                l.cos[i] = (float) Math.cos(rot);
                l.sin[i] = (float) Math.sin(rot);
                l.phase[i] = random.nextFloat() * 6.283f;
            }
            return l;
        }
    }

    /** Each zone's layout by its seed (made again when the server sends the sources, or a quality setting asks for more). */
    private static final java.util.Map<Long, Layout> LAYOUTS = new java.util.HashMap<>();

    static void register() {
        RadiationSources.Sync.hook = z -> {
            zones = z.sources();
            zonesLevel = Minecraft.getInstance().level;
            LAYOUTS.clear();
        };
    }

    static void clear() {
        zones = List.of();
        zonesLevel = null;
        LAYOUTS.clear();
    }

    private static List<RadiationSource> current(ClientLevel level) {
        if (zonesLevel == null && level != null && !zones.isEmpty()) {
            zonesLevel = level;                            // they came before the level did (logging in)
        }
        return level != null && level == zonesLevel ? zones : List.of();
    }

    static boolean active(ClientLevel level) {
        return !current(level).isEmpty();
    }

    /** How far the sky, fog and clouds turn green at {@code pos} (0 outside every zone; most at a fresh zone's middle). */
    static float tint(ClientLevel level, Vec3 pos) {
        float most = 0;
        long now = level.getGameTime();
        for (RadiationSource z : current(level)) {
            most = Math.max(most, z.levelAt(now, pos.distanceTo(z.center())));
        }
        return most <= 0 ? 0 : TINT * (float) Math.sqrt(Math.min(1, most));
    }

    /** Draws every zone's cloud layer, back to front. */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera, Quaternionf orientation, float partial) {
        List<RadiationSource> list = current(level);
        if (list.isEmpty()) {
            return;
        }
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        FxLight light = FxLight.current;
        Vector3f shade = new Vector3f();
        long now = level.getGameTime();
        double time = now + partial;
        float quality = Math.max(0.5f, Math.min(1.2f, Spectacle.quality()));
        double driftX = dev.groundburst.client.fallout.FalloutClient.driftX(partial), driftZ = dev.groundburst.client.fallout.FalloutClient.driftZ(partial);
        if (LAYOUTS.size() > 256) {
            LAYOUTS.clear();                                       // (a great many zones come and gone: start again)
        }
        Puffs.QuadBuffer quads = new Puffs.QuadBuffer(list.size() * 96);
        for (RadiationSource z : list) {
            float life = (float) Math.sqrt(z.strength(now));
            if (life < 0.02f) {
                continue;
            }
            double r = z.radius();
            // Overlapping smoke puffs (about twice over), so they merge into a lumpy layer instead of reading as blobs. Each
            // is fairly opaque: the cloud shader cuts away a puff's faint fringe (so it can write depth), and the fainter
            // the puff, the more of its edge that cut takes, leaving hard outlines.
            int n = (int) (Math.max(24, Math.min(300, r * r / 170)) * quality);
            long seed = Double.doubleToLongBits(z.center().x) * 31 + Double.doubleToLongBits(z.center().z) ^ z.start();
            Layout lay = LAYOUTS.get(seed);
            if (lay == null || lay.count() < n) {
                lay = Layout.of(seed, n);                          // (the first n of a longer layout are the same puffs)
                LAYOUTS.put(seed, lay);
            }
            float size0 = (float) (22 + Math.min(14, r / 12));
            for (int i = 0; i < n; i++) {
                // Every puff keeps the place its seed gave it, whether it is drawn or not.
                double ox = lay.ox()[i] * r, oz = lay.oz()[i] * r;
                double y = z.center().y + BASE + lay.y()[i];
                float scale = lay.scale()[i];
                int albedo = lay.albedo()[i];
                int variant = lay.variant()[i];
                float bright = lay.bright()[i];
                float phase = lay.phase()[i];
                // Drifting with the wind (slice 12b: the level's own, as this client has felt it), wrapping round inside the
                // square round the zone; it fades out toward the zone's edge, so a puff leaving one side and coming back in
                // on the other is never seen to jump.
                double px = wrap(ox + driftX * DRIFT, r), pz = wrap(oz + driftZ * DRIFT, r);
                double d = Math.sqrt(px * px + pz * pz) / r;
                if (d >= 1) {
                    continue;
                }
                float alpha = 0.6f * life * (1 - FxLight.smoothstep(0.7f, 1f, (float) d));
                if (alpha < 0.01f) {
                    continue;
                }
                double x = z.center().x + px, zz = z.center().z + pz;
                float size = (float) (size0 * scale * (1 + 0.06 * Math.sin(time * 0.004 + phase)));
                // Lit as the side the camera sees: from below the cloud's dark underside, from above its lit top.
                double hx = camera.x - x, hz = camera.z - zz, h = Math.sqrt(hx * hx + hz * hz) + 1e-4;
                float ny = (float) Math.max(-1, Math.min(1, (camera.y - y) / (size * 2)));
                float side = 1 - Math.abs(ny);
                light.shade((float) (hx / h) * side, ny, (float) (hz / h) * side, 0.8f + 0.15f * Math.max(0, ny), shade);
                int color = Puffs.multiply(albedo, shade.mul(bright));
                float half = size / 2, cs = lay.cos()[i] * half, sn = lay.sin()[i] * half;
                quads.add((float) (x - camera.x), (float) (y - camera.y), (float) (zz - camera.z),
                        (right.x * cs + up.x * sn) * WIDE, (right.y * cs + up.y * sn) * WIDE, (right.z * cs + up.z * sn) * WIDE,
                        (up.x * cs - right.x * sn) * TALL, (up.y * cs - right.y * sn) * TALL, (up.z * cs - right.z * sn) * TALL,
                        (int) (Math.min(1, alpha) * 255) << 24 | color, Puffs.Kind.SMOKE.first + variant);
            }
        }
        if (quads.isEmpty()) {
            return;
        }
        // (No sorting here: the cloud render type has the game sort every quad drawn with it, far to near, these and others'.)
        FxRenderTypes.submit(collector, poseStack, FxRenderTypes.cloud(Puffs.SHEET), (pose, buffer) -> Puffs.quads(buffer, pose, quads));
    }

    /** {@code v} wrapped into [-r, r). */
    private static double wrap(double v, double r) {
        double span = 2 * r;
        return v + r - span * Math.floor((v + r) / span) - r;
    }
}
