package dev.groundzero.blast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundzero.config.GZServerConfig;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * One running blast (DESIGN.md §11.2-11.4). The terrain front advances one shell (one block of radius) at a time, at
 * the profile's speed, inside the time and block budgets the {@link BlastManager} hands it each tick. The shock front
 * (what hits entities, and what clients draw) always runs at the profile's speed: when a big blast's terrain work falls
 * behind its budget, the crater goes on forming behind the shock (slice 8d, after benchmarking the Tsar).
 *
 * <p><b>Terrain.</b> Each ray carries energy. Inside its crater reach a ray destroys the blocks it enters while its
 * energy lasts, paying {@code 0.3 + resistance^0.75} per block (air costs little), so dense blocks shield what is
 * behind them; inside the core it destroys everything breakable for free. A ray's energy and reach are scaled by its
 * grain ({@link StreakNoise}), so the crater is a starburst: fingers past the mean rim, spikes in the floor. Past its
 * reach a surviving ray becomes a pressure ray: in the structural band it blows light blocks out of its way, and then
 * it stops at the first thing it cannot. Bedrock and other unbreakable blocks stop every ray (a shadow behind them). A
 * ray that is not heading down and has climbed above the highest block anywhere in the blast's reach can hit nothing
 * more, and is dropped. Rays split as the front reaches them ({@link RayField}).
 *
 * <p><b>Ground.</b> After each shell a ground pass looks at the ring of columns that far out, as the burst sees them:
 * a column hidden behind a nearer, higher one (a hill, a wall) is spared. Visible ground in the scorch band is
 * scorched, in spokes that follow the crater's fingers; in the fire band it may catch fire; and rubble from the crater
 * (cobblestone and gravel, dirt, sand: whatever was dug out) lands along the same spokes out to about twice the crater
 * radius.
 *
 * <p><b>Writing.</b> Blocks are set with flags that skip neighbour updates, drops, block-entity side effects (no
 * item spew) and on-place hooks; heightmaps, lighting and the batched client updates still happen.
 *
 * <p><b>After the front.</b> First a clean-up: any little cluster of blocks (up to {@link #FLOATER} blocks) the rays
 * left hanging in the air, attached to nothing, goes, so no lone block floats over the crater floor. Then the fires and
 * rubble the ground pass chose are put down, each only where its ground is still there: the crater reaches the ground
 * under a ring after the ring itself (it lies further off in 3D), so placing them as the ring passed left fire in mid
 * air. The clean-up also finds the crater's rim (every destroyed block next to one a ray reached and left standing);
 * last the rim gets ordinary neighbour updates, so sand and gravel fall and water flows in.
 *
 * <p><b>Entities.</b> As the shock front passes each entity it takes damage and knockback scaled by distance and by
 * exposure (how many of 4 lines from the burst to its body are unblocked: ground that is not loaded counts as open,
 * never loaded for it), and catches fire inside the fire radius. One the shock finds wholly shielded, where the terrain
 * work has yet to reach (the crater may still dig its cover away), is looked at again once it has. Items and experience
 * in the crater are destroyed.
 *
 * <p><b>Chunks and saving.</b> A blast that takes more than a tick keeps the chunks round its terrain front loaded
 * ({@link BlastChunks}; out to where its terrain work ends, not its entity reach) and is saved with the level ({@link State}): the rays, the front's radius and the crater rim,
 * so it carries on where it left off after a restart.
 */
final class BlastJob implements Blast {
    static final float AIR_COST = 0.05f;
    static final float FLUID_COST = 0.6f;
    /** Block set flags: tell clients, skip neighbour shape updates, drops, block-entity side effects and on-place hooks. */
    static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** How far down from the fireball's height the ground pass looks for a column's ground. */
    private static final int GROUND_SCAN = 64;
    /** Chunk tickets let go a tick once a blast is over. */
    private static final int RELEASE_PER_TICK = 64;
    /** The biggest cluster of blocks the clean-up removes when nothing holds it up. */
    static final int FLOATER = 4;

    /** FRONT: the fronts run; CLEAN: floaters and the rim; RIM: its neighbour updates; RELEASE: its chunks let go, a few a tick. */
    enum Phase { FRONT, CLEAN, RIM, RELEASE, DONE }

    final ServerLevel level;
    final UUID id;
    final Identifier profileId;
    /** Game time it went off (clients use it to time the sound of a blast they learn of late). */
    final long startTick;
    /** Short id for the client packets of this session. */
    int netId;
    final Vec3 center;
    final BlastProfile profile;
    final long seed;
    private final @Nullable Entity source;
    private final boolean terrain;
    private final TerrainTable table;
    private final @Nullable BlockPos protectedCenter;
    private final int protectedRadius;
    private final RandomSource random;
    /** The highest block anywhere within the terrain radius (loaded chunks): rays not heading down above it are done. */
    private int ceiling;
    /** The chunks round the front (none for an instant blast, which happens where it is, in loaded chunks). */
    private final @Nullable BlastChunks chunks;
    /** Seed of this blast's streak noise. */
    private final int streakSeed;
    /** A ray's grain by its direction. */
    private final RayField.DirectionalEnergy grain;
    /** Per azimuth, the steepest angle at which the burst has seen ground so far (the ground pass's horizon). */
    private final float[] horizon;
    /** What the crater has dug out, for the ejecta: stone, deepslate, soil, sand. */
    private final int[] dug = new int[4];

    private RayField rays;
    private int shell;
    private int cursor;
    private float entityRadius = -1;
    private int age;
    private Phase phase = Phase.FRONT;
    private final Long2ObjectOpenHashMap<long[]> visited = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet rim = new LongOpenHashSet();
    private @Nullable LongIterator rimCursor;
    /** The clean-up's place among the visited sections. */
    private it.unimi.dsi.fastutil.objects.@Nullable ObjectIterator<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<long[]>> cleanCursor;
    /** Fires and rubble the ground pass chose, put down after the front (see the class comment); and how far along. */
    private final it.unimi.dsi.fastutil.longs.LongArrayList fires = new it.unimi.dsi.fastutil.longs.LongArrayList();
    private final it.unimi.dsi.fastutil.longs.LongArrayList rubbleAt = new it.unimi.dsi.fastutil.longs.LongArrayList();
    private final List<BlockState> rubbleState = new java.util.ArrayList<>();
    private int placed;
    /** How many floating blocks the clean-up took away (for GameTests). */
    int floatersRemoved;
    private final IntOpenHashSet hit = new IntOpenHashSet();
    /** Entities the shock found shielded by ground the terrain work had not reached yet: looked at again once it has. */
    private final IntOpenHashSet waiting = new IntOpenHashSet();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int blocksChanged;

    BlastJob(ServerLevel level, UUID id, Identifier profileId, long startTick, Vec3 center, BlastProfile profile, long seed,
            @Nullable Entity source, boolean terrain, @Nullable BlockPos protectedCenter, int protectedRadius) {
        this.level = level;
        this.id = id;
        this.profileId = profileId;
        this.startTick = startTick;
        this.center = center;
        this.profile = profile;
        this.seed = seed;
        this.source = source;
        this.terrain = terrain;
        this.table = TerrainTable.byId(profile.terrain());
        this.protectedCenter = protectedCenter;
        this.protectedRadius = protectedRadius;
        this.random = RandomSource.create(seed);
        this.ceiling = highestBlock(level, center, profile.terrainRadius());
        this.chunks = profile.isInstant() ? null
                : new BlastChunks(level, id, center, workRadius(profile), GZServerConfig.GENERATE_CHUNKS.get(), this::raiseCeiling);
        this.streakSeed = StreakNoise.seed(seed);
        float streaks = profile.streaks();
        int noiseSeed = this.streakSeed;
        this.grain = streaks > 0 ? (dx, dy, dz) -> StreakNoise.factor(dx, dy, dz, noiseSeed, streaks) : (dx, dy, dz) -> 1;
        this.horizon = new float[Math.clamp((int) Math.ceil(2 * Math.PI * profile.groundRadius()), 720, 8192)];
        java.util.Arrays.fill(this.horizon, Float.NEGATIVE_INFINITY);
        Lobes lobes = new Lobes(seed, profile.lobes());
        float stone = cost(6f);
        float base = profile.crater() * stone;
        this.rays = RayField.start((dx, dy, dz) -> {
            float down = Math.max(0, -dy);
            return base * (1 - (1 - profile.craterDepth()) * down) * lobes.at(dx, dy, dz);
        }, this.grain);
    }

    /** A blast resumed from its saved state. */
    BlastJob(ServerLevel level, State state) {
        this(level, state.id(), state.profileId(), state.startTick(), state.center(), state.profile(), state.seed(), null,
                state.terrain(), state.protectedCenter().orElse(null), state.protectedRadius());
        this.rays = RayField.load(state.rays());
        this.shell = state.shell();
        this.age = state.age();
        this.entityRadius = state.entityRadius();
        this.ceiling = Math.max(this.ceiling, state.ceiling());
        this.phase = Phase.valueOf(state.phase());
        for (long p : state.rim().toArray()) {
            this.rim.add(p);
        }
        if (this.phase == Phase.RIM) {
            this.rimCursor = this.rim.iterator();
        }
        if (this.phase == Phase.CLEAN) {
            this.phase = Phase.RIM;                        // resumed: the visited sets were not saved, so nothing to clean
            this.rimCursor = this.rim.iterator();
        }
    }

    /** Everything needed to carry on after a restart. */
    State save() {
        return new State(this.id, this.profileId, this.profile, this.center, this.seed, this.terrain, Optional.ofNullable(this.protectedCenter),
                this.protectedRadius, this.shell, this.age, this.phase.name(), this.phase == Phase.FRONT ? this.rays.save() : java.nio.ByteBuffer.allocate(0),
                java.util.Arrays.stream(this.rim.toLongArray()), this.entityRadius, this.ceiling, this.startTick);
    }

    /** A saved blast. */
    record State(UUID id, Identifier profileId, BlastProfile profile, Vec3 center, long seed, boolean terrain,
            Optional<BlockPos> protectedCenter, int protectedRadius, int shell, int age, String phase, java.nio.ByteBuffer rays,
            java.util.stream.LongStream rim, float entityRadius, int ceiling, long startTick) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(State::id),
                Identifier.CODEC.fieldOf("profile_id").forGetter(State::profileId),
                BlastProfile.CODEC.fieldOf("profile").forGetter(State::profile),
                Vec3.CODEC.fieldOf("center").forGetter(State::center),
                Codec.LONG.fieldOf("seed").forGetter(State::seed),
                Codec.BOOL.fieldOf("terrain").forGetter(State::terrain),
                BlockPos.CODEC.optionalFieldOf("protected_center").forGetter(State::protectedCenter),
                Codec.INT.fieldOf("protected_radius").forGetter(State::protectedRadius),
                Codec.INT.fieldOf("shell").forGetter(State::shell),
                Codec.INT.fieldOf("age").forGetter(State::age),
                Codec.STRING.fieldOf("phase").forGetter(State::phase),
                Codec.BYTE_BUFFER.fieldOf("rays").forGetter(State::rays),
                Codec.LONG_STREAM.fieldOf("rim").forGetter(State::rim),
                Codec.FLOAT.fieldOf("entity_radius").forGetter(State::entityRadius),
                Codec.INT.fieldOf("ceiling").forGetter(State::ceiling),
                Codec.LONG.fieldOf("start_tick").forGetter(State::startTick)
        ).apply(i, State::new));
    }

    /** How far the terrain work runs: the crater, the bands beyond it and the ground pass. */
    private static float workRadius(BlastProfile p) {
        return Math.max(p.terrainRadius(), p.groundRadius() + 1);
    }

    private void raiseCeiling(int top) {
        this.ceiling = Math.max(this.ceiling, top);
    }

    /** The highest block in one chunk (for the ceiling, as far chunks load). */
    static int highestIn(net.minecraft.world.level.chunk.LevelChunk chunk) {
        int top = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                top = Math.max(top, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
            }
        }
        return top;
    }

    Phase phase() {
        return phase;
    }

    /**
     * The radius the shock front has reached (what clients draw the shockwave at): the profile's speed from the start,
     * never behind the terrain work.
     */
    @Override
    public int radius() {
        float outer = this.profile.outerRadius();
        float shock = this.profile.isInstant() || this.phase != Phase.FRONT ? outer : Math.min(outer, this.age * this.profile.frontSpeed());
        return (int) Math.max(this.shell, shock);
    }

    @Override
    public boolean isDone() {
        return this.phase == Phase.DONE;
    }

    @Override
    public int blocksChanged() {
        return this.blocksChanged;
    }

    @Override
    public Stats stats() {
        return new Stats(this.phase.name(), this.shell, this.rays.size, this.rays.room(), this.chunks == null ? 0 : this.chunks.forcedCount(),
                this.visited.size(), this.rim.size(), this.fires.size() + this.rubbleAt.size());
    }

    /** The top of the highest block in the loaded chunks within {@code radius} of {@code center} (horizontally). */
    private static int highestBlock(ServerLevel level, Vec3 center, float radius) {
        int top = level.getMinY();
        int r = (int) Math.ceil(radius) + 1;
        int cx0 = ((int) Math.floor(center.x) - r) >> 4, cx1 = ((int) Math.floor(center.x) + r) >> 4;
        int cz0 = ((int) Math.floor(center.z) - r) >> 4, cz1 = ((int) Math.floor(center.z) + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        top = Math.max(top, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                    }
                }
            }
        }
        return top;
    }

    /** Block cost of entering a block of this explosion resistance. */
    static float cost(float resistance) {
        return 0.3f + (float) Math.pow(resistance, 0.75);
    }

    /**
     * Advances the blast within the budget. Returns the number of block changes it made. Instant blasts ignore the
     * budget and finish in one call.
     */
    int step(long deadline, int blockBudget) {
        boolean instant = this.profile.isInstant();
        this.age++;
        int changesBefore = this.blocksChanged;
        float outer = this.profile.outerRadius(), work = workRadius(this.profile);
        float target = instant ? Float.MAX_VALUE : this.age * this.profile.frontSpeed();
        while (this.phase == Phase.FRONT && this.shell <= work && this.shell <= target) {
            if (this.terrain && this.chunks != null && !this.chunks.prepare(this.shell)) {
                break;                                     // its chunks are still loading: wait
            }
            if (this.terrain && this.shell <= this.profile.terrainRadius() && this.rays.size > 0) {
                int budget = instant ? Integer.MAX_VALUE : blockBudget - (this.blocksChanged - changesBefore);
                if (!runShell(this.shell, instant ? Long.MAX_VALUE : deadline, budget)) {
                    break;                                 // out of time or block budget: carry on next tick
                }
            }
            if (this.terrain && this.shell <= this.profile.groundRadius() + 1) {
                groundRing(this.shell);
            }
            this.shell++;
            this.cursor = 0;
            if (!instant && System.nanoTime() > deadline) {
                break;
            }
        }
        // The shock reaches entities at the profile's speed, however far behind the terrain work is.
        affectEntities(Math.min(Math.max(this.shell, target), outer));
        if (this.phase == Phase.FRONT && this.shell > work && target >= outer) {
            this.phase = Phase.CLEAN;
            this.rays = new RayField();                    // free the ray arrays
            this.cleanCursor = this.visited.long2ObjectEntrySet().fastIterator();
        }
        if (this.phase == Phase.CLEAN && clean(instant ? Long.MAX_VALUE : deadline) && placeDeferred(instant ? Long.MAX_VALUE : deadline)) {
            this.phase = Phase.RIM;
            this.rimCursor = this.rim.iterator();
        }
        if (this.phase == Phase.RIM) {
            updateRim(instant ? Long.MAX_VALUE : deadline);
        }
        if (this.phase == Phase.RELEASE && (this.chunks == null || this.chunks.release(RELEASE_PER_TICK))) {
            this.phase = Phase.DONE;                       // (a Tsar holds hundreds: let go at once they stalled a tick)
        }
        return this.blocksChanged - changesBefore;
    }

    // --- terrain ---------------------------------------------------------------------------------------------------------------
    /** Runs (the rest of) one shell. False if it stopped early for the budget. */
    private boolean runShell(int r, long deadline, int budget) {
        RayField f = this.rays;
        int startChanges = this.blocksChanged;
        int maxY = this.level.getMaxY(), minY = this.level.getMinY();
        float crater = this.profile.crater(), core = this.profile.core();
        while (this.cursor < f.size) {
            if ((this.cursor & 255) == 0 && (System.nanoTime() > deadline || this.blocksChanged - startChanges >= budget)) {
                return false;
            }
            int k = this.cursor++;
            if (f.mode[k] == RayField.DEAD) {
                continue;
            }
            if (f.needsSplit(k, r)) {
                f.split(k, this.grain);                    // its four children are added at the end: this shell reaches them later
                continue;
            }
            int bx = (int) Math.floor(this.center.x + f.dx[k] * r);
            int by = (int) Math.floor(this.center.y + f.dy[k] * r);
            int bz = (int) Math.floor(this.center.z + f.dz[k] * r);
            if (by < minY || by > maxY) {
                f.kill(k);
                continue;
            }
            this.pos.set(bx, by, bz);
            if (!this.level.isLoaded(this.pos)) {
                f.kill(k);                                 // (5b: chunk tickets keep the band loaded)
                continue;
            }
            BlockState state = this.level.getBlockState(this.pos);
            if (state.isAir()) {
                f.energy[k] -= AIR_COST;
                // Above everything there is to hit in the whole blast, and not coming down: nothing more to do.
                // (Not the ground under the ray itself: a tree or a hill further out may still be in its path.)
                if (f.dy[k] >= 0 && by > this.ceiling) {
                    f.kill(k);
                }
                continue;
            }
            if (!markVisited(bx, by, bz)) {
                // Another ray has dealt with this block already: whatever is left there blocks this one.
                f.kill(k);
                continue;
            }
            float reach = crater * f.grain[k];
            if (f.mode[k] == RayField.CRATER && r <= reach) {
                crater(f, k, r, state, r <= core);
            } else {
                f.mode[k] = RayField.PRESSURE;
                pressure(f, k, r, state);
            }
            if (f.mode[k] == RayField.CRATER && r + 1 > reach) {
                f.mode[k] = RayField.PRESSURE;
            }
        }
        if (f.dead * 4 > f.size) {
            f.compact();                                   // only once a quarter of them are dead: it costs a pass over them all
        }
        return true;
    }

    private void crater(RayField f, int k, int r, BlockState state, boolean inCore) {
        if (isProtected() || state.getDestroySpeed(this.level, this.pos) < 0) {
            f.kill(k);                                     // bedrock, barriers, protected spawn: a shadow behind
            return;
        }
        boolean liquid = state.getBlock() instanceof LiquidBlock;
        float c = liquid ? FLUID_COST : cost(state.getBlock().getExplosionResistance());
        if (!inCore) {
            if (f.energy[k] < c) {
                f.kill(k);
                return;
            }
            f.energy[k] -= c;
        }
        destroy(state);
    }

    private void pressure(RayField f, int k, int r, BlockState state) {
        if (isProtected() || !state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock) {
            f.kill(k);
            return;
        }
        if (r <= this.profile.structural() && this.table.blowsOut(state)) {
            write(this.pos, AIR);                          // blown out; the shock carries on through the gap
            return;
        }
        f.kill(k);                                         // (the ground pass scorches and sets fires)
    }

    // --- ground pass -----------------------------------------------------------------------------------------------------------
    /** The columns whose centres lie more than {@code r - 1} and at most {@code r} blocks out (horizontally). */
    private void groundRing(int r) {
        double cx = this.center.x, cz = this.center.z;
        for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++) {
            double ddx = x + 0.5 - cx;
            double outer2 = (double) r * r - ddx * ddx;
            if (outer2 < 0) {
                continue;
            }
            double outer = Math.sqrt(outer2);
            double inner2 = (double) (r - 1) * (r - 1) - ddx * ddx;
            double inner = inner2 > 0 ? Math.sqrt(inner2) : -1;
            int z1 = (int) Math.floor(cz - 0.5 + outer) + 1;
            for (int z = (int) Math.ceil(cz - 0.5 - outer) - 1; z <= z1; z++) {
                double ddz = z + 0.5 - cz;
                if (inner >= 0 && Math.abs(ddz) < inner - 1) {
                    z = Math.max(z, (int) Math.floor(cz - 0.5 + inner) - 1);   // skip the inside of the ring
                    continue;
                }
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (Math.ceil(dist) == r) {
                    groundColumn(x, z, (float) dist);
                }
            }
        }
    }

    private void groundColumn(int x, int z, float dist) {
        this.pos.set(x, this.level.getMinY(), z);
        if (!this.level.isLoaded(this.pos)) {
            return;
        }
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (top < this.level.getMinY()) {
            return;
        }
        BlastProfile p = this.profile;
        // Seen from the fireball, a little above the burst (so a charge set just below the surface still sees round it).
        double eye = this.center.y + Math.max(1.5, 0.25 * p.crater());
        // The column's ground is its highest solid block not above the fireball: indoors and in caves that is the
        // floor under the roof (and a wall at the fireball's height stands in the way of what is behind it).
        int from = Math.min(top, (int) Math.floor(eye));
        top = from;
        while (top >= this.level.getMinY() && from - top < GROUND_SCAN
                && !Heightmap.Types.MOTION_BLOCKING.isOpaque().test(this.level.getBlockState(this.pos.set(x, top, z)))) {
            top--;
        }
        if (top < this.level.getMinY() || from - top >= GROUND_SCAN) {
            return;
        }
        double az = Math.atan2(z + 0.5 - this.center.z, x + 0.5 - this.center.x);
        int n = this.horizon.length;
        int b = (int) ((az + Math.PI) / (2 * Math.PI) * n) % n;
        float angle = (float) Math.atan2(top + 1 - eye, Math.max(dist, 0.5f));
        boolean visible = angle >= this.horizon[b] - 0.02f;
        // The column hides what is behind it across its whole width (half a block either side of its centre line).
        int half = (int) Math.min(n / 8, Math.ceil(0.5 / Math.max(dist, 0.5f) / (2 * Math.PI) * n));
        for (int i = -half; i <= half; i++) {
            int j = Math.floorMod(b + i, n);
            this.horizon[j] = Math.max(this.horizon[j], angle);
        }
        this.pos.set(x, top, z);
        // Hidden from the burst, dug out by the crater, sure to be (deep inside it), or protected: nothing to do.
        if (!visible || isDestroyed(x, top + 1, z) || dist < p.crater() * 0.45f || isProtected()) {
            return;
        }
        float spoke = StreakNoise.at((float) Math.cos(az), 0, (float) Math.sin(az), this.streakSeed);
        float edge = p.streaks() > 0 ? 0.72f + 0.6f * spoke * (1 + p.streaks()) : 1;
        BlockPos above = this.pos.above();
        // Water (and lava, kelp, anything holding fluid) is a surface to the burst but not ground: rubble sinks through
        // it and nothing catches fire on it.
        boolean wet = !this.level.getBlockState(this.pos).getFluidState().isEmpty();
        if (dist <= p.scorch() && dist / p.scorch() < edge && this.random.nextFloat() > 0.08f) {
            BlockState scorched = this.table.scorched(this.level.getBlockState(this.pos));
            if (scorched != null) {
                write(this.pos, scorched);
                if (this.table.blowsOut(this.level.getBlockState(above))) {
                    write(above, AIR);                     // grass and flowers burnt off with it
                }
            }
        }
        if (!wet && dist <= p.fire() && this.random.nextFloat() < 0.6f * (1 - dist / Math.max(1, p.fire()))) {
            this.fires.add(above.asLong());                // put down after the front, if its ground is still there
        }
        if (!wet && p.ejecta() > 0 && dist >= p.crater() * 0.8f && dist <= p.crater() * 1.9f) {
            float t = Math.max(0, (dist - p.crater()) / (0.9f * p.crater()));
            float chance = p.ejecta() * Math.max(0, spoke + 0.05f) * 3 * (float) Math.pow(1 - t, 1.5);
            if (this.random.nextFloat() < chance) {
                BlockState rubble = rubble(this.level.getBlockState(this.pos));
                if (rubble != null) {
                    this.rubbleAt.add(above.asLong());     // put down after the front, if its ground is still there
                    this.rubbleState.add(rubble);
                }
            }
        }
    }

    /** Which of the {@link #dug} kinds a block is, or -1. */
    private static int kind(BlockState state) {
        if (state.is(Blocks.DEEPSLATE) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            return 1;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.BlockItems.GRAVELS.block()) || state.is(Tags.BlockItems.COBBLESTONES.block())) {
            return 0;
        }
        if (state.is(BlockTags.DIRT)) {
            return 2;
        }
        if (state.is(BlockTags.SAND)) {
            return 3;
        }
        return -1;
    }

    /**
     * A rubble block thrown out of the crater, in proportion to what it dug out (or, for a blast resumed after a
     * restart, from the ground it lands on).
     */
    private @Nullable BlockState rubble(BlockState ground) {
        int total = this.dug[0] + this.dug[1] + this.dug[2] + this.dug[3];
        int kind;
        if (total > 0) {
            int pick = this.random.nextInt(total);
            kind = 0;
            while (pick >= this.dug[kind]) {
                pick -= this.dug[kind++];
            }
        } else {
            kind = kind(ground);
        }
        boolean coarse = this.random.nextFloat() < 0.4f;
        return switch (kind) {
            case 0 -> (coarse ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState();
            case 1 -> (coarse ? Blocks.GRAVEL : Blocks.COBBLED_DEEPSLATE).defaultBlockState();
            case 2 -> (coarse ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
            case 3 -> Blocks.SAND.defaultBlockState();
            default -> null;
        };
    }

    /** Destroys the block at {@link #pos} (maybe dropping it). */
    private void destroy(BlockState state) {
        int kind = this.profile.ejecta() > 0 ? kind(state) : -1;
        if (kind >= 0) {
            this.dug[kind]++;
        }
        if (state.getBlock() instanceof dev.groundzero.content.ordnance.charge.ChargeBlock) {
            // A charge caught in the blast goes off a moment later (sympathetic detonation).
            dev.groundzero.content.ordnance.charge.Charges.caughtInBlast(this.level, this.pos.immutable(), state);
        }
        if (this.profile.drops() > 0 && this.random.nextFloat() < this.profile.drops()) {
            Block.dropResources(state, this.level, this.pos, this.level.getBlockEntity(this.pos));
        }
        write(this.pos, AIR);
        markDestroyed(this.pos.getX(), this.pos.getY(), this.pos.getZ());
    }

    private void write(BlockPos at, BlockState state) {
        if (this.level.setBlock(at, state, WRITE_FLAGS)) {
            this.blocksChanged++;
        }
    }

    private boolean isProtected() {
        return this.protectedCenter != null && Math.abs(this.pos.getX() - this.protectedCenter.getX()) <= this.protectedRadius
                && Math.abs(this.pos.getZ() - this.protectedCenter.getZ()) <= this.protectedRadius;
    }

    /**
     * After the front: every block a ray reached but did not destroy is looked at. The destroyed blocks next to it are
     * the crater's rim (they get neighbour updates last); and if it belongs to a little cluster (up to {@link #FLOATER}
     * blocks) that touches no other solid block, the cluster goes. True when done. (The rim used to be gathered as
     * blocks were destroyed, which took in nearly every destroyed block: 20 million for the Tsar.)
     */
    private boolean clean(long deadline) {
        var it = this.cleanCursor;
        int n = 0;
        while (it != null && it.hasNext()) {
            if ((++n & 15) == 0 && System.nanoTime() > deadline) {
                return false;
            }
            var entry = it.next();
            long key = entry.getLongKey();
            long[] words = entry.getValue();
            int sx = SectionPos.x(key) << 4, sy = SectionPos.y(key) << 4, sz = SectionPos.z(key) << 4;
            for (int w = 0; w < 64; w++) {
                long survivors = words[w] & ~words[64 + w];
                while (survivors != 0) {
                    int b = (w << 6) | Long.numberOfTrailingZeros(survivors);
                    survivors &= survivors - 1;
                    int x = sx + (b & 15), y = sy + (b >> 8), z = sz + ((b >> 4) & 15);
                    for (Direction d : Direction.values()) {
                        int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                        if (isDestroyed(nx, ny, nz)) {
                            this.rim.add(BlockPos.asLong(nx, ny, nz));
                        }
                    }
                    dropIfFloating(x, y, z);
                }
            }
        }
        this.cleanCursor = null;
        return true;
    }

    /** Solid for the clean-up: anything but air and liquids (a plant counts: it goes with its block). */
    private boolean solid(BlockPos at) {
        if (!this.level.isLoaded(at)) {
            return true;                                   // unknown: treat it as holding things up
        }
        BlockState s = this.level.getBlockState(at);
        return !s.isAir() && !(s.getBlock() instanceof LiquidBlock);
    }

    /** Removes the cluster of solid blocks round (x, y, z) if it has at most FLOATER blocks (it hangs in the air). */
    private void dropIfFloating(int x, int y, int z) {
        BlockPos start = new BlockPos(x, y, z);
        if (!solid(start) || !this.level.isLoaded(start)) {
            return;
        }
        long[] cluster = new long[FLOATER];
        int size = 0;
        cluster[size++] = start.asLong();
        for (int i = 0; i < size; i++) {
            BlockPos at = BlockPos.of(cluster[i]);
            for (Direction d : Direction.values()) {
                BlockPos next = at.relative(d);
                if (!this.level.isLoaded(next)) {
                    return;                                // runs into ground we cannot see: assume it is held up
                }
                if (!solid(next)) {
                    continue;
                }
                long l = next.asLong();
                boolean seen = false;
                for (int j = 0; j < size; j++) {
                    seen |= cluster[j] == l;
                }
                if (seen) {
                    continue;
                }
                if (size == FLOATER) {
                    return;                                // bigger than a floater: attached to something, leave it
                }
                cluster[size++] = l;
            }
        }
        for (int i = 0; i < size; i++) {
            BlockPos at = BlockPos.of(cluster[i]);
            this.pos.set(at);
            BlockState s = this.level.getBlockState(at);
            if (isProtected() || s.getDestroySpeed(this.level, at) < 0 || s.hasBlockEntity()) {
                return;                                    // something worth keeping (or unbreakable): leave the lot
            }
        }
        for (int i = 0; i < size; i++) {
            BlockPos at = BlockPos.of(cluster[i]);
            write(at, AIR);
            long[] words = this.visited.get(key(at.getX(), at.getY(), at.getZ()));
            if (words != null) {
                int b = bit(at.getX(), at.getY(), at.getZ());
                words[64 + (b >> 6)] |= 1L << (b & 63);   // destroyed (no new section is added mid-iteration)
            }
            this.floatersRemoved++;
        }
    }

    /** Puts down the fires and rubble the ground pass chose, each only if its ground is still there. True when done. */
    private boolean placeDeferred(long deadline) {
        int total = this.fires.size() + this.rubbleAt.size();
        while (this.placed < total) {
            if ((this.placed & 63) == 0 && System.nanoTime() > deadline) {
                return false;
            }
            int i = this.placed++;
            if (i < this.fires.size()) {
                BlockPos at = BlockPos.of(this.fires.getLong(i));
                if (this.level.isLoaded(at) && this.level.getBlockState(at).isAir() && BaseFireBlock.canBePlacedAt(this.level, at, Direction.UP)
                        && this.level.getBlockState(at.below()).isFaceSturdy(this.level, at.below(), Direction.UP)) {
                    write(at, BaseFireBlock.getState(this.level, at));
                }
            } else {
                int j = i - this.fires.size();
                BlockPos at = BlockPos.of(this.rubbleAt.getLong(j));
                BlockState here = this.level.isLoaded(at) ? this.level.getBlockState(at) : null;
                if (here != null && (here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty())
                        && this.level.getBlockState(at.below()).isFaceSturdy(this.level, at.below(), Direction.UP)) {
                    write(at, this.rubbleState.get(j));
                }
            }
        }
        this.fires.clear();
        this.rubbleAt.clear();
        this.rubbleState.clear();
        return true;
    }

    /** After the front: ordinary neighbour updates round the crater's edge (sand falls, water flows in). */
    private void updateRim(long deadline) {
        LongIterator it = this.rimCursor;
        int n = 0;
        while (it != null && it.hasNext()) {
            if ((++n & 127) == 0 && System.nanoTime() > deadline) {
                return;
            }
            BlockPos p = BlockPos.of(it.nextLong());
            if (this.level.isLoaded(p) && this.level.getBlockState(p).isAir()) {
                this.level.updateNeighborsAt(p, Blocks.AIR);
            }
        }
        this.phase = Phase.RELEASE;
    }

    // --- visited / destroyed bitsets, one pair of 4096-bit sets per chunk section ------------------------------------------
    private static long key(int x, int y, int z) {
        return SectionPos.asLong(x >> 4, y >> 4, z >> 4);
    }

    private static int bit(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    /** Marks a block visited; false if it already was. Words 0-63 hold "visited", 64-127 "destroyed". */
    private boolean markVisited(int x, int y, int z) {
        long[] words = this.visited.computeIfAbsent(key(x, y, z), k -> new long[128]);
        int b = bit(x, y, z);
        long mask = 1L << (b & 63);
        if ((words[b >> 6] & mask) != 0) {
            return false;
        }
        words[b >> 6] |= mask;
        return true;
    }

    private void markDestroyed(int x, int y, int z) {
        long[] words = this.visited.computeIfAbsent(key(x, y, z), k -> new long[128]);
        int b = bit(x, y, z);
        words[64 + (b >> 6)] |= 1L << (b & 63);
    }

    private boolean isDestroyed(int x, int y, int z) {
        long[] words = this.visited.get(key(x, y, z));
        int b = bit(x, y, z);
        return words != null && (words[64 + (b >> 6)] & (1L << (b & 63))) != 0;
    }

    // --- entities --------------------------------------------------------------------------------------------------------------
    /**
     * Hits every entity the shock has reached since the last call (up to {@code r}); and those it found shielded where
     * the terrain work has since caught up with them.
     */
    private void affectEntities(float r) {
        BlastProfile p = this.profile;
        float reach = Math.max(Math.max(p.damage(), p.lethal()), Math.max(p.crater(), p.fire()));
        boolean grows = r > this.entityRadius && this.entityRadius < reach;
        if (!grows && this.waiting.isEmpty()) {
            return;
        }
        float to = grows ? Math.min(r, reach) : this.entityRadius;
        this.entityRadius = to;
        // How far the terrain work has got (all the way once the fronts are done), and how far it will go.
        float dug = this.phase == Phase.FRONT ? this.shell : Float.MAX_VALUE, work = workRadius(p);
        // Every loaded entity, not a box query: a big blast's box covers thousands of empty chunk sections.
        List<Entity> entities = new java.util.ArrayList<>();
        for (Entity e : this.level.getAllEntities()) {
            if (e == null || e.isSpectator() || this.hit.contains(e.getId())) {
                continue;
            }
            double d2 = e.position().distanceToSqr(this.center);
            boolean waits = this.waiting.contains(e.getId());
            // New ones inside the shock; waiting ones once the terrain work has passed them (wherever they have got to).
            if (waits ? d2 <= (double) dug * dug : grows && d2 <= (double) to * to) {
                entities.add(e);
            }
        }
        for (Entity e : entities) {
            double d = e.position().distanceTo(this.center);
            if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
                this.hit.add(e.getId());
                this.waiting.remove(e.getId());
                if (d <= p.crater()) {
                    e.discard();
                }
                continue;
            }
            float exposure = exposure(e);
            if (exposure <= 0 && d > dug && d < work) {
                this.waiting.add(e.getId());               // its cover may yet be dug away: look again when the work gets there
                continue;
            }
            this.hit.add(e.getId());
            this.waiting.remove(e.getId());
            if (exposure <= 0) {
                continue;
            }
            float dmg = 0;
            if (d <= p.lethal()) {
                dmg = p.lethalDamage() * exposure;
            } else if (d <= p.damage()) {
                dmg = p.edgeDamage() * (float) (1 - (d - p.lethal()) / Math.max(1e-3, p.damage() - p.lethal())) * exposure;
            }
            if (dmg > 0 && e instanceof LivingEntity) {
                e.hurtServer(this.level, Blasts.damageSource(this.level, this.source, this.center), dmg);
            }
            float falloff = (float) Math.max(0, 1 - d / Math.max(1, Math.max(p.damage(), p.lethal())));
            if (falloff > 0) {
                Vec3 push = e.position().subtract(this.center).add(0, 0.5, 0).normalize().scale(p.knockback() * falloff * exposure);
                e.push(push.x, push.y, push.z);           // marks the entity for a velocity sync
            }
            if (d <= p.fire()) {
                e.igniteForSeconds(2 + 8 * (float) (1 - d / Math.max(1, p.fire())));
            }
        }
    }

    /** Share of 4 lines from the burst to the entity's body that no block stops (DESIGN.md §11.4). */
    private float exposure(Entity e) {
        AABB b = e.getBoundingBox();
        Vec3[] points = {b.getCenter(), new Vec3(b.getCenter().x, b.maxY - 0.1, b.getCenter().z),
                new Vec3(b.minX + 0.05, b.minY + 0.1, b.minZ + 0.05), new Vec3(b.maxX - 0.05, b.minY + 0.1, b.maxZ - 0.05)};
        int open = 0;
        for (Vec3 to : points) {
            if (clear(this.center, to)) {
                open++;
            }
        }
        return open / 4f;
    }

    /**
     * Whether no block's collision shape stands between two points. Ground that is not loaded counts as open: a line of
     * sight never loads a chunk (a level's own clip would, on the server thread).
     */
    private boolean clear(Vec3 from, Vec3 to) {
        return net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, this.level, (level, at) -> {
            if (!level.isLoaded(at)) {
                return null;
            }
            BlockState state = level.getBlockState(at);
            if (state.isAir()) {
                return null;
            }
            net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, at);
            return shape.isEmpty() || shape.clip(from, to, at) == null ? null : Boolean.FALSE;
        }, level -> Boolean.TRUE);
    }

    /** Smooth, seeded variation of ray energy over the sphere: a lobed, irregular crater rim. */
    private record Lobes(float[][] axes, float[] freq, float[] phase, float amplitude) {
        Lobes(long seed, float amplitude) {
            this(new float[4][], new float[4], new float[4], amplitude);
            RandomSource r = RandomSource.create(seed ^ 0x5DEECE66DL);
            for (int i = 0; i < 4; i++) {
                double x = r.nextGaussian(), y = r.nextGaussian(), z = r.nextGaussian();
                double n = Math.sqrt(x * x + y * y + z * z);
                this.axes[i] = new float[] {(float) (x / n), (float) (y / n), (float) (z / n)};
                this.freq[i] = 2 + r.nextFloat() * 3;
                this.phase[i] = r.nextFloat() * 6.283f;
            }
        }

        float at(float dx, float dy, float dz) {
            float sum = 0;
            for (int i = 0; i < 4; i++) {
                float dot = dx * this.axes[i][0] + dy * this.axes[i][1] + dz * this.axes[i][2];
                sum += (float) Math.sin(this.freq[i] * dot + this.phase[i]);
            }
            return Math.max(0.2f, 1 + this.amplitude * sum / 4);
        }
    }
}
