package dev.groundburst.blast;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.config.GBServerConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One annihilation (DESIGN.md §16.3): a sphere that grows to its full radius in {@link Annihilations#EXPAND} ticks and
 * erases everything it reaches. Blocks go with no drops, no rubble and no fire, whatever their blast resistance;
 * unbreakable ones (bedrock, barriers) stay. Containers go with what is in them. Creatures inside are annihilated; items,
 * arrows, minecarts and the like are simply gone.
 *
 * <p>Blocks are erased a shell (a one-block band of distance) at a time from the middle out, each shell column by
 * column, never past the sphere's size at that moment (the size players see), within the blast engine's shared budget
 * ({@link BlastManager}); on a busy server the erasing trails the sphere. Chunks are kept loaded as a blast's are
 * ({@link BlastChunks}; never-generated ones are left alone). Last the rim: every erased block on the sphere's surface
 * tells its neighbours, so sand there falls and water runs in, as a crater's rim does. Not saved: a server stopped
 * part way through leaves the sphere part erased.
 */
final class AnnihilationJob {
    enum Phase { ERASE, RIM, RELEASE, DONE }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** Neighbour updates and chunk releases a call, between deadline checks. */
    private static final int RIM_BATCH = 64, RELEASE_BATCH = 16;
    /**
     * Ticks it keeps clearing drops after its last kill: a creature's loot falls as it dies, its experience only once
     * its death is over (20 ticks on).
     */
    private static final int DROP_WAIT = 25;

    final int netId;
    final ServerLevel level;
    final BlockPos origin;
    final Vec3 center;
    final float radius;
    final long startTick;
    final @Nullable UUID by;
    private final UUID owner = UUID.randomUUID();
    private final BlastChunks chunks;
    private final double radiusSq;
    /** Squared distance past which an erased block is on the sphere's surface (its neighbours are told). */
    private final double rimSq;
    private final int maxShell;
    private final int minY, maxY;
    /** The highest block in any chunk it has seen: above it there is only air to erase. */
    private int ceiling;
    private int shell, column;
    private final LongArrayList rim = new LongArrayList();
    private int rimCursor;
    private Phase phase = Phase.ERASE;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Blocks erased so far (for the GameTests and the bench). */
    int erased;
    /** Game tick it last killed something (drops are cleared until {@link #DROP_WAIT} after). */
    private long lastKill = Long.MIN_VALUE / 2;

    AnnihilationJob(int netId, ServerLevel level, BlockPos origin, float radius, @Nullable Entity by) {
        this.netId = netId;
        this.level = level;
        this.origin = origin.immutable();
        this.center = Vec3.atCenterOf(origin);
        this.radius = radius;
        this.startTick = level.getGameTime();
        this.by = by == null ? null : by.getUUID();
        this.radiusSq = (double) radius * radius;
        this.rimSq = Math.max(0, radius - 1.8) * Math.max(0, radius - 1.8);
        this.maxShell = (int) Math.floor(radius);
        this.minY = level.getMinY();
        this.maxY = level.getMaxY();
        this.ceiling = this.minY - 1;
        this.chunks = new BlastChunks(level, this.owner, this.center, radius + 1, GBServerConfig.GENERATE_CHUNKS.get(),
                h -> this.ceiling = Math.max(this.ceiling, h));
    }

    Phase phase() {
        return this.phase;
    }

    int age() {
        return (int) (this.level.getGameTime() - this.startTick);
    }

    /** The sphere's size now (the size players see). */
    float reach() {
        return Annihilations.reach(this.radius, age());
    }

    /**
     * Works until {@code deadline} (System.nanoTime), {@code blockBudget} block changes, or the sphere's size stops it;
     * returns the blocks it changed.
     */
    int step(long deadline, int blockBudget) {
        int changed = 0;
        if (this.phase == Phase.ERASE) {
            float reach = reach();
            sweep(reach);
            while (this.phase == Phase.ERASE) {
                if (this.shell > this.maxShell) {
                    sweep(this.radius);                    // anything dropped by the last ones annihilated
                    this.phase = Phase.RIM;
                    break;
                }
                // A shell runs once the sphere has grown past its outer edge (at full size, all of them).
                if (reach < this.radius && this.shell + 1 > reach || !this.chunks.prepare(this.shell)) {
                    return changed;                        // the sphere has not got there yet, or its chunks are loading
                }
                int side = 2 * this.shell + 3;
                while (this.column < side * side) {
                    if ((this.column & 7) == 0 && (System.nanoTime() > deadline || changed >= blockBudget)) {
                        return changed;
                    }
                    int i = this.column++;
                    changed += column(i % side - this.shell - 1, i / side - this.shell - 1);
                }
                this.shell++;
                this.column = 0;
            }
        }
        if (this.phase == Phase.RIM) {
            sweepDrops();
            while (this.rimCursor < this.rim.size()) {
                if (System.nanoTime() > deadline) {
                    return changed;
                }
                for (int n = 0; n < RIM_BATCH && this.rimCursor < this.rim.size(); n++) {
                    BlockPos p = BlockPos.of(this.rim.getLong(this.rimCursor++));
                    if (this.level.isLoaded(p) && this.level.getBlockState(p).isAir()) {
                        this.level.updateNeighborsAt(p, Blocks.AIR);
                    }
                }
            }
            this.phase = Phase.RELEASE;
        }
        if (this.phase == Phase.RELEASE) {
            sweepDrops();
            if (this.chunks.release(RELEASE_BATCH) && this.level.getGameTime() >= this.lastKill + DROP_WAIT) {
                this.phase = Phase.DONE;
            }
        }
        return changed;
    }

    /** The blocks of the current shell in the column at (dx, dz) from the middle: up to two runs, above and below it. */
    private int column(int dx, int dz) {
        int s = this.shell;
        long q = (long) dx * dx + (long) dz * dz;
        long lo = (long) s * s - q, hi = (long) (s + 1) * (s + 1) - q;          // dy² in [lo, hi)
        if (hi <= 0) {
            return 0;
        }
        int dyMin = lo <= 0 ? 0 : (int) Math.ceil(Math.sqrt(lo));
        while ((long) dyMin * dyMin < lo) {
            dyMin++;
        }
        int dyMax = (int) Math.sqrt(hi - 1);
        while ((long) dyMax * dyMax >= hi) {
            dyMax--;
        }
        while ((long) (dyMax + 1) * (dyMax + 1) < hi) {
            dyMax++;
        }
        int n = 0;
        for (int dy = dyMin; dy <= dyMax; dy++) {
            long d2 = q + (long) dy * dy;
            n += erase(dx, dy, dz, d2);
            if (dy != 0) {
                n += erase(dx, -dy, dz, d2);
            }
        }
        return n;
    }

    private int erase(int dx, int dy, int dz, long d2) {
        if (d2 > this.radiusSq) {
            return 0;
        }
        int y = this.origin.getY() + dy;
        if (y < this.minY || y >= this.maxY || y > this.ceiling) {
            return 0;
        }
        int x = this.origin.getX() + dx, z = this.origin.getZ() + dz;
        LevelChunk chunk = this.level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return 0;                                      // never generated (with chunk generation off)
        }
        this.pos.set(x, y, z);
        BlockState state = chunk.getBlockState(this.pos);
        if (state.isAir() || state.getDestroySpeed(this.level, this.pos) < 0) {
            return 0;                                      // nothing there, or bedrock and the like
        }
        if (!this.level.setBlock(this.pos, AIR, BlastJob.WRITE_FLAGS)) {
            return 0;
        }
        this.erased++;
        if (d2 > this.rimSq) {
            this.rim.add(this.pos.asLong());
        }
        return 1;
    }

    /** Everything inside the sphere at {@code reach} annihilated (creative and spectating players aside). */
    private void sweep(float reach) {
        if (reach <= 0) {
            return;
        }
        double r2 = (double) reach * reach;
        Entity source = this.by == null ? null : this.level.getEntity(this.by);
        DamageSource damage = this.level.damageSources().source(Annihilations.ANNIHILATED, null, source);
        for (Entity e : this.level.getEntities((Entity) null, new AABB(this.center, this.center).inflate(reach), Entity::isAlive)) {
            if (e.getBoundingBox().getCenter().distanceToSqr(this.center) > r2
                    || e instanceof Player p && (p.isCreative() || p.isSpectator())
                    || e instanceof dev.groundburst.content.launch.MissileEntity) {     // flights are the flight manager's
                continue;
            }
            if (e instanceof LivingEntity living) {
                living.damageCooldownTime = 0;
                living.hurtServer(this.level, damage, 1.0E6f);
                this.lastKill = this.level.getGameTime();
            } else {
                e.discard();
            }
        }
    }

    /** What the dead dropped in the sphere (loot as they die, experience once their death is over): gone too. */
    private void sweepDrops() {
        if (this.level.getGameTime() > this.lastKill + DROP_WAIT) {
            return;
        }
        for (Entity e : this.level.getEntities((Entity) null, new AABB(this.center, this.center).inflate(this.radius),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb)) {
            if (e.position().distanceToSqr(this.center) <= this.radiusSq) {
                e.discard();
            }
        }
    }
}
