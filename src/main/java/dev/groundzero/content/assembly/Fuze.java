package dev.groundzero.content.assembly;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/**
 * A warhead's fuze (DESIGN.md §9.4, Phase 7): fitted at the Assembly Bench, it decides where a missile's warhead goes
 * off, with one setting (seconds, a range, a frequency or a burst height) set at the bench. It works only in a missile.
 */
public enum Fuze {
    /** Goes off where the missile hits. */
    IMPACT("impact", "Impact", Setting.NONE),
    /** Goes off a set time after launch, wherever the missile is. */
    TIMED("timed", "Timed", Setting.SECONDS),
    /** Goes off that close to the ground under the missile on its way down. */
    PROXIMITY("proximity", "Proximity", Setting.RANGE),
    /** A Remote Detonator on its frequency sets it off in flight (or it goes off where it hits). */
    REMOTE("remote", "Remote", Setting.FREQUENCY),
    /** Bursts at its set height over the target on the way down. */
    BAROMETRIC("barometric", "Barometric", Setting.HEIGHT);

    /** The one setting a fuze has: its name, range and default. */
    public enum Setting {
        NONE("", 0, 0, 0),
        SECONDS("Seconds (1-600)", 1, 600, 10),
        RANGE("Range (2-16)", 2, 16, 4),
        FREQUENCY("Frequency", 0, 9999, 0),
        HEIGHT("Height (10-200)", 10, 200, 30);

        public final String label;
        public final int min, max, fallback;

        Setting(String english, int min, int max, int fallback) {
            this.label = english.isEmpty() ? "" : GZLang.add(GroundZero.MODID + ".fuze.setting." + name().toLowerCase(java.util.Locale.ROOT), english);
            this.min = min;
            this.max = max;
            this.fallback = fallback;
        }

        public int clamp(int value) {
            return this == NONE ? 0 : Math.clamp(value, this.min, this.max);
        }
    }

    public final String id;
    public final String name;
    public final Setting setting;

    Fuze(String id, String english, Setting setting) {
        this.id = id;
        this.name = GZLang.add(GroundZero.MODID + ".fuze." + id, english);
        this.setting = setting;
    }

    public static Fuze byId(String id) {
        for (Fuze f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return IMPACT;
    }
}
