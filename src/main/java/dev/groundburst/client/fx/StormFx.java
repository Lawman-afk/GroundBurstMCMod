package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.StormPayloads;
import dev.groundburst.blast.Storms;
import dev.groundburst.config.GBClientConfig;
import dev.groundburst.registry.GBSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A storm as the client sees it (DESIGN.md §16.14; the server's side is {@link Storms}):
 * <ul>
 * <li>its birth (after the user's look): for its first five seconds the warhead's banks discharge upward, bolts
 * shooting from where it struck into the sky where its cloud deck will gather, their tops crawling out along the cloud
 * base; a burst of four at once with a crack and a zap, then fewer and fewer;</li>
 * <li>its cloud deck, a wide disc of dark cloud over the region turning slowly round the eye (faster near it), its wall
 * cloud hanging lower in the middle;</li>
 * <li>its bolts ({@link StormPayloads.Strike}): a jagged channel from the cloud deck to where it struck, forking as it
 * comes down, flickering and re-striking for a moment, and arcs jumping through the creatures its chain hit; the sky
 * lit up (vanilla's lightning flash), the screen a little if it is near and in view;</li>
 * <li>its thunder, heard when the sound arrives: a crack and a boom near, a long roll far off; the camera shaken by
 * near strikes;</li>
 * <li>rain round the camera wherever it stands under open sky in the storm, the sky and fog darkened toward a slate
 * grey, and the wind's howl, rising and dying with the storm;</li>
 * <li>its wind on this client's own player (the server drives everything else by the same reckoning).</li>
 * </ul>
 */
public final class StormFx {
    static final Identifier BOLT = GroundBurst.id("textures/fx/bolt.png");
    static final Identifier RAIN = Identifier.withDefaultNamespace("textures/environment/rain.png");
    private static final double SOUND_SPEED = 17.15, VIRTUAL_DISTANCE = 8;
    /** Ticks a bolt shows, its brightness each of them (it flickers, and strikes again down the same channel). */
    private static final float[] FLICKER = {1, 0.35f, 0.95f, 0.4f, 0.65f, 0.35f, 0.22f, 0.12f, 0.05f};
    /** A strike this near is heard as a crack, farther as a roll of thunder; and lights the screen a little within this. */
    private static final double NEAR = 64, FLASH_REACH = 96;
    /** Ticks a storm takes to clear away after it ends. */
    private static final int CLEAR = 40;
    /** Ticks its birth lasts (its bolts going up), and the colours of its bolts: a strike's, and the bluer ones going up. */
    private static final int BIRTH = 100;
    private static final int STRIKE_COLOR = 0xDCE8FF, RISE_COLOR = 0xC4D2FF;
    private static final Int2ObjectOpenHashMap<Cell> ACTIVE = new Int2ObjectOpenHashMap<>();
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Thunder> THUNDER = new ArrayList<>();
    private static final List<float[]> FLASHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private StormFx() {}

    public static void register() {
        StormPayloads.startHook = StormFx::start;
        StormPayloads.strikeHook = StormFx::strike;
        StormPayloads.endHook = StormFx::end;
    }

    /** One storm: what the server said of it, and its cloud deck laid out. */
    private static final class Cell {
        final StormPayloads.Start start;
        final double born;
        double ended = Double.NaN;
        Wind wind;
        /** The cloud deck's puffs: ring radius, starting angle, turn rate, height offset, size, sheet cell, darkness. */
        final List<float[]> deck = new ArrayList<>();
        /**
         * Its birth's bolts going up: when (ticks into its life), a seed for its shape, and what is heard of it (2: the
         * warhead's own discharge, a crack and a zap; 1: thunder; 0: nothing); and the next still to come.
         */
        final List<double[]> birth = new ArrayList<>();
        int birthNext;

        Cell(StormPayloads.Start start, double born) {
            this.start = start;
            this.born = born;
            RandomSource b = RandomSource.create(start.id() * 104729L + 17);
            for (int i = 0; i < 4; i++) {                      // the opening burst: four at once
                this.birth.add(new double[] {i * 0.5, b.nextInt(), i == 0 ? 2 : 1});
            }
            for (int t = 2; t < BIRTH; t++) {                  // then fewer and fewer
                if (b.nextDouble() < 0.35 * Math.pow(1 - t / (double) BIRTH, 2)) {
                    this.birth.add(new double[] {t + b.nextDouble(), b.nextInt(), b.nextFloat() < 0.4f ? 1 : 0});
                }
            }
            while (this.birthNext < this.birth.size() && this.birth.get(this.birthNext)[0] < start.age()) {
                this.birthNext++;                              // a client told of it late sees none of what has passed
            }
            RandomSource r = RandomSource.create(start.id() * 7919L);
            float radius = start.radius();
            float size = Math.max(5, radius * 0.17f);
            int rings = 6;
            for (int k = 0; k < rings; k++) {
                float ring = radius * (0.14f + 0.9f * (k + 0.5f) / rings);
                int count = 6 + 7 * k;
                float turn = (float) (0.0016 / (0.25 + (k + 0.5) / rings));
                for (int i = 0; i < count; i++) {
                    this.deck.add(new float[] {ring * (0.9f + 0.2f * r.nextFloat()), (float) (2 * Math.PI * (i + r.nextFloat() * 0.6) / count), turn,
                            (r.nextFloat() - 0.5f) * size * 0.25f, size * (0.8f + 0.5f * r.nextFloat()), r.nextInt(8), 0.86f + 0.1f * r.nextFloat()});
                }
            }
            // The wall cloud: dark and low under the middle, turning with the eye.
            for (int i = 0; i < 12; i++) {
                float ring = radius * 0.12f * r.nextFloat();
                this.deck.add(new float[] {ring, (float) (2 * Math.PI * r.nextFloat()), 0.004f, -size * (0.3f + 0.6f * r.nextFloat()),
                        size * (0.9f + 0.5f * r.nextFloat()), r.nextInt(8), 1.0f});
            }
        }

        Vec3 center() {
            return this.start.center();
        }

        double age(double now) {
            return now - this.born;
        }

        /** How strong it shows: its life's envelope, and clearing away once it has ended. */
        float intensity(double now) {
            float k = Storms.intensity(age(now), this.start.life());
            return Double.isNaN(this.ended) ? k : (float) (k * Math.max(0, 1 - (now - this.ended) / CLEAR));
        }

        /** 1 inside it, falling away to nothing half its radius beyond its edge. */
        float closeness(Vec3 at) {
            double dx = at.x - center().x, dz = at.z - center().z;
            double d = Math.sqrt(dx * dx + dz * dz), r = this.start.radius();
            return d <= r ? 1 : (float) Math.max(0, 1 - (d - r) / (0.5 * r));
        }

        boolean finished(double now) {
            return !Double.isNaN(this.ended) && now - this.ended > CLEAR || age(now) > this.start.life() + CLEAR;
        }
    }

    /** A bolt: its segments (x0, y0, z0, x1, y1, z1, width each), when it struck, its colour. */
    private record Bolt(List<float[]> segments, double born, int color) {}

    /** Thunder on its way: when it is heard, from where, near or far, and whether a discharge's zap comes with it. */
    private record Thunder(double at, Vec3 from, boolean near, boolean zap) {}

    private static void start(StormPayloads.Start start) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Cell old = ACTIVE.get(start.id());
        if (old != null && old.wind != null) {
            mc.getSoundManager().stop(old.wind);
        }
        Cell cell = new Cell(start, Spectacle.now() - start.age());
        ACTIVE.put(start.id(), cell);
        cell.wind = new Wind(cell);
        mc.getSoundManager().play(cell.wind);
    }

    private static void end(StormPayloads.End end) {
        Cell cell = ACTIVE.get(end.id());
        if (cell != null && Double.isNaN(cell.ended)) {
            cell.ended = Spectacle.now();
        }
    }

    /** A strike: its bolt and chain drawn, its thunder on its way, the sky (and, near, the screen) lit up. */
    private static void strike(StormPayloads.Strike s) {
        Minecraft mc = Minecraft.getInstance();
        Cell cell = ACTIVE.get(s.id());
        if (mc.level == null || mc.player == null || cell == null) {
            return;
        }
        double now = Spectacle.now();
        RandomSource r = RandomSource.create(s.seed());
        Vec3 foot = s.foot();
        double drop = cell.start.cloudY() - foot.y;
        Vec3 top = new Vec3(foot.x + (r.nextDouble() - 0.5) * drop * 0.3, cell.start.cloudY(), foot.z + (r.nextDouble() - 0.5) * drop * 0.3);
        List<float[]> segments = new ArrayList<>();
        channel(segments, top, foot, 0.34f, r, 2, 0.035, -1);
        double[] c = s.chain();
        for (int i = 0; i + 5 < c.length; i += 3) {
            channel(segments, new Vec3(c[i], c[i + 1], c[i + 2]), new Vec3(c[i + 3], c[i + 4], c[i + 5]), 0.14f, r, 0, 0.08, -1);
        }
        BOLTS.add(new Bolt(segments, now, STRIKE_COLOR));
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        double d = eye.distanceTo(foot);
        THUNDER.add(new Thunder(now + d / SOUND_SPEED, foot, d < NEAR, false));
        if (d < 400) {
            mc.level.setSkyFlashTime(2);
        }
        if (d < FLASH_REACH) {
            float facing = 0.35f + 0.65f * (float) Math.max(0, mc.player.getViewVector(1).dot(foot.subtract(eye).normalize()));
            FLASHES.add(new float[] {(float) now, 0.28f * GBClientConfig.FLASH_STRENGTH.get().floatValue() * (float) (1 - d / FLASH_REACH) * facing});
        }
    }

    /**
     * The storm's birth: one of its bolts going up from where the warhead struck into the sky where the cloud deck
     * gathers, forking upward as it goes, its top crawling out along the cloud base in two or three arms; heard as
     * {@code event} says, the sky (and, near, the screen) lit up.
     */
    private static void rise(Cell cell, double[] event, double now, Minecraft mc, Vec3 eye) {
        RandomSource r = RandomSource.create((long) event[1]);
        Vec3 foot = cell.center().add(0, 0.3, 0);
        double height = Math.max(8, cell.start.cloudY() - foot.y), spread = height * 0.35;
        Vec3 top = new Vec3(foot.x + (r.nextDouble() - 0.5) * 2 * spread, cell.start.cloudY() + (r.nextDouble() - 0.3) * 4,
                foot.z + (r.nextDouble() - 0.5) * 2 * spread);
        boolean first = event[2] >= 2;
        List<float[]> segments = new ArrayList<>();
        channel(segments, foot, top, first ? 0.5f : 0.36f, r, 2, 0.04, 1);
        double reach = Math.min(60, cell.start.radius() * 0.35);
        for (int i = 2 + r.nextInt(2); i > 0; i--) {
            double a = r.nextDouble() * 2 * Math.PI, len = reach * (0.4 + 0.6 * r.nextDouble());
            channel(segments, top, top.add(Math.cos(a) * len, (r.nextDouble() - 0.5) * 3, Math.sin(a) * len), 0.2f, r, 1, 0.05, 0);
        }
        BOLTS.add(new Bolt(segments, now, RISE_COLOR));
        double d = eye.distanceTo(foot);
        if (event[2] >= 1) {
            THUNDER.add(new Thunder(now + d / SOUND_SPEED, foot, d < NEAR, first));
        }
        if (d < 400 && mc.level != null) {
            mc.level.setSkyFlashTime(2);
        }
        if (d < FLASH_REACH && mc.player != null) {
            float facing = 0.35f + 0.65f * (float) Math.max(0, mc.player.getViewVector(1).dot(foot.subtract(eye).normalize()));
            FLASHES.add(new float[] {(float) now, (first ? 0.4f : 0.2f) * GBClientConfig.FLASH_STRENGTH.get().floatValue()
                    * (float) (1 - d / FLASH_REACH) * facing});
        }
    }

    /**
     * A jagged channel from {@code a} to {@code b}: straight-line points thrown aside by up to {@code wander} of its
     * length mid-way (meeting its ends exactly), narrowing as it goes; now and then (while {@code depth} allows) a
     * branch forking off to one side, ending in the air: down for a bolt coming down ({@code rise} -1), up for one going
     * up (1), level for one crawling along the cloud base (0).
     */
    private static void channel(List<float[]> out, Vec3 a, Vec3 b, float width, RandomSource r, int depth, double wander, int rise) {
        double length = a.distanceTo(b);
        int n = Math.max(4, (int) (length / 2.2));
        Vec3 along = b.subtract(a);
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            double j = i == n ? 0 : length * wander * Math.sin(Math.PI * t) + 0.25;
            Vec3 p = a.add(along.scale(t)).add((r.nextDouble() - 0.5) * 2 * j, (r.nextDouble() - 0.5) * j * 0.6, (r.nextDouble() - 0.5) * 2 * j);
            out.add(new float[] {(float) prev.x, (float) prev.y, (float) prev.z, (float) p.x, (float) p.y, (float) p.z, width * (float) (1 - 0.35 * t)});
            if (depth > 0 && i < n - 1 && r.nextFloat() < 0.13f) {
                double len = b.subtract(p).length() * (0.3 + 0.3 * r.nextDouble());
                double dy = rise == 0 ? (r.nextDouble() - 0.5) * 0.4 : rise * (0.6 + 0.6 * r.nextDouble());
                Vec3 dir = new Vec3((r.nextDouble() - 0.5) * 2, dy, (r.nextDouble() - 0.5) * 2).normalize().scale(len);
                channel(out, p, p.add(dir), width * 0.45f, r, depth - 1, wander * 1.2, rise);
            }
            prev = p;
        }
    }

    static boolean isEmpty() {
        return ACTIVE.isEmpty() && BOLTS.isEmpty();
    }

    static void clear() {
        for (Cell c : ACTIVE.values()) {
            if (c.wind != null) {
                Minecraft.getInstance().getSoundManager().stop(c.wind);
            }
        }
        ACTIVE.clear();
        BOLTS.clear();
        THUNDER.clear();
        FLASHES.clear();
    }

    /** The near strikes' flashes on the screen now. */
    static float flash(double now) {
        float sum = 0;
        for (float[] f : FLASHES) {
            double t = now - f[0];
            if (t >= 0) {
                sum += f[1] * (float) Math.exp(-t / 2.5);
            }
        }
        return sum;
    }

    /** How far the sky and fog round the camera are darkened toward slate grey by a storm over it. */
    static float tint(Vec3 camera) {
        double now = Spectacle.now();
        float most = 0;
        for (Cell c : ACTIVE.values()) {
            most = Math.max(most, 0.78f * c.intensity(now) * c.closeness(camera));
        }
        return most;
    }

    /**
     * Once a client tick: this client's own player driven by the wind of any storm it stands in; thunder heard as it
     * arrives; bolts, flashes and storms long gone forgotten.
     */
    static void tick(ClientLevel level, double now, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        for (var it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Cell c = it.next();
            while (c.birthNext < c.birth.size() && c.birth.get(c.birthNext)[0] <= c.age(now)) {
                rise(c, c.birth.get(c.birthNext++), now, mc, eye);
            }
            if (player != null && !player.isCreative() && !player.isSpectator()) {
                Vec3 push = Storms.windOn(player.position().subtract(c.center()), c.start.radius(), c.intensity(now));
                if (push != Vec3.ZERO) {
                    player.addDeltaMovement(push);
                }
            }
            if (c.finished(now)) {
                it.remove();
            }
        }
        for (var it = THUNDER.iterator(); it.hasNext(); ) {
            Thunder t = it.next();
            if (now < t.at()) {
                continue;
            }
            it.remove();
            double d = t.from().distanceTo(eye);
            if (t.zap()) {                                       // the warhead's banks letting go
                mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.EMP_ZAP.get(), SoundSource.WEATHER, 4f, 0.7f, RANDOM,
                        t.from().x, t.from().y, t.from().z));
            }
            if (t.near()) {
                mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.STORM_THUNDER_NEAR.get(), SoundSource.WEATHER, 4f,
                        0.9f + RANDOM.nextFloat() * 0.2f, RANDOM, t.from().x, t.from().y, t.from().z));
                Spectacle.addTrauma((float) (0.3 * (1 - d / NEAR)));
            } else {
                Vec3 toward = t.from().subtract(eye);
                Vec3 at = eye.add(toward.scale(Math.min(d, VIRTUAL_DISTANCE) / Math.max(1e-3, d)));
                float volume = (float) Math.max(0.15, Math.min(1, 1.3 - d / 500));
                mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.STORM_THUNDER_FAR.get(), SoundSource.WEATHER, volume * 1.6f,
                        0.85f + RANDOM.nextFloat() * 0.25f, RANDOM, at.x, at.y, at.z));
            }
        }
        BOLTS.removeIf(b -> now - b.born() > FLICKER.length);
        FLASHES.removeIf(f -> now - f[0] > 20);
    }

    // --- drawing ---------------------------------------------------------------------------------------------------------------
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, Quaternionf orientation, double now) {
        if (isEmpty()) {
            return;
        }
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        List<float[]> clouds = new ArrayList<>();
        float rain = 0;
        for (Cell c : ACTIVE.values()) {
            float k = c.intensity(now);
            if (k > 0.01f) {
                deck(c, camera, right, up, now, k, clouds);
                rain = Math.max(rain, k * c.closeness(camera));
            }
        }
        if (!clouds.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.cloud(Puffs.SHEET), (pose, buffer) -> Puffs.quads(buffer, pose, clouds));
        }
        if (!BOLTS.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(BOLT), (pose, buffer) -> {
                for (Bolt b : BOLTS) {
                    int tick = (int) Math.floor(now - b.born());
                    if (tick >= 0 && tick < FLICKER.length) {
                        int color = color(b.color(), FLICKER[tick]);
                        for (float[] s : b.segments()) {
                            ribbon(buffer, pose, s, camera, color);
                        }
                    }
                }
            });
        }
        Minecraft mc = Minecraft.getInstance();
        if (rain > 0.05f && mc.level != null && mc.level.canSeeSky(BlockPos.containing(camera))) {
            float shown = rain;
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.fire(RAIN), (pose, buffer) -> rain(buffer, pose, now, shown));
        }
    }

    /** The cloud deck's puffs as camera-facing quads ({@link Puffs#quads}'s form), turning round the eye. */
    private static void deck(Cell c, Vec3 camera, Vector3f right, Vector3f up, double now, float k, List<float[]> out) {
        Vec3 center = c.center();
        double t = c.age(now);
        for (float[] p : c.deck) {
            double angle = p[1] + p[2] * t;
            float x = (float) (center.x + p[0] * Math.cos(angle) - camera.x);
            float y = (float) (c.start.cloudY() + p[3] - camera.y);
            float z = (float) (center.z + p[0] * Math.sin(angle) - camera.z);
            float half = p[4] / 2;
            float rot = (float) (angle * 0.5);
            float cs = (float) Math.cos(rot) * half, sn = (float) Math.sin(rot) * half;
            float ax = right.x * cs + up.x * sn, ay = right.y * cs + up.y * sn, az = right.z * cs + up.z * sn;
            float bx = up.x * cs - right.x * sn, by = up.y * cs - right.y * sn, bz = up.z * cs - right.z * sn;
            int shade = (int) (64 * (2 - p[6]));
            int rgb = shade << 16 | (shade + 4) << 8 | (shade + 12);
            float alpha = 0.93f * k;
            out.add(new float[] {x, y, z, ax, ay, az, bx, by, bz, Float.intBitsToFloat((int) (Math.min(1, alpha) * 255) << 24 | rgb), p[5]});
        }
    }

    /** A camera-facing ribbon along one segment of a bolt (v across it, u along it). */
    private static void ribbon(VertexConsumer b, PoseStack.Pose pose, float[] s, Vec3 camera, int color) {
        float x0 = (float) (s[0] - camera.x), y0 = (float) (s[1] - camera.y), z0 = (float) (s[2] - camera.z);
        float x1 = (float) (s[3] - camera.x), y1 = (float) (s[4] - camera.y), z1 = (float) (s[5] - camera.z);
        Vector3f along = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        Vector3f view = new Vector3f(-(x0 + x1) / 2, -(y0 + y1) / 2, -(z0 + z1) / 2);
        Vector3f side = along.cross(view, new Vector3f());
        if (side.lengthSquared() < 1e-8f) {
            return;
        }
        side.normalize(s[6] * 2.2f);                              // the glow is wider than the channel
        float u1 = along.length() / 4;
        vertex(b, pose, x0 - side.x, y0 - side.y, z0 - side.z, 0, 0, color);
        vertex(b, pose, x0 + side.x, y0 + side.y, z0 + side.z, 0, 1, color);
        vertex(b, pose, x1 + side.x, y1 + side.y, z1 + side.z, u1, 1, color);
        vertex(b, pose, x1 - side.x, y1 - side.y, z1 - side.z, u1, 0, color);
    }

    /** Rain round the camera: two rings of falling sheets (vanilla's rain streaks), driving down fast. */
    private static void rain(VertexConsumer b, PoseStack.Pose pose, double now, float k) {
        int color = color(0xB4C2D2, 0.55f * k);
        float scroll = (float) (-(now * 0.11) % 1);
        for (float radius : new float[] {3.2f, 7.5f}) {
            int segments = radius < 5 ? 12 : 20;
            for (int i = 0; i < segments; i++) {
                double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
                float x0 = (float) (Math.cos(a0) * radius), z0 = (float) (Math.sin(a0) * radius);
                float x1 = (float) (Math.cos(a1) * radius), z1 = (float) (Math.sin(a1) * radius);
                float bottom = -6, top = 12, v0 = scroll, v1 = scroll + (top - bottom) / 8;
                vertex(b, pose, x0, bottom, z0, 0, v1, color);
                vertex(b, pose, x1, bottom, z1, 1, v1, color);
                vertex(b, pose, x1, top, z1, 1, v0, color);
                vertex(b, pose, x0, top, z0, 0, v0, color);
            }
        }
    }

    private static int color(int rgb, float alpha) {
        return (int) (Math.max(0, Math.min(1, alpha)) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /** A storm's wind: a looping howl heard all round (not from a point), rising and dying with it and with how deep in it the camera is. */
    private static final class Wind extends AbstractTickableSoundInstance {
        private final Cell cell;

        Wind(Cell cell) {
            super(GBSounds.STORM_WIND.get(), SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.cell = cell;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            double now = Spectacle.now();
            Minecraft mc = Minecraft.getInstance();
            if (ACTIVE.get(this.cell.start.id()) != this.cell || this.cell.finished(now)) {
                stop();
                return;
            }
            float k = this.cell.intensity(now) * this.cell.closeness(mc.gameRenderer.mainCamera().position());
            this.volume = Math.max(0.01f, 2.2f * k);
            this.pitch = 0.85f + 0.25f * this.cell.intensity(now);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
