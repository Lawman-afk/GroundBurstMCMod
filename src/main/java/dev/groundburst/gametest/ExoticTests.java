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
    /** The antimatter sphere at its test scale: 67 x 0.0716 = 4.8 blocks; its gamma flash kills to 6.4 and hurts to 9.6. */
    private static final float SCALE = 0.1f * 48 / 67;
    /** The singularity's test scale: it opens to 2.6 blocks' reach (fed nothing) and grows to 11 at most. */
    private static final float HOLE_SCALE = 0.1f;

    private ExoticTests() {}

    static void register() {
        GBGameTests.test("antimatter_missile_erases_a_sphere", GBGameTests.FLOOR_16, 200, ExoticTests::antimatter);
        GBGameTests.test("placed_antimatter_warhead_breaches", GBGameTests.FLOOR_16, 120, ExoticTests::breach);
        GBGameTests.test("exotic_warheads_fly_as_icbms", GBGameTests.FLOOR_16, 20, ExoticTests::icbmOnly);
        GBGameTests.test("singularity_swallows_tears_and_collapses", GBGameTests.FLOOR_16, 200, ExoticTests::singularity);
        GBGameTests.test("singularity_grows_by_what_it_eats", GBGameTests.FLOOR_16, 200, ExoticTests::feeds);
        GBGameTests.test("placed_singularity_warhead_breaches", GBGameTests.FLOOR_16, 160, ExoticTests::singularityBreach);
    }

    private static void floor(GameTestHelper helper) {
        floor(helper, SCALE, Blocks.STONE);
    }

    private static void floor(GameTestHelper helper, float scale, net.minecraft.world.level.block.Block block) {
        Charges.bombScale = scale;
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, block);
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
            helper.assertTrue(items.stream().noneMatch(i -> i.position().distanceTo(middle) <= dev.groundburst.content.exotic.Antimatter.RADIUS * SCALE),
                    "nothing left in the sphere");
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

    /**
     * The exotic warheads are whole weapons, only ever built into their ICBMs: the bench takes no exotic core and no
     * missile assembler takes the warhead (it is no bench warhead at all); the Special Weapons Assembler builds the ICBM
     * whole from the core and a booster, and taken apart it gives the core back. JEI and the creative tab leave the
     * warheads out.
     */
    private static void icbmOnly(GameTestHelper helper) {
        ItemStack body = new ItemStack(GBAssembly.STRATEGIC_BODY.get());
        ItemStack liquid = new ItemStack(GBAssembly.LIQUID_FUEL_STAGE.get());
        ItemStack booster = new ItemStack(dev.groundburst.content.strategic.GBStrategic.INTERCONTINENTAL_BOOSTER.get());
        ItemStack casing = new ItemStack(dev.groundburst.content.assembly.WarheadAssembly.casing(SizeClass.XL));
        ItemStack fuze = new ItemStack(GBAssembly.FUZES.getFirst().get());
        var cores = dev.groundburst.content.strategic.SpecialWeapons.cores();
        var warheads = java.util.List.of(GBOrdnance.ANTIMATTER_WARHEAD, GBOrdnance.SINGULARITY_WARHEAD, GBOrdnance.STORM_WARHEAD);
        for (int i = 0; i < warheads.size(); i++) {
            var warhead = warheads.get(i);
            ItemStack exotic = new ItemStack(warhead.item().get());
            ItemStack core = cores.get(i);
            String name = warhead.block().get().kind.id;
            helper.assertTrue(warhead.block().get().kind.wholeWeapon(), name + " is a whole weapon");
            helper.assertTrue(dev.groundburst.content.assembly.WarheadAssembly.check(core, casing, fuze, ItemStack.EMPTY)
                    == dev.groundburst.content.assembly.WarheadAssembly.Problem.NO_CORE, name + ": the bench takes no exotic core");
            helper.assertTrue(!dev.groundburst.content.assembly.WarheadAssembly.isWarhead(exotic), name + ": no bench warhead");
            helper.assertTrue(MissileAssembly.check(exotic, body, liquid, ItemStack.EMPTY) != MissileAssembly.Problem.NONE,
                    name + ": no Strategic body takes it");
            helper.assertTrue(MissileAssembly.check(exotic, body, liquid, ItemStack.EMPTY, SizeClass.XL) != MissileAssembly.Problem.NONE,
                    name + ": the Strategic Missile Assembler refuses it");
            helper.assertTrue(MissileAssembly.fit(exotic, SizeClass.XL) == MissileAssembly.Problem.WHOLE_WEAPON, name + ": built whole");
            helper.assertTrue(!dev.groundburst.registry.GBItems.contents(dev.groundburst.registry.GBTab.ORDNANCE).contains(warhead.item()),
                    name + ": out of the creative tab");
            helper.assertTrue(dev.groundburst.content.strategic.SpecialWeapons.check(core, booster, ItemStack.EMPTY)
                    == dev.groundburst.content.strategic.SpecialWeapons.Problem.NONE, name + ": the Special Weapons Assembler takes its core and a booster");
            ItemStack icbm = dev.groundburst.content.strategic.SpecialWeapons.assemble(core, new ItemStack(GBAssembly.GPS_GUIDANCE.get()),
                    ItemStack.EMPTY);
            MissileSpec spec = dev.groundburst.content.assembly.MissileItem.spec(icbm);
            helper.assertTrue(spec != null && spec.exotic() && spec.intercontinental() && spec.needsComplex()
                    && dev.groundburst.content.assembly.AssemblyStats.kindOf(spec.warhead()) == warhead.block().get().kind,
                    name + ": an ICBM, fired from a Launch Complex or a Missile Silo: " + spec);
            var parts = dev.groundburst.content.strategic.SpecialWeapons.takeApart(icbm);
            helper.assertTrue(parts.size() == 4 && ItemStack.isSameItem(parts.get(0), core) && ItemStack.isSameItem(parts.get(1), booster)
                    && parts.get(2).is(GBAssembly.GPS_GUIDANCE.get()), name + ": taken apart, the core, the booster and the GPS back: " + parts);
        }
        helper.succeed();
    }

    /**
     * A missile's singularity warhead arriving at (8, 3, 8) over a bedrock floor (nothing to feed it but what is set round
     * it), living 60 ticks at a tenth of its size (reaching about 3.3 by the end): a pig standing beside it (on bedrock,
     * which it never tears) is swallowed; a pig 5.4 off, in its pull, lives through it all; stone beside it and dirt 2 off
     * are torn loose, obsidian 2 off holds, bedrock beside it stays, stone 4 off is past its reach; then it collapses.
     */
    private static void singularity(GameTestHelper helper) {
        floor(helper, HOLE_SCALE, Blocks.BEDROCK);
        dev.groundburst.blast.Singularities.lifeOverride = 60;
        BlockPos centre = new BlockPos(8, 3, 8);
        helper.setBlock(new BlockPos(9, 2, 8), Blocks.BEDROCK);               // the near pig's footing
        helper.setBlock(new BlockPos(7, 3, 8), Blocks.STONE);                 // 1 off: torn loose
        helper.setBlock(new BlockPos(8, 3, 10), Blocks.DIRT);                 // 2 off: torn loose
        helper.setBlock(new BlockPos(8, 3, 6), Blocks.OBSIDIAN);              // 2 off: holds
        helper.setBlock(new BlockPos(8, 4, 8), Blocks.BEDROCK);               // 1 off: never torn
        helper.setBlock(new BlockPos(12, 3, 8), Blocks.STONE);                // 4 off: past its reach
        Pig near = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(9, 3, 8));         // 1 from the middle
        Pig far = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 13));         // 5.4 off, in its pull
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
            helper.assertTrue(far.isAlive(), "the pig out in its pull lives: " + far.getHealth());
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 4, 8)).is(Blocks.BEDROCK), "bedrock stays");
            helper.assertTrue(helper.getBlockState(new BlockPos(12, 3, 8)).is(Blocks.STONE), "stone past its reach stays");
        });
    }

    /** A placed singularity warhead set off (caught in a blast): its cells breached, a small one for four seconds. */
    private static void singularityBreach(GameTestHelper helper) {
        floor(helper, HOLE_SCALE, Blocks.STONE);
        dev.groundburst.blast.Singularities.lifeOverride = 0;
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

    /**
     * A singularity buried in earth feeds: at six hundredths of its size it opens only to 1.6 blocks' reach by itself,
     * but buried in a 13 by 7 by 13 heap of dirt (round (8, 4, 8), on stone) it eats its way out, growing as it goes, to
     * well past 4 blocks' reach within 80 ticks of its 100, and tears loose dirt 4 off that it could never reach unfed;
     * then it collapses.
     */
    private static void feeds(GameTestHelper helper) {
        floor(helper, 0.06f, Blocks.STONE);
        dev.groundburst.blast.Singularities.lifeOverride = 100;
        for (BlockPos p : BlockPos.betweenClosed(2, 1, 2, 14, 7, 14)) {
            helper.setBlock(p, Blocks.DIRT);
        }
        BlockPos centre = new BlockPos(8, 4, 8);
        var middle = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(centre));
        Charges.impact(helper.getLevel(), ChargeKind.SINGULARITY_WARHEAD, helper.absolutePos(centre), null);
        float unfed = dev.groundburst.blast.Singularities.reach(dev.groundburst.blast.Singularities.SEED * 0.06f, 0, 0, 80, 100, 100);
        boolean[] grown = {false};
        helper.runAfterDelay(80, () -> {
            float reach = dev.groundburst.blast.Singularities.reachAt(helper.getLevel(), middle);
            helper.assertTrue(reach > 4.2f && reach > 3 * unfed, "it has fed and grown: reach " + reach + ", unfed " + unfed);
            helper.assertTrue(isAirRel(helper, 12, 4, 8), "dirt 4 off torn loose");
            grown[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(grown[0], "not grown yet");
            helper.assertTrue(dev.groundburst.blast.Singularities.reachAt(helper.getLevel(), middle) == 0, "it has collapsed");
        });
    }
}
