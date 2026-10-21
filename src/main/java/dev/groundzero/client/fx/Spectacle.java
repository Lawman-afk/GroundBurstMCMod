package dev.groundzero.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundzero.GroundZero;
import dev.groundzero.blast.BlastPayloads;
import dev.groundzero.blast.StreakNoise;
import dev.groundzero.config.GZClientConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Spectacle (DESIGN.md §12): everything a blast looks like on the client. The server only says where and how big
 * ({@link BlastPayloads}); every radius here is derived from those numbers and the server's shock front.
 *
 * <ul>
 * <li><b>Flash</b>: a white-out over the screen, at once (light outruns sound), scaled by size, distance, whether the
 * fireball is in sight (a quarter of it if not, bounced light) and how directly the camera faces it.</li>
 * <li><b>Fireball</b>: a boiling sphere of two glowing shells with a scrolling fire texture and darkened limbs, inside a
 * swarm of glowing puffs, cooling from white-yellow through orange to dull red as it climbs, then collapsing into
 * dark smoke.</li>
 * <li><b>Shock front</b>: a pale, fading shell at the front's radius, and dust kicked up off the ground (coloured by
 * that ground) all along the ring where it sweeps the surface.</li>
 * <li><b>Debris</b> ({@link Debris}): chunks of the blocks that were there, thrown along the crater's spokes.</li>
 * <li><b>Sparks, jets and smoke</b> ({@link Puffs}): a flare, embers, dirt jets thrown up in a cone along the spokes, fire
 * jets out of the fireball, a smoke plume off the crater, all smoke lit by the sun and sky ({@link FxLight}).</li>
 * <li><b>Mushroom cloud</b> ({@link MushroomCloud}) for nukes: a rolling toroidal cap on a ground-fed stem, collar and
 * condensation shell, seen from far beyond the render distance.</li>
 * <li><b>Camera shake</b>: trauma added when the shock (with the sound) reaches the player, decaying over a second or
 * two.</li>
 * <li><b>Sky and fog</b>: after large blasts the sky, fog and clouds nearby turn a smoky orange-brown for minutes.</li>
 * </ul>
 */
public final class Spectacle {
    static final Identifier FIREBALL = GroundZero.id("textures/fx/fireball.png");
    static final Identifier SHOCK = GroundZero.id("textures/fx/shock.png");
    private static final int LAT = 14, LON = 28;
    /** Smoky orange-brown the sky and fog shift toward (scaled to the sky's own brightness). */
    private static final Vector3fc SMOKE_SKY = new Vector3f(1.0f, 0.62f, 0.38f);

    private static final Int2ObjectOpenHashMap<BlastFx> BLASTS = new Int2ObjectOpenHashMap<>();
    private static final Puffs PUFFS = new Puffs();
    private static final Debris DEBRIS = new Debris();
    /** Client ticks the world has run (not counting pauses): the clock every effect is timed by. */
    static long ticks;
    private static float trauma;
    private static ClientLevel lastLevel;

    private Spectacle() {}

