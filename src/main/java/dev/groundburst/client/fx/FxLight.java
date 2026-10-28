package dev.groundburst.client.fx;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import net.minecraft.client.renderer.state.level.SkyRenderState;

/**
 * How the sky lights smoke this frame (DESIGN.md §12.2): smoke, dust and cloud puffs are drawn unlit with a colour
 * worked out here, from a normal given by the shape of the cloud they belong to (out from a plume's axis, out from a
 * mushroom cap's core circle). Sunlight comes from the sun's real direction, warmed at sunrise and sunset; the shadowed
 * side gets the sky's light (nearly grey) as ambient light; everything dims with the daylight, so a cloud at night is a dark
 * shape lit only by its own glow.
 */
final class FxLight {
    /** The light of the frame being drawn (updated from the sky render state as each frame is extracted). */
    static FxLight current = new FxLight();

    final Vector3f sun = new Vector3f(0, 1, 0);
    final Vector3f sunColor = new Vector3f(1, 0.97f, 0.9f);
    final Vector3f ambient = new Vector3f(0.45f, 0.5f, 0.6f);

    /** Reads the sun angle, sky colour and sunset glow vanilla just extracted. */
    static void update(SkyRenderState sky) {
        FxLight l = new FxLight();
        // Vanilla draws the sun at +Y turned about X by the sun angle, then about Y by -90 degrees.
        float a = sky.sunAngle;
        l.sun.set(-(float) Math.sin(a), (float) Math.cos(a), 0).normalize();
        float up = l.sun.y;
        float day = smoothstep(-0.12f, 0.3f, up);
        Vector3fc skyColor = sky.skyColor != null ? sky.skyColor : new Vector3f(0.47f, 0.65f, 1f);
        Vector4fc glow = sky.sunriseAndSunsetColor;
        float dusk = glow != null ? Math.max(0, Math.min(1, glow.w())) : 0;
        l.sunColor.set(1, 0.96f, 0.88f);
        if (glow != null) {
            l.sunColor.lerp(new Vector3f(glow.x(), glow.y() * 0.85f, glow.z() * 0.7f), dusk * 0.8f);
        }
        l.sunColor.mul(day * 0.9f);
        // Ambient: the sky's brightness, almost grey (smoke scatters light many times over; with more of the sky's blue in it
        // the shaded half of a smoke cloud looked tinted blue), never quite black (starlight, the moon).
        float lum = 0.3f * skyColor.x() + 0.59f * skyColor.y() + 0.11f * skyColor.z();
        l.ambient.set(skyColor).lerp(new Vector3f(lum), 0.88f).mul(0.78f).add(0.06f, 0.06f, 0.06f);
        current = l;
    }

    /**
     * Light reaching a surface with normal (nx, ny, nz) (any length), times {@code shadow} (how much of the sky it
     * sees: 1 in the open, less in a cloud's underside). Written into {@code out} as an RGB multiplier.
     */
    Vector3f shade(float nx, float ny, float nz, float shadow, Vector3f out) {
        float n = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
        // Wrapped diffuse: a puff is soft, so light reaches a little past its terminator.
        float lambert = Math.max(0, ((nx * this.sun.x + ny * this.sun.y + nz * this.sun.z) / n + 0.3f) / 1.3f);
        float sky = 0.75f + 0.25f * ny / n;               // tops see more of the sky than undersides
        return out.set(this.ambient).mul(sky).add(this.sunColor.x * lambert, this.sunColor.y * lambert, this.sunColor.z * lambert).mul(shadow);
    }

    static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
