package dev.groundzero.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.groundzero.content.nuclear.Contamination;
import dev.groundzero.content.nuclear.ContaminationPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Fallout over the contaminated zones (DESIGN.md §15.1; a stand-in until Phase 12's fallout plumes, after the in-game
 * look at the nukes): a broken layer of dark grey and sickly green cloud 50 to 80 blocks over each zone, drifting with
 * its own wind and thinning as the zone fades, and, inside a zone, a green cast to the sky, the fog and the clouds,
 * stronger toward its middle. The server sends every zone in the level; each zone's clouds are laid out from a seed and
 * moved by the level's clock, so every player sees the same sky.
 */
final class FalloutFx {
    /** The zones in {@link #zonesLevel} (the server sends the new level's on a dimension change). */
    private static List<Contamination.Zone> zones = List.of();
    private static ClientLevel zonesLevel;
    /** Cloud albedos: dark grey, grey, olive, sickly green. */
    private static final int[] COLOURS = {0x55585A, 0x6E716E, 0x5F7A2E, 0x7A9C3A};
    /** Blocks a tick the layer drifts. */
    private static final double WIND = 0.03;
    /** How high the layer's base and how deep it is, over the zone's centre. */
    private static final double BASE = 50, DEPTH = 30;
    /** How far the sky turns toward fallout green at a zone's middle while it is fresh. */
    private static final float TINT = 0.4f;
    /** Puffs are drawn wider than tall (a layer, not a heap). */
    private static final float WIDE = 1.4f, TALL = 0.8f;

    private FalloutFx() {}

    static void register() {
        ContaminationPayloads.zonesHook = z -> {
            zones = z.zones();
            zonesLevel = Minecraft.getInstance().level;
        };
    }

    static void clear() {
        zones = List.of();
        zonesLevel = null;
    }

    private static List<Contamination.Zone> current(ClientLevel level) {
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
        for (Contamination.Zone z : current(level)) {
            most = Math.max(most, z.levelAt(now, pos.distanceTo(z.center())));
        }
        return most <= 0 ? 0 : TINT * (float) Math.sqrt(Math.min(1, most));
    }

    /** Draws every zone's cloud layer, back to front. */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera, Quaternionf orientation, float partial) {
        List<Contamination.Zone> list = current(level);
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
        List<float[]> quads = new ArrayList<>();
        for (Contamination.Zone z : list) {
            double age = (now - z.start()) / (double) Math.max(1, z.duration());
            float life = (float) Math.sqrt(Math.max(0, z.strength() * (1 - age)));
            if (life < 0.02f) {
                continue;
            }
            double r = z.radius();
            // Overlapping smoke puffs (about twice over), so they merge into a lumpy layer instead of reading as blobs. Each
            // is fairly opaque: the cloud shader cuts away a puff's faint fringe (so it can write depth), and the fainter
            // the puff, the more of its edge that cut takes, leaving hard outlines.
            int n = (int) (Math.max(24, Math.min(300, r * r / 170)) * quality);
            RandomSource random = RandomSource.create(Double.doubleToLongBits(z.center().x) * 31 + Double.doubleToLongBits(z.center().z) ^ z.start());
            float windAngle = random.nextFloat() * 6.283f;
            double wx = Math.cos(windAngle) * WIND, wz = Math.sin(windAngle) * WIND;
            float size0 = (float) (22 + Math.min(14, r / 12));
            for (int i = 0; i < n; i++) {
                // Every puff draws the same numbers each frame (its place is its seed's), whether it is drawn or not.
                double ox = (random.nextDouble() * 2 - 1) * r, oz = (random.nextDouble() * 2 - 1) * r;
                double y = z.center().y + BASE + random.nextDouble() * DEPTH;
                float scale = 0.7f + 0.6f * random.nextFloat();
                int albedo = COLOURS[random.nextInt(COLOURS.length)];
                int variant = random.nextInt(8);                   // the smoke cells: lumpy, shaded
                float bright = 0.88f + 0.24f * random.nextFloat();
                float rot = (random.nextFloat() - 0.5f) * 0.3f;         // nearly level: the stretch stays a layer
                float phase = random.nextFloat() * 6.283f;
                // Drifting with the wind, wrapping round inside the square round the zone; it fades out toward the zone's
                // edge, so a puff leaving one side and coming back in on the other is never seen to jump.
                double px = wrap(ox + wx * time, r), pz = wrap(oz + wz * time, r);
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
                float half = size / 2, cs = (float) Math.cos(rot) * half, sn = (float) Math.sin(rot) * half;
                quads.add(new float[] {(float) (x - camera.x), (float) (y - camera.y), (float) (zz - camera.z),
                        (right.x * cs + up.x * sn) * WIDE, (right.y * cs + up.y * sn) * WIDE, (right.z * cs + up.z * sn) * WIDE,
                        (up.x * cs - right.x * sn) * TALL, (up.y * cs - right.y * sn) * TALL, (up.z * cs - right.z * sn) * TALL,
                        Float.intBitsToFloat((int) (Math.min(1, alpha) * 255) << 24 | color), Puffs.Kind.SMOKE.first + variant});
            }
        }
        if (quads.isEmpty()) {
            return;
        }
        quads.sort((a, b) -> Float.compare(b[0] * b[0] + b[1] * b[1] + b[2] * b[2], a[0] * a[0] + a[1] * a[1] + a[2] * a[2]));
        FxRenderTypes.submit(collector, poseStack, FxRenderTypes.cloud(Puffs.SHEET), (pose, buffer) -> Puffs.quads(buffer, pose, quads));
    }

    /** {@code v} wrapped into [-r, r). */
    private static double wrap(double v, double r) {
        double span = 2 * r;
        return v + r - span * Math.floor((v + r) / span) - r;
    }
}
