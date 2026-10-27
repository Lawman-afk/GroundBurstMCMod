package dev.groundzero.content.fallout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.blast.BlastProfile;
import dev.groundzero.config.GZServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The wasteland a nuclear blast leaves (DESIGN.md §21.7, slice 12e). Once a nuclear blast has finished its crater (its
 * chunks still held), two jobs run over the next ticks, a share a tick:
 * <ul>
 * <li><b>The biome.</b> The Nuclear Wasteland (grey-brown grass and leaves, dead-olive water, a dusty sky, brown fog, ash
 * drifting down, no animals) is painted over the scorched ground, from a little under the lowest ground in each chunk
 * up (deep caves keep their own biomes), and the chunks are sent again (the server's {@code convertBiomes} switch).</li>
 * <li><b>The crater lake.</b> A crater floods if water could get into it: it broke into the sea, a lake or a river (it
 * fills to that water's level), or its floor lies under the sea level where the land is wet (groundwater; not in dry
 * biomes, not where water boils away). The water stays under the lowest point of the rim all round (traced on rays
 * out from the middle), and a giant crater fills only to a pond in its deepest part ({@link #MAX_LAKE} blocks).</li>
 * </ul>
 * Neither is saved: a server stopped in the middle of one leaves it half done.
 */
public final class Wasteland {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, GroundZero.id("nuclear_wasteland"));
    /** Chunks painted a tick; lake water placed a tick (blocks). */
    static final int CHUNKS_PER_TICK = 48, WATER_PER_TICK = 6000;
    /** How far under a chunk's lowest ground the biome reaches (caves deeper down keep theirs). */
    static final int DEPTH = 24;
    /** The smallest crater that floods, its radius in blocks; the most water a lake may hold. */
    public static final float LAKE_MIN_CRATER = 6;
    public static final int MAX_LAKE = 300_000;
    /** The shallowest a lake may be at its deepest point (blocks). */
    static final int MIN_DEPTH = 2;

    interface Job {
        /** One tick's share; true once done. */
        boolean step(ServerLevel level);
    }

    private static final Map<ResourceKey<Level>, ArrayDeque<Job>> JOBS = new HashMap<>();

    private Wasteland() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> JOBS.clear());
    }

    /**
     * A nuclear blast at {@code centre} has finished its crater: the wasteland over its scorched ground, and a lake in
     * its crater if the land would fill it. Called while its chunks are still held.
     */
    public static void afterBlast(ServerLevel level, Vec3 centre, BlastProfile profile, boolean crater) {
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;                                        // the GameTests paint and flood what they test themselves
        }
        float radius = Math.max(profile.scorch(), profile.crater() * 1.5f);
        if (GZServerConfig.CONVERT_BIOMES.get() && radius >= 4) {
            queue(level, new Painting(level, centre, radius));
        }
        if (crater && profile.crater() >= LAKE_MIN_CRATER) {
            Job lake = Lake.plan(level, centre, profile.crater());
            if (lake != null) {
                queue(level, lake);
            }
        }
    }

    /** Paints the wasteland over a disc now (GameTests). */
    public static void paintNow(ServerLevel level, Vec3 centre, float radius) {
        Painting p = new Painting(level, centre, radius);
        while (!p.step(level)) {
            // all of it
        }
    }

    /** Floods the crater at {@code centre} now, if it would flood; the water level, or {@code Integer.MIN_VALUE} if dry (GameTests). */
    public static int floodNow(ServerLevel level, Vec3 centre, float crater) {
        Lake lake = Lake.plan(level, centre, crater);
        if (lake == null) {
            return Integer.MIN_VALUE;
        }
        while (!lake.step(level)) {
            // all of it
        }
        return lake.water();
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

    // --- the biome -----------------------------------------------------------------------------------------------------------
    /** Paints the wasteland over a disc, a few chunks a tick (only chunks loaded now: the blast holds its own). */
    static final class Painting implements Job {
        final double cx, cz;
        final float radius;
        final long[] chunks;
        int next;

        Painting(ServerLevel level, Vec3 centre, float radius) {
            this.cx = centre.x;
            this.cz = centre.z;
            this.radius = radius;
            List<Long> list = new ArrayList<>();
            int r = Mth.ceil(radius);
            for (int x = SectionPos.blockToSectionCoord(cx - r); x <= SectionPos.blockToSectionCoord(cx + r); x++) {
                for (int z = SectionPos.blockToSectionCoord(cz - r); z <= SectionPos.blockToSectionCoord(cz + r); z++) {
                    double nx = Mth.clamp(cx, x * 16, x * 16 + 16), nz = Mth.clamp(cz, z * 16, z * 16 + 16);
                    if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= radius * radius) {
                        list.add(net.minecraft.world.level.ChunkPos.pack(x, z));
                    }
                }
            }
            this.chunks = list.stream().mapToLong(Long::longValue).toArray();
        }

        @Override
        public boolean step(ServerLevel level) {
            Holder<Biome> wasteland = level.registryAccess().lookupOrThrow(Registries.BIOME).get(BIOME).orElse(null);
            if (wasteland == null) {
                return true;
            }
            List<ChunkAccess> changed = new ArrayList<>();
            int end = Math.min(this.chunks.length, this.next + CHUNKS_PER_TICK);
            for (; this.next < end; this.next++) {
                long key = this.chunks[this.next];
                LevelChunk chunk = level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(key),
                        net.minecraft.world.level.ChunkPos.getZ(key));
                if (chunk != null && paint(chunk, wasteland)) {
                    changed.add(chunk);
                }
            }
            if (!changed.isEmpty()) {
                level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
            }
            return this.next >= this.chunks.length;
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
     * Fills a crater with still water up to {@link #water}, column by column inside its shore: on each ray out from the
     * middle the shore is where the rim stands highest, and a column fills only if it lies inside the shore on its own
     * ray and the rays either side (so the water meets the rim, never the land past it).
     */
    static final class Lake implements Job {
        final int cx, cz, water, extent, rays;
        final float[] shore;
        int cursor, columns;

        Lake(int cx, int cz, int water, int extent, float[] shore) {
            this.cx = cx;
            this.cz = cz;
            this.water = water;
            this.extent = extent;
            this.rays = shore.length;
            this.shore = shore;
            this.columns = (2 * extent + 1) * (2 * extent + 1);
        }

        /** The lake a crater of radius {@code crater} at {@code centre} would hold, or null if it stays dry. */
        static @Nullable Lake plan(ServerLevel level, Vec3 centre, float crater) {
            int cx = Mth.floor(centre.x), cz = Mth.floor(centre.z);
            BlockPos middle = new BlockPos(cx, Mth.floor(centre.y), cz);
            if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, middle)) {
                return null;                                   // water boils away here (the Nether)
            }
            float reach = crater * 1.8f;
            int extent = Mth.ceil(reach) + 1;
            int rays = Mth.clamp(Mth.ceil(2 * Math.PI * reach), 64, 4096);
            // The floor: the lowest ground in the crater's middle.
            int floor = Integer.MAX_VALUE;
            for (int dx = -Mth.ceil(crater * 0.6f); dx <= crater * 0.6f; dx += 2) {
                for (int dz = -Mth.ceil(crater * 0.6f); dz <= crater * 0.6f; dz += 2) {
                    if (dx * dx + dz * dz <= crater * crater * 0.36f) {
                        floor = Math.min(floor, surface(level, cx + dx, cz + dz));
                    }
                }
            }
            // The rim: on each ray, where the ground stands highest out past the crater's edge; the lowest of those is
            // the most the water may rise to. Water found out there (the sea, a lake, a river) is what floods it.
            float[] shore = new float[rays];
            int rim = Integer.MAX_VALUE, wet = Integer.MIN_VALUE;
            for (int i = 0; i < rays; i++) {
                double a = 2 * Math.PI * i / rays, ux = Math.cos(a), uz = Math.sin(a);
                int crest = Integer.MIN_VALUE;
                float at = reach;
                for (float d = crater * 0.85f; d <= reach; d += 0.75f) {
                    int x = Mth.floor(cx + 0.5 + ux * d), z = Mth.floor(cz + 0.5 + uz * d);
                    if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                        return null;                           // not all of it loaded: leave it dry rather than guess
                    }
                    int y = surface(level, x, z);
                    if (y > crest) {
                        crest = y;
                        at = d;
                    }
                    BlockState top = level.getBlockState(new BlockPos(x, y, z));
                    if (top.getFluidState().isSource() && top.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                        wet = Math.max(wet, y);
                    }
                }
                shore[i] = at;
                rim = Math.min(rim, crest);
            }
            int seaLevel = level.getSeaLevel();
            Holder<Biome> biome = level.getBiome(middle);
            boolean groundwater = level.dimension() == Level.OVERWORLD && biome.value().hasPrecipitation() && floor < seaLevel - 1;
            int water;
            if (wet != Integer.MIN_VALUE) {
                water = Math.min(wet, rim - 1);
            } else if (groundwater) {
                water = Math.min(seaLevel - 1, rim - 1);
            } else {
                return null;
            }
            // A giant crater: only as much water as a pond in its deepest part.
            while (water - floor >= MIN_DEPTH && volume(level, cx, cz, water, crater) > MAX_LAKE) {
                water--;
            }
            if (water - floor < MIN_DEPTH || rim - floor < 3) {
                return null;                                   // too shallow, or no basin at all (an airburst's flat ground)
            }
            return new Lake(cx, cz, water, extent, shore);
        }

        /** About how much water the crater holds up to {@code water} (sampled every other column). */
        private static int volume(ServerLevel level, int cx, int cz, int water, float crater) {
            int total = 0, r = Mth.ceil(crater * 1.2f);
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (dx * dx + dz * dz <= r * r) {
                        total += Math.max(0, water - surface(level, cx + dx, cz + dz)) * 4;
                    }
                }
            }
            return total;
        }

        /** The y of the top block of a column (fluids count). */
        static int surface(ServerLevel level, int x, int z) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        }

        private float shoreAt(double angle) {
            int i = Math.floorMod((int) Math.round(angle / (2 * Math.PI) * this.rays), this.rays);
            return Math.min(this.shore[i], Math.min(this.shore[Math.floorMod(i - 1, this.rays)], this.shore[(i + 1) % this.rays]));
        }

        @Override
        public boolean step(ServerLevel level) {
            int placed = 0;
            BlockState still = Blocks.WATER.defaultBlockState();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            int side = 2 * this.extent + 1;
            while (this.cursor < this.columns && placed < WATER_PER_TICK) {
                int dx = this.cursor % side - this.extent, dz = this.cursor / side - this.extent;
                this.cursor++;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= shoreAt(Math.atan2(dz, dx))) {
                    continue;
                }
                int x = this.cx + dx, z = this.cz + dz;
                if (!level.hasChunkAt(at.set(x, 0, z))) {
                    continue;
                }
                for (int y = surface(level, x, z) + 1; y <= this.water; y++) {
                    at.set(x, y, z);
                    BlockState s = level.getBlockState(at);
                    if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(BlockTags.FIRE)
                            || s.is(BlockTags.FIRE)) {
                        level.setBlock(at, still, Block.UPDATE_CLIENTS);
                        placed++;
                    }
                }
            }
            return this.cursor >= this.columns;
        }

        int water() {
            return this.water;
        }
    }

}
