package dev.groundburst.blast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.groundburst.content.effect.Frost;
import dev.groundburst.content.fallout.CryoSnowBlock;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.content.ordnance.GBOrdnance;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * What the slower weapons do to the hulls round them ({@link Hulls}: ships' and contraptions' blocks, which the world's
 * passes never meet), by the rules they keep for the world's ground (DESIGN.md §17.16): a Particle Uplink Cannon's beam
 * eats down through a hull from the top, scorching and kindling round it ({@link Beam}); a deep freeze ices a hull over as
 * its front passes ({@link Freeze}); a singularity draws a free hull in as it draws in creatures ({@link #drawIn}).
 */
final class HullEffects {
    private HullEffects() {}

    /** The world's column a point of the world is in. */
    private static long column(Vec3 w) {
        return BlockPos.asLong(Mth.floor(w.x), 0, Mth.floor(w.z));
    }

    // --- the particle beam ----------------------------------------------------------------------------------------------
    /** Whether the column of the world at (x, z) is spared (the protected spawn). */
    @FunctionalInterface
    interface Spared {
        boolean at(int x, int z);
    }

    /**
     * A beam's work on the hulls under it, from the sky down to its trench: in each column of the world round it, the
     * hull's topmost block there is what it meets. Within its melt that block heats, as fast as the ground's top does, and
     * is eaten through once it has taken {@link #BITE} (the ground's first block's worth), baring the one under it; beyond,
     * to its fire's reach, it is scorched once (leaves and plants burnt away, wood charred, earth and stone burnt) and may
     * catch napalm fire on top. Obsidian and bedrock stop it, as they stop its trench.
     */
    static final class Beam {
        /** The heat a block of a hull takes to be eaten through: what the ground's first block takes (a tick of the core is 1). */
        static final float BITE = 8;
        private final ServerLevel level;
        private final RandomSource random;
        private final Spared spared;
        /** Each hull's blocks' heat, and the blocks it has scorched. */
        private final Map<Object, Object2FloatOpenHashMap<BlockPos>> heat = new HashMap<>();
        private final Map<Object, Set<BlockPos>> scorched = new HashMap<>();

        Beam(ServerLevel level, RandomSource random, Spared spared) {
            this.level = level;
            this.random = random;
            this.spared = spared;
        }

        /** A tick of the beam standing at (x, z), its foot on the ground at {@code y}. */
        void burn(double x, double y, double z) {
            if (!Hulls.any()) {
                return;
            }
            float reach = ParticleBeams.FIRE;
            AABB box = new AABB(x - reach, y - ParticleBeams.MAX_DEPTH - 1, z - reach, x + reach, (this.level.getMaxBuildHeight() - 1) + 1, z + reach);
            Vec3 mid = box.getCenter();
            double radius = Math.max(box.getXsize(), box.getYsize()) / 2;
            for (Hulls.Hull hull : Hulls.near(this.level, mid, radius)) {
                burn(hull, box, x, z);
                hull.flush();
            }
        }

        private void burn(Hulls.Hull hull, AABB box, double x, double z) {
            AABB local = Hulls.toLocal(hull, box);
            if (!local.intersects(hull.bounds())) {
                return;
            }
            // The hull's topmost block in each column of the world round it: what the beam, coming down, meets first.
            Long2ObjectOpenHashMap<BlockPos> tops = new Long2ObjectOpenHashMap<>();
            Map<Long, Double> topY = new HashMap<>();
            hull.blocks(local.intersect(hull.bounds()), (pos, state) -> {
                Vec3 w = hull.toWorld(Vec3.atCenterOf(pos));
                if (w.y < box.minY || w.y > box.maxY || Math.hypot(w.x - x, w.z - z) > ParticleBeams.FIRE) {
                    return;
                }
                long c = column(w);
                Double was = topY.get(c);
                if (was == null || w.y > was) {
                    tops.put(c, pos.immutable());
                    topY.put(c, w.y);
                }
            });
            Object2FloatOpenHashMap<BlockPos> heat = this.heat.computeIfAbsent(hull.key(), k -> new Object2FloatOpenHashMap<>());
            Set<BlockPos> scorched = this.scorched.computeIfAbsent(hull.key(), k -> new HashSet<>());
            for (BlockPos pos : tops.values()) {
                Vec3 w = hull.toWorld(Vec3.atCenterOf(pos));
                if (this.spared.at(Mth.floor(w.x), Mth.floor(w.z))) {
                    continue;
                }
                double d = Math.hypot(w.x - x, w.z - z);
                BlockState state = hull.state(pos);
                if (d <= ParticleBeams.MELT) {
                    float h = heat.addTo(pos, ParticleBeamJob.warmth(d)) + ParticleBeamJob.warmth(d);
                    if (h >= BITE && yields(hull, pos, state)) {
                        hull.destroy(pos);                       // eaten through: the beam meets the block under it next
                        heat.removeFloat(pos);
                    }
                } else {
                    if (scorched.add(pos)) {
                        scorch(hull, pos, state);
                    }
                    kindle(hull, pos, d);
                }
            }
        }

        /** What the beam eats through: anything a pick could dig short of obsidian, and water, which boils away. */
        private boolean yields(Hulls.Hull hull, BlockPos pos, BlockState s) {
            if (hull.holds(pos, s)) {
                return false;
            }
            if (!s.getFluidState().isEmpty()) {
                return s.getFluidState().is(FluidTags.WATER);
            }
            BlockPos at = hull.inLevel(pos);
            float hardness = s.getDestroySpeed(this.level, at != null ? at : pos);
            return hardness >= 0 && hardness < 50;
        }

        /** A block round it, once: leaves and plants burnt away, napalm out; else burnt as the nuclear fireball burns. */
        private void scorch(Hulls.Hull hull, BlockPos pos, BlockState s) {
            if (s.is(BlockTags.LEAVES) || s.canBeReplaced() && s.getFluidState().isEmpty() || s.is(GBOrdnance.NAPALM_FIRE.get())) {
                hull.destroy(pos);
                return;
            }
            if (!s.getFluidState().isEmpty()) {
                return;
            }
            BlockState burnt = TerrainTable.byId(TerrainTable.NUCLEAR).scorched(s);
            if (burnt != null && !burnt.isAir() && burnt.getFluidState().isEmpty()) {
                hull.set(pos, burnt);
            }
        }

        /** A block round it outside its melt may catch napalm fire on top this tick, more likely nearer (as the ground does). */
        private void kindle(Hulls.Hull hull, BlockPos pos, double d) {
            double u = (d - ParticleBeams.MELT) / (ParticleBeams.FIRE - ParticleBeams.MELT);
            if (this.random.nextFloat() >= ParticleBeamJob.FIRE_CHANCE * (1 - u)) {
                return;
            }
            BlockPos above = pos.above();
            BlockPos at = hull.inLevel(above);
            BlockState fire = GBOrdnance.NAPALM_FIRE.get().defaultBlockState();
            if (at != null && hull.state(above).isAir() && fire.canSurvive(this.level, at)) {
                hull.set(above, fire);
            }
        }
    }

    // --- the deep freeze ------------------------------------------------------------------------------------------------
    /**
     * A deep freeze's work on the hulls round it, as its front passes their blocks, nearest first (found when the front
     * first reaches each hull): in its heart's sphere every block freezes as the world's do there (water to ice, lava
     * crusted, fires out, glass shattered, plants frozen, open stone and grass rimed, now and then a crystal); beyond it,
     * out to its reach and no higher than its cold climbs, each block open to the sky is iced as the land is (fires out,
     * plants frozen, water frozen, stone and grass rimed) and snowed on, deeper near the middle.
     */
    static final class Freeze {
        private final FreezeJob job;
        private final Map<Object, Pass> passes = new HashMap<>();
        private int shattered;

        Freeze(FreezeJob job) {
            this.job = job;
        }

        /** The front has run out to {@code front}: what of the hulls it has passed is frozen. */
        void advance(float front) {
            if (!Hulls.any()) {
                return;
            }
            this.shattered = 0;
            for (Hulls.Hull hull : Hulls.near(this.job.level, this.job.center, front)) {
                this.passes.computeIfAbsent(hull.key(), k -> new Pass()).run(hull, front);
                hull.flush();
            }
        }

        /** A block of a hull the front will reach: how far from the middle over the ground, and through the air. */
        private record Target(BlockPos pos, float across, float through) {}

        private final class Pass {
            private @Nullable List<Target> targets;
            private int next;

            void run(Hulls.Hull hull, float front) {
                FreezeJob j = Freeze.this.job;
                if (this.targets == null) {
                    this.targets = gather(hull);
                }
                while (this.next < this.targets.size() && this.targets.get(this.next).across() <= front) {
                    Target t = this.targets.get(this.next++);
                    BlockState state = hull.state(t.pos());
                    if (state.isAir()) {
                        continue;
                    }
                    boolean open = hull.state(t.pos().above()).isAir();
                    if (t.through() <= j.core) {
                        heart(hull, t.pos(), state, open);
                    } else if (open) {
                        Vec3 w = hull.toWorld(Vec3.atCenterOf(t.pos()));
                        if (w.y - j.center.y <= j.climb) {               // above where its cold climbs, nothing freezes
                            surface(hull, t.pos(), state, 1 - t.across() / j.radius, t.across() <= j.core, w);
                        }
                    }
                }
            }

            /** Its blocks within reach (over the ground), nearest first. */
            private List<Target> gather(Hulls.Hull hull) {
                FreezeJob j = Freeze.this.job;
                AABB box = new AABB(j.center.x - j.radius, j.center.y - j.radius, j.center.z - j.radius, j.center.x + j.radius,
                        j.center.y + Math.max(j.climb, j.core) + 1, j.center.z + j.radius);
                AABB local = Hulls.toLocal(hull, box);
                List<Target> out = new ArrayList<>();
                if (!local.intersects(hull.bounds())) {
                    return out;
                }
                hull.blocks(local.intersect(hull.bounds()), (pos, state) -> {
                    Vec3 w = hull.toWorld(Vec3.atCenterOf(pos));
                    double across = Math.hypot(w.x - j.center.x, w.z - j.center.z);
                    if (across <= j.radius) {
                        out.add(new Target(pos.immutable(), (float) across, (float) w.distanceTo(j.center)));
                    }
                });
                out.sort((a, b) -> Float.compare(a.across(), b.across()));
                return out;
            }
        }

        /** A block in the heart's sphere frozen through, as the world's are there. */
        private void heart(Hulls.Hull hull, BlockPos pos, BlockState s, boolean open) {
            RandomSource random = this.job.random();
            BlockState ice = FreezeJob.frozenWater(s);
            if (ice != null) {
                hull.set(pos, ice);
            } else if (s.is(Blocks.LAVA)) {
                hull.set(pos, (s.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
            } else if (s.getBlock() instanceof BaseFireBlock || s.is(GBOrdnance.NAPALM_FIRE.get())) {
                hull.destroy(pos);
            } else if (s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)) {
                hull.destroy(pos);                                   // cracked by the cold, it falls apart
                if (this.shattered++ < 3) {
                    BlockPos w = BlockPos.containing(hull.toWorld(Vec3.atCenterOf(pos)));
                    this.job.level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, w, Block.getId(s));
                    this.job.level.playSound(null, w, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f + 0.3f * random.nextFloat());
                }
            } else if (Frost.plant(s) || s.is(BlockTags.CROPS)) {
                freezePlant(hull, pos, s);
            } else if (open && Frost.frostable(s) && random.nextFloat() < 0.6f) {
                if (hull.set(pos, frosted(s)) && random.nextFloat() < 0.035f) {
                    BlockPos up = pos.above(), at = hull.inLevel(up);
                    BlockState crystal = GBFallout.CRYO_CRYSTAL.block().get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
                    if (at != null && crystal.canSurvive(this.job.level, at)) {
                        hull.set(up, crystal);
                    }
                }
            }
        }

        /**
         * A block open to the sky outside the heart, as the land's top is: fires out, plants frozen, water frozen, lava
         * crusted, stone and grass rimed ({@code k}: 1 at the middle, 0 at the rim), then snow over it in drifts.
         */
        private void surface(Hulls.Hull hull, BlockPos pos, BlockState s, double k, boolean heart, Vec3 w) {
            RandomSource random = this.job.random();
            if (s.getBlock() instanceof BaseFireBlock || s.is(GBOrdnance.NAPALM_FIRE.get())) {
                hull.destroy(pos);
                return;
            }
            if (Frost.plant(s) || s.is(BlockTags.CROPS)) {
                freezePlant(hull, pos, s);
                return;
            }
            double drift = this.job.drift(Mth.floor(w.x), Mth.floor(w.z));
            double depth = 3.2 * k * k + 1.1 + 0.8 * drift - 0.5 * (1 - k);
            BlockState ice = FreezeJob.frozenWater(s);
            if (ice != null) {
                hull.set(pos, ice);
                depth = drift > 0.25 ? 1 : 0;                        // snow lies on the new ice in patches
            } else if (s.is(Blocks.LAVA)) {
                hull.set(pos, (s.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
                depth = 0;
            } else if (Frost.frostable(s) && random.nextFloat() < 0.55f + 0.45f * k) {
                hull.set(pos, frosted(s));
            }
            BlockPos up = pos.above(), at = hull.inLevel(up);
            if (depth >= 0.75 && at != null) {
                int layers = Math.min(6, Math.max(1, (int) Math.round(depth)) + (heart ? 1 : 0));
                BlockState laid = GBFallout.CRYO_SNOW.block().get().defaultBlockState().setValue(CryoSnowBlock.LAYERS, layers);
                if (laid.canSurvive(this.job.level, at)) {
                    hull.set(up, laid);
                }
            }
        }

        private static BlockState frosted(BlockState s) {
            return (s.is(Blocks.STONE) ? GBFallout.FROSTED_STONE : GBFallout.FROSTED_GRASS_BLOCK).block().get().defaultBlockState();
        }

        /** A plant frozen solid where one can stand (in the level), else gone; a tall plant's top half goes. */
        private void freezePlant(Hulls.Hull hull, BlockPos pos, BlockState s) {
            boolean upper = s.hasProperty(DoublePlantBlock.HALF) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
            BlockState frozen = GBFallout.FROZEN_PLANT.block().get().defaultBlockState();
            BlockPos at = hull.inLevel(pos);
            if (upper || at == null || !frozen.canSurvive(this.job.level, at) || !hull.set(pos, frozen)) {
                hull.destroy(pos);
            }
            if (!upper && s.hasProperty(DoublePlantBlock.HALF) && hull.state(pos.above()).is(s.getBlock())) {
                hull.destroy(pos.above());
            }
        }
    }

    // --- the singularity --------------------------------------------------------------------------------------------------
    /**
     * A singularity at {@code center} draws in every free hull within {@code pull} as it draws in creatures (its pull of
     * strength {@code gm}, swirl and all), the whole hull alike, taken at its middle.
     */
    static void drawIn(ServerLevel level, Vec3 center, float pull, double gm) {
        if (!Hulls.any()) {
            return;
        }
        for (Hulls.Hull hull : Hulls.near(level, center, pull)) {
            hull.pull(at -> {
                Vec3 to = center.subtract(at);
                return to.length() > pull ? Vec3.ZERO : Singularities.pullOn(to, gm);
            });
        }
    }
}
