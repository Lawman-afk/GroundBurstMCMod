package dev.groundburst.port;

/**
 * A missile's look (main names it in the item's custom model data, a string in 26.x) as 1.21.1's custom model data, a
 * number: the same number for the same name every time, and tools/backport/port.py works out the same numbers when it
 * writes the missile item's model overrides, one a look (checking no two looks share one). The numbers stay below 2^24,
 * so each is exact where 1.21.1's model overrides compare them as floats.
 */
public final class Looks {
    private Looks() {}

    public static int id(String look) {
        return Math.floorMod(look.hashCode(), 16_777_213) + 1;
    }
}
