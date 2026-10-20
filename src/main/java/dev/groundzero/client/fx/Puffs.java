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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Soft camera-facing billboards (DESIGN.md §12): the fireball's boiling body, smoke, the dust kicked up along the shock
 * front, and sparks. Each puff moves on a closed-form path (a launch slowed by drag, plus rise, wind and gravity), so it
 * is drawn smoothly between ticks without keeping its last position. Smoke and dust are lit by the world (dark at night,
 * in shadow under overhangs); fire is full-bright but blended over what is behind it (it stays orange against a bright
 * sky); glow and sparks are full-bright and add light. The textures are one 4x4 sheet,
 * {@code textures/fx/puffs.png}: smoke 0-7, glow 8-11, spark 12, dust 13-15.
 */
final class Puffs {
    static final Identifier SHEET = GroundZero.id("textures/fx/puffs.png");
    /** Most puffs alive at once (at high quality; scaled with it). */
    static final int MAX = 6000;

    enum Kind {
        SMOKE(0, 8), FIRE(8, 4), GLOW(8, 4), SPARK(12, 1), DUST(13, 3);

        final int first, count;

        Kind(int first, int count) {
            this.first = first;
            this.count = count;
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
        // Snapshot this frame's quads; the collector draws them later in the frame.
        List<float[]> lit = new ArrayList<>(), fire = new ArrayList<>(), bright = new ArrayList<>();
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
            int light = LightCoordsUtil.FULL_BRIGHT;
            if (p.kind == Kind.SMOKE || p.kind == Kind.DUST) {
                pos.set(Math.floor(at[0]), Math.floor(at[1]), Math.floor(at[2]));
                light = LightCoordsUtil.getLightCoords(level, pos);
            }
            float angle = p.rot + p.spin * (float) a;
            int cell = p.kind.first + Math.floorMod(p.variant, p.kind.count);
            float[] q = {(float) (at[0] - camera.x), (float) (at[1] - camera.y), (float) (at[2] - camera.z), size / 2, angle,
                    Float.intBitsToFloat((int) (Math.min(1, alpha) * 255) << 24 | color), Float.intBitsToFloat(light), cell};
            (p.kind == Kind.SMOKE || p.kind == Kind.DUST ? lit : p.kind == Kind.FIRE ? fire : bright).add(q);
        }
        float rx = right.x, ry = right.y, rz = right.z, ux = up.x, uy = up.y, uz = up.z;
        if (!lit.isEmpty()) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SHEET),
                    (pose, buffer) -> quads(buffer, pose, lit, rx, ry, rz, ux, uy, uz));
        }
        if (!fire.isEmpty()) {
            collector.submitCustomGeometry(poseStack, FxRenderTypes.fire(SHEET),
                    (pose, buffer) -> quads(buffer, pose, fire, rx, ry, rz, ux, uy, uz));
        }
        if (!bright.isEmpty()) {
            RenderType glow = FxRenderTypes.glow(SHEET);
            collector.submitCustomGeometry(poseStack, glow, (pose, buffer) -> quads(buffer, pose, bright, rx, ry, rz, ux, uy, uz));
        }
    }

    private static void quads(VertexConsumer b, PoseStack.Pose pose, List<float[]> quads, float rx, float ry, float rz, float ux, float uy,
            float uz) {
        for (float[] q : quads) {
            float half = q[3], c = (float) Math.cos(q[4]) * half, s = (float) Math.sin(q[4]) * half;
            // The quad's in-plane axes, turned by the puff's spin.
            float ax = rx * c + ux * s, ay = ry * c + uy * s, az = rz * c + uz * s;
            float bx = ux * c - rx * s, by = uy * c - ry * s, bz = uz * c - rz * s;
            int color = Float.floatToRawIntBits(q[5]), light = Float.floatToRawIntBits(q[6]);
            int cell = (int) q[7];
            float u0 = (cell % 4) / 4f, v0 = (cell / 4) / 4f, u1 = u0 + 0.25f, v1 = v0 + 0.25f;
            float x = q[0], y = q[1], z = q[2];
            vertex(b, pose, x - ax - bx, y - ay - by, z - az - bz, u0, v1, color, light);
            vertex(b, pose, x + ax - bx, y + ay - by, z + az - bz, u1, v1, color, light);
            vertex(b, pose, x + ax + bx, y + ay + by, z + az + bz, u1, v0, color, light);
            vertex(b, pose, x - ax + bx, y - ay + by, z - az + bz, u0, v0, color, light);
        }
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    static int lerpRgb(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
