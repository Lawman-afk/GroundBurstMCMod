package dev.groundburst.content.artillery;

/**
 * What an artillery shell holds (DESIGN.md §20.1). The 81 mm Mortar fires them all; the 155 mm Howitzer HE and
 * incendiary.
 *
 * @param key     its items' id ({@code <calibre>_shell_<key>})
 * @param english its short name ("HE", "Smoke", ...)
 * @param colour  its readout's colour, as its band is painted
 */
public enum ShellType {
    /** High explosive: a blast and fragments. */
    HE("he", "HE", 0xE0C060),
    /** Smoke: a screen of three smoke canisters' clouds across the line of fire. */
    SMOKE("smoke", "Smoke", 0xE8E8E0),
    /** Incendiary: a small burst and napalm fire over the ground round it. */
    INCENDIARY("incendiary", "Incendiary", 0xF07040),
    /** Chemical: two chlorine canisters' clouds. */
    CHEMICAL("chemical", "Chemical", 0x8CD05A),
    /** Cryo: a freeze round where it lands. */
    CRYO("cryo", "Cryo", 0x8AD8FF),
    /** EMP: a field round where it lands that shuts machines down. */
    EMP("emp", "EMP", 0xA890FF);

    public final String key;
    public final String english;
    public final int colour;

    ShellType(String key, String english, int colour) {
        this.key = key;
        this.english = english;
        this.colour = colour;
    }

    public static ShellType of(int ordinal) {
        ShellType[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }
}
