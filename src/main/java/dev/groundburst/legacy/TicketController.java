package dev.groundburst.legacy;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * NeoForge's chunk ticket controller, over Forge 1.20.1's chunk manager (which keeps one set of tickets for the mod: every
 * controller's callback is asked about them as a world loads, {@link RegisterTicketControllersEvent}).
 */
public final class TicketController {
    private final ResourceLocation id;
    final ForgeChunkManager.@Nullable LoadingValidationCallback callback;

    public TicketController(ResourceLocation id, ForgeChunkManager.@Nullable LoadingValidationCallback callback) {
        this.id = id;
        this.callback = callback;
    }

    public TicketController(ResourceLocation id) {
        this(id, null);
    }

    public ResourceLocation id() {
        return this.id;
    }

    public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        return ForgeChunkManager.forceChunk(level, GroundBurst.MODID, owner, chunkX, chunkZ, add, ticking);
    }

    public boolean forceChunk(ServerLevel level, Entity owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        return ForgeChunkManager.forceChunk(level, GroundBurst.MODID, owner, chunkX, chunkZ, add, ticking);
    }

    public boolean forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        return ForgeChunkManager.forceChunk(level, GroundBurst.MODID, owner, chunkX, chunkZ, add, ticking);
    }
}
