package dev.groundburst.content.machine;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;

/** The reason a machine is (or is not) working, shown as a status lamp + text in its GUI. */
public enum MachineStatus {
    IDLE("Idle"),
    RUNNING("Running"),
    NO_POWER("Insufficient power"),
    NO_RECIPE("No valid input"),
    OUTPUT_FULL("Output full"),
    NO_FUEL("No fuel"),
    BUFFER_FULL("Energy buffer full"),
    REDSTONE("Halted by redstone"),
    EMP("Disabled by EMP"),
    INCOMPLETE("Structure incomplete"),
    NEED_FLUID("Needs more fluid"),
    NO_WATER("No water source below"),
    SCRAM("SCRAM: the core overheated"),
    OVERHEATING("Core overheating"),
    CRITICAL("CORE CRITICAL: SCRAM now"),
    CHARGING("Charging up"),
    NO_CORE("No singularity core");

    private final String translationKey;

    MachineStatus(String english) {
        this.translationKey = GBLang.add(GroundBurst.MODID + ".status." + name().toLowerCase(java.util.Locale.ROOT), english);
    }

    public String translationKey() {
        return translationKey;
    }

    /** Lamp colour in the GUI: green running, amber idle/waiting, red blocked. */
    public int lampColor() {
        return switch (this) {
            case RUNNING -> 0xFF4AFF6A;
            case IDLE, NO_RECIPE, BUFFER_FULL, NEED_FLUID, OVERHEATING, CHARGING, NO_CORE -> 0xFFFFB020;
            default -> 0xFFFF3A2A;
        };
    }

    public static MachineStatus byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : IDLE;
    }
}
