package dev.groundburst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SpreadingSnowyBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Healing (DESIGN.md §21.9): grass dying under the rubble or ash the damage left on it, or creeping onto earth the
 * damage bared, next to remembered damage is remembered first, so the ground comes back as it was, grass where grass
 * was and earth where earth was.
 */
@Mixin(SpreadingSnowyBlock.class)
abstract class SpreadingSnowyBlockMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean groundburst$spreading(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        boolean[] done = {false};
        dev.groundburst.heal.Scars.aftermathCourse(level, pos, false, () -> done[0] = original.call(level, pos, state));
        return done[0];
    }
}
