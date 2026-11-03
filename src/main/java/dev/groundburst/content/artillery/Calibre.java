package dev.groundburst.content.artillery;

/**
 * A gun's bore (DESIGN.md §20.1): its shells' items ({@code <key>_shell_<type>}), how big they burst and how loud they
 * come in.
 *
 * @param key       its shells' id prefix
 * @param english   as its shells are named ("81 mm")
 * @param fragments HE: the fragments thrown
 * @param napalm    incendiary: how far round the napalm spreads
 * @param whistle   its incoming whistle's pitch (a bigger shell sings lower)
 */
public enum Calibre {
    MORTAR("mortar", "81 mm", 20, 6, 1.0f),
    HOWITZER("howitzer", "155 mm", 32, 9, 0.8f),
    ATOMIC("atomic", "280 mm", 48, 13, 0.62f);

    public final String key;
    public final String english;
    public final int fragments;
    public final float napalm;
    public final float whistle;

    Calibre(String key, String english, int fragments, float napalm, float whistle) {
        this.key = key;
        this.english = english;
        this.fragments = fragments;
        this.napalm = napalm;
        this.whistle = whistle;
    }

    /** The item id of its shell of {@code type}. */
    public String item(ShellType type) {
        return this.key + "_shell_" + type.key;
    }

    public static Calibre of(int ordinal) {
        Calibre[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }
}
