package dev.groundburst.registry;

import dev.groundburst.GroundBurst;

/** The mod's creative tabs, in display order. Every item is assigned to exactly one at registration. */
public enum GBTab {
    RESOURCES("resources", "Ground Burst: Resources"),
    MACHINES("machines", "Ground Burst: Machines & Power"),
    ORDNANCE("ordnance", "Ground Burst: Ordnance"),
    NUCLEAR("nuclear", "Ground Burst: Nuclear & Special"),
    MISSILES("missiles", "Ground Burst: Missiles & Defence"),
    GEAR("gear", "Ground Burst: Guns & Gear"),
    SUPERWEAPONS("superweapons", "Ground Burst: Superweapons"),
    CONSTRUCTION("construction", "Ground Burst: Construction"),
    AFTERMATH("aftermath", "Ground Burst: Aftermath");

    private final String id;
    private final String translationKey;

    GBTab(String id, String english) {
        this.id = id;
        this.translationKey = GBLang.add("itemGroup." + GroundBurst.MODID + "." + id, english);
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }
}
