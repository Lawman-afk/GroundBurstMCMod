package dev.groundburst.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.groundburst.config.GBClientConfig;
import net.minecraft.client.Minecraft;
import dev.groundburst.port.render.SubmitNodeCollector;
import dev.groundburst.port.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A nuclear flash seen from afar (DESIGN.md §21.10). Glasstone and Dolan: a megaton dazzles to about 21 km on a clear day
 * and 85 km on a clear night, dark-adapted eyes about four times as far, and the light of the fireball lights the sky over
 * the horizon where the fireball itself cannot be seen. So, on top of the near flash ({@link Spectacle#start}):
 * <ul>
 * <li><b>Its reach.</b> A share of the flash ({@link #SHARE}) reaches far, falling as {@code (1 - d / reach)^1.4}, the
 * reach {@link #DAY_REACHES} of the blast's own by day and up to {@link #NIGHT_REACHES} more by night.</li>
 * <li><b>Blinding.</b> Looked at straight on (within 35 degrees, all of it within 10) and in sight, a strong flash holds
 * the white-out (up to {@link #HOLD} ticks) and clears slowly through a pale haze; nothing is hurt. The client's
 * {@code flashBlinding} turns it off, its flash strength all of it.</li>
 * <li><b>The sky.</b> For a few seconds the sky and fog are lit toward a hot white, seen or not (the light comes round
 * hills and through windows).</li>
 * <li><b>The glow over the horizon.</b> Beyond the loaded terrain, where the fireball is not drawn, a glow stands where
 * it is, white, then yellow, orange and gone: drawn just past the terrain so the hills in front of it hide it, on the far
 * glow pipeline (hazed, not lost in the fog).</li>
 * </ul>
 */
final class FarFlash {
    /** The far flash's reach in the blast's reaches by day, and how much more at full night. */
    static final float DAY_REACHES = 6, NIGHT_REACHES = 18;
    /** The far flash's share of a full flash, at the burst. */
    static final float SHARE = 0.85f;
    /** Looking within this angle of it blinds, all of it within the second (cosines of 35 and 10 degrees). */
    static final float BLIND_EDGE = 0.819f, BLIND_FULL = 0.985f;
    /** The longest the white-out is held when blinded, ticks; how much slower it then clears (times). */
    static final float HOLD = 50, SLOW = 3;
    /** How bright a blinding white-out is held. */
    static final float BLIND_LEVEL = 0.9f;
    /** How long the glow over the horizon and the lit sky last, ticks. */
    static final float GLOW_LIFE = 160, SKY_LIFE = 50;
    /** The glow's colours as it cools: the first light, yellow, orange, a dull red. */
    private static final int[] COOLING = {0xF2F4FF, 0xFFE8BC, 0xFFA45A, 0xB05A34};
    /** The sky lit by the flash: a hot white. */
    private static final Vector3f FLARE = new Vector3f(1f, 0.95f, 0.86f);
    /** The puff sheet's soft round cell (the spark's). */
    private static final int CELL = 12;

    private FarFlash() {}

    /** Whether a blast's effects are a nuclear one's (its preset has a mushroom cloud). */
    static boolean nuclear(FxPreset preset) {
        return preset.mushroom() > 0;
    }

    /** How dark it is where the camera is (0 day, 1 night), as the effects' light found it on the last frame. */
    static float darkness() {
        return Math.max(0, Math.min(1, 1 - FxLight.current.day));
    }

    /** The far flash's strength {@code reaches} of the blast's reach away (before line of sight and the client's strength). */
    static float share(double reaches) {
        double reach = DAY_REACHES + NIGHT_REACHES * darkness();
        return reaches >= reach ? 0 : (float) (SHARE * Math.pow(1 - reaches / reach, 1.4));
    }

    /** How much a flash of {@code strength} blinds, looked at with {@code facing} (the look's dot with the way to it), seen or not. */
    static float blind(float strength, float facing, boolean seen) {
        if (!seen || !GBClientConfig.FLASH_BLINDING.get()) {
            return 0;
        }
        float straight = FxLight.smoothstep(BLIND_EDGE, BLIND_FULL, facing);
        return straight * Math.max(0, Math.min(1, (strength - 0.12f) / 0.45f));
    }

    /** The white-out of a blast's flash at {@code t} ticks: held, longer when it blinds, then fading. */
    static float level(float flash, float blind, float flashTicks, double t) {
        if (flash <= 0 || t < 0) {
            return 0;
        }
        double hold = 1.5 + HOLD * blind;
        float held = Math.max(flash, BLIND_LEVEL * blind);
        if (t < hold) {
            return held;
        }
        return (float) (held * Math.exp(-(t - hold) / (flashTicks * 0.5 * (1 + SLOW * blind))));
    }

    /** How far the sky is lit now by every far flash (0 to 1). */
    static float skyFlare(Collection<BlastFx> blasts, double now) {
        float most = 0;
        for (BlastFx fx : blasts) {
            if (fx.skyFlash > 0) {
                double t = fx.age(now);
                if (t >= 0 && t < SKY_LIFE * 4) {
                    most = Math.max(most, (float) (fx.skyFlash * (t < 2 ? 1 : Math.exp(-(t - 2) / SKY_LIFE))));
                }
            }
        }
        return Math.min(1, most);
    }

    /** {@code color} lit toward the flash's hot white (at night too: the flash lights a night sky). */
    static Vector3f flared(float r, float g, float b, float amount) {
        return new Vector3f(r + (FLARE.x() - r) * amount, g + (FLARE.y() - g) * amount, b + (FLARE.z() - b) * amount);
    }

    /** The glow over the horizon for every nuclear blast beyond the loaded terrain whose glow is still going. */
    static void submit(Collection<BlastFx> blasts, double now, Vec3 camera, SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        double terrain = mc.options.getEffectiveRenderDistance() * 16.0;
        List<float[]> quads = new ArrayList<>();
        for (BlastFx fx : blasts) {
            if (fx.farShare <= 0 || fx.distance < terrain * 0.8) {
                continue;                                  // near enough that its own fireball is drawn
            }
            double t = fx.age(now);
            if (t < 0 || t > GLOW_LIFE) {
                continue;
            }
            Vec3 at = fx.fireballCentre(t);
            Vec3 way = at.subtract(camera);
            double far = way.length();
            if (far < 1) {
                continue;
            }
            Vec3 dir = way.scale(1 / far);
            // Stood just past the loaded terrain, the size it would be seen at: what is drawn in front of it hides it.
            double place = Math.min(far, terrain * 1.08);
            double angle = Math.max(0.07, 1.7 * fx.fireballRadius() / far);
            // A light against the sky by day too: its strength eased up (a fireball's light far outshines the sky).
            float alpha = (float) (Math.pow(fx.farShare, 0.6) * (t < 6 ? 1 : Math.exp(-(t - 6) / 45)));
            if (alpha < 0.01f) {
                continue;
            }
            int colour = cooling(t / GLOW_LIFE);
            Vec3 c = dir.scale(place);
            Vec3 right = dir.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1e-6) {
                right = new Vec3(1, 0, 0);
            }
            right = right.normalize();
            Vec3 up = right.cross(dir).normalize();
            // A wide faint halo, a brighter middle and a white-hot heart while it is new.
            float[][] layers = {{1f, 0.35f}, {0.5f, 0.6f}, {0.2f, t < 30 ? 1f : 0.5f}};
            for (float[] layer : layers) {
                double r = place * angle * layer[0] * 2.4;
                int argb = ARGB.color((int) (Math.min(1, alpha * layer[1]) * 255), layer[0] < 0.3f && t < 20 ? 0xFFFFFF : colour);
                quads.add(new float[] {(float) c.x, (float) c.y, (float) c.z, (float) (right.x * r), (float) (right.y * r), (float) (right.z * r),
                        (float) (up.x * r), (float) (up.y * r), (float) (up.z * r), Float.intBitsToFloat(argb), CELL});
            }
        }
        if (!quads.isEmpty()) {
            FxRenderTypes.submitNow(collector, poseStack, FxRenderTypes.farGlow(Puffs.SHEET), (pose, buffer) -> Puffs.quads(buffer, pose, quads));
        }
    }

    /** The glow's colour {@code k} of the way through its life (0 to 1). */
    private static int cooling(double k) {
        double x = Math.max(0, Math.min(0.999, k)) * (COOLING.length - 1);
        int i = (int) x;
        return ARGB.srgbLerp((float) (x - i), COOLING[i] | 0xFF000000, COOLING[i + 1] | 0xFF000000) & 0xFFFFFF;
    }
}
