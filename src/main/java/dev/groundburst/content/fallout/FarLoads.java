package dev.groundburst.content.fallout;

import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * How the far aftermath's background pass holds a chunk on disk loaded while it changes it (main's DESIGN.md §21.10), this
 * version's way: a region ticket of its own at the full chunk's level, so the chunk loads over the next ticks as any other
 * (never waited for in the tick, never ticking, never saved with the level), and is let go as soon as it has changed. A
 * ticket the pass forgets goes by itself after a minute. (Main's ticket type is a registered one; 1.21.1's are made.)
 */
final class FarLoads {
    static final TicketType<ChunkPos> TYPE = TicketType.create("groundburst:far_aftermath", Comparator.comparingLong(ChunkPos::toLong), 1200);

    private FarLoads() {}

    static void register(IEventBus modBus) {}

    /** Asks for the chunk ({@link ChunkPos#toLong()}) to load; it is there a few ticks later. */
    static void hold(ServerLevel level, long chunk) {
        ChunkPos pos = new ChunkPos(chunk);
        level.getChunkSource().addRegionTicket(TYPE, pos, 0, pos);
    }

    /** Lets the chunk go (it unloads, and is saved, as the server finds the time). */
    static void letGo(ServerLevel level, long chunk) {
        ChunkPos pos = new ChunkPos(chunk);
        level.getChunkSource().removeRegionTicket(TYPE, pos, 0, pos);
    }
}
