package dev.groundburst.legacy;

import org.jspecify.annotations.Nullable;

/** NeoForge's capability provider of a block entity, an item stack or an entity. */
@FunctionalInterface
public interface ICapabilityProvider<O, C, T> {
    @Nullable T getCapability(O object, C context);
}
