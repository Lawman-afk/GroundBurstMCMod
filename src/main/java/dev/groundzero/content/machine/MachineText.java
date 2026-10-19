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

    // Side configuration tab
    public static final String SIDES_TITLE = GZLang.add(GroundZero.MODID + ".gui.sides", "Configuration");
    public static final String SIDES_TAB = GZLang.add(GroundZero.MODID + ".gui.sides.tab", "Side configuration");
    public static final String CHANNEL_ITEMS = GZLang.add(GroundZero.MODID + ".gui.sides.items", "Items");
    public static final String CHANNEL_FUEL = GZLang.add(GroundZero.MODID + ".gui.sides.fuel", "Fuel");
    public static final String CHANNEL_ENERGY = GZLang.add(GroundZero.MODID + ".gui.sides.energy", "Energy");
    public static final String ENERGY_ON = GZLang.add(GroundZero.MODID + ".gui.sides.energy_on", "Energy connected");
    public static final String ENERGY_OFF = GZLang.add(GroundZero.MODID + ".gui.sides.energy_off", "Energy disconnected");
    public static final String AUTO_EJECT = GZLang.add(GroundZero.MODID + ".gui.sides.auto_eject", "Auto-eject");
    public static final String AUTO_EJECT_TIP = GZLang.add(GroundZero.MODID + ".gui.sides.auto_eject.tip", "Push finished products out of every Output face");
    public static final String HINT_CYCLE = GZLang.add(GroundZero.MODID + ".gui.sides.hint_cycle", "Left-click: next, right-click: previous");
    public static final String HINT_TOGGLE = GZLang.add(GroundZero.MODID + ".gui.sides.hint_toggle", "Click to toggle");
    public static final String FACE_MODE = GZLang.add(GroundZero.MODID + ".gui.sides.face", "%s: %s");

    // Recipe viewer (JEI) texts
    public static final String RECIPE_TIME_ENERGY = GZLang.add(GroundZero.MODID + ".recipe.time_energy", "%s s · %s FE/t");
    public static final String RECIPE_TOTAL_ENERGY = GZLang.add(GroundZero.MODID + ".recipe.total_energy", "%s FE in total");
    public static final String RECIPE_CHANCE = GZLang.add(GroundZero.MODID + ".recipe.chance", "%s%% chance");
    public static final String RECIPE_NOT_CONSUMED = GZLang.add(GroundZero.MODID + ".recipe.not_consumed", "Die: not consumed");
    public static final String INFO_DIE = GZLang.add(GroundZero.MODID + ".info.die",
            "Goes in the Industrial Press die slot and picks what the press makes. Dies are never used up.");
    public static final String INFO_SIDES = GZLang.add(GroundZero.MODID + ".info.sides",
            "Every machine has a side-configuration tab (the wrench on the right of its screen): choose which faces accept items, give out products and connect to power, and switch on auto-eject.");

    private MachineText() {}

    /** Loads this class and the enums whose constants register their own keys. */
    static void init() {
        MachineStatus.values();
        RedstoneMode.values();
        SideConfig.Face.values();
        SideConfig.ItemMode.values();
    }
}
