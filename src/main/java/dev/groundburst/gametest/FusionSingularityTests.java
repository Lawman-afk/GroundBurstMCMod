package dev.groundburst.gametest;

import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.machine.FusionReactorBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SingularityReactorBlockEntity;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.Multiblocks;
import dev.groundburst.content.nuclear.GBNuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Phase 4 slice 8: the Fusion Reactor (ignition, fuel, nitrogen) and the Singularity Reactor (cage, matter, collapse). */
final class FusionSingularityTests {
    private FusionSingularityTests() {}

    static void register() {
        GBGameTests.test("fusion_forms_and_unforms", GBGameTests.FLOOR_16, 20, FusionSingularityTests::fusionForms);
        GBGameTests.test("fusion_ignites_and_runs_on_gas", GBGameTests.FLOOR_16, 80, FusionSingularityTests::fusionRuns);
        GBGameTests.test("fusion_needs_nitrogen_to_light", GBGameTests.FLOOR_16, 40, FusionSingularityTests::fusionNeedsNitrogen);
        GBGameTests.test("fusion_takes_no_items", GBGameTests.FLOOR_16, 20, FusionSingularityTests::fusionNoItems);
        GBGameTests.test("fusion_plasma_goes_out_without_fuel", GBGameTests.FLOOR_16, 60, FusionSingularityTests::fusionRunsDry);
        GBGameTests.test("singularity_engages_and_burns_matter", GBGameTests.FLOOR_16, 60, FusionSingularityTests::singularityRuns);
        GBGameTests.test("singularity_collapses_without_power", GBGameTests.FLOOR_16, 40, FusionSingularityTests::singularityStarves);
        GBGameTests.test("singularity_collapses_when_broken", GBGameTests.FLOOR_16, 40, FusionSingularityTests::singularityBroken);
        GBGameTests.test("singularity_lets_go_when_seed_taken", GBGameTests.FLOOR_16, 40, FusionSingularityTests::singularitySeedOut);
        GBGameTests.test("singularity_takes_extra_floor_ports", GBGameTests.FLOOR_16, 20, FusionSingularityTests::singularityExtraPorts);
    }

