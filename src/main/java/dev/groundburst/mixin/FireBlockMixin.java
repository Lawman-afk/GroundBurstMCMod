package dev.groundburst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;

/**
 * Healing (DESIGN.md §21.9): a block a fire burns away (or turns to fire) next to remembered damage is remembered
 * first, with what its burning takes with it (a torch on it, a door on it), so a fire a blast started heals with the
 * crater, however far it has spread.
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    @Inject(method = "checkBurnOut", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z")})
    private void groundburst$burning(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
        if (level instanceof ServerLevel server) {
            dev.groundburst.heal.Scars.aftermath(server, pos, true);
        }
    }

    @WrapMethod(method = "checkBurnOut")
    private void groundburst$burningCourse(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face,
            Operation<Void> original) {
        boolean watched = level instanceof ServerLevel server && dev.groundburst.heal.Scars.watching(server, pos);
        if (watched) {
            dev.groundburst.heal.Scars.beginCascade();
        }
        try {
            original.call(level, pos, chance, random, age, face);
        } finally {
            if (watched) {
                dev.groundburst.heal.Scars.endCascade();
            }
        }
    }
}
