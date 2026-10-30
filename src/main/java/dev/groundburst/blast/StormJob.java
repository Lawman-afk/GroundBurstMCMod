package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One storm (DESIGN.md §16.14): a supercell over a region {@link #radius} round its eye, raging for {@link #life} ticks
 * ({@link Storms#intensity}: gathering, at full strength, dying away). Each tick:
 * <ul>
 * <li>its wind drives everything but players (each player's own client drives its player) round the eye and a little
 * in ({@link Storms#windOn});</li>
 * <li>lightning comes down ({@link Storms#strikeRate}), a third of it at a creature standing in the open, a third at
 * the tallest of a few spots, the rest anywhere; a lightning rod near where it would land draws it to itself. A strike
 * hurts whatever stands at its foot (vanilla lightning's own effects too: a creeper charged, a pig turned), then
 * chains on through the nearest creatures, each jump weaker; it scorches the ground (grass burnt to dirt, sand fused to
 * glass, leaves burnt away), may start a fire where fire may spread, and sends out an EMP pulse
 * ({@link Storms#EMP_RADIUS} round: machines and interceptor defences go dark in it, interceptors flying through it fried);
 * then it explodes where it came down ({@link Storms#STRIKE_BLAST}: a small crater, fires round its rim). Clients are
 * told of each strike, for the bolt, its chain and its thunder.</li>
 * </ul>
 * Strikes only come down where the world is loaded. Not saved.
 */
final class StormJob {
    final int netId;
    final ServerLevel level;
    final Vec3 center;
    final float radius;
    final int life;
    /** The height its cloud deck hangs at (its bolts come down from it). */
    final double cloudY;
    final long startTick;
    final @Nullable UUID by;
    /** Whether its strikes explode where they come down (always, but in a GameTest that counts them on its grass). */
    final boolean blasts;
    /** Strikes a second at full strength set by a GameTest (0: its own rate, by its size). */
    private final double rateOverride;
    private final RandomSource random = RandomSource.create();
    private double strikeCarry;
    private int strikes;
    /** Creatures standing in the open within it, refreshed every second (what a third of its strikes aim at). */
    private List<LivingEntity> exposed = List.of();

    StormJob(int netId, ServerLevel level, Vec3 center, float radius, int life, @Nullable Entity by, boolean blasts, double rateOverride) {
        this.netId = netId;
        this.level = level;
        this.center = center;
        this.radius = Math.max(4, radius);
        this.life = Math.max(1, life);
        this.cloudY = Math.min(level.getMaxY() - 6, center.y + Storms.CLOUD_HEIGHT * Math.max(0.3, this.radius / Storms.RADIUS));
        this.startTick = level.getGameTime();
        this.by = by == null ? null : by.getUUID();
        this.blasts = blasts;
        this.rateOverride = rateOverride;
    }

    int age() {
        return (int) (this.level.getGameTime() - this.startTick);
    }

    int strikes() {
        return this.strikes;
    }

    StormPayloads.Start startPayload() {
        return new StormPayloads.Start(this.netId, this.center, this.radius, this.life, age(), this.cloudY);
    }

    /** A tick of it; false once it has died away. */
    boolean step() {
        int age = age();
        if (age >= this.life) {
            PacketDistributor.sendToPlayersInDimension(this.level, new StormPayloads.End(this.netId));
            return false;
        }
        float k = Storms.intensity(age, this.life);
        if (age % 2 == 0) {
            wind(k);
        }
        if (age % 20 == 0) {
            this.exposed = exposed();
        }
        this.strikeCarry += Storms.strikeRate(this.radius, k, this.rateOverride) / 20;
        while (this.strikeCarry >= 1) {
            this.strikeCarry -= 1;
            strike();
        }
        return true;
    }

    private AABB box() {
        return new AABB(this.center.x - this.radius, this.level.getMinY(), this.center.z - this.radius,
                this.center.x + this.radius, this.level.getMaxY(), this.center.z + this.radius);
    }

    private boolean inside(Vec3 p) {
        double dx = p.x - this.center.x, dz = p.z - this.center.z;
        return dx * dx + dz * dz <= (double) this.radius * this.radius;
    }

    private static boolean spared(Entity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator()) || e instanceof dev.groundburst.content.launch.MissileEntity;
    }

    /** Everything but players driven round its eye (every other tick, twice the push). */
    private void wind(float k) {
        if (k <= 0) {
            return;
        }
        for (Entity e : this.level.getEntities((Entity) null, box(), Entity::isAlive)) {
            if (e instanceof Player || spared(e) || !inside(e.position())) {
                continue;
            }
            Vec3 push = Storms.windOn(e.position().subtract(this.center), this.radius, k);
            if (push != Vec3.ZERO) {
                e.addDeltaMovement(push.scale(2));
                e.needsSync = true;
            }
        }
    }

    private List<LivingEntity> exposed() {
        return this.level.getEntitiesOfClass(LivingEntity.class, box(), e -> e.isAlive() && !spared(e) && inside(e.position())
                && this.level.canSeeSky(e.blockPosition()));
    }

    /** One bolt: where it comes down, then what it does there. */
    private void strike() {
        double roll = this.random.nextDouble();
        BlockPos at = null;
        LivingEntity target = null;
        if (roll < 0.35 && !this.exposed.isEmpty()) {
            target = this.exposed.get(this.random.nextInt(this.exposed.size()));
            if (target.isAlive() && this.level.canSeeSky(target.blockPosition())) {
                at = target.blockPosition();
            } else {
                target = null;
            }
        }
        if (at == null && roll < 0.7) {
            for (int i = 0; i < 6; i++) {                         // the tallest of a few spots: a tree, a tower, a hill
                BlockPos spot = randomColumn();
                if (spot != null && (at == null || spot.getY() > at.getY())) {
                    at = spot;
                }
            }
        }
        if (at == null) {
            at = randomColumn();
        }
        if (at == null) {
            return;                                               // where the world is not loaded: no strike
        }
        Optional<BlockPos> rod = rodNear(at);
        if (rod.isPresent()) {
            grounded(rod.get());
        } else {
            hit(at, target);
        }
    }

    /** For GameTests: a bolt aimed at {@code target}, now. */
    void strikeForTest(LivingEntity target) {
        hit(target.blockPosition(), target);
    }

    /** For GameTests: a bolt into the ground at {@code spot} (the air over its surface), now. */
    void strikeForTest(BlockPos spot) {
        hit(spot, null);
    }

    /** The surface (the air over the highest solid block) at a random spot in the region, or null where it is not loaded. */
    private @Nullable BlockPos randomColumn() {
        double a = this.random.nextDouble() * 2 * Math.PI, r = this.radius * Math.sqrt(this.random.nextDouble());
        int x = (int) Math.floor(this.center.x + r * Math.cos(a)), z = (int) Math.floor(this.center.z + r * Math.sin(a));
        if (!this.level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        return this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
    }

    /** A lightning rod standing at the surface in the storm, within reach of where a bolt would land (it draws the bolt). */
    private Optional<BlockPos> rodNear(BlockPos at) {
        return this.level.getPoiManager().findClosest(p -> p.is(PoiTypes.LIGHTNING_ROD),
                rod -> rod.getY() == this.level.getHeight(Heightmap.Types.WORLD_SURFACE, rod.getX(), rod.getZ()) - 1 && inside(Vec3.atCenterOf(rod)),
                at, Storms.ROD_REACH, PoiManager.Occupancy.ANY);
    }

    /** A bolt into a lightning rod: it takes it safely and gives its redstone pulse. */
    private void grounded(BlockPos rod) {
        BlockState state = this.level.getBlockState(rod);
        if (state.getBlock() instanceof LightningRodBlock block) {
            block.onLightningStrike(state, this.level, rod);
        }
        this.strikes++;
        tell(Vec3.atCenterOf(rod).add(0, 0.5, 0), List.of());
    }

    /** A bolt coming down at {@code at} (on {@code target}, if aimed at one): what it does there. */
    private void hit(BlockPos at, @Nullable LivingEntity target) {
        Vec3 foot = target != null ? target.position() : Vec3.atBottomCenterOf(at);
        Entity source = this.by == null ? null : this.level.getEntity(this.by);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(this.level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(foot);
            bolt.setVisualOnly(true);                              // never added: only what it does to those it hits
        }
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, new AABB(foot, foot).inflate(1.5, 3, 1.5),
                e -> e.isAlive() && !spared(e))) {
            struck(e, Storms.STRIKE_DAMAGE, bolt);
            hit.add(e);
        }
        // The chain: on from the creature struck (or the bolt's foot) to the nearest not yet hit, each jump weaker.
        List<Vec3> chain = new ArrayList<>();
        Vec3 from = target != null ? target.getBoundingBox().getCenter() : foot.add(0, 0.5, 0);
        float damage = Storms.STRIKE_DAMAGE;
        for (int hop = 0; hop < Storms.CHAIN_HOPS; hop++) {
            LivingEntity next = null;
            double best = Storms.CHAIN_RANGE * Storms.CHAIN_RANGE;
            for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(Storms.CHAIN_RANGE),
                    e -> e.isAlive() && !spared(e))) {
                double d = e.getBoundingBox().getCenter().distanceToSqr(from);
                if (!hit.contains(e) && d <= best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            damage *= Storms.CHAIN_KEEP;
            struck(next, damage, null);
            hit.add(next);
            if (chain.isEmpty()) {
                chain.add(from);
            }
            from = next.getBoundingBox().getCenter();
            chain.add(from);
        }
        scorch(at);
        dev.groundburst.content.effect.EmpFields.pulse(this.level, foot, Storms.empRadius(this.radius), Storms.EMP_TICKS);
        if (this.blasts) {
            Blasts.detonate(this.level, foot, Storms.STRIKE_BLAST, source);      // its explosive impact: a small crater
        }
        this.strikes++;
        tell(foot, chain);
    }

    /** A creature struck for {@code damage} (the bolt's foot: vanilla lightning's own hit and burn as part of it). */
    private void struck(LivingEntity e, float damage, @Nullable LightningBolt bolt) {
        if (bolt != null) {
            e.thunderHit(this.level, bolt);                        // 5 and a burn; a creeper charged, a pig turned...
            damage -= 5;
        } else {
            e.igniteForSeconds(2);
        }
        if (damage > 0 && e.isAlive()) {
            e.damageCooldownTime = 0;
            e.hurtServer(this.level, this.level.damageSources().lightningBolt(), damage);
        }
    }

    /** The ground where it struck: grass burnt to dirt, sand fused to glass, leaves burnt away; a fire where fire may spread. */
    private void scorch(BlockPos at) {
        BlockPos top = at.below();
        BlockState s = this.level.getBlockState(top);
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
            this.level.setBlockAndUpdate(top, (this.random.nextFloat() < 0.4f ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState());
        } else if (s.is(BlockTags.SAND) && this.random.nextFloat() < 0.35f) {
            this.level.setBlockAndUpdate(top, Blocks.GLASS.defaultBlockState());
        } else if (s.is(BlockTags.LEAVES)) {
            this.level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
        }
        if (this.random.nextFloat() < 0.4f && this.level.canSpreadFireAround(at)) {
            BlockState fire = BaseFireBlock.getState(this.level, at);
            if (this.level.getBlockState(at).isAir() && fire.canSurvive(this.level, at)) {
                this.level.setBlockAndUpdate(at, fire);
            }
        }
    }

    /** Clients told of a strike: where it came down, the creatures it chained through, a seed for the bolt's shape. */
    private void tell(Vec3 foot, List<Vec3> chain) {
        double[] points = new double[chain.size() * 3];
        for (int i = 0; i < chain.size(); i++) {
            points[i * 3] = chain.get(i).x;
            points[i * 3 + 1] = chain.get(i).y;
            points[i * 3 + 2] = chain.get(i).z;
        }
        PacketDistributor.sendToPlayersNear(this.level, null, foot.x, foot.y, foot.z, 512,
                new StormPayloads.Strike(this.netId, foot, points, this.random.nextInt()));
    }
}
