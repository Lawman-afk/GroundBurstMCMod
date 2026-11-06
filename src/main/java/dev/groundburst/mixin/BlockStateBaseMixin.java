package dev.groundburst.mixin;

import java.util.function.BiConsumer;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Healing (DESIGN.md §21.9): a block the game's or another mod's explosion hits (a creeper's, TNT's, any explosion that
 * goes through the game's explosion code) is remembered first, and if it will heal it drops nothing and spills nothing
 * ({@link dev.groundburst.heal.Explosions}).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @WrapMethod(method = "onExplosionHit")
    private void groundburst$explosionHit(ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit,
            Operation<Void> original) {
        dev.groundburst.heal.Explosions.hit((BlockState) (Object) this, level, pos, explosion, onHit,
                drops -> original.call(level, pos, explosion, drops));
    }
}
