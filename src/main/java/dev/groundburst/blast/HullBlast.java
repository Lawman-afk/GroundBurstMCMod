package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A blast's reach into the hulls round it ({@link Hulls}: ships' and contraptions' blocks, which the world's rays never
 * meet). As its front passes a hull's blocks it breaks them by the world's rules (DESIGN.md §17.16): everything in its
 * core; in its crater, each as far as a ray's energy carries through the hull's blocks between it and the burst; light
 * blocks (glass, leaves, doors) blown out to its structural radius where nothing solid stands in front of them. What it
 * sees in the open and leaves standing is scorched in its scorch band as the world's ground is (a nuclear fireball chars
 * wood) and may catch fire on top in its fire band (ash, after a nuclear one), where the blocks stand in the level (a
 * ship's). And it shoves a hull that moves freely as it shoves entities: every block it reaches in the open is given a
 * push away from the burst, so a ship is driven off and turned by the side that faces it.
 */
final class HullBlast {
    /** A block's shove, in its mass times blocks a second, for each unit of the profile's knockback (an entity's is a block a tick). */
    static final float SHOVE = 20;

    private final ServerLevel level;
    private final Vec3 center;
    private final BlastProfile profile;
    private final boolean terrain;
    private final TerrainTable table;
    private final @Nullable BlockPos protectedCenter;
    private final int protectedRadius;
    private final RandomSource random;
    /** How far it breaks a hull's blocks, how far it shoves, how far it scorches or sets alight; the furthest of them. */
    private final float breaks, shoves, burns, reach;
    /** An erasing sphere's (antimatter's, a singularity's): it takes a hull's blocks where they are each tick, as the hull moves. */
    private final boolean erasing;
    /** A ray's energy, as the world's level rays start with it ({@link BlastJob}). */
    private final float energy;
    private final Map<Object, Pass> passes = new HashMap<>();
    /** How far the front has got. */
    private float front;

    HullBlast(ServerLevel level, Vec3 center, BlastProfile profile, boolean terrain, @Nullable BlockPos protectedCenter, int protectedRadius,
            RandomSource random) {
        this(level, center, profile, terrain, protectedCenter, protectedRadius, random, false);
    }

    private HullBlast(ServerLevel level, Vec3 center, BlastProfile profile, boolean terrain, @Nullable BlockPos protectedCenter,
            int protectedRadius, RandomSource random, boolean erasing) {
        this.level = level;
        this.center = center;
        this.profile = profile;
        this.terrain = terrain;
        this.table = TerrainTable.byId(profile.terrain());
        this.protectedCenter = protectedCenter;
        this.protectedRadius = protectedRadius;
        this.random = random;
        this.breaks = terrain ? Math.max(Math.max(profile.crater(), profile.core()), profile.structural()) : 0;
        this.shoves = Math.max(profile.damage(), profile.lethal());
        this.burns = terrain ? Math.max(profile.scorch(), profile.fire()) : 0;
        this.reach = Math.max(Math.max(this.breaks, this.shoves), this.burns);
        this.erasing = erasing;
        this.energy = profile.crater() * BlastJob.cost(6f);
    }

    /**
     * A sphere that erases whatever of a hull it reaches as it grows to {@code radius}, as an antimatter annihilation or a
     * singularity's horizon does the world's (they shove nothing): every block but those nothing breaks.
     */
    static HullBlast erasing(ServerLevel level, Vec3 center, float radius) {
        BlastProfile all = new BlastProfile(radius, radius, 0, 0, 0, BlastProfile.EntityZones.NONE, 1, 1, 0, 0, 0, 0, TerrainTable.CONVENTIONAL,
                java.util.Optional.of(false), java.util.Optional.empty(), java.util.Optional.empty());
        return new HullBlast(level, center, all, true, null, 0, level.getRandom(), true);
    }

    /** The front has reached {@code r} blocks: the hulls' blocks within it are dealt with. */
    void reach(float r) {
        float to = Math.min(r, this.reach);
        if (to <= this.front || this.reach <= 0) {
            return;
        }
        this.front = to;
        for (Hulls.Hull hull : Hulls.near(this.level, this.center, to)) {
            this.passes.computeIfAbsent(hull.key(), k -> new Pass()).run(hull, to);
        }
    }

    /** A hull block the front will reach, and how far it is from the burst. */
    private record Target(BlockPos pos, float distance) {}

    /** The blast's work on one hull. */
    private final class Pass {
        /** Its blocks in reach, nearest first (found when the front first reaches it); how many are done. */
        private @Nullable List<Target> targets;
        private int next;
        /** The blocks it has broken and what each cost the ray that broke it (they still stood in the way of the rays behind). */
        private final Object2FloatOpenHashMap<BlockPos> broken = new Object2FloatOpenHashMap<>();

        void run(Hulls.Hull hull, float to) {
            Vec3 local = hull.toLocal(HullBlast.this.center);
            double scale = hull.scale();
            if (this.targets == null || HullBlast.this.erasing) {
                this.targets = gather(hull, local, scale);       // (an erasing sphere's: where they are now)
                this.next = 0;
            }
            Vec3 impulse = Vec3.ZERO, moment = Vec3.ZERO;
            double weight = 0;
            while (this.next < this.targets.size() && this.targets.get(this.next).distance() <= to) {
                Target t = this.targets.get(this.next++);
                BlockState state = hull.state(t.pos());
                if (state.isAir()) {
                    continue;
                }
                Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(t.pos());
                Path path = path(hull, local, at, t.pos(), scale);
                float d = t.distance();
                boolean broke = false;
                if (d <= HullBlast.this.breaks && breakable(hull, t.pos(), state)) {
                    float cost = state.getBlock() instanceof LiquidBlock ? BlastJob.FLUID_COST : BlastJob.cost(state.getBlock().getExplosionResistance());
                    if (d <= HullBlast.this.profile.core()) {
                        broke = true;
                        cost = 0;
                    } else if (d <= HullBlast.this.profile.crater()) {
                        broke = path.cost + cost <= HullBlast.this.energy;
                    } else {
                        broke = d <= HullBlast.this.profile.structural() && !path.solid && HullBlast.this.table.blowsOut(state);
                    }
                    if (broke) {
                        this.broken.put(t.pos(), cost);
                        destroy(hull, t.pos(), state);
                    }
                }
                if (!broke && !path.standing && d <= HullBlast.this.burns && !sheltered(hull, t.pos())) {
                    burn(hull, t.pos(), state, d);                  // seen from the fireball, and left standing
                }
                if (!broke && !path.standing && d <= HullBlast.this.shoves) {
                    // In the open: the shock shoves the block, and the hull with it, straight away from the burst.
                    double falloff = Math.max(0, 1 - d / Math.max(1, HullBlast.this.shoves));
                    double j = SHOVE * HullBlast.this.profile.knockback() * falloff;
                    Vec3 away = at.subtract(local);
                    if (j > 0 && away.lengthSqr() > 1e-6) {
                        impulse = impulse.add(away.normalize().scale(j));
                        moment = moment.add(at.scale(j));
                        weight += j;
                    }
                }
            }
            if (weight > 0) {
                hull.push(moment.scale(1 / weight), impulse);    // all of it at its centre of pressure
            }
            hull.flush();
        }

        /** Its blocks within reach of the burst, nearest first. */
        private List<Target> gather(Hulls.Hull hull, Vec3 local, double scale) {
            double r = HullBlast.this.reach / scale;
            AABB box = new AABB(local, local).inflate(r + 1);
            List<Target> out = new ArrayList<>();
            if (!box.intersects(hull.bounds())) {
                return out;
            }
            hull.blocks(box.intersect(hull.bounds()), (pos, state) -> {
                float d = (float) (Vec3.atCenterOf(pos).distanceTo(local) * scale);
                if (d <= HullBlast.this.reach) {
                    out.add(new Target(pos.immutable(), d));
                }
            });
            out.sort((a, b) -> Float.compare(a.distance(), b.distance()));
            return out;
        }

        /** What stands between the burst and a block of the hull (not the block itself). */
        private Path path(Hulls.Hull hull, Vec3 from, Vec3 to, BlockPos target, double scale) {
            Path path = new Path();
            path.cost = (float) (from.distanceTo(to) * scale) * BlastJob.AIR_COST;
            AABB bounds = hull.bounds();
            BlockGetter.traverseBlocks(from, to, path, (p, at) -> {
                if (at.equals(target) || !bounds.intersects(new AABB(at))) {
                    return null;
                }
                float spent = this.broken.getOrDefault(at, -1f);
                if (spent >= 0) {
                    p.cost += spent;                       // broken already, by a ray that paid for it on its way
                    return null;
                }
                BlockState state = hull.state(at);
                if (state.isAir()) {
                    return null;
                }
                p.standing = true;
                p.solid |= !HullBlast.this.table.blowsOut(state);
                boolean core = Vec3.atCenterOf(at).distanceTo(from) * scale <= HullBlast.this.profile.core();
                if (!core) {
                    p.cost += state.getBlock() instanceof LiquidBlock ? BlastJob.FLUID_COST : BlastJob.cost(state.getBlock().getExplosionResistance());
                }
                return p.cost > HullBlast.this.energy && p.solid ? Boolean.TRUE : null;   // nothing more to learn
            }, p -> null);
            return path;
        }

        /** Whether the blast may break it: not bedrock and the like, not in the protected spawn. */
        private boolean breakable(Hulls.Hull hull, BlockPos pos, BlockState state) {
            BlockPos inLevel = hull.inLevel(pos);
            if (state.getDestroySpeed(HullBlast.this.level, inLevel != null ? inLevel : pos) < 0 || hull.holds(pos, state)) {
                return false;
            }
            return !sheltered(hull, pos);
        }

        /** Whether it is over the protected spawn, where a blast changes nothing. */
        private boolean sheltered(Hulls.Hull hull, BlockPos pos) {
            if (HullBlast.this.protectedCenter == null) {
                return false;
            }
            BlockPos w = BlockPos.containing(hull.toWorld(Vec3.atCenterOf(pos)));
            return Math.abs(w.getX() - HullBlast.this.protectedCenter.getX()) <= HullBlast.this.protectedRadius
                    && Math.abs(w.getZ() - HullBlast.this.protectedCenter.getZ()) <= HullBlast.this.protectedRadius;
        }

        /**
         * A block the fireball sees and leaves standing, {@code d} from it: scorched in its scorch band, as the world's open
         * ground is; in its fire band it may catch fire on top (ash, after a nuclear blast), more likely nearer, where the
         * hull's blocks stand in the level (a contraption's do not burn).
         */
        private void burn(Hulls.Hull hull, BlockPos pos, BlockState state, float d) {
            BlastProfile p = HullBlast.this.profile;
            RandomSource random = HullBlast.this.random;
            if (d <= p.scorch() && random.nextFloat() > 0.08f) {
                BlockState scorched = HullBlast.this.table.scorched(state);
                if (scorched != null && hull.set(pos, scorched)) {
                    state = scorched;
                }
            }
            if (d > p.fire() || state.isAir() || !state.getFluidState().isEmpty()
                    || random.nextFloat() >= 0.6f * (1 - d / Math.max(1, p.fire()))) {
                return;
            }
            BlockPos above = pos.above();
            BlockPos at = hull.inLevel(above);
            if (at == null || !hull.state(above).isAir()) {
                return;
            }
            if (HullBlast.this.table.nuclear() && random.nextFloat() < BlastJob.ASH_SHARE) {
                hull.set(above, dev.groundburst.content.fallout.GBFallout.ASH.block().get().layers(1 + random.nextInt(2)));
            } else if (BaseFireBlock.canBePlacedAt(HullBlast.this.level, at, Direction.UP)) {
                hull.set(above, BaseFireBlock.getState(HullBlast.this.level, at));
            }
        }

        /** Breaks it: a charge caught in it goes off a moment later; it may drop itself where it was (small blasts). */
        private void destroy(Hulls.Hull hull, BlockPos pos, BlockState state) {
            BlockPos inLevel = hull.inLevel(pos);
            BlockPos world = BlockPos.containing(hull.toWorld(Vec3.atCenterOf(pos)));
            if (state.getBlock() instanceof dev.groundburst.content.ordnance.charge.ChargeBlock) {
                dev.groundburst.content.ordnance.charge.Charges.caughtInBlast(HullBlast.this.level, inLevel != null ? inLevel : world, state);
            }
            if (HullBlast.this.profile.drops() > 0 && HullBlast.this.random.nextFloat() < HullBlast.this.profile.drops()) {
                for (ItemStack stack : Block.getDrops(state, HullBlast.this.level, world, null)) {
                    Block.popResource(HullBlast.this.level, world, stack);
                }
            }
            hull.destroy(pos);
        }
    }

    /** What lies on a ray's way to a block: what it costs to get through, whether anything stands there now, and anything solid. */
    private static final class Path {
        float cost;
        boolean standing, solid;
    }
}
