package dev.groundburst.content.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The launch silos loaded in each dimension, for the Launch Computers to find (DESIGN.md §17.3); and the Launch
 * Computers and Radar Arrays, so the terrain survey knows where to map ({@link TerrainSurvey}).
 */
public final class Launches {
    /** How far a Launch Computer reaches its silos, blocks. */
    public static final int LINK_RANGE = 256;
    private static final Map<ResourceKey<Level>, LongOpenHashSet> SILOS = new HashMap<>();
    private static final Map<ResourceKey<Level>, LongOpenHashSet> MAPPERS = new HashMap<>();

    private Launches() {}

    /** A launch site at {@code pos} (a silo, a superweapon) for the computers to find. */
    public static void track(ServerLevel level, BlockPos pos) {
        SILOS.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(pos.asLong());
    }

    /**
     * A launch site's tick: known to the computers, and if it is on a Launch Control Station's network it takes its
     * orders and reports (DESIGN.md §17.12).
     */
    public static void track(ServerLevel level, LaunchSite site) {
        track(level, site.getBlockPos());
        LaunchNetwork.of(level).tickSite(level, site);
    }

    public static void untrack(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = SILOS.get(level.dimension());
        if (set != null) {
            set.remove(pos.asLong());
        }
    }

    static void clear() {
        SILOS.clear();
        MAPPERS.clear();
    }

    /** A Launch Computer (or anything else that maps the ground round itself) at {@code pos}. */
    static void trackMapper(ServerLevel level, BlockPos pos) {
        MAPPERS.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(pos.asLong());
    }

    static void untrackMapper(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = MAPPERS.get(level.dimension());
        if (set != null) {
            set.remove(pos.asLong());
        }
    }

    /**
     * Whether a mapper is within {@code range} blocks (square) of x/z: a Launch Computer, a Radar Array, or anything on
     * a Launch Control Station's network (its station and members: the station's map reaches round all of them).
     */
    static boolean mapperNear(ServerLevel level, int x, int z, int range) {
        LongOpenHashSet set = MAPPERS.get(level.dimension());
        if (set == null) {
            return LaunchNetwork.of(level).memberNear(x, z, range);
        }
        for (long l : set) {
            BlockPos p = BlockPos.of(l);
            if (Math.abs(p.getX() - x) <= range && Math.abs(p.getZ() - z) <= range) {
                return true;
            }
        }
        return LaunchNetwork.of(level).memberNear(x, z, range);
    }

    /** The launch sites (silos, superweapons) within reach of a computer at {@code from}, nearest first. */
    public static List<LaunchSite> silosNear(ServerLevel level, BlockPos from) {
        LongOpenHashSet set = SILOS.get(level.dimension());
        List<LaunchSite> out = new ArrayList<>();
        if (set == null) {
            return out;
        }
        for (long l : set.toLongArray()) {
            BlockPos p = BlockPos.of(l);
            if (p.distSqr(from) <= (double) LINK_RANGE * LINK_RANGE && level.getBlockEntity(p) instanceof LaunchSite silo) {
                out.add(silo);
            }
        }
        out.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(from), b.getBlockPos().distSqr(from)));
        return out;
    }
}
