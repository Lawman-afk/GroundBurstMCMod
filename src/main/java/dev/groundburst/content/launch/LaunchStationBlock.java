package dev.groundburst.content.launch;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.multiblock.MultiblockControllerBlock;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Launch Control Station's controller: formed, right-clicking it opens the station's screen (DESIGN.md §17.12). */
public class LaunchStationBlock extends MultiblockControllerBlock {
    /** Client hook: opens the station's screen (set by the client). */
    public static Consumer<BlockPos> screenHook = p -> {};

    public LaunchStationBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties, type);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(MultiblockPartBlock.FORMED)) {
            return super.useWithoutItem(state, level, pos, player, hit);       // the preview and what is missing
        }
        if (level.isClientSide()) {
            screenHook.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }
}
