package dev.groundburst.port;

import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.util.ARGB;

/** The sky's colours: 26.1.2 keeps them as packed ARGB colours, 26.3 as vectors (null where there is none). */
public final class SkyColors {
    private SkyColors() {}

    /** One of the Spectacle's tints: a colour (0..1 each) changed by {@code amount}. */
    @FunctionalInterface
    public interface Tint {
        Vector3f apply(float r, float g, float b, float amount);
    }

    public static Vector3f vector(int argb) {
        return new Vector3f(ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb));
    }

    /** Red, green, blue and (as w) how strong it is: a sunrise's glow is clear when there is none. */
    public static Vector4f vector4(int argb) {
        return new Vector4f(ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb), ARGB.alphaFloat(argb));
    }

    /** {@code argb} tinted by {@code tint}, its alpha kept. */
    public static int tinted(int argb, Tint tint, float amount) {
        Vector3f c = tint.apply(ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb), amount);
        return ARGB.colorFromFloat(ARGB.alphaFloat(argb), clamp(c.x), clamp(c.y), clamp(c.z));
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
