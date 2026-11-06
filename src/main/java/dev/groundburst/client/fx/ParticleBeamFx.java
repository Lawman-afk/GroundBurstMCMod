package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.ParticleBeamPayloads;
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
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A Particle Uplink Cannon's beam as the client sees it (DESIGN.md §19.6; the server's side is {@code ParticleBeams}):
 * <ul>
 * <li>the <b>uplink</b>: the cannon's core flares and a beam shoots up out of its pit into the sky, held while the strike
 * lasts;</li>
 * <li>two seconds later the <b>strike</b>: a column of white-blue light comes down from orbit on the target with a flash
 * (the sky lit, and the screen if it is near and in view), a ring of light running out over the ground, a burst of
 * sparks and smoke, the camera shaken;</li>
 * <li>while it <b>burns</b>: the column wavering, a double coil of light twisting down round it, a pool of white-hot
 * glare where it touches, sparks and molten spatter flying off and smoke boiling up out of its trench; the sky and fog
 * round it paled with its light; its roar following it;</li>
 * <li>its <b>end</b>: both beams thin to threads and are gone as the roar dies away.</li>
 * </ul>
 */
public final class ParticleBeamFx {
    static final Identifier BEAM = GroundBurst.id("textures/fx/particle_beam.png");
    static final Identifier FLARE = GroundBurst.id("textures/fx/particle_flare.png");
    /** The beams' colours: the white-hot core, the blue halo round it, the coil twisting down it. */
    private static final int CORE_RGB = 0xF2F6FF, HALO_RGB = 0x6E9CFF, COIL_RGB = 0xB4CCFF, FLARE_RGB = 0xE4EEFF;
    /** The glare's colour in the sky and fog round a beam. */
    static final float GLARE_R = 0.80f, GLARE_G = 0.87f, GLARE_B = 1.0f;
    /** Ticks after the uplink starts that its beam leaves the pit; ticks it takes to reach the sky; to come down from orbit. */
    private static final double LIFT = 4, CLIMB = 6, DROP = 3;
    /** Ticks a beam takes to thin away once it is gone; ticks its strike's ring runs out over the ground. */
    private static final double FADE = 12, RING = 14;
    /** How high the beams reach over where they stand: far above the clouds. */
    private static final double SKY = 420;
    /** Within this the strike flashes the screen and shakes the camera; its glare tints the sky out to this. */
    private static final double NEAR = 96, GLARE_REACH = 220;
    private static final Int2ObjectOpenHashMap<Beam> ACTIVE = new Int2ObjectOpenHashMap<>();
    private static final List<float[]> FLASHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ParticleBeamFx() {}

    public static void register() {
        ParticleBeamPayloads.startHook = ParticleBeamFx::start;
        ParticleBeamPayloads.moveHook = ParticleBeamFx::move;
        ParticleBeamPayloads.endHook = ParticleBeamFx::end;
    }

    /** One beam: what the server said of it, where it touches as last told (shown smoothly between), what has been played. */
    private static final class Beam {
        final ParticleBeamPayloads.Start start;
        /** Client time its uplink started. */
        final double born;
        double ended = Double.NaN;
        /** Where it touched before the latest word, the latest word, and when that came (shown moving between them). */
        Vec3 from, to;
        double movedAt;
        boolean struck, heardUplink;
        Roar roar;

        Beam(ParticleBeamPayloads.Start start, double born) {
            this.start = start;
            this.born = born;
            this.from = this.to = start.at();
        }

        double age(double now) {
            return now - this.born;
        }

        /** Where it touches the ground now. */
        Vec3 foot(double now) {
            double u = Math.max(0, Math.min(1, (now - this.movedAt) / 2));
            return this.from.lerp(this.to, u);
        }

        /** Ticks since it came down from orbit (negative: still on its way). */
        double down(double now) {
            return age(now) - this.start.uplink();
        }

        /** 1 while it burns, thinning to nothing once it is gone. */
        float life(double now) {
            return Double.isNaN(this.ended) ? 1 : (float) Math.max(0, 1 - (now - this.ended) / FADE);
        }

