package dev.groundburst.content.construction;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The bunker doors' block set (DESIGN.md §22.3): the Pressure Door and the Bunker Hatch open by hand and by redstone, not
 * by a wind charge, sound as heavy steel when broken, and open and shut with their own sounds. Made when the first of
 * them is (the game registers its sounds before its blocks), and only once.
 */
public final class BunkerSet {
    private static BlockSetType type;

    private BunkerSet() {}

    public static synchronized BlockSetType get() {
        if (type == null) {
            type = BlockSetType.register(new BlockSetType(GroundBurst.MODID + ":bunker", true, SoundType.NETHERITE_BLOCK,
                    GBSounds.PRESSURE_DOOR_CLOSE.get(), GBSounds.PRESSURE_DOOR_OPEN.get(),
                    GBSounds.BUNKER_HATCH_CLOSE.get(), GBSounds.BUNKER_HATCH_OPEN.get(),
                    SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                    SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));
        }
        return type;
    }
}
