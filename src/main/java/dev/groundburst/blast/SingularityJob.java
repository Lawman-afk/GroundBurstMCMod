package dev.groundburst.blast;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.config.GBServerConfig;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One singularity (DESIGN.md §16.2): a black hole whose event horizon grows to its full size over {@link #grow} ticks,
 * then collapses in a blast. While it grows:
 * <ul>
 * <li>everything within {@link #pull} is drawn in, harder the nearer it is and the bigger the hole has grown (an
 * inverse-square pull with a swirl round the hole), and whatever reaches the horizon is gone: a creature is
 * spaghettified ({@link Singularities#SPAGHETTIFIED}), anything else simply vanishes;</li>
 * <li>blocks out to the tear radius (which grows with it) are torn loose where they lie open to the air (it eats the
 * ground from its surface in), the weakest and the nearest first: each block holds by its hardness (a fluid barely
 * holds), the pull on it grows as the hole grows and falls off with distance, and it goes once the pull is the stronger.
 * Inside the horizon everything goes; unbreakable blocks never do. Torn blocks drop nothing; their neighbours are told
 * (sand falls in, water runs in, to be torn in their turn), and clients are told, for the debris that spirals in.</li>
 * </ul>
 * The tearing sweeps the sphere shell by shell from the middle out and starts again from the middle each time round,
 * within the blast engine's budget; chunks within its reach are kept loaded. Not saved.
 */
final class SingularityJob {
    enum Phase { GROW, RELEASE, DONE }

    /** Blocks torn a tick at most (so it eats, not erases), and how many of them clients are told of. */
    private static final int TEAR_PER_TICK = 900, TOLD_PER_TICK = 96;
    /** Torn blocks: neighbours told, no drops, containers' contents gone with them. */
    private static final int TEAR_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** How hard a fluid holds against the pull. */
    private static final double FLUID_HOLD = 0.5;

    final int netId;
    final ServerLevel level;
    final BlockPos origin;
    final Vec3 center;
    final float horizon, pull, rip;
    final int grow;
    /** Its collapse blast's size (the bomb scale). */
    final float scale;
    final long startTick;
    final @Nullable UUID by;
    private final UUID owner = UUID.randomUUID();
    private final BlastChunks chunks;
    private final int minY, maxY;
    /** The most blocks it may tear in all. */
    private final int maxTorn;
    /** The pull at a block: its strength times (this / distance)^1.5. */
    private final double tearRef;
    private int shell, column;
    private int torn;
    private final LongArrayList told = new LongArrayList();
    private final IntArrayList toldStates = new IntArrayList();
    private Phase phase = Phase.GROW;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), side = new BlockPos.MutableBlockPos();

    SingularityJob(int netId, ServerLevel level, BlockPos origin, float horizon, float pull, float rip, int grow, float scale, @Nullable Entity by) {
        this.netId = netId;
        this.level = level;
        this.origin = origin.immutable();
        this.center = Vec3.atCenterOf(origin);
        this.horizon = horizon;
        this.pull = pull;
        this.rip = rip;
        this.grow = Math.max(1, grow);
        this.scale = scale;
        this.startTick = level.getGameTime();
        this.by = by == null ? null : by.getUUID();
        this.minY = level.getMinY();
        this.maxY = level.getMaxY();
        this.maxTorn = (int) Math.max(500, Singularities.MAX_TORN * Math.min(1, Math.pow(rip / Singularities.RIP, 3)));
        this.tearRef = 8.0 * rip / Singularities.RIP;
        this.chunks = new BlastChunks(level, this.owner, this.center, rip + 1, GBServerConfig.GENERATE_CHUNKS.get(), h -> {});
    }

    Phase phase() {
        return this.phase;
    }

    int age() {
        return (int) (this.level.getGameTime() - this.startTick);
    }

    int torn() {
        return this.torn;
    }

    /** How far through its growth it is, 0 to 1. */
    private double grown(int age) {
        return Math.min(1, age / (double) this.grow);
    }

    /** How hard it pulls now (see {@link Singularities#strength}). */
    private double strength(int age) {
        return Singularities.strength(grown(age));
    }

    /** How far out it tears blocks loose now (growing with it). */
    private double tearReach(int age) {
        return Math.max(1, this.rip * Math.pow(grown(age), 0.7));
    }

    /** Works within {@code deadline} and {@code blockBudget}; returns the blocks it changed. */
    int step(long deadline, int blockBudget) {
        int changed = 0;
        if (this.phase == Phase.GROW) {
            int age = age();
            if (age >= this.grow) {
                collapse();
                this.phase = Phase.RELEASE;
            } else {
                float h = Singularities.horizon(this.horizon, age, this.grow);
                drawIn(age, h);
                // Its chunks: everything within its tear radius stays loaded (the band of a front standing at the middle).
                if (this.chunks.prepare(Math.max(0, (int) Math.ceil(this.rip) - 32))) {
                    changed = tear(age, h, deadline, Math.min(blockBudget, TEAR_PER_TICK));
                }
                tell();
            }
        }
        if (this.phase == Phase.RELEASE && this.chunks.release(16)) {
            this.phase = Phase.DONE;
        }
        return changed;
    }

    /** Everything within reach drawn in; what reaches the horizon gone. */
    private void drawIn(int age, float h) {
        double strength = strength(age);
        Entity source = this.by == null ? null : this.level.getEntity(this.by);
        DamageSource damage = this.level.damageSources().source(Singularities.SPAGHETTIFIED, null, source);
        for (Entity e : this.level.getEntities((Entity) null, new AABB(this.center, this.center).inflate(this.pull), Entity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator()) || e instanceof dev.groundburst.content.launch.MissileEntity) {
                continue;
            }
            Vec3 to = this.center.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d > this.pull) {
                continue;
            }
            if (d <= h + 0.5 * e.getBbWidth()) {
                if (e instanceof LivingEntity living) {
                    living.damageCooldownTime = 0;
                    living.hurtServer(this.level, damage, 1.0E6f);
                } else {
                    e.discard();
                }
                continue;
            }
            if (e instanceof Player) {
                continue;                                  // a player's own client pulls it (it moves its own player)
            }
            e.addDeltaMovement(Singularities.pullOn(to, this.pull, strength));
            e.needsSync = true;
        }
    }

    /**
     * Tears blocks loose round it, sweeping the sphere out to the tear radius shell by shell (and again from the middle
     * once it gets there) until the deadline, the budget or its tick's share; returns the blocks it changed.
     */
    private int tear(int age, float h, long deadline, int budget) {
        if (this.torn >= this.maxTorn) {
            return 0;
        }
        double reach = tearReach(age), strength = strength(age);
        int maxShell = (int) Math.ceil(reach);
        int changed = 0;
        int checked = 0;
        while (changed < budget && this.torn < this.maxTorn) {
            if (this.shell > maxShell) {
                this.shell = 0;                            // round again from the middle: the pull has grown since
                this.column = 0;
                if (checked > 0) {
                    break;                                 // one pass a tick at most
                }
            }
            int side = 2 * this.shell + 3;
            if (this.column >= side * side) {
                this.shell++;
                this.column = 0;
                continue;
            }
            if ((++checked & 15) == 0 && System.nanoTime() > deadline) {
                break;
            }
            int i = this.column++;
            changed += column(i % side - this.shell - 1, i / side - this.shell - 1, reach, strength, h, budget - changed);
        }
        return changed;
    }

    /** The current shell's blocks in the column at (dx, dz) from the middle that the pull tears loose. */
    private int column(int dx, int dz, double reach, double strength, float h, int budget) {
        int s = this.shell;
        long q = (long) dx * dx + (long) dz * dz;
        long lo = (long) s * s - q, hi = (long) (s + 1) * (s + 1) - q;
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
        int x = this.origin.getX() + dx, z = this.origin.getZ() + dz;
        LevelChunk chunk = this.level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return 0;
        }
        int n = 0;
        for (int dy = dyMin; dy <= dyMax && n < budget; dy++) {
            double d = Math.sqrt(q + (long) dy * dy);
            if (d > reach) {
                break;
            }
            n += tearAt(chunk, x, this.origin.getY() + dy, z, d, strength, h);
            if (dy != 0 && n < budget) {
                n += tearAt(chunk, x, this.origin.getY() - dy, z, d, strength, h);
            }
        }
        return n;
    }

    private int tearAt(LevelChunk chunk, int x, int y, int z, double d, double strength, float h) {
        if (y < this.minY || y >= this.maxY) {
            return 0;
        }
        this.pos.set(x, y, z);
        BlockState state = chunk.getBlockState(this.pos);
        if (state.isAir()) {
            return 0;
        }
        float hardness = state.getDestroySpeed(this.level, this.pos);
        if (hardness < 0) {
            return 0;                                      // bedrock and the like
        }
        if (d > h) {
            // Outside the horizon: only what lies open, and only once the pull on it beats its hold.
            if (!open()) {
                return 0;
            }
            double hold = state.getBlock() instanceof LiquidBlock ? FLUID_HOLD : hardness + 0.3;
            if (hold > strength * Math.pow(this.tearRef / Math.max(d, 0.5), 1.5)) {
                return 0;
            }
        }
        if (!this.level.setBlock(this.pos, AIR, TEAR_FLAGS)) {
            return 0;
        }
        this.torn++;
        if (this.told.size() < TOLD_PER_TICK) {
            this.told.add(this.pos.asLong());
            this.toldStates.add(Block.getId(state));
        }
        return 1;
    }

    /** Whether the block at {@link #pos} lies open: air or a fluid on any side. */
    private boolean open() {
        for (Direction dir : Direction.values()) {
            this.side.setWithOffset(this.pos, dir);
            BlockState s = this.level.getBlockState(this.side);
            if (s.isAir() || s.getBlock() instanceof LiquidBlock) {
                return true;
            }
        }
        return false;
    }

    /** Clients near it are told of the blocks torn this tick (for the debris). */
    private void tell() {
        if (this.told.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayersNear(this.level, null, this.center.x, this.center.y, this.center.z, 256,
                new SingularityPayloads.Torn(this.netId, this.told.toLongArray(), this.toldStates.toIntArray()));
        this.told.clear();
        this.toldStates.clear();
    }

    /** Its growth over: it collapses in a blast of its own, and clients see it go. */
    private void collapse() {
        Entity source = this.by == null ? null : this.level.getEntity(this.by);
        PacketDistributor.sendToPlayersInDimension(this.level, new SingularityPayloads.Collapse(this.netId));
        Blasts.detonate(this.level, this.center, Blasts.profile(Singularities.COLLAPSE_PROFILE), source, this.scale);
    }
}
