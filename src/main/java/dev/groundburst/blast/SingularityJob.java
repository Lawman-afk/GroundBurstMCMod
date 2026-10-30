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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One singularity (DESIGN.md §16.2): a black hole that opens, feeds on what it reaches and grows by it, then collapses
 * in a blast. Its size is its reach ({@link Singularities#reach}: its opening over its life and the cube root of what it
 * has swallowed), and everything follows from it: its event horizon, how far and how hard it pulls, how far it tears
 * blocks loose. While it lives:
 * <ul>
 * <li>everything within its pull is drawn in, harder the nearer it is and the bigger the hole has grown (an
 * inverse-square pull with a swirl round the hole), and whatever reaches the horizon is gone, and fed to it: a creature
 * is spaghettified ({@link Singularities#SPAGHETTIFIED}), anything else simply vanishes;</li>
 * <li>blocks out to a little past its reach are torn loose where they lie open to the air (it eats the ground from its
 * surface in), each once its tidal pull beats the block's hold (its hardness; a fluid barely holds), so earth goes past
 * the reach, stone to nine tenths of it, obsidian only to half; inside the horizon everything goes, unbreakable blocks
 * never do. Torn blocks drop nothing and feed it; their neighbours are told (sand falls in, water runs in, to be torn
 * in their turn), and clients are told, for the debris that spirals in.</li>
 * </ul>
 * The tearing sweeps the sphere shell by shell, each pass from just inside the nearest block the last one found (from
 * the middle now and then, for anything that has wandered in), within the blast engine's budget; every chunk within its
 * reach is kept loaded. Clients are told how far it reaches as it grows. Not saved.
 */
final class SingularityJob {
    enum Phase { GROW, RELEASE, DONE }

    /** Blocks torn a tick at most (so it eats, not erases), and how many of them clients are told of. */
    private static final int TEAR_PER_TICK = 1500, TOLD_PER_TICK = 120;
    /** Torn blocks: neighbours told, no drops, containers' contents gone with them. */
    private static final int TEAR_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** How hard a fluid holds against the pull. */
    private static final double FLUID_HOLD = 0.5;
    /** How far past its reach it looks for blocks to tear (only the weakest go past it). */
    private static final double SWEEP_SHARE = 1.04;
    /** Every this many passes sweeps from the middle (the others from just inside the nearest block the last one found). */
    private static final int FULL_PASS_EVERY = 12;
    /** Ticks between telling clients how far it reaches. */
    private static final int TELL_EVERY = 4;

    final int netId;
    final ServerLevel level;
    final BlockPos origin;
    final Vec3 center;
    /** Its opening's reach by the end of its life, what feeding adds (see {@link Singularities#reach}), its most. */
    final float seed, feed, max;
    final int life;
    final long startTick;
    final @Nullable UUID by;
    private final UUID owner = UUID.randomUUID();
    private final BlastChunks chunks;
    private final int minY, maxY;
    /** Blocks' worth swallowed so far, and the reach it gives. */
    private double mass;
    private float reach;
    private float toldReach = -1;
    /** The sweep: its shell and column, the passes made, the nearest shell this pass found anything breakable in. */
    private int shell, column, passes;
    private int nearest = Integer.MAX_VALUE;
    private int torn;
    private final LongArrayList told = new LongArrayList();
    private final IntArrayList toldStates = new IntArrayList();
    private Phase phase = Phase.GROW;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), side = new BlockPos.MutableBlockPos();
    /** The last chunk a column was in (columns run along x, so most share it). */
    private @Nullable LevelChunk chunk;
    private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
    /** This tick's reach to the tide's power (a block of hold h at distance d goes once h d^TIDE <= this). */
    private double reachTide;

    SingularityJob(int netId, ServerLevel level, BlockPos origin, float seed, float feed, float max, int life, @Nullable Entity by) {
        this.netId = netId;
        this.level = level;
        this.origin = origin.immutable();
        this.center = Vec3.atCenterOf(origin);
        this.seed = seed;
        this.feed = feed;
        this.max = Math.max(seed, max);
        this.life = Math.max(1, life);
        this.startTick = level.getGameTime();
        this.by = by == null ? null : by.getUUID();
        this.minY = level.getMinY();
        this.maxY = level.getMaxY();
        this.reach = Singularities.reach(seed, feed, 0, 0, this.life, this.max);
        this.chunks = new BlastChunks(level, this.owner, this.center, (float) (this.max * SWEEP_SHARE) + 2, GBServerConfig.GENERATE_CHUNKS.get(), h -> {});
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

    float reach() {
        return this.reach;
    }

    /** Works within {@code deadline} and {@code blockBudget}; returns the blocks it changed. */
    int step(long deadline, int blockBudget) {
        int changed = 0;
        if (this.phase == Phase.GROW) {
            int age = age();
            if (age >= this.life) {
                collapse();
                this.phase = Phase.RELEASE;
            } else {
                this.reach = Singularities.reach(this.seed, this.feed, this.mass, age, this.life, this.max);
                float h = Singularities.horizon(this.reach);
                drawIn(h);
                double sweep = SWEEP_SHARE * this.reach;
                if (this.chunks.prepare((int) Math.ceil(sweep), true)) {
                    changed = tear(h, sweep, deadline, Math.min(blockBudget, TEAR_PER_TICK));
                }
                tell(age);
            }
        }
        if (this.phase == Phase.RELEASE && this.chunks.release(16)) {
            this.phase = Phase.DONE;
        }
        return changed;
    }

    /** Everything within its pull drawn in; what reaches the horizon gone, and fed to it. */
    private void drawIn(float h) {
        double gm = Singularities.gm(this.reach);
        float pull = Singularities.pullRadius(this.reach);
        Entity source = this.by == null ? null : this.level.getEntity(this.by);
        DamageSource damage = this.level.damageSources().source(Singularities.SPAGHETTIFIED, null, source);
        for (Entity e : this.level.getEntities((Entity) null, new AABB(this.center, this.center).inflate(pull), Entity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator()) || e instanceof dev.groundburst.content.launch.MissileEntity) {
                continue;
            }
            Vec3 to = this.center.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d > pull) {
                continue;
            }
            if (d <= h + 0.5 * e.getBbWidth()) {
                this.mass += worth(e);
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
            e.addDeltaMovement(Singularities.pullOn(to, gm));
            e.needsSync = true;
        }
    }

    /** What a thing swallowed feeds it, in blocks' worth: a falling block one, a creature its bulk, anything else a little. */
    private static double worth(Entity e) {
        if (e instanceof FallingBlockEntity) {
            return 1;
        }
        if (e instanceof LivingEntity) {
            return Math.max(1, e.getBbWidth() * e.getBbWidth() * e.getBbHeight());
        }
        return 0.2;
    }

    /**
     * Tears blocks loose round it, sweeping its sphere out to {@code sweep} shell by shell until the deadline, the budget
     * or its tick's share; returns the blocks it changed. Each pass starts just inside the nearest breakable block the
     * last found (all inside that is already eaten), every {@link #FULL_PASS_EVERY}th from the middle.
     */
    private int tear(float h, double sweep, long deadline, int budget) {
        this.reachTide = Math.pow(this.reach, Singularities.TIDE);
        int maxShell = (int) Math.ceil(sweep);
        int changed = 0;
        int checked = 0;
        while (changed < budget) {
            if (this.shell > maxShell) {
                this.passes++;
                int from = this.passes % FULL_PASS_EVERY == 0 || this.nearest == Integer.MAX_VALUE ? 0 : Math.max(0, this.nearest - 2);
                this.nearest = Integer.MAX_VALUE;
                this.shell = Math.min(from, maxShell);
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
            changed += column(i % side - this.shell - 1, i / side - this.shell - 1, sweep, h, budget - changed);
        }
        return changed;
    }

    /** The current shell's blocks in the column at (dx, dz) from the middle that the pull tears loose. */
    private int column(int dx, int dz, double sweep, float h, int budget) {
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
        LevelChunk chunk = chunkAt(x, z);
        if (chunk == null) {
            return 0;
        }
        int n = 0;
        for (int dy = dyMin; dy <= dyMax && n < budget; dy++) {
            double d2 = q + (long) dy * dy;
            if (d2 > sweep * sweep) {
                break;
            }
            n += tearAt(chunk, x, this.origin.getY() + dy, z, d2, h);
            if (dy != 0 && n < budget) {
                n += tearAt(chunk, x, this.origin.getY() - dy, z, d2, h);
            }
        }
        return n;
    }

    private @Nullable LevelChunk chunkAt(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        if (cx != this.chunkX || cz != this.chunkZ || this.chunk == null) {
            this.chunk = this.level.getChunkSource().getChunkNow(cx, cz);
            this.chunkX = cx;
            this.chunkZ = cz;
        }
        return this.chunk;
    }

    /** The block at (x, y, z), {@code sqrt(d2)} from the middle: torn loose if the pull beats it and it lies open. */
    private int tearAt(LevelChunk chunk, int x, int y, int z, double d2, float h) {
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
        this.nearest = Math.min(this.nearest, this.shell);
        if (d2 > (double) h * h) {
            // Outside the horizon: only once its tidal pull beats the block's hold, and only what lies open.
            double hold = state.getBlock() instanceof LiquidBlock ? FLUID_HOLD : hardness + 0.3;
            if (hold * d2 * d2 * d2 > this.reachTide || !open()) {
                return 0;
            }
        }
        FluidState fluid = state.getFluidState();
        double worth = state.getBlock() instanceof LiquidBlock ? fluid.getAmount() / 8.0
                : state.isCollisionShapeFullBlock(this.level, this.pos) ? 1 : 0.25;
        if (!this.level.setBlock(this.pos, AIR, TEAR_FLAGS)) {
            return 0;
        }
        this.mass += worth;
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

    /** Clients are told how far it reaches now and then, and those near it of the blocks torn this tick (for the debris). */
    private void tell(int age) {
        if (age % TELL_EVERY == 0 && Math.abs(this.reach - this.toldReach) > 0.01f) {
            this.toldReach = this.reach;
            PacketDistributor.sendToPlayersInDimension(this.level, new SingularityPayloads.Grow(this.netId, this.reach));
        }
        if (this.told.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayersNear(this.level, null, this.center.x, this.center.y, this.center.z, 256,
                new SingularityPayloads.Torn(this.netId, this.told.toLongArray(), this.toldStates.toIntArray()));
        this.told.clear();
        this.toldStates.clear();
    }

    /** Its life over: it collapses in a blast of its own (as big as it has grown), and clients see it go. */
    private void collapse() {
        Entity source = this.by == null ? null : this.level.getEntity(this.by);
        PacketDistributor.sendToPlayersInDimension(this.level, new SingularityPayloads.Collapse(this.netId, this.reach));
        Blasts.detonate(this.level, this.center, Blasts.profile(Singularities.COLLAPSE_PROFILE), source, this.reach / Singularities.COLLAPSE_REACH);
    }
}
