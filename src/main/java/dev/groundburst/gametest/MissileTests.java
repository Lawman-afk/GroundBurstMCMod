package dev.groundburst.gametest;

import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.launch.Flights;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Phase 7 slice 7b (DESIGN.md §10): what a missile's parts do (its guidance, its motor's range, its hardening). */
final class MissileTests {
    private MissileTests() {}

    static void register() {
        // (Assembling and taking missiles apart moved to the missile assemblers with the bench's missile page: AssemblerTests.)
        GBGameTests.test("guidance_sets_how_close_it_lands", GBGameTests.FLOOR_16, 20, MissileTests::guidance);
        GBGameTests.test("silo_range_comes_from_its_motor", GBGameTests.FLOOR_16, 20, MissileTests::siloRange);
        GBGameTests.testOpenSky("hardened_missile_flies_through_an_emp", GBGameTests.FLOOR_16, 400, MissileTests::hardened);
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = 0.3f;
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /**
     * Over many shots of 1000 blocks: unguided within 250 blocks (and on average far off), inertial within 40, GPS within
     * 2; misses spread more long or short than to the side.
     */
    private static void guidance(GameTestHelper helper) {
        RandomSource r = RandomSource.create(7);
        Vec3 from = Vec3.ZERO, target = new Vec3(1000, 0, 0);
        for (var g : MissileParts.Guidance.values()) {
            double worst = 0, total = 0, longShort = 0, side = 0;
            for (int i = 0; i < 500; i++) {
                Vec3 off = g.aim(from, target, r).subtract(target);
                worst = Math.max(worst, off.length());
                total += off.length();
                longShort = Math.max(longShort, Math.abs(off.x));
                side = Math.max(side, Math.abs(off.z));
            }
            double limit = g.scatter(1000);              // unguided 250, inertial and laser (no spot) 40, GPS 2
            helper.assertTrue(worst <= limit + 1e-6 && worst > limit * 0.8, g + " lands within " + limit + " (worst " + worst + ")");
            helper.assertTrue(total / 500 > limit * 0.35, g + " misses are mostly wide (average " + total / 500 + ")");
            helper.assertTrue(side <= limit * 0.5 + 1e-6 && side < longShort, g + " spreads long and short more than sideways");
        }
        helper.succeed();
    }

    /** A silo loaded with a Solid Rocket Motor I missile (400 blocks) will not arm on a target 500 blocks off. */
    private static void siloRange(GameTestHelper helper) {
        LaunchTestsAccess access = new LaunchTestsAccess(helper);
        var s = access.silo();
        s.inventory().setItem(0, MissileAssembly.missile(new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.S,
                MissileParts.Motor.SRM_I, MissileParts.Guidance.UNGUIDED, false)));
        s.energy().generate(dev.groundburst.content.launch.LaunchSiloBlockEntity.LAUNCH_COST);
        helper.assertTrue(s.range() == 400, "range 400: " + s.range());
        s.setTarget(s.getBlockPos().getX() + 500, s.getBlockPos().getZ());
        helper.assertTrue(!s.arm(true), "too far to arm");
        s.setTarget(s.getBlockPos().getX() + 300, s.getBlockPos().getZ());
        helper.assertTrue(s.arm(true), "in range");
        s.arm(false);
        helper.succeed();
    }

    /** A hardened missile flying out through an EMP field still sets its warhead off at the target. */
    private static void hardened(GameTestHelper helper) {
        floor(helper);
        Vec3 pad = helper.absoluteVec(new Vec3(3, 2, 3)), target = helper.absoluteVec(new Vec3(13, 2, 13));
        EmpFields.add(helper.getLevel(), pad, 12, 400, null);
        MissileSpec spec = new MissileSpec(new ItemStack(GBOrdnance.HE_WARHEAD.item().get()), SizeClass.M, MissileParts.Motor.SRM_II,
                MissileParts.Guidance.GPS, true);
        var f = Flights.launch(helper.getLevel(), pad, target, spec, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(!Flights.of(helper.getLevel()).all().contains(f), "it has come down");
            boolean hole = false;
            for (BlockPos p : BlockPos.betweenClosed(11, 1, 11, 15, 1, 15)) {
                hole |= helper.getLevel().getBlockState(helper.absolutePos(p)).isAir();
            }
            helper.assertTrue(hole, "its warhead went off");
        });
    }

    /** Builds a formed silo through the launch tests' helpers. */
    private record LaunchTestsAccess(GameTestHelper helper) {
        dev.groundburst.content.launch.LaunchSiloBlockEntity silo() {
            return LaunchTests.formedSilo(helper);
        }
    }
}
