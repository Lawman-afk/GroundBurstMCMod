package dev.groundburst.legacy.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.groundburst.legacy.component.DataComponentInput;
import dev.groundburst.legacy.component.ImplicitComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A placed block item hands the data its block entity keeps in it (1.21's implicit components) to the new block entity. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private static void groundburst$implicit(Level level, @Nullable Player player, BlockPos pos, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ImplicitComponents be) {
            be.applyImplicitComponents(new DataComponentInput(stack));
            ((BlockEntity) be).setChanged();
        }
    }
}
