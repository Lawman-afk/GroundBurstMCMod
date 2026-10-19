package dev.groundzero.content.machine;

import java.util.Arrays;
import java.util.Locale;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Per-face automation settings of a machine (DESIGN.md §8 "side configuration"). Faces are relative to the machine's
 * front, so the config survives rotating the block. Each face has an item mode (which slots pipes and hoppers may
 * reach through it) and an energy switch; auto-eject pushes outputs out of every output face.
 */
public final class SideConfig {
    /** A face relative to the machine's front. */
    public enum Face {
        FRONT("Front"), BACK("Back"), LEFT("Left"), RIGHT("Right"), TOP("Top"), BOTTOM("Bottom");

        private final String translationKey;

        Face(String english) {
            this.translationKey = GZLang.add(GroundZero.MODID + ".side." + name().toLowerCase(Locale.ROOT), english);
        }

        public String translationKey() {
            return translationKey;
        }

        /** The world direction of this face for a machine facing {@code facing} (left/right as seen from the front). */
        public Direction toDirection(Direction facing) {
            return switch (this) {
                case FRONT -> facing;
                case BACK -> facing.getOpposite();
                case LEFT -> facing.getClockWise();
                case RIGHT -> facing.getCounterClockWise();
                case TOP -> Direction.UP;
                case BOTTOM -> Direction.DOWN;
            };
        }

        public static Face of(Direction side, Direction facing) {
            for (Face f : values()) {
                if (f.toDirection(facing) == side) {
                    return f;
                }
            }
            return FRONT;
        }
    }

    /** What pipes, hoppers and auto-eject may do with items through a face. */
    public enum ItemMode {
        NONE("None", 0x00000000), INPUT("Input", 0xB03C78FF), OUTPUT("Output", 0xB0FF9A2E), BOTH("Input & Output", 0xB04CC85A);

        private final String translationKey;
        private final int color;

        ItemMode(String english, int color) {
            this.translationKey = GZLang.add(GroundZero.MODID + ".item_mode." + name().toLowerCase(Locale.ROOT), english);
            this.color = color;
        }

        public String translationKey() {
            return translationKey;
        }

        /** ARGB overlay colour in the configuration tab. */
        public int color() {
            return color;
        }

        public boolean canInsert() {
            return this == INPUT || this == BOTH;
        }

        public boolean canExtract() {
            return this == OUTPUT || this == BOTH;
        }

        public ItemMode cycle(boolean forward) {
            ItemMode[] v = values();
            return v[(ordinal() + (forward ? 1 : v.length - 1)) % v.length];
        }
    }

    private final ItemMode[] items = new ItemMode[6];
    private final boolean[] energy = new boolean[6];
    private boolean autoEject;

    public SideConfig() {
        Arrays.fill(this.items, ItemMode.BOTH);
        Arrays.fill(this.energy, true);
    }

    public ItemMode items(Face face) {
        return this.items[face.ordinal()];
    }

    public boolean energy(Face face) {
        return this.energy[face.ordinal()];
    }

    public boolean autoEject() {
        return this.autoEject;
    }

    public void cycleItems(Face face, boolean forward) {
        this.items[face.ordinal()] = this.items[face.ordinal()].cycle(forward);
    }

    public void toggleEnergy(Face face) {
        this.energy[face.ordinal()] = !this.energy[face.ordinal()];
    }

    public void toggleAutoEject() {
        this.autoEject = !this.autoEject;
    }

    // --- packing: two 16-bit menu data slots -------------------------------------------------------------------------
    /** Item modes, 2 bits per face. */
    public int packItems() {
        int v = 0;
        for (int i = 0; i < 6; i++) {
            v |= this.items[i].ordinal() << (i * 2);
        }
        return v;
    }

    /** Energy switches (bit per face) and auto-eject (bit 6). */
    public int packFlags() {
        int v = 0;
        for (int i = 0; i < 6; i++) {
            v |= (this.energy[i] ? 1 : 0) << i;
        }
        return v | (this.autoEject ? 1 << 6 : 0);
    }

    public void unpack(int items, int flags) {
        for (int i = 0; i < 6; i++) {
            this.items[i] = ItemMode.values()[(items >> (i * 2)) & 3];
            this.energy[i] = ((flags >> i) & 1) != 0;
        }
        this.autoEject = ((flags >> 6) & 1) != 0;
    }

    public void save(ValueOutput output) {
        output.putInt("items", packItems());
        output.putInt("flags", packFlags());
    }

    public void load(ValueInput input) {
        SideConfig defaults = new SideConfig();
        unpack(input.getIntOr("items", defaults.packItems()), input.getIntOr("flags", defaults.packFlags()));
    }
}
