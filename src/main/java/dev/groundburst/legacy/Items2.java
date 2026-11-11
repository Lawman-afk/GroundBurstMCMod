package dev.groundburst.legacy;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Minecraft 1.21's item stack calls 1.20.1 lacks. */
public final class Items2 {
    private Items2() {}

    /** 1.21's stack.consume: used up, but not by a player in creative mode. */
    public static void consume(ItemStack stack, int count, @Nullable LivingEntity user) {
        if (!(user instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(count);
        }
    }
}
