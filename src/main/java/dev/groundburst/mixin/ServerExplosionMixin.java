package dev.groundburst.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;

/**
 * Healing (DESIGN.md §21.9): a painting, item frame or armour stand the game's or another mod's explosion breaks is kept
 * whole, to be put back with the ground ({@link dev.groundburst.heal.Explosions#hurt}).
 */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
    @WrapOperation(method = "hurtEntities(Ljava/util/List;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean groundburst$hurt(Entity entity, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return dev.groundburst.heal.Explosions.hurt(entity, (Explosion) (Object) this, () -> original.call(entity, level, source, amount));
    }
}
