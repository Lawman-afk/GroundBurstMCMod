package dev.groundburst.content.assembly;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;

/**
 * A payload's size class (DESIGN.md §9.1): S tactical, M medium, L heavy; XL strategic (Phase 8: the Strategic
 * Missile Body, the one that carries nuclear warheads); XXL intercontinental (slice 8c: no body or casing of its own,
 * only the whole weapons the Strategic Assembly Hall builds). A payload core only goes into a casing of its own class.
 */
public enum SizeClass {
    S("Light"), M("Medium"), L("Heavy"), XL("Strategic"), XXL("Intercontinental");

    /** "Class S (Light)": for tooltips and the bench readout. */
    public final String label;

    SizeClass(String english) {
        this.label = GBLang.add(GroundBurst.MODID + ".size_class." + name().toLowerCase(java.util.Locale.ROOT), "Class " + name() + " (" + english + ")");
    }
}
