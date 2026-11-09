package dev.groundburst.content.command;

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

/**
 * The War Room Display's controller (DESIGN.md §17.17): unformed, a click shows what is missing; formed, its settings
 * (the view and what it draws). Launch Codes used on any of its blocks link it to their network.
 */
public class WarRoomDisplayBlock extends MultiblockControllerBlock {
    /** Client hook: opens its settings screen (set by the client). */
    public static Consumer<BlockPos> screenHook = p -> {};

    public WarRoomDisplayBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
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
