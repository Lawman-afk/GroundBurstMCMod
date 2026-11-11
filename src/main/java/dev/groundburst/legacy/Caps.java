package dev.groundburst.legacy;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge's capability lookups ({@code level.getCapability(cap, pos, side)}, {@code stack.getCapability(cap)}), as the
 * port writes them on 1.20.1 ({@code Caps.get(level, cap, pos, side)}): Forge 1.20.1's own getCapability takes Forge's
 * capabilities, not these.
 */
public final class Caps {
    private Caps() {}

    public static <T, C> @Nullable T get(Level level, BlockCapability<T, C> capability, BlockPos pos, C context) {
        return capability.getCapability(level, pos, null, null, context);
    }

    public static <T, C> @Nullable T get(Level level, BlockCapability<T, C> capability, BlockPos pos, @Nullable BlockState state,
            @Nullable BlockEntity be, C context) {
        return capability.getCapability(level, pos, state, be, context);
    }

    public static <T, C> @Nullable T get(ItemStack stack, ItemCapability<T, C> capability, C context) {
        return capability.getCapability(stack, context);
    }

    public static <T> @Nullable T get(ItemStack stack, ItemCapability<T, @Nullable Void> capability) {
        return capability.getCapability(stack, null);
    }

    /** 26.x's item access asked for a capability (the 1.21.1 build's stand-in has its own lookup). */
    public static <T> @Nullable T get(dev.groundburst.port.transfer.access.ItemAccess access,
            ItemCapability<T, dev.groundburst.port.transfer.access.ItemAccess> capability) {
        return access.getCapability(capability);
    }

    /** NeoForge's word that a place's capabilities changed: nothing to forget here (nothing is kept between lookups). */
    public static void invalidate(Level level, BlockPos pos) {
    }

    public static void invalidate(Level level) {
    }
}