    private static void build(GameTestHelper helper, BlockPos rel, Block controller, MultiblockDefinition def) {
        helper.setBlock(rel, controller.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : def.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    private static BlockPos cellOf(MultiblockDefinition def, BlockPos controller, char key) {
        return def.cells(controller, Direction.NORTH).stream().filter(c -> c.key() == key).findFirst().orElseThrow().pos();
    }

    // --- Fusion Reactor -------------------------------------------------------------------------------------------------------
    private static FusionReactorBlockEntity fusion(GameTestHelper helper) {
        BlockPos rel = new BlockPos(8, 1, 1);
        build(helper, rel, GBMachines.FUSION_REACTOR.block().get(), GBMultiblocks.FUSION_REACTOR);
        return helper.getBlockEntity(rel, FusionReactorBlockEntity.class);
    }

    private static void fuelGas(FusionReactorBlockEntity r, int mb) {
        r.tank(FusionReactorBlockEntity.TANK_DEUTERIUM).fillInternal(new FluidStack(GBFluids.DEUTERIUM.sourceFluid(), mb));
        r.tank(FusionReactorBlockEntity.TANK_TRITIUM).fillInternal(new FluidStack(GBFluids.TRITIUM.sourceFluid(), mb));
    }

    private static void nitrogen(FusionReactorBlockEntity r, int mb) {
        r.tank(FusionReactorBlockEntity.TANK_NITROGEN).fillInternal(new FluidStack(GBFluids.LIQUID_NITROGEN.sourceFluid(), mb));
    }

    /** Charges the ignition through the Energy Port, as a capacitor bank would: it takes power only while unlit. */
    private static void charge(GameTestHelper helper, FusionReactorBlockEntity r) {
        BlockPos port = cellOf(r.definition(), r.controllerPos(), 'E');
        var handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, port, Direction.SOUTH);
        helper.assertTrue(handler != null, "the Energy Port exposes the reactor");
        int total = 0;
        while (total < FusionReactorBlockEntity.IGNITION_ENERGY) {
            try (Transaction tx = Transaction.openRoot()) {
                int in = handler.insert(FusionReactorBlockEntity.IGNITION_INTAKE, tx);
                helper.assertTrue(in > 0, "the port takes the ignition charge");
                tx.commit();
                total += in;
            }
        }
    }

    private static void fusionForms(GameTestHelper helper) {
        FusionReactorBlockEntity r = fusion(helper);
        helper.assertTrue(r.isFormed(), "the fusion ring forms");
        var level = helper.getLevel();
        var parts = r.definition().partPositions(r.controllerPos(), r.controllerFacing());
        helper.assertTrue(parts.size() == 98, "96 ring parts and 3 solenoid blocks less the controller, have " + parts.size());
        for (BlockPos part : parts) {
            helper.assertTrue(Multiblocks.formedControllerOf(level, part) == r, "the part at " + part + " leads to the controller");
        }
        for (Direction d : Direction.values()) {
            helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, r.controllerPos(), d) == null, "the controller exposes nothing on " + d);
        }
        level.destroyBlock(cellOf(r.definition(), r.controllerPos(), 'S'), false);
        helper.assertTrue(!r.isFormed(), "taking out the central solenoid un-forms it");
        helper.succeed();
    }

    /** Gas and nitrogen in, ignition charged: the plasma lights, makes 6 000 FE a tick and uses fuel and nitrogen. */
    private static void fusionRuns(GameTestHelper helper) {
        FusionReactorBlockEntity r = fusion(helper);
        fuelGas(r, 1_000);
        nitrogen(r, 1_000);
        charge(helper, r);
        helper.runAfterDelay(41, () -> {
            helper.assertTrue(r.lit(), "the plasma is lit, status " + r.status());
            helper.assertTrue(r.ignition().stored() == 0, "the ignition charge is spent");
            helper.assertTrue(r.energy().stored() >= 30 * FusionReactorBlockEntity.OUTPUT, "about 6 000 FE a tick, have " + r.energy().stored());
            helper.assertTrue(r.tank(FusionReactorBlockEntity.TANK_NITROGEN).amount() < 1_000, "the magnets use nitrogen");
            helper.assertTrue(r.tank(FusionReactorBlockEntity.TANK_DEUTERIUM).amount() < 1_000
                    && r.tank(FusionReactorBlockEntity.TANK_TRITIUM).amount() < 1_000, "deuterium and tritium are burnt");
            helper.assertTrue(r.status() == MachineStatus.RUNNING, "running, status " + r.status());
            // Lit, the port takes no more charge.
            BlockPos port = cellOf(r.definition(), r.controllerPos(), 'E');
            var handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, port, Direction.SOUTH);
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(handler.insert(1_000, tx) == 0, "a lit reactor takes no power in");
            }
            helper.succeed();
        });
    }

    private static void fusionNeedsNitrogen(GameTestHelper helper) {
        FusionReactorBlockEntity r = fusion(helper);
        fuelGas(r, 1_000);
        charge(helper, r);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!r.lit(), "no nitrogen, no plasma");
            helper.assertTrue(r.status() == MachineStatus.NEED_FLUID, "it asks for nitrogen, status " + r.status());
            helper.assertTrue(r.ignition().stored() == FusionReactorBlockEntity.IGNITION_ENERGY, "the charge is kept");
            helper.succeed();
        });
    }

    /** The reactor burns gas only: no item slots, and the structure has no Item Port. */
    private static void fusionNoItems(GameTestHelper helper) {
        FusionReactorBlockEntity r = fusion(helper);
        helper.assertTrue(r.inventory().getContainerSize() == 0, "no item slots");
        helper.assertTrue(r.definition().cells(r.controllerPos(), r.controllerFacing()).stream().noneMatch(c -> c.key() == 'I'), "no Item Port");
        helper.succeed();
    }

    /** With a few mB of gas the plasma burns them and goes out, and needs a fresh charge to light again. */
    private static void fusionRunsDry(GameTestHelper helper) {
        FusionReactorBlockEntity r = fusion(helper);
        fuelGas(r, 2);
        nitrogen(r, 1_000);
        charge(helper, r);
        helper.succeedWhen(() -> {
            helper.assertTrue(!r.lit() && r.energy().stored() > 0, "it ran, then the plasma went out, status " + r.status());
            helper.assertTrue(r.status() == MachineStatus.NO_FUEL, "status no fuel, " + r.status());
            helper.assertTrue(r.ignition().stored() == 0, "the charge was spent on the first ignition");
        });
    }

    // --- Singularity Reactor --------------------------------------------------------------------------------------------------
    private static SingularityReactorBlockEntity singularity(GameTestHelper helper) {
        BlockPos rel = new BlockPos(5, 1, 1);
        build(helper, rel, GBMachines.SINGULARITY_REACTOR.block().get(), GBMultiblocks.SINGULARITY_REACTOR);
        return helper.getBlockEntity(rel, SingularityReactorBlockEntity.class);
    }

    private static void engage(GameTestHelper helper, SingularityReactorBlockEntity s) {
        s.inventory().setItem(SingularityReactorBlockEntity.SEED_SLOT, new ItemStack(GBNuclear.SINGULARITY_SEED.get()));
        s.energy().generate(SingularityReactorBlockEntity.ENGAGE);
    }

    /** The seed stays in its slot; with a full feed the core eats 20 items a second for 10 000 FE each: 10 000 FE a tick. */
    private static void singularityRuns(GameTestHelper helper) {
        SingularityReactorBlockEntity s = singularity(helper);
        helper.assertTrue(s.isFormed(), "the cage forms");
        s.inventory().setItem(SingularityReactorBlockEntity.SEED_SLOT, new ItemStack(GBNuclear.SINGULARITY_SEED.get()));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!s.engaged() && s.status() == MachineStatus.CHARGING, "without the charge the seed waits, " + s.status());
            s.energy().generate(SingularityReactorBlockEntity.ENGAGE);
            for (int slot = 0; slot < SingularityReactorBlockEntity.FEED_SLOTS; slot++) {
                s.inventory().setItem(SingularityReactorBlockEntity.FEED_START + slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(s.engaged(), "the cage holds the seed");
                helper.assertTrue(s.inventory().getItem(SingularityReactorBlockEntity.SEED_SLOT).is(GBNuclear.SINGULARITY_SEED.get()),
                        "the seed stays in its slot");
                int rate = s.feedRate();
                helper.assertTrue(rate == SingularityReactorBlockEntity.MAX_ITEMS_PER_SECOND, "it eats 20 items a second, ate " + rate);
                helper.assertTrue(s.status() == MachineStatus.RUNNING, "running, " + s.status());
                helper.assertTrue((long) rate * SingularityReactorBlockEntity.FE_PER_ITEM / 20 == SingularityReactorBlockEntity.MAX_OUTPUT,
                        "20 items a second at 10 000 FE each is the 10 000 FE/t ceiling");
                // The port only gives power above the reserve.
                BlockPos port = cellOf(s.definition(), s.controllerPos(), 'E');
                var handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, port, Direction.EAST);
                try (Transaction tx = Transaction.openRoot()) {
                    int out = handler.extract(Integer.MAX_VALUE, tx);
                    helper.assertTrue(out <= s.energy().stored() - SingularityReactorBlockEntity.RESERVE, "the reserve stays, took " + out);
                }
                helper.succeed();
            });
        });
    }

    /** A held core with the buffer run dry collapses: the core is gone. */
    private static void singularityStarves(GameTestHelper helper) {
        SingularityReactorBlockEntity s = singularity(helper);
        engage(helper, s);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(s.engaged(), "engaged");
            s.energy().consume(s.energy().stored() - 500);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!s.engaged(), "out of power, the singularity collapses");
                helper.assertTrue(s.inventory().getItem(SingularityReactorBlockEntity.SEED_SLOT).isEmpty(), "the seed is destroyed");
                helper.assertTrue(s.status() == MachineStatus.NO_CORE, "no seed left, " + s.status());
                helper.succeed();
            });
        });
    }

    private static void singularityBroken(GameTestHelper helper) {
        SingularityReactorBlockEntity s = singularity(helper);
        engage(helper, s);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(s.engaged(), "engaged");
            helper.getLevel().destroyBlock(cellOf(s.definition(), s.controllerPos(), 'Q'), false);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!s.isFormed() && !s.engaged(), "breaking an emitter collapses the core");
                helper.assertTrue(s.inventory().getItem(SingularityReactorBlockEntity.SEED_SLOT).isEmpty(), "and destroys the seed");
                helper.succeed();
            });
        });
    }

    /** Taking the seed out is the safe way to shut it down: the cage lets go and nothing collapses. */
    private static void singularitySeedOut(GameTestHelper helper) {
        SingularityReactorBlockEntity s = singularity(helper);
        engage(helper, s);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(s.engaged(), "engaged");
            ItemStack seed = s.inventory().removeItem(SingularityReactorBlockEntity.SEED_SLOT, 1);
            helper.assertTrue(seed.is(GBNuclear.SINGULARITY_SEED.get()), "the seed comes out");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!s.engaged() && s.energy().stored() > 0, "the cage let go without collapsing");
                helper.assertTrue(s.status() == MachineStatus.NO_CORE, "waiting for a seed, " + s.status());
                helper.succeed();
            });
        });
    }

    /** A floor block swapped for an Item Port still forms the cage, and the extra port feeds it. */
    private static void singularityExtraPorts(GameTestHelper helper) {
        BlockPos rel = new BlockPos(5, 1, 1);
        helper.setBlock(rel, GBMachines.SINGULARITY_REACTOR.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        BlockPos extra = null;
        for (MultiblockDefinition.Cell cell : GBMultiblocks.SINGULARITY_REACTOR.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() == MultiblockDefinition.CONTROLLER || cell.key() == '.') {
                continue;
            }
            boolean swap = cell.key() == 'B' && cell.col() == 0 && cell.row() == 0;
            helper.getLevel().setBlockAndUpdate(cell.pos(), swap ? GBMultiblocks.ITEM_PORT.block().get().defaultBlockState()
                    : GBMultiblocks.SINGULARITY_REACTOR.legend().get(cell.key()).preview().get());
            if (swap) {
                extra = cell.pos();
            }
        }
        SingularityReactorBlockEntity s = helper.getBlockEntity(rel, SingularityReactorBlockEntity.class);
        helper.assertTrue(s.isFormed(), "an Item Port in the floor still forms the cage");
        var items = helper.getLevel().getCapability(Capabilities.Item.BLOCK, extra, Direction.NORTH);
        helper.assertTrue(items != null, "the extra port is connected");
        try (Transaction tx = Transaction.openRoot()) {
            int in = items.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.OAK_LOG), 16, tx);
            helper.assertTrue(in == 16, "it takes matter, took " + in);
            tx.commit();
        }
        helper.assertTrue(s.inventory().getItem(SingularityReactorBlockEntity.FEED_START).is(Items.OAK_LOG), "into a feed slot");
        helper.succeed();
    }
}
