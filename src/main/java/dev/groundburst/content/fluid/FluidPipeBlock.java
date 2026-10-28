package dev.groundburst.content.fluid;

import dev.groundburst.content.pipe.PipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A fluid pipe (DESIGN.md §6.4), in three tiers. Sides, the Wrench and pulling work as for every {@link PipeBlock};
 * runs of connected fluid pipe form a {@link FluidPipeNetwork}. A pull side draws every tick, up to the run's rate.
 */
public class FluidPipeBlock extends PipeBlock {
    /** Ticks between pulls (each pulling side draws up to the tier's per-tick rate each time, so it pulls at full rate). */
    public static final int PULL_INTERVAL = 1;

    private final FluidPipeTier tier;

    public FluidPipeBlock(Properties properties, FluidPipeTier tier) {
        super(properties);
        this.tier = tier;
    }

    public FluidPipeTier tier() {
        return this.tier;
    }

    @Override
    public boolean isSameKind(BlockState state) {
        return state.getBlock() instanceof FluidPipeBlock;
    }

    @Override
    protected boolean hasHandler(Level level, BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.Fluid.BLOCK, pos, side) != null;
    }

    @Override
    protected void invalidateNetworks(Level level) {
        FluidPipeNetwork.invalidate(level);
    }

    @Override
    protected int pullInterval() {
        return PULL_INTERVAL;
    }

    @Override
    protected void pull(ServerLevel level, BlockPos pos, Direction side) {
        ResourceHandler<FluidResource> from = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
        if (from != null) {
            ResourceHandlerUtil.move(from, FluidPipeNetwork.handler(level, pos, side), r -> true, this.tier.rate(), null);
        }
    }
}
