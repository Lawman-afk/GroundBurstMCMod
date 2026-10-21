package dev.groundzero.content.assembly;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/**
 * A warhead's fuze (DESIGN.md §9.4, Phase 7): fitted at the Assembly Bench, it decides what sets an armed warhead off.
 * Every armed warhead also has a contact backup (dropped or shot, it goes off) and goes off on redstone; the fuze adds
 * its own trigger, with one setting: a frequency, seconds, a range or a burst height.
 */
public enum Fuze {
    /** Nothing more than contact and redstone. Mechanical: an EMP does not make it safe. */
    IMPACT("impact", "Impact", Setting.NONE),
    /** Redstone starts a countdown (and, delivered by a missile, it counts from launch). */
    TIMED("timed", "Timed", Setting.SECONDS),
    /** Anything living within its range sets it off (a missile's bursts that far above the ground). */
    PROXIMITY("proximity", "Proximity", Setting.RANGE),
    /** A Remote Detonator on its frequency sets it off. */
    REMOTE("remote", "Remote", Setting.FREQUENCY),
    /** Placed, it works as an impact fuze; delivered by a missile, it bursts at its set height. */
    BAROMETRIC("barometric", "Barometric", Setting.HEIGHT);

    /** The one setting a fuze has: its name, range and default. */
    public enum Setting {
        NONE("", 0, 0, 0),
        SECONDS("Timer, seconds (1-600)", 1, 600, 10),
        RANGE("Range, blocks (2-16)", 2, 16, 4),
        FREQUENCY("Frequency (0-9999)", 0, 9999, 0),
        HEIGHT("Burst height, blocks (10-200)", 10, 200, 30);

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

    /** Electronic fuzes are made safe by an EMP; the impact fuze is mechanical. */
    public boolean electronic() {
        return this != IMPACT;
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
