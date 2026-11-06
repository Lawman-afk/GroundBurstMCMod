package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

/**
 * The effects' blended quads sorted far to near by the mod, once a frame (DESIGN.md §11.10). Their render types had the
 * game sort them as each frame's geometry went up: a comparator's merge sort over every quad, the biggest part of drawing
 * a mushroom cloud. Everything the effects submit with a sorting type while they submit ({@link #open}) is kept back
 * instead, and at the end each type's lot is drawn as one: every renderer run into a buffer of its own, the quads put in
 * the game's own order (by the square of their centre's distance from the camera, the centre the middle of a quad's first
 * and third corners, the farthest first, ties in the order they came) with a sort of plain numbers, and written out in
 * that order under a twin of the type that leaves them as they come.
 */
final class FxSort {
    /** A renderer kept back, and the pose it was submitted with. */
    private record Queued(PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer renderer) {}

    private static final Map<RenderType, List<Queued>> QUEUED = new LinkedHashMap<>();
    private static boolean open;
    private static final Capture CAPTURE = new Capture();
    private static long[] keys = new long[1024];

    private FxSort() {}

    /** The effects begin submitting: sorted types are kept back from now. */
    static void begin() {
        open = true;
    }

    /**
     * Keeps a renderer back to be drawn sorted with the rest under {@code presorted} (its type's twin); false if the
     * effects are not submitting now (the caller submits it as it is).
     */
    static boolean add(PoseStack poseStack, RenderType presorted, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (!open) {
            return false;
        }
        QUEUED.computeIfAbsent(presorted, k -> new ArrayList<>()).add(new Queued(poseStack.last().copy(), renderer));
        return true;
    }

    /** The effects have submitted: each type's quads go to the game as one lot, sorted when it draws them. */
    static void end(SubmitNodeCollector collector, PoseStack poseStack) {
        open = false;
        for (Map.Entry<RenderType, List<Queued>> e : QUEUED.entrySet()) {
            Queued[] lot = e.getValue().toArray(new Queued[0]);
            FxRenderTypes.submitNow(collector, poseStack, e.getKey(), (pose, buffer) -> draw(buffer, lot));
        }
        QUEUED.clear();
    }

    /** Runs the lot's renderers, then writes their quads far to near (their vertices already in the level's space). */
    private static void draw(VertexConsumer out, Queued[] lot) {
        Capture c = CAPTURE;
        c.reset();
        for (int q = 0; q < lot.length; q++) {
            lot[q].renderer().render(lot[q].pose(), c);
        }
        int quads = c.count / 4;
        if (quads == 0) {
            return;
        }
        if (keys.length < quads) {
            keys = new long[Math.max(quads, keys.length * 2)];
        }
        float[] d = c.data;
        for (int i = 0; i < quads; i++) {
            int a = i * 4 * Capture.STRIDE, b = a + 2 * Capture.STRIDE;
            float x = (d[a] + d[b]) / 2.0F, y = (d[a + 1] + d[b + 1]) / 2.0F, z = (d[a + 2] + d[b + 2]) / 2.0F;
            float dist = x * x + y * y + z * z;            // (never below 0: its bits sort as the number does)
            keys[i] = (long) Float.floatToRawIntBits(dist) << 32 | (Integer.MAX_VALUE - i);
        }
        Arrays.sort(keys, 0, quads);
        for (int k = quads - 1; k >= 0; k--) {
            int i = Integer.MAX_VALUE - (int) keys[k];
            for (int v = i * 4 * Capture.STRIDE, end = v + 4 * Capture.STRIDE; v < end; v += Capture.STRIDE) {
                out.addVertex(d[v], d[v + 1], d[v + 2], Float.floatToRawIntBits(d[v + 3]), d[v + 4], d[v + 5],
                        Float.floatToRawIntBits(d[v + 6]), Float.floatToRawIntBits(d[v + 7]), d[v + 8], d[v + 9], d[v + 10]);
            }
        }
    }

    /** Keeps the vertices a renderer writes (place, colour, texture's place, overlay, light, normal), to be sorted. */
    private static final class Capture implements VertexConsumer {
        static final int STRIDE = 11;
        float[] data = new float[STRIDE * 4096];
        int count;
        private int at;

        void reset() {
            this.count = 0;
            this.at = 0;
        }

        private int next() {
            int i = this.count * STRIDE;
            if (i + STRIDE > this.data.length) {
                this.data = Arrays.copyOf(this.data, this.data.length * 2);
            }
            this.count++;
            this.at = i;
            return i;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            int i = next();
            float[] d = this.data;
            d[i] = x;
            d[i + 1] = y;
            d[i + 2] = z;
            d[i + 3] = Float.intBitsToFloat(-1);
            d[i + 4] = 0;
            d[i + 5] = 0;
            d[i + 6] = Float.intBitsToFloat(OverlayTexture.NO_OVERLAY);
            d[i + 7] = Float.intBitsToFloat(LightCoordsUtil.FULL_BRIGHT);
            d[i + 8] = 0;
            d[i + 9] = 1;
            d[i + 10] = 0;
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            int i = next();
            float[] d = this.data;
            d[i] = x;
            d[i + 1] = y;
            d[i + 2] = z;
            d[i + 3] = Float.intBitsToFloat(color);
            d[i + 4] = u;
            d[i + 5] = v;
            d[i + 6] = Float.intBitsToFloat(overlay);
            d[i + 7] = Float.intBitsToFloat(light);
            d[i + 8] = nx;
            d[i + 9] = ny;
            d[i + 10] = nz;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.data[this.at + 3] = Float.intBitsToFloat(ARGB.color(a, r, g, b));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.data[this.at + 3] = Float.intBitsToFloat(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.data[this.at + 4] = u;
            this.data[this.at + 5] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.data[this.at + 6] = Float.intBitsToFloat(u & 0xFFFF | v << 16);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.data[this.at + 7] = Float.intBitsToFloat(u & 0xFFFF | v << 16);
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.data[this.at + 8] = x;
            this.data[this.at + 9] = y;
            this.data[this.at + 10] = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
