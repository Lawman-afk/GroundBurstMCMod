package dev.groundzero.gametest;

import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.Fuze;
import dev.groundzero.content.assembly.FuzeSettings;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.WarheadAssembly;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.WideMachineBlock;
import dev.groundzero.content.ordnance.GZOrdnance;
import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeBlockEntity;
import dev.groundzero.content.ordnance.charge.Charges;
import dev.groundzero.registry.GZDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Phase 7 slice 7a (DESIGN.md §9-10): the Ordnance Assembly Bench and warhead fuzes. */
final class AssemblyTests {
    private AssemblyTests() {}

    static void register() {
        GZGameTests.test("bench_assembles_a_warhead_with_its_fuze", GZGameTests.FLOOR_16, 200, AssemblyTests::assembles);
        GZGameTests.test("bench_refuses_a_casing_of_the_wrong_class", GZGameTests.FLOOR_16, 20, AssemblyTests::wrongClass);
        GZGameTests.test("bench_waits_without_power", GZGameTests.FLOOR_16, 40, AssemblyTests::noPower);
        GZGameTests.test("bench_takes_a_warhead_apart", GZGameTests.FLOOR_16, 20, AssemblyTests::takesApart);
        GZGameTests.test("bench_is_two_blocks_wide", GZGameTests.FLOOR_16, 20, AssemblyTests::wide);
        GZGameTests.test("warhead_keeps_its_fuze_when_picked_up", GZGameTests.FLOOR_16, 20, AssemblyTests::keepsFuze);
        GZGameTests.test("placed_warhead_ignores_detonators", GZGameTests.FLOOR_16, 20, AssemblyTests::placedIgnoresDetonator);
        GZGameTests.test("bench_sets_the_fuze_setting", GZGameTests.FLOOR_16, 200, AssemblyTests::fuzeSetting);
        GZGameTests.test("bench_halves_have_their_own_sides", GZGameTests.FLOOR_16, 20, AssemblyTests::halfSides);
        GZGameTests.testOpenSky("barometric_fuze_bursts_high_over_the_target", GZGameTests.FLOOR_16, 400, AssemblyTests::barometric);
        GZGameTests.testOpenSky("timed_fuze_bursts_in_flight", GZGameTests.FLOOR_16, 200, AssemblyTests::timedFlight);
        GZGameTests.testOpenSky("remote_fuze_bursts_on_the_detonator_in_flight", GZGameTests.FLOOR_16, 200, AssemblyTests::remoteFlight);
    }

    private static ItemStack fuzed(Fuze fuze, int value) {
        ItemStack w = new ItemStack(GZOrdnance.HE_WARHEAD.item().get());
        w.set(GZDataComponents.FUZE.get(), FuzeSettings.of(fuze, value));
        return w;
    }

    /** A missile whose warhead has a barometric fuze at 20 bursts about 20 blocks over its target, not on the ground. */
    private static void barometric(GameTestHelper helper) {
        floor(helper);
        Vec3 pad = helper.absoluteVec(new Vec3(8, 2, 8)), target = pad.add(14, 0, 0);
        var f = dev.groundzero.content.launch.Flights.launch(helper.getLevel(), pad, target, fuzed(Fuze.BAROMETRIC, 20), null);
        helper.succeedWhen(() -> {
            helper.assertTrue(!dev.groundzero.content.launch.Flights.of(helper.getLevel()).all().contains(f), "it burst");
            double over = f.position().y - target.y;
            helper.assertTrue(over > 12 && over <= 21, "about 20 blocks up: " + over);
        });
    }

    /** A timed fuze at 2 s goes off in the air two seconds after the missile lights, long before it would come down. */
    private static void timedFlight(GameTestHelper helper) {
        floor(helper);
        Vec3 pad = helper.absoluteVec(new Vec3(8, 2, 8));
        var f = dev.groundzero.content.launch.Flights.launch(helper.getLevel(), pad, pad.add(300, 0, 0), fuzed(Fuze.TIMED, 2), null);
        helper.succeedWhen(() -> {
            helper.assertTrue(!dev.groundzero.content.launch.Flights.of(helper.getLevel()).all().contains(f), "it burst");
            helper.assertTrue(f.ticksToImpact() > 100, "well short of its target");
        });
    }

