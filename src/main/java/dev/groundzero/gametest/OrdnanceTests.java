package dev.groundzero.gametest;

import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.Shrapnel;
import dev.groundzero.content.ordnance.ThrownExplosive;
import dev.groundzero.content.ordnance.ToxicSludgeBlock;
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

/** Phase 6 slice 6a: thrown explosives (fuse and blast, shrapnel, bouncing to rest, sticking, fire carpet, smoke, poison gas, toxin). */
final class OrdnanceTests {
    private OrdnanceTests() {}

    static void register() {
        GZGameTests.test("dynamite_goes_off_after_its_fuse", GZGameTests.FLOOR_16, 100, OrdnanceTests::dynamite);
        GZGameTests.test("frag_grenade_throws_shrapnel", GZGameTests.FLOOR_16, 40, OrdnanceTests::frag);
        GZGameTests.test("grenade_bounces_and_comes_to_rest", GZGameTests.FLOOR_16, 60, OrdnanceTests::bounce);
        GZGameTests.test("sticky_grenade_sticks_to_a_wall", GZGameTests.FLOOR_16, 40, OrdnanceTests::sticky);
        GZGameTests.test("incendiary_grenade_lays_fire", GZGameTests.FLOOR_16, 40, OrdnanceTests::incendiary);
        GZGameTests.test("smoke_grenade_blinds_mobs", GZGameTests.FLOOR_16, 120, OrdnanceTests::smoke);
        GZGameTests.test("smoke_hides_whoever_is_in_it", GZGameTests.FLOOR_16, 60, OrdnanceTests::hidden);
        GZGameTests.test("gas_grenade_poisons_but_not_through_a_suit", GZGameTests.FLOOR_16, 120, OrdnanceTests::gas);
        GZGameTests.test("toxin_grenade_splashes_sludge", GZGameTests.FLOOR_16, 40, OrdnanceTests::toxin);
        GZGameTests.test("toxic_sludge_dries_out_and_needs_a_floor", GZGameTests.FLOOR_16, 20, OrdnanceTests::sludge);
        GZGameTests.test("suit_pieces_share_chemical_protection", GZGameTests.FLOOR_16, 20, OrdnanceTests::shares);
    }

    private static void wear(net.minecraft.world.entity.Mob mob, java.util.List<? extends java.util.function.Supplier<? extends Item>> suit) {
        var slots = new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < suit.size(); i++) {
            mob.setItemSlot(slots[i], new ItemStack(suit.get(i).get()));
        }
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

