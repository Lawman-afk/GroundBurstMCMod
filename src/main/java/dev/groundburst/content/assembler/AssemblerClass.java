package dev.groundburst.content.assembler;

import dev.groundburst.content.assembly.SizeClass;

/**
 * The four missile assemblers (DESIGN.md §10.4), one for each class of missile below the Strategic Assembly Hall's:
 * which warheads each takes, how long it works and how much power it draws, and how its model shows the missile.
 */
public enum AssemblerClass {
    /** An A-frame hoist over a concrete stand: the missile laid on two cradles in three sections. */
    LIGHT(SizeClass.S, "light_missile_assembler", "Light Missile Assembler", 20, 200, 40_000, true),
    /** A slewing jib crane beside a bay: sections swung over from its rack. */
    MEDIUM(SizeClass.M, "medium_missile_assembler", "Medium Missile Assembler", 40, 300, 80_000, true),
    /** A portal gantry crane running on rails the length of its cradles. */
    HEAVY(SizeClass.L, "heavy_missile_assembler", "Heavy Missile Assembler", 80, 400, 160_000, true),
    /** An integration tower: the missile stacked upright, stage on stage, its work platforms folding in round it. */
    STRATEGIC(SizeClass.XL, "strategic_missile_assembler", "Strategic Missile Assembler", 160, 600, 320_000, false);

    /** The class of missile it builds: its warheads' class and its body's. */
    public final SizeClass size;
    /** The controller's id (and its model's, its menu title's). */
    public final String id;
    public final String english;
    /** Power it draws while it works (FE a tick), how long a missile takes (ticks), and how much power it holds. */
    public final int energyPerTick, ticks, capacity;
    /** Whether the missile lies on cradles (false: it stands on the integration stand). */
    public final boolean lying;

    AssemblerClass(SizeClass size, String id, String english, int energyPerTick, int ticks, int capacity, boolean lying) {
        this.size = size;
        this.id = id;
        this.english = english;
        this.energyPerTick = energyPerTick;
        this.ticks = ticks;
        this.capacity = capacity;
        this.lying = lying;
    }

    /** The assembler that builds missiles of a class (null: none does; the hall builds intercontinental ones). */
    public static @org.jspecify.annotations.Nullable AssemblerClass of(SizeClass size) {
        for (AssemblerClass c : values()) {
            if (c.size == size) {
                return c;
            }
        }
        return null;
    }
}
