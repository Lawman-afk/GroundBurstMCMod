package dev.groundburst.content.launch;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.GroundBurst;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The ground round the Launch Computers as a map (DESIGN.md §17.3): for every chunk loaded within {@link #RANGE} blocks
 * of a computer (by players, by missiles flying over, by anything), the colour of each column seen from above, shaded
 * by slope as vanilla maps are (one byte a column: map colour id x 4 + brightness). Kept with the world
 * ({@code data/groundburst/terrain_survey.dat}), so what has been seen stays on the map. Chunks are surveyed as they load
 * (a few a tick), when they unload, and again when a computer asks for them and they are loaded but stale (craters show).
 */
public final class TerrainSurvey extends SavedData {
    /**
     * How far round a computer the ground is mapped (blocks, square): a Liquid-fuel Stage's 2500 blocks from a silo, with
     * room for the computer standing some way off from it.
     */
    public static final int RANGE = 3000;
    /** Blocks a map pixel at each zoom step (32: the whole survey on the computer's screen). */
    public static final int[] SCALES = {1, 2, 4, 8, 16, 32};

    public static boolean validScale(int scale) {
        for (int s : SCALES) {
            if (s == scale) {
                return true;
            }
        }
        return false;
    }
    /** Map tiles are this many samples a side; a tile at scale s covers TILE * s blocks. */
    public static final int TILE = 64;
    private static final int QUEUE_PER_TICK = 32, FRESH_PER_TILE = 64, STALE_TICKS = 200;

    private record Entry(long chunk, ByteBuffer colours) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("c").forGetter(Entry::chunk), Codec.BYTE_BUFFER.fieldOf("d").forGetter(Entry::colours)).apply(i, Entry::new));
    }

    static final SavedDataType<TerrainSurvey> TYPE = new SavedDataType<>(GroundBurst.id("terrain_survey"), TerrainSurvey::new,
            Entry.CODEC.listOf().fieldOf("chunks").codec().xmap(list -> {
                TerrainSurvey s = new TerrainSurvey();
                for (Entry e : list) {
                    byte[] d = new byte[256];
                    e.colours().duplicate().get(d, 0, Math.min(256, e.colours().remaining()));
                    s.chunks.put(e.chunk(), d);
                }
                return s;
            }, s -> {
                List<Entry> out = new ArrayList<>(s.chunks.size());
                s.chunks.long2ObjectEntrySet().forEach(e -> out.add(new Entry(e.getLongKey(), ByteBuffer.wrap(e.getValue()))));
                return out;
            }));

    private final Long2ObjectOpenHashMap<byte[]> chunks = new Long2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap sampledAt = new Long2LongOpenHashMap();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();

    private TerrainSurvey() {}

    public static TerrainSurvey of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static boolean inRange(ServerLevel level, long chunk) {
        return Launches.mapperNear(level, ChunkPos.getX(chunk) * 16 + 8, ChunkPos.getZ(chunk) * 16 + 8, RANGE + 16);
    }

    /** A chunk loaded: survey it soon (once it is ticking). */
    void chunkLoaded(long chunk) {
        if (this.queued.add(chunk)) {
            this.queue.enqueue(chunk);
        }
    }

    /** A chunk about to unload: survey it now, as it is. */
    void chunkUnloading(ServerLevel level, LevelChunk chunk) {
        long c = chunk.getPos().pack();
        if (inRange(level, c)) {
            record(level, c, sample(chunk));
        }
    }

    /** A server tick: survey a few of the newly loaded chunks. */
    void tick(ServerLevel level) {
        for (int i = 0; i < QUEUE_PER_TICK && !this.queue.isEmpty(); i++) {
            long c = this.queue.dequeueLong();
            this.queued.remove(c);
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            if (chunk != null && inRange(level, c)) {
                record(level, c, sample(chunk));
            }
        }
    }

    private void record(ServerLevel level, long chunk, byte[] colours) {
        this.chunks.put(chunk, colours);
        this.sampledAt.put(chunk, level.getGameTime());
        setDirty();
    }

    /** Surveys every loaded chunk round a computer that has just formed (what it can already see). */
    void surveyAround(ServerLevel level, BlockPos at, int chunkRadius) {
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                if (level.getChunkSource().getChunkNow(x, z) != null) {
                    chunkLoaded(ChunkPos.pack(x, z));
                }
            }
        }
    }

    /**
     * A map tile's colours ({@link #TILE} squared, or empty if nothing in it is known), and whether loaded ground in it
     * was left out (too many chunks to survey at once): the screen asks again soon for a partial tile.
     */
    public record Tile(byte[] colours, boolean partial) {}

    /**
     * A map tile: {@link #TILE} x {@link #TILE} samples, each the column every {@code scale} blocks from the tile's corner
     * (tx, tz in tiles of that scale). 0 where nothing is known.
     */
    public Tile tile(ServerLevel level, int scale, int tx, int tz) {
        byte[] out = new byte[TILE * TILE];
        boolean any = false;
        int x0 = tx * TILE * scale, z0 = tz * TILE * scale;
        Long2ObjectOpenHashMap<byte @Nullable []> local = new Long2ObjectOpenHashMap<>();
        int[] fresh = {FRESH_PER_TILE};
        boolean[] partial = {false};
        for (int j = 0; j < TILE; j++) {
            for (int i = 0; i < TILE; i++) {
                int x = x0 + i * scale, z = z0 + j * scale;
                long c = ChunkPos.pack(x >> 4, z >> 4);
                byte[] d = local.containsKey(c) ? local.get(c) : chunkData(level, c, fresh, partial);
                local.put(c, d);
                if (d != null) {
                    out[j * TILE + i] = d[(z & 15) * 16 + (x & 15)];
                    any |= out[j * TILE + i] != 0;
                }
            }
        }
        return new Tile(any ? out : new byte[0], partial[0]);
    }

    private byte @Nullable [] chunkData(ServerLevel level, long c, int[] fresh, boolean[] partial) {
        byte[] stored = this.chunks.get(c);
        boolean stale = stored == null || level.getGameTime() - this.sampledAt.getOrDefault(c, Long.MIN_VALUE / 2) > STALE_TICKS;
        if (stale) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            if (chunk != null && fresh[0] <= 0) {
                partial[0] = true;                         // loaded, but out of budget this time
            } else if (chunk != null) {
                fresh[0]--;
                byte[] d = sample(chunk);
                if (inRange(level, c)) {
                    record(level, c, d);
                }
                return d;
            }
        }
        return stored;
    }

    /**
     * A chunk's columns seen from above, as a vanilla map shades them: the top block with a map colour, brighter where
     * the ground rises to the south and darker where it falls; water by its depth.
     */
    static byte[] sample(LevelChunk chunk) {
        byte[] out = new byte[256];
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ(), minY = chunk.getMinY();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int[] above = new int[16];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                p.set(minX + x, y, minZ + z);
                BlockState s = chunk.getBlockState(p);
                MapColor colour = s.getMapColor(chunk, p);
                while (colour == MapColor.NONE && y > minY) {
                    p.setY(--y);
                    s = chunk.getBlockState(p);
                    colour = s.getMapColor(chunk, p);
                }
                MapColor.Brightness b;
                if (!s.getFluidState().isEmpty()) {
                    int depth = 0;
                    while (depth < 10 && y - depth > minY && !chunk.getBlockState(p.setY(y - depth - 1)).getFluidState().isEmpty()) {
                        depth++;
                    }
                    double shade = depth * 0.1 + ((x + z) & 1) * 0.2;
                    b = shade < 0.5 ? MapColor.Brightness.HIGH : shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else if (z == 0) {
                    b = MapColor.Brightness.NORMAL;
                } else {
                    b = y > above[x] ? MapColor.Brightness.HIGH : y < above[x] ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                above[x] = y;
                out[z * 16 + x] = colour == MapColor.NONE ? 0 : (byte) colour.getPackedId(b);
            }
        }
        return out;
    }
}
