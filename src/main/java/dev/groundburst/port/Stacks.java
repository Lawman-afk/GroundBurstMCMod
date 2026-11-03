package dev.groundburst.port;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

/** Minecraft 26.x's item stack components that 1.21.1 keeps on the item instead. */
public final class Stacks {
    private Stacks() {}

    /** The colour a dye stack dyes (26.x's dye component). */
    public static @Nullable DyeColor dye(ItemStack stack) {
        return stack.getItem() instanceof DyeItem dye ? dye.getDyeColor() : null;
    }
}
