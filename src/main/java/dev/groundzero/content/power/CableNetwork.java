package dev.groundzero.content.power;

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
import dev.groundzero.content.pipe.NetworkEndpoints;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A connected set of cables. Energy inserted into any cable is delivered immediately to the consumers touching
 * the network (round-robin, so several machines share fairly), capped per tick by the weakest cable's rate.
 * Networks are cached per level and rebuilt lazily after any cable is placed or removed.
 *
 * <p>Performance: the consumers along the network ({@link NetworkEndpoints}) are found once, when the network is built,
 * and read through capability caches; delivering never walks the cables, so generators pushing every tick cost the same
 * on a long network as on a short one, and a consumer in an unloaded chunk is skipped rather than loaded.
 */
public final class CableNetwork {
    /** Larger networks are cut off here; stops a pathological cable maze from stalling a tick. */
    private static final int MAX_CABLES = 4096;
    private static final Map<Level, Map<BlockPos, CableNetwork>> CACHE = new WeakHashMap<>();

    private final Set<BlockPos> cables;
    private final NetworkEndpoints<EnergyHandler> endpoints;
    private final int rate;
    private long tick = Long.MIN_VALUE;
    private int transferred;
    private int roundRobin;
    private final Journal journal = new Journal();

    private CableNetwork(Set<BlockPos> cables, NetworkEndpoints<EnergyHandler> endpoints, int rate) {
        this.cables = cables;
        this.endpoints = endpoints;
        this.rate = rate;
    }

    public static void invalidate(Level level) {
        CACHE.remove(level);
    }

    public static @Nullable CableNetwork of(Level level, BlockPos cable) {
        Map<BlockPos, CableNetwork> byPos = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        CableNetwork net = byPos.get(cable);
        if (net == null) {
            net = build(level, cable);
            if (net == null) {
                return null;
            }
            for (BlockPos p : net.cables) {
                byPos.put(p, net);
            }
        }
        return net;
    }

    private static @Nullable CableNetwork build(Level level, BlockPos start) {
        if (!(level.getBlockState(start).getBlock() instanceof CableBlock)) {
            return null;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        int rate = Integer.MAX_VALUE;
        while (!queue.isEmpty() && seen.size() <= MAX_CABLES) {
            BlockPos p = queue.poll();
            if (!(level.getBlockState(p).getBlock() instanceof CableBlock cable)) {
                continue;
            }
            rate = Math.min(rate, cable.tier().rate());
            BlockState ps = level.getBlockState(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof CableBlock
                        && CableBlock.side(ps, d) == CableBlock.Side.CONNECTED
                        && CableBlock.side(level.getBlockState(n), d.getOpposite()) == CableBlock.Side.CONNECTED) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        // Consumers: whatever sits beside a connected side and is not a cable; a machine touching two cables counts once.
        List<NetworkEndpoints.Endpoint> ends = new ArrayList<>();
        Set<BlockPos> targets = new HashSet<>();
        for (BlockPos p : seen) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof CableBlock)) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos target = p.relative(d);
                if (CableBlock.side(state, d) == CableBlock.Side.CONNECTED && !seen.contains(target)
                        && !(level.getBlockState(target).getBlock() instanceof CableBlock) && targets.add(target.immutable())) {
                    ends.add(new NetworkEndpoints.Endpoint(target.immutable(), d.getOpposite()));
                }
            }
        }
        return new CableNetwork(seen, new NetworkEndpoints<>(Capabilities.Energy.BLOCK, ends), rate);
    }

    /** Delivers up to {@code amount} FE to the network's consumers, never back into {@code source}. */
    public int distribute(Level level, int amount, @Nullable BlockPos source, TransactionContext transaction) {
        long now = level.getGameTime();
        if (now != this.tick) {
            this.tick = now;
            this.transferred = 0;
        }
        int allowed = Math.min(amount, this.rate - this.transferred);
        if (allowed <= 0) {
            return 0;
        }
        int n = this.endpoints.size();
        if (n == 0) {
            return 0;
        }
        int sent = 0;
        int start = Math.floorMod(this.roundRobin++, n);
        for (int k = 0; k < n && sent < allowed; k++) {
            int i = (start + k) % n;
            if (this.endpoints.get(i).pos().equals(source)) {
                continue;                                  // never back into the block that pushed it
            }
            EnergyHandler h = this.endpoints.handler(level, i);
            if (h != null) {
                sent += h.insert(allowed - sent, transaction);
            }
        }
        if (sent > 0) {
            this.journal.updateSnapshots(transaction);
            this.transferred += sent;
        }
        return sent;
    }

    /** The handler a neighbour sees when it looks at a cable from {@code side}. */
    public static EnergyHandler handler(Level level, BlockPos cable, @Nullable Direction side) {
        BlockPos source = side == null ? null : cable.relative(side);
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return 0;
            }

            @Override
            public long getCapacityAsLong() {
                CableNetwork net = of(level, cable);
                return net == null ? 0 : net.rate;
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                CableNetwork net = of(level, cable);
                return net == null ? 0 : net.distribute(level, amount, source, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
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
