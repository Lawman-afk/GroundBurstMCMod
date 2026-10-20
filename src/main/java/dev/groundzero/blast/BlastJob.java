package dev.groundzero.blast;

import java.util.List;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One running blast (DESIGN.md §11.2-11.4). The front advances one shell (one block of radius) at a time, at the
 * profile's speed, inside the time and block budgets the {@link BlastManager} hands it each tick.
 *
 * <p><b>Terrain.</b> Each ray carries energy. Inside the crater radius a ray destroys the blocks it enters while its
 * energy lasts, paying {@code 0.3 + resistance^0.75} per block (air costs little), so dense blocks shield what is
 * behind them; inside the core it destroys everything breakable for free. Past the crater a surviving ray becomes a
 * pressure ray: in the structural band it blows light blocks out of its way, in the scorch band it scorches the first
 * surface it hits, in the fire band it may set that surface alight, and then it stops. Bedrock and other unbreakable
 * blocks stop every ray (a shadow behind them). A ray climbing above the terrain, with nothing left to hit, is dropped.
 *
 * <p><b>Writing.</b> Blocks are set with flags that skip neighbour updates, drops, block-entity side effects (no
 * item spew) and on-place hooks; heightmaps, lighting and the batched client updates still happen. When the front has
 * passed, the crater's rim gets ordinary neighbour updates, so sand and gravel fall and water flows in.
 *
 * <p><b>Entities.</b> As the front passes each entity it takes damage and knockback scaled by distance and by
 * exposure (how many of 4 lines from the burst to its body are unblocked), and catches fire inside the fire radius.
 * Items and experience in the crater are destroyed.
 */
final class BlastJob implements Blast {
    static final float AIR_COST = 0.05f;
    static final float FLUID_COST = 0.6f;
    /** Block set flags: tell clients, skip neighbour shape updates, drops, block-entity side effects and on-place hooks. */
    static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    enum Phase { FRONT, RIM, DONE }

    final ServerLevel level;
    final Vec3 center;
    final BlastProfile profile;
    final long seed;
    private final @Nullable Entity source;
    private final boolean terrain;
    private final TerrainTable table;
    private final @Nullable BlockPos protectedCenter;
    private final int protectedRadius;
    private final RandomSource random;

    private RayField rays;
    private int shell;
    private int cursor;
    private boolean shellSplit;
    private float entityRadius = -1;
    private int age;
    private Phase phase = Phase.FRONT;
    private final Long2ObjectOpenHashMap<long[]> visited = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet rim = new LongOpenHashSet();
    private @Nullable LongIterator rimCursor;
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int blocksChanged;

    BlastJob(ServerLevel level, Vec3 center, BlastProfile profile, long seed, @Nullable Entity source, boolean terrain,
            @Nullable BlockPos protectedCenter, int protectedRadius) {
        this.level = level;
        this.center = center;
        this.profile = profile;
        this.seed = seed;
        this.source = source;
        this.terrain = terrain;
        this.table = TerrainTable.byId(profile.terrain());
        this.protectedCenter = protectedCenter;
        this.protectedRadius = protectedRadius;
        this.random = RandomSource.create(seed);
        Lobes lobes = new Lobes(seed, profile.lobes());
        float stone = cost(6f);
        float base = profile.crater() * stone;
        this.rays = RayField.start((dx, dy, dz) -> {
            float down = Math.max(0, -dy);
            return base * (1 - (1 - profile.craterDepth()) * down) * lobes.at(dx, dy, dz);
        });
    }

    Phase phase() {
        return phase;
    }

    /** The radius the front has reached (what clients draw the shockwave at). */
    @Override
    public int radius() {
        return this.shell;
    }

    @Override
    public boolean isDone() {
        return this.phase == Phase.DONE;
    }

    @Override
    public int blocksChanged() {
        return this.blocksChanged;
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
        float outer = this.profile.outerRadius();
        float target = instant ? Float.MAX_VALUE : this.age * this.profile.frontSpeed();
        while (this.phase == Phase.FRONT && this.shell <= outer && this.shell <= target) {
            if (this.terrain && this.shell <= this.profile.terrainRadius() && this.rays.size > 0) {
                int budget = instant ? Integer.MAX_VALUE : blockBudget - (this.blocksChanged - changesBefore);
                if (!runShell(this.shell, instant ? Long.MAX_VALUE : deadline, budget)) {
                    break;                                 // out of time or block budget: carry on next tick
                }
            }
            this.shell++;
            this.cursor = 0;
            this.shellSplit = false;
            if (!instant && System.nanoTime() > deadline) {
                break;
            }
        }
        affectEntities(Math.min(this.shell, outer));
        if (this.phase == Phase.FRONT && this.shell > outer) {
            this.phase = Phase.RIM;
            this.rays = new RayField();                    // free the ray arrays
            this.rimCursor = this.rim.iterator();
        }
        if (this.phase == Phase.RIM) {
            updateRim(instant ? Long.MAX_VALUE : deadline);
        }
        return this.blocksChanged - changesBefore;
    }

