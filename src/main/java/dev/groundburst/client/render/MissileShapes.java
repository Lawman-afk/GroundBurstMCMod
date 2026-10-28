package dev.groundburst.client.render;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.groundburst.GroundBurst;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Each missile's shape (assets/groundburst/missiles.json, written by tools/assets/missiles.py with its models): where its
 * tail is in its item model, how long it is, where its stages part and its engines burn, its assembly sections, and its
 * radius by height. Heights are the world's pixels up from its tail (the item models are drawn four times over).
 */
public final class MissileShapes {
    /**
     * @param cls     its class ("S" to "XXL")
     * @param tail    the item model's y (its own pixels) at the missile's tail
     * @param length  from tail to tip
     * @param head    the warhead's foot
     * @param stages  each stage's top, bottom up (the last: the tip)
     * @param engines each stage's exhaust: the bottom of its bell
     * @param parts   each assembly section's top, bottom up (the last: the tip)
     * @param profile its round body: {from, to, radius} by height
     */
    public record Shape(String cls, float tail, float length, float head, float[] stages, float[] engines, float[] parts, float[][] profile) {
        /** How far (blocks) the item model is lifted to stand its tail where it is drawn (it is drawn about its middle). */
        public float lift() {
            return (8 - this.tail) / 16f;
        }

        /** Blocks up its axis from the tail to where the exhaust of the stage now lowest leaves ({@code dropped} gone). */
        public float engine(int dropped) {
            return this.engines.length == 0 ? 0 : this.engines[Math.clamp(dropped, 0, this.engines.length - 1)] / 16f;
        }

        /** Blocks up its axis from the tail to its tip. */
        public float nose() {
            return this.length / 16f;
        }

        /** The middle of its {@code k}th stage (1: the first), the world's pixels up from its tail. */
        public float stageMiddle(int k) {
            int i = Math.clamp(k - 1, 0, this.stages.length - 1);
            float bottom = i == 0 ? 0 : this.stages[i - 1];
            return (bottom + this.stages[i]) / 2;
        }

        /** Its radius (the world's pixels) at a height up from its tail (0 above its body). */
        public float radiusAt(float y) {
            float r = 0;
            for (float[] p : this.profile) {
                if (y >= p[0] && y < p[1]) {
                    r = Math.max(r, p[2]);
                }
            }
            return r;
        }

        /** Its skin's radius at a height, or where its round body is nearest within 8 px (a joint or a gap). */
        public float skinRadius(float y) {
            for (int k = 0; k <= 8; k++) {
                float r = Math.max(radiusAt(y - k), radiusAt(y + k));
                if (r > 0) {
                    return r;
                }
            }
            return 4;
        }

        /** The height up its axis (the world's pixels from its tail) where an arm holds it. */
        public float holdHeight(dev.groundburst.content.launch.MissileCarrier.Hold hold) {
            return switch (hold) {
                case UNDER_WARHEAD -> this.head - 8;
                case LOW -> this.stages.length >= 3 ? stageMiddle(2) : this.stages.length == 2 ? stageMiddle(1)
                        : Math.max(0.4f * this.head, (this.parts.length > 0 ? this.parts[0] : 0) + 4);
            };
        }
    }

    private static final Identifier FILE = GroundBurst.id("missiles.json");
    private static volatile Map<String, Shape> table = Map.of();

    private MissileShapes() {}

    /** The shape of the missile carrying {@code warhead} (its block id's path); null for none (the plain missile). */
    public static @Nullable Shape get(String warhead) {
        return table.get(warhead);
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    /** Reads the table with the client's resources. */
    public static final class Loader extends SimplePreparableReloadListener<Map<String, Shape>> {
        @Override
        protected Map<String, Shape> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Shape> out = new HashMap<>();
            var resource = manager.getResource(FILE);
            if (resource.isEmpty()) {
                GroundBurst.LOGGER.error("Missiles: {} is missing", FILE);
                return out;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    JsonObject m = e.getValue().getAsJsonObject();
                    JsonArray prof = m.getAsJsonArray("profile");
                    float[][] profile = new float[prof.size()][];
                    for (int i = 0; i < profile.length; i++) {
                        profile[i] = floats(prof.get(i).getAsJsonArray());
                    }
                    out.put(e.getKey(), new Shape(m.get("class").getAsString(), m.get("tail").getAsFloat(), m.get("length").getAsFloat(),
                            m.get("head").getAsFloat(), floats(m.getAsJsonArray("stages")), floats(m.getAsJsonArray("engines")),
                            floats(m.getAsJsonArray("parts")), profile));
                }
            } catch (Exception ex) {
                GroundBurst.LOGGER.error("Missiles: bad {}", FILE, ex);
            }
            return out;
        }

        @Override
        protected void apply(Map<String, Shape> prepared, ResourceManager manager, ProfilerFiller profiler) {
            table = Map.copyOf(prepared);
        }
    }
}
