package dev.groundzero.gametest;

import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.Fuze;
import dev.groundzero.content.assembly.FuzeSettings;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileItem;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.SizeClass;
import dev.groundzero.content.effect.EmpFields;
import dev.groundzero.content.launch.Flights;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.WideMachineBlock;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.Charges;
import dev.groundzero.registry.GZDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Phase 7 slice 7b (DESIGN.md §10): missiles assembled from parts, and what their parts do. */
final class MissileTests {
    private MissileTests() {}

    static void register() {
        GZGameTests.test("bench_assembles_a_missile", GZGameTests.FLOOR_16, 240, MissileTests::assembles);
        GZGameTests.test("missile_body_must_take_the_warhead", GZGameTests.FLOOR_16, 20, MissileTests::bodyClass);
        GZGameTests.test("missile_motor_must_fit_the_body", GZGameTests.FLOOR_16, 20, MissileTests::motorFit);
        GZGameTests.test("bench_takes_a_missile_apart", GZGameTests.FLOOR_16, 20, MissileTests::takesApart);
        GZGameTests.test("guidance_sets_how_close_it_lands", GZGameTests.FLOOR_16, 20, MissileTests::guidance);
        GZGameTests.test("silo_range_comes_from_its_motor", GZGameTests.FLOOR_16, 20, MissileTests::siloRange);
        GZGameTests.testOpenSky("hardened_missile_flies_through_an_emp", GZGameTests.FLOOR_16, 400, MissileTests::hardened);
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = 0.3f;
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    private static AssemblyBenchBlockEntity bench(GameTestHelper helper) {
        BlockPos rel = new BlockPos(8, 2, 8);
        Block block = GZAssembly.ASSEMBLY_BENCH.block().get();
        helper.setBlock(rel, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        helper.setBlock(rel.west(), block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH)
                .setValue(WideMachineBlock.PART, WideMachineBlock.Part.SIDE));
        return (AssemblyBenchBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
    }

    private static ItemStack warhead(ItemStack w, Fuze fuze, int value) {
        w.set(GZDataComponents.FUZE.get(), FuzeSettings.of(fuze, value));
        return w;
    }

    private static void load(AssemblyBenchBlockEntity b, ItemStack warhead, ItemStack body, ItemStack motor, ItemStack guidance, ItemStack module) {
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD, warhead);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_BODY, body);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_MOTOR, motor);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_GUIDANCE, guidance);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_MODULE, module);
    }

    /** A gas warhead (barometric at 40), a medium body, Solid Rocket Motor II, GPS and hardening make that missile. */
    private static void assembles(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper);
        b.energy().generate(AssemblyBenchBlockEntity.CAPACITY);
        load(b, warhead(new ItemStack(GZOrdnance.GAS_WARHEAD.item().get()), Fuze.BAROMETRIC, 40), new ItemStack(GZAssembly.MEDIUM_BODY.get()),
                new ItemStack(GZAssembly.SRM_II.get()), new ItemStack(GZAssembly.GPS_GUIDANCE.get()), new ItemStack(GZAssembly.HARDENED_ELECTRONICS.get()));
        helper.assertTrue(b.startMissile(), "assembly starts: " + b.missileProblem());
        helper.succeedWhen(() -> {
            MissileSpec s = MissileItem.spec(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MISSILE));
            helper.assertTrue(s != null, "a missile");
            helper.assertTrue(s.warhead().is(GZOrdnance.GAS_WARHEAD.item().get()) && s.body() == SizeClass.M && s.motor() == MissileParts.Motor.SRM_II
                    && s.guidance() == MissileParts.Guidance.GPS && s.hardened() && s.range() == 1024, "its parts: " + s);
            helper.assertTrue(s.warhead().getOrDefault(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT).value() == 40, "the fuze kept its setting");
            for (int i = AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD; i <= AssemblyBenchBlockEntity.SLOT_MODULE; i++) {
                helper.assertTrue(b.inventory().getItem(i).isEmpty(), "part slot " + i + " used");
            }
        });
    }

    /** The Massive Ordnance Warhead (class L) does not go on a Medium body. */
    private static void bodyClass(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper);
        load(b, new ItemStack(GZOrdnance.MASSIVE_WARHEAD.item().get()), new ItemStack(GZAssembly.MEDIUM_BODY.get()),
                new ItemStack(GZAssembly.SRM_II.get()), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(b.missileProblem() == MissileAssembly.Problem.WARHEAD_TOO_BIG, "too big: " + b.missileProblem());
        helper.assertTrue(!b.startMissile(), "it will not start");
        helper.succeed();
    }

    /** A Liquid-fuel Stage fits only a Heavy body. */
    private static void motorFit(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper);
        load(b, new ItemStack(GZOrdnance.HE_WARHEAD.item().get()), new ItemStack(GZAssembly.MEDIUM_BODY.get()),
                new ItemStack(GZAssembly.LIQUID_FUEL_STAGE.get()), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(b.missileProblem() == MissileAssembly.Problem.MOTOR_DOES_NOT_FIT, "does not fit: " + b.missileProblem());
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_BODY, new ItemStack(GZAssembly.HEAVY_BODY.get()));
        helper.assertTrue(b.missileProblem() == MissileAssembly.Problem.NONE, "fits a heavy body: " + b.missileProblem());
        helper.succeed();
    }

    /** A missile comes apart into its warhead (fuze and all), body, motor and guidance (no module: none fitted). */
    private static void takesApart(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper);
        ItemStack w = warhead(new ItemStack(GZOrdnance.HE_WARHEAD.item().get()), Fuze.BAROMETRIC, 25);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_MISSILE, MissileAssembly.missile(new MissileSpec(w, SizeClass.S,
                MissileParts.Motor.SRM_I, MissileParts.Guidance.INERTIAL, false)));
        helper.assertTrue(b.takeApartMissile(), "taken apart");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD).getOrDefault(GZDataComponents.FUZE.get(),
                FuzeSettings.IMPACT).value() == 25, "the warhead, its fuze set as it was");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_BODY).is(GZAssembly.TACTICAL_BODY.get()), "the body");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MOTOR).is(GZAssembly.SRM_I.get()), "the motor");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_GUIDANCE).is(GZAssembly.INERTIAL_GUIDANCE.get()), "the guidance");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MODULE).isEmpty(), "no module");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MISSILE).isEmpty(), "the missile is gone");
        helper.succeed();
    }

    /** Over many shots of 1000 blocks: unguided within 80 blocks, inertial within 20, GPS within 2. */
    private static void guidance(GameTestHelper helper) {
        RandomSource r = RandomSource.create(7);
        Vec3 from = Vec3.ZERO, target = new Vec3(1000, 0, 0);
        for (var g : MissileParts.Guidance.values()) {
            double worst = 0;
            for (int i = 0; i < 500; i++) {
                worst = Math.max(worst, g.aim(from, target, r).distanceTo(target));
            }
            double limit = g == MissileParts.Guidance.UNGUIDED ? 80 : g == MissileParts.Guidance.INERTIAL ? 20 : 2;
            helper.assertTrue(worst <= limit + 1e-6 && worst > limit * 0.5, g + " lands within " + limit + " (worst " + worst + ")");
        }
        helper.succeed();
    }

    /** A silo loaded with a Solid Rocket Motor I missile (400 blocks) will not arm on a target 500 blocks off. */
    private static void siloRange(GameTestHelper helper) {
        LaunchTestsAccess access = new LaunchTestsAccess(helper);
        var s = access.silo();
        s.inventory().setItem(0, MissileAssembly.missile(new MissileSpec(new ItemStack(GZOrdnance.HE_WARHEAD.item().get()), SizeClass.S,
                MissileParts.Motor.SRM_I, MissileParts.Guidance.UNGUIDED, false)));
        s.energy().generate(dev.groundzero.content.launch.LaunchSiloBlockEntity.LAUNCH_COST);
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
        MissileSpec spec = new MissileSpec(new ItemStack(GZOrdnance.HE_WARHEAD.item().get()), SizeClass.M, MissileParts.Motor.SRM_II,
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
        dev.groundzero.content.launch.LaunchSiloBlockEntity silo() {
            return LaunchTests.formedSilo(helper);
        }
    }
}
