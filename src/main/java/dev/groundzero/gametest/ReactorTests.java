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
import dev.groundzero.content.gear.GZArmor;
import dev.groundzero.content.nuclear.Contamination;
import dev.groundzero.content.nuclear.GZEffects;
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
 * and the Particle Accelerator; Phase 5: the meltdown and contamination. After 9b: the reactor at any size from 3x3x3
 * to 6x6x6, its channel columns, every rod burning at once with its own lifetime, and the GUI's core map.
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
        GZGameTests.test("reactor_melts_down_when_allowed", GZGameTests.FLOOR_16, 200, ReactorTests::meltdown);
        GZGameTests.test("reactor_critical_core_saved_by_scram", GZGameTests.FLOOR_16, 100, ReactorTests::criticalSaved);
        GZGameTests.test("contamination_sickens_the_living", GZGameTests.FLOOR_16, 60, ReactorTests::contamination);
        GZGameTests.test("reactor_critical_with_coolant_still_melts_down", GZGameTests.FLOOR_16, 300, ReactorTests::cooledMeltdown);
        GZGameTests.test("hazmat_suits_stop_radiation", GZGameTests.FLOOR_16, 20, ReactorTests::hazmat);
        GZGameTests.test("reactor_forms_at_any_size_to_six", GZGameTests.FLOOR_16, 20, ReactorTests::reactorSizes);
        GZGameTests.test("reactor_channels_run_floor_to_roof", GZGameTests.FLOOR_16, 20, ReactorTests::channelColumns);
        GZGameTests.test("reactor_burns_every_rod_at_once", GZGameTests.FLOOR_16, 120, ReactorTests::everyRodBurns);
        GZGameTests.test("reactor_gui_has_a_slot_per_channel", GZGameTests.FLOOR_16, 20, ReactorTests::guiSlots);
        GZGameTests.test("reactor_big_core_makes_more_power", GZGameTests.FLOOR_16, 60, ReactorTests::bigCore);
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

    /** Loads {@code rods} fresh fuel rods into the first channels on the core map. */
    private static void fuel(FissionReactorBlockEntity r, int rods) {
        var slots = r.channelSlots();
        for (int i = 0; i < rods; i++) {
            r.inventory().setItem(slots.get(i), new ItemStack(GZNuclear.URANIUM_FUEL_ROD.get()));
        }
    }

    /**
     * Builds a {@code w} x {@code h} x {@code d} reactor by hand round a controller at {@code rel} facing north, the
     * controller second from the west in the bottom of the north wall (as the example): casing walls with an Energy Port
     * in the middle of the roof, and in the core a Fuel Channel column wherever {@code channel} says (core column x
     * from the west, z from the north).
     */
    private static FissionReactorBlockEntity box(GameTestHelper helper, BlockPos rel, int w, int h, int d,
            java.util.function.BiPredicate<Integer, Integer> channel) {
        helper.setBlock(rel, GZMachines.FISSION_REACTOR.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        BlockPos corner = helper.absolutePos(rel).west();
        var level = helper.getLevel();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    BlockPos pos = corner.offset(x, y, z);
                    if (pos.equals(helper.absolutePos(rel))) {
                        continue;
                    }
                    boolean wall = x == 0 || x == w - 1 || y == 0 || y == h - 1 || z == 0 || z == d - 1;
                    var state = wall ? (y == h - 1 && x == w / 2 && z == d / 2 ? GZMultiblocks.ENERGY_PORT : GZMultiblocks.LEAD_LINED_CASING).block().get()
                            .defaultBlockState()
                            : channel.test(x - 1, z - 1) ? GZMultiblocks.FUEL_CHANNEL.block().get().defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                    level.setBlockAndUpdate(pos, state);
                }
            }
        }
        FissionReactorBlockEntity r = helper.getBlockEntity(rel, FissionReactorBlockEntity.class);
        r.revalidate();
        return r;
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
        helper.assertTrue(r.channelSlots().size() == 4, "the example has four channels, " + r.channelSlots());
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        helper.assertTrue(r.insertion() == 0 && Math.abs(r.powerLevel() - 1f) < 1e-4f, "full power with four rods and the rods out");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(r.energy().stored() >= 30 * 4 * FissionReactorBlockEntity.ROD_OUTPUT, "about 1 200 FE a tick, have " + r.energy().stored());
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

    /** Meltdowns off: no water, the core heats until it SCRAMs and drops every rod in. */
    private static void reactorScrams(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        r.setMeltdownsForTest(false);
        fuel(r, 4);
        withdrawAll(r);
        helper.succeedWhen(() -> {
            helper.assertTrue(r.scrammed(), "the reactor SCRAMs, heat " + r.heat());
            helper.assertTrue(r.insertion() == 100 && r.powerLevel() == 0, "every rod is in");
            helper.assertTrue(r.status() == MachineStatus.SCRAM, "status SCRAM");
        });
    }

    /** Meltdowns off, water coolant at full power: the core heads past 900 C and SCRAMs, with water still in the tank. */
    private static void waterOverheats(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        r.setMeltdownsForTest(false);
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
            int water = Math.round(300 * 4 * FissionReactorBlockEntity.WATER_PER_ROD);
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
        // The staircase corners are cut to the octagon's slope: the front-left outer stair (pattern column 2, row 0)
        // loses its north-west corner, on both layers.
        for (MultiblockDefinition.Cell cell : pa.definition().cells(pa.controllerPos(), pa.controllerFacing())) {
            if (cell.col() == 2 && cell.row() == 0) {
                var state = level.getBlockState(cell.pos());
                helper.assertTrue(state.getValue(dev.groundzero.content.multiblock.MultiblockPartBlock.CUT) == dev.groundzero.content.multiblock.PartCut.NORTH_WEST,
                        "the outer stair is cut north-west, got " + state);
                var shape = state.getShape(level, cell.pos());
                helper.assertTrue(shape.toAabbs().stream().noneMatch(b -> b.contains(0.05, 0.5, 0.05)), "its north-west corner is open");
                helper.assertTrue(shape.toAabbs().stream().anyMatch(b -> b.contains(0.95, 0.5, 0.95)), "its south-east corner is solid");
            }
        }
        helper.assertTrue(dev.groundzero.content.multiblock.PartCut.NORTH_WEST.rotated(Direction.EAST) == dev.groundzero.content.multiblock.PartCut.NORTH_EAST
                && dev.groundzero.content.multiblock.PartCut.NORTH_WEST.rotated(Direction.SOUTH) == dev.groundzero.content.multiblock.PartCut.SOUTH_EAST,
                "cuts turn with the structure");
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

    /**
     * Meltdowns on, no coolant, rods out, the core already near the end: it goes critical (no SCRAM), then melts down.
     * The reactor is gone, corium lies in the test area and a contaminated zone is left.
     */
    private static void meltdown(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(7, 1, 6));
        BlockPos ctrl = r.controllerPos();
        helper.assertTrue(dev.groundzero.config.GZServerConfig.REACTOR_MELTDOWNS.getDefault(), "meltdowns are on by default");
        r.setMeltdownsForTest(true);                       // whatever this run's config file says
        fuel(r, 4);
        withdrawAll(r);
        r.setHeatForTest(FissionReactorBlockEntity.MELTDOWN_HEAT - 120);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(r.critical() && r.status() == MachineStatus.CRITICAL && !r.scrammed(), "critical, not SCRAMmed: " + r.status());
            helper.succeedWhen(() -> {
                var level = helper.getLevel();
                helper.assertTrue(!(level.getBlockEntity(ctrl) instanceof FissionReactorBlockEntity), "the reactor is gone");
                int corium = 0;
                for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(15, 8, 15)))) {
                    if (level.getBlockState(pos).is(GZNuclear.CORIUM.block().get())) {
                        corium++;
                    }
                }
                helper.assertTrue(corium > 0, "corium is left");
                helper.assertTrue(Contamination.of(level).levelAt(level, net.minecraft.world.phys.Vec3.atCenterOf(ctrl)) > 0.5f,
                        "the land round it is contaminated");
            });
        });
    }

    /** Meltdowns on, critical: SCRAM drops the rods in and the core cools instead of melting down. */
    private static void criticalSaved(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        r.setMeltdownsForTest(true);
        fuel(r, 4);
        withdrawAll(r);
        r.setHeatForTest(1_000);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(r.critical(), "critical at 1 000 C");
            r.handleButton(FissionReactorBlockEntity.BUTTON_SCRAM);
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(!r.critical() && r.powerLevel() == 0, "SCRAM ends it");
                helper.assertTrue(r.heat() < 1_000, "the core cools, " + r.heat());
                helper.assertTrue(helper.getLevel().getBlockEntity(r.controllerPos()) == r, "and the reactor is still there");
                helper.succeed();
            });
        });
    }

    /** A contaminated zone gives a pig radiation sickness, strongest near the centre; a zombie is immune. */
    private static void contamination(GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.world.phys.Vec3 centre = helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 2, 8));
        Contamination.of(level).add(new Contamination.Zone(centre, 10, 1f, level.getGameTime(), 200));
        var pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(8, 2, 9));
        var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(9, 2, 8));
        helper.succeedWhen(() -> {
            var sick = pig.getEffect(GZEffects.RADIATION_SICKNESS);
            helper.assertTrue(sick != null && sick.getAmplifier() >= 2, "the pig is badly sick near the centre");
            helper.assertTrue(!zombie.hasEffect(GZEffects.RADIATION_SICKNESS), "the undead are immune");
        });
    }

    /** Critical with water still flowing: coolant only slows the runaway, it cannot hold the core; it melts down. */
    private static void cooledMeltdown(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(7, 1, 6));
        BlockPos ctrl = r.controllerPos();
        r.setMeltdownsForTest(true);
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        r.setHeatForTest(FissionReactorBlockEntity.MELTDOWN_HEAT - 60);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(r.critical() && r.tank(0).amount() > 0, "critical, with water in the tank");
            helper.succeedWhen(() -> helper.assertTrue(!(helper.getLevel().getBlockEntity(ctrl) instanceof FissionReactorBlockEntity),
                    "the cooled core still melts down"));
        });
    }

    /**
     * Any box from 3x3x3 to 6x6x6 forms; its channel count, energy buffer and coolant tank follow its core. A 7-wide box
     * does not, nor one with no channels.
     */
    private static void reactorSizes(GameTestHelper helper) {
        FissionReactorBlockEntity small = box(helper, new BlockPos(1, 1, 1), 3, 3, 3, (x, z) -> true);
        helper.assertTrue(small.isFormed() && small.channelSlots().size() == 1, "a 3x3x3 reactor forms with one channel");
        helper.assertTrue(small.core().width() == 3 && small.core().height() == 3 && small.core().depth() == 3, "its size is found");
        helper.assertTrue(small.energy().capacity() == FissionReactorBlockEntity.ENERGY_PER_CHANNEL
                && small.tank(0).capacity() == FissionReactorBlockEntity.COOLANT_PER_CHANNEL, "buffers for one channel");
        FissionReactorBlockEntity big = box(helper, new BlockPos(6, 1, 1), 6, 6, 6, (x, z) -> true);
        helper.assertTrue(big.isFormed() && big.channelSlots().size() == 16, "a 6x6x6 reactor forms with sixteen channels, " + big.channelSlots());
        helper.assertTrue(big.energy().capacity() == 16 * FissionReactorBlockEntity.ENERGY_PER_CHANNEL
                && big.tank(0).capacity() == FissionReactorBlockEntity.COOLANT_MAX, "buffers for sixteen channels (the tank capped)");
        helper.assertTrue(big.thermalMass() == 2f, "a four-block core heats half as fast");
        // Part of the core left as coolant: only the channel columns get rod slots.
        FissionReactorBlockEntity mixed = box(helper, new BlockPos(1, 1, 8), 5, 4, 5, (x, z) -> (x + z) % 2 == 0);
        helper.assertTrue(mixed.isFormed() && mixed.channelSlots().size() == 5, "a checkerboard core: five channels, " + mixed.channelSlots());
        // Too wide: seven blocks.
        FissionReactorBlockEntity wide = box(helper, new BlockPos(8, 1, 9), 7, 3, 3, (x, z) -> true);
        helper.assertTrue(!wide.isFormed() && wide.problem(helper.getLevel()) != null, "a 7-wide box does not form");
        helper.succeed();
    }

    /** A channel column must run floor to roof: a gap un-forms the reactor, and the ghost preview asks for the missing block. */
    private static void channelColumns(GameTestHelper helper) {
        FissionReactorBlockEntity r = box(helper, new BlockPos(3, 1, 3), 4, 5, 4, (x, z) -> true);
        helper.assertTrue(r.isFormed() && r.channelSlots().size() == 4, "a 4x5x4 reactor with four channel columns forms");
        var level = helper.getLevel();
        BlockPos gap = r.core().column(r.channelSlots().get(0)).get(1);
        level.setBlockAndUpdate(gap, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(!r.isFormed(), "a gap in a column un-forms it");
        var missing = r.mismatches(level);
        helper.assertTrue(missing.size() == 1 && missing.get(0).pos().equals(gap) && missing.get(0).empty(),
                "the preview asks for the missing channel, " + missing);
        helper.assertTrue(r.ghostState(missing.get(0)).is(GZMultiblocks.FUEL_CHANNEL.block().get()), "shown as a Fuel Channel");
        // Emptying the column instead makes it a coolant column: the reactor forms again with three channels.
        for (int dy = -4; dy <= 4; dy++) {
            if (level.getBlockState(gap.above(dy)).is(GZMultiblocks.FUEL_CHANNEL.block().get())) {
                level.setBlockAndUpdate(gap.above(dy), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        r.revalidate();
        helper.assertTrue(r.isFormed() && r.channelSlots().size() == 3, "an empty column is coolant: three channels, " + r.channelSlots());
        // Glass on an edge is wrong.
        BlockPos edge = r.core().pos(r.core().col0(), 1, 0);
        level.setBlockAndUpdate(edge, GZMultiblocks.REACTOR_GLASS.block().get().defaultBlockState());
        helper.assertTrue(!r.isFormed() && r.mismatches(level).stream().anyMatch(m -> m.pos().equals(edge) && !m.empty()),
                "glass on an edge is outlined as wrong");
        helper.succeed();
    }

    /**
     * Every loaded rod burns at once (each takes burnup every tick), each rod's lifetime is its own (within 15% of the
     * average), and a rod at the end of its life comes out spent while the others keep burning.
     */
    private static void everyRodBurns(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        fuel(r, 4);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        var slots = r.channelSlots();
        helper.runAfterDelay(41, () -> {
            r.burnForTest();
            java.util.Set<Integer> lives = new java.util.HashSet<>();
            for (int slot : slots) {
                var burnup = r.inventory().getItem(slot).get(dev.groundzero.registry.GZDataComponents.FUEL_BURNUP.get());
                helper.assertTrue(burnup != null && burnup.used() >= 38, "rod in slot " + slot + " has burned, " + burnup);
                int life = burnup.life();
                int avg = FissionReactorBlockEntity.ROD_LIFE;
                helper.assertTrue(Math.abs(life - avg) <= avg * FissionReactorBlockEntity.ROD_LIFE_SPREAD + 1, "its lifetime is near the average, " + life);
                lives.add(life);
            }
            helper.assertTrue(lives.size() > 1, "each rod has its own lifetime, " + lives);
            // One rod near its end: it comes out spent; the others go on.
            int first = slots.get(0);
            ItemStack worn = r.inventory().getItem(first).copy();
            var b = worn.get(dev.groundzero.registry.GZDataComponents.FUEL_BURNUP.get());
            worn.set(dev.groundzero.registry.GZDataComponents.FUEL_BURNUP.get(), new dev.groundzero.content.nuclear.FuelBurnup(b.life() - 5, b.life()));
            r.inventory().setItem(first, worn);
            helper.runAfterDelay(25, () -> {
                r.burnForTest();
                helper.assertTrue(r.inventory().getItem(first).isEmpty(), "the used-up rod has left its channel");
                helper.assertTrue(r.inventory().getItem(FissionReactorBlockEntity.SPENT_SLOT).is(GZNuclear.SPENT_FUEL_ROD.get()), "as a spent fuel rod");
                helper.assertTrue(r.loadedRods() == 3, "the other three still burn, " + r.loadedRods());
                helper.succeed();
            });
        });
    }

    /** The menu offers a rod slot on each channel only (one rod each), and on a cell still holding a rod after a rebuild. */
    private static void guiSlots(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = (dev.groundzero.content.machine.MachineMenu) r.createMenu(1, player.getInventory(), player);
        var channels = r.channelSlots();
        for (int slot = 0; slot < FissionReactorBlockEntity.CHANNELS; slot++) {
            helper.assertTrue(menu.slotShown(slot) == channels.contains(slot), "slot " + slot + " shown only on a channel");
        }
        player.getInventory().setItem(0, new ItemStack(GZNuclear.URANIUM_FUEL_ROD.get(), 8));
        int hotbar0 = menu.layout().slots().size() + 27;
        menu.clicked(hotbar0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);    // a shift-click, as the game does it
        for (int slot : channels) {
            helper.assertTrue(r.inventory().getItem(slot).getCount() == 1, "one rod in each channel, slot " + slot);
        }
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 4, "the other four stay in hand");
        // Not a channel any more (the reactor broken): the rods stay shown, to be taken out, but nothing more goes in.
        helper.getLevel().destroyBlock(r.controllerPos().above(), false);
        helper.assertTrue(!r.isFormed(), "broken");
        for (int slot : channels) {
            helper.assertTrue(menu.slotShown(slot), "the rod in slot " + slot + " can still be taken out");
        }
        helper.assertTrue(!r.inventory().canPlaceItem(channels.get(0), new ItemStack(GZNuclear.URANIUM_FUEL_ROD.get())), "no rods go in now");
        helper.succeed();
    }

    /** Sixteen rods in a 6x6x6 reactor: 4 800 FE/t, four times the coolant of four rods. */
    private static void bigCore(GameTestHelper helper) {
        FissionReactorBlockEntity r = box(helper, new BlockPos(5, 1, 3), 6, 6, 6, (x, z) -> true);
        fuel(r, 16);
        r.tank(0).fillInternal(new FluidStack(GZFluids.HEAVY_WATER.source().get(), 32_000));
        withdrawAll(r);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(r.status() == MachineStatus.RUNNING, "running, " + r.status());
            int made = r.energy().stored();
            helper.assertTrue(made >= 18 * FissionReactorBlockEntity.MAX_OUTPUT, "about 4 800 FE a tick, have " + made);
            helper.assertTrue(r.data().get(dev.groundzero.content.machine.MachineBlockEntity.DATA_BASE_COUNT + FissionReactorBlockEntity.DATA_POWER)
                    == FissionReactorBlockEntity.MAX_OUTPUT, "the GUI shows 4 800 FE/t");
            helper.succeed();
        });
    }

    /** Suit protection: none, one piece, a full Hazmat Suit, a full lead-lined one. */
    private static void hazmat(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        helper.assertTrue(GZArmor.radiationProtection(player) == 0, "no suit, no protection");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(GZArmor.HAZMAT_HOOD.get()));
        helper.assertTrue(Math.abs(GZArmor.radiationProtection(player) - 0.15f) < 1e-4f, "a hood alone: a fifth of 75%");
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            player.setItemSlot(slots[i], new ItemStack(GZArmor.HAZMAT_SET.get(i).get()));
        }
        helper.assertTrue(Math.abs(GZArmor.radiationProtection(player) - 0.75f) < 1e-4f, "a full Hazmat Suit: 75%");
        for (int i = 0; i < 4; i++) {
            player.setItemSlot(slots[i], new ItemStack(GZArmor.LEAD_HAZMAT_SET.get(i).get()));
        }
        helper.assertTrue(Math.abs(GZArmor.radiationProtection(player) - 0.92f) < 1e-4f, "a full lead-lined suit: 92%");
        helper.succeed();
    }
}
