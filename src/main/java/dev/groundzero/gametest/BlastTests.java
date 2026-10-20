package dev.groundzero.gametest;

import dev.groundzero.blast.Blast;
import dev.groundzero.blast.Blasts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Phase 5 slice 5a: the explosion engine (instant blasts, shielding, a front that takes several ticks, exposure, no item spew). */
final class BlastTests {
    private BlastTests() {}

    static void register() {
        GZGameTests.test("blast_small_is_instant", GZGameTests.FLOOR_16, 20, BlastTests::smallInstant);
        GZGameTests.test("blast_dense_blocks_shield", GZGameTests.FLOOR_16, 20, BlastTests::shielding);
        GZGameTests.test("blast_front_takes_several_ticks", GZGameTests.FLOOR_16, 80, BlastTests::slowFront);
        GZGameTests.test("blast_damage_follows_exposure", GZGameTests.FLOOR_16, 20, BlastTests::exposure);
        GZGameTests.test("blast_voids_destroyed_containers", GZGameTests.FLOOR_16, 20, BlastTests::noSpew);
        GZGameTests.test("blast_reaches_blocks_above_it", GZGameTests.FLOOR_16, 20, BlastTests::upward);
    }

    private static final BlockPos C = new BlockPos(8, 4, 8);

    /** Fills a box (inclusive, relative to the test) with a block. */
    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), block);
                }
            }
        }
    }

    private static Vec3 center(GameTestHelper helper) {
        return helper.absoluteVec(Vec3.atCenterOf(C));
    }

    private static boolean air(GameTestHelper helper, int dx, int dy, int dz) {
        return helper.getLevel().getBlockState(helper.absolutePos(C.offset(dx, dy, dz))).isAir();
    }

    /** A small charge in a stone block clears a ball round itself in the same tick and leaves the stone further out. */
    private static void smallInstant(GameTestHelper helper) {
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        Blast blast = Blasts.detonate(helper.getLevel(), center(helper), Blasts.profile("test/small"), null);
        helper.assertTrue(blast != null, "the profile exists");
        helper.assertTrue(blast.isDone(), "a small blast finishes at once");
        helper.assertTrue(air(helper, 0, 0, 0) && air(helper, 1, 0, 0) && air(helper, 0, 1, 0) && air(helper, -1, 0, 1), "the middle is cleared");
        helper.assertTrue(!air(helper, 5, 0, 0) && !air(helper, 0, 0, -5), "stone further out stays");
        helper.succeed();
    }

    /** Obsidian two blocks out stops the rays behind it; the same stone on the open side is destroyed. */
    private static void shielding(GameTestHelper helper) {
        fill(helper, 10, 3, 7, 10, 5, 9, Blocks.OBSIDIAN);                 // a 3x3 obsidian wall at +2
        helper.setBlock(C.offset(4, 0, 0), Blocks.STONE);                  // stone behind it
        helper.setBlock(C.offset(-4, 0, 0), Blocks.STONE);                 // the same stone, unshielded
        Blasts.detonate(helper.getLevel(), center(helper), Blasts.profile("test/crater_only"), null);
        helper.assertTrue(!air(helper, 2, 0, 0), "the obsidian holds");
        helper.assertTrue(!air(helper, 4, 0, 0), "the stone behind it is shielded");
        helper.assertTrue(air(helper, -4, 0, 0), "the open stone is destroyed");
        helper.succeed();
    }

    /** A slow front (half a block a tick) works over several ticks and then finishes. */
    private static void slowFront(GameTestHelper helper) {
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        Blast blast = Blasts.detonate(helper.getLevel(), center(helper), Blasts.profile("test/slow"), null);
        helper.assertTrue(blast != null && !blast.isDone(), "the blast is running");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!air(helper, 4, 0, 0), "two ticks in, the front has not reached 4 blocks");
            helper.succeedWhen(() -> {
                helper.assertTrue(blast.isDone(), "the blast finishes");
                helper.assertTrue(air(helper, 4, 0, 0) && air(helper, 0, -4, 0), "and the crater reaches 4 blocks");
            });
        });
    }

    /** A pig in the open is killed; one the same distance away behind obsidian is untouched. */
    private static void exposure(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);                    // floor
        fill(helper, 11, 2, 6, 11, 5, 10, Blocks.OBSIDIAN);                // wall between the burst and the far pig
        Pig open = helper.spawn(EntityTypes.PIG, C.offset(-2, -2, 0));
        Pig hidden = helper.spawn(EntityTypes.PIG, C.offset(5, -2, 0));
        float before = hidden.getHealth();
        Blasts.detonate(helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(C.offset(0, -2, 0))), Blasts.profile("test/small"), null);
        helper.assertTrue(!open.isAlive(), "the exposed pig is killed");
        helper.assertTrue(hidden.isAlive() && hidden.getHealth() == before, "the pig behind obsidian is unhurt");
        helper.succeed();
    }

    /** A chest in the core is destroyed with its contents: no items spill out. */
    private static void noSpew(GameTestHelper helper) {
        fill(helper, 2, 1, 2, 14, 7, 14, Blocks.STONE);
        helper.setBlock(C.offset(1, 0, 0), Blocks.CHEST);
        ((Container) helper.getBlockEntity(C.offset(1, 0, 0), net.minecraft.world.level.block.entity.BlockEntity.class))
                .setItem(0, new ItemStack(Items.DIAMOND, 64));
        Blasts.detonate(helper.getLevel(), center(helper), Blasts.profile("test/small"), null);
        helper.assertTrue(air(helper, 1, 0, 0), "the chest is gone");
        AABB area = new AABB(helper.absolutePos(C)).inflate(8);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "no items spilled");
        helper.succeed();
    }

    /**
     * A burst on open ground still destroys what stands above it within the crater: a block straight overhead and one
     * up and to the side, over ground lower than it (the rays to them pass over that low ground first).
     */
    private static void upward(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);                    // open ground
        BlockPos burst = new BlockPos(8, 2, 8);
        helper.setBlock(burst.offset(0, 5, 0), Blocks.STONE);              // overhead
        helper.setBlock(burst.offset(4, 3, 0), Blocks.STONE);              // up and to the side
        helper.setBlock(burst.offset(-3, 4, 0), Blocks.OAK_LOG);           // a tree trunk up the other way
        Blasts.detonate(helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(burst)), Blasts.profile("test/crater_only"), null);
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(burst.offset(0, 5, 0))).isAir(), "the block overhead is destroyed");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(burst.offset(4, 3, 0))).isAir(), "the block up and to the side is destroyed");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(burst.offset(-3, 4, 0))).isAir(), "the log is destroyed");
        helper.succeed();
    }
}
