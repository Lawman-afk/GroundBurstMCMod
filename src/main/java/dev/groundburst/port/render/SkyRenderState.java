package dev.groundburst.port.render;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/** Minecraft 26.x's sky for a frame, as the effects read it (its colour, the sun's angle, the sunrise glow). */
public final class SkyRenderState {
    public @Nullable Vector3fc skyColor;
    public float sunAngle;
    public @Nullable Vector4fc sunriseAndSunsetColor;
}
