package dev.groundzero.content.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * An item pipe (DESIGN.md §6.5), in three tiers. Sides, the Wrench and pulling work as for every {@link PipeBlock}:
 * a side set to pull draws items out of the chest or machine it touches (a machine only gives from its output slots,
 * as its side configuration allows), and the run hands them straight to the inventories on its normal sides. Pipes
 * hold nothing, so breaking one never drops items. Runs of connected item pipe form an {@link ItemPipeNetwork}.
 */
public class ItemPipeBlock extends PipeBlock {
    private final ItemPipeTier tier;

    public ItemPipeBlock(Properties properties, ItemPipeTier tier) {
        super(properties);
        this.tier = tier;
    }

    public ItemPipeTier tier() {
        return this.tier;
    }

    @Override
    public boolean isSameKind(BlockState state) {
        return state.getBlock() instanceof ItemPipeBlock;
    }

    @Override
    protected boolean hasHandler(Level level, BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.Item.BLOCK, pos, side) != null;
    }

    @Override
    protected void invalidateNetworks(Level level) {
        ItemPipeNetwork.invalidate(level);
    }

    /** Pull sides draw once a batch window. */
    @Override
    protected int pullInterval() {
        return ItemPipeTier.WINDOW;
    }

    @Override
    protected void pull(ServerLevel level, BlockPos pos, Direction side) {
        ResourceHandler<ItemResource> from = level.getCapability(Capabilities.Item.BLOCK, pos.relative(side), side.getOpposite());
        if (from != null) {
            ResourceHandlerUtil.moveStacking(from, ItemPipeNetwork.handler(level, pos, side), r -> true, this.tier.batch(), null);
        }
    }
}
