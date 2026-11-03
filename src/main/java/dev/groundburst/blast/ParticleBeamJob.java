package dev.groundburst.blast;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundburst.config.GBServerConfig;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.content.launch.Designators;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.superweapon.ParticleCannonBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One Particle Uplink Cannon beam (DESIGN.md §19.6). It leaves its cannon's pit and, {@link ParticleBeams#UPLINK} ticks
 * later, comes down from orbit where the cannon is aimed and burns for {@link ParticleBeams#BURN} ticks:
 * <ul>
 * <li>It <b>moves</b> toward its aim at {@link ParticleBeams#SPEED}: the spot whoever fired it lases with a Laser
 * Designator while they lase, or a new aim of the cannon's (a Launch Computer's).</li>
 * <li>It <b>eats down</b> into what it stands on from the top, the deeper the longer it lingers (each column's heat, its
 * trench {@link ParticleBeams#MAX_DEPTH} deep at most, a bowl round it out to {@link ParticleBeams#MELT}); trees, roofs
 * and water go first. Bedrock and obsidian stop it. The trench's bottom and sides melt: rock to slag (glowing magma here
 * and there in the bottom of its core), sand to glassed sand, earth to scorched earth, wood charred.</li>
 * <li>Round it out to {@link ParticleBeams#FIRE} the ground is scorched, leaves and plants burn away and napalm fires
 * catch.</li>
 * <li>Whatever is in its core, under it anywhere from the sky down, is vaporised (its damage goes through armour and
 * every grace period); round it, things catch fire and are scalded.</li>
 * </ul>
 * It burns while its cannon keeps firing it (power cut, knocked out, broken: the beam is gone). Terrain is left alone
 * when the server says so (as for blasts: the master switch, protected dimensions, the spawn area). The chunks round it
 * are kept loaded as it goes ({@link #KEEP}: a 48 s beam can walk 144 blocks). Not saved.
 */
final class ParticleBeamJob {
    /** Heat over which a column's trench deepens: {@code MAX_DEPTH (1 - e^(-heat / DEEPEN))}; a tick in its core is 1. */
    private static final float DEEPEN = 60;
    /** Damage a tick to whatever is in its core; every half second to what is near it. */
    private static final float VAPORISE = 20, SCALD = 3;
    /** How far round it the chunks are kept loaded, and how far it may move before they are kept round it anew. */
    private static final float KEEP = ParticleBeams.FIRE + 24, KEEP_SLACK = 12;
    /**
     * Chance a tick that a column round it catches fire while it is there (at its melt's edge, falling to none at its
     * reach): a column the beam passes at full speed is round it about a second or three, so a third or so of them burn;
     * round a beam parked its whole time, most.
     */
    private static final float FIRE_CHANCE = 0.012f;

    final int netId;
    final ServerLevel level;
    /** Its cannon (null: a beam with no cannon, for GameTests). */
    final @Nullable BlockPos cannon;
    final Vec3 from;
    final @Nullable UUID by;
    final long startTick;
    final int uplink, burn;
    /** Whether it changes the ground, and whether it hurts what is under it (a GameTest's dry beam does neither). */
    private final boolean terrain, hurts;
    private final @Nullable BlockPos spawn;
    private final int spawnRadius;
    /** The chunks it keeps loaded, round {@link #chunkX}, {@link #chunkZ} (each loader its own ticket owner). */
    private BlastChunks chunks;
    private double chunkX, chunkZ;
    private final RandomSource random = RandomSource.create();
    /** Where it touches now (x, z; y the ground's top there) and where it is heading. */
    private double x, y, z, aimX, aimZ;
    /** Its cannon's aim as last seen: a new one turns the beam toward it. */
    private @Nullable BlockPos aimed;
    /** Each column's heat, the top it had when the beam first reached it, how far down it has been eaten. */
    private final Long2FloatOpenHashMap heat = new Long2FloatOpenHashMap();
    private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap floors = new Long2IntOpenHashMap();
    private final LongOpenHashSet scorched = new LongOpenHashSet();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    private boolean ended;

    ParticleBeamJob(int netId, ServerLevel level, @Nullable BlockPos cannon, @Nullable Vec3 from, BlockPos aim, @Nullable UUID by, boolean dry) {
        this.netId = netId;
        this.level = level;
        this.cannon = cannon == null ? null : cannon.immutable();
        this.by = by;
        this.startTick = level.getGameTime();
        this.uplink = cannon == null ? 0 : ParticleBeams.UPLINK;
        this.burn = ParticleBeams.burnOverride > 0 ? ParticleBeams.burnOverride : ParticleBeams.BURN;
        this.hurts = !dry;
        this.x = this.aimX = aim.getX() + 0.5;
        this.z = this.aimZ = aim.getZ() + 0.5;
        this.aimed = aim.immutable();
        this.y = ground(Mth.floor(this.x), Mth.floor(this.z), aim.getY());
        this.from = from == null ? new Vec3(this.x, this.y, this.z) : from;
        this.terrain = !dry && GBServerConfig.DESTROY_BLOCKS.get()
                && !GBServerConfig.PROTECTED_DIMENSIONS.get().contains(level.dimension().location().toString());
        BlockPos spawnAt = null;
        int spawnR = 0;
        if (GBServerConfig.PROTECT_SPAWN.get() && level.dimension() == Level.OVERWORLD) {
            spawnAt = level.getSharedSpawnPos();
            spawnR = level.getServer() instanceof DedicatedServer server ? server.getSpawnProtectionRadius() : 16;
        }
        this.spawn = spawnAt;
        this.spawnRadius = spawnR;
        this.chunks = keep();
        this.heat.defaultReturnValue(0);
    }

    int age() {
        return (int) (this.level.getGameTime() - this.startTick);
    }

    /** Where it touches the ground now. */
    Vec3 foot() {
        return new Vec3(this.x, this.y, this.z);
    }

    ParticleBeamPayloads.Start startPayload() {
        return new ParticleBeamPayloads.Start(this.netId, this.from, foot(), age(), this.uplink, this.burn);
    }

    /** A tick of it; false once it is gone. */
    boolean step() {
        if (this.ended) {
            return false;
        }
        int age = age();
        ParticleCannonBlockEntity cannon = cannon();
        if (this.cannon != null && cannon == null || age >= this.uplink + this.burn) {
            end();
            return false;
        }
        if (age % 5 == 0) {
            if (Math.hypot(this.x - this.chunkX, this.z - this.chunkZ) > KEEP_SLACK) {
                BlastChunks old = this.chunks;                   // it has moved on: keep the chunks round it now
                this.chunks = keep();
                this.chunks.prepare((int) Math.ceil(KEEP), true);
                old.release(Integer.MAX_VALUE);
            } else {
                this.chunks.prepare((int) Math.ceil(KEEP), true);
            }
        }
        steer(cannon);
        if (age >= this.uplink) {
            if (this.terrain) {
                burn();
            }
            if (this.hurts) {
                hurt(age);
            }
        }
        int bx = Mth.floor(this.x), bz = Mth.floor(this.z);
        if (this.level.hasChunk(bx >> 4, bz >> 4)) {
            this.y = ground(bx, bz, (int) Math.floor(this.y));
        }
        if (age % 2 == 0) {
            PacketDistributor.sendToPlayersInDimension(this.level, new ParticleBeamPayloads.Move(this.netId, foot()));
        }
        return true;
    }

    /** Its cannon, while it is there and still firing this beam (null: gone, or it has no cannon). */
    private @Nullable ParticleCannonBlockEntity cannon() {
        if (this.cannon == null || !this.level.isLoaded(this.cannon)) {
            return null;
        }
        return this.level.getBlockEntity(this.cannon) instanceof ParticleCannonBlockEntity c && c.beamLive(this.netId) ? c : null;
    }

    private void end() {
        if (!this.ended) {
            this.ended = true;
            PacketDistributor.sendToPlayersInDimension(this.level, new ParticleBeamPayloads.End(this.netId));
            this.chunks.release(Integer.MAX_VALUE);
        }
    }

    /** The level is going: let every chunk go now. */
    void releaseAll() {
        this.chunks.release(Integer.MAX_VALUE);
    }

    /** A chunk loader round where it is now. */
    private BlastChunks keep() {
        this.chunkX = this.x;
        this.chunkZ = this.z;
        return new BlastChunks(this.level, UUID.randomUUID(), new Vec3(this.x, this.y, this.z), KEEP, GBServerConfig.GENERATE_CHUNKS.get(), h -> {});
    }

    /** The top of the ground at a column: the first air over whatever is highest there ({@code fallback} if unloaded). */
    private int ground(int bx, int bz, int fallback) {
        return this.level.hasChunk(bx >> 4, bz >> 4) ? this.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) : fallback;
    }

    // --- steering -------------------------------------------------------------------------------------------------------------
    /**
     * Where it heads: the spot whoever fired it is lasing (while they lase), or the cannon's aim when that changes (a
     * Launch Computer's); once down, it moves toward it at {@link ParticleBeams#SPEED}, never beyond its reach.
     */
    private void steer(@Nullable ParticleCannonBlockEntity cannon) {
        if (this.by != null) {
            Designators.Spot spot = Designators.spotOf(this.level, this.by);
            if (spot != null) {
                this.aimX = spot.pos().x;
                this.aimZ = spot.pos().z;
            }
        }
        BlockPos t = cannon == null ? null : cannon.target();
        if (t != null && !t.equals(this.aimed)) {
            this.aimed = t.immutable();
            this.aimX = t.getX() + 0.5;
            this.aimZ = t.getZ() + 0.5;
        }
        if (cannon != null) {                                     // it reaches no farther than its cannon does
            double dx = this.aimX - this.cannon.getX(), dz = this.aimZ - this.cannon.getZ(), d = Math.hypot(dx, dz);
            if (d > cannon.range()) {
                this.aimX = this.cannon.getX() + dx / d * cannon.range();
                this.aimZ = this.cannon.getZ() + dz / d * cannon.range();
            }
        }
        if (age() < this.uplink) {
            return;                                               // still on its way down: it comes down where it was aimed
        }
        double dx = this.aimX - this.x, dz = this.aimZ - this.z, d = Math.hypot(dx, dz);
        if (d > 1e-4) {
            double step = Math.min(d, ParticleBeams.SPEED);
            this.x += dx / d * step;
            this.z += dz / d * step;
        }
    }

    // --- the ground -----------------------------------------------------------------------------------------------------------
    /** A tick of its burn: every column round it melted, eaten down or scorched. */
    private void burn() {
        int r = (int) Math.ceil(ParticleBeams.FIRE);
        int bx = Mth.floor(this.x), bz = Mth.floor(this.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = bx + dx, cz = bz + dz;
                double d = Math.hypot(cx + 0.5 - this.x, cz + 0.5 - this.z);
                if (d > ParticleBeams.FIRE || !this.level.hasChunk(cx >> 4, cz >> 4) || spared(cx, cz)) {
                    continue;
                }
                if (d <= ParticleBeams.MELT) {
                    melt(cx, cz, d);
                } else {
                    scorch(cx, cz);
                    kindle(cx, cz, d);
                }
            }
        }
    }

    /** Whether a column lies in the protected spawn area. */
    private boolean spared(int cx, int cz) {
        return this.spawn != null && Math.abs(cx - this.spawn.getX()) <= this.spawnRadius && Math.abs(cz - this.spawn.getZ()) <= this.spawnRadius;
    }

    /** How much a tick of it heats a column {@code d} from its middle: all through its core, falling away to its melt's edge. */
    static float warmth(double d) {
        if (d <= ParticleBeams.CORE) {
            return 1;
        }
        double u = Math.max(0, (ParticleBeams.MELT - d) / (ParticleBeams.MELT - ParticleBeams.CORE));
        return (float) Math.pow(u, 1.5);
    }

    /** How deep a column with this much heat is eaten down, blocks. */
    static int depth(float heat) {
        return (int) Math.floor(ParticleBeams.MAX_DEPTH * (1 - Math.exp(-heat / DEEPEN)));
    }

    /** A column within its melt: heated, eaten down from its top as deep as its heat says, its new bottom and sides melted. */
    private void melt(int cx, int cz, double d) {
        long key = ChunkPos.asLong(cx, cz);
        if (!this.tops.containsKey(key)) {
            scorch(cx, cz);                                       // what stands on it burns first
            this.tops.put(key, ground(cx, cz, (int) Math.floor(this.y)) - 1);
        }
        float w = warmth(d);
        float h = this.heat.addTo(key, w) + w;
        int depth = depth(h);
        if (depth <= 0) {
            return;
        }
        int top0 = this.tops.get(key);
        int floor = top0 - depth + 1;
        int reached = this.floors.containsKey(key) ? this.floors.get(key) : top0 + 1;
        int top = ground(cx, cz, top0 + 1) - 1;                   // what is there now: water flows back, sand falls in
        BlockPos.MutableBlockPos p = this.at;
        for (int y = top; y >= floor; y--) {
            p.set(cx, y, cz);
            BlockState s = this.level.getBlockState(p);
            if (s.isAir()) {
                continue;
            }
            if (!yields(s, p)) {
                floor = y + 1;                                    // bedrock, obsidian: the beam stops on it
                break;
            }
            this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (floor < reached) {
            this.floors.put(key, floor);
            line(cx, cz, floor, reached, d <= ParticleBeams.CORE);
        }
    }

    /** What the beam eats through: anything a pick could dig short of obsidian (50), and water, which boils away. */
    private boolean yields(BlockState s, BlockPos p) {
        if (!s.getFluidState().isEmpty()) {
            return s.getFluidState().is(FluidTags.WATER);
        }
        float hardness = s.getDestroySpeed(this.level, p);
        return hardness >= 0 && hardness < 50;
    }

    /**
     * The trench's new bottom at a column eaten down to {@code floor}, and its sides beside the stretch from there up to
     * {@code above}: melted (the bottom of its core glowing here and there).
     */
    private void line(int cx, int cz, int floor, int above, boolean core) {
        BlockPos.MutableBlockPos p = this.at;
        p.set(cx, floor - 1, cz);
        BlockState melted = molten(this.level.getBlockState(p), core);
        if (melted != null) {
            this.level.setBlock(p, melted, Block.UPDATE_ALL);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int y = floor; y < above; y++) {
                p.set(cx + side.getStepX(), y, cz + side.getStepZ());
                BlockState s = this.level.getBlockState(p);
                BlockState m = molten(s, false);
                if (m != null) {
                    this.level.setBlock(p, m, Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * What the beam's heat makes of a block it lays bare (null: it stays): rock and gravel to slag (in the bottom of its
     * core, now and then glowing magma), netherrack to magma, sand to glassed sand, earth and clay to scorched earth;
     * otherwise as the nuclear fireball scorches (wood charred, snow gone).
     */
    private @Nullable BlockState molten(BlockState s, boolean core) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity()) {
            return null;
        }
        if (TerrainTable.rock(s) || s.is(Blocks.GRAVEL) || s.is(GBFallout.SCORCHED_STONE.block().get())) {
            return core && this.random.nextFloat() < 0.3f ? Blocks.MAGMA_BLOCK.defaultBlockState() : GBFallout.SLAG.block().get().defaultBlockState();
        }
        if (s.is(Blocks.NETHERRACK)) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (TerrainTable.sand(s)) {
            return GBFallout.GLASSED_SAND.block().get().defaultBlockState();
        }
        if (TerrainTable.earth(s) || s.is(Blocks.CLAY) || s.is(net.minecraft.world.level.block.Blocks.MUD)) {
            return GBFallout.SCORCHED_EARTH.block().get().defaultBlockState();
        }
        return TerrainTable.NUCLEAR_TABLE.scorched(s);
    }

    /** A column round it, once: leaves and plants on it burn away, logs char, its ground is scorched. */
    private void scorch(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (!this.scorched.add(key)) {
            return;
        }
        BlockPos.MutableBlockPos p = this.at;
        int top = ground(cx, cz, (int) Math.floor(this.y)) - 1;
        int y = top;
        for (; y > top - 16 && y > this.level.getMinBuildHeight(); y--) {
            p.set(cx, y, cz);
            BlockState s = this.level.getBlockState(p);
            if (s.isAir()) {
                continue;
            }
            if (s.is(BlockTags.LEAVES) || s.canBeReplaced() && s.getFluidState().isEmpty() || s.is(GBOrdnance.NAPALM_FIRE.get())) {
                this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                continue;
            }
            if (s.is(BlockTags.LOGS)) {
                BlockState charred = TerrainTable.NUCLEAR_TABLE.scorched(s);
                if (charred != null) {
                    this.level.setBlock(p, charred, Block.UPDATE_ALL);
                }
                continue;                                         // a trunk: on down to the ground under it
            }
            break;
        }
        BlockState ground = this.level.getBlockState(p);
        if (!ground.getFluidState().isEmpty() || ground.isAir()) {
            return;
        }
        BlockState burnt = TerrainTable.NUCLEAR_TABLE.scorched(ground);
        if (burnt != null && !burnt.isAir() && burnt.getFluidState().isEmpty()) {
            this.level.setBlock(p, burnt, Block.UPDATE_ALL);
        }
    }

    /**
     * A column round it outside its melt may catch fire this tick: napalm on its ground, more likely near the melt (not
     * on one within it: that is about to be eaten).
     */
    private void kindle(int cx, int cz, double d) {
        double u = (d - ParticleBeams.MELT) / (ParticleBeams.FIRE - ParticleBeams.MELT);
        if (this.random.nextFloat() >= FIRE_CHANCE * (1 - u)) {
            return;
        }
        BlockPos above = new BlockPos(cx, ground(cx, cz, (int) Math.floor(this.y)), cz);
        BlockState fire = GBOrdnance.NAPALM_FIRE.get().defaultBlockState();
        if (this.level.getBlockState(above).isAir() && fire.canSurvive(this.level, above)) {
            this.level.setBlock(above, fire, Block.UPDATE_ALL);
        }
    }

    // --- creatures --------------------------------------------------------------------------------------------------------------
    /**
     * Whatever is under it, from the sky down to the bottom of its trench: in its core vaporised (every tick, through
     * armour and grace periods), round it set alight and scalded every half second.
     */
    private void hurt(int age) {
        Entity source = this.by == null ? null : this.level.getEntity(this.by);
        DamageSource beam = this.level.damageSources().source(ParticleBeams.PARTICLE_BEAM, null, source);
        float reach = ParticleBeams.FIRE;
        AABB box = new AABB(this.x - reach, this.y - ParticleBeams.MAX_DEPTH - 2, this.z - reach, this.x + reach, (this.level.getMaxBuildHeight() - 1) + 1,
                this.z + reach);
        for (Entity e : this.level.getEntities((Entity) null, box, e -> e.isAlive() && !spared(e))) {
            double d = Math.hypot(e.getX() - this.x, e.getZ() - this.z) - e.getBbWidth() / 2;
            if (d <= ParticleBeams.CORE) {
                e.igniteForSeconds(10);
                e.hurt(beam, VAPORISE);
            } else if (d <= reach && e.getY() <= this.y + 6) {
                e.igniteForSeconds(6);
                if (e instanceof LivingEntity && age % 10 == 0) {
                    e.hurt(beam, SCALD);
                }
            }
        }
    }

    private static boolean spared(Entity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator()) || e instanceof dev.groundburst.content.launch.MissileEntity;
    }
}
