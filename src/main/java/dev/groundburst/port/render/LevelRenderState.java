package dev.groundburst.port.render;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.ContextKey;

/**
 * Minecraft 26.x's state of the level for a frame, as the mod's effects use it: the camera, the sky, the clouds' colour,
 * and data a mod keeps for the frame (set while it is taken, read while it is drawn). In 1.21.1 the sky and clouds are
 * not drawn from it, so tinting them here shows nothing.
 */
public final class LevelRenderState {
    public final CameraRenderState cameraRenderState;
    public final SkyRenderState skyRenderState = new SkyRenderState();
    public int cloudColor = 0xFFFFFFFF;
    private final Map<ContextKey<?>, Object> data = new HashMap<>();

    public LevelRenderState(CameraRenderState camera) {
        this.cameraRenderState = camera;
    }

    public <T> void setRenderData(ContextKey<T> key, @Nullable T value) {
        if (value == null) {
            this.data.remove(key);
        } else {
            this.data.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T getRenderData(ContextKey<T> key) {
        return (T) this.data.get(key);
    }
}
