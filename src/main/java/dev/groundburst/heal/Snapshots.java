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

    /** The level's time of day in ticks (0 at sunrise, 24 000 a day), or -1 where there is no day and night. */
    static long dayTime(ServerLevel level) {
        return level.dimensionType().hasFixedTime() ? -1 : level.getDayTime();
    }
}
