package dev.groundzero.gametest;

import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.Shrapnel;
import dev.groundzero.content.ordnance.ThrownExplosive;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Phase 6 slice 6a: thrown explosives (fuse and blast, shrapnel, bouncing to rest, sticking, fire carpet, smoke). */
final class OrdnanceTests {
    private OrdnanceTests() {}

    static void register() {
        GZGameTests.test("dynamite_goes_off_after_its_fuse", GZGameTests.FLOOR_16, 100, OrdnanceTests::dynamite);
        GZGameTests.test("frag_grenade_throws_shrapnel", GZGameTests.FLOOR_16, 40, OrdnanceTests::frag);
        GZGameTests.test("grenade_bounces_and_comes_to_rest", GZGameTests.FLOOR_16, 60, OrdnanceTests::bounce);
        GZGameTests.test("sticky_grenade_sticks_to_a_wall", GZGameTests.FLOOR_16, 40, OrdnanceTests::sticky);
        GZGameTests.test("incendiary_grenade_lays_fire", GZGameTests.FLOOR_16, 40, OrdnanceTests::incendiary);
        GZGameTests.test("smoke_grenade_blinds_mobs", GZGameTests.FLOOR_16, 120, OrdnanceTests::smoke);
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(p, block);
        }
    }

    /** A grenade of this kind thrown by a mock player, placed at {@code at} (relative) and moving at {@code velocity}. */
    private static ThrownExplosive grenade(GameTestHelper helper, Item item, Vec3 at, Vec3 velocity) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        ThrownExplosive g = new ThrownExplosive(helper.getLevel(), player, new ItemStack(item));
        g.setPos(helper.absoluteVec(at));
        g.setDeltaMovement(velocity);
        helper.getLevel().addFreshEntity(g);
        return g;
    }

    private static boolean air(GameTestHelper helper, int x, int y, int z) {
        return helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir();
    }

    /** Dynamite lying on stone does nothing for 3 s, then blows a hole. */
    private static void dynamite(GameTestHelper helper) {
        fill(helper, 2, 1, 2, 14, 3, 14, Blocks.STONE);
        ThrownExplosive g = grenade(helper, GZOrdnance.DYNAMITE.get(), new Vec3(8.5, 4.05, 8.5), Vec3.ZERO);
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(g.isAlive() && !air(helper, 8, 3, 8), "still waiting on its fuse");
            helper.succeedWhen(() -> {
                helper.assertTrue(!g.isAlive(), "it has gone off");
                helper.assertTrue(air(helper, 8, 3, 8), "and blown a hole in the stone");
            });
        });
    }

    /** A frag grenade throws fragments, and they hurt what they hit. */
    private static void frag(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        ThrownExplosive g = grenade(helper, GZOrdnance.FRAG_GRENADE.get(), new Vec3(8.5, 2.05, 8.5), Vec3.ZERO);
        g.setFuseForTest(5);
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(16, 10, 16);
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(Shrapnel.class, area).size() >= 12,
                "fragments are flying"));
    }

    /** Dropped onto stone, a grenade bounces and settles on top of it, unstuck. */
    private static void bounce(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        ThrownExplosive g = grenade(helper, GZOrdnance.FRAG_GRENADE.get(), new Vec3(8.5, 6, 8.5), new Vec3(0.2, 0, 0));
        g.setFuseForTest(1_000);
        helper.runAfterDelay(50, () -> {
            double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
            helper.assertTrue(!g.stuck() && g.getDeltaMovement().lengthSqr() < 0.01, "at rest, moving " + g.getDeltaMovement());
            helper.assertTrue(g.getY() >= floor - 0.01 && g.getY() < floor + 0.5, "lying on the stone, at " + g.getY() + " (floor " + floor + ")");
            g.discard();
            helper.succeed();
        });
    }

    /** Thrown at a wall, a sticky grenade sticks there instead of bouncing off. */
    private static void sticky(GameTestHelper helper) {
        fill(helper, 12, 1, 4, 12, 6, 12, Blocks.STONE);
        ThrownExplosive g = grenade(helper, GZOrdnance.STICKY_GRENADE.get(), new Vec3(6.5, 4, 8.5), new Vec3(0.8, 0.05, 0));
        g.setFuseForTest(1_000);
        helper.succeedWhen(() -> {
            helper.assertTrue(g.stuck(), "it sticks");
            double wall = helper.absolutePos(new BlockPos(12, 0, 0)).getX();
            helper.assertTrue(Math.abs(g.getX() - wall) < 0.3 && g.getDeltaMovement().lengthSqr() == 0, "on the wall's face, still");
            g.discard();
        });
    }

    /** An incendiary grenade bursts on landing and sets fire round it. */
    private static void incendiary(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        grenade(helper, GZOrdnance.INCENDIARY_GRENADE.get(), new Vec3(8.5, 4, 8.5), new Vec3(0, -0.3, 0));
        helper.succeedWhen(() -> {
            int fires = 0;
            for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(3, 2, 3)), helper.absolutePos(new BlockPos(13, 3, 13)))) {
                if (helper.getLevel().getBlockState(p).is(Blocks.FIRE)) {
                    fires++;
                }
            }
            helper.assertTrue(fires >= 10, "a carpet of fire (" + fires + ")");
        });
    }

    /** A spider hunting a pig inside the smoke loses its target. */
    private static void smoke(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        var pig = helper.spawn(EntityTypes.PIG, new BlockPos(8, 2, 9));
        var spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(3, 2, 3));
        ThrownExplosive g = grenade(helper, GZOrdnance.SMOKE_GRENADE.get(), new Vec3(8.5, 2.05, 8.5), Vec3.ZERO);
        g.setFuseForTest(2);
        spider.setTarget(pig);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(g.smoking(), "it is pouring smoke");
            helper.assertTrue(spider.getTarget() == null, "the spider has lost the pig in the smoke");
            g.discard();
            helper.succeed();
        });
    }
}
