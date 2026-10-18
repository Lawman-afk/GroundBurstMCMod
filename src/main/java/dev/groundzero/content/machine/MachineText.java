package dev.groundzero.content.machine;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/**
 * Translation keys shared by the machine GUIs. Kept out of the client screen class so the keys are registered with
 * {@link GZLang} on both sides (datagen loads this through {@link GZMachines#init()}).
 */
public final class MachineText {
    public static final String ENERGY = GZLang.add(GroundZero.MODID + ".gui.energy", "Energy: %s / %s FE");
    public static final String PROGRESS = GZLang.add(GroundZero.MODID + ".gui.progress", "Progress: %s%%");
    public static final String BURN = GZLang.add(GroundZero.MODID + ".gui.burn", "Burning: %s s left");
    public static final String OUTPUT = GZLang.add(GroundZero.MODID + ".gui.output", "OUT %s FE/t");
    public static final String STORED = GZLang.add(GroundZero.MODID + ".gui.stored", "BUF %s%%");
    public static final String REDSTONE = GZLang.add(GroundZero.MODID + ".gui.redstone", "Redstone: %s");

    private MachineText() {}

    /** Loads this class and the enums whose constants register their own keys. */
    static void init() {
        MachineStatus.values();
        RedstoneMode.values();
    }
}
