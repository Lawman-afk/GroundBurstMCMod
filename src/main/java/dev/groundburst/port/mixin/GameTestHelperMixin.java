package dev.groundburst.port.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;

/**
 * Main's GameTests count their blocks from their structure's own corner, as 26.x's test helper does; 1.21.1's counts
 * from the test's structure block, which it sets a block under that corner, so every one of main's tests stood a block
 * too low here (its floor at y 0 was the structure block's layer). The helper's own sums from relative to world and back
 * are made from the corner (docs/BACKPORT.md). Only the helper's: the framework still finds its structure block where
 * it put it.
 */
@Mixin(GameTestHelper.class)
public abstract class GameTestHelperMixin {
    @WrapOperation(method = {"absolutePos", "relativePos", "absoluteVec", "relativeVec"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/gametest/framework/GameTestInfo;getStructureBlockPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos groundburst$fromTheCorner(GameTestInfo info, Operation<BlockPos> original) {
        BlockPos structureBlock = original.call(info);
        return structureBlock == null ? null : structureBlock.above();
    }
}
