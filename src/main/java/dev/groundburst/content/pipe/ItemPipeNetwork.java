package dev.groundburst.content.pipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A connected run of item pipes (pipes joined by sides in {@code normal} mode). Pipes hold nothing: whatever a machine
 * pushes into the run, a hopper drops in, or a {@code pull} side draws in, is handed straight to the inventories on the
 * run's {@code normal} sides, shared round-robin, never back to where it came from, up to the weakest pipe tier's batch
 * every {@link ItemPipeTier#WINDOW} ticks. Breaking a pipe therefore never drops or loses items.
 *
 * <p>Performance: like the cable and fluid networks, the run and its inventories ({@link NetworkEndpoints}) are found
 * once after a change and cached; delivering only looks at those inventories (through capability caches), never at the
 * pipes, and an inventory in an unloaded chunk is skipped rather than loaded.
 */
public final class ItemPipeNetwork {
    private static final int MAX_PIPES = 4096;
    private static final Map<Level, Map<BlockPos, ItemPipeNetwork>> CACHE = new WeakHashMap<>();

    private final NetworkEndpoints<ResourceHandler<ItemResource>> endpoints;
    /** Items per window: the weakest pipe tier on the run. */
    private final int batch;
    private long window = Long.MIN_VALUE;
    private int moved;
    private int roundRobin;
    private final Journal journal = new Journal();

    private ItemPipeNetwork(NetworkEndpoints<ResourceHandler<ItemResource>> endpoints, int batch) {
        this.endpoints = endpoints;
        this.batch = batch;
    }

    public static void invalidate(Level level) {
        CACHE.remove(level);
    }

    public static @Nullable ItemPipeNetwork of(Level level, BlockPos pipe) {
        Map<BlockPos, ItemPipeNetwork> byPos = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        ItemPipeNetwork net = byPos.get(pipe);
        if (net != null) {
            return net;
        }
        if (!(level.getBlockState(pipe).getBlock() instanceof ItemPipeBlock)) {
            return null;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pipe.immutable());
        seen.add(pipe.immutable());
        while (!queue.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos p = queue.poll();
            BlockState ps = level.getBlockState(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof ItemPipeBlock
                        && ps.getBlock() instanceof ItemPipeBlock && PipeBlock.mode(ps, d) == PipeBlock.Mode.NORMAL
                        && PipeBlock.mode(level.getBlockState(n), d.getOpposite()) == PipeBlock.Mode.NORMAL) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        int batch = Integer.MAX_VALUE;
        List<NetworkEndpoints.Endpoint> ends = new ArrayList<>();
        Set<BlockPos> targets = new HashSet<>();
        for (BlockPos p : seen) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof ItemPipeBlock pipeBlock)) {
                continue;
            }
            batch = Math.min(batch, pipeBlock.tier().batch());
            for (Direction d : Direction.values()) {
                BlockPos target = p.relative(d);
                // Only normal sides receive (pull sides only give); an inventory touching two pipes counts once.
                if (PipeBlock.mode(state, d) == PipeBlock.Mode.NORMAL && !seen.contains(target)
                        && !(level.getBlockState(target).getBlock() instanceof ItemPipeBlock) && targets.add(target.immutable())) {
                    ends.add(new NetworkEndpoints.Endpoint(target.immutable(), d.getOpposite()));
                }
            }
        }
        net = new ItemPipeNetwork(new NetworkEndpoints<>(Capabilities.Item.BLOCK, ends), batch);
        for (BlockPos p : seen) {
            byPos.put(p, net);
        }
        return net;
    }

    /** Items the run may still move in the current window. */
    private int room(Level level) {
        long now = level.getGameTime() / ItemPipeTier.WINDOW;
        if (now != this.window) {
            this.window = now;
            this.moved = 0;
        }
        return this.batch - this.moved;
    }

    /** Hands up to {@code amount} of an item to the run's inventories, never back into {@code source}. */
    public int distribute(Level level, ItemResource resource, int amount, @Nullable BlockPos source, TransactionContext transaction) {
        int allowed = Math.min(amount, room(level));
        int n = this.endpoints.size();
        if (allowed <= 0 || n == 0) {
            return 0;
        }
        int sent = 0;
        int start = Math.floorMod(this.roundRobin++, n);
        for (int k = 0; k < n && sent < allowed; k++) {
            int i = (start + k) % n;
            if (this.endpoints.get(i).pos().equals(source)) {
                continue;                                  // never back where it came from
            }
            ResourceHandler<ItemResource> h = this.endpoints.handler(level, i);
            if (h != null) {
                sent += h.insert(resource, allowed - sent, transaction);
            }
        }
        if (sent > 0) {
            this.journal.updateSnapshots(transaction);
            this.moved += sent;
        }
        return sent;
    }

    /** What a neighbour sees when it looks at a pipe from {@code side}: somewhere to put items, nothing to take. */
    public static ResourceHandler<ItemResource> handler(Level level, BlockPos pipe, @Nullable Direction side) {
        BlockPos source = side == null ? null : pipe.relative(side);
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemResource getResource(int index) {
                return ItemResource.EMPTY;
            }

            @Override
            public long getAmountAsLong(int index) {
                return 0;
            }

            @Override
            public long getCapacityAsLong(int index, ItemResource resource) {
                ItemPipeNetwork net = of(level, pipe);
                return net == null ? 0 : net.batch;
            }

            @Override
            public boolean isValid(int index, ItemResource resource) {
                return true;
            }

            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                ItemPipeNetwork net = of(level, pipe);
                return net == null ? 0 : net.distribute(level, resource, amount, source, transaction);
            }

            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /** Rolls back the window's move counter when a simulated (aborted) transaction is undone. */
    private final class Journal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return moved;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            moved = snapshot;
        }
    }
}
