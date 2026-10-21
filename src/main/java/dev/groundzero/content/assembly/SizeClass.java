package dev.groundzero.content.assembly;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/**
 * A payload's size class (DESIGN.md §9.1): S tactical, M medium, L heavy (XL strategic comes with the Phase 8 weapons).
 * A payload core only goes into a casing of its own class.
 */
public enum SizeClass {
    S("Light"), M("Medium"), L("Heavy");

    /** "Class S (Light)": for tooltips and the bench readout. */
    public final String label;

    SizeClass(String english) {
        this.label = GZLang.add(GroundZero.MODID + ".size_class." + name().toLowerCase(java.util.Locale.ROOT), "Class " + name() + " (" + english + ")");
    }
}
