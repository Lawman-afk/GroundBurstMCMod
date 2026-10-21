package dev.groundzero.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundzero.GroundZero;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Soft camera-facing billboards (DESIGN.md §12): the fireball's boiling body, smoke, the dust kicked up along the shock
 * front, dirt and fire jets, sparks. Each puff moves on a closed-form path (a launch slowed by drag, plus rise, wind and
 * gravity), so it is drawn smoothly between ticks without keeping its last position.
 *
 * <p>Smoke and dust are shaded like clouds ({@link FxLight}): a normal pointing out from the axis of the plume they
 * belong to and up, lit by the sun and the sky, dimmed by the world's light where they are (dark at night, in caves).
 * They are drawn with the cloud pipeline (depth-writing, seen from afar). Fire is full-bright and blended over what is
 * behind it; glow and sparks add light. A puff with a {@code stretch} is
 * drawn as a streak along its motion (dirt and fire jets). The textures are one 4x5 sheet,
 * {@code textures/fx/puffs.png}: smoke 0-7, glow 8-11, spark 12, dust 13-15, mushroom cloudlets 16-19 (one sheet, so
 * smoke and the mushroom cloud sort together in one batch: no hard edges where they meet).
 */
final class Puffs {
    static final Identifier SHEET = GroundZero.id("textures/fx/puffs.png");
    /** Rows of cells in the sheet (four to a row). */
    static final int ROWS = 5;
    /** The first of the mushroom cloud's four cloudlet cells. */
    static final int CLOUDLET = 16;
    /** Most puffs alive at once (at high quality; scaled with it). */
    static final int MAX = 6000;

    enum Kind {
        SMOKE(0, 8, true), FIRE(8, 4, false), GLOW(8, 4, false), SPARK(12, 1, false), DUST(13, 3, true);

        final int first, count;
        final boolean lit;

        Kind(int first, int count, boolean lit) {
            this.first = first;
            this.count = count;
            this.lit = lit;
        }
    }

    static final class Puff {
        Kind kind = Kind.SMOKE;
        double x, y, z;
        float vx, vy, vz;
        /** Drag (per tick): the launch velocity decays as {@code e^(-drag t)}. */
        float drag = 0.1f;
        float rise, gravity, windX, windZ;
        /** Lowest it may sink to (sparks settle on the ground). */
        double floor = Double.NEGATIVE_INFINITY;
        double birth;
        int life = 40;
        float size0 = 1, size1 = 2;
        int color0 = 0xFFFFFF, color1 = 0xFFFFFF;
        float alpha = 1;
        int variant;
        float rot, spin;
        /** Streak length per block a tick of speed (0: a round billboard). */
        float stretch;
        /** The axis of the plume it belongs to (its shading normal points out from it). */
        double axisX = Double.NaN, axisZ;

        double age(double now) {
            return now - this.birth;
        }
    }

    private final List<Puff> puffs = new ArrayList<>();

    int size() {
        return this.puffs.size();
    }

    void add(Puff p, float quality) {
        if (this.puffs.size() < MAX * quality) {
            this.puffs.add(p);
        }
    }

    void clear() {
        this.puffs.clear();
    }

    void tick(double now) {
        this.puffs.removeIf(p -> p.age(now) >= p.life);
    }

    /** Where a puff is at age {@code a} ticks. */
    private static void position(Puff p, double a, double[] out) {
        double f = p.drag > 1e-4 ? (1 - Math.exp(-p.drag * a)) / p.drag : a;
        out[0] = p.x + p.vx * f + p.windX * a;
        out[1] = Math.max(p.floor, p.y + p.vy * f + p.rise * a - 0.5 * p.gravity * a * a);
        out[2] = p.z + p.vz * f + p.windZ * a;
    }

