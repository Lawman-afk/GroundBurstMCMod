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
        GBGameTests.test("singularity_swallows_tears_and_collapses", GBGameTests.FLOOR_16, 200, ExoticTests::singularity);
        GBGameTests.test("placed_singularity_warhead_breaches", GBGameTests.FLOOR_16, 160, ExoticTests::singularityBreach);
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

    /** The exotic warheads ride only the Strategic body, which a Launch Complex or a Missile Silo fires. */
    private static void strategic(GameTestHelper helper) {
        ItemStack heavy = new ItemStack(GBAssembly.HEAVY_BODY.get());
        ItemStack body = new ItemStack(GBAssembly.STRATEGIC_BODY.get());
        ItemStack liquid = new ItemStack(GBAssembly.LIQUID_FUEL_STAGE.get());
        for (var warhead : java.util.List.of(GBOrdnance.ANTIMATTER_WARHEAD, GBOrdnance.SINGULARITY_WARHEAD)) {
            ItemStack exotic = new ItemStack(warhead.item().get());
            String name = warhead.block().get().kind.id;
            helper.assertTrue(MissileAssembly.check(exotic, heavy, liquid, ItemStack.EMPTY) == MissileAssembly.Problem.NUCLEAR_NEEDS_STRATEGIC,
                    name + " needs a Strategic body");
            helper.assertTrue(MissileAssembly.check(exotic, body, liquid, ItemStack.EMPTY) == MissileAssembly.Problem.NONE, name + " fits one");
            MissileSpec spec = dev.groundburst.content.assembly.MissileItem.spec(MissileAssembly.missile(new MissileSpec(exotic, SizeClass.XL,
                    MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS, false)));
            helper.assertTrue(spec != null && spec.exotic() && spec.needsComplex(), name + " fires from a Launch Complex or a Missile Silo: " + spec);
            helper.assertTrue(dev.groundburst.content.assembly.WarheadAssembly.sizeOf(warhead.block().get()) == SizeClass.XL, name + " is class XL");
        }
        helper.succeed();
    }

    /**
     * A missile's singularity warhead arriving at (8, 3, 8), growing for 60 ticks at a tenth of its size (horizon 1,
     * pull 4.8, tearing to 3.2): a pig standing beside it (on bedrock, which it never tears) is swallowed; a pig at the
     * edge of its pull lives through it all; stone beside it and dirt 2 off are torn loose, obsidian 2 off holds,
     * bedrock beside it stays, stone 4 off is past its reach; then it collapses.
     */
    private static void singularity(GameTestHelper helper) {
        floor(helper);
        dev.groundburst.blast.Singularities.growOverride = 60;
        BlockPos centre = new BlockPos(8, 3, 8);
        helper.setBlock(new BlockPos(9, 2, 8), Blocks.BEDROCK);               // the near pig's footing
        helper.setBlock(new BlockPos(7, 3, 8), Blocks.STONE);                 // 1 off: torn loose
        helper.setBlock(new BlockPos(8, 3, 10), Blocks.DIRT);                 // 2 off: torn loose
        helper.setBlock(new BlockPos(8, 3, 6), Blocks.OBSIDIAN);              // 2 off: holds
        helper.setBlock(new BlockPos(8, 4, 8), Blocks.BEDROCK);               // 1 off: never torn
        helper.setBlock(new BlockPos(12, 3, 8), Blocks.STONE);                // 4 off: past its reach
        Pig near = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(9, 3, 8));         // 1 from the middle
        Pig far = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 12));         // 4.5 off, at its pull's edge
        Charges.impact(helper.getLevel(), ChargeKind.SINGULARITY_WARHEAD, helper.absolutePos(centre), null);
        helper.runAfterDelay(20, () -> helper.assertTrue(dev.groundburst.blast.Singularities.runningIn(helper.getLevel()), "it is open"));
        // Just before it collapses (its blast's core breaks anything, obsidian too): what its pull tore and what held.
        helper.runAfterDelay(56, () -> {
            helper.assertTrue(isAirRel(helper, 7, 3, 8), "stone beside it torn loose");
            helper.assertTrue(isAirRel(helper, 8, 3, 10), "dirt 2 off torn loose");
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 3, 6)).is(Blocks.OBSIDIAN), "obsidian 2 off holds");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!dev.groundburst.blast.Singularities.runningIn(helper.getLevel()), "it has collapsed");
            helper.assertTrue(!near.isAlive(), "the pig beside it is swallowed");
            helper.assertTrue(far.isAlive(), "the pig at the edge of its pull lives: " + far.getHealth());
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 4, 8)).is(Blocks.BEDROCK), "bedrock stays");
            helper.assertTrue(helper.getBlockState(new BlockPos(12, 3, 8)).is(Blocks.STONE), "stone past its reach stays");
        });
    }

    /** A placed singularity warhead set off (caught in a blast): its cells breached, a small one for three seconds. */
    private static void singularityBreach(GameTestHelper helper) {
        floor(helper);
        dev.groundburst.blast.Singularities.growOverride = 0;
        BlockPos at = new BlockPos(8, 1, 8);
        helper.setBlock(at, GBOrdnance.SINGULARITY_WARHEAD.block().get().defaultBlockState());
        helper.setBlock(new BlockPos(12, 1, 8), Blocks.STONE);
        Charges.caughtInBlast(helper.getLevel(), helper.absolutePos(at), helper.getLevel().getBlockState(helper.absolutePos(at)));
        helper.runAfterDelay(20, () -> helper.assertTrue(dev.groundburst.blast.Singularities.runningIn(helper.getLevel()), "a small one opens"));
        helper.succeedWhen(() -> {
            helper.assertTrue(!dev.groundburst.blast.Singularities.runningIn(helper.getLevel()), "and collapses");
            helper.assertTrue(isAirRel(helper, 8, 1, 8), "the warhead is gone");
            helper.assertTrue(helper.getBlockState(new BlockPos(12, 1, 8)).is(Blocks.STONE), "stone 4 off untouched");
        });
    }
}
