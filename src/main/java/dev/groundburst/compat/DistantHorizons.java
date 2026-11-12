package dev.groundburst.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import dev.groundburst.GroundBurst;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

/**
 * Distant Horizons (DESIGN.md §21.10): its far view is built from chunks as it sees them, so a chunk the far aftermath has
 * changed is handed to it at once ({@code IDhApiTerrainDataRepo.overwriteChunkDataAsync} with the chunk and its level),
 * and the charred, broken and withered land shows in the far view as it changes. Only touched when Distant Horizons is
 * installed ({@link Hook} names its classes); should its API not take the chunk (another version), it is left alone after
 * one warning.
 */
public final class DistantHorizons {
    private static final boolean LOADED = ModList.get().isLoaded("distanthorizons");
    private static volatile boolean broken;

    private DistantHorizons() {}

    /** A loaded chunk the far aftermath has just changed. */
    public static void chunkChanged(ServerLevel level, LevelChunk chunk) {
        if (!LOADED || broken) {
            return;
        }
        try {
            Hook.push(level, chunk);
        } catch (LinkageError | RuntimeException e) {
            broken = true;
            GroundBurst.LOGGER.warn("Could not hand changed chunks to Distant Horizons: {}", e.toString());
        }
    }

    /** The calls into Distant Horizons' API (loaded only with it there). */
    private static final class Hook {
        static void push(ServerLevel level, LevelChunk chunk) {
            if (DhApi.Delayed.worldProxy == null || DhApi.Delayed.terrainRepo == null || !DhApi.Delayed.worldProxy.worldLoaded()) {
                return;
            }
            for (IDhApiLevelWrapper wrapper : DhApi.Delayed.worldProxy.getAllLoadedLevelWrappers()) {
                if (wrapper.getWrappedMcObject() == level) {
                    DhApi.Delayed.terrainRepo.overwriteChunkDataAsync(wrapper, new Object[] {chunk, level});
                    return;
                }
            }
        }
    }
}
