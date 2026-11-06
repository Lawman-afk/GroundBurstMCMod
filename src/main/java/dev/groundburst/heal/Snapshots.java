package dev.groundburst.heal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * The few things healing does that differ between game versions (DESIGN.md §21.9): saving and loading a block entity
 * or an entity as a tag, telling a block its neighbour changed, and the time of day. (The 1.21.1 build has its own copy.)
 */
final class Snapshots {
    private Snapshots() {}

    /** A block entity as it is now, everything it holds (its contents, its words, its custom name). */
    static CompoundTag save(BlockEntity be, ServerLevel level) {
        return be.saveWithFullMetadata(level.registryAccess());
    }

    /** Puts a saved block entity's contents back into the one standing there now. */
    static void load(BlockEntity be, CompoundTag tag, ServerLevel level) {
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    /** An entity as it is (even one just taken out of the world), with its type; null if it is not one that is saved. */
    static @Nullable CompoundTag save(Entity entity, ServerLevel level) {
        String id = entity.getEncodeId();
        if (id == null) {
            return null;
        }
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        out.putString("id", id);
        entity.saveWithoutId(out);
        return out.buildResult();
    }

    /** A saved entity made again (not yet added to the level), or null if it cannot be. */
    static @Nullable Entity load(CompoundTag tag, ServerLevel level) {
        return EntityType.loadEntityRecursive(tag, level, new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP);
    }

    /** Tells a block that the one beside it changed, as a block set down beside it would (nothing else round it told). */
    static void tellNeighbour(ServerLevel level, BlockState neighbour, BlockPos at, net.minecraft.world.level.block.Block changed, BlockPos from) {
        level.neighborChanged(neighbour, at, changed, null, false);
    }

    /** The id of an explosion's damage type, if it is one of the game's kind (which keeps its damage source), else null. */
    static net.minecraft.resources.@Nullable Identifier damageType(net.minecraft.world.level.Explosion explosion) {
        return explosion instanceof net.minecraft.world.level.ServerExplosion s
                ? s.getDamageSource().typeHolder().unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null) : null;
    }

    /**
     * Whether a fire at {@code pos} burns on, to go out by itself: its chunk ticks, and a player is near enough for fire to
     * burn there (26.x's {@code fire_spread_radius_around_player}: beyond it a fire neither spreads nor goes out).
     */
    static boolean burnsOut(ServerLevel level, BlockPos pos) {
        return level.shouldTickBlocksAt(pos) && level.canSpreadFireAround(pos);
    }

    /** The level's time of day in ticks (0 at sunrise, 24 000 a day), or -1 where there is no day and night. */
    static long dayTime(ServerLevel level) {
        return level.dimensionType().hasFixedTime() ? -1 : level.getDefaultClockTime();
    }
}
