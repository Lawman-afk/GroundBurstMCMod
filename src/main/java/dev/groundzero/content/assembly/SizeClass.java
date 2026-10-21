package dev.groundzero.content.assembly;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/**
 * A payload's size class (DESIGN.md §9.1): S tactical, M medium, L heavy; XL strategic (Phase 8: so far only the
 * Strategic Missile Body, the one that carries nuclear warheads). A payload core only goes into a casing of its own class.
 */
public enum SizeClass {
    S("Light"), M("Medium"), L("Heavy"), XL("Strategic");

    /** "Class S (Light)": for tooltips and the bench readout. */
    public final String label;

    SizeClass(String english) {
        this.label = GZLang.add(GroundZero.MODID + ".size_class." + name().toLowerCase(java.util.Locale.ROOT), "Class " + name() + " (" + english + ")");
    }
}
