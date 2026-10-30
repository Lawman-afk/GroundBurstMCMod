package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.Singularities;
import dev.groundburst.blast.SingularityPayloads;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A singularity as the client sees it (DESIGN.md §16.2; the server's side is {@link Singularities}):
 * <ul>
 * <li>its event horizon, a perfectly black sphere growing as the server says it has (by what it has eaten:
 * {@link SingularityPayloads.Grow}, smoothed between), ringed by a thin bright photon ring;</li>
 * <li>its accretion disc, tilted a little so it shows from the side: hot white-lilac at its inner edge cooling to deep
 * violet, streaked and turning, faster nearer the hole;</li>
 * <li>two jets out along the disc's axis, flickering;</li>
 * <li>the blocks it tears loose ({@link SingularityPayloads.Torn}) as debris spiralling in and settling into the disc
 * until they cross the horizon ({@link Debris}); the air round it darkening and losing its colour;</li>
 * <li>a deep drone rising in pitch as it grows, the crunch of the ground tearing; then its implosion: bright shells
 * rushing inward, the disc winding in faster and brighter and the jets flaring, everything pulled in harder, the horizon
 * shrinking slowly and then all at once to a point; a last pinpoint flash, the implosion heard when its sound arrives
 * (an inrush ending in a deep thud as it goes).</li>
 * </ul>
 */
public final class SingularityFx {
    static final Identifier DISC = GroundBurst.id("textures/fx/accretion.png");
    /** Ticks a hole is kept after it has gone, and its last flash lasts. */
    private static final int AFTER = 40, FLASH = 10;
    /** How far into the implosion's sound its thud comes, ticks (it ends as the horizon closes). */
    private static final double THUD = Singularities.IMPLODE;
    /** Ticks a new reach from the server is eased in over (it is told every four). */
    private static final double EASE = 4;
    private static final double SOUND_SPEED = 17.15, VIRTUAL_DISTANCE = 8;
    /** How steep the disc lies, degrees from flat. */
    private static final double TILT = 18;
    private static final Int2ObjectOpenHashMap<Hole> ACTIVE = new Int2ObjectOpenHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private SingularityFx() {}

    public static void register() {
        SingularityPayloads.startHook = SingularityFx::start;
        SingularityPayloads.growHook = SingularityFx::grow;
        SingularityPayloads.tornHook = SingularityFx::torn;
        SingularityPayloads.collapseHook = SingularityFx::collapse;
    }

    private static final class Hole {
        final SingularityPayloads.Start start;
        final double born;
        double collapsed = Double.NaN;
        /** The disc's normal and two axes across it. */
        final Vec3 normal, e1, e2;
        final int seed;
        /** Its last flash's strength on the screen; when its implosion is heard, and its thud. */
        float flash;
        double implodeHeard = Double.NaN, thudAt = Double.NaN;
        Drone drone;
        /** Its reach as last drawn and as the server last said, and when it said so (eased between). */
        float reachFrom, reachTo;
        double reachAt;

        Hole(SingularityPayloads.Start start, double born) {
            this.start = start;
            this.born = born;
            this.reachFrom = this.reachTo = start.reach();
            this.reachAt = born + start.age();
            this.seed = start.id() * 7919;
            double yaw = (start.id() * 2.399963) % (2 * Math.PI), tilt = Math.toRadians(TILT);
            this.normal = new Vec3(Math.sin(tilt) * Math.cos(yaw), Math.cos(tilt), Math.sin(tilt) * Math.sin(yaw)).normalize();
            this.e1 = this.normal.cross(new Vec3(0, 0, 1)).normalize();
            this.e2 = this.normal.cross(this.e1).normalize();
        }

        Vec3 center() {
            return this.start.center();
        }

        double age(double now) {
            return now - this.born;
        }

        /** How far through its life it is, 0 to 1 (held at the moment it collapsed). */
        float growth(double now) {
            double age = Double.isNaN(this.collapsed) ? age(now) : this.collapsed - this.born;
            return (float) Math.max(0, Math.min(1, age / Math.max(1, this.start.life())));
        }

        /** Its reach now: the server's last word, eased in. */
        float reach(double now) {
            double u = Math.max(0, Math.min(1, (now - this.reachAt) / EASE));
            return (float) (this.reachFrom + (this.reachTo - this.reachFrom) * u);
        }

        /** The server says it reaches {@code reach} now. */
        void grew(float reach, double now) {
            this.reachFrom = reach(now);
            this.reachTo = reach;
            this.reachAt = now;
        }

        /** How far through its implosion it is, 0 to 1 (0 while it grows). */
        double implosion(double now) {
            return Double.isNaN(this.collapsed) ? 0 : Math.max(0, Math.min(1, (now - this.collapsed) / Singularities.IMPLODE));
        }

        /** Whether it has imploded and gone. */
        boolean gone(double now) {
            return !Double.isNaN(this.collapsed) && now - this.collapsed >= Singularities.IMPLODE;
        }

        /** The event horizon's radius now: as the server's while it grows, shrinking to a point as it implodes. */
        float horizon(double now) {
            if (gone(now)) {
                return 0;
            }
            float reach = reach(now);
            return Double.isNaN(this.collapsed) ? Singularities.horizon(reach) : Singularities.implodeHorizon(reach, implosion(now));
        }

        /** How strongly its disc and jets show: whole until it has gone. */
        float presence(double now) {
            return gone(now) ? 0 : 1;
        }

        /** How much harder it pulls now (its implosion's), as the server's. */
        double boost(double now) {
            return Double.isNaN(this.collapsed) ? 1 : Singularities.implodePull(implosion(now));
        }

        boolean finished(double now) {
            return !Double.isNaN(this.collapsed) && now - this.collapsed > Singularities.IMPLODE + AFTER;
        }

        /** The debris's pull (stronger for a bigger hole): a share of its pull on creatures, so the debris spirals in slowly. */
        double pullK(double now) {
            return 0.2 * Singularities.gm(reach(now));
        }
    }

    private static void start(SingularityPayloads.Start start) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Hole old = ACTIVE.get(start.id());
        if (old != null && old.drone != null) {
            mc.getSoundManager().stop(old.drone);
        }
        Hole hole = new Hole(start, Spectacle.now() - start.age());
        ACTIVE.put(start.id(), hole);
        hole.drone = new Drone(hole);
        mc.getSoundManager().play(hole.drone);
    }

    /** It has grown: its new reach, eased in. */
    private static void grow(SingularityPayloads.Grow grow) {
        Hole hole = ACTIVE.get(grow.id());
        if (hole != null && Double.isNaN(hole.collapsed)) {
            hole.grew(grow.reach(), Spectacle.now());
        }
    }

    /** Blocks torn loose: each a chunk of itself, set orbiting; now and then the crunch of the ground tearing. */
    private static void torn(SingularityPayloads.Torn torn) {
        Minecraft mc = Minecraft.getInstance();
        Hole hole = ACTIVE.get(torn.id());
        if (mc.level == null || hole == null) {
            return;
        }
        Vec3 c = hole.center();
        Vec3 ear = mc.gameRenderer.mainCamera().position();
        float q = Spectacle.quality();
        double k = hole.pullK(Spectacle.now());
        java.util.function.DoubleSupplier pull = () -> k * hole.boost(Spectacle.now());
        int sounds = 0;
        for (int i = 0; i < torn.positions().length; i++) {
            BlockPos at = BlockPos.of(torn.positions()[i]);
            BlockState state = Block.stateById(torn.states()[i]);
            if (state.isAir() || state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) {
                continue;
            }
            Vec3 p = Vec3.atCenterOf(at);
            Vec3 r = p.subtract(c);
            double d = Math.max(0.5, r.length());
            // Sent round the hole (a little short of an orbit, so it spirals in), and lifted a little off where it lay.
            Vec3 round = hole.normal.cross(r);
            round = round.lengthSqr() < 1e-6 ? hole.e1 : round.normalize();
            Vec3 v = round.scale(Math.sqrt(k / d) * 0.75).add(hole.normal.scale(0.06));
            float size = 0.3f + RANDOM.nextFloat() * 0.35f;
            Debris.Chunk chunk = new Debris.Chunk(state, Debris.tint(state, mc.level, at), RANDOM, p.x, p.y, p.z, v.x, v.y, v.z, size)
                    .orbit(c.x, c.y, c.z, hole.normal.x, hole.normal.y, hole.normal.z, pull, () -> hole.horizon(Spectacle.now()));
            Spectacle.DEBRIS.add(chunk, q);
            if (sounds < 2 && RANDOM.nextFloat() < 0.08f && ear.distanceTo(p) < 96) {
                mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.SINGULARITY_TEAR.get(), SoundSource.BLOCKS, 1.4f,
                        0.75f + RANDOM.nextFloat() * 0.5f, RANDOM, p.x, p.y, p.z));
                sounds++;
            }
        }
    }

    /** Whether a hole is still growing here, its collapse not yet come. */
    static boolean growing() {
        return ACTIVE.values().stream().anyMatch(h -> Double.isNaN(h.collapsed));
    }

    private static void collapse(SingularityPayloads.Collapse collapse) {
        Minecraft mc = Minecraft.getInstance();
        Hole hole = ACTIVE.get(collapse.id());
        if (mc.level == null || mc.player == null || hole == null || !Double.isNaN(hole.collapsed)) {
            return;
        }
        double now = Spectacle.now();
        hole.grew(collapse.reach(), now - EASE);           // its last reach, at once
        hole.collapsed = now;
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        Vec3 c = hole.center();
        double d = eye.distanceTo(c);
        boolean seen = mc.level.clip(new ClipContext(eye, c, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
        float facing = 0.35f + 0.65f * (float) Math.max(0, mc.player.getViewVector(1).dot(c.subtract(eye).normalize()));
        float near = (float) (1 / (1 + Math.pow(d / (Singularities.horizon(collapse.reach()) * 6 + 30), 2)));
        hole.flash = 0.45f * GBClientConfig.FLASH_STRENGTH.get().floatValue() * near * facing * (seen ? 1 : 0.25f);
        hole.implodeHeard = now + d / SOUND_SPEED;
        hole.thudAt = hole.implodeHeard + THUD;
    }

    static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    static void clear() {
        for (Hole h : ACTIVE.values()) {
            if (h.drone != null) {
                Minecraft.getInstance().getSoundManager().stop(h.drone);
            }
        }
        ACTIVE.clear();
    }

    /** Its last flash on the screen now, as its horizon closes. */
    static float flash(double now) {
        float sum = 0;
        for (Hole h : ACTIVE.values()) {
            if (!Double.isNaN(h.collapsed) && h.flash > 0) {
                double t = now - h.collapsed - Singularities.IMPLODE;
                if (t >= 0) {
                    sum += h.flash * (float) Math.exp(-t / 6);
                }
            }
        }
        return sum;
    }

    /** How far the sky and fog round the camera are drained toward a dark violet grey by a nearby singularity. */
    static float voidTint(Vec3 camera) {
        double now = Spectacle.now();
        float most = 0;
        for (Hole h : ACTIVE.values()) {
            double reach = Singularities.pullRadius(h.reach(now)) * 1.2;
            double d = camera.distanceTo(h.center());
            if (d < reach) {
                most = Math.max(most, (float) ((1 - d / reach) * 0.7 * (0.3 + 0.7 * h.growth(now)) * h.presence(now) * (1 + 0.4 * h.implosion(now))));
            }
        }
        return Math.min(0.8f, most);
    }

    /**
     * Once a client tick: this client's own player drawn in by every singularity in reach (the server pulls everything
     * else by the same reckoning, {@link Singularities#pullOn}); collapses heard as their sound arrives; holes long gone
     * forgotten.
     */
    static void tick(ClientLevel level, double now, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        for (var it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Hole h = it.next();
            if (player != null && !h.gone(now) && !player.isCreative() && !player.isSpectator()) {
                Vec3 to = h.center().subtract(player.getBoundingBox().getCenter());
                float reach = h.reach(now);
                if (to.length() <= Singularities.pullRadius(reach)) {
                    player.addDeltaMovement(Singularities.pullOn(to, Singularities.gm(reach) * h.boost(now)));
                }
            }
            if (!Double.isNaN(h.implodeHeard) && now >= h.implodeHeard) {
                h.implodeHeard = Double.NaN;
                Vec3 toward = h.center().subtract(eye);
                double d = toward.length();
                Vec3 at = d < 1e-3 ? eye : eye.add(toward.scale(Math.min(d, VIRTUAL_DISTANCE) / d));
                float volume = (float) Math.max(0.2, Math.min(1, 1.2 - d / 400));
                mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.SINGULARITY_IMPLODE.get(), SoundSource.BLOCKS, volume * 1.8f,
                        0.95f + RANDOM.nextFloat() * 0.1f, RANDOM, at.x, at.y, at.z));
                Spectacle.addTrauma((float) (0.25 / (1 + Math.pow(d / 60, 2))));
            }
            if (!Double.isNaN(h.thudAt) && now >= h.thudAt) {     // the thud as it goes: the ground shakes
                h.thudAt = Double.NaN;
                double d = h.center().distanceTo(eye);
                Spectacle.addTrauma((float) (0.6 / (1 + Math.pow(d / 70, 2))));
            }
            if (h.finished(now)) {
                it.remove();
            }
        }
    }

    // --- drawing ---------------------------------------------------------------------------------------------------------------
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, double now) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        List<float[]> black = new ArrayList<>(), glows = new ArrayList<>();
        List<Hole> drawn = new ArrayList<>();
        for (Hole h : ACTIVE.values()) {
            double t = h.age(now);
            if (t < 0) {
                continue;
            }
            float r = h.horizon(now);
            Vec3 c = h.center();
            double u = h.implosion(now);
            if (r > 0.02f) {
                black.add(Spectacle.sphere(c, camera, r, 0x000000, 1, 0, 0, 0, 0, h.seed));
                glows.add(Spectacle.sphere(c, camera, r * (1.07f + 0.08f * (float) u) + 0.05f, 0xEBDDFF, (0.9f + 0.1f * (float) u) * h.presence(now), 0,
                        (float) t * 0.03f, 0, -3f, h.seed + 1));
            }
            if (!Double.isNaN(h.collapsed) && !h.gone(now)) {
                // The implosion: five thin violet rims rushing inward from past the hole's edge, one after another, onto the
                // shrinking horizon (rims alone: nothing across their faces), brightening as they close in.
                float from = Math.max(Singularities.horizon(h.reachTo) * 3, h.reachTo * 1.1f);
                for (int k = 0; k < 5; k++) {
                    double start = 0.14 * k, v = (u - start) / (1 - start);
                    if (v > 0 && v < 1) {
                        float radius = (float) (from + (r - from) * Math.pow(v, 1.6));
                        glows.add(Spectacle.rim(c, camera, Math.max(r + 0.1f, radius), 0xB48CFF, (float) (0.2 + 0.8 * v), 0.02f,
                                (float) t * 0.05f, 8f, h.seed + 2 + k));
                    }
                }
            }
            if (h.gone(now)) {
                // Its last flash: a white point at the middle, shrinking away.
                double f = (now - h.collapsed - Singularities.IMPLODE) / FLASH;
                if (f >= 0 && f < 1) {
                    glows.add(Spectacle.sphere(c, camera, (float) (0.3 + 1.8 * (1 - f)), 0xFFFFFF, (float) Math.pow(1 - f, 1.5), 0, 0, 0, 1f, h.seed + 6));
                }
            }
            if (h.presence(now) > 0 && r > 0.02f) {
                drawn.add(h);
            }
        }
        if (!black.isEmpty()) {
            // Black, written to depth: the disc and jets behind it are hidden by it, as light from behind a black hole is.
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.cloud(AnnihilationFx.SURFACE), (pose, buffer) -> Spectacle.spheres(buffer, pose, black));
        }
        if (!drawn.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(DISC), (pose, buffer) -> {
                for (Hole h : drawn) {
                    disc(buffer, pose, h, camera, now);
                }
            });
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(Spectacle.SHOCK), (pose, buffer) -> {
                for (Hole h : drawn) {
                    jets(buffer, pose, h, camera, now);
                }
            });
        }
        if (!glows.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(Spectacle.SHOCK), (pose, buffer) -> Spectacle.spheres(buffer, pose, glows));
        }
    }

    /** The accretion disc: four rings of quads round the hole in the disc's plane, hot within, fading out to its rim. */
    private static void disc(VertexConsumer b, PoseStack.Pose pose, Hole h, Vec3 camera, double now) {
        float r = h.horizon(now);
        float grown = h.growth(now);
        double u = h.implosion(now);
        // Out to most of its reach (where the ground is torn loose to spiral in), wider as it has fed longer; wound in onto
        // the horizon as it implodes, hotter and faster.
        float full = Math.max(r * 1.95f, h.reach(now) * (0.7f + 0.25f * grown));
        float inner = r * 1.3f, outer = (float) (full + (inner * 1.5f - full) * u * u);
        float presence = (float) Math.min(1.6, 1 + 0.6 * u);
        float[] radii = {inner, inner + (outer - inner) * 0.12f, inner + (outer - inner) * 0.45f, inner + (outer - inner) * 0.8f, outer};
        int[] colors = {0xFFE6FF, 0xE09CFF, 0xA055FF, 0x6A2AD8, 0x3A1080};
        float[] alphas = {1f, 0.95f, 0.7f, 0.35f, 0f};
        int segments = 72;
        double t = h.age(now);
        Vec3 c = h.center().subtract(camera);
        for (int i = 0; i < radii.length - 1; i++) {
            for (int j = 0; j < segments; j++) {
                float[] u0 = discVertex(h, c, radii[i], j, segments, t, inner, outer);
                float[] u1 = discVertex(h, c, radii[i + 1], j, segments, t, inner, outer);
                float[] u2 = discVertex(h, c, radii[i + 1], j + 1, segments, t, inner, outer);
                float[] u3 = discVertex(h, c, radii[i], j + 1, segments, t, inner, outer);
                int ca = color(colors[i], alphas[i] * presence), cb = color(colors[i + 1], alphas[i + 1] * presence);
                vertex(b, pose, u0, ca);
                vertex(b, pose, u1, cb);
                vertex(b, pose, u2, cb);
                vertex(b, pose, u3, ca);
            }
        }
    }

    /** A disc vertex: its place (camera-relative) and its texture coordinates, turning faster nearer the hole. */
    private static float[] discVertex(Hole h, Vec3 c, float radius, int j, int segments, double t, float inner, float outer) {
        double a = 2 * Math.PI * j / segments;
        Vec3 p = c.add(h.e1.scale(radius * Math.cos(a))).add(h.e2.scale(radius * Math.sin(a)));
        float spin = (float) (t * 0.03 * Math.pow(inner / Math.max(radius, 0.01), 1.5) * (1 + 25 * Math.pow(h.implosion(Spectacle.now()), 2)));
        return new float[] {(float) p.x, (float) p.y, (float) p.z, 3f * j / segments - spin, (radius - inner) / Math.max(0.01f, outer - inner)};
    }

    /** Two jets out along the disc's axis from the horizon: narrowing cones, flickering, fading to their tips. */
    private static void jets(VertexConsumer b, PoseStack.Pose pose, Hole h, Vec3 camera, double now) {
        float r = h.horizon(now);
        double t = h.age(now);
        double u = h.implosion(now);
        float flicker = 0.75f + 0.25f * (float) Math.sin(t * 0.9 + h.seed) * (float) Math.sin(t * 0.37);
        // As it implodes the jets flare, longer and brighter, then fall back into it.
        float flare = (float) Math.sin(Math.PI * u);
        float alpha = Math.min(1, 0.42f * flicker * h.presence(now) * (0.4f + 0.6f * h.growth(now)) * (1 + 1.5f * flare));
        float full = Singularities.horizon(h.reach(now));
        Vec3 c = h.center().subtract(camera);
        int segments = 16;
        for (int sign = -1; sign <= 1; sign += 2) {
            Vec3 axis = h.normal.scale(sign);
            float length = (float) (full * 5 * (1 + 0.8 * flare) * (1 - Math.pow(u, 4)));
            Vec3 base = c.add(axis.scale(r * 0.9)), tip = c.add(axis.scale(Math.max(r, length)));
            float rb = Math.max(r, full * 0.3f) * 0.22f, rt = rb * 0.18f;
            for (int j = 0; j < segments; j++) {
                double a0 = 2 * Math.PI * j / segments, a1 = 2 * Math.PI * (j + 1) / segments;
                Vec3 o0 = h.e1.scale(Math.cos(a0)).add(h.e2.scale(Math.sin(a0))), o1 = h.e1.scale(Math.cos(a1)).add(h.e2.scale(Math.sin(a1)));
                float v = (float) (-t * 0.05);
                vertex(b, pose, point(base.add(o0.scale(rb)), (float) j / segments, v), color(0xC08CFF, alpha));
                vertex(b, pose, point(tip.add(o0.scale(rt)), (float) j / segments, v + 1), color(0x7A3CFF, 0));
                vertex(b, pose, point(tip.add(o1.scale(rt)), (float) (j + 1) / segments, v + 1), color(0x7A3CFF, 0));
                vertex(b, pose, point(base.add(o1.scale(rb)), (float) (j + 1) / segments, v), color(0xC08CFF, alpha));
            }
        }
    }

    private static float[] point(Vec3 p, float u, float v) {
        return new float[] {(float) p.x, (float) p.y, (float) p.z, u, v};
    }

    private static int color(int rgb, float alpha) {
        return (int) (Math.max(0, Math.min(1, alpha)) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float[] p, int color) {
        b.addVertex(pose, p[0], p[1], p[2]).setColor(color).setUv(p[3], p[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /** The singularity's drone: a deep loop that swells and climbs in pitch as it grows, and dies with it. */
    private static final class Drone extends AbstractTickableSoundInstance {
        private final Hole hole;
        private float fade;

        Drone(Hole hole) {
            super(GBSounds.SINGULARITY_DRONE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.hole = hole;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = 0.55f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            Vec3 c = hole.center();
            this.x = c.x;
            this.y = c.y;
            this.z = c.z;
        }

        @Override
        public void tick() {
            double now = Spectacle.now();
            boolean on = !this.hole.gone(now) && ACTIVE.get(this.hole.start.id()) == this.hole;
            this.fade = on ? Math.min(1, this.fade + 0.05f) : this.fade - 0.3f;
            if (this.fade <= 0 && !on) {
                stop();
                return;
            }
            float grown = this.hole.growth(now);
            float u = (float) this.hole.implosion(now);
            this.volume = Math.max(0.01f, 6f * this.fade * (0.45f + 0.55f * grown) * (1 + 0.5f * u)
                    * (float) Math.min(1, Singularities.pullRadius(this.hole.reach(now)) / 12));
            this.pitch = Math.min(2f, 0.55f + 0.8f * grown + 0.6f * u);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
