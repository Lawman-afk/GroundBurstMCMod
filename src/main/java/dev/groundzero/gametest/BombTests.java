package dev.groundzero.gametest;

import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.NapalmFireBlock;
import dev.groundzero.content.ordnance.ThrownExplosive;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeBlockEntity;
import dev.groundzero.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 6 slice 6d: bombs (signal, the impact fuze on landing, falling safe, the bunker buster's bore, the thermobaric
 * delay, cluster bomblets, napalm). Bombs here are scaled down ({@link Charges#bombScale}) so they stay in their plot.
 */
final class BombTests {
    private static final float SCALE = 0.3f;

    private BombTests() {}

    static void register() {
        GZGameTests.test("he_bomb_goes_off_on_its_frequency", GZGameTests.FLOOR_16, 40, BombTests::signal);
        GZGameTests.test("armed_bomb_goes_off_when_it_lands", GZGameTests.FLOOR_16, 80, BombTests::impact);
        GZGameTests.test("safe_bomb_falls_and_lands_whole", GZGameTests.FLOOR_16, 80, BombTests::safeFall);
        GZGameTests.test("bunker_buster_goes_off_where_it_stands", GZGameTests.FLOOR_16, 40, BombTests::bunker);
        GZGameTests.test("bunker_buster_penetrates_when_delivered", GZGameTests.FLOOR_16, 60, BombTests::penetrate);
        GZGameTests.test("thermobaric_lights_its_cloud_a_moment_later", GZGameTests.FLOOR_16, 60, BombTests::thermobaric);
        GZGameTests.test("cluster_bomb_scatters_bomblets", GZGameTests.FLOOR_16, 20, BombTests::cluster);
        GZGameTests.test("napalm_warhead_lays_napalm_all_round", GZGameTests.FLOOR_16, 20, BombTests::napalm);
        GZGameTests.test("napalm_fire_burns_and_burns_out", GZGameTests.FLOOR_16, 40, BombTests::napalmFire);
        GZGameTests.test("gas_warhead_scatters_gas_canisters", GZGameTests.FLOOR_16, 20,
                h -> canisters(h, GZOrdnance.GAS_WARHEAD.block().get(), GZOrdnance.GAS_GRENADE.get()));
        GZGameTests.test("cryo_warhead_scatters_cryo_canisters", GZGameTests.FLOOR_16, 20,
                h -> canisters(h, GZOrdnance.CRYO_WARHEAD.block().get(), GZOrdnance.CRYO_GRENADE.get()));
        GZGameTests.test("emp_warhead_scatters_emp_canisters", GZGameTests.FLOOR_16, 20,
                h -> canisters(h, GZOrdnance.EMP_WARHEAD.block().get(), GZOrdnance.EMP_GRENADE.get()));
        GZGameTests.test("toxic_waste_warhead_splashes_sludge", GZGameTests.FLOOR_16, 20, BombTests::toxicWaste);
        GZGameTests.testOpenSky("smoke_warhead_bursts_over_its_target", GZGameTests.FLOOR_16, 120, BombTests::airburst);
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = SCALE;
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    private static ChargeBlockEntity place(GameTestHelper helper, Block block, BlockPos rel, Direction facing, boolean armed) {
        helper.setBlock(rel, block.defaultBlockState().setValue(ChargeBlock.FACING, facing).setValue(ChargeBlock.ARMED, armed));
        ChargeBlockEntity be = ChargeBlock.at(helper.getLevel(), helper.absolutePos(rel));
        helper.assertTrue(be != null, "a bomb block entity");
        return be;
    }

    private static boolean is(GameTestHelper helper, BlockPos rel, Block block) {
        return helper.getLevel().getBlockState(helper.absolutePos(rel)).is(block);
    }

    /** An armed HE warhead with a remote fuze on frequency 12 goes off when a detonator on 12 is pressed. */
    private static void signal(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.HE_WARHEAD.block().get(), at, Direction.NORTH, false);
        be.setFuze(dev.groundzero.content.assembly.FuzeSettings.of(dev.groundzero.content.assembly.Fuze.REMOTE, 12));
        be.arm(helper.getLevel(), null);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(Charges.remote(helper.getLevel(), helper.absoluteVec(new Vec3(8, 3, 8)), 12, null, 10) == 1, "it took the signal");
            helper.succeedWhen(() -> helper.assertTrue(!is(helper, at, GZOrdnance.HE_WARHEAD.block().get())
                    && is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "gone off, and blown a hole"));
        });
    }

    /** An armed bomb placed in the air falls and goes off where it lands. */
    private static void impact(GameTestHelper helper) {
        floor(helper);
        int before = Charges.fired;
        place(helper, GZOrdnance.HE_WARHEAD.block().get(), new BlockPos(8, 8, 8), Direction.NORTH, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(Charges.fired > before, "it went off");
            helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "on the ground, where it landed");
        });
    }

    /** A safe bomb placed in the air falls and lands whole, its fuze settings kept. */
    private static void safeFall(GameTestHelper helper) {
        floor(helper);
        ChargeBlockEntity be = place(helper, GZOrdnance.HE_WARHEAD.block().get(), new BlockPos(8, 8, 8), Direction.NORTH, false);
        be.setFrequency(77);
        helper.succeedWhen(() -> {
            helper.assertTrue(is(helper, new BlockPos(8, 2, 8), GZOrdnance.HE_WARHEAD.block().get()), "landed on the floor");
            ChargeBlockEntity landed = ChargeBlock.at(helper.getLevel(), helper.absolutePos(new BlockPos(8, 2, 8)));
            helper.assertTrue(landed != null && landed.frequency() == 77 && !landed.armed(), "safe, frequency kept");
        });
    }

    /** Set off where it stands, a bunker buster is just a bomb: it blows a crater and bores nothing. */
    private static void bunker(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.BUNKER_BUSTER.block().get(), at, Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        helper.assertTrue(!Charges.pendingAt(helper.getLevel(), helper.absolutePos(at)), "no boring");
        helper.succeedWhen(() -> helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "a crater where it stood"));
    }

    /** Delivered by a missile (the penetrate call), it bores a 3x3 shaft down through stone and obsidian. */
    private static void penetrate(GameTestHelper helper) {
        floor(helper);
        for (int y = 2; y <= 9; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 7; z <= 9; z++) {
                    helper.setBlock(new BlockPos(x, y, z), y == 4 && x == 8 && z == 8 ? Blocks.OBSIDIAN : Blocks.STONE);
                }
            }
        }
        Charges.penetrate(helper.getLevel(), helper.absolutePos(new BlockPos(8, 10, 8)), null);
        helper.succeedWhen(() -> {
            for (int y = 2; y <= 9; y++) {
                for (int x = 7; x <= 9; x++) {
                    for (int z = 7; z <= 9; z++) {
                        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir(),
                                "a 3x3 shaft bored at " + x + " " + y + " " + z);
                    }
                }
            }
        });
    }

    /** The thermobaric bomb disperses its cloud first: for a moment nothing is broken, then it is lit. */
    private static void thermobaric(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.THERMOBARIC_WARHEAD.block().get(), at, Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!is(helper, at, GZOrdnance.THERMOBARIC_WARHEAD.block().get()), "the bomb has burst");
            helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.STONE) && Charges.pendingAt(helper.getLevel(), helper.absolutePos(at)),
                    "but the cloud is not lit yet");
            helper.succeedWhen(() -> helper.assertTrue(is(helper, new BlockPos(8, 1, 8), Blocks.AIR), "then it is"));
        });
    }

    /** The gas warhead set off on the ground throws out its gas canisters. */
    private static void canisters(GameTestHelper helper, Block warhead, net.minecraft.world.item.Item grenade) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, warhead, at, Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(16, 16, 16);
        var canisters = helper.getLevel().getEntitiesOfClass(ThrownExplosive.class, area,
                e -> e.getItem().is(grenade));
        helper.assertTrue(canisters.size() == Charges.CLUSTER_CANISTERS, "canisters out (" + canisters.size() + ")");
        helper.succeed();
    }

    /** The toxic waste warhead splashes toxic sludge all round it. */
    private static void toxicWaste(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.TOXIC_WASTE_WARHEAD.block().get(), at, Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        helper.succeedWhen(() -> {
            int sludge = 0;
            for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(15, 5, 15)))) {
                if (helper.getLevel().getBlockState(p).is(GZOrdnance.TOXIC_SLUDGE.get())) {
                    sludge++;
                }
            }
            helper.assertTrue(sludge >= 8, "sludge splashed round (" + sludge + ")");
        });
    }

    /**
     * Carried by a missile, a cluster warhead (smoke, harmless here) bursts high over its target, not on it: its
     * canisters come down out of the sky.
     */
    private static void airburst(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        net.minecraft.world.phys.Vec3 target = helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 1, 8));
        var f = dev.groundzero.content.launch.Flights.launch(level, target.add(-150, 0, 0), target,
                new net.minecraft.world.item.ItemStack(GZOrdnance.SMOKE_WARHEAD.item().get()), null);
        f.skipForTest(f.ticksToImpact() - 25);
        AABB sky = new AABB(target, target).inflate(40, 60, 40);
        helper.succeedWhen(() -> {
            var high = level.getEntitiesOfClass(ThrownExplosive.class, sky,
                    e -> e.getItem().is(GZOrdnance.SMOKE_GRENADE.get()) && e.getY() > target.y + 10);
            helper.assertTrue(high.size() >= Charges.CLUSTER_CANISTERS / 2, "canisters raining from high up (" + high.size() + ")");
            helper.assertTrue(!dev.groundzero.content.launch.Flights.of(level).all().contains(f), "the missile is spent");
        });
    }

    /** The cluster bomb bursts into 24 bomblets. */
    private static void cluster(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.CLUSTER_WARHEAD.block().get(), at, Direction.NORTH, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(16, 16, 16);
        var bomblets = helper.getLevel().getEntitiesOfClass(ThrownExplosive.class, area);
        helper.assertTrue(bomblets.size() == Charges.CLUSTER_BOMBLETS, "bomblets out (" + bomblets.size() + ")");
        helper.succeed();
    }

    /** The napalm warhead lays burning napalm all round itself. */
    private static void napalm(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        ChargeBlockEntity be = place(helper, GZOrdnance.NAPALM_WARHEAD.block().get(), at, Direction.EAST, false);
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        int[] sides = new int[4];                                    // east, west, south, north of the warhead
        BlockPos c = helper.absolutePos(at);
        for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(15, 4, 15)))) {
            if (helper.getLevel().getBlockState(p).is(GZOrdnance.NAPALM_FIRE.get())) {
                if (p.getX() > c.getX()) sides[0]++;
                if (p.getX() < c.getX()) sides[1]++;
                if (p.getZ() > c.getZ()) sides[2]++;
                if (p.getZ() < c.getZ()) sides[3]++;
            }
        }
        helper.assertTrue(java.util.Arrays.stream(sides).allMatch(n -> n >= 2), "napalm all round it " + java.util.Arrays.toString(sides));
        helper.succeed();
    }

    /** Napalm fire sets a pig in it alight, and burns out stage by stage. */
    private static void napalmFire(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at, GZOrdnance.NAPALM_FIRE.get());
        var pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(pig.getRemainingFireTicks() > 0, "the pig is alight");
            var level = helper.getLevel();
            BlockPos a = helper.absolutePos(at);
            for (int i = 0; i < NapalmFireBlock.STAGES; i++) {
                level.getBlockState(a).tick(level, a, level.getRandom());
            }
            helper.assertTrue(!is(helper, at, GZOrdnance.NAPALM_FIRE.get()), "burned out");
            pig.discard();
            helper.succeed();
        });
    }
}
