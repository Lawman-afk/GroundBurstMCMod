package dev.groundburst.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.DesignatorPayloads;
import dev.groundburst.content.launch.Designators;
import dev.groundburst.content.launch.LaserDesignatorItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Laser Designator's beam (DESIGN.md §17.13), drawn for every player lasing: a thin red line from the designator at
 * the eye all the way to where it lands, a pulsing spot there and a glint at its port; for our own designator in
 * call-for-fire mode, a ring closing on the spot as the lock builds. All of it is additive light, as a visible laser is:
 * faint against a bright sky, bright at night. The beam keeps one width on screen however far it reaches, so a spot far
 * off still shows; a beam that lands on nothing (open sky, smoke) fades out. Our own beam is traced here each frame, as
 * far as we see; another player's goes to the spot the server traced for it ({@link DesignatorPayloads.Beam}).
 */
public final class LaserFx {
    static final Identifier LASER = GroundBurst.id("textures/fx/laser.png");
    /** The texture's parts: the beam's cross-section along the top third, the spot and the ring in the two cells below. */
    private static final float V_BEAM = 1 / 3f;
    private static final int BEAM = 0xFF3020, SPOT = 0xFF5A3C, RING = 0xFF3A28, CORE = 0xFF2010;
    /**
     * The beam's half-width on screen (radians), and the longest piece it is drawn in: shorter near the camera (half its
     * distance), so a beam passing close by keeps its width there instead of taking its far ends' width.
     */
    private static final float WIDTH = 0.0034f, SEGMENT = 16;
    /** How far a beam that lands on nothing is drawn, fading out; ticks another player's beam from the server is believed. */
    private static final double NO_RETURN = 160;
    private static final int FRESH = 10;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Another player's beam as the server traced it: where it lands (null: on nothing), and the game time it came. */
    private record Remote(@Nullable Vec3 at, long time) {}

    private static final Int2ObjectOpenHashMap<Remote> REMOTE = new Int2ObjectOpenHashMap<>();
    /** Our own beam's trace, kept for the frame (the sight reads it too) and redone each tick and whenever we move. */
    private static @Nullable Vec3 tracedEye, tracedLook;
    private static long tracedTick = Long.MIN_VALUE;
    private static Designators.@Nullable Hit traced;
    /** The photographers' runs: draw our own beam from the server's spot, as another player sees it. */
    static boolean serverBeamForSelf;

    /** One player's beam this frame: from its lens, to where it ends, the spot (null: it lands on nothing), and whether it is ours. */
    private record Beam(Vec3 from, Vec3 to, @Nullable Vec3 spot, boolean ours, boolean scoped) {}

