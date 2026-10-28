package dev.groundburst.gametest;

import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.machine.CircuitFabricatorBlockEntity;
import dev.groundburst.content.machine.ExoticMatterFabricatorBlockEntity;
import dev.groundburst.content.machine.FusionFuelSynthesizerBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.GasCentrifugeBlockEntity;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.ProcessingBlockEntity;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.nuclear.GBNuclear;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.resource.GBResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

/** Phase 4 slice 5: Circuit Fabricator, Gas Centrifuge (and its cascade), Fusion Fuel Synthesizer, Exotic Matter Fabricator. */
final class AdvancedMachineTests {
    private AdvancedMachineTests() {}

    static void register() {
        GBGameTests.test("fabricator_makes_basic_circuits", GBGameTests.FLOOR_16, 260, AdvancedMachineTests::basicCircuits);
        GBGameTests.test("fabricator_coil_needs_liquid_nitrogen", GBGameTests.FLOOR_16, 480, AdvancedMachineTests::coil);
        GBGameTests.test("centrifuge_enriches_uranium", GBGameTests.FLOOR_16, 660, AdvancedMachineTests::centrifuge);
        GBGameTests.test("centrifuge_cascade_counts_a_line", GBGameTests.FLOOR_16, 20, AdvancedMachineTests::cascadeLine);
        GBGameTests.test("centrifuge_cascade_runs_faster", GBGameTests.FLOOR_16, 140, AdvancedMachineTests::cascadeSpeed);
        GBGameTests.test("synthesizer_makes_lithium_deuteride", GBGameTests.FLOOR_16, 360, AdvancedMachineTests::fusionFuel);
        GBGameTests.test("exotic_fabricator_stabilises_shards", GBGameTests.FLOOR_16, 660, AdvancedMachineTests::exotic);
        GBGameTests.test("canning_machine_fills_canisters", GBGameTests.FLOOR_16, 200, AdvancedMachineTests::cannerFills);
        GBGameTests.test("canning_machine_empties_canisters", GBGameTests.FLOOR_16, 200, AdvancedMachineTests::cannerEmpties);
    }

    private static <T extends MachineBlockEntity> T place(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.Block block, Class<T> type) {
        helper.setBlock(pos, block.defaultBlockState());
        if (block instanceof TallMachineBlock) {
            block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        }
        return helper.getBlockEntity(pos, type);
    }

    private static int progress(ProcessingBlockEntity machine) {
        return machine.data().get(MachineBlockEntity.DATA_BASE_COUNT + ProcessingBlockEntity.DATA_PROGRESS);
    }

    /** Two copper wire, two redstone and a plastic sheet make two basic circuits. */
    private static void basicCircuits(GameTestHelper helper) {
        CircuitFabricatorBlockEntity fab = place(helper, new BlockPos(3, 1, 3), GBMachines.CIRCUIT_FABRICATOR.block().get(), CircuitFabricatorBlockEntity.class);
        fab.energy().generate(20_000);
        fab.inventory().setItem(0, new ItemStack(GBParts.COPPER_WIRE.get(), 2));
        fab.inventory().setItem(1, new ItemStack(Items.REDSTONE, 2));
        fab.inventory().setItem(2, new ItemStack(GBChemicals.PLASTIC_SHEET.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(fab.inventory().getItem(4).is(GBParts.BASIC_CIRCUIT.get()) && fab.inventory().getItem(4).getCount() == 2,
                    "two basic circuits in the output, have " + fab.inventory().getItem(4));
            helper.assertTrue(fab.inventory().getItem(0).isEmpty() && fab.inventory().getItem(2).isEmpty(), "inputs are used up");
        });
    }

