package dev.groundburst.content.fallout;

import dev.groundburst.GroundBurst;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * How the far aftermath's background pass holds a chunk on disk loaded while it changes it (DESIGN.md §21.10): a ticket of
 * its own at the full chunk's level, so the chunk loads over the next ticks as any other (never waited for in the tick,
 * never ticking, never saved with the level), and is let go as soon as it has changed. A ticket the pass forgets goes by
 * itself a minute after its chunk loaded.
 */
final class FarLoads {
    static final DeferredRegister<TicketType> TYPES = DeferredRegister.create(Registries.TICKET_TYPE, GroundBurst.MODID);
    static final Supplier<TicketType> TYPE = TYPES.register("far_aftermath", () -> new TicketType(1200L, TicketType.FLAG_LOADING));

    private FarLoads() {}

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** Asks for the chunk ({@link ChunkPos#pack()}) to load; it is there a few ticks later. */
    static void hold(ServerLevel level, long chunk) {
        level.getChunkSource().addTicketWithRadius(TYPE.get(), new ChunkPos(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)), 0);
    }

    /** Lets the chunk go (it unloads, and is saved, as the server finds the time). */
    static void letGo(ServerLevel level, long chunk) {
        level.getChunkSource().removeTicketWithRadius(TYPE.get(), new ChunkPos(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)), 0);
    }
}