    private LaserFx() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LaserFx::submit);
        DesignatorPayloads.beamHook = b -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                REMOTE.put(b.holder(), new Remote(b.lands() ? new Vec3(b.x(), b.y(), b.z()) : null, level.getGameTime()));
            }
        };
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> REMOTE.clear());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone event) -> REMOTE.clear());     // a new dimension
    }

    /** How far our designator reaches: as far as we see (our render distance), to its greatest reach. */
    static int range(Minecraft mc) {
        return Math.min(Designators.MAX_RANGE, mc.options.getEffectiveRenderDistance() * 16 + 8);
    }

    /** Where our own beam lands this frame (traced once a frame, as far as we see), or null. */
    static Designators.@Nullable Hit ourHit(Minecraft mc, float partial) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition(partial), look = mc.player.getViewVector(partial);
        long tick = mc.level.getGameTime();
        if (tick != tracedTick || !eye.equals(tracedEye) || !look.equals(tracedLook)) {
            traced = Designators.trace(mc.level, mc.player, eye, look, range(mc));
            tracedEye = eye;
            tracedLook = look;
            tracedTick = tick;
        }
        return traced;
    }

    /** Whether {@code player} is lasing: holding a designator to the eye. */
    public static boolean lasing(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof LaserDesignatorItem;
    }

    /** Whether the camera is our own eye and we are lasing: the designator's sight is up. */
    public static boolean scoped(Minecraft mc) {
        return mc.player != null && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson() && lasing(mc.player);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = mc.isPaused() ? 1 : mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Beam> beams = new ArrayList<>();
        for (AbstractClientPlayer p : level.players()) {
            if (lasing(p)) {
                beams.add(beam(mc, level, p, partial));
            }
        }
        if (beams.isEmpty()) {
            return;
        }
        var camera = event.getLevelRenderState().cameraRenderState;
        Vec3 eye = camera.pos;
        Quaternionf orientation = camera.orientation;
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        double now = Spectacle.now();
        DesignatorPayloads.Status status = DesignatorScope.status();
        FxRenderTypes.submit(event.getSubmitNodeCollector(), event.getPoseStack(), FxRenderTypes.fire(LASER), (pose, buffer) -> {
            for (Beam b : beams) {
                pieces(buffer, pose, b, eye, 0.6f, CORE, 0.5f);       // the beam's red middle, blended: red over bright ground too
                core(buffer, pose, b, eye, right, up, now);
            }
        });
        FxRenderTypes.submit(event.getSubmitNodeCollector(), event.getPoseStack(), FxRenderTypes.glow(LASER), (pose, buffer) -> {
            for (Beam b : beams) {
                draw(buffer, pose, b, eye, right, up, now, b.ours() ? status : null);
            }
        });
    }

    /** {@code p}'s beam this frame: from the designator's laser port (just under the eye in our own sight). */
    private static Beam beam(Minecraft mc, ClientLevel level, AbstractClientPlayer p, float partial) {
        Vec3 eye = p.getEyePosition(partial), look = p.getViewVector(partial);
        Vec3 right = look.cross(UP);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look);
        boolean ours = p == mc.player, scoped = ours && scoped(mc);
        // Raised to the eye, its laser port is in front of the face's middle (laser_designator_raised's place for it).
        Vec3 from = scoped ? eye.add(look.scale(0.3)).subtract(up.scale(0.1)) : eye.add(look.scale(0.68));
        Vec3 spot;
        Remote remote = ours && !serverBeamForSelf ? null : REMOTE.get(p.getId());
        if (ours && !serverBeamForSelf) {
            Designators.Hit hit = ourHit(mc, partial);
            spot = hit == null ? null : hit.surface();
        } else if (remote != null && level.getGameTime() - remote.time() <= FRESH) {
            spot = remote.at();                            // where the server's beam lands: the spot missiles home on
        } else {
            Designators.Hit hit = Designators.trace(level, p, eye, look, range(mc));   // (until the server's comes)
            spot = hit == null ? null : hit.surface();
        }
        return spot == null ? new Beam(from, eye.add(look.scale(NO_RETURN)), null, ours, scoped) : new Beam(from, spot, spot, ours, scoped);
    }

    private static void draw(VertexConsumer b, PoseStack.Pose pose, Beam beam, Vec3 camera, Vector3f right, Vector3f up, double now,
            DesignatorPayloads.@Nullable Status status) {
        pieces(b, pose, beam, camera, 1, BEAM, 1);
        float pulse = 0.75f + 0.25f * (float) Math.sin(now * Math.PI / 2);        // five times a second: its pulse code
        if (!beam.scoped()) {
            quad(b, pose, beam.from(), camera, right, up, 0.05f, 0, 0.5f, color(SPOT, 0.9f));  // the lens
        }
        Vec3 spot = beam.spot();
        if (spot == null) {
            return;
        }
        Vec3 toCamera = camera.subtract(spot);
        double dist = toCamera.length();
        Vec3 at = dist > 0.6 ? spot.add(toCamera.scale(0.3 / dist)) : spot;   // a little toward the eye, clear of the face it is on
        float size = (float) (0.18 + 0.0075 * dist);
        quad(b, pose, at, camera, right, up, size * (0.92f + 0.08f * pulse), 0, 0.5f, color(SPOT, pulse));
        if (status != null && status.mode() == LaserDesignatorItem.Mode.FIRE.ordinal() && status.lock() > 0) {
            boolean blink = status.fired() && (int) (now / 3) % 2 == 0;
            float ring = status.fired() ? 1.3f : 1.3f + 3.2f * (1 - status.lock());
            quad(b, pose, at, camera, right, up, size * ring, 0.5f, 1, color(RING, status.fired() ? (blink ? 1 : 0.35f) : 0.35f + 0.6f * status.lock()));
        }
    }

    /** The spot's middle, blended over the face it is on: by day added light hardly shows on a bright face, this does. */
    private static void core(VertexConsumer b, PoseStack.Pose pose, Beam beam, Vec3 camera, Vector3f right, Vector3f up, double now) {
        Vec3 spot = beam.spot();
        if (spot == null) {
            return;
        }
        Vec3 toCamera = camera.subtract(spot);
        double dist = toCamera.length();
        Vec3 at = dist > 0.6 ? spot.add(toCamera.scale(0.28 / dist)) : spot;
        float pulse = 0.75f + 0.25f * (float) Math.sin(now * Math.PI / 2);
        quad(b, pose, at, camera, right, up, (float) (0.18 + 0.0075 * dist) * 0.5f, 0, 0.5f, color(CORE, 0.6f + 0.35f * pulse));
    }

    /**
     * The beam in camera-facing pieces, {@code width} times its width, in {@code rgb} at {@code alpha}: shorter pieces
     * near the camera; a beam that lands on nothing fading out along its length.
     */
    private static void pieces(VertexConsumer b, PoseStack.Pose pose, Beam beam, Vec3 camera, float width, int rgb, float alpha) {
        Vec3 d = beam.to().subtract(beam.from());
        double length = d.length();
        if (length < 1e-3) {
            return;
        }
        Vec3 dir = d.scale(1 / length);
        double t = 0;
        for (int i = 0; i < 256 && t < length; i++) {
            double near = beam.from().add(dir.scale(t)).distanceTo(camera);
            double t1 = Math.min(length, t + Math.clamp(near * 0.5, 0.2, SEGMENT));
            float a0 = beam.spot() == null ? (float) (1 - t / length) : 1, a1 = beam.spot() == null ? (float) (1 - t1 / length) : 1;
            ribbon(b, pose, beam.from().add(dir.scale(t)), beam.from().add(dir.scale(t1)), camera, width, rgb, alpha * a0, alpha * a1);
            t = t1;
        }
    }

    /** A piece of beam facing the camera, as wide on screen at either end ({@code width} times the beam's). */
    private static void ribbon(VertexConsumer b, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 camera, float width, int rgb, float a0, float a1) {
        float x0 = (float) (p0.x - camera.x), y0 = (float) (p0.y - camera.y), z0 = (float) (p0.z - camera.z);
        float x1 = (float) (p1.x - camera.x), y1 = (float) (p1.y - camera.y), z1 = (float) (p1.z - camera.z);
        Vector3f along = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        Vector3f view = new Vector3f(-(x0 + x1) / 2, -(y0 + y1) / 2, -(z0 + z1) / 2);
        Vector3f side = along.cross(view, new Vector3f());
        if (side.lengthSquared() < 1e-12f) {
            return;
        }
        side.normalize();
        float w = WIDTH * width;
        float w0 = w * (float) Math.sqrt(x0 * x0 + y0 * y0 + z0 * z0), w1 = w * (float) Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
        int c0 = color(rgb, a0), c1 = color(rgb, a1);
        vertex(b, pose, x0 - side.x * w0, y0 - side.y * w0, z0 - side.z * w0, 0.25f, 0, c0);
        vertex(b, pose, x0 + side.x * w0, y0 + side.y * w0, z0 + side.z * w0, 0.25f, V_BEAM, c0);
        vertex(b, pose, x1 + side.x * w1, y1 + side.y * w1, z1 + side.z * w1, 0.75f, V_BEAM, c1);
        vertex(b, pose, x1 - side.x * w1, y1 - side.y * w1, z1 - side.z * w1, 0.75f, 0, c1);
    }

    /** A camera-facing square of half-size {@code half} at {@code at}, showing the cell from u0 to u1 below the beam. */
    private static void quad(VertexConsumer b, PoseStack.Pose pose, Vec3 at, Vec3 camera, Vector3f right, Vector3f up, float half,
            float u0, float u1, int color) {
        float x = (float) (at.x - camera.x), y = (float) (at.y - camera.y), z = (float) (at.z - camera.z);
        float rx = right.x * half, ry = right.y * half, rz = right.z * half;
        float ux = up.x * half, uy = up.y * half, uz = up.z * half;
        vertex(b, pose, x - rx - ux, y - ry - uy, z - rz - uz, u0, 1, color);
        vertex(b, pose, x + rx - ux, y + ry - uy, z + rz - uz, u1, 1, color);
        vertex(b, pose, x + rx + ux, y + ry + uy, z + rz + uz, u1, V_BEAM, color);
        vertex(b, pose, x - rx + ux, y - ry + uy, z - rz + uz, u0, V_BEAM, color);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    private static int color(int rgb, float alpha) {
        return (int) (Math.max(0, Math.min(1, alpha)) * 255) << 24 | (rgb & 0xFFFFFF);
    }
}
