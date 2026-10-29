package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Chunks of the blocks a blast tore out (DESIGN.md §12): small spinning cubes wearing a corner of the real block's
 * texture, thrown on ballistic arcs. They bounce off the ground and walls, come to rest, lie there a few seconds and
 * shrink away. Purely visual: the server never knows about them. A chunk a singularity tore loose (DESIGN.md §16.2)
 * orbits instead: drawn toward the hole on a decaying spiral that settles into its disc's plane, through anything in
 * its way, until it crosses the event horizon and is gone.
 */
final class Debris {
    static final int MAX = 1200;
    private static final double GRAVITY = 0.045;
    private static final double DRAG = 0.985;
    /** Ticks a chunk lies still before it shrinks away, and how long the shrinking takes. */
    private static final int REST = 70, SHRINK = 20;

    static final class Chunk {
        final BlockState state;
        final TextureAtlasSprite sprite;
        final float u0, v0, u1, v1;
        /** Biome or block tint (grass, leaves), white for untinted blocks. */
        final int tint;
        double x, y, z, px, py, pz, vx, vy, vz;
        final float size;
        final float ax, ay, az;
        float angle, prevAngle, spin;
        int age, still;
        boolean resting;
        /** Orbiting a singularity: its middle, its disc's normal, how hard it pulls, and its horizon now (null: thrown). */
        double ox, oy, oz, nx, ny, nz, pullK;
        java.util.function.DoubleSupplier horizon;
        boolean swallowed;

        Chunk(BlockState state, int tint, RandomSource r, double x, double y, double z, double vx, double vy, double vz, float size) {
            this.state = state;
            this.tint = tint;
            this.sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).particleMaterial().sprite();
            // A random corner of the texture, as big as the chunk is (a chunk is a piece of a block, not a tiny block).
            float frac = Math.max(0.25f, Math.min(1, size));
            float fu = r.nextFloat() * (1 - frac), fv = r.nextFloat() * (1 - frac);
            float su = this.sprite.getU1() - this.sprite.getU0(), sv = this.sprite.getV1() - this.sprite.getV0();
            this.u0 = this.sprite.getU0() + fu * su;
            this.v0 = this.sprite.getV0() + fv * sv;
            this.u1 = this.u0 + frac * su;
            this.v1 = this.v0 + frac * sv;
            this.x = this.px = x;
            this.y = this.py = y;
            this.z = this.pz = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            double gx = r.nextGaussian(), gy = r.nextGaussian(), gz = r.nextGaussian(), n = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-6;
            this.ax = (float) (gx / n);
            this.ay = (float) (gy / n);
            this.az = (float) (gz / n);
            this.angle = this.prevAngle = r.nextFloat() * 6.283f;
            this.spin = (r.nextFloat() - 0.5f) * 0.9f;
        }

        boolean dead() {
            return this.swallowed || this.still > REST + SHRINK || this.age > 600;
        }

