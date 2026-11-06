package dev.groundburst.mixin;

import java.util.function.BiConsumer;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Healing (main's DESIGN.md §21.9): a block the game's or another mod's explosion hits (a creeper's, TNT's, any explosion
 * that goes through the game's explosion code) is remembered first, and if it will heal it drops nothing and spills
 * nothing ({@link dev.groundburst.heal.Explosions}). 1.21.1's: the hit takes any level (the client runs an explosion's
 * blocks too, ahead of the server's word), and only the server's is remembered.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @WrapMethod(method = "onExplosionHit")
    private void groundburst$explosionHit(Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit,
            Operation<Void> original) {
        if (level instanceof ServerLevel server) {
            dev.groundburst.heal.Explosions.hit((BlockState) (Object) this, server, pos, explosion, onHit,
                    drops -> original.call(level, pos, explosion, drops));
        } else {
            original.call(level, pos, explosion, onHit);
        }
    }
}
