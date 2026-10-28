package dev.groundburst.content.multiblock;

import org.jspecify.annotations.Nullable;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A part block that also exposes its formed structure's inventory and tank to automation. */
public class PortBlock extends MultiblockPartBlock implements EntityBlock {
    public PortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortBlockEntity(pos, state);
    }
}
