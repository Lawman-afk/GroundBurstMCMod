package dev.groundburst.legacy;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Minecraft 1.21 asks a block about a click twice (with the held item, then without); 1.20.1 asks once (use). The port
 * gives each of the mod's blocks a use that asks its two methods in turn; these are the same two questions put to any
 * block's state (a multiblock part passing a click to its controller).
 */
public final class UseOnBlock {
    private UseOnBlock() {}

    public static ItemInteractionResult itemOn(BlockState state, ItemStack stack, Level level, Player player, InteractionHand hand,
            BlockHitResult hit) {
        InteractionResult r = state.use(level, player, hand, hit);
        return switch (r) {
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    public static InteractionResult withoutItem(BlockState state, Level level, Player player, BlockHitResult hit) {
        return state.use(level, player, InteractionHand.MAIN_HAND, hit);
    }
}
