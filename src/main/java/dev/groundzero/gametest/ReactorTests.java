package dev.groundzero.gametest;

import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.FissionReactorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.machine.ParticleAcceleratorBlockEntity;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.power.RtgBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Phase 4 slice 7: the Fission Reactor (forming, power, coolant and heat balance, SCRAM, heavy water, breeding, ports)
 * and the Particle Accelerator.
 */
final class ReactorTests {
    private ReactorTests() {}

    static void register() {
        GZGameTests.test("reactor_forms_and_unforms", GZGameTests.FLOOR_16, 20, ReactorTests::reactorForms);
        GZGameTests.test("reactor_makes_power_and_uses_coolant", GZGameTests.FLOOR_16, 60, ReactorTests::reactorRuns);
        GZGameTests.test("reactor_scrams_without_coolant", GZGameTests.FLOOR_16, 300, ReactorTests::reactorScrams);
        GZGameTests.test("reactor_water_overheats_at_full_power", GZGameTests.FLOOR_16, 400, ReactorTests::waterOverheats);
        GZGameTests.test("reactor_heavy_water_holds_full_power", GZGameTests.FLOOR_16, 400, ReactorTests::heavyWaterHolds);
        GZGameTests.test("reactor_connects_only_through_ports", GZGameTests.FLOOR_16, 20, ReactorTests::portsOnly);
        GZGameTests.test("reactor_breeds_target_rods", GZGameTests.FLOOR_16, 40, ReactorTests::reactorBreeds);
        GZGameTests.test("reactor_energy_port_pushes_power", GZGameTests.FLOOR_16, 60, ReactorTests::reactorEnergyPort);
        GZGameTests.test("accelerator_forms_and_runs", GZGameTests.FLOOR_16, 60, ReactorTests::accelerator);
        GZGameTests.test("rtg_burns_plutonium", GZGameTests.FLOOR_16, 20, ReactorTests::rtgPlutonium);
    }

