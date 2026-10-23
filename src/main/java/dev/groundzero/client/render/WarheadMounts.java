package dev.groundzero.client.render;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.groundzero.GroundZero;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Where a missile carries its warhead and how big (assets/groundzero/missile_warheads.json, written by
 * tools/assets/launch.py from the warheads' models): for each airframe the height of its body's top, and for each warhead
 * it may carry the scale its model is drawn at, so its mounting ring sits just inside the body's sides.
 */
public final class WarheadMounts {
    /**
     * @param top    the height of the missile body's top, item pixels (the warhead's foot)
     * @param scale  item pixels a pixel of the warhead's model is drawn
     * @param half   how far the warhead reaches out from its axis, its own pixels
     * @param height its height, its own pixels
     */
    public record Mount(float top, float scale, float half, float height) {}

    private static final Identifier FILE = GroundZero.id("missile_warheads.json");
    private static volatile Map<String, Map<String, Mount>> table = Map.of();

    private WarheadMounts() {}

    /** How {@code warhead} (its block id's path) stands on {@code airframe} (null: not one it carries). */
    public static @Nullable Mount get(String airframe, String warhead) {
        Map<String, Mount> mounts = table.get(airframe);
        return mounts == null ? null : mounts.get(warhead);
    }

    /** Reads the table with the client's resources (and forgets the warheads' models, which may have changed). */
    public static final class Loader extends SimplePreparableReloadListener<Map<String, Map<String, Mount>>> {
        @Override
        protected Map<String, Map<String, Mount>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Map<String, Mount>> out = new HashMap<>();
            var resource = manager.getResource(FILE);
            if (resource.isEmpty()) {
                GroundZero.LOGGER.error("Missile warheads: {} is missing", FILE);
                return out;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> airframe : json.entrySet()) {
                    JsonObject a = airframe.getValue().getAsJsonObject();
                    float top = a.get("top").getAsFloat();
                    Map<String, Mount> mounts = new HashMap<>();
                    for (Map.Entry<String, JsonElement> w : a.getAsJsonObject("warheads").entrySet()) {
                        JsonObject m = w.getValue().getAsJsonObject();
                        mounts.put(w.getKey(), new Mount(top, m.get("scale").getAsFloat(), m.get("half").getAsFloat(), m.get("height").getAsFloat()));
                    }
                    out.put(airframe.getKey(), Map.copyOf(mounts));
                }
            } catch (Exception e) {
                GroundZero.LOGGER.error("Missile warheads: bad {}", FILE, e);
            }
            return out;
        }

        @Override
        protected void apply(Map<String, Map<String, Mount>> prepared, ResourceManager manager, ProfilerFiller profiler) {
            table = Map.copyOf(prepared);
            WarheadNose.forget();
        }
    }
}
