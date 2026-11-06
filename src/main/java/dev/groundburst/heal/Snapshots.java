package dev.groundburst.heal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The few things healing does that differ between game versions (main's DESIGN.md §21.9): saving and loading a block
 * entity or an entity as a tag, telling a block its neighbour changed, and the time of day. This is 1.21.1's (main
 * has its own).
 */
final class Snapshots {
    private Snapshots() {}

    /** A block entity as it is now, everything it holds (its contents, its words, its custom name). */
    static CompoundTag save(BlockEntity be, ServerLevel level) {
        return be.saveWithFullMetadata(level.registryAccess());
    }

    /** Puts a saved block entity's contents back into the one standing there now. */
    static void load(BlockEntity be, CompoundTag tag, ServerLevel level) {
        be.loadWithComponents(tag, level.registryAccess());
    }

    /** An entity as it is (even one just taken out of the world), with its type; null if it is not one that is saved. */
    static @Nullable CompoundTag save(Entity entity, ServerLevel level) {
        String id = entity.getEncodeId();
        if (id == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        entity.saveWithoutId(tag);
        return tag;
    }

    /** A saved entity made again (not yet added to the level), or null if it cannot be. */
    static @Nullable Entity load(CompoundTag tag, ServerLevel level) {
        return EntityType.loadEntityRecursive(tag, level, e -> e);
    }

    /** Tells a block that the one beside it changed, as a block set down beside it would (nothing else round it told). */
    static void tellNeighbour(ServerLevel level, BlockState neighbour, BlockPos at, Block changed, BlockPos from) {
        level.neighborChanged(neighbour, at, changed, from, false);
    }

    /**
     * The id of an explosion's damage type (1.21.1's explosion gives its damage source out through
     * {@code mixin/ServerExplosionMixin}, {@link dev.groundburst.port.ExplosionDamage}), else null.
     */
    static net.minecraft.resources.@Nullable ResourceLocation damageType(net.minecraft.world.level.Explosion explosion) {
        return explosion instanceof dev.groundburst.port.ExplosionDamage d
                ? d.groundburst$damageSource().typeHolder().unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null) : null;
    }

    /**
     * Whether a fire at {@code pos} burns on, to go out by itself: its chunk ticks, and fire ticks at all (1.21.1's
     * {@code doFireTick}; 26.x has a reach round players instead, beyond which a fire neither spreads nor goes out).
     */
    static boolean burnsOut(ServerLevel level, BlockPos pos) {
        return level.shouldTickBlocksAt(pos) && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
    }

    /** The level's time of day in ticks (0 at sunrise, 24 000 a day), or -1 where there is no day and night. */
    static long dayTime(ServerLevel level) {
        return level.dimensionType().hasFixedTime() ? -1 : level.getDayTime();
    }
}
