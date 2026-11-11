package dev.groundburst.legacy;

import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * NeoForge's cached capability lookup at a place (the pipes' ends). On 1.20.1 it asks each time: the lookup is cheap
 * (two map reads, or Forge's capability of the block entity there) and so never goes stale.
 */
public final class BlockCapabilityCache<T, C> {
    private final BlockCapability<T, C> capability;
    private final ServerLevel level;
    private final BlockPos pos;
    private final C context;
    private final BooleanSupplier isValid;

    private BlockCapabilityCache(BlockCapability<T, C> capability, ServerLevel level, BlockPos pos, C context, BooleanSupplier isValid) {
        this.capability = capability;
        this.level = level;
        this.pos = pos.immutable();
        this.context = context;
        this.isValid = isValid;
    }

    public static <T, C> BlockCapabilityCache<T, C> create(BlockCapability<T, C> capability, ServerLevel level, BlockPos pos, C context) {
        return new BlockCapabilityCache<>(capability, level, pos, context, () -> true);
    }

    public static <T, C> BlockCapabilityCache<T, C> create(BlockCapability<T, C> capability, ServerLevel level, BlockPos pos, C context,
            BooleanSupplier isValid, Runnable invalidationListener) {
        return new BlockCapabilityCache<>(capability, level, pos, context, isValid);
    }

    public ServerLevel level() {
        return this.level;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public C context() {
        return this.context;
    }

    public @Nullable T getCapability() {
        if (!this.isValid.getAsBoolean() || !this.level.isLoaded(this.pos)) {
            return null;
        }
        return this.capability.getCapability(this.level, this.pos, null, null, this.context);
    }
}
