package dev.groundzero.content.machine;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/** How a machine reacts to redstone. Cycled with the button in every machine GUI. */
public enum RedstoneMode {
    IGNORE("Ignore redstone"),
    HIGH("Run with redstone signal"),
    LOW("Run without redstone signal");

    private final String translationKey;

    RedstoneMode(String english) {
        this.translationKey = GZLang.add(GroundZero.MODID + ".redstone_mode." + name().toLowerCase(java.util.Locale.ROOT), english);
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }

    public RedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RedstoneMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : IGNORE;
    }
}
