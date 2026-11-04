package dev.groundburst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Healing (DESIGN.md §21.9): leaves that decay because damage took their tree's trunk are remembered first, so the
 * whole tree comes back; they drop nothing meanwhile (no saplings had twice), unless the server lets healing blocks drop.
 */
@Mixin(LeavesBlock.class)
abstract class LeavesBlockMixin {
    @Shadow
    protected abstract boolean decaying(BlockState state);

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void groundburst$decaying(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (decaying(state) && dev.groundburst.heal.Scars.aftermath(level, pos, false) && !dev.groundburst.heal.Scars.healingDrops(level, pos)) {
            dev.groundburst.heal.Scars.beginCascade();     // (a vine or cocoa pod on it goes with it, remembered)
            try {
                level.removeBlock(pos, false);
            } finally {
                dev.groundburst.heal.Scars.endCascade();
            }
            ci.cancel();
        }
    }
}
