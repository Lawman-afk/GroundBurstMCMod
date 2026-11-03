package dev.groundburst.content.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.machine.MachineBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The launch silos loaded in each dimension, for the Launch Computers to find (DESIGN.md §17.3); and the Launch
 * Computers and Radar Arrays, so the terrain survey knows where to map ({@link TerrainSurvey}). A launch site another
 * mod carries out of the world (a Create contraption, DESIGN.md §17.16) is found here while its carrier ticks it.
 */
public final class Launches {
    /** How far a Launch Computer reaches its silos, blocks. */
    public static final int LINK_RANGE = 256;
    private static final Map<ResourceKey<Level>, LongOpenHashSet> SILOS = new HashMap<>();
    /** Each mapper (by its block) and where it is in the world. */
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> MAPPERS = new HashMap<>();

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

    /** The launch site a carrier (a contraption) is carrying with its key at {@code pos}, if it ticked it just now. */
    public static @Nullable LaunchSite carried(ServerLevel level, BlockPos pos) {
        return dev.groundburst.content.machine.Carried.at(level, pos) instanceof LaunchSite site ? site : null;
    }

    /** The launch site with its key at {@code pos}: the one standing there, or one carried from there. */
    public static @Nullable LaunchSite site(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LaunchSite site) {
            return site;
        }
        return carried(level, pos);
    }

    static void clear() {
        SILOS.clear();
        MAPPERS.clear();
    }

    /**
     * A Launch Computer (or anything else that maps the ground round itself) at {@code pos}, {@code at} where it is in the
     * world (on a ship, DESIGN.md §17.16).
     */
    static void trackMapper(ServerLevel level, BlockPos pos, BlockPos at) {
        MAPPERS.computeIfAbsent(level.dimension(), k -> new Long2LongOpenHashMap()).put(pos.asLong(), at.asLong());
    }

    static void untrackMapper(ServerLevel level, BlockPos pos) {
        Long2LongOpenHashMap map = MAPPERS.get(level.dimension());
        if (map != null) {
            map.remove(pos.asLong());
        }
    }

    /**
     * Whether a mapper is within {@code range} blocks (square) of x/z: a Launch Computer, a Radar Array, or anything on
     * a Launch Control Station's network (its station and members: the station's map reaches round all of them).
     */
    static boolean mapperNear(ServerLevel level, int x, int z, int range) {
        Long2LongOpenHashMap map = MAPPERS.get(level.dimension());
        if (map == null) {
            return LaunchNetwork.of(level).memberNear(x, z, range);
        }
        for (long l : map.values()) {
            BlockPos p = BlockPos.of(l);
            if (Math.abs(p.getX() - x) <= range && Math.abs(p.getZ() - z) <= range) {
                return true;
            }
        }
        return LaunchNetwork.of(level).memberNear(x, z, range);
    }

    /**
     * The launch sites (silos, superweapons) within reach of a computer at {@code from} (where it is in the world),
     * nearest first; carried ones too, where they are now.
     */
    public static List<LaunchSite> silosNear(ServerLevel level, BlockPos from) {
        LongOpenHashSet set = SILOS.get(level.dimension());
        List<LaunchSite> out = new ArrayList<>();
        if (set == null) {
            return out;
        }
        for (long l : set.toLongArray()) {
            BlockPos p = BlockPos.of(l);
            LaunchSite silo = level.isLoaded(p) && level.getBlockEntity(p) instanceof LaunchSite s ? s : carried(level, p);
            if (silo != null && silo.worldBlockPos().distSqr(from) <= (double) LINK_RANGE * LINK_RANGE) {
                out.add(silo);
            }
        }
        out.sort((a, b) -> Double.compare(a.worldBlockPos().distSqr(from), b.worldBlockPos().distSqr(from)));
        return out;
    }
}
