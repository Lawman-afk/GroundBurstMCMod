package dev.groundburst.legacy;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** NeoForge's block capability provider. */
@FunctionalInterface
public interface IBlockCapabilityProvider<T, C> {
    @Nullable T getCapability(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, C context);
}