    // --- terrain ---------------------------------------------------------------------------------------------------------------
    /** Runs (the rest of) one shell. False if it stopped early for the budget. */
    private boolean runShell(int r, long deadline, int budget) {
        RayField f = this.rays;
        if (!this.shellSplit) {
            f.splitFor(Math.max(1, r));
            this.shellSplit = true;
        }
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
            int bx = (int) Math.floor(this.center.x + f.dx[k] * r);
            int by = (int) Math.floor(this.center.y + f.dy[k] * r);
            int bz = (int) Math.floor(this.center.z + f.dz[k] * r);
            if (by < minY || by > maxY) {
                f.mode[k] = RayField.DEAD;
                continue;
            }
            this.pos.set(bx, by, bz);
            if (!this.level.isLoaded(this.pos)) {
                f.mode[k] = RayField.DEAD;                 // (5b: chunk tickets keep the band loaded)
                continue;
            }
            BlockState state = this.level.getBlockState(this.pos);
            if (state.isAir()) {
                f.energy[k] -= AIR_COST;
                // Climbing above everything there is to hit: nothing more for this ray to do.
                if (f.dy[k] > 0.05f && by > this.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)) {
                    f.mode[k] = RayField.DEAD;
                }
                continue;
            }
            if (!markVisited(bx, by, bz)) {
                // Another ray has dealt with this block already: whatever is left there blocks this one.
                f.mode[k] = RayField.DEAD;
                continue;
            }
            if (f.mode[k] == RayField.CRATER && r <= crater) {
                crater(f, k, r, state, r <= core);
            } else {
                f.mode[k] = RayField.PRESSURE;
                pressure(f, k, r, state);
            }
            if (f.mode[k] == RayField.CRATER && r + 1 > crater) {
                f.mode[k] = RayField.PRESSURE;
            }
        }
        f.compact();
        return true;
    }

    private void crater(RayField f, int k, int r, BlockState state, boolean inCore) {
        if (isProtected() || state.getDestroySpeed(this.level, this.pos) < 0) {
            f.mode[k] = RayField.DEAD;                     // bedrock, barriers, protected spawn: a shadow behind
            return;
        }
        boolean liquid = state.getBlock() instanceof LiquidBlock;
        float c = liquid ? FLUID_COST : cost(state.getBlock().getExplosionResistance());
        if (!inCore) {
            if (f.energy[k] < c) {
                f.mode[k] = RayField.DEAD;
                return;
            }
            f.energy[k] -= c;
        }
        destroy(state);
    }

    private void pressure(RayField f, int k, int r, BlockState state) {
        if (isProtected() || !state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock) {
            f.mode[k] = RayField.DEAD;
            return;
        }
        if (r <= this.profile.structural() && this.table.blowsOut(state)) {
            write(this.pos, AIR);                          // blown out; the shock carries on through the gap
            return;
        }
        if (r <= this.profile.scorch()) {
            BlockState scorched = this.table.scorched(state);
            if (scorched != null) {
                write(this.pos, scorched);
            }
        }
        if (r <= this.profile.fire() && this.random.nextFloat() < 0.6f * (1 - r / Math.max(1, this.profile.fire()))) {
            // Set the surface alight from the side the ray came from.
            BlockPos before = BlockPos.containing(this.center.x + f.dx[k] * (r - 1), this.center.y + f.dy[k] * (r - 1),
                    this.center.z + f.dz[k] * (r - 1));
            if (this.level.getBlockState(before).isAir() && BaseFireBlock.canBePlacedAt(this.level, before, Direction.UP)) {
                write(before, BaseFireBlock.getState(this.level, before));
            }
        }
        f.mode[k] = RayField.DEAD;
    }

    /** Destroys the block at {@link #pos} (maybe dropping it) and remembers rim candidates around it. */
    private void destroy(BlockState state) {
        if (this.profile.drops() > 0 && this.random.nextFloat() < this.profile.drops()) {
            Block.dropResources(state, this.level, this.pos, this.level.getBlockEntity(this.pos));
        }
        write(this.pos, AIR);
        markDestroyed(this.pos.getX(), this.pos.getY(), this.pos.getZ());
        for (Direction d : Direction.values()) {
            BlockPos n = this.pos.relative(d);
            if (!isDestroyed(n.getX(), n.getY(), n.getZ()) && !this.level.getBlockState(n).isAir()) {
                this.rim.add(this.pos.asLong());
                break;
            }
        }
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
        this.phase = Phase.DONE;
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
    /** Hits every entity the front has reached since the last call (up to {@code r}). */
    private void affectEntities(float r) {
        BlastProfile p = this.profile;
        float reach = Math.max(Math.max(p.damage(), p.lethal()), Math.max(p.crater(), p.fire()));
        if (r <= this.entityRadius || this.entityRadius >= reach) {
            return;
        }
        float to = Math.min(r, reach);
        this.entityRadius = to;
        AABB box = new AABB(this.center, this.center).inflate(to);
        List<Entity> entities = this.level.getEntities((Entity) null, box, e -> !e.isSpectator() && !this.hit.contains(e.getId()));
        for (Entity e : entities) {
            double d = e.position().distanceTo(this.center);
            if (d > to) {
                continue;
            }
            this.hit.add(e.getId());
            if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
                if (d <= p.crater()) {
                    e.discard();
                }
                continue;
            }
            float exposure = exposure(e);
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
            HitResult hitResult = this.level.clip(new ClipContext(this.center, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            if (hitResult.getType() == HitResult.Type.MISS) {
                open++;
            }
        }
        return open / 4f;
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
