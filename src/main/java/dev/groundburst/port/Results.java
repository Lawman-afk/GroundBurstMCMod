package dev.groundburst.port;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** What an item's use answers in 1.21.1: main's result with the stack now in the hand. */
public final class Results {
    private Results() {}

    public static InteractionResultHolder<ItemStack> holder(InteractionResult result, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    public static InteractionResultHolder<ItemStack> holder(InteractionResultHolder<ItemStack> result, Player player, InteractionHand hand) {
        return result;
    }
}
