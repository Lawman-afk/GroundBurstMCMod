package dev.groundburst.blast;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.groundburst.GroundBurst;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Keeps the chunks a blast is working in loaded (DESIGN.md §11.5). The active band is the ring of chunk columns from
 * 16 blocks behind the front to 32 ahead of it: those are force-loaded (without ticking) and released once the front
 * has passed, so a blast finishes with nobody nearby. With {@code blast.generateChunks} off (the default), chunks that
 * were never generated are left alone: whether one exists is read from disk in the background, and the front waits
 * the tick or two that takes. Tickets left over from a crash are dropped when the world loads; a resumed blast takes
 * them again.
 */
final class BlastChunks {
    static final TicketController TICKETS = new TicketController(GroundBurst.id("blast"), (level, tickets) -> {
        for (UUID owner : java.util.List.copyOf(tickets.getEntityTickets().keySet())) {
            tickets.removeAllTickets(owner);
        }
    });
    private static final int BEHIND = 16, AHEAD = 32;

    private enum Known { EXISTS, MISSING }

    private final ServerLevel level;
    private final UUID owner;
    private final Vec3 center;
    private final float outer;
    private final boolean generate;
    private final LongOpenHashSet forced = new LongOpenHashSet();
    /** What the disk said about chunks that were not loaded (filled in from the IO thread). */
    private final Map<Long, Known> known = new ConcurrentHashMap<>();
    private final LongOpenHashSet asked = new LongOpenHashSet();
    /** Told the height of each chunk's highest block the first time it is seen loaded (the blast's ray ceiling). */
    private final java.util.function.IntConsumer heights;
    private final LongOpenHashSet measured = new LongOpenHashSet();

    BlastChunks(ServerLevel level, UUID owner, Vec3 center, float outer, boolean generate, java.util.function.IntConsumer heights) {
        this.level = level;
        this.owner = owner;
        this.center = center;
        this.outer = outer;
        this.generate = generate;
        this.heights = heights;
    }

    /** Nearest and farthest horizontal distance from the burst to a chunk column. */
    private double[] range(int cx, int cz) {
        double x0 = cx * 16, x1 = x0 + 16, z0 = cz * 16, z1 = z0 + 16;
        double nx = Math.max(x0, Math.min(this.center.x, x1)), nz = Math.max(z0, Math.min(this.center.z, z1));
        double fx = Math.abs(this.center.x - x0) > Math.abs(this.center.x - x1) ? x0 : x1;
        double fz = Math.abs(this.center.z - z0) > Math.abs(this.center.z - z1) ? z0 : z1;
        return new double[] {Math.hypot(nx - this.center.x, nz - this.center.z), Math.hypot(fx - this.center.x, fz - this.center.z)};
    }

    /**
     * Brings the band round radius {@code r} up to date and says whether the shell at {@code r} can run: every chunk it
     * touches is loaded or known not to exist.
     */
    boolean prepare(int r) {
        int reach = (int) Math.ceil(Math.min(this.outer, r + AHEAD)) + 16;
        int cx0 = ((int) Math.floor(this.center.x) - reach) >> 4, cx1 = ((int) Math.floor(this.center.x) + reach) >> 4;
        int cz0 = ((int) Math.floor(this.center.z) - reach) >> 4, cz1 = ((int) Math.floor(this.center.z) + reach) >> 4;
        LongOpenHashSet wanted = new LongOpenHashSet();
        boolean ready = true;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                double[] d = range(cx, cz);
                if (d[0] > this.outer || d[1] < r - BEHIND || d[0] > r + AHEAD) {
                    continue;
                }
                long key = ChunkPos.pack(cx, cz);
                net.minecraft.world.level.chunk.LevelChunk chunk = this.level.getChunkSource().getChunkNow(cx, cz);
                boolean loaded = chunk != null;
                if (loaded && this.measured.add(key)) {
                    this.heights.accept(BlastJob.highestIn(chunk));
                }
                Known k = this.known.get(key);
                if (loaded || this.generate || k == Known.EXISTS) {
                    wanted.add(key);
                } else if (k == null && this.asked.add(key)) {
                    this.level.getChunkSource().chunkMap.read(new ChunkPos(cx, cz)).thenAccept(tag -> this.known.put(key,
                            tag.isPresent() && tag.get().getStringOr("Status", "").endsWith("full") ? Known.EXISTS : Known.MISSING))
                            .exceptionally(error -> {
                                this.known.put(key, Known.MISSING);       // unreadable: treat as not there
                                return null;
                            });
                }
                // The shell at r needs its chunks settled: loaded, or known to be missing.
                if (d[0] <= r + 1 && d[1] >= r - 1 && !loaded && k != Known.MISSING) {
                    ready = false;
                }
            }
        }
        for (LongIterator it = wanted.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if (this.forced.add(key)) {
                TICKETS.forceChunk(this.level, this.owner, ChunkPos.getX(key), ChunkPos.getZ(key), true, false);
            }
        }
        for (LongIterator it = this.forced.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if (!wanted.contains(key)) {
                TICKETS.forceChunk(this.level, this.owner, ChunkPos.getX(key), ChunkPos.getZ(key), false, false);
                it.remove();
            }
        }
        return ready;
    }

    /** The chunks it has seen loaded: every one in its reach that exists (with chunk generation off, no others). */
    it.unimi.dsi.fastutil.longs.LongSet seen() {
        return new LongOpenHashSet(this.measured);
    }

    /** How many chunks it keeps loaded now. */
    int forcedCount() {
        return this.forced.size();
    }

    /** Lets up to {@code n} chunks go (the blast is finished); true once none is held. */
    boolean release(int n) {
        LongIterator it = this.forced.iterator();
        for (int i = 0; i < n && it.hasNext(); i++) {
            long key = it.nextLong();
            TICKETS.forceChunk(this.level, this.owner, ChunkPos.getX(key), ChunkPos.getZ(key), false, false);
            it.remove();
        }
        return this.forced.isEmpty();
    }
}
