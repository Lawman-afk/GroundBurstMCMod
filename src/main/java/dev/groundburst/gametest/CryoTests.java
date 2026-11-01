package dev.groundburst.gametest;

import dev.groundburst.blast.Freezes;
import dev.groundburst.content.effect.EffectSystems;
import dev.groundburst.content.fallout.CryoIceBlock;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The cryogenic warhead's deep freeze (DESIGN.md §16.16), shrunk to its plot: a front 12 blocks round (a missile's at
 * 12/128 of its size: its heart 3.75, its cold climbing 6 over where it struck), its cold snap cut short.
 */
final class CryoTests {
    private static final float SCALE = 12f / Freezes.RADIUS;

    private CryoTests() {}

    static void register() {
        GBGameTests.testOpenSky("cryo_icbm_freezes_its_region", GBGameTests.FLOOR_16, 200, CryoTests::freezes);
        GBGameTests.testOpenSky("cryo_cold_snap_freezes_what_walks_in", GBGameTests.FLOOR_16, 300, CryoTests::coldSnap);
        GBGameTests.test("cryo_ice_and_snow_thaw_where_it_is_warm", GBGameTests.FLOOR_16, 20, CryoTests::thaw);
    }

    /** Grass over the plot, then a Cryo ICBM's freeze from its middle, its cold snap {@code life} ticks. */
    private static Vec3 freeze(GameTestHelper helper, int life) {
        Freezes.lifeOverride = life;
        BlockPos eye = new BlockPos(8, 1, 8);
        try {
            Charges.scaled(SCALE, () -> Charges.impact(helper.getLevel(), ChargeKind.CRYOGENIC_WARHEAD, helper.absolutePos(eye), null));
        } finally {
            Freezes.lifeOverride = 0;                        // read as it starts: the next freeze is its own again
        }
        return Vec3.atCenterOf(helper.absolutePos(eye));
    }

