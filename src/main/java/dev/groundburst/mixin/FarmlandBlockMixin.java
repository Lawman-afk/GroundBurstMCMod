package dev.groundburst.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Healing (DESIGN.md §21.9): farmland that dries out because the damage took the water beside it, or turns back to
 * earth (dried out, or under rubble the blast threw on it) with the crop it bore, is remembered first, next to
 * remembered damage, so the field comes back wet with its crops. (1.21.1's farmland is a {@code FarmBlock} turning back
 * with the static {@code turnToDirt}; 26.x's a {@code FarmlandBlock} with {@code turnToBaseBlock}.)
 */
@Mixin(FarmBlock.class)
abstract class FarmlandBlockMixin {
    @Shadow
    private static boolean isNearWater(LevelReader level, BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void groundburst$drying(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!isNearWater(level, pos) && !level.isRainingAt(pos.above())) {
            dev.groundburst.heal.Scars.aftermath(level, pos, false);
        }
    }

    /** However it comes to it (dried out, rubble landed on it, trodden), farmland turning back to earth is remembered first. */
    @WrapMethod(method = "turnToDirt")
    private static void groundburst$toEarth(@Nullable Entity by, BlockState state, Level level, BlockPos pos, Operation<Void> original) {
        if (level instanceof ServerLevel server) {
            dev.groundburst.heal.Scars.aftermathCourse(server, pos, false, () -> original.call(by, state, level, pos));
        } else {
            original.call(by, state, level, pos);
        }
    }
}
