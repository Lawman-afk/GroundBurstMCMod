package dev.groundzero.content.assembly;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/**
 * A warhead's fuze (DESIGN.md §9.4, Phase 7), fitted at the Assembly Bench. A missile always sets its warhead off where
 * it comes down; a barometric fuze changes that to a burst in the air, its set height over the target.
 */
public enum Fuze {
    /** Goes off where the missile hits. */
    IMPACT("impact", "Impact", Setting.NONE),
    /** Bursts at its set height over the target on the way down. */
    BAROMETRIC("barometric", "Barometric", Setting.HEIGHT);

    /** The one setting a fuze has: its name, range and default. */
    public enum Setting {
        NONE("", 0, 0, 0),
        HEIGHT("Burst height (10-200)", 10, 200, 30);

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
