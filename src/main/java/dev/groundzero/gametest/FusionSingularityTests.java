package dev.groundzero.gametest;

import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.FusionReactorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.SingularityReactorBlockEntity;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.Multiblocks;
import dev.groundzero.content.nuclear.GZNuclear;
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
        GZGameTests.test("fusion_forms_and_unforms", GZGameTests.FLOOR_16, 20, FusionSingularityTests::fusionForms);
        GZGameTests.test("fusion_ignites_and_runs_on_gas", GZGameTests.FLOOR_16, 80, FusionSingularityTests::fusionRuns);
        GZGameTests.test("fusion_needs_nitrogen_to_light", GZGameTests.FLOOR_16, 40, FusionSingularityTests::fusionNeedsNitrogen);
        GZGameTests.test("fusion_burns_pellets", GZGameTests.FLOOR_16, 40, FusionSingularityTests::fusionPellets);
        GZGameTests.test("fusion_plasma_goes_out_without_fuel", GZGameTests.FLOOR_16, 60, FusionSingularityTests::fusionRunsDry);
        GZGameTests.test("singularity_engages_and_burns_matter", GZGameTests.FLOOR_16, 60, FusionSingularityTests::singularityRuns);
        GZGameTests.test("singularity_collapses_without_power", GZGameTests.FLOOR_16, 40, FusionSingularityTests::singularityStarves);
        GZGameTests.test("singularity_collapses_when_broken", GZGameTests.FLOOR_16, 40, FusionSingularityTests::singularityBroken);
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
        build(helper, rel, GZMachines.FUSION_REACTOR.block().get(), GZMultiblocks.FUSION_REACTOR);
        return helper.getBlockEntity(rel, FusionReactorBlockEntity.class);
    }

    private static void fuelGas(FusionReactorBlockEntity r, int mb) {
        r.tank(FusionReactorBlockEntity.TANK_DEUTERIUM).fillInternal(new FluidStack(GZFluids.DEUTERIUM.sourceFluid(), mb));
        r.tank(FusionReactorBlockEntity.TANK_TRITIUM).fillInternal(new FluidStack(GZFluids.TRITIUM.sourceFluid(), mb));
    }

    private static void nitrogen(FusionReactorBlockEntity r, int mb) {
        r.tank(FusionReactorBlockEntity.TANK_NITROGEN).fillInternal(new FluidStack(GZFluids.LIQUID_NITROGEN.sourceFluid(), mb));
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

    private static void fusionPellets(GameTestHelper helper) {
        FusionReactorBlockEntity r = fusion(helper);
        r.inventory().setItem(0, new ItemStack(GZNuclear.LITHIUM_DEUTERIDE_PELLET.get(), 2));
        nitrogen(r, 1_000);
        charge(helper, r);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(r.lit(), "pellets light it too, status " + r.status());
            helper.assertTrue(r.inventory().getItem(0).getCount() == 1, "one pellet is burning");
            helper.succeed();
        });
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
        build(helper, rel, GZMachines.SINGULARITY_REACTOR.block().get(), GZMultiblocks.SINGULARITY_REACTOR);
        return helper.getBlockEntity(rel, SingularityReactorBlockEntity.class);
    }

    private static void engage(GameTestHelper helper, SingularityReactorBlockEntity s) {
        s.inventory().setItem(SingularityReactorBlockEntity.SEED_SLOT, new ItemStack(GZNuclear.SINGULARITY_SEED.get()));
        s.energy().generate(SingularityReactorBlockEntity.ENGAGE);
    }

    private static void singularityRuns(GameTestHelper helper) {
        SingularityReactorBlockEntity s = singularity(helper);
        helper.assertTrue(s.isFormed(), "the cage forms");
        s.inventory().setItem(SingularityReactorBlockEntity.SEED_SLOT, new ItemStack(GZNuclear.SINGULARITY_SEED.get()));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!s.holdsCore() && s.status() == MachineStatus.CHARGING, "without the charge the seed waits, " + s.status());
            s.energy().generate(SingularityReactorBlockEntity.ENGAGE);
            s.inventory().setItem(SingularityReactorBlockEntity.MATTER_SLOT, new ItemStack(Items.COBBLESTONE, 64));
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(s.holdsCore(), "the seed is drawn into the cage");
                helper.assertTrue(s.inventory().getItem(SingularityReactorBlockEntity.SEED_SLOT).isEmpty(), "the seed slot is empty");
                helper.assertTrue(s.inventory().getItem(SingularityReactorBlockEntity.MATTER_SLOT).getCount() < 64, "matter is fed in");
                helper.assertTrue(s.energy().stored() > SingularityReactorBlockEntity.ENGAGE, "matter becomes power, have " + s.energy().stored());
                helper.assertTrue(s.status() == MachineStatus.RUNNING, "running, " + s.status());
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
            helper.assertTrue(s.holdsCore(), "engaged");
            s.energy().consume(s.energy().stored() - 500);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!s.holdsCore(), "out of power, the singularity collapses");
                helper.assertTrue(s.status() == MachineStatus.NO_CORE, "no core left, " + s.status());
                helper.succeed();
            });
        });
    }

    private static void singularityBroken(GameTestHelper helper) {
        SingularityReactorBlockEntity s = singularity(helper);
        engage(helper, s);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(s.holdsCore(), "engaged");
            helper.getLevel().destroyBlock(cellOf(s.definition(), s.controllerPos(), 'Q'), false);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!s.isFormed() && !s.holdsCore(), "breaking an emitter collapses the core");
                helper.succeed();
            });
        });
    }
}