    /** A player standing in the smoke can only be noticed from a tenth of the usual range. */
    private static void hidden(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(8.5, 2, 9.5)));
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        helper.assertTrue(player.getVisibilityPercent(helper.getLevel(), zombie) > 0.5, "seen plainly before the smoke");
        ThrownExplosive g = grenade(helper, GZOrdnance.SMOKE_GRENADE.get(), new Vec3(8.5, 2.05, 8.5), Vec3.ZERO);
        g.setFuseForTest(2);
        helper.runAfterDelay(30, () -> {
            double seen = player.getVisibilityPercent(helper.getLevel(), zombie);
            helper.assertTrue(seen <= 0.11, "hidden in the smoke (visibility " + seen + ")");
            g.discard();
            helper.succeed();
        });
    }

    /** Two pigs in the poison gas: the bare one is poisoned and hurt, the one in a full hazmat suit is untouched. */
    private static void gas(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        var bare = helper.spawn(EntityTypes.PIG, new BlockPos(7, 2, 8));
        var suited = helper.spawn(EntityTypes.PIG, new BlockPos(9, 2, 8));
        bare.setNoAi(true);
        suited.setNoAi(true);
        wear(suited, dev.groundzero.content.gear.GZArmor.HAZMAT_SET);
        ThrownExplosive g = grenade(helper, GZOrdnance.GAS_GRENADE.get(), new Vec3(8.5, 2.05, 8.5), Vec3.ZERO);
        g.setFuseForTest(2);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(g.smoking(), "it is pouring gas");
            helper.assertTrue(!bare.isAlive() || bare.hasEffect(net.minecraft.world.effect.MobEffects.POISON) && bare.getHealth() < bare.getMaxHealth(),
                    "the bare pig is poisoned and hurt");
            helper.assertTrue(suited.isAlive() && !suited.hasEffect(net.minecraft.world.effect.MobEffects.POISON)
                    && suited.getHealth() == suited.getMaxHealth(), "the suited pig is untouched (" + suited.getHealth() + ")");
            g.discard();
            helper.succeed();
        });
    }

    /** A toxin grenade bursting on stone splashes sludge round it and poisons a pig standing there. */
    private static void toxin(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        var pig = helper.spawn(EntityTypes.PIG, new BlockPos(9, 2, 8));
        pig.setNoAi(true);
        grenade(helper, GZOrdnance.TOXIN_GRENADE.get(), new Vec3(8.5, 4, 8.5), new Vec3(0, -0.3, 0));
        helper.succeedWhen(() -> {
            int sludge = 0;
            for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(3, 2, 3)), helper.absolutePos(new BlockPos(13, 2, 13)))) {
                if (helper.getLevel().getBlockState(p).is(GZOrdnance.TOXIC_SLUDGE.get())) {
                    sludge++;
                }
            }
            helper.assertTrue(sludge >= 20, "a splash of sludge (" + sludge + ")");
            helper.assertTrue(!pig.isAlive() || pig.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "the pig is poisoned");
        });
    }

    /** Sludge dries out stage by stage and then goes; with its floor gone, it goes at once. */
    private static void sludge(GameTestHelper helper) {
        fill(helper, 1, 1, 1, 15, 1, 15, Blocks.STONE);
        var level = helper.getLevel();
        var block = GZOrdnance.TOXIC_SLUDGE.get();
        BlockPos a = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(a, block.defaultBlockState());
        for (int stage = 1; stage < ToxicSludgeBlock.STAGES; stage++) {
            level.getBlockState(a).tick(level, a, level.getRandom());
            helper.assertTrue(level.getBlockState(a).is(block) && level.getBlockState(a).getValue(ToxicSludgeBlock.AGE) == stage,
                    "drying, stage " + stage);
        }
        level.getBlockState(a).tick(level, a, level.getRandom());
        helper.assertTrue(level.getBlockState(a).isAir(), "dried away");
        BlockPos b = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlockAndUpdate(b, block.defaultBlockState());
        level.setBlockAndUpdate(b.below(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(!level.getBlockState(b).is(block), "gone with its floor");
        helper.assertTrue(!block.defaultBlockState().canSurvive(level, b), "and cannot be put back on air");
        helper.succeed();
    }

    /** Each suit piece keeps out its share of a dose (hood 20%), a full suit all of it. */
    private static void shares(GameTestHelper helper) {
        var pig = helper.spawn(EntityTypes.PIG, new BlockPos(8, 2, 8));
        pig.setNoAi(true);
        helper.assertTrue(dev.groundzero.content.gear.GZArmor.chemicalProtection(pig) == 0, "nothing without a suit");
        pig.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(dev.groundzero.content.gear.GZArmor.HAZMAT_HOOD.get()));
        float hood = dev.groundzero.content.gear.GZArmor.chemicalProtection(pig);
        helper.assertTrue(Math.abs(hood - 0.2f) < 0.01f, "a hood keeps out a fifth (" + hood + ")");
        wear(pig, dev.groundzero.content.gear.GZArmor.LEAD_HAZMAT_SET);
        float full = dev.groundzero.content.gear.GZArmor.chemicalProtection(pig);
        helper.assertTrue(Math.abs(full - 1) < 0.01f, "a full lead-lined suit keeps out everything (" + full + ")");
        helper.succeed();
    }
}
