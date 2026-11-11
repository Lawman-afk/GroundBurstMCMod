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

/** The Launch Computer's controller: formed, right-clicking it opens its radar scope and launch screen. */
public class LaunchComputerBlock extends MultiblockControllerBlock {
    /** Client hook: opens the computer's screen (set by the client). */
    public static Consumer<BlockPos> screenHook = p -> {};

    public LaunchComputerBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties, type);
    }

    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(MultiblockPartBlock.FORMED)) {
            return super.useWithoutItem(state, level, pos, player, hit);       // the preview and what is missing
        }
        if (level.isClientSide()) {
            screenHook.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state_, net.minecraft.world.level.Level level_, net.minecraft.core.BlockPos pos_, net.minecraft.world.entity.player.Player player_, net.minecraft.world.InteractionHand hand_, net.minecraft.world.phys.BlockHitResult hit_) {
        dev.groundburst.legacy.ItemInteractionResult item_ = this.useItemOn(player_.getItemInHand(hand_), state_, level_, pos_, player_, hand_, hit_);
        if (item_ != dev.groundburst.legacy.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return item_.result();
        }
        return hand_ == net.minecraft.world.InteractionHand.MAIN_HAND ? this.useWithoutItem(state_, level_, pos_, player_, hit_)
                : net.minecraft.world.InteractionResult.PASS;
    }
}
