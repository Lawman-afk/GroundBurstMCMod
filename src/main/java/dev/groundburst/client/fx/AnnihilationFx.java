package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.AnnihilationPayloads;
import dev.groundburst.blast.Annihilations;
import dev.groundburst.config.GBClientConfig;
import dev.groundburst.registry.GBSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An annihilation as the client sees it (DESIGN.md §16.3; the server's side is {@link Annihilations}):
 * <ul>
 * <li>a blinding white flash at once, stronger the nearer and the more squarely it is faced, a quarter of it out of
 * sight;</li>
 * <li>a perfect white sphere growing to its full size in a second and a half (the same curve the server erases by),
 * opaque, so the carving inside it is never seen, under a violet rim glow, shimmering faintly while it holds;</li>
 * <li>once the server has erased all it reaches, the sphere fading in a second, showing the clean void, where a faint
 * violet glow drifts for ten seconds;</li>
 * <li>its sound when it arrives at the speed of sound: a deep thump and the air rushing in (the far sound past
 * {@link #NEAR_SOUND} blocks), and the camera shaken as it does.</li>
 * </ul>
 */
public final class AnnihilationFx {
    static final Identifier SURFACE = GroundBurst.id("textures/fx/annihilation.png");
    /** Ticks the sphere takes to fade once erasing is done; ticks the void glows violet after that. */
    private static final int FADE = 20, AFTERGLOW = 200;
    /** Blocks a tick sound covers (343 m/s); past this the far sound plays; nearer than this a sound is heard in full. */
    private static final double SOUND_SPEED = 17.15, NEAR_SOUND = 160, VIRTUAL_DISTANCE = 8;
    private static final Int2ObjectOpenHashMap<Sphere> ACTIVE = new Int2ObjectOpenHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private AnnihilationFx() {}

    public static void register() {
        AnnihilationPayloads.startHook = AnnihilationFx::start;
        AnnihilationPayloads.endHook = AnnihilationFx::end;
    }

    private static final class Sphere {
        final AnnihilationPayloads.Start start;
        /** Client tick it began on (its age when the client heard of it taken off). */
        final double born;
        /** Client tick erasing finished (NaN: still erasing). */
        double ended = Double.NaN;
        final float flash;
        /** Client tick its sound arrives, and the distance it comes from (NaN once played). */
        double soundAt;
        final double distance;
        final int seed;

        Sphere(AnnihilationPayloads.Start start, double born, float flash, double soundAt, double distance) {
            this.start = start;
            this.born = born;
            this.flash = flash;
            this.soundAt = soundAt;
            this.distance = distance;
            this.seed = start.id() * 7919;
        }

        double age(double now) {
            return now - this.born;
        }

        float radius(double now) {
            return Annihilations.reach(this.start.radius(), age(now) * Annihilations.EXPAND / Math.max(1, this.start.expand()));
        }

        /** How solid the sphere is: whole until erasing is done, then fading. */
        float opacity(double now) {
            return Double.isNaN(this.ended) ? 1 : (float) Math.max(0, 1 - (now - this.ended) / FADE);
        }

        boolean finished(double now) {
            return !Double.isNaN(this.ended) && now - this.ended > FADE + AFTERGLOW;
        }
    }

    private static void start(AnnihilationPayloads.Start start) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        double now = Spectacle.now();
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        Vec3 c = start.center();
        double d = eye.distanceTo(c);
        // The flash: in sight or not (bounced light), faced or not, and fainter far off (but it is antimatter: blinding).
        // In sight if its middle is, or a point a little over it (a burst on a hilltop lights the sky over the brow).
        Vec3 over = c.add(0, Math.min(start.radius() / 4, 12), 0);
        boolean seen = mc.level.clip(new ClipContext(eye, c, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType()
                == HitResult.Type.MISS
                || mc.level.clip(new ClipContext(eye, over, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
        float facing = 0.35f + 0.65f * (float) Math.max(0, mc.player.getViewVector(1).dot(c.subtract(eye).normalize()));
        float near = (float) (1 / (1 + Math.pow(d / (start.radius() * 6 + 40), 2)));
        float flash = start.age() > 4 ? 0 : 1.3f * GBClientConfig.FLASH_STRENGTH.get().floatValue() * near * facing * (seen ? 1 : 0.25f);
        double soundAt = now - start.age() + d / SOUND_SPEED;
        ACTIVE.put(start.id(), new Sphere(start, now - start.age(), flash, soundAt < now - 2 ? Double.NaN : soundAt, d));
    }

    private static void end(AnnihilationPayloads.End end) {
        Sphere s = ACTIVE.get(end.id());
        if (s != null && Double.isNaN(s.ended)) {
            s.ended = Spectacle.now();
        }
    }

    static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    static void clear() {
        ACTIVE.clear();
    }

    /** The flash on the screen now (added to the blasts' in the Spectacle's white-out). */
    static float flash(double now) {
        float sum = 0;
        for (Sphere s : ACTIVE.values()) {
            double t = s.age(now);
            if (t >= 0 && s.flash > 0) {
                sum += s.flash * (float) Math.exp(-t / 14);
            }
        }
        return sum;
    }

    /** Once a client tick: sounds that arrive, the void's afterglow, and spheres long gone forgotten. */
    static void tick(ClientLevel level, Puffs puffs, double now, float quality, Vec3 eye) {
        for (var it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Sphere s = it.next();
            if (!Double.isNaN(s.soundAt) && now >= s.soundAt) {
                sound(s, eye);
                s.soundAt = Double.NaN;
            }
            if (!Double.isNaN(s.ended)) {
                afterglow(s, puffs, now, quality);
            }
            if (s.finished(now)) {
                it.remove();
            }
        }
    }

    private static void sound(Sphere s, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = s.start.center();
        Vec3 toward = c.subtract(eye);
        double d = toward.length();
        boolean far = d > NEAR_SOUND;
        // Heard from far beyond a sound's own reach: played close by (as the blasts' are), quieter the farther it came.
        Vec3 at = d < 1e-3 ? eye : eye.add(toward.scale(Math.min(d, VIRTUAL_DISTANCE) / d));
        float volume = (float) Math.max(0.15, Math.min(1, 1.3 - d / (s.start.radius() * 20 + 400)));
        mc.getSoundManager().play(new SimpleSoundInstance(far ? GBSounds.ANTIMATTER_FAR.get() : GBSounds.ANTIMATTER_NEAR.get(),
                SoundSource.BLOCKS, volume * 1.6f, 0.95f + RANDOM.nextFloat() * 0.1f, RANDOM, at.x, at.y, at.z));
        float shake = (float) (1 / (1 + Math.pow(d / (s.start.radius() * 3 + 30), 2)));
        Spectacle.addTrauma(0.9f * shake);
    }

    /** A faint violet glow drifting in the void it left, thinning away. */
    private static void afterglow(Sphere s, Puffs puffs, double now, float quality) {
        double since = now - s.ended;
        if (since > AFTERGLOW) {
            return;
        }
        float left = 1 - (float) (since / AFTERGLOW);
        float r = s.start.radius();
        float rate = quality * Math.min(6, r / 8) * left;
        int n = (int) rate + (RANDOM.nextFloat() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < n; i++) {
            // Anywhere in the sphere, evenly through its volume.
            double u = Math.cbrt(RANDOM.nextDouble()) * r * 0.92, a = RANDOM.nextDouble() * Math.PI * 2, z = RANDOM.nextDouble() * 2 - 1;
            double ring = Math.sqrt(1 - z * z);
            Puffs.Puff p = new Puffs.Puff();
            p.kind = Puffs.Kind.GLOW;
            p.x = s.start.center().x + Math.cos(a) * ring * u;
            p.y = s.start.center().y + z * u;
            p.z = s.start.center().z + Math.sin(a) * ring * u;
            p.vx = (RANDOM.nextFloat() - 0.5f) * 0.02f;
            p.vz = (RANDOM.nextFloat() - 0.5f) * 0.02f;
            p.rise = 0;                                    // it hangs in the void
            p.drag = 0.02f;
            p.birth = now;
            p.life = 60 + RANDOM.nextInt(60);
            p.size0 = Math.max(1, r / 14) * (1 + RANDOM.nextFloat());
            p.size1 = p.size0 * 1.8f;
            p.color0 = 0xA66CFF;
            p.color1 = 0x5A26C0;
            p.alpha = 0.22f * left;
            p.variant = RANDOM.nextInt(4);
            p.rot = RANDOM.nextFloat() * 6.283f;
            p.spin = (RANDOM.nextFloat() - 0.5f) * 0.01f;
            puffs.add(p, quality);
        }
    }

    /** The spheres: an opaque white body, a faint white bloom over it, and a violet glow brightest at its rim. */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, double now) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        // The body writes depth while it is solid (the cloud type), so vanilla's clouds, drawn after it, stay behind it;
        // fading, it is only blended (the cloud type cuts out what is faint, which would end the fade early).
        List<float[]> solid = new ArrayList<>(), fading = new ArrayList<>(), glows = new ArrayList<>();
        for (Sphere s : ACTIVE.values()) {
            double t = s.age(now);
            float alpha = s.opacity(now);
            float r = s.radius(now);
            if (t < 0 || alpha <= 0 || r < 0.05f) {
                continue;
            }
            Vec3 c = s.start.center();
            float shimmer = (float) t * 0.015f;
            List<float[]> bodies = alpha >= 0.999f ? solid : fading;
            // Two bodies a hair apart, so the surface stays opaque where its texture is thinnest and fades as one.
            bodies.add(Spectacle.sphere(c, camera, r, 0xFFFFFF, alpha, 0.0f, shimmer, shimmer, 0, s.seed));
            bodies.add(Spectacle.sphere(c, camera, r * 0.985f, 0xFBF8FF, alpha, 0.0f, -shimmer, -shimmer * 0.7f, 0, s.seed + 1));
            glows.add(Spectacle.sphere(c, camera, r * 1.035f + 0.2f, 0xA77BFF, 0.85f * alpha, 0.004f, shimmer * 2, shimmer, -1.6f, s.seed + 2));
            glows.add(Spectacle.sphere(c, camera, r * 1.002f, 0xFFFFFF, 0.35f * alpha, 0.0f, shimmer, -shimmer, 0, s.seed + 3));
        }
        if (!solid.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.cloud(SURFACE), (pose, buffer) -> Spectacle.spheres(buffer, pose, solid));
        }
        if (!fading.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.fire(SURFACE), (pose, buffer) -> Spectacle.spheres(buffer, pose, fading));
        }
        if (!glows.isEmpty()) {
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(Spectacle.SHOCK), (pose, buffer) -> Spectacle.spheres(buffer, pose, glows));
        }
    }
}
