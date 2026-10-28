package dev.groundburst.config;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.groundburst.registry.GBLang;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Local (per-installation) settings. Worldgen toggles live here because features are placed while chunks
 * generate, before any world-synced config is available. Read by {@code ConfigToggleFilter}.
 */
public final class GBCommonConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final Map<String, ModConfigSpec.BooleanValue> TOGGLES = new LinkedHashMap<>();

    static {
        B.translation(GBLang.config("worldgen", "World generation"))
                .comment("Turn individual resources off, e.g. when another mod already provides that ore. Affects newly generated chunks only.")
                .push("worldgen");
        toggle("uranium", "Uranium ore");
        toggle("lithium", "Lithium ore");
        toggle("sulfur", "Sulfur ore");
        toggle("saltpeter", "Saltpeter ore");
        toggle("tungsten", "Tungsten ore");
        toggle("lead", "Lead ore");
        toggle("rare_earth", "Rare-earth ore");
        toggle("exotic_geodes", "Exotic matter geodes (End)");
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private GBCommonConfig() {}

    private static void toggle(String key, String english) {
        TOGGLES.put(key, B.comment("Generate " + english.toLowerCase(java.util.Locale.ROOT) + ".")
                .translation(GBLang.config("worldgen." + key, english))
                .define(key, true));
    }

    /** Whether the named resource generates. Unknown keys are treated as enabled. */
    public static boolean isEnabled(String key) {
        ModConfigSpec.BooleanValue value = TOGGLES.get(key);
        return value == null || !SPEC.isLoaded() || value.getAsBoolean();
    }
}
