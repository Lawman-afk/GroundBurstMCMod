package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.blast.FreezePayloads;
import dev.groundburst.blast.Freezes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A deep freeze as the client sees it (DESIGN.md §16.16; the server's side is {@link Freezes}):
 * <ul>
 * <li>its burst: a blue-white flash, a dome of white cryogen vapour boiling up and slumping out over the ground, and its
 * sound when it arrives (a thump, the cryogen's roar, ice cracking through it), the camera shaken;</li>
 * <li>its front: a low white wall of frozen air rolling out over the land as the server freezes it, hugging the ground
 * and pooling in low places (it never climbs past where the server's cold stops), glinting with ice; as it passes the
 * camera the crackle of the ground freezing round it and a breath of frost on the screen;</li>
 * <li>its spires: a groan of ice and a burst of ice dust at each one's foot, glints rising up it as it grows;</li>
 * <li>its cold snap, wherever the camera stands in the region it froze: snow falling, the sky and fog paled toward an icy
 * white and the fog drawn in (a whiteout near its heart), a low mist drifting over the ground, diamond dust (ice
 * glinting in the air), creatures' breath smoking, and its wind whistling, all easing off as it does.</li>
 * </ul>
 * (The frost on a player's own screen is vanilla's freezing overlay: the server freezes them.)
 */
public final class FreezeFx {
    static final Identifier SNOW = Identifier.withDefaultNamespace("textures/environment/snow.png");
    private static final double SOUND_SPEED = 17.15, VIRTUAL_DISTANCE = 8;
    /** Ticks its cold takes to clear on the client once the server has ended it. */
    private static final int CLEAR = 60;
    /** Its icy white: what the sky and fog are drawn toward (scaled to the sky's own brightness). */
    static final float ICE_R = 0.86f, ICE_G = 0.92f, ICE_B = 0.98f;
    private static final Int2ObjectOpenHashMap<Cell> ACTIVE = new Int2ObjectOpenHashMap<>();
    private static final List<Spire> SPIRES = new ArrayList<>();
    private static final List<float[]> FLASHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private FreezeFx() {}

    public static void register() {
        FreezePayloads.startHook = FreezeFx::start;
        FreezePayloads.frontHook = FreezeFx::front;
        FreezePayloads.spireHook = FreezeFx::spire;
        FreezePayloads.endHook = FreezeFx::end;
        NeoForge.EVENT_BUS.addListener(FreezeFx::fog);
    }

    /** One freeze: what the server said of it, how far its front has run, its white surge laid out so far. */
    private static final class Cell {
        final FreezePayloads.Start start;
        final double born;
        double ended = Double.NaN;
        /** How far the server says its front has run, and how far out the surge has been laid. */
        float told, surge;
        /** Client tick its burst is heard (NaN once it has been), and the flash it gave. */
        double soundAt;
        boolean passed;
        Wind wind;

        Cell(FreezePayloads.Start start, double born, double soundAt) {
            this.start = start;
            this.born = born;
            this.soundAt = soundAt;
        }

        Vec3 center() {
            return this.start.center();
        }

        double age(double now) {
            return now - this.born;
        }

        /** Its front now: the server's last word moved on by the curve, never far ahead of it (it waits for the world to load). */
        float front(double now) {
            double age = age(now);
            float predicted = Freezes.front(this.start.radius(), this.start.frontTicks(), age);
            float lead = predicted - Freezes.front(this.start.radius(), this.start.frontTicks(), age - 6) + 1;
            return Math.max(this.told, Math.min(predicted, this.told + lead));
        }

        /** How hard its cold bites now: its life's envelope, clearing away once it has ended. */
        float cold(double now) {
            float k = Freezes.cold(age(now), this.start.life());
            return Double.isNaN(this.ended) ? k : (float) (k * Math.max(0, 1 - (now - this.ended) / CLEAR));
        }

        double across(Vec3 at) {
            double dx = at.x - center().x, dz = at.z - center().z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        /**
         * How deep in its cold {@code at} stands (0 to 1): behind its front, 1 at its heart easing to a half at its edge and
         * to nothing a quarter of its radius past it; nothing above where its cold climbs.
         */
        float depth(Vec3 at, double now) {
            double d = across(at), r = this.start.radius();
            if (d > front(now) + 2) {
                return 0;
            }
            double reach = d <= r ? 1 : Math.max(0, 1 - (d - r) / (0.25 * r));
            double above = at.y - (center().y + this.start.climb());
            double high = above <= 0 ? 1 : Math.max(0, 1 - above / 16);
            return (float) (reach * high * (0.5 + 0.5 * (1 - Math.min(1, d / r))));
        }

        boolean finished(double now) {
            return !Double.isNaN(this.ended) && now - this.ended > CLEAR || age(now) > this.start.life() + CLEAR;
        }
    }

    /** A spire of ice growing: its foot, height and growing time, and when it began. */
    private record Spire(Vec3 foot, float height, int ticks, double born) {}

    private static void start(FreezePayloads.Start start) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        double now = Spectacle.now();
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        double d = eye.distanceTo(start.center());
        Cell old = ACTIVE.get(start.id());
        if (old != null && old.wind != null) {
            mc.getSoundManager().stop(old.wind);
        }
        double soundAt = now - start.age() + d / SOUND_SPEED;
        Cell cell = new Cell(start, now - start.age(), soundAt < now - 2 ? Double.NaN : soundAt);
        cell.told = Math.min(start.radius(), Freezes.front(start.radius(), start.frontTicks(), start.age()));
        cell.surge = start.age() > 4 ? cell.told : 0;            // told of it late: no surge for what has passed
        ACTIVE.put(start.id(), cell);
        cell.wind = new Wind(cell);
        mc.getSoundManager().play(cell.wind);
        if (start.age() <= 4) {
            float facing = 0.35f + 0.65f * (float) Math.max(0, mc.player.getViewVector(1).dot(start.center().subtract(eye).normalize()));
            float near = (float) (1 / (1 + Math.pow(d / (start.radius() * 2 + 40), 2)));
            FLASHES.add(new float[] {(float) now, 0.7f * GBClientConfig.FLASH_STRENGTH.get().floatValue() * near * facing});
            if (d < 1200) {
                mc.level.setSkyFlashTime(3);
            }
            dome(cell, Spectacle.PUFFS, now, Spectacle.quality());
        }
    }

    private static void front(FreezePayloads.Front front) {
        Cell cell = ACTIVE.get(front.id());
        if (cell != null) {
            cell.told = Math.max(cell.told, front.reach());
        }
    }

    private static void end(FreezePayloads.End end) {
        Cell cell = ACTIVE.get(end.id());
        if (cell != null && Double.isNaN(cell.ended)) {
            cell.ended = Spectacle.now();
        }
    }

    /** A spire of ice bursting up: its groan, a burst of ice dust round its foot, glints rising up it as it grows. */
    private static void spire(FreezePayloads.Spire s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = Spectacle.now();
        SPIRES.add(new Spire(s.foot(), s.height(), s.ticks(), now));
        mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.CRYO_SPIRE.get(), SoundSource.BLOCKS, 2.5f,
                0.9f + 0.2f * RANDOM.nextFloat() - Math.min(0.25f, s.height() / 160), RANDOM, s.foot().x, s.foot().y, s.foot().z));
        float q = Spectacle.quality();
        int n = (int) (14 * q * Math.min(1.5f, 0.5f + s.height() / 30));
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * 2 * Math.PI;
            Puffs.Puff p = vapour(s.foot().x + Math.cos(a) * 0.8, s.foot().y + 0.4, s.foot().z + Math.sin(a) * 0.8, now);
            float out = 0.12f + 0.2f * RANDOM.nextFloat();
            p.vx = (float) Math.cos(a) * out;
            p.vz = (float) Math.sin(a) * out;
            p.vy = 0.03f + 0.05f * RANDOM.nextFloat();
            p.drag = 0.06f;
            p.life = 40 + RANDOM.nextInt(40);
            p.size0 = 1.2f;
            p.size1 = 3f + 2.5f * RANDOM.nextFloat();
            p.alpha = 0.55f;
            Spectacle.PUFFS.add(p, q);
        }
    }

    static boolean isEmpty() {
        return ACTIVE.isEmpty() && SPIRES.isEmpty();
    }

    static void clear() {
        for (Cell c : ACTIVE.values()) {
            if (c.wind != null) {
                Minecraft.getInstance().getSoundManager().stop(c.wind);
            }
        }
        ACTIVE.clear();
        SPIRES.clear();
        FLASHES.clear();
    }

    /** Its flashes on the screen now: the burst's, and the frost's as the front passes the camera. */
    static float flash(double now) {
        float sum = 0;
        for (float[] f : FLASHES) {
            double t = now - f[0];
            if (t >= 0) {
                sum += f[1] * (float) Math.exp(-t / 6);
            }
        }
        return sum;
    }

    /** How far the sky and fog round the camera are paled toward its icy white (0 to 1). */
    static float tint(Vec3 camera) {
        double now = Spectacle.now();
        float most = 0;
        for (Cell c : ACTIVE.values()) {
            most = Math.max(most, 0.85f * c.cold(now) * c.depth(camera, now));
        }
        return most;
    }

    /** Where the camera stands deepest in a freeze's cold: how deep, and how near its heart (for the whiteout's thickness). */
    private static float[] deepest(Vec3 camera, double now) {
        float depth = 0, heart = 0;
        for (Cell c : ACTIVE.values()) {
            float k = c.cold(now) * c.depth(camera, now);
            if (k > depth) {
                depth = k;
                heart = (float) Math.max(0, 1 - c.across(camera) / c.start.radius());
            }
        }
        return new float[] {depth, heart};
    }

    /** The whiteout: the fog drawn in round the camera in a freeze's cold, thickest near its heart. */
    private static void fog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.ATMOSPHERIC || ACTIVE.isEmpty()) {
            return;
        }
        float[] k = deepest(event.getCamera().position(), Spectacle.now());
        if (k[0] <= 0.01f) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float target = Math.min(far, 110 - 70 * k[1]);
        float amount = Math.min(1, k[0] * 1.15f);
        // (vanilla's own end is hundreds of blocks off: blended straight toward it, the cold would barely show; so the
        // whiteout's distance itself is drawn in as the cold deepens, to its full thickness at full cold)
        float shown = Math.min(far, target / Math.max(0.12f, amount));
        event.setFarPlaneDistance(shown);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), shown * (0.15f - 0.12f * amount)));
    }

    // --- the client's clock ---------------------------------------------------------------------------------------------------
    /**
     * Once a client tick: each freeze's burst heard as it arrives, its surge laid out as its front runs, its front's
     * crackle as it passes the camera; the cold snap's mist, diamond dust and breath round the camera; glints up the
     * growing spires; freezes long gone forgotten.
     */
    static void tick(ClientLevel level, Puffs puffs, double now, float quality, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        float deep = 0;
        for (var it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Cell c = it.next();
            if (!Double.isNaN(c.soundAt) && now >= c.soundAt) {
                burstSound(c, eye);
                c.soundAt = Double.NaN;
            }
            surge(c, level, puffs, now, quality);
            if (!c.passed && c.age(now) > 2 && c.across(eye) <= c.front(now) && c.depth(eye, now) > 0) {
                c.passed = true;
                frontPassed(c, eye, now, c.across(eye) < c.start.core());
            }
            deep = Math.max(deep, c.cold(now) * c.depth(eye, now));
            if (c.finished(now)) {
                if (c.wind != null) {
                    mc.getSoundManager().stop(c.wind);
                }
                it.remove();
            }
        }
        if (deep > 0.03f) {
            coldAir(level, puffs, now, quality, eye, deep);
            breath(level, puffs, now, quality, eye);
        }
        for (var it = SPIRES.iterator(); it.hasNext(); ) {
            Spire s = it.next();
            double t = now - s.born();
            if (t > s.ticks()) {
                it.remove();
                continue;
            }
            float up = (float) (t / s.ticks()) * s.height();
            for (int i = 0; i < 2; i++) {
                if (RANDOM.nextFloat() < quality) {
                    Puffs.Puff p = glint(s.foot().x + (RANDOM.nextDouble() - 0.5) * 2.5, s.foot().y + up * RANDOM.nextDouble(),
                            s.foot().z + (RANDOM.nextDouble() - 0.5) * 2.5, now);
                    p.vy = 0.04f;
                    puffs.add(p, quality);
                }
            }
        }
        FLASHES.removeIf(f -> now - f[0] > 40);
    }

    /** Its burst arriving: a thump and the roar of its cryogen boiling off, played close by when far (as the blasts' are). */
    private static void burstSound(Cell c, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 toward = c.center().subtract(eye);
        double d = toward.length();
        Vec3 at = d < 1e-3 ? eye : eye.add(toward.scale(Math.min(d, VIRTUAL_DISTANCE) / d));
        float volume = (float) Math.max(0.2, Math.min(1, 1.3 - d / 700));
        mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.CRYO_ICBM_BURST.get(), SoundSource.BLOCKS, volume * 1.8f,
                0.95f + RANDOM.nextFloat() * 0.08f, RANDOM, at.x, at.y, at.z));
        Spectacle.addTrauma((float) (0.7 / (1 + Math.pow(d / (c.start.radius() * 0.6 + 20), 2))));
    }

    /** The front sweeping past the camera: the crackle of the ground freezing round it, a breath of frost on the screen. */
    private static void frontPassed(Cell c, Vec3 eye, double now, boolean heart) {
        Minecraft mc = Minecraft.getInstance();
        float k = (float) Math.max(0.3, 1 - c.across(eye) / c.start.radius());
        mc.getSoundManager().play(new SimpleSoundInstance(GBSounds.CRYO_FRONT.get(), SoundSource.BLOCKS, 1.4f * k + 0.4f,
                0.92f + RANDOM.nextFloat() * 0.12f, RANDOM, eye.x, eye.y, eye.z));
        FLASHES.add(new float[] {(float) now, (heart ? 0.45f : 0.22f) * k * GBClientConfig.FLASH_STRENGTH.get().floatValue()});
        Spectacle.addTrauma(0.2f * k);
    }

    /** A puff of white vapour: frozen air and fog, lit like a cloud. */
    private static Puffs.Puff vapour(double x, double y, double z, double now) {
        Puffs.Puff p = new Puffs.Puff();
        p.kind = Puffs.Kind.SMOKE;
        p.x = x;
        p.y = y;
        p.z = z;
        p.birth = now;
        p.color0 = 0xF8FCFF;
        p.color1 = 0xDDE8F0;
        p.variant = RANDOM.nextInt(8);
        p.rot = RANDOM.nextFloat() * 6.283f;
        p.spin = (RANDOM.nextFloat() - 0.5f) * 0.015f;
        return p;
    }

    /** A glint of ice in the air: a tiny bright speck that sparkles as it drifts. */
    private static Puffs.Puff glint(double x, double y, double z, double now) {
        Puffs.Puff p = new Puffs.Puff();
        p.kind = Puffs.Kind.SPARK;
        p.x = x;
        p.y = y;
        p.z = z;
        p.birth = now;
        p.life = 20 + RANDOM.nextInt(30);
        p.size0 = 0.12f + 0.08f * RANDOM.nextFloat();
        p.size1 = 0.05f;
        p.color0 = 0xF2FAFF;
        p.color1 = 0xA8D8FF;
        p.alpha = 0.95f;
        p.drag = 0.05f;
        return p;
    }

    /** Its burst's vapour: a dome of white cryogen boiling up from where it struck and slumping out over the ground. */
    private static void dome(Cell c, Puffs puffs, double now, float quality) {
        Vec3 o = c.center();
        float s = (float) Math.sqrt(c.start.radius() / Freezes.RADIUS);
        int n = (int) (240 * quality * Math.max(0.3f, s));
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * 2 * Math.PI, up = Math.pow(RANDOM.nextDouble(), 0.7) * 0.92;
            double h = Math.sqrt(1 - up * up);
            float size = (13 + 10 * RANDOM.nextFloat()) * s + 2;
            Puffs.Puff p = vapour(o.x, o.y + 0.4 * size, o.z, now);
            float speed = (0.6f + 1.3f * RANDOM.nextFloat()) * s;
            p.vx = (float) (Math.cos(a) * h * speed);
            p.vz = (float) (Math.sin(a) * h * speed);
            p.vy = (float) (up * speed * 1.1);
            p.drag = 0.05f;
            p.gravity = 0.0016f;                                   // the cold vapour climbs, falls back and slumps out
            p.floor = o.y - 0.5 + 0.3 * size;
            p.life = (int) ((100 + RANDOM.nextInt(100)) * Math.max(0.5f, s));
            p.size0 = 4f * s + 1;
            p.size1 = size;
            p.alpha = 0.82f;
            p.axisX = o.x;
            p.axisZ = o.z;
            puffs.add(p, quality);
        }
        for (int i = 0; i < 6; i++) {                              // the heat sink's own flash: a blue-white glow
            Puffs.Puff p = new Puffs.Puff();
            p.kind = Puffs.Kind.GLOW;
            p.x = o.x;
            p.y = o.y + 1;
            p.z = o.z;
            p.birth = now;
            p.life = 14 + RANDOM.nextInt(8);
            p.size0 = 8 * s + 2;
            p.size1 = 26 * s + 4;
            p.color0 = 0xE8F6FF;
            p.color1 = 0x7FC8FF;
            p.alpha = 0.9f;
            p.variant = RANDOM.nextInt(4);
            puffs.add(p, quality);
        }
    }

    /**
     * The white surge laid out ring by ring as the front runs: low vapour on the ground (under the trees), rolling out
     * and settling, taller and thicker near the heart; glints of ice in it. None where the cold does not climb.
     */
    private static void surge(Cell c, ClientLevel level, Puffs puffs, double now, float quality) {
        float r = c.start.radius();
        float target = Math.min(r, c.front(now));
        float step = Math.max(1.5f, r / 48);
        Vec3 o = c.center();
        float s = (float) Math.max(0.35, Math.sqrt(r / Freezes.RADIUS));
        while (c.surge + step <= target + 1e-3) {
            c.surge += step;
            float at = c.surge;
            float k = 1 - at / r;
            int count = (int) Math.min(48, Math.ceil(2 * Math.PI * at * 0.36 * quality));
            for (int i = 0; i < count; i++) {
                double az = (i + RANDOM.nextFloat()) / count * 2 * Math.PI;
                double x = o.x + Math.cos(az) * at, z = o.z + Math.sin(az) * at;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                if (ground - o.y > c.start.climb() || ground <= level.getMinY() + 1) {
                    continue;                                       // a hilltop the cold air never reaches
                }
                // (each sits clear of the ground by a third of its size: a billboard cut by the ground reads as a dome)
                float size = (4 + 3 * RANDOM.nextFloat()) * (0.8f + 0.9f * k) * s;
                Puffs.Puff p = vapour(x, ground + 0.32 * size + RANDOM.nextFloat() * (1.0 + 2.5 * k) * s, z, now);
                float out = (0.06f + 0.16f * RANDOM.nextFloat()) * s;
                p.vx = (float) Math.cos(az) * out;
                p.vz = (float) Math.sin(az) * out;
                p.vy = 0.01f * RANDOM.nextFloat();
                p.drag = 0.035f;
                p.rise = -0.0015f;
                p.floor = ground + 0.3 * size;
                p.life = (int) ((50 + RANDOM.nextInt(60)) * (0.7f + 0.8f * k));
                p.size0 = 0.45f * size;
                p.size1 = size;
                p.alpha = 0.35f + 0.35f * k;
                p.axisX = o.x;
                p.axisZ = o.z;
                puffs.add(p, quality);
                if (RANDOM.nextFloat() < 0.25f) {
                    Puffs.Puff g = glint(x, ground + 0.5 + RANDOM.nextFloat() * 2.5, z, now);
                    g.vx = p.vx;
                    g.vz = p.vz;
                    g.vy = 0.02f;
                    puffs.add(g, quality);
                }
            }
        }
    }

    /** The cold snap's air round the camera: a low mist drifting over the ground, diamond dust glinting in the air. */
    private static void coldAir(ClientLevel level, Puffs puffs, double now, float quality, Vec3 eye, float deep) {
        float mist = 0.5f * deep * quality;
        if (RANDOM.nextFloat() < mist) {
            double a = RANDOM.nextDouble() * 2 * Math.PI, d = 6 + RANDOM.nextDouble() * 24;
            double x = eye.x + Math.cos(a) * d, z = eye.z + Math.sin(a) * d;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            if (Math.abs(ground - eye.y) < 24) {
                Puffs.Puff p = vapour(x, ground + 0.4 + RANDOM.nextFloat() * 1.2, z, now);
                p.vx = (float) (RANDOM.nextGaussian() * 0.015);
                p.vz = (float) (RANDOM.nextGaussian() * 0.015);
                p.drag = 0;
                p.floor = ground + 0.2;
                p.life = 120 + RANDOM.nextInt(80);
                p.size0 = 4;
                p.size1 = 7 + 5 * RANDOM.nextFloat();
                p.alpha = 0.22f * deep;
                puffs.add(p, quality);
            }
        }
        float dust = 1.6f * deep * quality;
        int n = (int) dust + (RANDOM.nextFloat() < dust - (int) dust ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * 2 * Math.PI, d = 1.5 + RANDOM.nextDouble() * 9;
            Puffs.Puff g = glint(eye.x + Math.cos(a) * d, eye.y - 1.5 + RANDOM.nextDouble() * 4, eye.z + Math.sin(a) * d, now);
            g.life = 40 + RANDOM.nextInt(40);
            g.size0 = 0.07f + 0.05f * RANDOM.nextFloat();
            g.vx = (float) (RANDOM.nextGaussian() * 0.004);
            g.vz = (float) (RANDOM.nextGaussian() * 0.004);
            g.vy = -0.006f;
            g.drag = 0;
            puffs.add(g, quality);
        }
    }

    /** Breath smoking in the cold: a little puff from each player's mouth now and then (not the camera's own, seen from inside). */
    private static void breath(ClientLevel level, Puffs puffs, double now, float quality, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        double t = now;
        for (Player pl : level.players()) {
            if (pl.distanceToSqr(eye) > 32 * 32 || pl.isSpectator() || (pl == mc.player && mc.options.getCameraType().isFirstPerson())) {
                continue;
            }
            if ((long) (t + pl.getId() * 13) % 50 != 0) {
                continue;
            }
            Vec3 mouth = pl.getEyePosition().add(pl.getViewVector(1).scale(0.35)).add(0, -0.12, 0);
            float k = 0;
            for (Cell c : ACTIVE.values()) {
                k = Math.max(k, c.cold(now) * c.depth(mouth, now));
            }
            if (k < 0.15f) {
                continue;
            }
            Vec3 look = pl.getViewVector(1);
            for (int i = 0; i < 3; i++) {
                Puffs.Puff p = vapour(mouth.x, mouth.y, mouth.z, now + i);
                p.kind = Puffs.Kind.DUST;
                p.vx = (float) (look.x * 0.04);
                p.vy = (float) (look.y * 0.04 + 0.004);
                p.vz = (float) (look.z * 0.04);
                p.drag = 0.08f;
                p.life = 24 + RANDOM.nextInt(10);
                p.size0 = 0.15f;
                p.size1 = 0.55f;
                p.color0 = 0xF4F8FC;
                p.color1 = 0xE4ECF2;
                p.alpha = 0.32f * k;
                puffs.add(p, quality);
            }
        }
    }

    // --- drawing ---------------------------------------------------------------------------------------------------------------
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, Quaternionf orientation, double now) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        float snow = 0;
        for (Cell c : ACTIVE.values()) {
            snow = Math.max(snow, c.cold(now) * c.depth(camera, now));
        }
        Minecraft mc = Minecraft.getInstance();
        if (snow > 0.04f && mc.level != null && mc.level.canSeeSky(BlockPos.containing(camera))) {
            float shown = snow;
            FxRenderTypes.submit(collector, poseStack, FxRenderTypes.fire(SNOW), (pose, buffer) -> snow(buffer, pose, now, shown));
        }
    }

    /**
     * Snow round the camera: three rings of slowly falling sheets (vanilla's snow, at vanilla's scale: a texture's width
     * a block across, a quarter of its height a block down), drifting sideways as they fall.
     */
    private static void snow(VertexConsumer b, PoseStack.Pose pose, double now, float k) {
        int color = (int) (Math.min(1, 0.85f * k + 0.1f) * 255) << 24 | 0xF4F8FF;
        for (float radius : new float[] {2.6f, 5.5f, 9f}) {
            int segments = radius < 4 ? 10 : radius < 7 ? 16 : 24;
            float fall = (float) (now * (radius < 4 ? 0.012 : 0.009) % 1), drift = (float) (now * 0.0021 * (radius < 4 ? 1.3 : 1) % 1);
            for (int i = 0; i < segments; i++) {
                double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
                float x0 = (float) (Math.cos(a0) * radius), z0 = (float) (Math.sin(a0) * radius);
                float x1 = (float) (Math.cos(a1) * radius), z1 = (float) (Math.sin(a1) * radius);
                float width = (float) (2 * Math.PI * radius / segments);
                float bottom = -5, top = 9, u0 = drift + i * 0.37f, u1 = u0 + width, v0 = fall + i * 0.19f, v1 = v0 + (top - bottom) / 4;
                vertex(b, pose, x0, bottom, z0, u0, v1, color);
                vertex(b, pose, x1, bottom, z1, u1, v1, color);
                vertex(b, pose, x1, top, z1, u1, v0, color);
                vertex(b, pose, x0, top, z0, u0, v0, color);
            }
        }
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /** Its cold snap's wind: a thin whistling loop heard all round, rising and dying with the cold and how deep in it the camera is. */
    private static final class Wind extends AbstractTickableSoundInstance {
        private final Cell cell;

        Wind(Cell cell) {
            super(GBSounds.CRYO_WIND.get(), SoundSource.WEATHER, SoundInstance.createUnseededRandom());
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
            float k = this.cell.cold(now) * this.cell.depth(mc.gameRenderer.mainCamera().position(), now);
            this.volume = Math.max(0.01f, 1.8f * k);
            this.pitch = 0.9f + 0.15f * k;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
