package dev.groundburst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Healing (DESIGN.md §21.9): water or lava running out of the damage (a crater lake over its rim, a pond the blast
 * broke) is remembered where it runs, next to remembered damage, with what it washes away and what that takes with it
 * (a crop whose farmland it fills), so the ground comes back dry and the field whole; nothing washed away drops meanwhile.
 */
@Mixin(FlowingFluid.class)
abstract class FlowingFluidMixin {
    @WrapMethod(method = "spreadTo")
    private void groundburst$spreading(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target,
            Operation<Void> original) {
        if (level instanceof ServerLevel server && dev.groundburst.heal.Scars.watching(server, pos)) {
            dev.groundburst.heal.Scars.aftermathCourse(server, pos, false, () -> original.call(level, pos, state, direction, target));
        } else {
            original.call(level, pos, state, direction, target);
        }
    }

    @WrapOperation(method = "spreadTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FlowingFluid;beforeDestroyingBlock(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void groundburst$washedAway(FlowingFluid fluid, LevelAccessor level, BlockPos pos, BlockState state, Operation<Void> original) {
        if (level instanceof ServerLevel server && dev.groundburst.heal.Scars.remembered(server, pos)
                && !dev.groundburst.heal.Scars.healingDrops(server, pos)) {
            return;                                        // it comes back when the ground heals: nothing washed out meanwhile
        }
        original.call(fluid, level, pos, state);
    }
}
