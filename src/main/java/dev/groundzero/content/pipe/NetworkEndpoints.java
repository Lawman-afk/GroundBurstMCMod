package dev.groundzero.content.pipe;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

/**
 * The blocks a pipe or cable network delivers to, found once when the network is built (they only change when a
 * pipe's or cable's connections change, which rebuilds the network). Each one is looked up through a NeoForge
 * {@link BlockCapabilityCache}, which is cheap to read every tick, follows the block entity being replaced, and returns
 * nothing for a block in an unloaded chunk (so a network never loads chunks by delivering into them).
 *
 * @param <T> the handler type (energy, fluid or item handler)
 */
public final class NetworkEndpoints<T> {
    /** A block next to the network: where it is, and the side of it that faces the network. */
    public record Endpoint(BlockPos pos, Direction side) {}

    private final BlockCapability<T, @Nullable Direction> capability;
    private final List<Endpoint> endpoints;
    private @Nullable List<BlockCapabilityCache<T, @Nullable Direction>> caches;

    public NetworkEndpoints(BlockCapability<T, @Nullable Direction> capability, List<Endpoint> endpoints) {
        this.capability = capability;
        this.endpoints = List.copyOf(endpoints);
    }

    public int size() {
        return this.endpoints.size();
    }

    public Endpoint get(int index) {
        return this.endpoints.get(index);
    }

    /** The handler at endpoint {@code index}, or null if there is none right now (or its chunk is not loaded). */
    public @Nullable T handler(Level level, int index) {
        if (!(level instanceof ServerLevel server)) {
            Endpoint e = this.endpoints.get(index);
            return level.isLoaded(e.pos()) ? level.getCapability(this.capability, e.pos(), e.side()) : null;
        }
        if (this.caches == null) {
            List<BlockCapabilityCache<T, @Nullable Direction>> list = new ArrayList<>(this.endpoints.size());
            for (Endpoint e : this.endpoints) {
                list.add(BlockCapabilityCache.create(this.capability, server, e.pos(), e.side()));
            }
            this.caches = list;
        }
        return this.caches.get(index).getCapability();
    }
}