    /** A remote fuze: pressing a detonator on its frequency sets the missile's warhead off where it flies. */
    private static void remoteFlight(GameTestHelper helper) {
        floor(helper);
        Vec3 pad = helper.absoluteVec(new Vec3(8, 2, 8));
        var f = dev.groundzero.content.launch.Flights.launch(helper.getLevel(), pad, pad.add(300, 0, 0), fuzed(Fuze.REMOTE, 77), null);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(Charges.remote(helper.getLevel(), f.position(), 76, null, 64) == 0, "another frequency does nothing");
            helper.assertTrue(Charges.remote(helper.getLevel(), f.position(), 77, null, 64) == 1, "its frequency sets it off");
            helper.assertTrue(!dev.groundzero.content.launch.Flights.of(helper.getLevel()).all().contains(f), "the flight is over");
            helper.succeed();
        });
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = 0.3f;
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** Both halves of a bench facing north (its side half to the west), its block entity. */
    private static AssemblyBenchBlockEntity bench(GameTestHelper helper, BlockPos rel) {
        Block block = GZAssembly.ASSEMBLY_BENCH.block().get();
        helper.setBlock(rel, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        helper.setBlock(rel.west(), block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH)
                .setValue(WideMachineBlock.PART, WideMachineBlock.Part.SIDE));
        return (AssemblyBenchBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
    }

    private static void load(AssemblyBenchBlockEntity b, ItemStack core, ItemStack casing, ItemStack fuze) {
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_CORE, core);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_CASING, casing);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_FUZE, fuze);
    }

    /** An HE charge, a light casing and a remote fuze make an HE warhead carrying a remote fuze; the parts are used. */
    private static void assembles(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper, new BlockPos(8, 2, 8));
        b.energy().generate(AssemblyBenchBlockEntity.CAPACITY);
        load(b, new ItemStack(GZAssembly.HE_CHARGE.get()), new ItemStack(GZAssembly.LIGHT_CASING.get()), new ItemStack(GZAssembly.REMOTE_FUZE.get()));
        helper.assertTrue(b.problem() == WarheadAssembly.Problem.NONE, "the parts fit: " + b.problem());
        helper.assertTrue(b.startAssembly(), "assembly starts");
        helper.succeedWhen(() -> {
            ItemStack out = b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
            helper.assertTrue(out.is(GZOrdnance.HE_WARHEAD.item().get()), "an HE warhead");
            helper.assertTrue(out.getOrDefault(GZDataComponents.FUZE.get(), FuzeSettings.IMPACT).fuze() == Fuze.REMOTE, "with a remote fuze");
            helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_CORE).isEmpty()
                    && b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_CASING).isEmpty()
                    && b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_FUZE).isEmpty(), "the parts are used");
        });
    }

    /** A medium core in a light casing will not go together. */
    private static void wrongClass(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper, new BlockPos(8, 2, 8));
        b.energy().generate(AssemblyBenchBlockEntity.CAPACITY);
        load(b, new ItemStack(GZAssembly.HEAVY_HE_CHARGE.get()), new ItemStack(GZAssembly.LIGHT_CASING.get()), new ItemStack(GZAssembly.IMPACT_FUZE.get()));
        helper.assertTrue(b.problem() == WarheadAssembly.Problem.WRONG_CASING, "wrong casing: " + b.problem());
        helper.assertTrue(!b.startAssembly(), "it will not start");
        helper.succeed();
    }

    /** With no power the job waits (no progress, status says so). */
    private static void noPower(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper, new BlockPos(8, 2, 8));
        load(b, new ItemStack(GZAssembly.HE_CHARGE.get()), new ItemStack(GZAssembly.LIGHT_CASING.get()), new ItemStack(GZAssembly.IMPACT_FUZE.get()));
        helper.assertTrue(b.startAssembly(), "assembly starts");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(b.progress() == 0 && b.assembling(), "no progress without power");
            helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD).isEmpty(), "no warhead");
            helper.succeed();
        });
    }

    /** A warhead in the output slot comes apart into its core, a casing of its class and its fuze. */
    private static void takesApart(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper, new BlockPos(8, 2, 8));
        ItemStack warhead = new ItemStack(GZOrdnance.GAS_WARHEAD.item().get());
        warhead.set(GZDataComponents.FUZE.get(), FuzeSettings.fresh(Fuze.BAROMETRIC));
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_WARHEAD, warhead);
        helper.assertTrue(b.takeApart(), "taken apart");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_CORE).is(GZAssembly.GAS_DISPENSER.get()), "the core");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_CASING).is(GZAssembly.MEDIUM_CASING.get()), "a medium casing");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_FUZE).is(GZAssembly.BAROMETRIC_FUZE.get()), "its fuze");
        helper.assertTrue(b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD).isEmpty(), "the warhead is gone");
        helper.succeed();
    }

    /** Breaking either half removes both; the side half reaches the main half's block entity. */
    private static void wide(GameTestHelper helper) {
        floor(helper);
        BlockPos main = new BlockPos(8, 2, 8);
        bench(helper, main);
        helper.assertTrue(WideMachineBlock.base(helper.absolutePos(main.west()), helper.getLevel().getBlockState(helper.absolutePos(main.west())))
                .equals(helper.absolutePos(main)), "the side half points at the main half");
        helper.getLevel().destroyBlock(helper.absolutePos(main.west()), false);
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(main)).isAir(), "both halves gone"));
    }

    /** A placed warhead's fuze goes back into its item (what it drops or is picked as). */
    private static void keepsFuze(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at, GZOrdnance.HE_WARHEAD.block().get().defaultBlockState().setValue(ChargeBlock.FACING, Direction.NORTH));
        ChargeBlockEntity be = ChargeBlock.at(helper.getLevel(), helper.absolutePos(at));
        be.setFuze(FuzeSettings.of(Fuze.TIMED, 42));
        FuzeSettings kept = be.collectComponents().get(GZDataComponents.FUZE.get());
        helper.assertTrue(kept != null && kept.fuze() == Fuze.TIMED && kept.value() == 42, "the item keeps a 42 s timed fuze: " + kept);
        helper.succeed();
    }

    /** A placed warhead does not answer a detonator, whatever its fuze: fuzes act only in a missile. */
    private static void placedIgnoresDetonator(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at, GZOrdnance.HE_WARHEAD.block().get().defaultBlockState().setValue(ChargeBlock.FACING, Direction.NORTH));
        ChargeBlockEntity be = ChargeBlock.at(helper.getLevel(), helper.absolutePos(at));
        be.setFuze(FuzeSettings.of(Fuze.REMOTE, 7));
        helper.assertTrue(!be.hearsDetonator(), "it does not listen");
        helper.assertTrue(Charges.remote(helper.getLevel(), helper.absoluteVec(new Vec3(8, 3, 8)), 7, null, 12) == 0, "nothing heard it");
        helper.succeed();
    }

    /** The setting typed on the bench goes with the fuze into the warhead (and comes back out when it is taken apart). */
    private static void fuzeSetting(GameTestHelper helper) {
        floor(helper);
        AssemblyBenchBlockEntity b = bench(helper, new BlockPos(8, 2, 8));
        b.energy().generate(AssemblyBenchBlockEntity.CAPACITY);
        load(b, new ItemStack(GZAssembly.HE_CHARGE.get()), new ItemStack(GZAssembly.LIGHT_CASING.get()), new ItemStack(GZAssembly.BAROMETRIC_FUZE.get()));
        b.setFuzeValue(500);
        helper.assertTrue(b.fuzeValue() == 200, "kept within the fuze's range: " + b.fuzeValue());
        b.setFuzeValue(75);
        helper.assertTrue(b.startAssembly(), "assembly starts");
        helper.succeedWhen(() -> {
            ItemStack out = b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD);
            FuzeSettings f = out.get(GZDataComponents.FUZE.get());
            helper.assertTrue(f != null && f.fuze() == Fuze.BAROMETRIC && f.value() == 75, "a barometric fuze at 75: " + f);
        });
    }

    /** Each half of the bench answers pipes through its own faces; the faces where the halves meet expose nothing. */
    private static void halfSides(GameTestHelper helper) {
        floor(helper);
        BlockPos main = new BlockPos(8, 2, 8);
        AssemblyBenchBlockEntity b = bench(helper, main);
        var level = helper.getLevel();
        BlockPos m = helper.absolutePos(main), s = helper.absolutePos(main.west());
        var cap = net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK;
        helper.assertTrue(level.getCapability(cap, m, Direction.DOWN) != null && level.getCapability(cap, s, Direction.DOWN) != null,
                "both bottoms take items to begin with");
        // The side half's bottom set to None: only that bottom closes.
        b.changeSides(true, c -> c.cycleItems(dev.groundzero.content.machine.SideConfig.Face.BOTTOM, true, b.allowedItemModes()));
        helper.assertTrue(b.sides(true).items(dev.groundzero.content.machine.SideConfig.Face.BOTTOM)
                == dev.groundzero.content.machine.SideConfig.ItemMode.NONE, "the side half's bottom is None");
        helper.assertTrue(level.getCapability(cap, s, Direction.DOWN) == null, "the side half's bottom is closed");
        helper.assertTrue(level.getCapability(cap, m, Direction.DOWN) != null, "the main half's bottom is still open");
        helper.assertTrue(level.getCapability(cap, m, Direction.WEST) == null && level.getCapability(cap, s, Direction.EAST) == null,
                "the faces between the halves expose nothing");
        helper.succeed();
    }
}
