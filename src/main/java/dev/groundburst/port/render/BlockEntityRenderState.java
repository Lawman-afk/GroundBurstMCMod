package dev.groundburst.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Minecraft 26.x's state of a block entity for a frame (taken by {@link StateBlockEntityRenderer}). */
public class BlockEntityRenderState {
    public BlockPos blockPos = BlockPos.ZERO;
    public BlockState blockState = Blocks.AIR.defaultBlockState();
    public @Nullable BlockEntityType<?> blockEntityType;
    public int lightCoords = 0xF000F0;
    public ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress;
}
