package dev.groundzero.registry;

import dev.groundzero.GroundZero;

/** The mod's creative tabs, in display order. Every item is assigned to exactly one at registration. */
public enum GZTab {
    RESOURCES("resources", "Ground Zero: Resources"),
    MACHINES("machines", "Ground Zero: Machines & Power"),
    ORDNANCE("ordnance", "Ground Zero: Ordnance"),
    NUCLEAR("nuclear", "Ground Zero: Nuclear & Special"),
    MISSILES("missiles", "Ground Zero: Missiles & Defence"),
    GEAR("gear", "Ground Zero: Guns & Gear"),
    SUPERWEAPONS("superweapons", "Ground Zero: Superweapons"),
    AFTERMATH("aftermath", "Ground Zero: Aftermath");

    private final String id;
    private final String translationKey;

    GZTab(String id, String english) {
        this.id = id;
        this.translationKey = GZLang.add("itemGroup." + GroundZero.MODID + "." + id, english);
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }
}
