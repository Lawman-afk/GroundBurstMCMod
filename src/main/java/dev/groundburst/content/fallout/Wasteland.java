package dev.groundburst.content.fallout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.BlastProfile;
import dev.groundburst.config.GBServerConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The wasteland a nuclear blast leaves (DESIGN.md §21.7, slice 12e). Once a nuclear blast has finished its crater, two
 * jobs run over the next ticks, a share a tick, each loading the chunks it works in a batch at a time and letting them
 * go again (the blast let them go as its front passed: a missile fired at a far target bursts with nobody near):
 * <ul>
 * <li><b>The biome.</b> The Nuclear Wasteland (grey-brown grass and leaves, dead-olive water, a dusty sky, brown fog, ash
 * drifting down, no animals) is painted over the scorched ground, from a little under the lowest ground in each chunk
 * up (deep caves keep their own biomes), and the chunks are sent again (the server's {@code convertBiomes} switch).</li>
 * <li><b>The crater lake.</b> A crater floods if water could get into it: it broke into the sea, a lake or a river (it
 * fills to that water's level), or its floor lies under the sea level where the land is wet (groundwater; not in dry
 * biomes, not where water boils away). The water stays under the lowest point of the rim all round (traced on rays
 * out from the middle), and a giant crater fills only to a pond in its deepest part ({@link #MAX_LAKE} blocks). The
 * ground is read first (every column's height, into grids), the lake planned from that (the basin the water would
 * fill, found from the crater's middle out), then filled.</li>
 * </ul>
 * Only chunks the blast saw are loaded, so none is generated anew. Neither job is saved: a server stopped in the middle
 * of one leaves it half done (and its chunk tickets are dropped when the world loads again).
 */
public final class Wasteland {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, GroundBurst.id("nuclear_wasteland"));
    /** Chunks painted a tick; chunks read a tick for a lake; lake water placed a tick (blocks). */
    static final int CHUNKS_PER_TICK = 48, READ_PER_TICK = 64, WATER_PER_TICK = 6000;
    /** How far under a chunk's lowest ground the biome reaches (caves deeper down keep theirs). */
    static final int DEPTH = 24;
    /** The smallest crater that floods, its radius in blocks; the most water a lake may hold. */
    public static final float LAKE_MIN_CRATER = 6;
    public static final int MAX_LAKE = 300_000;
    /** The shallowest a lake may be at its deepest point (blocks). */
    static final int MIN_DEPTH = 2;
    /** Ticks a batch waits for a chunk to load before going on without it. */
    static final int LOAD_PATIENCE = 400;

    /** Holds the chunks a job works in (not ticking); tickets a stopped server left are dropped when the world loads. */
    static final TicketController TICKETS = new TicketController(GroundBurst.id("wasteland"), (level, tickets) -> {
        for (UUID owner : List.copyOf(tickets.getEntityTickets().keySet())) {
            tickets.removeAllTickets(owner);
        }
    });

    interface Job {
        /** One tick's share; true once done. */
        boolean step(ServerLevel level);
    }

    private static final Map<ResourceKey<Level>, ArrayDeque<Job>> JOBS = new HashMap<>();

    private Wasteland() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TICKETS));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> JOBS.clear());
    }

    /**
     * A nuclear blast at {@code centre} has finished its crater: the wasteland over its scorched ground, and a lake in
     * its crater if the land would fill it. {@code existing}: the chunks the blast saw (the only ones loaded for the
     * work), or null to work in the chunks loaded now.
     */
    public static void afterBlast(ServerLevel level, Vec3 centre, BlastProfile profile, boolean crater, @Nullable LongSet existing) {
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;                                        // the GameTests paint and flood what they test themselves
        }
        float radius = Math.max(profile.scorch(), profile.crater() * 1.5f);
        if (GBServerConfig.CONVERT_BIOMES.get() && radius >= 4) {
            queue(level, new Painting(centre, radius, existing));
        }
        if (crater && profile.crater() >= LAKE_MIN_CRATER) {
            Lake lake = Lake.start(level, centre, profile.crater(), existing);
            if (lake != null) {
                queue(level, lake);
            }
        }
    }

    /** Paints the wasteland over a disc now, in the chunks loaded (GameTests). */
    public static void paintNow(ServerLevel level, Vec3 centre, float radius) {
        Painting p = new Painting(centre, radius, null);
        while (!p.step(level)) {
            // all of it
        }
    }

    /**
     * Floods the crater at {@code centre} now, if it would flood, in the chunks loaded; the water level, or
     * {@code Integer.MIN_VALUE} if it stays dry (GameTests).
     */
    public static int floodNow(ServerLevel level, Vec3 centre, float crater) {
        Lake lake = Lake.start(level, centre, crater, null);
        if (lake == null) {
            return Integer.MIN_VALUE;
        }
        while (!lake.step(level)) {
            // all of it
        }
        return lake.water;
    }

    /** Whether no wasteland work is waiting in this level (the world survey waits for it). */
    public static boolean idle(ServerLevel level) {
        ArrayDeque<Job> jobs = JOBS.get(level.dimension());
        return jobs == null || jobs.isEmpty();
    }

    static void queue(ServerLevel level, Job job) {
        JOBS.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).add(job);
    }

    private static void tick(ServerLevel level) {
        ArrayDeque<Job> jobs = JOBS.get(level.dimension());
        if (jobs != null && !jobs.isEmpty() && jobs.peek().step(level)) {
            jobs.poll();
        }
    }

    /** The chunks touching a disc, nearest first. */
    static long[] disc(double cx, double cz, float radius) {
        LongArrayList list = new LongArrayList();
        int r = Mth.ceil(radius);
        for (int x = SectionPos.blockToSectionCoord(cx - r); x <= SectionPos.blockToSectionCoord(cx + r); x++) {
            for (int z = SectionPos.blockToSectionCoord(cz - r); z <= SectionPos.blockToSectionCoord(cz + r); z++) {
                double nx = Mth.clamp(cx, x * 16, x * 16 + 16), nz = Mth.clamp(cz, z * 16, z * 16 + 16);
                if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= (double) radius * radius) {
                    list.add(ChunkPos.pack(x, z));
                }
            }
        }
        return java.util.Arrays.stream(list.toLongArray()).boxed()
                .sorted(java.util.Comparator.comparingDouble(k -> Math.hypot(ChunkPos.getX(k) * 16 + 8 - cx, ChunkPos.getZ(k) * 16 + 8 - cz)))
                .mapToLong(Long::longValue).toArray();
    }

    /**
     * A job's chunks, a batch at a time. With {@code existing}, each batch's chunks that the blast saw are held loaded
     * while the job works in them and let go after; a chunk that does not come in time ({@link #LOAD_PATIENCE}) is
     * passed over. Without, the batch is the chunks loaded now, at once.
     */
    static final class Batches {
        final long[] keys;
        final @Nullable LongSet existing;
        final UUID owner = UUID.randomUUID();
        int next, end, waited;
        boolean asked;

        Batches(long[] keys, @Nullable LongSet existing) {
            this.keys = keys;
            this.existing = existing;
        }

        /** The current batch's loaded chunks once it is ready, null while it loads. */
        @Nullable List<LevelChunk> current(ServerLevel level, int size) {
            if (!this.asked) {
                this.end = Math.min(this.keys.length, this.next + size);
                this.waited = 0;
                this.asked = true;
                if (this.existing != null) {
                    for (int i = this.next; i < this.end; i++) {
                        long k = this.keys[i];
                        if (this.existing.contains(k)) {
                            TICKETS.forceChunk(level, this.owner, ChunkPos.getX(k), ChunkPos.getZ(k), true, false);
                        }
                    }
                }
            }
            List<LevelChunk> ready = new ArrayList<>(this.end - this.next);
            boolean waiting = false;
            for (int i = this.next; i < this.end; i++) {
                long k = this.keys[i];
                LevelChunk c = level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k));
                if (c != null) {
                    ready.add(c);
                } else if (this.existing != null && this.existing.contains(k)) {
                    waiting = true;
                }
            }
            if (waiting && ++this.waited < LOAD_PATIENCE) {
                return null;
            }
            return ready;
        }

        /** Done with the current batch: its chunks let go, on to the next. */
        void advance(ServerLevel level) {
            if (this.existing != null) {
                for (int i = this.next; i < this.end; i++) {
                    long k = this.keys[i];
                    if (this.existing.contains(k)) {
                        TICKETS.forceChunk(level, this.owner, ChunkPos.getX(k), ChunkPos.getZ(k), false, false);
                    }
                }
            }
            this.next = this.end;
            this.asked = false;
        }

        boolean done() {
            return this.next >= this.keys.length;
        }

        /** Back to the first batch (a second pass over the same chunks). */
        void rewind() {
            this.next = 0;
            this.end = 0;
            this.asked = false;
        }
    }

    // --- the biome -----------------------------------------------------------------------------------------------------------
    /** Paints the wasteland over a disc, a batch of chunks a tick. */
    static final class Painting implements Job {
        final double cx, cz;
        final float radius;
        final Batches chunks;

        Painting(Vec3 centre, float radius, @Nullable LongSet existing) {
            this.cx = centre.x;
            this.cz = centre.z;
            this.radius = radius;
            this.chunks = new Batches(disc(this.cx, this.cz, radius), existing);
        }

        @Override
        public boolean step(ServerLevel level) {
            Holder<Biome> wasteland = level.registryAccess().lookupOrThrow(Registries.BIOME).get(BIOME).orElse(null);
            if (wasteland == null || this.chunks.done()) {
                return true;
            }
            List<LevelChunk> batch = this.chunks.current(level, CHUNKS_PER_TICK);
            if (batch == null) {
                return false;                              // still loading
            }
            List<ChunkAccess> changed = new ArrayList<>();
            for (LevelChunk chunk : batch) {
                if (paint(chunk, wasteland)) {
                    changed.add(chunk);
                }
            }
            if (!changed.isEmpty()) {
                level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
            }
            this.chunks.advance(level);
            return this.chunks.done();
        }

        /** Paints one chunk's cells inside the disc, from a little under its lowest ground there up. True if any changed. */
        private boolean paint(LevelChunk chunk, Holder<Biome> wasteland) {
            int low = Integer.MAX_VALUE;
            int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
            for (int dx = 0; dx < 16; dx += 3) {
                for (int dz = 0; dz < 16; dz += 3) {
                    if (inside(x0 + dx + 0.5, z0 + dz + 0.5)) {
                        low = Math.min(low, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz));
                    }
                }
            }
            if (low == Integer.MAX_VALUE) {
                low = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            }
            int floor = low - DEPTH;
            boolean[] any = {false};
            chunk.fillBiomesFromNoise((qx, qy, qz) -> {
                Holder<Biome> now = chunk.getNoiseBiome(qx, qy, qz);
                double bx = QuartPos.toBlock(qx) + 2, bz = QuartPos.toBlock(qz) + 2;
                if (QuartPos.toBlock(qy) + 3 >= floor && inside(bx, bz) && !now.is(BIOME)) {
                    any[0] = true;
                    return wasteland;
                }
                return now;
            });
            if (any[0]) {
                chunk.markUnsaved();
            }
            return any[0];
        }

        private boolean inside(double x, double z) {
            return (x - this.cx) * (x - this.cx) + (z - this.cz) * (z - this.cz) <= this.radius * this.radius;
        }
    }

    // --- the crater lake -------------------------------------------------------------------------------------------------------
    /**
     * A crater lake, in passes over the chunks round the crater: <b>read</b> (each column's top block, whether it is water,
     * and its solid ground, into grids), <b>plan</b> (from the grids: the floor, the rim on each ray out from the middle,
     * the water level, and the lake: every column whose ground lies under the water and is joined to the crater's middle,
     * so the water fills the basin to its shore and nothing past it; should the lake run out past the rim, through a gap
     * the rays missed, or hold more than {@link #MAX_LAKE}, the level comes down until it does not), <b>fill</b> (still
     * water from each lake column's ground up to the level).
     */
    static final class Lake implements Job {
        enum Pass { READ, FILL, DONE }

        static final short UNKNOWN = Short.MIN_VALUE;

        final int cx, cz, extent, side, rays;
        final float crater, reach;
        /** Whether rain falls on the land here (read before the wasteland is painted over it). */
        final boolean rains;
        /** Each column's top block (a fluid counts, leaves do not) and its solid ground (fluids do not count); {@link #UNKNOWN} unread. */
        final short[] tops, beds;
        final BitSet wet;
        final Batches chunks;
        BitSet lake = new BitSet();
        int water = Integer.MIN_VALUE;
        Pass pass = Pass.READ;
        /** In the fill: the chunk of the current batch reached, and the column in it. */
        int chunkCursor, columnCursor;

        private Lake(int cx, int cz, float crater, boolean rains, @Nullable LongSet existing) {
            this.cx = cx;
            this.cz = cz;
            this.crater = crater;
            this.reach = crater * 1.8f;
            this.extent = Mth.ceil(this.reach) + 1;
            this.side = 2 * this.extent + 1;
            this.rays = Mth.clamp(Mth.ceil(2 * Math.PI * this.reach), 64, 4096);
            this.rains = rains;
            this.tops = new short[this.side * this.side];
            this.beds = new short[this.side * this.side];
            java.util.Arrays.fill(this.tops, UNKNOWN);
            java.util.Arrays.fill(this.beds, UNKNOWN);
            this.wet = new BitSet(this.side * this.side);
            this.chunks = new Batches(disc(cx + 0.5, cz + 0.5, this.extent * 1.42f), existing);
        }

        /** The lake job for a crater of radius {@code crater} at {@code centre}, or null where water cannot stay (it boils). */
        static @Nullable Lake start(ServerLevel level, Vec3 centre, float crater, @Nullable LongSet existing) {
            int cx = Mth.floor(centre.x), cz = Mth.floor(centre.z);
            BlockPos middle = new BlockPos(cx, Mth.floor(centre.y), cz);
            if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, middle)) {
                return null;                                   // water boils away here (the Nether)
            }
            boolean rains = level.dimension() == Level.OVERWORLD && level.getBiome(middle).value().hasPrecipitation();
            return new Lake(cx, cz, crater, rains, existing);
        }

        private int index(int x, int z) {
            int dx = x - this.cx + this.extent, dz = z - this.cz + this.extent;
            return dx < 0 || dz < 0 || dx >= this.side || dz >= this.side ? -1 : dz * this.side + dx;
        }

        /** The top block's y at a column (a fluid counts), {@link #UNKNOWN} if it was not read. */
        private int top(int x, int z) {
            int i = index(x, z);
            return i < 0 ? UNKNOWN : this.tops[i];
        }

        @Override
        public boolean step(ServerLevel level) {
            switch (this.pass) {
                case READ -> {
                    List<LevelChunk> batch = this.chunks.current(level, READ_PER_TICK);
                    if (batch == null) {
                        return false;
                    }
                    for (LevelChunk chunk : batch) {
                        read(chunk);
                    }
                    this.chunks.advance(level);
                    if (this.chunks.done()) {
                        if (!plan(level)) {
                            this.pass = Pass.DONE;
                            return true;
                        }
                        this.chunks.rewind();
                        this.pass = Pass.FILL;
                    }
                    return false;
                }
                case FILL -> {
                    return fill(level);
                }
                default -> {
                    return true;
                }
            }
        }

        private void read(LevelChunk chunk) {
            int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int i = index(x0 + dx, z0 + dz);
                    if (i < 0) {
                        continue;
                    }
                    int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
                    this.tops[i] = (short) y;
                    this.beds[i] = (short) chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, dx, dz);
                    var fluid = chunk.getFluidState(at.set(x0 + dx, y, z0 + dz));
                    if (fluid.isSource() && fluid.is(FluidTags.WATER)) {
                        this.wet.set(i);
                    }
                }
            }
        }

        /** Plans the lake from the grids; false if it stays dry. */
        private boolean plan(ServerLevel level) {
            float crater = this.crater;
            // The floor: the lowest solid ground in the crater's middle.
            int floor = Integer.MAX_VALUE;
            for (int dx = -Mth.ceil(crater * 0.6f); dx <= crater * 0.6f; dx++) {
                for (int dz = -Mth.ceil(crater * 0.6f); dz <= crater * 0.6f; dz++) {
                    int i = index(this.cx + dx, this.cz + dz);
                    if (dx * dx + dz * dz <= crater * crater * 0.36f && i >= 0 && this.beds[i] != UNKNOWN) {
                        floor = Math.min(floor, this.beds[i]);
                    }
                }
            }
            if (floor == Integer.MAX_VALUE) {
                return false;
            }
            // The rim: on each ray, where the ground stands highest out past the crater's edge; the lowest of those is
            // the most the water may rise to. Water found out there (the sea, a lake, a river) is what floods it.
            int rim = Integer.MAX_VALUE, wetTop = Integer.MIN_VALUE;
            for (int i = 0; i < this.rays; i++) {
                double a = 2 * Math.PI * i / this.rays, ux = Math.cos(a), uz = Math.sin(a);
                int crest = Integer.MIN_VALUE;
                for (float d = crater * 0.85f; d <= this.reach; d += 0.75f) {
                    int x = Mth.floor(this.cx + 0.5 + ux * d), z = Mth.floor(this.cz + 0.5 + uz * d);
                    int y = top(x, z);
                    if (y == UNKNOWN) {
                        return false;                          // ground that could not be read: leave it dry rather than guess
                    }
                    crest = Math.max(crest, y);
                    if (this.wet.get(index(x, z))) {
                        wetTop = Math.max(wetTop, y);
                    }
                }
                rim = Math.min(rim, crest);
            }
            if (rim - floor < 3) {
                return false;                                  // no basin at all (an airburst's flat ground)
            }
            int seaLevel = level.getSeaLevel();
            int most;
            if (wetTop != Integer.MIN_VALUE) {
                most = Math.min(wetTop, rim - 1);
            } else if (this.rains && floor < seaLevel - 1) {
                most = Math.min(seaLevel - 1, rim - 1);       // groundwater
            } else {
                return false;
            }
            // The highest level whose lake stays inside the rim and holds no more than a pond's worth (both only grow
            // with the level, so halving the range finds it).
            int lo = floor + MIN_DEPTH, hi = most;
            int[] queue = new int[this.side * this.side];
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                BitSet basin = basin(mid, queue);
                if (basin != null && volume(basin, mid) <= MAX_LAKE) {
                    this.lake = basin;
                    this.water = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return this.water != Integer.MIN_VALUE;
        }

        /**
         * The columns whose ground lies under {@code level} and that are joined to the crater's middle, or null if they
         * run out past the reach (the water would pour over the rim) or into ground that could not be read.
         */
        private @Nullable BitSet basin(int level, int[] queue) {
            BitSet seen = new BitSet(this.side * this.side);
            int head = 0, tail = 0;
            float inner = this.crater * 0.6f;
            for (int dx = -Mth.ceil(inner); dx <= inner; dx++) {
                for (int dz = -Mth.ceil(inner); dz <= inner; dz++) {
                    int i = index(this.cx + dx, this.cz + dz);
                    if (dx * dx + dz * dz <= inner * inner && i >= 0 && this.beds[i] != UNKNOWN && this.beds[i] < level && !seen.get(i)) {
                        seen.set(i);
                        queue[tail++] = i;
                    }
                }
            }
            float reach2 = this.reach * this.reach;
            while (head < tail) {
                int i = queue[head++];
                int gx = i % this.side, gz = i / this.side;
                int dx = gx - this.extent, dz = gz - this.extent;
                if (dx * dx + dz * dz >= reach2) {
                    return null;
                }
                for (int k = 0; k < 4; k++) {
                    int nx = gx + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = gz + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || nz < 0 || nx >= this.side || nz >= this.side) {
                        return null;
                    }
                    int n = nz * this.side + nx;
                    if (seen.get(n)) {
                        continue;
                    }
                    int bed = this.beds[n];
                    if (bed == UNKNOWN) {
                        return null;
                    }
                    if (bed < level) {
                        seen.set(n);
                        queue[tail++] = n;
                    }
                }
            }
            return seen;
        }

        /** The water a lake at {@code level} holds, in blocks. */
        private long volume(BitSet basin, int level) {
            long total = 0;
            for (int i = basin.nextSetBit(0); i >= 0; i = basin.nextSetBit(i + 1)) {
                total += level - this.beds[i];
            }
            return total;
        }

        /** Fills the lake's columns in the current batch's chunks, {@link #WATER_PER_TICK} blocks a tick; true once every batch is done. */
        private boolean fill(ServerLevel level) {
            List<LevelChunk> batch = this.chunks.current(level, CHUNKS_PER_TICK);
            if (batch == null) {
                return false;
            }
            int placed = 0;
            BlockState still = Blocks.WATER.defaultBlockState();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            while (this.chunkCursor < batch.size() && placed < WATER_PER_TICK) {
                LevelChunk chunk = batch.get(this.chunkCursor);
                int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
                while (this.columnCursor < 256 && placed < WATER_PER_TICK) {
                    int x = x0 + (this.columnCursor & 15), z = z0 + (this.columnCursor >> 4);
                    this.columnCursor++;
                    int i = index(x, z);
                    if (i < 0 || !this.lake.get(i)) {
                        continue;
                    }
                    for (int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1; y <= this.water; y++) {
                        at.set(x, y, z);
                        BlockState s = chunk.getBlockState(at);
                        if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty() || s.is(BlockTags.FIRE)) {
                            level.setBlock(at, still, Block.UPDATE_CLIENTS);
                            placed++;
                        }
                    }
                }
                if (this.columnCursor >= 256) {
                    this.columnCursor = 0;
                    this.chunkCursor++;
                }
            }
            if (this.chunkCursor < batch.size()) {
                return false;                                  // this batch again next tick
            }
            this.chunkCursor = 0;
            this.chunks.advance(level);
            if (this.chunks.done()) {
                this.pass = Pass.DONE;
                return true;
            }
            return false;
        }
    }
}