        /** Sets it orbiting a singularity at (ox, oy, oz) whose disc has normal (nx, ny, nz). */
        Chunk orbit(double ox, double oy, double oz, double nx, double ny, double nz, double pullK, java.util.function.DoubleSupplier horizon) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.pullK = pullK;
            this.horizon = horizon;
            return this;
        }
    }

    private final List<Chunk> chunks = new ArrayList<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    /** The tint a chunk of this block shows (grass and leaves green, most blocks white). */
    static int tint(BlockState state, ClientLevel level, BlockPos at) {
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        return source == null ? 0xFFFFFF : source.colorAsTerrainParticle(state, level, at) & 0xFFFFFF;
    }

    void add(Chunk c, float quality) {
        if (this.chunks.size() < MAX * quality) {
            this.chunks.add(c);
        }
    }

    int size() {
        return this.chunks.size();
    }

    void clear() {
        this.chunks.clear();
    }

    void tick(ClientLevel level) {
        for (Chunk c : this.chunks) {
            c.px = c.x;
            c.py = c.y;
            c.pz = c.z;
            c.prevAngle = c.angle;
            c.age++;
            if (c.horizon != null) {
                spiral(c);
                continue;
            }
            if (c.resting) {
                c.still++;
                continue;
            }
            c.vy -= GRAVITY;
            c.vx *= DRAG;
            c.vy *= DRAG;
            c.vz *= DRAG;
            double half = c.size / 2;
            // Vertical move first: land on whatever is underneath.
            double ny = c.y + c.vy;
            double floor = top(level, c.x, ny - half, c.z);
            if (c.vy < 0 && ny - half < floor) {
                ny = floor + half;
                c.vy = -c.vy * 0.3;
                c.vx *= 0.6;
                c.vz *= 0.6;
                c.spin *= 0.5f;
                if (Math.abs(c.vy) < 0.06) {
                    c.vy = 0;
                    if (c.vx * c.vx + c.vz * c.vz < 0.004) {
                        c.resting = true;
                    }
                }
            }
            c.y = ny;
            // Then sideways: a wall turns it back.
            double nx = c.x + c.vx, nz = c.z + c.vz;
            if (solid(level, nx, c.y, c.z)) {
                c.vx = -c.vx * 0.3;
            } else {
                c.x = nx;
            }
            if (solid(level, c.x, c.y, nz)) {
                c.vz = -c.vz * 0.3;
            } else {
                c.z = nz;
            }
            c.angle += c.spin;
        }
        this.chunks.removeIf(Chunk::dead);
    }

    /**
     * A chunk orbiting a singularity: pulled in (inverse square), drawn toward its disc's plane, its orbit decaying, so
     * it spirals in ever faster; gone once it crosses the horizon (or the hole has gone).
     */
    private static void spiral(Chunk c) {
        double dx = c.x - c.ox, dy = c.y - c.oy, dz = c.z - c.oz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double h = c.horizon.getAsDouble();
        if (h <= 0 || d <= h) {
            c.swallowed = true;
            return;
        }
        double a = Math.min(0.9, c.pullK / (d * d));
        double off = dx * c.nx + dy * c.ny + dz * c.nz;               // out of the disc's plane
        c.vx += -dx / d * a - off * c.nx * 0.03;
        c.vy += -dy / d * a - off * c.ny * 0.03;
        c.vz += -dz / d * a - off * c.nz * 0.03;
        c.vx *= 0.985;
        c.vy *= 0.985;
        c.vz *= 0.985;
        c.x += c.vx;
        c.y += c.vy;
        c.z += c.vz;
        c.angle += c.spin;
    }

    /** The top of the solid surface at or below (x, y, z), or minus infinity if (x, y, z) is in the open. */
    private double top(ClientLevel level, double x, double y, double z) {
        this.pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
        VoxelShape shape = level.getBlockState(this.pos).getCollisionShape(level, this.pos);
        if (shape.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        return this.pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
    }

    private boolean solid(ClientLevel level, double x, double y, double z) {
        this.pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
        return !level.getBlockState(this.pos).getCollisionShape(level, this.pos).isEmpty();
    }

    void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera, float partial) {
        if (this.chunks.isEmpty()) {
            return;
        }
        List<float[]> drawn = new ArrayList<>();
        for (Chunk c : this.chunks) {
            double x = c.px + (c.x - c.px) * partial, y = c.py + (c.y - c.py) * partial, z = c.pz + (c.z - c.pz) * partial;
            double d2 = (x - camera.x) * (x - camera.x) + (y - camera.y) * (y - camera.y) + (z - camera.z) * (z - camera.z);
            if (d2 > 160 * 160) {
                continue;
            }
            float shrink = c.still > REST ? 1 - (c.still - REST + partial) / SHRINK : 1;
            if (shrink <= 0) {
                continue;
            }
            this.pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
            int light = LightCoordsUtil.getLightCoords(level, this.pos);
            float angle = c.prevAngle + (c.angle - c.prevAngle) * partial;
            drawn.add(new float[] {(float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), c.size * shrink, c.ax, c.ay, c.az,
                    angle, c.u0, c.v0, c.u1, c.v1, Float.intBitsToFloat(light), Float.intBitsToFloat(0xFF000000 | c.tint)});
        }
        if (drawn.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            Quaternionf q = new Quaternionf();
            for (float[] d : drawn) {
                PoseStack.Pose local = pose.copy();
                local.translate(d[0], d[1], d[2]);
                local.rotate(q.identity().rotateAxis(d[7], d[4], d[5], d[6]));
                local.scale(d[3], d[3], d[3]);
                cube(buffer, local, d[8], d[9], d[10], d[11], Float.floatToRawIntBits(d[12]), Float.floatToRawIntBits(d[13]));
            }
        });
    }

    /** A unit cube centred on the origin, every face showing the same piece of texture. Vanilla face winding. */
    private static void cube(VertexConsumer b, PoseStack.Pose pose, float u0, float v0, float u1, float v1, int light, int color) {
        float a = -0.5f, z = 0.5f;
        quad(b, pose, light, color, 0, 1, 0, a, z, a, a, z, z, z, z, z, z, z, a, u0, v0, u1, v1);
        quad(b, pose, light, color, 0, -1, 0, a, a, z, a, a, a, z, a, a, z, a, z, u0, v0, u1, v1);
        quad(b, pose, light, color, 0, 0, -1, z, z, a, z, a, a, a, a, a, a, z, a, u0, v0, u1, v1);
        quad(b, pose, light, color, 0, 0, 1, a, z, z, a, a, z, z, a, z, z, z, z, u0, v0, u1, v1);
        quad(b, pose, light, color, -1, 0, 0, a, z, a, a, a, a, a, a, z, a, z, z, u0, v0, u1, v1);
        quad(b, pose, light, color, 1, 0, 0, z, z, z, z, a, z, z, a, a, z, z, a, u0, v0, u1, v1);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int light, int white, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        buffer.addVertex(pose, ax, ay, az).setColor(white).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, bx, by, bz).setColor(white).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, cx, cy, cz).setColor(white).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, dx, dy, dz).setColor(white).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