    void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera, Quaternionf orientation, double now) {
        if (this.puffs.isEmpty()) {
            return;
        }
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        FxLight light = FxLight.current;
        Vector3f shade = new Vector3f();
        // Snapshot this frame's quads (centre and two half-axes each); the collector draws them later in the frame.
        List<float[]> smoke = new ArrayList<>(), blended = new ArrayList<>(), bright = new ArrayList<>();
        double[] at = new double[3];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Puff p : this.puffs) {
            double a = p.age(now);
            if (a < 0) {
                continue;
            }
            double u = a / p.life;
            position(p, a, at);
            float size = (float) (p.size1 + (p.size0 - p.size1) * Math.exp(-3 * u));
            float fadeIn = (float) Math.min(1, a / Math.max(1, Math.min(4, p.life * 0.1)));
            float fadeOut = (float) (p.kind == Kind.GLOW || p.kind == Kind.FIRE ? 1 - Math.pow(u, 3) : Math.pow(1 - u, 1.4));
            float alpha = p.alpha * fadeIn * fadeOut;
            if (alpha < 0.01f) {
                continue;
            }
            int color = lerpRgb(p.color0, p.color1, (float) Math.pow(u, p.kind == Kind.GLOW || p.kind == Kind.FIRE ? 0.8 : 0.6));
            if (p.kind.lit) {
                pos.set(Math.floor(at[0]), Math.floor(at[1]), Math.floor(at[2]));
                int coords = LightCoordsUtil.getLightCoords(level, pos);
                float world = Math.max(0.07f, Math.max(LightCoordsUtil.sky(coords) / 15f, LightCoordsUtil.block(coords) / 15f * 0.7f));
                // Out from the plume's axis and up: the plume's sunny side and top are lit, its far side in shade.
                float nx = 0, nz = 0;
                if (!Double.isNaN(p.axisX)) {
                    nx = (float) (at[0] - p.axisX);
                    nz = (float) (at[2] - p.axisZ);
                    float n = (float) Math.sqrt(nx * nx + nz * nz) + 1e-3f;
                    nx = nx / n * 0.6f;
                    nz = nz / n * 0.6f;
                }
                light.shade(nx, 1, nz, world, shade);
                color = multiply(color, shade);
            }
            float half = size / 2, angle = p.rot + p.spin * (float) a;
            float c = (float) Math.cos(angle) * half, s = (float) Math.sin(angle) * half;
            float ax = right.x * c + up.x * s, ay = right.y * c + up.y * s, az = right.z * c + up.z * s;
            float bx = up.x * c - right.x * s, by = up.y * c - right.y * s, bz = up.z * c - right.z * s;
            float x = (float) (at[0] - camera.x), y = (float) (at[1] - camera.y), z = (float) (at[2] - camera.z);
            if (p.stretch > 0) {
                // A streak: long along its motion, as wide as the puff, turned to face the camera.
                double e = Math.exp(-p.drag * a);
                float vx = (float) (p.vx * e + p.windX), vy = (float) (p.vy * e + p.rise - p.gravity * a), vz = (float) (p.vz * e + p.windZ);
                float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                Vector3f dir = new Vector3f(vx, vy, vz).div(speed + 1e-6f);
                Vector3f side = new Vector3f(dir).cross(x, y, z);
                if (speed > 0.02f && side.lengthSquared() > 1e-6f) {
                    side.normalize(half);
                    dir.mul(half + speed * p.stretch);
                    ax = side.x;
                    ay = side.y;
                    az = side.z;
                    bx = dir.x;
                    by = dir.y;
                    bz = dir.z;
                }
            }
            int cell = p.kind.first + Math.floorMod(p.variant, p.kind.count);
            float[] q = {x, y, z, ax, ay, az, bx, by, bz, Float.intBitsToFloat((int) (Math.min(1, alpha) * 255) << 24 | color), cell};
            (p.kind.lit ? smoke : p.kind == Kind.GLOW || p.kind == Kind.SPARK ? bright : blended).add(q);
        }
        if (!smoke.isEmpty()) {
            // Smoke and dust write depth (with their faint fringe cut out), so vanilla's clouds stay behind them.
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.cloud(SHEET), (pose, buffer) -> quads(buffer, pose, smoke));
        }
        if (!blended.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.fire(SHEET), (pose, buffer) -> quads(buffer, pose, blended));
        }
        if (!bright.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(SHEET), (pose, buffer) -> quads(buffer, pose, bright));
        }
    }

    static void quads(VertexConsumer b, PoseStack.Pose pose, List<float[]> quads) {
        for (float[] q : quads) {
            float x = q[0], y = q[1], z = q[2], ax = q[3], ay = q[4], az = q[5], bx = q[6], by = q[7], bz = q[8];
            int color = Float.floatToRawIntBits(q[9]);
            int cell = (int) q[10];
            float u0 = (cell % 4) / 4f, v0 = (cell / 4) / (float) ROWS, u1 = u0 + 0.25f, v1 = v0 + 1f / ROWS;
            vertex(b, pose, x - ax - bx, y - ay - by, z - az - bz, u0, v1, color);
            vertex(b, pose, x + ax - bx, y + ay - by, z + az - bz, u1, v1, color);
            vertex(b, pose, x + ax + bx, y + ay + by, z + az + bz, u1, v0, color);
            vertex(b, pose, x - ax + bx, y - ay + by, z - az + bz, u0, v0, color);
        }
    }

    static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /** An RGB colour times a light multiplier (clamped). */
    static int multiply(int rgb, Vector3f light) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * light.x), g = (int) Math.min(255, ((rgb >> 8) & 255) * light.y),
                b = (int) Math.min(255, (rgb & 255) * light.z);
        return r << 16 | g << 8 | b;
    }

    static int lerpRgb(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
