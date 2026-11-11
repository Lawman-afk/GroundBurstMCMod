package dev.groundburst.legacy;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Minecraft 1.21's tooltip context (1.20.1 hands a tooltip the level, or for a block's item what the block is in): the
 * port gives an item's or block's appendHoverText one of these made from what 1.20.1 hands it.
 */
public final class TooltipContext {
    public static final TooltipContext EMPTY = new TooltipContext(null);

    private final @Nullable BlockGetter getter;

    private TooltipContext(@Nullable BlockGetter getter) {
        this.getter = getter;
    }

    public static TooltipContext of(@Nullable BlockGetter getter) {
        return getter == null ? EMPTY : new TooltipContext(getter);
    }

    public @Nullable Level level() {
        return this.getter instanceof Level level ? level : null;
    }

    public HolderLookup.Provider registries() {
        Level level = level();
        return level != null ? level.registryAccess() : Regs.access();
    }

    public float tickRate() {
        return 20f;
    }
}