    /** Builds a structure by hand around a controller placed at {@code rel}. */
    private static void build(GameTestHelper helper, BlockPos rel, Direction facing, Block controller, MultiblockDefinition def) {
        helper.setBlock(rel, controller.defaultBlockState().setValue(MachineBlock.FACING, facing));
        BlockPos abs = helper.absolutePos(rel);
        for (MultiblockDefinition.Cell cell : def.cells(abs, facing)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
    }

    private static FissionReactorBlockEntity reactor(GameTestHelper helper, BlockPos rel) {
        build(helper, rel, Direction.NORTH, GZMachines.FISSION_REACTOR.block().get(), GZMultiblocks.FISSION_REACTOR);
        return helper.getBlockEntity(rel, FissionReactorBlockEntity.class);
    }

    private static void fuel(FissionReactorBlockEntity r, int rods) {
        for (int i = 0; i < rods; i++) {
            r.inventory().setItem(i, new ItemStack(GZNuclear.URANIUM_FUEL_ROD.get()));
        }
    }

    private static void withdrawAll(FissionReactorBlockEntity r) {
        for (int i = 0; i < 10; i++) {
            r.handleButton(FissionReactorBlockEntity.BUTTON_WITHDRAW);
        }
    }

    private static void reactorForms(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        helper.assertTrue(r.isFormed(), "the reactor forms");
        helper.getLevel().destroyBlock(r.controllerPos().above(), false);    // the front window
        helper.assertTrue(!r.isFormed(), "breaking a window un-forms it");
        helper.succeed();
    }

    /** Four rods, rods fully out, water in: 1 200 FE/t, water drawn, the core warms but stays well below SCRAM. */
    private static void reactorRuns(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        helper.assertTrue(r.insertion() == 0 && Math.abs(r.powerLevel() - 1f) < 1e-4f, "full power with four rods and the rods out");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(r.energy().stored() >= 30 * FissionReactorBlockEntity.MAX_OUTPUT, "about 1 200 FE a tick, have " + r.energy().stored());
            helper.assertTrue(r.tank(0).amount() < 16_000, "coolant is used");
            helper.assertTrue(r.heat() > FissionReactorBlockEntity.AMBIENT && r.heat() < FissionReactorBlockEntity.WARN_HEAT, "the core warms, " + r.heat());
            helper.assertTrue(r.status() == MachineStatus.RUNNING, "running, status " + r.status());
            // Half the rods back in halves the power.
            for (int i = 0; i < 5; i++) {
                r.handleButton(FissionReactorBlockEntity.BUTTON_INSERT);
            }
            helper.assertTrue(Math.abs(r.powerLevel() - 0.5f) < 1e-4f, "rods half in, half power");
            helper.succeed();
        });
    }

    /** No water: the core heats until it SCRAMs and drops every rod in. */
    private static void reactorScrams(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        fuel(r, 4);
        withdrawAll(r);
        helper.succeedWhen(() -> {
            helper.assertTrue(r.scrammed(), "the reactor SCRAMs, heat " + r.heat());
            helper.assertTrue(r.insertion() == 100 && r.powerLevel() == 0, "every rod is in");
            helper.assertTrue(r.status() == MachineStatus.SCRAM, "status SCRAM");
        });
    }

    /** Water coolant at full power: the core heads past 900 C and SCRAMs, with water still in the tank. */
    private static void waterOverheats(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        helper.assertTrue(r.equilibrium(1f) > FissionReactorBlockEntity.SCRAM_HEAT, "full power on water heads past SCRAM");
        helper.assertTrue(r.equilibrium(0.7f) < FissionReactorBlockEntity.SCRAM_HEAT, "70% on water is safe");
        helper.succeedWhen(() -> {
            helper.assertTrue(r.scrammed(), "the reactor SCRAMs, heat " + r.heat());
            helper.assertTrue(r.tank(0).amount() > 0, "it was not for lack of water");
        });
    }

    /** Heavy water at full power: the core settles below SCRAM and uses a fifth of the coolant. */
    private static void heavyWaterHolds(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(GZFluids.HEAVY_WATER.source().get(), 16_000));
        withdrawAll(r);
        helper.assertTrue(r.equilibrium(1f) < FissionReactorBlockEntity.SCRAM_HEAT - 100, "full power on heavy water stays well below SCRAM");
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(!r.scrammed() && r.status() != MachineStatus.SCRAM, "no SCRAM, heat " + r.heat());
            helper.assertTrue(r.heat() > 600 && r.heat() < FissionReactorBlockEntity.SCRAM_HEAT, "the core runs hot but holds, " + r.heat());
            int used = 16_000 - r.tank(0).amount();
            int water = 300 * FissionReactorBlockEntity.WATER_AT_FULL_POWER;
            helper.assertTrue(used > 0 && used <= water / FissionReactorBlockEntity.HEAVY_WATER_SAVING + 10, "a fifth of the water rate, used " + used);
            helper.succeed();
        });
    }

    /** The controller exposes nothing; coolant goes in through the Fluid Port, items through the Item Ports only. */
    private static void portsOnly(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        var level = helper.getLevel();
        BlockPos ctrl = r.controllerPos();
        for (Direction d : Direction.values()) {
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, ctrl, d) == null
                    && level.getCapability(Capabilities.Fluid.BLOCK, ctrl, d) == null
                    && level.getCapability(Capabilities.Energy.BLOCK, ctrl, d) == null, "the controller exposes nothing on " + d);
        }
        BlockPos coolant = cell(r, 'W');
        helper.assertTrue(level.getBlockState(coolant).is(GZMultiblocks.FLUID_PORT.block().get()), "the coolant port is a Fluid Port");
        var fluids = level.getCapability(Capabilities.Fluid.BLOCK, coolant, Direction.NORTH);
        helper.assertTrue(fluids != null && level.getCapability(Capabilities.Item.BLOCK, coolant, Direction.NORTH) == null,
                "the Fluid Port carries fluids, not items");
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(fluids.insert(0, FluidResource.of(GZFluids.HEAVY_WATER.source().get()), 1_000, tx) == 1_000, "heavy water goes in");
            tx.commit();
        }
        helper.assertTrue(r.tank(0).amount() == 1_000, "the coolant reaches the controller");
        BlockPos fuelPort = cell(r, 'U');
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, fuelPort, Direction.UP) != null
                && level.getCapability(Capabilities.Fluid.BLOCK, fuelPort, Direction.UP) == null, "the Item Port carries items, not fluids");
        helper.succeed();
    }

    private static BlockPos cell(FissionReactorBlockEntity r, char key) {
        return r.definition().cells(r.controllerPos(), r.controllerFacing()).stream().filter(c -> c.key() == key).findFirst().orElseThrow().pos();
    }

    /** A DU target rod nearly done becomes an irradiated rod in an output slot. */
    private static void reactorBreeds(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        r.inventory().setItem(FissionReactorBlockEntity.TARGET_START, new ItemStack(GZNuclear.DU_TARGET_ROD.get()));
        r.setBreedProgress(0, FissionReactorBlockEntity.BREED_TIME - 5);
        withdrawAll(r);
        helper.succeedWhen(() -> {
            boolean out = r.inventory().getItem(FissionReactorBlockEntity.IRRADIATED_START).is(GZNuclear.IRRADIATED_DU_TARGET_ROD.get());
            helper.assertTrue(out, "an irradiated DU target rod comes out");
            helper.assertTrue(r.inventory().getItem(FissionReactorBlockEntity.TARGET_START).isEmpty(), "the target rod is used");
        });
    }

    /** Power leaves through the Energy Port on top into a machine sitting on it. */
    private static void reactorEnergyPort(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        BlockPos port = r.definition().cells(r.controllerPos(), r.controllerFacing()).stream()
                .filter(c -> c.key() == 'E').findFirst().orElseThrow().pos();
        helper.getLevel().setBlockAndUpdate(port.above(), GZMachines.ORE_CRUSHER.block().get().defaultBlockState());
        OreCrusherBlockEntity crusher = (OreCrusherBlockEntity) helper.getLevel().getBlockEntity(port.above());
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        helper.succeedWhen(() -> helper.assertTrue(crusher.energy().stored() > 0, "the machine on the Energy Port gets power"));
    }

    /** The ring forms, takes a containment cell and starts filling it at 2 000 FE/t. */
    private static void accelerator(GameTestHelper helper) {
        BlockPos rel = new BlockPos(4, 2, 2);
        build(helper, rel, Direction.NORTH, GZMachines.PARTICLE_ACCELERATOR.block().get(), GZMultiblocks.PARTICLE_ACCELERATOR);
        ParticleAcceleratorBlockEntity pa = helper.getBlockEntity(rel, ParticleAcceleratorBlockEntity.class);
        helper.assertTrue(pa.isFormed(), "the accelerator ring forms");
        var level = helper.getLevel();
        // Every part of the octagon, staircase corners included, belongs to the controller (right-clicking it opens it).
        for (BlockPos part : pa.definition().partPositions(pa.controllerPos(), pa.controllerFacing())) {
            helper.assertTrue(dev.groundzero.content.multiblock.Multiblocks.formedControllerOf(level, part) == pa,
                    "the part at " + part + " leads to the controller");
        }
        helper.assertTrue(pa.definition().partPositions(pa.controllerPos(), pa.controllerFacing()).size() == 63, "63 parts and the controller");
        for (Direction d : Direction.values()) {
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, pa.controllerPos(), d) == null
                    && level.getCapability(Capabilities.Energy.BLOCK, pa.controllerPos(), d) == null, "the controller exposes nothing on " + d);
        }
        // A containment cell goes in through the ring's Item Port.
        BlockPos itemPort = pa.definition().cells(pa.controllerPos(), pa.controllerFacing()).stream()
                .filter(c -> c.key() == 'I').findFirst().orElseThrow().pos();
        var items = level.getCapability(Capabilities.Item.BLOCK, itemPort, Direction.EAST);
        helper.assertTrue(items != null, "the Item Port carries items");
        try (var tx = Transaction.openRoot()) {
            int in = 0;
            for (int i = 0; i < items.size() && in == 0; i++) {
                in += items.insert(i, net.neoforged.neoforge.transfer.item.ItemResource.of(GZNuclear.CONTAINMENT_CELL.get()), 1, tx);
            }
            helper.assertTrue(in == 1, "a containment cell goes in");
            tx.commit();
        }
        helper.assertTrue(pa.inventory().getItem(0).is(GZNuclear.CONTAINMENT_CELL.get()), "it reaches the controller's input slot");
        helper.onEachTick(() -> pa.energy().generate(ParticleAcceleratorBlockEntity.ENERGY_PER_TICK));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(pa.status() == MachineStatus.RUNNING, "running, status " + pa.status());
            int progress = pa.data().get(dev.groundzero.content.machine.MachineBlockEntity.DATA_BASE_COUNT);
            helper.assertTrue(progress > 10, "making progress, " + progress);
            helper.succeed();
        });
    }

    private static void rtgPlutonium(GameTestHelper helper) {
        helper.assertTrue(RtgBlockEntity.fuelTicks(new ItemStack(GZNuclear.PLUTONIUM_INGOT.get())) == 240_000, "plutonium lasts ten days");
        helper.succeed();
    }
}
