package dev.groundburst.content.machine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The machines something carries out of the world (DESIGN.md §17.16: a Create contraption's), known by their key, the
 * place each was taken from, while their carrier ticks them: so a screen, a menu or a packet that names that place finds
 * the machine wherever it has been carried. On the client, another mod's support finds the copy it draws
 * ({@link #addClientFinder}).
 */
public final class Carried {
    /** A carried machine its carrier has not ticked for this long has gone (set down, unloaded), ticks. */
    private static final int GONE = 4;
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Seen>> SEEN = new HashMap<>();
    private static final List<ClientFinder> CLIENT_FINDERS = new CopyOnWriteArrayList<>();

    private record Seen(MachineBlockEntity machine, long time) {}

    /** Something that finds, on the client, the copy of a carried machine it draws by its key. */
    @FunctionalInterface
    public interface ClientFinder {
        @Nullable BlockEntity find(Level level, BlockPos key);
    }

    private Carried() {}

    public static void addClientFinder(ClientFinder finder) {
        CLIENT_FINDERS.add(finder);
    }

    /** Its carrier ticked it: known by its key until it stops. */
    public static void seen(ServerLevel level, MachineBlockEntity machine) {
        SEEN.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>())
                .put(machine.getBlockPos().asLong(), new Seen(machine, level.getGameTime()));
    }

    /** Its carrier let it go (set it down, or is gone). */
    public static void gone(ServerLevel level, BlockPos key) {
        Long2ObjectOpenHashMap<Seen> seen = SEEN.get(level.dimension());
        if (seen != null) {
            seen.remove(key.asLong());
        }
    }

    public static void clear() {
        SEEN.clear();
    }

    /** The machine carried with its key at {@code key}, if its carrier ticked it just now (on the client: the copy drawn). */
    public static @Nullable BlockEntity at(Level level, BlockPos key) {
        if (((Object) level) instanceof ServerLevel server) {
            Long2ObjectOpenHashMap<Seen> seen = SEEN.get(server.dimension());
            Seen s = seen == null ? null : seen.get(key.asLong());
            if (s == null) {
                return null;
            }
            if (server.getGameTime() - s.time > GONE || !s.machine.carried()) {
                seen.remove(key.asLong());
                return null;
            }
            return s.machine;
        }
        for (ClientFinder finder : CLIENT_FINDERS) {
            BlockEntity be = finder.find(level, key);
            if (be != null) {
                return be;
            }
        }
        return null;
    }

    /** The block entity of {@code type} with its key at {@code pos}: standing there, else one carried from there. */
    public static <T> @Nullable T blockEntity(Level level, BlockPos pos, Class<T> type) {
        BlockEntity there = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
        if (type.isInstance(there)) {
            return type.cast(there);
        }
        BlockEntity carried = at(level, pos);
        return type.isInstance(carried) ? type.cast(carried) : null;
    }

    /**
     * Where the block entity with its key at {@code pos} is in the world (the one standing there, carried from there, or
     * on a Sable ship): the middle of its block.
     */
    public static Vec3 centre(Level level, BlockPos pos) {
        MachineBlockEntity m = blockEntity(level, pos, MachineBlockEntity.class);
        return m != null ? m.inWorld(Vec3.atCenterOf(m.getBlockPos())) : Frames.toWorld(level, Vec3.atCenterOf(pos));
    }

    /** Whether {@code player} is within {@code reach} of the block entity with its key at {@code pos}, wherever it is. */
    public static boolean near(Player player, BlockPos pos, double reach) {
        return player.position().distanceTo(centre(player.level(), pos)) <= reach;
    }
}
