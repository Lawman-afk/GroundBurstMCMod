package dev.groundzero.content.fluid;

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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A connected run of fluid pipes (pipes joined by sides in {@code normal} mode). Pipes hold nothing: whatever a
 * machine pushes into the run, or a {@code pull} side draws in, is passed straight on to the tanks on the run's
 * {@code normal} sides, shared round-robin, never back to where it came from, up to the weakest pipe tier's rate per
 * tick.
 * Breaking a pipe therefore never spills or loses fluid.
 */
public final class FluidPipeNetwork {
    private static final int MAX_PIPES = 4096;
    private static final Map<Level, Map<BlockPos, FluidPipeNetwork>> CACHE = new WeakHashMap<>();

    private final Set<BlockPos> pipes;
    /** mB per tick: the weakest pipe tier on the run. */
    private final int rate;
    private long tick = Long.MIN_VALUE;
    private int transferred;
    private int roundRobin;
    private final Journal journal = new Journal();

    private FluidPipeNetwork(Set<BlockPos> pipes, int rate) {
        this.pipes = pipes;
        this.rate = rate;
    }

    public static void invalidate(Level level) {
        CACHE.remove(level);
    }

    public static @Nullable FluidPipeNetwork of(Level level, BlockPos pipe) {
        Map<BlockPos, FluidPipeNetwork> byPos = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        FluidPipeNetwork net = byPos.get(pipe);
        if (net == null) {
            if (!(level.getBlockState(pipe).getBlock() instanceof FluidPipeBlock)) {
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
                    if (!seen.contains(n) && level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof FluidPipeBlock
                            && ps.getBlock() instanceof FluidPipeBlock && FluidPipeBlock.mode(ps, d) == FluidPipeBlock.Mode.NORMAL
                            && FluidPipeBlock.mode(level.getBlockState(n), d.getOpposite()) == FluidPipeBlock.Mode.NORMAL) {
                        seen.add(n);
                        queue.add(n);
                    }
                }
            }
            int rate = Integer.MAX_VALUE;
            for (BlockPos p : seen) {
                if (level.getBlockState(p).getBlock() instanceof FluidPipeBlock pipeBlock) {
                    rate = Math.min(rate, pipeBlock.tier().rate());
                }
            }
            net = new FluidPipeNetwork(seen, rate);
            for (BlockPos p : seen) {
                byPos.put(p, net);
            }
        }
        return net;
    }

    /** Passes up to {@code amount} of a fluid to the run's tanks, never back into {@code source}. */
    public int distribute(Level level, FluidResource resource, int amount, @Nullable BlockPos source, TransactionContext transaction) {
        long now = level.getGameTime();
        if (now != this.tick) {
            this.tick = now;
            this.transferred = 0;
        }
        int allowed = Math.min(amount, this.rate - this.transferred);
        if (allowed <= 0) {
            return 0;
        }
        List<ResourceHandler<FluidResource>> consumers = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos pipe : this.pipes) {
            BlockState state = level.getBlockState(pipe);
            for (Direction d : Direction.values()) {
                if (!(state.getBlock() instanceof FluidPipeBlock) || FluidPipeBlock.mode(state, d) != FluidPipeBlock.Mode.NORMAL) {
                    continue;          // only normal sides receive; pull sides only give
                }
                BlockPos target = pipe.relative(d);
                if (target.equals(source) || this.pipes.contains(target) || !visited.add(target.immutable())
                        || level.getBlockState(target).getBlock() instanceof FluidPipeBlock) {
                    continue;
                }
                ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK, target, d.getOpposite());
                if (h != null) {
                    consumers.add(h);
                }
            }
        }
        if (consumers.isEmpty()) {
            return 0;
        }
        int sent = 0;
        int start = this.roundRobin++ % consumers.size();
        for (int k = 0; k < consumers.size() && sent < allowed; k++) {
            sent += consumers.get((start + k) % consumers.size()).insert(resource, allowed - sent, transaction);
        }
        if (sent > 0) {
            this.journal.updateSnapshots(transaction);
            this.transferred += sent;
        }
        return sent;
    }

    /** What a neighbour sees when it looks at a pipe from {@code side}: somewhere to pour fluid, nothing to drain. */
    public static ResourceHandler<FluidResource> handler(Level level, BlockPos pipe, @Nullable Direction side) {
        BlockPos source = side == null ? null : pipe.relative(side);
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public FluidResource getResource(int index) {
                return FluidResource.EMPTY;
            }

            @Override
            public long getAmountAsLong(int index) {
                return 0;
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                FluidPipeNetwork net = of(level, pipe);
                return net == null ? 0 : net.rate;
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return true;
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                FluidPipeNetwork net = of(level, pipe);
                return net == null ? 0 : net.distribute(level, resource, amount, source, transaction);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /** Rolls back the per-tick transfer counter when a simulated (aborted) transaction is undone. */
    private final class Journal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return transferred;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            transferred = snapshot;
        }
    }
}
