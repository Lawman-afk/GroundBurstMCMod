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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A connected set of cables. Energy inserted into any cable is delivered immediately to the consumers touching
 * the network (round-robin, so several machines share fairly), capped per tick by the weakest cable's rate.
 * Networks are cached per level and rebuilt lazily after any cable is placed or removed.
 */
public final class CableNetwork {
    /** Larger networks are cut off here; stops a pathological cable maze from stalling a tick. */
    private static final int MAX_CABLES = 4096;
    private static final Map<Level, Map<BlockPos, CableNetwork>> CACHE = new WeakHashMap<>();

    private final Set<BlockPos> cables;
    private final int rate;
    private long tick = Long.MIN_VALUE;
    private int transferred;
    private int roundRobin;
    private final Journal journal = new Journal();

    private CableNetwork(Set<BlockPos> cables, int rate) {
        this.cables = cables;
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
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof CableBlock) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return new CableNetwork(seen, rate);
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
        List<EnergyHandler> consumers = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos cable : this.cables) {
            for (Direction d : Direction.values()) {
                BlockPos target = cable.relative(d);
                if (target.equals(source) || this.cables.contains(target) || !visited.add(target.immutable())) {
                    continue;
                }
                if (level.getBlockState(target).getBlock() instanceof CableBlock) {
                    continue;   // a different network (e.g. beyond MAX_CABLES)
                }
                EnergyHandler h = level.getCapability(Capabilities.Energy.BLOCK, target, d.getOpposite());
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
            sent += consumers.get((start + k) % consumers.size()).insert(allowed - sent, transaction);
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