    public static void register(IEventBus modBus) {
        FxRenderTypes.register(modBus);
        dev.groundzero.content.ordnance.ThrownExplosive.smokeHook = Spectacle::grenadeSmoke;
        dev.groundzero.content.launch.MissileEntity.exhaustHook = Spectacle::missileExhaust;
        EffectFx.register();
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(GroundZero.id("blast_flash"), (graphics, delta) -> {
            float flash = flash();
            if (flash > 0.002f) {
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color((int) (flash * 255), 0xFFF6E6));
            }
        }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener(Spectacle::submit);
        NeoForge.EVENT_BUS.addListener(Spectacle::extract);
        NeoForge.EVENT_BUS.addListener(Spectacle::shake);
        NeoForge.EVENT_BUS.addListener(Spectacle::fogColor);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> clear());
    }

    private static void clear() {
        EffectFx.clear();
        BLASTS.clear();
        PUFFS.clear();
        DEBRIS.clear();
        trauma = 0;
    }

    static float quality() {
        return switch (GZClientConfig.FX_QUALITY.get()) {
            case LOW -> 0.35f;
            case MEDIUM -> 0.6f;
            case HIGH -> 1f;
            case ULTRA -> 1.6f;
        };
    }

    private static double now() {
        Minecraft mc = Minecraft.getInstance();
        return ticks + (mc.isPaused() ? 0 : mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    static void addTrauma(float amount) {
        trauma = Math.min(1, trauma + amount * GZClientConfig.CAMERA_SHAKE.get().floatValue());
    }

    // --- from ClientBlasts -------------------------------------------------------------------------------------------------
    /** A blast went off (or the client just heard of one still going). */
    public static void start(BlastPayloads.Start start) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        BlastFx fx = new BlastFx(start, ticks, mc.level, eye);
        // Flash: in sight or not, and facing it or not.
        boolean seen = mc.level.clip(new ClipContext(eye, start.center(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player))
                .getType() == HitResult.Type.MISS;
        Vec3 look = mc.player.getViewVector(1);
        Vec3 toward = start.center().subtract(eye).normalize();
        float facing = 0.35f + 0.65f * (float) Math.max(0, look.dot(toward));
        float near = 1 / (1 + (float) (fx.reaches * fx.reaches) / 9);
        fx.flash = fx.preset.flash() * GZClientConfig.FLASH_STRENGTH.get().floatValue() * near * (seen ? 1 : 0.25f) * facing;
        if (fx.age(ticks) < fx.preset.fireballLife()) {
            fx.ignite(PUFFS, ticks, quality());
        }
        BLASTS.put(start.id(), fx);
    }

    public static void front(int id, float radius) {
        BlastFx fx = BLASTS.get(id);
        if (fx != null) {
            fx.serverRadius = radius;
        }
    }

    public static void end(int id) {
        BlastFx fx = BLASTS.get(id);
        if (fx != null) {
            fx.ended = true;
        }
    }

    /**
     * A smoking smoke grenade, once a client tick: dense, pale, lit puffs filling a dome round it as the cloud billows
     * out; none in the last three seconds, so it thins away. A poison gas grenade's cloud is yellow-green and lower.
     */
    public static void grenadeSmoke(dev.groundzero.content.ordnance.ThrownExplosive grenade) {
        boolean gas = grenade.type() == dev.groundzero.content.ordnance.GrenadeType.POISON_GAS;
        if (grenade.smokeTicks() > grenade.type().cloudTicks() - 60) {
            return;
        }
        var random = grenade.level().getRandom();
        float radius = grenade.smokeRadius();
        float q = quality();
        // A gas canister only vents a thin jet of its own: the gas grid draws the cloud it pours out (EffectFx).
        float rate = q * (gas ? 0.6f : 2.5f);
        int count = (int) rate + (random.nextFloat() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < count; i++) {
            Puffs.Puff p = new Puffs.Puff();
            p.kind = Puffs.Kind.SMOKE;
            // Smoke billows out round the can; gas only puffs out of the can itself (the grid's haze is the cloud, and it
            // knows where the walls are).
            double a = random.nextDouble() * 2 * Math.PI, d = gas ? random.nextDouble() * 0.25 : Math.sqrt(random.nextDouble()) * radius * 0.8;
            p.x = grenade.getX() + Math.cos(a) * d;
            p.y = grenade.getY() + 0.3 + random.nextDouble() * (gas ? 0.3 : radius * 0.5);
            p.z = grenade.getZ() + Math.sin(a) * d;
            p.vx = (float) (Math.cos(a) * 0.05);
            p.vz = (float) (Math.sin(a) * 0.05);
            p.drag = 0.05f;
            p.rise = gas ? 0.0005f : 0.004f;
            p.birth = ticks;
            p.life = 120 + random.nextInt(60);
            p.size0 = 2f;
            p.size1 = 4.5f + random.nextFloat() * 1.5f;
            p.color0 = gas ? 0xC6DA5E : 0xE2E2E0;
            p.color1 = gas ? 0x94AE3C : 0xCACAC8;
            p.alpha = gas ? 0.7f : 0.8f;
            p.variant = random.nextInt(8);
            p.rot = random.nextFloat() * 6.283f;
            p.spin = (random.nextFloat() - 0.5f) * 0.01f;
            p.axisX = grenade.getX();
            p.axisZ = grenade.getZ();
            PUFFS.add(p, q);
        }
    }

    /**
     * A missile, once a client tick: on the pad, a fire under it and smoke billowing out round the pad; in flight, a
     * white-hot flame at its nozzle and a thick trail of smoke that lingers and spreads for half a minute.
     */
    public static void missileExhaust(dev.groundzero.content.launch.MissileEntity missile) {
        var random = missile.level().getRandom();
        float q = quality();
        double t = ticks;
        // Out of the nozzle, opposite the way the model points (its yaw and tilt from straight up).
        double tilt = Math.toRadians(missile.tilt(1)), yaw = Math.toRadians(missile.getYRot());
        net.minecraft.world.phys.Vec3 back = new net.minecraft.world.phys.Vec3(-Math.sin(tilt) * Math.sin(yaw), -Math.cos(tilt),
                -Math.sin(tilt) * Math.cos(yaw));
        double x = missile.getX(), y = missile.getY(), z = missile.getZ();
        if (missile.igniting()) {
            int n = (int) (q * 6);
            for (int i = 0; i < n; i++) {
                Puffs.Puff p = new Puffs.Puff();
                p.kind = Puffs.Kind.SMOKE;
                double a = random.nextDouble() * 2 * Math.PI;
                p.x = x;
                p.y = y + 0.3;
                p.z = z;
                float speed = 0.25f + random.nextFloat() * 0.35f;
                p.vx = (float) Math.cos(a) * speed;
                p.vz = (float) Math.sin(a) * speed;
                p.vy = 0.02f;
                p.drag = 0.04f;
                p.rise = 0.012f;
                p.birth = t;
                p.life = 160 + random.nextInt(80);
                p.size0 = 1.5f;
                p.size1 = 6 + random.nextFloat() * 3;
                p.color0 = 0xEDEBE6;
                p.color1 = 0xB8B4AE;
                p.alpha = 0.85f;
                p.variant = random.nextInt(8);
                p.rot = random.nextFloat() * 6.283f;
                PUFFS.add(p, q);
            }
        }
        // The flame at the nozzle.
        for (int i = 0; i < 3; i++) {
            Puffs.Puff f = new Puffs.Puff();
            f.kind = Puffs.Kind.FIRE;
            f.x = x + back.x * (0.5 + i * 0.8);
            f.y = y + back.y * (0.5 + i * 0.8);
            f.z = z + back.z * (0.5 + i * 0.8);
            f.vx = (float) back.x * 0.3f;
            f.vy = (float) back.y * 0.3f;
            f.vz = (float) back.z * 0.3f;
            f.drag = 0.2f;
            f.birth = t;
            f.life = 4 + random.nextInt(3);
            f.size0 = 1.8f - i * 0.4f;
            f.size1 = 0.6f;
            f.color0 = 0xFFF4D0;
            f.color1 = 0xFF7A20;
            f.alpha = 0.95f;
            f.variant = random.nextInt(4);
            f.rot = random.nextFloat() * 6.283f;
            PUFFS.add(f, 1);
        }
        if (missile.igniting()) {
            return;
        }
        // The trail: puffs laid all along the last tick's path, so it is one unbroken plume however fast it flies.
        double speed = Math.sqrt(missile.distanceToSqr(missile.xo, missile.yo, missile.zo));
        int n = Math.max(1, (int) (speed * 1.2 * q));
        for (int i = 0; i < n; i++) {
            double k = i / (double) n;
            Puffs.Puff p = new Puffs.Puff();
            p.kind = Puffs.Kind.SMOKE;
            p.x = x + back.x * (1 + speed * k) + (random.nextDouble() - 0.5) * 0.4;
            p.y = y + back.y * (1 + speed * k) + (random.nextDouble() - 0.5) * 0.4;
            p.z = z + back.z * (1 + speed * k) + (random.nextDouble() - 0.5) * 0.4;
            p.vx = (float) (random.nextGaussian() * 0.02);
            p.vz = (float) (random.nextGaussian() * 0.02);
            p.drag = 0.02f;
            p.rise = 0.004f;
            p.birth = t - k;
            p.life = 500 + random.nextInt(200);
            p.size0 = 1.2f;
            p.size1 = 5 + random.nextFloat() * 2.5f;
            p.color0 = 0xF2F0EC;
            p.color1 = 0xC4C0BA;
            p.alpha = 0.7f;
            p.variant = random.nextInt(8);
            p.rot = random.nextFloat() * 6.283f;
            p.spin = (random.nextFloat() - 0.5f) * 0.01f;
            PUFFS.add(p, q);
        }
    }

    // --- ticking ---------------------------------------------------------------------------------------------------------------
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        ticks++;
        trauma = Math.max(0, trauma - 0.035f);
        float q = quality();
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        EffectFx.tick(PUFFS, q, eye);
        for (var it = BLASTS.values().iterator(); it.hasNext(); ) {
            BlastFx fx = it.next();
            fx.tick(mc.level, PUFFS, DEBRIS, ticks, q, eye);
            if (fx.finished(ticks)) {
                it.remove();
            }
        }
        PUFFS.tick(ticks);
        DEBRIS.tick(mc.level);
    }

    private static float flash() {
        double now = now();
        float sum = 0;
        for (BlastFx fx : BLASTS.values()) {
            sum += fx.flashAt(fx.age(now));
        }
        return Math.min(0.92f, sum);
    }

    // --- camera, fog and sky ------------------------------------------------------------------------------------------------
    private static void shake(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0) {
            return;
        }
        float s = trauma * trauma;
        float t = (float) now() * 0.45f;
        event.setYaw(event.getYaw() + 5 * s * StreakNoise.value(t, 0.5f, 0, 101));
        event.setPitch(event.getPitch() + 4 * s * StreakNoise.value(t, 7.5f, 0, 202));
        event.setRoll(event.getRoll() + 3 * s * StreakNoise.value(t, 14.5f, 0, 303));
    }

    private static float skyTint() {
        double now = now();
        float most = 0;
        for (BlastFx fx : BLASTS.values()) {
            most = Math.max(most, fx.skyAt(fx.age(now)));
        }
        return Math.min(1, most);
    }

    /**
     * {@code color} moved toward smoky orange-brown, keeping its brightness: first greyed (a blue sky mixed straight
     * with orange would turn lavender), then warmed.
     */
    private static Vector3f smoky(float r, float g, float b, float amount) {
        float lum = 0.3f * r + 0.59f * g + 0.11f * b;
        float grey = Math.min(1, amount * 1.6f);
        r += (lum - r) * grey;
        g += (lum - g) * grey;
        b += (lum - b) * grey;
        float k = Math.min(1, lum * 1.1f);
        return new Vector3f(clamp(r + (SMOKE_SKY.x() * k - r) * amount), clamp(g + (SMOKE_SKY.y() * k - g) * amount),
                clamp(b + (SMOKE_SKY.z() * k - b) * amount));
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static void fogColor(ViewportEvent.ComputeFogColor event) {
        float a = skyTint();
        if (a > 0) {
            Vector3f c = smoky(event.getRed(), event.getGreen(), event.getBlue(), a);
            event.setRed(c.x);
            event.setGreen(c.y);
            event.setBlue(c.z);
        }
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var sky = event.getRenderState().skyRenderState;
        float a = skyTint();
        if (a > 0) {
            if (sky.skyColor != null) {
                sky.skyColor = smoky(sky.skyColor.x(), sky.skyColor.y(), sky.skyColor.z(), a);
            }
            int cloud = event.getRenderState().cloudColor;
            Vector3f c = smoky(ARGB.red(cloud) / 255f, ARGB.green(cloud) / 255f, ARGB.blue(cloud) / 255f, a * 0.8f);
            event.getRenderState().cloudColor = ARGB.color(ARGB.alpha(cloud), (int) (c.x * 255), (int) (c.y * 255), (int) (c.z * 255));
        }
        FxLight.update(sky);                               // smoke is lit by this frame's (tinted) sky
    }

    // --- drawing ---------------------------------------------------------------------------------------------------------------
    private static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (BLASTS.isEmpty() && PUFFS.size() == 0 && DEBRIS.size() == 0)) {
            return;
        }
        var camera = event.getLevelRenderState().cameraRenderState;
        double now = now();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        PoseStack poseStack = event.getPoseStack();
        List<float[]> fire = new ArrayList<>(), cores = new ArrayList<>(), shells = new ArrayList<>();
        for (BlastFx fx : BLASTS.values()) {
            double t = fx.age(now);
            fireball(fx, t, camera.pos, fire, cores);
            shell(fx, t, camera.pos, shells);
        }
        if (!shells.isEmpty()) {
            collector.submitCustomGeometry(poseStack, FxRenderTypes.glow(SHOCK), (pose, buffer) -> spheres(buffer, pose, shells));
        }
        if (!fire.isEmpty()) {
            // The fire's body is blended over what is behind it (so it stays orange against a bright sky); its core adds light.
            collector.submitCustomGeometry(poseStack, FxRenderTypes.fire(FIREBALL), (pose, buffer) -> spheres(buffer, pose, fire));
            collector.submitCustomGeometry(poseStack, FxRenderTypes.glow(FIREBALL), (pose, buffer) -> spheres(buffer, pose, cores));
        }
        float partial = mc.isPaused() ? 0 : mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (BlastFx fx : BLASTS.values()) {
            if (fx.mushroom != null) {
                fx.mushroom.submit(collector, poseStack, camera.pos, camera.orientation, fx.age(now), partial);
            }
        }
        PUFFS.submit(collector, poseStack, level, camera.pos, camera.orientation, now);
        DEBRIS.submit(collector, poseStack, level, camera.pos, partial);
    }

    /** Colour of fire cooling from {@code u} = 0 (white-hot) to 1 (dull red). */
    private static int heat(float u) {
        int[] ramp = {0xFFF2C8, 0xFFC458, 0xFF8424, 0xC84414, 0x5E1C0C};
        float x = Math.max(0, Math.min(0.999f, u)) * (ramp.length - 1);
        int i = (int) x;
        return Puffs.lerpRgb(ramp[i], ramp[i + 1], x - i);
    }

    /**
     * One sphere to draw: centre (camera-relative), radius, colour, alpha, how lumpy, the noise time, texture scroll, and
     * whether its edges fade (limb darkening).
     */
    private static float[] sphere(Vec3 c, Vec3 camera, float radius, int color, float alpha, float boil, float time, float scroll, float limb, int seed) {
        return new float[] {(float) (c.x - camera.x), (float) (c.y - camera.y), (float) (c.z - camera.z), radius, Float.intBitsToFloat(color),
                alpha, boil, time, scroll, limb, seed};
    }

    private static void fireball(BlastFx fx, double t, Vec3 camera, List<float[]> out, List<float[]> cores) {
        int life = fx.preset.fireballLife();
        if (t < 0 || t > life) {
            return;
        }
        float u = (float) (t / life);
        float big = fx.fireballRadius();
        float r = big * (float) (1 - Math.exp(-t / Math.max(0.8, life * 0.08)));
        float alpha = u < 0.6f ? 1 : 1 - (u - 0.6f) / 0.4f;
        Vec3 c = fx.fireballCentre(t);
        // Outer turbulent shell (blended), then a hotter core that adds light.
        out.add(sphere(c, camera, r, heat(u * 1.1f + 0.12f), alpha * 0.9f, 0.22f, (float) t * 0.05f, (float) t * 0.012f, 1, fx.noiseSeed));
        cores.add(sphere(c, camera, r * 0.7f, heat(u * 0.9f), alpha * 0.55f * (1 - u * 0.5f), 0.1f, (float) t * 0.07f + 3, (float) -t * 0.02f,
                0.8f, fx.noiseSeed + 1));
    }

    private static void shell(BlastFx fx, double t, Vec3 camera, List<float[]> out) {
        float strength = fx.preset.shell();
        float outer = fx.start.outer();
        float r = fx.frontRadius(t);
        if (strength <= 0 || r <= fx.fireballRadius() * 0.5f || r >= outer) {
            return;
        }
        float alpha = strength * (float) Math.pow(1 - r / outer, 1.3);
        out.add(sphere(fx.start.center(), camera, r, 0xF4F0EA, alpha, 0.03f, (float) t * 0.02f, (float) t * 0.004f, 1.4f, fx.noiseSeed + 7));
    }

    private static void spheres(VertexConsumer b, PoseStack.Pose pose, List<float[]> spheres) {
        float[] px = new float[(LAT + 1) * (LON + 1)], py = new float[px.length], pz = new float[px.length];
        int[] col = new int[px.length];
        for (float[] s : spheres) {
            float cx = s[0], cy = s[1], cz = s[2], radius = s[3], alpha = s[5], boil = s[6], time = s[7], scroll = s[8], limb = s[9];
            int color = Float.floatToRawIntBits(s[4]), seed = (int) s[10];
            float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz) + 1e-3f;
            for (int i = 0; i <= LAT; i++) {
                double th = Math.PI * i / LAT;
                for (int j = 0; j <= LON; j++) {
                    double ph = 2 * Math.PI * (j % LON) / LON;
                    float dx = (float) (Math.sin(th) * Math.cos(ph)), dy = (float) Math.cos(th), dz = (float) (Math.sin(th) * Math.sin(ph));
                    float rr = radius * (1 + boil * StreakNoise.value(dx * 2.2f + time, dy * 2.2f, dz * 2.2f - time, seed));
                    int k = i * (LON + 1) + j;
                    px[k] = cx + dx * rr;
                    py[k] = cy + dy * rr;
                    pz[k] = cz + dz * rr;
                    // Limb: how squarely this point faces the camera (the camera is at the origin).
                    float facing = Math.abs(dx * -cx + dy * -cy + dz * -cz) / cl;
                    float edge = (float) Math.pow(facing, 0.5 * limb);
                    int a = (int) (Math.max(0, Math.min(1, alpha * (0.25f + 0.75f * edge))) * 255);
                    int shade = Puffs.lerpRgb(color & 0xFFFFFF, 0x401008, (1 - facing) * 0.45f * limb);
                    col[k] = a << 24 | shade;
                }
            }
            for (int i = 0; i < LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    int a = i * (LON + 1) + j, bq = a + LON + 1;
                    float u0 = 2f * j / LON + scroll, u1 = 2f * (j + 1) / LON + scroll, v0 = (float) i / LAT + scroll * 0.5f, v1 = (float) (i + 1) / LAT + scroll * 0.5f;
                    vertex(b, pose, px[a], py[a], pz[a], u0, v0, col[a]);
                    vertex(b, pose, px[bq], py[bq], pz[bq], u0, v1, col[bq]);
                    vertex(b, pose, px[bq + 1], py[bq + 1], pz[bq + 1], u1, v1, col[bq + 1]);
                    vertex(b, pose, px[a + 1], py[a + 1], pz[a + 1], u1, v0, col[a + 1]);
                }
            }
        }
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
