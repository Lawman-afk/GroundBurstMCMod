package dev.groundburst.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;

/**
 * Healing (main's DESIGN.md §21.9): a painting, item frame or armour stand the game's or another mod's explosion breaks
 * is kept whole, to be put back with the ground ({@link dev.groundburst.heal.Explosions#hurt}). 1.21.1's explosion is
 * one class, {@code Explosion}, which hurts what it reaches in {@code explode} (main's is {@code ServerExplosion}); it
 * keeps its damage source to itself, so this gives it out too ({@link dev.groundburst.port.ExplosionDamage}: a bed's
 * explosion, with no entity behind it, is known by it).
 */
@Mixin(Explosion.class)
abstract class ServerExplosionMixin implements dev.groundburst.port.ExplosionDamage {
    @Shadow
    @Final
    private DamageSource damageSource;

    @Override
    public DamageSource groundburst$damageSource() {
        return this.damageSource;
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean groundburst$hurt(Entity entity, DamageSource source, float amount, Operation<Boolean> original) {
        return dev.groundburst.heal.Explosions.hurt(entity, (Explosion) (Object) this, () -> original.call(entity, source, amount));
    }
}
