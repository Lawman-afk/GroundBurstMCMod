package dev.groundburst.gametest;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Blasts;
import dev.groundburst.blast.Hulls;
import dev.groundburst.content.fallout.GBFallout;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ships' and contraptions' blocks, which other mods keep apart from where they are seen (DESIGN.md §17.16), stood in for
 * by a hull whose blocks are kept away from the place it shows them (as a ship's are kept in a plot far off): a blast
 * breaks them where they are seen, by the world's rules, scorches and sets alight what it sees of them and shoves the
 * hull away; one out of its reach is left alone; a flight strikes them where they are seen; a particle beam eats down
 * through them, a deep freeze ices them over, a singularity draws the hull in and erases what comes into its horizon.
 */
final class HullTests {
    private HullTests() {}

    static void register() {
        GBGameTests.testOpenSky("blast_breaks_a_hull_where_it_is_seen", GBGameTests.FLOOR_16, 20, HullTests::breaks);
        GBGameTests.testOpenSky("blast_shoves_a_hull_away", GBGameTests.FLOOR_16, 20, HullTests::shoves);
        GBGameTests.testOpenSky("blast_leaves_a_hull_out_of_reach", GBGameTests.FLOOR_16, 20, HullTests::outOfReach);
        GBGameTests.testOpenSky("flight_strikes_a_hull_where_it_is_seen", GBGameTests.FLOOR_16, 20, HullTests::clips);
        GBGameTests.testOpenSky("annihilation_erases_a_hull_inside_it", GBGameTests.FLOOR_16, 80, HullTests::annihilated);
        GBGameTests.testOpenSky("blast_scorches_a_hull_in_the_open", GBGameTests.FLOOR_16, 20, HullTests::scorches);
        GBGameTests.testOpenSky("blast_sets_a_hull_alight", GBGameTests.FLOOR_16, 60, HullTests::alight);
        GBGameTests.testOpenSky("beam_eats_down_through_a_hull", GBGameTests.FLOOR_16, 100, HullTests::beamed);
        GBGameTests.testOpenSky("freeze_ices_a_hull_over", GBGameTests.FLOOR_16, 200, HullTests::frozen);
        GBGameTests.testOpenSky("singularity_draws_a_hull_in", GBGameTests.FLOOR_16, 60, HullTests::drawnIn);
        GBGameTests.testOpenSky("singularity_erases_a_hull_drawn_into_it", GBGameTests.FLOOR_16, 120, HullTests::swallowed);
    }

    /** Where the bursts are (the hull shows its blocks round it). */
    private static final BlockPos C = new BlockPos(8, 3, 8);
    /** How far over the place it shows them the hull keeps its blocks (unless a test keeps them elsewhere). */
    private static final int LIFT = 10;

    /**
     * A hull keeping its blocks {@link #offset} from where it shows them (by default {@link #LIFT} blocks over): the
     * world's own blocks, in a box round where it keeps those it shows round {@link #C}.
     */
    private static final class Stored implements Hulls.Hull {
        final ServerLevel level;
        final AABB bounds;
        Vec3 offset;
        Vec3 pushed = Vec3.ZERO, pulled = Vec3.ZERO;
        @Nullable Vec3 pushedAt;
        /** Where its middle is shown (a pull is taken there). */
        Vec3 middle;

        Stored(GameTestHelper helper) {
            this(helper, new Vec3(0, LIFT, 0), 7);
        }

        Stored(GameTestHelper helper, Vec3 offset, int half) {
            this.level = helper.getLevel();
            this.offset = offset;
            BlockPos kept = C.offset((int) offset.x, (int) offset.y, (int) offset.z);
            this.bounds = AABB.encapsulatingFullBlocks(helper.absolutePos(kept.offset(-half, -half, -half)), helper.absolutePos(kept.offset(half, half, half)));
            this.middle = helper.absoluteVec(Vec3.atCenterOf(C));
        }

        @Override
        public Object key() {
            return this;
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            return world.add(this.offset);
        }

        @Override
        public Vec3 toWorld(Vec3 local) {
            return local.subtract(this.offset);
        }

        @Override
        public AABB bounds() {
            return this.bounds;
        }

        @Override
        public void blocks(AABB local, BiConsumer<BlockPos, BlockState> each) {
            if (!local.intersects(this.bounds)) {
                return;
            }
            AABB box = local.intersect(this.bounds);
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX - 1e-6, box.maxY - 1e-6, box.maxZ - 1e-6))) {
                BlockState state = this.level.getBlockState(p);
                if (!state.isAir()) {
                    each.accept(p, state);
                }
            }
        }

        @Override
        public BlockState state(BlockPos local) {
            return this.bounds.contains(Vec3.atCenterOf(local)) ? this.level.getBlockState(local) : Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockPos inLevel(BlockPos local) {
            return local;
        }

        @Override
        public void destroy(BlockPos local) {
            this.level.setBlock(local, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        @Override
        public boolean set(BlockPos local, BlockState state) {
            this.level.setBlock(local, state, Block.UPDATE_ALL);
            return true;
        }

        @Override
        public void push(Vec3 localPoint, Vec3 localImpulse) {
            this.pushedAt = localPoint;
            this.pushed = this.pushed.add(localImpulse);
        }

        @Override
        public void pull(Function<Vec3, Vec3> pull) {
            this.pulled = this.pulled.add(pull.apply(this.middle));
        }

        /** Where it keeps the block it shows at {@code shown} (relative to the test). */
        BlockPos kept(BlockPos shown) {
            return shown.offset((int) Math.round(this.offset.x), (int) Math.round(this.offset.y), (int) Math.round(this.offset.z));
        }
    }

    /** Sets a block of the hull, given where it shows it (relative to the test). */
    private static void put(GameTestHelper helper, BlockPos shown, Block block) {
        helper.setBlock(shown.offset(0, LIFT, 0), block);
    }

    private static BlockState kept(GameTestHelper helper, BlockPos shown) {
        return helper.getLevel().getBlockState(helper.absolutePos(shown.offset(0, LIFT, 0)));
    }

    private static void put(GameTestHelper helper, Stored hull, BlockPos shown, Block block) {
        helper.setBlock(hull.kept(shown), block);
    }

    private static BlockState kept(GameTestHelper helper, Stored hull, BlockPos shown) {
        return helper.getBlockState(hull.kept(shown));
    }

    /** A finder that hands out {@code hull} to whatever looks round {@code at} (within a block of it). */
    private static Hulls.Finder finder(Stored hull, Vec3 at) {
        return (level, center, radius, out) -> {
            if (level == hull.level && center.distanceTo(at) < 1) {
                out.accept(hull);
            }
        };
    }

    /**
     * Sets off a blast at {@link #C} with the hull findable round it, and makes it unfindable after. A stone lid over the
     * hull's store keeps the world's own crater sweep (which takes a lone block off the top of a column over the crater)
     * off its blocks: they are over the burst only because the test keeps them there.
     */
    private static void blast(GameTestHelper helper, Stored hull, String profile) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                helper.setBlock(C.offset(x, LIFT + 8, z), Blocks.STONE);
            }
        }
        Vec3 burst = helper.absoluteVec(Vec3.atCenterOf(C));
        Hulls.Finder finder = finder(hull, burst);
        Hulls.addFinder(finder);
        try {
            helper.assertTrue(Blasts.detonate(helper.getLevel(), burst, Blasts.profile(profile), null) != null, "the profile exists");
        } finally {
            Hulls.removeFinder(finder);
        }
    }

    /**
     * The world's shielding test, in a hull: of two stone blocks it shows four blocks either side of the burst, the open
     * one is broken; the one behind an obsidian wall it shows two blocks off is shielded, and the wall holds.
     */
    private static void breaks(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                put(helper, C.offset(2, y, z), Blocks.OBSIDIAN);
            }
        }
        put(helper, C.offset(4, 0, 0), Blocks.STONE);
        put(helper, C.offset(-4, 0, 0), Blocks.STONE);
        blast(helper, hull, "test/crater_only");
        helper.assertTrue(kept(helper, C.offset(-4, 0, 0)).isAir(), "the open stone is broken where it is kept");
        helper.assertTrue(kept(helper, C.offset(4, 0, 0)).is(Blocks.STONE), "the stone behind the wall is shielded");
        helper.assertTrue(kept(helper, C.offset(2, 0, 0)).is(Blocks.OBSIDIAN), "the obsidian holds");
        helper.succeed();
    }

    /** A block the hull shows five blocks east of a small blast, past its crater: unbroken, it is shoved east. */
    private static void shoves(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        put(helper, C.offset(5, 0, 0), Blocks.STONE);
        blast(helper, hull, "test/small");
        helper.assertTrue(kept(helper, C.offset(5, 0, 0)).is(Blocks.STONE), "past the crater it stands");
        helper.assertTrue(hull.pushed.x > 0 && Math.abs(hull.pushed.y) < 0.2 * hull.pushed.x && Math.abs(hull.pushed.z) < 1e-6,
                "shoved east: " + hull.pushed);
        Vec3 block = Vec3.atCenterOf(helper.absolutePos(C.offset(5, LIFT, 0)));
        helper.assertTrue(hull.pushedAt != null && hull.pushedAt.distanceTo(block) < 1e-6, "at the block, where it is kept: " + hull.pushedAt);
        helper.succeed();
    }

    /** A block the hull shows nine blocks off a blast that breaks six is left as it is. */
    private static void outOfReach(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        put(helper, C.offset(0, 0, 9), Blocks.STONE);
        put(helper, C.offset(0, 0, -5), Blocks.STONE);
        blast(helper, hull, "test/crater_only");
        helper.assertTrue(kept(helper, C.offset(0, 0, 9)).is(Blocks.STONE), "the far block stands");
        helper.assertTrue(kept(helper, C.offset(0, 0, -5)).isAir(), "the near one is broken");
        helper.assertTrue(hull.pushedAt == null, "a blast with no shock shoves nothing");
        helper.succeed();
    }

    /**
     * An antimatter sphere of radius 4 erases what the hull shows inside it, obsidian too (nothing holds against
     * annihilation), and leaves what it shows outside.
     */
    private static void annihilated(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        put(helper, C.offset(3, 0, 0), Blocks.OBSIDIAN);
        put(helper, C.offset(0, 0, 6), Blocks.STONE);
        Vec3 burst = helper.absoluteVec(Vec3.atCenterOf(C));
        Hulls.Finder finder = finder(hull, burst);
        Hulls.addFinder(finder);
        dev.groundburst.blast.Annihilations.start(helper.getLevel(), burst, 4, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(kept(helper, C.offset(3, 0, 0)).isAir(), "the obsidian it shows inside the sphere is erased");
            helper.assertTrue(kept(helper, C.offset(0, 0, 6)).is(Blocks.STONE), "the stone it shows outside stands");
            Hulls.removeFinder(finder);
        });
    }

    /** A line through the place the hull shows a block strikes the block's near face there (where it is seen, not kept). */
    private static void clips(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        put(helper, C, Blocks.STONE);
        Vec3 from = helper.absoluteVec(Vec3.atCenterOf(C.offset(-5, 0, 0))), to = helper.absoluteVec(Vec3.atCenterOf(C.offset(5, 0, 0)));
        Vec3 hit = Hulls.clip(hull, from, to);
        double face = helper.absolutePos(C).getX();
        helper.assertTrue(hit != null && Math.abs(hit.x - face) < 1e-6 && Math.abs(hit.y - from.y) < 1e-6 && Math.abs(hit.z - from.z) < 1e-6,
                "struck at the block's west face where it is seen: " + hit);
        helper.assertTrue(Hulls.clip(hull, from, from.add(0, 0, 4)) == null, "a line past it strikes nothing");
        helper.succeed();
    }

    /** The places round {@link #C}, {@code r} off at its height, of a ring of {@code n}. */
    private static BlockPos[] ring(double r, int n) {
        BlockPos[] out = new BlockPos[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            out[i] = C.offset((int) Math.round(r * Math.cos(a)), 0, (int) Math.round(r * Math.sin(a)));
        }
        return out;
    }

    /** Grass the hull shows in a ring five blocks round a scorching blast, past its crater and in the open: scorched to dirt. */
    private static void scorches(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        BlockPos[] grass = ring(5, 12);
        for (BlockPos p : grass) {
            put(helper, p, Blocks.GRASS_BLOCK);
        }
        blast(helper, hull, "test/scorch");
        int scorched = 0;
        for (BlockPos p : grass) {
            scorched += kept(helper, p).is(Blocks.DIRT) ? 1 : 0;
        }
        helper.assertTrue(scorched >= 8, "most of the grass it shows round the burst is scorched where it is kept: " + scorched + " of 12");
        helper.succeed();
    }

    /**
     * Bedrock the hull shows in a ring five blocks round a blast with a fire band of ten, kept forty blocks over (out of
     * the world's own blast): nothing breaks it, and in the open some of it catches fire on top where it is kept.
     */
    private static void alight(GameTestHelper helper) {
        Stored hull = new Stored(helper, new Vec3(0, 40, 0), 7);
        BlockPos[] ring = ring(5, 24);
        for (BlockPos p : ring) {
            put(helper, hull, p, Blocks.BEDROCK);
        }
        Vec3 burst = helper.absoluteVec(Vec3.atCenterOf(C));
        Hulls.Finder finder = finder(hull, burst);
        Hulls.addFinder(finder);
        helper.assertTrue(Blasts.detonate(helper.getLevel(), burst, Blasts.profile("test/medium"), null) != null, "the profile exists");
        helper.succeedWhen(() -> {
            int fires = 0;
            for (BlockPos p : ring) {
                helper.assertTrue(kept(helper, hull, p).is(Blocks.BEDROCK), "the bedrock holds");
                fires += kept(helper, hull, p.above()).getBlock() instanceof BaseFireBlock ? 1 : 0;
            }
            helper.assertTrue(fires >= 1, "some of it caught fire on top: " + fires);
            Hulls.removeFinder(finder);
        });
    }

    /**
     * A particle beam comes down on two stone blocks the hull shows stacked over the ground at its core and grass it shows
     * six blocks off: it eats down through both, from the top, and scorches the grass. The hull keeps them eight blocks
     * west of where it shows them, out of the beam's reach.
     */
    private static void beamed(GameTestHelper helper) {
        Stored hull = new Stored(helper, new Vec3(-8, 0, 0), 7);
        BlockPos low = C.offset(0, 2, 0), high = low.above(), grass = C.offset(0, 2, 6);
        put(helper, hull, low, Blocks.STONE);
        put(helper, hull, high, Blocks.STONE);
        put(helper, hull, grass, Blocks.GRASS_BLOCK);
        BlockPos aim = helper.absolutePos(C);
        Hulls.Finder finder = (level, center, radius, out) -> {
            if (level == hull.level && Math.hypot(center.x - (aim.getX() + 0.5), center.z - (aim.getZ() + 0.5)) < 1) {
                out.accept(hull);
            }
        };
        Hulls.addFinder(finder);
        dev.groundburst.blast.ParticleBeams.burnOverride = 80;
        try {
            dev.groundburst.blast.ParticleBeams.strike(helper.getLevel(), aim, null, false);
        } finally {
            dev.groundburst.blast.ParticleBeams.burnOverride = 0;
        }
        boolean[] topFirst = {true};
        helper.onEachTick(() -> topFirst[0] &= !(kept(helper, hull, low).isAir() && !kept(helper, hull, high).isAir()));
        helper.succeedWhen(() -> {
            helper.assertTrue(kept(helper, hull, high).isAir() && kept(helper, hull, low).isAir(), "both blocks it shows in the core eaten through");
            helper.assertTrue(topFirst[0], "from the top down");
            helper.assertTrue(kept(helper, hull, grass).is(GBFallout.SCORCHED_EARTH.block().get()), "the grass round it scorched: " + kept(helper, hull, grass));
            Hulls.removeFinder(finder);
        });
    }

    /**
     * A deep freeze of radius ten: glass the hull shows beside where it struck (in its heart) shatters, and stone it shows
     * in a ring six off, open to the sky, is rimed or snowed on where it is kept (ten blocks over: above where the world's
     * own cold climbs).
     */
    private static void frozen(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        put(helper, C.offset(1, 0, 0), Blocks.GLASS);
        BlockPos[] stone = ring(6, 12);
        for (BlockPos p : stone) {
            put(helper, p, Blocks.STONE);
        }
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(C));
        Hulls.Finder finder = finder(hull, at);
        Hulls.addFinder(finder);
        dev.groundburst.blast.Freezes.start(helper.getLevel(), at, 10, 60, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(kept(helper, C.offset(1, 0, 0)).isAir(), "the glass in its heart shattered");
            int iced = 0;
            for (BlockPos p : stone) {
                iced += kept(helper, p).is(GBFallout.FROSTED_STONE.block().get()) || kept(helper, p.above()).is(GBFallout.CRYO_SNOW.block().get()) ? 1 : 0;
            }
            helper.assertTrue(iced >= 6, "most of the stone it shows rimed or snowed on: " + iced + " of 12");
            Hulls.removeFinder(finder);
        });
    }

    /** A small singularity five blocks west of the hull's middle pulls it west, toward itself, and swirls it a little. */
    private static void drawnIn(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        hull.middle = helper.absoluteVec(Vec3.atCenterOf(C.offset(5, 0, 0)));
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(C));
        Hulls.Finder finder = finder(hull, at);
        Hulls.addFinder(finder);
        dev.groundburst.blast.Singularities.start(helper.getLevel(), at, 4, 0.56f, 6, 40, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(hull.pulled.x < -1e-3, "pulled west, toward it: " + hull.pulled);
            helper.assertTrue(Math.abs(hull.pulled.z) < Math.abs(hull.pulled.x), "mostly in, a little round: " + hull.pulled);
            Hulls.removeFinder(finder);
        });
    }

    /**
     * Stone the hull shows six blocks east of a singularity, the hull drawn west a quarter block a tick (as its pull would
     * draw it): once it is shown inside the horizon, it is erased where it is kept.
     */
    private static void swallowed(GameTestHelper helper) {
        Stored hull = new Stored(helper);
        BlockPos shown = C.offset(6, 0, 0);
        BlockPos keptAt = hull.kept(shown);
        put(helper, shown, Blocks.STONE);
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(C));
        Hulls.Finder finder = finder(hull, at);
        Hulls.addFinder(finder);
        dev.groundburst.blast.Singularities.start(helper.getLevel(), at, 4, 0.56f, 6, 100, null);
        helper.onEachTick(() -> {
            if (hull.offset.x < 6) {
                hull.offset = hull.offset.add(0.25, 0, 0);       // what it keeps at keptAt is shown a quarter block further west
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hull.offset.x >= 6, "drawn all the way in");
            helper.assertTrue(helper.getBlockState(keptAt).isAir(), "the stone, drawn into the horizon, erased");
            Hulls.removeFinder(finder);
        });
    }
}
