package dev.groundzero.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.groundzero.GroundZero;

/**
 * English names for everything we register, captured at registration time.
 * The language data generator writes these out, and a GameTest checks every block and item has one,
 * so a missing translation can never ship.
 */
public final class GZLang {
    private static final Map<String, String> ENTRIES = new LinkedHashMap<>();

    private GZLang() {}

    public static String add(String key, String english) {
        String previous = ENTRIES.putIfAbsent(key, english);
        if (previous != null && !previous.equals(english)) {
            throw new IllegalStateException("Conflicting English text for " + key + ": '" + previous + "' vs '" + english + "'");
        }
        return key;
    }

    /** Translation key for a config entry or section; also records its English label. */
    public static String config(String path, String english) {
        return add(GroundZero.MODID + ".configuration." + path, english);
    }

    public static boolean has(String key) {
        return ENTRIES.containsKey(key);
    }

    public static Map<String, String> entries() {
        return Collections.unmodifiableMap(ENTRIES);
    }
}
