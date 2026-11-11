package dev.groundburst.legacy.mixin;

import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Healing (main's DESIGN.md §21.9) of the game's and other mods' explosions on 1.20.1: a block an explosion hits is
 * remembered first, and if it will heal it drops nothing and spills nothing ({@link dev.groundburst.heal.Explosions}).
 * The 1.21.1 build wraps the block's own {@code onExplosionHit} (its drops and its removal, 1.21's); 1.20.1 has none, its
 * explosion breaking each block in its own loop, so the loop's work for a block (its drops, then Forge's
 * {@code onBlockExploded}) is done here as that hit, vanilla's own then skipped for it. Only the server's is remembered
 * (the client runs an explosion's blocks too, ahead of the server's word).
 */
@Mixin(Explosion.class)
abstract class ExplosionHitMixin {
    @Shadow
    @Final
    private Explosion.BlockInteraction blockInteraction;

    @Shadow
    @Final
    private @Nullable Entity source;

    @Shadow
    @Final
    private float radius;

    /** The block whose hit was done here, its own removal (the loop's next call) then skipped. */
    @Unique
    private @Nullable BlockPos groundburst$done;

    @Shadow
    private static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> drops, ItemStack stack, BlockPos pos) {
        throw new AssertionError();
    }

    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/state/BlockState;canDropFromExplosion(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)Z"))
    private boolean groundburst$hit(BlockState state, BlockGetter getter, BlockPos pos, Explosion explosion, Operation<Boolean> original,
            @Local ObjectArrayList<Pair<ItemStack, BlockPos>> drops) {
        if (!(((Object) getter) instanceof ServerLevel server)) {
            return original.call(state, getter, pos, explosion);
        }
        BlockPos at = pos.immutable();
        BiConsumer<ItemStack, BlockPos> toLoop = (stack, where) -> addBlockDrops(drops, stack, where);
        dev.groundburst.heal.Explosions.hit(state, server, at, explosion, toLoop, dropTo -> {
            if (original.call(state, getter, pos, explosion)) {
                drop(state, server, at, explosion, dropTo);
            }
            state.onBlockExploded(server, at, explosion);
        });
        this.groundburst$done = at;
        return false;                                       // (its drops are done: the loop's own skipped)
    }

    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/state/BlockState;onBlockExploded(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)V"))
    private void groundburst$removed(BlockState state, Level level, BlockPos pos, Explosion explosion, Operation<Void> original) {
        BlockPos done = this.groundburst$done;
        this.groundburst$done = null;
        if (done == null || !done.equals(pos)) {
            original.call(state, level, pos, explosion);
        }
    }

    /** A block's drops in an explosion, as the loop works them out (1.20.1's Explosion.finalizeExplosion). */
    @Unique
    private void drop(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropTo) {
        BlockEntity entity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        LootParams.Builder loot = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY, entity)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this.source);
        if (this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
            loot.withParameter(LootContextParams.EXPLOSION_RADIUS, this.radius);
        }
        state.spawnAfterBreak(level, pos, ItemStack.EMPTY, explosion.getIndirectSourceEntity() instanceof Player);
        state.getDrops(loot).forEach(stack -> dropTo.accept(stack, pos));
    }
}