    /** A superconducting coil waits for liquid nitrogen in the coolant tank, then uses 1000 mB of it. */
    private static void coil(GameTestHelper helper) {
        CircuitFabricatorBlockEntity fab = place(helper, new BlockPos(3, 1, 3), GBMachines.CIRCUIT_FABRICATOR.block().get(), CircuitFabricatorBlockEntity.class);
        fab.energy().generate(20_000);
        fab.inventory().setItem(0, new ItemStack(GBParts.TUNGSTEN_FILAMENT.get(), 2));
        fab.inventory().setItem(1, new ItemStack(GBResources.RARE_EARTH_DUST.get()));
        fab.inventory().setItem(2, new ItemStack(GBParts.GOLD_WIRE.get(), 2));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(fab.status() == MachineStatus.NEED_FLUID, "without liquid nitrogen the fabricator waits for fluid, status " + fab.status());
            fab.tank(0).fillInternal(new FluidStack(GBFluids.LIQUID_NITROGEN.source().get(), 1_000));
            helper.succeedWhen(() -> {
                helper.assertTrue(fab.inventory().getItem(4).is(GBParts.SUPERCONDUCTING_COIL.get()), "a superconducting coil is made");
                helper.assertTrue(fab.tank(0).isEmpty(), "1000 mB of liquid nitrogen is used");
            });
        });
    }

    /** 1000 mB of UF6 gives two enriched uranium nuggets and a depleted uranium dust. */
    private static void centrifuge(GameTestHelper helper) {
        GasCentrifugeBlockEntity cf = place(helper, new BlockPos(3, 1, 3), GBMachines.GAS_CENTRIFUGE.block().get(), GasCentrifugeBlockEntity.class);
        cf.tank(0).fillInternal(new FluidStack(GBFluids.URANIUM_HEXAFLUORIDE.source().get(), 1_000));
        helper.onEachTick(() -> cf.energy().generate(100));
        helper.succeedWhen(() -> {
            helper.assertTrue(cf.inventory().getItem(0).is(GBNuclear.ENRICHED_URANIUM_NUGGET.get()) && cf.inventory().getItem(0).getCount() == 2,
                    "two enriched uranium nuggets, have " + cf.inventory().getItem(0));
            helper.assertTrue(cf.inventory().getItem(1).is(GBNuclear.DEPLETED_URANIUM_DUST.get()), "a depleted uranium dust");
            helper.assertTrue(cf.tank(0).isEmpty(), "the UF6 is used");
        });
    }

    /**
     * Three side by side along their flange axis form a cascade of 3; a gap breaks the line; ten in a row count as eight;
     * a line along the other axis, or a neighbour turned a quarter, does not count.
     */
    private static void cascadeLine(GameTestHelper helper) {
        var block = GBMachines.GAS_CENTRIFUGE.block().get();
        GasCentrifugeBlockEntity[] row = new GasCentrifugeBlockEntity[3];
        for (int i = 0; i < 3; i++) {
            row[i] = place(helper, new BlockPos(1 + i, 1, 2), block, GasCentrifugeBlockEntity.class);
        }
        GasCentrifugeBlockEntity alone = place(helper, new BlockPos(5, 1, 2), block, GasCentrifugeBlockEntity.class);
        GasCentrifugeBlockEntity[] long_ = new GasCentrifugeBlockEntity[10];
        for (int i = 0; i < 10; i++) {
            long_[i] = place(helper, new BlockPos(1 + i, 1, 6), block, GasCentrifugeBlockEntity.class);
        }
        var level = helper.getLevel();
        for (GasCentrifugeBlockEntity c : row) {
            c.updateCascade(level);
            helper.assertTrue(c.cascade() == 3, "three in a row make a cascade of 3, got " + c.cascade());
        }
        alone.updateCascade(level);
        helper.assertTrue(alone.cascade() == 1, "a centrifuge one block away is not in the cascade, got " + alone.cascade());
        long_[0].updateCascade(level);
        helper.assertTrue(long_[0].cascade() == GasCentrifugeBlockEntity.MAX_CASCADE, "a cascade counts at most 8, got " + long_[0].cascade());
        // North-facing centrifuges have their flanges east and west: a north-south column is not a cascade.
        GasCentrifugeBlockEntity[] column = new GasCentrifugeBlockEntity[2];
        for (int i = 0; i < 2; i++) {
            column[i] = place(helper, new BlockPos(13, 1, 2 + i), block, GasCentrifugeBlockEntity.class);
        }
        column[0].updateCascade(level);
        helper.assertTrue(column[0].cascade() == 1, "neighbours off the flange axis do not count, got " + column[0].cascade());
        // Turn the middle of the three-row a quarter: its flanges no longer meet, so the row splits.
        BlockPos mid = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(mid, level.getBlockState(mid).setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        row[0].updateCascade(level);
        helper.assertTrue(row[0].cascade() == 1, "a neighbour turned a quarter breaks the line, got " + row[0].cascade());
        // Facing the opposite way keeps the flanges on the same axis, so the line holds.
        level.setBlock(mid, level.getBlockState(mid).setValue(dev.groundburst.content.machine.MachineBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
        row[0].updateCascade(level);
        helper.assertTrue(row[0].cascade() == 3, "a neighbour facing the opposite way still joins, got " + row[0].cascade());
        helper.assertTrue(Math.abs(GasCentrifugeBlockEntity.speedFor(3) - 1.30f) < 1e-4f, "3 centrifuges run 30% faster");
        helper.assertTrue(Math.abs(GasCentrifugeBlockEntity.speedFor(20) - 2.05f) < 1e-4f, "the bonus stops at +105%");
        helper.succeed();
    }

    /** A centrifuge in a pair gets 15% further through a run than one standing alone. */
    private static void cascadeSpeed(GameTestHelper helper) {
        var block = GBMachines.GAS_CENTRIFUGE.block().get();
        GasCentrifugeBlockEntity a = place(helper, new BlockPos(2, 1, 2), block, GasCentrifugeBlockEntity.class);
        GasCentrifugeBlockEntity b = place(helper, new BlockPos(3, 1, 2), block, GasCentrifugeBlockEntity.class);
        GasCentrifugeBlockEntity solo = place(helper, new BlockPos(2, 1, 6), block, GasCentrifugeBlockEntity.class);
        for (GasCentrifugeBlockEntity c : new GasCentrifugeBlockEntity[] {a, b, solo}) {
            c.tank(0).fillInternal(new FluidStack(GBFluids.URANIUM_HEXAFLUORIDE.source().get(), 1_000));
            c.energy().generate(25_000);
        }
        helper.runAfterDelay(100, () -> {
            int paired = progress(a), single = progress(solo);
            helper.assertTrue(single > 80, "the lone centrifuge is running, progress " + single);
            helper.assertTrue(paired * 100 >= single * 112, "the paired centrifuge is ~15% ahead: " + paired + " vs " + single);
            helper.succeed();
        });
    }

    /** Lithium-6 dust and 1000 mB of deuterium make a lithium deuteride pellet. */
    private static void fusionFuel(GameTestHelper helper) {
        FusionFuelSynthesizerBlockEntity syn = place(helper, new BlockPos(3, 1, 3), GBMachines.FUSION_FUEL_SYNTHESIZER.block().get(),
                FusionFuelSynthesizerBlockEntity.class);
        syn.energy().generate(60_000);
        syn.inventory().setItem(0, new ItemStack(GBChemicals.LITHIUM6_DUST.get()));
        syn.tank(0).fillInternal(new FluidStack(GBFluids.DEUTERIUM.source().get(), 1_000));
        helper.succeedWhen(() -> {
            helper.assertTrue(syn.inventory().getItem(1).is(GBNuclear.LITHIUM_DEUTERIDE_PELLET.get()), "a lithium deuteride pellet is made");
            helper.assertTrue(syn.tank(0).isEmpty() && syn.inventory().getItem(0).isEmpty(), "the deuterium and dust are used");
        });
    }

    /** Four exotic shards and a neodymium magnet make stabilised exotic matter (400 FE/t, topped up every tick). */
    private static void exotic(GameTestHelper helper) {
        ExoticMatterFabricatorBlockEntity fab = place(helper, new BlockPos(3, 1, 3), GBMachines.EXOTIC_MATTER_FABRICATOR.block().get(),
                ExoticMatterFabricatorBlockEntity.class);
        fab.inventory().setItem(0, new ItemStack(GBResources.EXOTIC_SHARD.get(), 4));
        fab.inventory().setItem(1, new ItemStack(GBParts.NEODYMIUM_MAGNET.get()));
        helper.onEachTick(() -> fab.energy().generate(ExoticMatterFabricatorBlockEntity.ENERGY_PER_TICK));
        helper.succeedWhen(() -> {
            helper.assertTrue(fab.inventory().getItem(3).is(GBNuclear.STABILISED_EXOTIC_MATTER.get()), "stabilised exotic matter is made");
            helper.assertTrue(fab.inventory().getItem(0).isEmpty(), "the shards are used");
        });
    }

    /** Two empty gas canisters and 2000 mB of deuterium in the supply tank become two deuterium canisters. */
    private static void cannerFills(GameTestHelper helper) {
        dev.groundburst.content.machine.CanningMachineBlockEntity can = place(helper, new BlockPos(3, 1, 3), GBMachines.CANNING_MACHINE.block().get(),
                dev.groundburst.content.machine.CanningMachineBlockEntity.class);
        can.energy().generate(10_000);
        can.inventory().setItem(0, new ItemStack(GBChemicals.EMPTY_GAS_CANISTER.get(), 2));
        can.tank(0).fillInternal(new FluidStack(GBFluids.DEUTERIUM.source().get(), 2_000));
        helper.succeedWhen(() -> {
            helper.assertTrue(can.inventory().getItem(1).is(GBChemicals.DEUTERIUM_CANISTER.get()) && can.inventory().getItem(1).getCount() == 2,
                    "two deuterium canisters are filled, have " + can.inventory().getItem(1));
            helper.assertTrue(can.tank(0).isEmpty() && can.inventory().getItem(0).isEmpty(), "the gas and the empty canisters are used");
        });
    }

    /** A napalm canister is poured into the catch tank and comes back out empty. */
    private static void cannerEmpties(GameTestHelper helper) {
        dev.groundburst.content.machine.CanningMachineBlockEntity can = place(helper, new BlockPos(3, 1, 3), GBMachines.CANNING_MACHINE.block().get(),
                dev.groundburst.content.machine.CanningMachineBlockEntity.class);
        can.energy().generate(10_000);
        can.inventory().setItem(0, new ItemStack(GBChemicals.NAPALM_CANISTER.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(can.inventory().getItem(1).is(dev.groundburst.content.part.GBParts.EMPTY_CANISTER.get()), "an empty canister comes out");
            helper.assertTrue(can.tank(1).amount() == 1_000 && can.tank(1).fluid().getFluid() == GBFluids.NAPALM.source().get(),
                    "1000 mB of napalm in the catch tank, have " + can.tank(1).amount());
        });
    }
}
