package dev.groundburst.gametest;

import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/** Phase 11 (DESIGN.md §16): the exotic warheads. */
final class ExoticTests {
    /** The antimatter sphere at this test scale: 48 x 0.1 = 4.8 blocks; its gamma flash kills to 6.4 and hurts to 9.6. */
    private static final float SCALE = 0.1f;

    private ExoticTests() {}

    static void register() {
        GBGameTests.test("antimatter_missile_erases_a_sphere", GBGameTests.FLOOR_16, 200, ExoticTests::antimatter);
        GBGameTests.test("placed_antimatter_warhead_breaches", GBGameTests.FLOOR_16, 120, ExoticTests::breach);
        GBGameTests.test("strategic_body_carries_antimatter", GBGameTests.FLOOR_16, 20, ExoticTests::strategic);
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = SCALE;
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /**
     * A missile's antimatter warhead arriving at (8, 5, 8): stone, obsidian and a full chest within the sphere are gone
     * with nothing dropped, bedrock in it and stone past it stay; a pig in it is annihilated, a pig in the open past it
     * dies of the gamma flash, a pig as far off behind two blocks of obsidian lives.
     */
    private static void antimatter(GameTestHelper helper) {
        floor(helper);
        BlockPos centre = new BlockPos(8, 5, 8);
        helper.setBlock(new BlockPos(8, 5, 11), Blocks.STONE);                 // 3 from the middle: erased
        helper.setBlock(new BlockPos(8, 5, 13), Blocks.STONE);                 // 5: past the sphere
        helper.setBlock(new BlockPos(8, 7, 8), Blocks.OBSIDIAN);               // 2: erased, blast resistance or not
        helper.setBlock(new BlockPos(10, 5, 8), Blocks.BEDROCK);               // 2: unbreakable, stays
        BlockPos chest = new BlockPos(6, 5, 8);
        helper.setBlock(chest, Blocks.CHEST);
        if (helper.getLevel().getBlockEntity(helper.absolutePos(chest)) instanceof ChestBlockEntity c) {
            c.setItem(0, new ItemStack(Items.DIAMOND, 16));
        }
        for (int y = 1; y <= 4; y++) {                                          // the shelter: two blocks of obsidian
            helper.setBlock(new BlockPos(8, y, 10), Blocks.OBSIDIAN);
            helper.setBlock(new BlockPos(8, y, 11), Blocks.OBSIDIAN);
        }
        Pig inside = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(9, 1, 8));       // 4.2 from the middle
        Pig open = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(12, 1, 8));        // 5.7, nothing between
        Pig sheltered = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 12));   // 5.7, behind the obsidian
        Charges.impact(helper.getLevel(), ChargeKind.ANTIMATTER_WARHEAD, helper.absolutePos(centre), null);
        helper.succeedWhen(() -> {
            helper.assertTrue(!dev.groundburst.blast.Annihilations.runningIn(helper.getLevel()) || isAirRel(helper, 8, 5, 11),
                    "the sphere has eaten its way out");
            helper.assertTrue(isAirRel(helper, 8, 5, 11), "stone 3 blocks off erased");
            helper.assertTrue(isAirRel(helper, 8, 7, 8), "obsidian erased");
            helper.assertTrue(isAirRel(helper, 6, 5, 8), "the chest erased");
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 5, 13)).is(Blocks.STONE), "stone 5 blocks off stays");
            helper.assertTrue(helper.getBlockState(new BlockPos(10, 5, 8)).is(Blocks.BEDROCK), "bedrock stays");
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 0, 8)).is(Blocks.STONE), "the floor under it, 5 down, stays");
            helper.assertTrue(!inside.isAlive(), "the pig inside is annihilated");
            helper.assertTrue(!open.isAlive(), "the pig in the open is killed by the flash");
            helper.assertTrue(sheltered.isAlive(), "the pig behind obsidian lives: " + sheltered.getHealth());
            // Nothing left in the sphere (the chest's diamonds, the annihilated pig's pork); the pig the flash killed outside
            // it drops its own where it fell, as any creature killed would.
            AABB plot = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(16, 12, 16);
            var middle = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(centre));
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, plot);
            helper.assertTrue(items.stream().noneMatch(i -> i.position().distanceTo(middle) <= 48 * SCALE), "nothing left in the sphere");
            helper.assertTrue(items.stream().noneMatch(i -> i.getItem().is(Items.DIAMOND)), "the chest's diamonds are gone");
        });
    }

    private static boolean isAirRel(GameTestHelper helper, int x, int y, int z) {
        return helper.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    /**
     * A placed antimatter warhead set off (caught in a blast): its trap breached, only a small sphere (1.5 blocks at
     * least): the warhead and the floor under it gone, stone 3 blocks off untouched.
     */
    private static void breach(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GBOrdnance.ANTIMATTER_WARHEAD.block().get().defaultBlockState());
        helper.setBlock(new BlockPos(11, 1, 8), Blocks.STONE);
        Charges.caughtInBlast(helper.getLevel(), helper.absolutePos(at), helper.getLevel().getBlockState(helper.absolutePos(at)));
        helper.succeedWhen(() -> {
            helper.assertTrue(isAirRel(helper, 8, 1, 8), "the warhead is gone");
            helper.assertTrue(isAirRel(helper, 8, 0, 8), "the floor under it erased");
            helper.assertTrue(helper.getBlockState(new BlockPos(11, 1, 8)).is(Blocks.STONE), "stone 3 blocks off untouched");
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 0, 11)).is(Blocks.STONE), "the floor 3 blocks off untouched");
        });
    }

    /** The antimatter warhead rides only the Strategic body, which a Launch Complex or a Missile Silo fires. */
    private static void strategic(GameTestHelper helper) {
        ItemStack antimatter = new ItemStack(GBOrdnance.ANTIMATTER_WARHEAD.item().get());
        ItemStack heavy = new ItemStack(GBAssembly.HEAVY_BODY.get());
        ItemStack body = new ItemStack(GBAssembly.STRATEGIC_BODY.get());
        ItemStack liquid = new ItemStack(GBAssembly.LIQUID_FUEL_STAGE.get());
        helper.assertTrue(MissileAssembly.check(antimatter, heavy, liquid, ItemStack.EMPTY) == MissileAssembly.Problem.NUCLEAR_NEEDS_STRATEGIC,
                "an antimatter warhead needs a Strategic body");
        helper.assertTrue(MissileAssembly.check(antimatter, body, liquid, ItemStack.EMPTY) == MissileAssembly.Problem.NONE,
                "and fits one");
        MissileSpec spec = dev.groundburst.content.assembly.MissileItem.spec(MissileAssembly.missile(new MissileSpec(antimatter, SizeClass.XL, MissileParts.Motor.LIQUID,
                MissileParts.Guidance.GPS, false)));
        helper.assertTrue(spec != null && spec.exotic() && spec.needsComplex(), "fires from a Launch Complex or a Missile Silo: " + spec);
        helper.assertTrue(dev.groundburst.content.assembly.WarheadAssembly.sizeOf(GBOrdnance.ANTIMATTER_WARHEAD.block().get()) == SizeClass.XL,
                "class XL");
        helper.succeed();
    }
}