    private static void grass(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.GRASS_BLOCK);
        }
    }

    /** A block ringed with stone at its level (a pool of {@code fluid}). */
    private static void pool(GameTestHelper helper, BlockPos at, BlockState fluid) {
        for (BlockPos side : new BlockPos[] {at.north(), at.south(), at.east(), at.west()}) {
            helper.setBlock(side, Blocks.STONE);
        }
        helper.setBlock(at, fluid);
    }

    /**
     * Its front runs out over the plot: still water 4 off freezes to cryo ice, a pool of lava crusts to obsidian, the grass
     * 5 to 7 off is laid with cryo snow (most of it: it lies in drifts); a pig at its heart dies, one 7 off freezes solid but lives; glass in the heart
     * shatters; a spire of ice stands at the middle; and a pillar's top 8.5 over where it struck (its cold climbs 6) is
     * spared, grass still and no snow.
     */
    private static void freezes(GameTestHelper helper) {
        grass(helper);
        pool(helper, new BlockPos(12, 1, 8), Blocks.WATER.defaultBlockState());
        pool(helper, new BlockPos(8, 1, 12), Blocks.LAVA.defaultBlockState());
        for (int y = 1; y <= 8; y++) {
            helper.setBlock(new BlockPos(2, y, 2), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(2, 9, 2), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.GLASS);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.STONE);
        Pig heart = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(9, 1, 9));
        Pig out = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(15, 1, 8));
        Vec3 at = freeze(helper, 400);
        helper.succeedWhen(() -> {
            helper.assertTrue(Freezes.settledAt(helper.getLevel(), at), "its front has run out and its spires grown");
            BlockState water = helper.getBlockState(new BlockPos(12, 1, 8));
            helper.assertTrue(water.is(GBFallout.CRYO_ICE.block().get()) && water.getValue(CryoIceBlock.SOURCE), "the still water froze: " + water);
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 1, 12)).is(Blocks.OBSIDIAN), "the lava crusted over");
            int snowed = 0, ring = 0;                     // (snow lies in drifts: most of the open grass 5 to 7 off)
            for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
                double d = Math.hypot(p.getX() - 8, p.getZ() - 8);
                if (d >= 5 && d <= 7 && helper.getBlockState(p.below()).isFaceSturdy(helper.getLevel(), helper.absolutePos(p.below()), net.minecraft.core.Direction.UP)
                        && !helper.getBlockState(p.below()).is(Blocks.STONE)) {
                    ring++;
                    snowed += helper.getBlockState(p).is(GBFallout.CRYO_SNOW.block().get()) ? 1 : 0;
                }
            }
            helper.assertTrue(ring > 20 && snowed >= ring * 0.7, "the grass 5 to 7 off laid with snow: " + snowed + " of " + ring);
            helper.assertTrue(!heart.isAlive(), "nothing lives at its heart");
            helper.assertTrue(out.isAlive() && out.hasEffect(EffectSystems.FROZEN), "7 off: frozen solid, alive");
            helper.assertTrue(!helper.getBlockState(new BlockPos(7, 2, 7)).is(Blocks.GLASS), "the glass in its heart shattered");
            BlockState spire = helper.getBlockState(new BlockPos(8, 3, 8));
            helper.assertTrue(spire.is(Blocks.PACKED_ICE) || spire.is(Blocks.BLUE_ICE), "a spire of ice at its middle: " + spire);
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 9, 2)).is(Blocks.GRASS_BLOCK)
                    && helper.getBlockState(new BlockPos(2, 10, 2)).isAir(), "the hilltop over its cold spared");
        });
    }

    /**
     * Once its front has passed, its cold snap freezes what comes into the region (a pig brought in frosts over), but not
     * one in leather boots (vanilla's rule); then it ends.
     */
    private static void coldSnap(GameTestHelper helper) {
        grass(helper);
        Vec3 at = freeze(helper, 200);
        Pig[] pigs = new Pig[2];
        int[] coldest = {0};
        helper.onEachTick(() -> {
            if (pigs[0] == null && Freezes.settledAt(helper.getLevel(), at)) {
                pigs[0] = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(12, 1, 12));
                pigs[1] = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(4, 1, 12));
                pigs[1].setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            }
            if (pigs[0] != null) {
                coldest[0] = Math.max(coldest[0], pigs[0].getTicksFrozen());
                helper.assertTrue(pigs[1].getTicksFrozen() == 0, "leather keeps the cold out");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(coldest[0] >= 40, "the pig frosted over: " + coldest[0]);
            helper.assertTrue(!Freezes.runningAt(helper.getLevel(), at), "its cold snap has ended");
        });
    }

    /**
     * Where it is not cold enough for snow (the test plot's plains), cryo ice that was still water melts back to water,
     * running water's melts away, and cryo snow thins a layer at a time and goes.
     */
    private static void thaw(GameTestHelper helper) {
        var level = helper.getLevel();
        CryoIceBlock ice = GBFallout.CRYO_ICE.block().get();
        BlockPos still = new BlockPos(4, 1, 4), running = new BlockPos(8, 1, 4), snow = new BlockPos(12, 1, 4);
        helper.setBlock(still, ice.frozen(true));
        helper.setBlock(running, ice.frozen(false));
        helper.setBlock(snow, GBFallout.CRYO_SNOW.block().get().defaultBlockState().setValue(dev.groundburst.content.fallout.CryoSnowBlock.LAYERS, 3));
        helper.assertTrue(!dev.groundburst.content.fallout.FrostedBlock.cold(level, helper.absolutePos(still)), "the plot is warm (plains)");
        RandomSource random = RandomSource.create(5);
        for (int i = 0; i < 400; i++) {
            for (BlockPos p : new BlockPos[] {still, running, snow}) {
                BlockPos abs = helper.absolutePos(p);
                BlockState s = level.getBlockState(abs);
                if (s.is(ice) || s.is(GBFallout.CRYO_SNOW.block().get())) {
                    s.randomTick(level, abs, random);
                }
            }
        }
        helper.assertTrue(helper.getBlockState(still).is(Blocks.WATER), "still water's ice melted back to water: " + helper.getBlockState(still));
        helper.assertTrue(helper.getBlockState(running).isAir(), "running water's melted away: " + helper.getBlockState(running));
        helper.assertTrue(helper.getBlockState(snow).isAir(), "the snow thawed away: " + helper.getBlockState(snow));
        helper.succeed();
    }
}
