package dev.groundburst.gametest;

import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.blast.Blasts;
import dev.groundburst.blast.Hulls;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ships' and contraptions' blocks, which other mods keep apart from where they are seen (DESIGN.md §17.16), stood in for
 * by a hull whose blocks are kept ten blocks over the place it shows them (as a ship's are kept in a plot far off): a
 * blast breaks them where they are seen, by the world's rules, and shoves the hull away; one out of its reach is left
 * alone; a flight strikes them where they are seen.
 */
final class HullTests {
    private HullTests() {}

    static void register() {
        GBGameTests.testOpenSky("blast_breaks_a_hull_where_it_is_seen", GBGameTests.FLOOR_16, 20, HullTests::breaks);
        GBGameTests.testOpenSky("blast_shoves_a_hull_away", GBGameTests.FLOOR_16, 20, HullTests::shoves);
        GBGameTests.testOpenSky("blast_leaves_a_hull_out_of_reach", GBGameTests.FLOOR_16, 20, HullTests::outOfReach);
        GBGameTests.testOpenSky("flight_strikes_a_hull_where_it_is_seen", GBGameTests.FLOOR_16, 20, HullTests::clips);
    }

    /** Where the bursts are (the hull shows its blocks round it). */
    private static final BlockPos C = new BlockPos(8, 3, 8);
    /** How far over the place it shows them the hull keeps its blocks. */
    private static final int LIFT = 10;

    /** A hull keeping its blocks {@link #LIFT} blocks over where it shows them: the world's own blocks, in a box. */
    private static final class Stored implements Hulls.Hull {
        final ServerLevel level;
        final AABB bounds;
        Vec3 pushed = Vec3.ZERO;
        @Nullable Vec3 pushedAt;

        Stored(GameTestHelper helper) {
            this.level = helper.getLevel();
            this.bounds = AABB.encapsulatingFullBlocks(helper.absolutePos(C.offset(-7, LIFT - 7, -7)), helper.absolutePos(C.offset(7, LIFT + 7, 7)));
        }

        @Override
        public Object key() {
            return this;
        }

        @Override
        public Vec3 toLocal(Vec3 world) {
            return world.add(0, LIFT, 0);
        }

        @Override
        public Vec3 toWorld(Vec3 local) {
            return local.subtract(0, LIFT, 0);
        }

        @Override
        public AABB bounds() {
            return this.bounds;
        }

        @Override
        public void blocks(AABB local, BiConsumer<BlockPos, BlockState> each) {
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
        public void push(Vec3 localPoint, Vec3 localImpulse) {
            this.pushedAt = localPoint;
            this.pushed = this.pushed.add(localImpulse);
        }
    }

    /** Sets a block of the hull, given where it shows it (relative to the test). */
    private static void put(GameTestHelper helper, BlockPos shown, Block block) {
        helper.setBlock(shown.offset(0, LIFT, 0), block);
    }

    private static BlockState kept(GameTestHelper helper, BlockPos shown) {
        return helper.getLevel().getBlockState(helper.absolutePos(shown.offset(0, LIFT, 0)));
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
        Hulls.Finder finder = (level, center, radius, out) -> {
            if (level == hull.level && center.distanceTo(burst) < 1) {
                out.accept(hull);
            }
        };
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
}
