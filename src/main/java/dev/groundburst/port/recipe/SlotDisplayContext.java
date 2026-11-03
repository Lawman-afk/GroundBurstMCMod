package dev.groundburst.port.recipe;

import dev.groundburst.port.ContextMap;
import net.minecraft.world.level.Level;

/** Minecraft 26.x's slot display context: 1.21.1's ingredients resolve without one. */
public final class SlotDisplayContext {
    private SlotDisplayContext() {}

    public static ContextMap fromLevel(Level level) {
        return ContextMap.EMPTY;
    }
}