        /** The uplink beam's strength: rising as it leaves the pit, then held. */
        float up(double now) {
            if (this.start.uplink() <= 0) {
                return 0;
            }
            double t = age(now) - LIFT;
            return t < 0 ? 0 : (float) Math.min(1, t / 2) * life(now);
        }

        /** The strike's strength: on once it has come down. */
        float strike(double now) {
            return down(now) < 0 ? 0 : life(now);
        }

        boolean finished(double now) {
            return !Double.isNaN(this.ended) && now - this.ended > FADE
                    || age(now) > this.start.uplink() + this.start.burn() + 60 + FADE;
        }
    }

    private static void start(ParticleBeamPayloads.Start start) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Beam old = ACTIVE.get(start.id());
        if (old != null && old.roar != null) {
            mc.getSoundManager().stop(old.roar);
        }
        double now = Spectacle.now();
        Beam beam = new Beam(start, now - start.age());
        beam.movedAt = now;
        beam.struck = beam.down(now) > DROP + 2;                 // a client told of it late sees no strike that has passed
        beam.heardUplink = start.age() > 20;
        ACTIVE.put(start.id(), beam);
    }

    private static void move(ParticleBeamPayloads.Move move) {
        Beam beam = ACTIVE.get(move.id());
        if (beam != null) {
            double now = Spectacle.now();
            beam.from = beam.foot(now);
            beam.to = move.at();
            beam.movedAt = now;
        }
    }

    private static void end(ParticleBeamPayloads.End end) {
        Beam beam = ACTIVE.get(end.id());
        if (beam != null && Double.isNaN(beam.ended)) {
            double now = Spectacle.now();
            beam.ended = now;
            Minecraft mc = Minecraft.getInstance();
            if (beam.down(now) >= 0) {
                Vec3 f = beam.foot(now);
                mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.PARTICLE_FADE.get(), SoundSource.BLOCKS, 5f, 1f, RANDOM, f.x, f.y, f.z));
            }
        }
    }

    static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    static void clear() {
        for (Beam b : ACTIVE.values()) {
            if (b.roar != null) {
                Minecraft.getInstance().getSoundManager().stop(b.roar);
            }
        }
        ACTIVE.clear();
        FLASHES.clear();
    }

    /** The strikes' flashes on the screen now. */
    static float flash(double now) {
        float sum = 0;
        for (float[] f : FLASHES) {
            double t = now - f[0];
            if (t >= 0) {
                sum += f[1] * (float) Math.exp(-t / 3);
            }
        }
        return sum;
    }

    /** How far the sky and fog round the camera are paled by a beam's light (strong near it, gone {@link #GLARE_REACH} off). */
    static float tint(Vec3 camera) {
        double now = Spectacle.now();
        float most = 0;
        for (Beam b : ACTIVE.values()) {
            float k = Math.max(b.strike(now), 0.5f * b.up(now));
            if (k <= 0) {
                continue;
            }
            Vec3 f = b.strike(now) > 0 ? b.foot(now) : b.start.from();
            double d = Math.hypot(camera.x - f.x, camera.z - f.z);
            float near = (float) Math.max(0, Math.min(1, 1 - (d - 24) / (GLARE_REACH - 24)));
            most = Math.max(most, 0.34f * k * near * near);
        }
        return most;
    }

    // --- each tick ------------------------------------------------------------------------------------------------------------
    /** Once a client tick: each beam's sounds, its strike, its sparks, spatter and smoke; beams long gone forgotten. */
    static void tick(ClientLevel level, Puffs puffs, double now, float quality, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        for (var it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Beam b = it.next();
            if (b.finished(now)) {
                if (b.roar != null) {
                    mc.getSoundManager().stop(b.roar);
                }
                it.remove();
                continue;
            }
            Vec3 core = b.start.from();
            if (!b.heardUplink && b.start.uplink() > 0) {
                b.heardUplink = true;
                mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.PARTICLE_UPLINK.get(), SoundSource.BLOCKS, 8f, 1f, RANDOM,
                        core.x, core.y, core.z));
            }
            float up = b.up(now);
            if (up > 0 && Double.isNaN(b.ended)) {
                pit(core, puffs, now, quality, up);
            }
            if (b.down(now) >= 0 && !b.struck) {
                b.struck = true;
                strike(b, b.foot(now), puffs, now, quality, mc, eye);
            }
            if (b.strike(now) > 0 && Double.isNaN(b.ended)) {
                burn(b.foot(now), puffs, now, quality);
                double d = eye.distanceTo(b.foot(now));
                if (d < 40) {
                    Spectacle.addTrauma((float) (0.03 * (1 - d / 40)));
                }
            }
        }
        FLASHES.removeIf(f -> now - f[0] > 30);
    }

    /** Over the pit as the uplink runs: the core's flare, and sparks crackling off it. */
    private static void pit(Vec3 core, Puffs puffs, double now, float quality, float k) {
        Puffs.Puff g = glowPuff(core.x, core.y, core.z, now);
        g.life = 4;
        g.size0 = 3.5f * k;
        g.size1 = 5f * k;
        g.alpha = 0.7f;
        puffs.add(g, quality);
        for (int i = 0; i < 2; i++) {
            puffs.add(spark(core.x, core.y, core.z, now, 0.25f + 0.35f * RANDOM.nextFloat(), 0.7f), quality);
        }
    }

    /** The strike: its sound, the sky and (near, in view) the screen lit, the camera shaken; a burst of sparks, glow and smoke. */
    private static void strike(Beam b, Vec3 foot, Puffs puffs, double now, float quality, Minecraft mc, Vec3 eye) {
        mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.PARTICLE_STRIKE.get(), SoundSource.BLOCKS, 9f, 1f, RANDOM, foot.x, foot.y, foot.z));
        b.roar = new Roar(b);
        mc.getSoundManager().play(b.roar);
        double d = eye.distanceTo(foot);
        if (d < 500 && mc.level != null) {
            mc.level.setSkyFlashTime(3);
        }
        if (d < NEAR && mc.player != null) {
            float facing = 0.4f + 0.6f * (float) Math.max(0, mc.player.getViewVector(1).dot(foot.subtract(eye).normalize()));
            FLASHES.add(new float[] {(float) now, 0.55f * GBClientConfig.FLASH_STRENGTH.get().floatValue() * (float) (1 - d / NEAR) * facing});
            Spectacle.addTrauma((float) (0.55 * (1 - d / NEAR)));
        }
        for (int i = 0; i < 6; i++) {
            Puffs.Puff g = glowPuff(foot.x, foot.y + 1, foot.z, now);
            g.life = 10 + RANDOM.nextInt(8);
            g.size0 = 6;
            g.size1 = 16 + RANDOM.nextFloat() * 6;
            puffs.add(g, quality);
        }
        for (int i = 0; i < 70; i++) {
            puffs.add(spark(foot.x, foot.y + 0.5, foot.z, now, 0.6f + 1.1f * RANDOM.nextFloat(), 1f), quality);
        }
        for (int i = 0; i < 26; i++) {
            Puffs.Puff s = smoke(foot.x, foot.y, foot.z, now, 3.5f);
            float a = RANDOM.nextFloat() * 6.283f, speed = 0.35f + 0.4f * RANDOM.nextFloat();
            s.vx = (float) Math.cos(a) * speed;
            s.vz = (float) Math.sin(a) * speed;
            s.vy = 0.15f + 0.2f * RANDOM.nextFloat();
            s.drag = 0.07f;
            puffs.add(s, quality);
        }
    }

    /** A tick of its burn where it touches: its glare, sparks and molten spatter flying off, smoke boiling up out of the trench. */
    private static void burn(Vec3 foot, Puffs puffs, double now, float quality) {
        Puffs.Puff g = glowPuff(foot.x, foot.y + 0.6, foot.z, now);
        g.life = 5;
        g.size0 = 5.5f;
        g.size1 = 7.5f + RANDOM.nextFloat() * 2;
        g.alpha = 0.75f;
        puffs.add(g, quality);
        for (int i = 0; i < 5; i++) {
            puffs.add(spark(foot.x, foot.y + 0.3, foot.z, now, 0.4f + 0.7f * RANDOM.nextFloat(), 0.9f), quality);
        }
        for (int i = 0; i < 2; i++) {                             // molten spatter: orange, falling back
            Puffs.Puff p = new Puffs.Puff();
            p.kind = Puffs.Kind.FIRE;
            p.x = foot.x + (RANDOM.nextDouble() - 0.5) * 3;
            p.y = foot.y + 0.2;
            p.z = foot.z + (RANDOM.nextDouble() - 0.5) * 3;
            p.birth = now;
            float a = RANDOM.nextFloat() * 6.283f, speed = 0.15f + 0.3f * RANDOM.nextFloat();
            p.vx = (float) Math.cos(a) * speed;
            p.vz = (float) Math.sin(a) * speed;
            p.vy = 0.35f + 0.35f * RANDOM.nextFloat();
            p.drag = 0.02f;
            p.gravity = 0.06f;
            p.floor = foot.y - 1;
            p.life = 18 + RANDOM.nextInt(12);
            p.size0 = 0.45f;
            p.size1 = 0.2f;
            p.color0 = 0xFFE8A0;
            p.color1 = 0xFF6A1E;
            p.alpha = 1;
            p.variant = RANDOM.nextInt(4);
            p.stretch = 1.2f;
            puffs.add(p, quality);
        }
        if (RANDOM.nextFloat() < 0.7f) {
            double a = RANDOM.nextDouble() * 6.283, r = 1.5 + RANDOM.nextDouble() * 3;
            Puffs.Puff s = smoke(foot.x + Math.cos(a) * r, foot.y + 0.5, foot.z + Math.sin(a) * r, now, 2.2f);
            s.rise = 0.07f + 0.04f * RANDOM.nextFloat();
            puffs.add(s, quality);
        }
    }

    private static Puffs.Puff glowPuff(double x, double y, double z, double now) {
        Puffs.Puff p = new Puffs.Puff();
        p.kind = Puffs.Kind.GLOW;
        p.x = x;
        p.y = y;
        p.z = z;
        p.birth = now;
        p.color0 = 0xF4F8FF;
        p.color1 = 0x8CB4FF;
        p.alpha = 0.85f;
        p.variant = RANDOM.nextInt(4);
        p.rot = RANDOM.nextFloat() * 6.283f;
        return p;
    }

    /** A spark of the beam: white-blue, flung out and up, falling back and dying out. */
    private static Puffs.Puff spark(double x, double y, double z, double now, float speed, float alpha) {
        Puffs.Puff p = new Puffs.Puff();
        p.kind = Puffs.Kind.SPARK;
        p.x = x;
        p.y = y;
        p.z = z;
        p.birth = now;
        double a = RANDOM.nextDouble() * 6.283, up = 0.25 + RANDOM.nextDouble() * 0.75;
        double h = Math.sqrt(1 - up * up);
        p.vx = (float) (Math.cos(a) * h * speed);
        p.vz = (float) (Math.sin(a) * h * speed);
        p.vy = (float) (up * speed);
        p.drag = 0.04f;
        p.gravity = 0.035f;
        p.floor = y - 1.5;
        p.life = 12 + RANDOM.nextInt(18);
        p.size0 = 0.22f + 0.1f * RANDOM.nextFloat();
        p.size1 = 0.06f;
        p.color0 = 0xFFFFFF;
        p.color1 = 0x9CC0FF;
        p.alpha = alpha;
        p.stretch = 0.9f;
        return p;
    }

    /** Smoke off the burning ground: dark, lit like a cloud, boiling up and spreading. */
    private static Puffs.Puff smoke(double x, double y, double z, double now, float size) {
        Puffs.Puff p = new Puffs.Puff();
        p.kind = Puffs.Kind.SMOKE;
        p.x = x;
        p.y = y;
        p.z = z;
        p.birth = now;
        p.life = 90 + RANDOM.nextInt(70);
        p.size0 = size;
        p.size1 = size * 3.2f;
        p.color0 = 0x4A4C52;
        p.color1 = 0x7A7C82;
        p.alpha = 0.7f;
        p.rise = 0.05f;
        p.drag = 0.05f;
        p.windX = 0.012f;
        p.windZ = 0.006f;
        p.variant = RANDOM.nextInt(8);
        p.rot = RANDOM.nextFloat() * 6.283f;
        p.spin = (RANDOM.nextFloat() - 0.5f) * 0.02f;
        return p;
    }

    // --- drawing ---------------------------------------------------------------------------------------------------------------
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, Quaternionf orientation, double now) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(BEAM), (pose, buffer) -> {
            for (Beam b : ACTIVE.values()) {
                beams(buffer, pose, b, camera, now);
            }
        });
        FxRenderTypes.submit(collector, poseStack, FxRenderTypes.glow(FLARE), (pose, buffer) -> {
            for (Beam b : ACTIVE.values()) {
                flares(buffer, pose, b, camera, orientation, now);
            }
        });
    }

    /** A beam's columns: the uplink out of the pit and the strike from orbit, each a halo round a core, the strike's coil round it. */
    private static void beams(VertexConsumer buffer, PoseStack.Pose pose, Beam b, Vec3 camera, double now) {
        float flicker = 0.88f + 0.12f * (float) Math.sin(now * 1.9 + b.start.id()) * (float) Math.sin(now * 0.73);
        float scroll = (float) (now * 0.45 % 1);
        float up = b.up(now);
        if (up > 0) {
            Vec3 core = b.start.from();
            double climb = Math.min(1, (b.age(now) - LIFT) / CLIMB);
            Vec3 top = core.add(0, SKY * climb * climb, 0);
            float thin = b.life(now);
            column(buffer, pose, core, top, camera, 2.6f * thin, color(HALO_RGB, 0.65f * up * flicker), -scroll, 0.6f);
            column(buffer, pose, core, top, camera, 0.9f * thin, color(CORE_RGB, up), -scroll * 1.6f, 0.3f);
        }
        float k = b.strike(now);
        if (k > 0) {
            Vec3 foot = b.foot(now);
            double drop = Math.min(1, b.down(now) / DROP);
            Vec3 sky = foot.add(0, SKY, 0);
            Vec3 bottom = sky.add(0, -SKY * drop, 0);
            float thin = b.life(now) * (0.92f + 0.08f * (float) Math.sin(now * 3.1));
            column(buffer, pose, sky, bottom, camera, 6.5f * thin, color(HALO_RGB, 0.7f * k * flicker), scroll, 0.5f);
            column(buffer, pose, sky, bottom, camera, 3.2f * thin, color(CORE_RGB, 0.8f * k), scroll * 1.3f, 0.35f);
            column(buffer, pose, sky, bottom, camera, 1.6f * thin, color(CORE_RGB, k), scroll * 1.7f, 0.25f);
            if (drop >= 1) {
                for (int strand = 0; strand < 2; strand++) {
                    coil(buffer, pose, foot, camera, now, strand * Math.PI, thin, color(COIL_RGB, 0.6f * k));
                }
                double ring = b.down(now) / RING;
                if (ring < 1) {                                   // the strike's ring of light running out over the ground
                    ring(buffer, pose, foot.add(0, 0.15, 0), camera, (float) (2 + 22 * Math.sqrt(ring)), 1.6f + 2f * (float) ring,
                            color(FLARE_RGB, 0.8f * (float) (1 - ring)));
                }
            }
        }
    }

    /**
     * A column of light from {@code a} to {@code b}, {@code half} wide either side, always facing the camera, its texture
     * scrolled along it by {@code scroll} (once every {@code 4 / repeat} blocks); drawn in pieces 24 blocks long, so it
     * faces the camera all along even close to it.
     */
    private static void column(VertexConsumer buf, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 camera, float half, int color, float scroll, float repeat) {
        double length = a.distanceTo(b);
        if (length < 1e-3 || half <= 0) {
            return;
        }
        int pieces = Math.max(1, (int) Math.ceil(length / 24));
        for (int i = 0; i < pieces; i++) {
            Vec3 p0 = a.lerp(b, i / (double) pieces), p1 = a.lerp(b, (i + 1) / (double) pieces);
            float u0 = (float) (i * length / pieces * repeat / 4) + scroll, u1 = (float) ((i + 1) * length / pieces * repeat / 4) + scroll;
            ribbon(buf, pose, p0, p1, camera, half, u0, u1, color);
        }
    }

    /** One strand of the coil twisting down round the strike's column near the ground, turning as it goes. */
    private static void coil(VertexConsumer buf, PoseStack.Pose pose, Vec3 foot, Vec3 camera, double now, double phase, float thin, int color) {
        double radius = 3.4 * thin, pitch = 0.32, turn = now * 0.55 + phase;
        Vec3 prev = null;
        for (int i = 0; i <= 64; i++) {
            double h = i * 1.25;
            double a = turn + h * pitch;
            double r = radius * (1 + 0.15 * Math.sin(h * 0.3 + now * 0.2));
            Vec3 p = foot.add(Math.cos(a) * r, h, Math.sin(a) * r);
            if (prev != null) {
                float fade = (float) Math.max(0, 1 - h / 80);
                ribbon(buf, pose, prev, p, camera, 0.45f * thin, (float) (h / 6 + now * 0.3), (float) ((h + 1.25) / 6 + now * 0.3),
                        scale(color, fade));
            }
            prev = p;
        }
    }

    /** A flat ring of light on the ground round {@code center}, {@code radius} out, {@code width} across (the beam's profile across it). */
    private static void ring(VertexConsumer buf, PoseStack.Pose pose, Vec3 center, Vec3 camera, float radius, float width, int color) {
        int segments = 48;
        float cx = (float) (center.x - camera.x), cy = (float) (center.y - camera.y), cz = (float) (center.z - camera.z);
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
            float r0 = radius - width, r1 = radius + width;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vertex(buf, pose, cx + c0 * r0, cy, cz + s0 * r0, i / 8f, 0, color);
            vertex(buf, pose, cx + c1 * r0, cy, cz + s1 * r0, (i + 1) / 8f, 0, color);
            vertex(buf, pose, cx + c1 * r1, cy, cz + s1 * r1, (i + 1) / 8f, 1, color);
            vertex(buf, pose, cx + c0 * r1, cy, cz + s0 * r1, i / 8f, 1, color);
        }
    }

    /** The glare where the strike touches (a pool of light flat on the ground, and a flare facing the camera) and over the pit. */
    private static void flares(VertexConsumer buf, PoseStack.Pose pose, Beam b, Vec3 camera, Quaternionf orientation, double now) {
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        float k = b.strike(now);
        if (k > 0 && b.down(now) >= DROP) {
            Vec3 foot = b.foot(now);
            float pulse = 0.9f + 0.1f * (float) Math.sin(now * 2.3);
            float spin = (float) (now * 0.03);
            flat(buf, pose, foot.add(0, 0.12, 0), camera, 9f * pulse, spin, color(FLARE_RGB, 0.85f * k));
            billboard(buf, pose, foot.add(0, 1.6, 0), camera, right, up, 8f * pulse, spin * 2, color(FLARE_RGB, 0.95f * k));
        }
        float u = b.up(now);
        if (u > 0) {
            billboard(buf, pose, b.start.from(), camera, right, up, 3.6f, (float) (now * -0.05), color(FLARE_RGB, 0.95f * u));
        }
    }

    /** A square flat on the ground, turned by {@code spin}. */
    private static void flat(VertexConsumer buf, PoseStack.Pose pose, Vec3 at, Vec3 camera, float half, float spin, int color) {
        float x = (float) (at.x - camera.x), y = (float) (at.y - camera.y), z = (float) (at.z - camera.z);
        float c = (float) Math.cos(spin) * half, s = (float) Math.sin(spin) * half;
        vertex(buf, pose, x - c + s, y, z - s - c, 0, 0, color);
        vertex(buf, pose, x - c - s, y, z - s + c, 0, 1, color);
        vertex(buf, pose, x + c - s, y, z + s + c, 1, 1, color);
        vertex(buf, pose, x + c + s, y, z + s - c, 1, 0, color);
    }

    /** A square facing the camera, turned by {@code spin}. */
    private static void billboard(VertexConsumer buf, PoseStack.Pose pose, Vec3 at, Vec3 camera, Vector3f right, Vector3f up, float half, float spin,
            int color) {
        float x = (float) (at.x - camera.x), y = (float) (at.y - camera.y), z = (float) (at.z - camera.z);
        float cs = (float) Math.cos(spin) * half, sn = (float) Math.sin(spin) * half;
        float ax = right.x * cs + up.x * sn, ay = right.y * cs + up.y * sn, az = right.z * cs + up.z * sn;
        float bx = up.x * cs - right.x * sn, by = up.y * cs - right.y * sn, bz = up.z * cs - right.z * sn;
        vertex(buf, pose, x - ax - bx, y - ay - by, z - az - bz, 0, 1, color);
        vertex(buf, pose, x + ax - bx, y + ay - by, z + az - bz, 1, 1, color);
        vertex(buf, pose, x + ax + bx, y + ay + by, z + az + bz, 1, 0, color);
        vertex(buf, pose, x - ax + bx, y - ay + by, z - az + bz, 0, 0, color);
    }

    /** A camera-facing ribbon from {@code a} to {@code b} (v across it, u along it). */
    private static void ribbon(VertexConsumer buf, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 camera, float half, float u0, float u1, int color) {
        float x0 = (float) (a.x - camera.x), y0 = (float) (a.y - camera.y), z0 = (float) (a.z - camera.z);
        float x1 = (float) (b.x - camera.x), y1 = (float) (b.y - camera.y), z1 = (float) (b.z - camera.z);
        Vector3f along = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        Vector3f view = new Vector3f(-(x0 + x1) / 2, -(y0 + y1) / 2, -(z0 + z1) / 2);
        Vector3f side = along.cross(view, new Vector3f());
        if (side.lengthSquared() < 1e-8f) {
            return;
        }
        side.normalize(half);
        vertex(buf, pose, x0 - side.x, y0 - side.y, z0 - side.z, u0, 0, color);
        vertex(buf, pose, x0 + side.x, y0 + side.y, z0 + side.z, u0, 1, color);
        vertex(buf, pose, x1 + side.x, y1 + side.y, z1 + side.z, u1, 1, color);
        vertex(buf, pose, x1 - side.x, y1 - side.y, z1 - side.z, u1, 0, color);
    }

    private static int color(int rgb, float alpha) {
        return (int) (Math.max(0, Math.min(1, alpha)) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    private static int scale(int argb, float k) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, k)));
        return a << 24 | (argb & 0xFFFFFF);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
        FxVertex.put(b, pose, x, y, z, u, v, color);
    }

    /** The beam's roar, following where it touches: swelling in as it comes down, dying with it. */
    private static final class Roar extends AbstractTickableSoundInstance {
        private final Beam beam;
        private float fade;

        Roar(Beam beam) {
            super(GBSounds.PARTICLE_BEAM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.beam = beam;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            Vec3 f = beam.to;
            this.x = f.x;
            this.y = f.y;
            this.z = f.z;
        }

        @Override
        public void tick() {
            double now = Spectacle.now();
            boolean on = ACTIVE.get(this.beam.start.id()) == this.beam && Double.isNaN(this.beam.ended);
            this.fade = on ? Math.min(1, this.fade + 0.2f) : this.fade - 0.12f;
            if (this.fade <= 0 && !on) {
                stop();
                return;
            }
            Vec3 f = this.beam.foot(now);
            this.x = f.x;
            this.y = f.y + 1;
            this.z = f.z;
            this.volume = Math.max(0.01f, 5f * this.fade);
            this.pitch = 0.95f + 0.05f * (float) Math.sin(now * 0.21);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
