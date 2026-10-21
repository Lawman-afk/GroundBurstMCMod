package dev.groundzero.content.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** The launch silos loaded in each dimension, for the Launch Computers to find (DESIGN.md §17.3). */
public final class Launches {
    /** How far a Launch Computer reaches its silos, blocks. */
    public static final int LINK_RANGE = 256;
    private static final Map<ResourceKey<Level>, LongOpenHashSet> SILOS = new HashMap<>();

    private Launches() {}

    static void track(ServerLevel level, BlockPos pos) {
        SILOS.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(pos.asLong());
    }

    static void untrack(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = SILOS.get(level.dimension());
        if (set != null) {
            set.remove(pos.asLong());
        }
    }

    static void clear() {
        SILOS.clear();
    }

    /** The silos within reach of a computer at {@code from}, nearest first. */
    public static List<LaunchSiloBlockEntity> silosNear(ServerLevel level, BlockPos from) {
        LongOpenHashSet set = SILOS.get(level.dimension());
        List<LaunchSiloBlockEntity> out = new ArrayList<>();
        if (set == null) {
            return out;
        }
        for (long l : set.toLongArray()) {
            BlockPos p = BlockPos.of(l);
            if (p.distSqr(from) <= (double) LINK_RANGE * LINK_RANGE && level.getBlockEntity(p) instanceof LaunchSiloBlockEntity silo) {
                out.add(silo);
            }
        }
        out.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(from), b.getBlockPos().distSqr(from)));
        return out;
    }
}
