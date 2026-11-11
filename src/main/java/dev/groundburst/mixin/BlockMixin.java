package dev.groundburst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Healing (DESIGN.md §21.9): a block changed or broken because its neighbour changed shape, while a remembered change
 * runs its course (water running out of a crater takes a crop's farmland; a fire takes a torch's wall), is remembered
 * first, and broken without its drops (it comes back).
 */
@Mixin(Block.class)
abstract class BlockMixin {
    @Inject(method = "updateOrDestroy(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V", at = @At("HEAD"), cancellable = true)
    private static void groundburst$shapeChanged(BlockState old, BlockState updated, LevelAccessor level, BlockPos pos, int flags, int limit,
            CallbackInfo ci) {
        if (updated != old && ((Object) level) instanceof ServerLevel server && dev.groundburst.heal.Scars.shapeChanged(server, pos) && updated.isAir()) {
            if (old.hasBlockEntity()) {
                server.removeBlockEntity(pos);             // nothing spilt either
            }
            server.destroyBlock(pos, false, null, limit);
            ci.cancel();
        }
    }
}
