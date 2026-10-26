package dev.groundzero.gametest;

import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.FissionCore;
import dev.groundzero.content.machine.FissionReactorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.machine.ParticleAcceleratorBlockEntity;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.gear.GZArmor;
import dev.groundzero.content.radiation.RadiationSource;
import dev.groundzero.content.radiation.RadiationSources;
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
 * to 6x6x6, a rod in every Fuel Channel wherever it stands in the core, one port of each kind, the plan the ghost
 * preview shows, every rod burning at once with its own lifetime, and the GUI's core map a layer at a time.
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
        GZGameTests.test("reactor_channels_stand_anywhere_in_the_core", GZGameTests.FLOOR_16, 20, ReactorTests::channelsAnywhere);
        GZGameTests.test("reactor_takes_one_port_of_each_kind", GZGameTests.FLOOR_16, 20, ReactorTests::onePortEach);
        GZGameTests.test("reactor_preview_shows_its_plan", GZGameTests.FLOOR_16, 20, ReactorTests::planPreview);
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

    /** A rod in every channel. */
    private static void fuelAll(FissionReactorBlockEntity r) {
        fuel(r, r.channelSlots().size());
    }

    /** Which core cells of a {@link #box} are Fuel Channels (x from the west, y up, z from the north). */
    @FunctionalInterface
    private interface CoreFill {
        boolean channel(int x, int y, int z);
    }

    /**
     * Builds a {@code w} x {@code h} x {@code d} reactor by hand round a controller at {@code rel} facing north, the
     * controller second from the west in the bottom of the north wall (as the example): casing walls with the Energy Port
     * in the middle of the roof, the Fluid Port low in the west wall and the Item Port low in the east wall, and a Fuel
     * Channel wherever {@code core} says.
     */
    private static FissionReactorBlockEntity box(GameTestHelper helper, BlockPos rel, int w, int h, int d, CoreFill core) {
        return box(helper, rel, w, h, d, 1, core);
    }

    /** As {@link #box(GameTestHelper, BlockPos, int, int, int, CoreFill)}, the controller {@code cx} from the west. */
    private static FissionReactorBlockEntity box(GameTestHelper helper, BlockPos rel, int w, int h, int d, int cx, CoreFill core) {
        helper.setBlock(rel, GZMachines.FISSION_REACTOR.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        BlockPos corner = helper.absolutePos(rel).west(cx);
        var level = helper.getLevel();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    BlockPos pos = corner.offset(x, y, z);
                    if (pos.equals(helper.absolutePos(rel))) {
                        continue;
                    }
                    boolean wall = x == 0 || x == w - 1 || y == 0 || y == h - 1 || z == 0 || z == d - 1;
                    Block block;
                    if (!wall) {
                        block = core.channel(x - 1, y - 1, z - 1) ? GZMultiblocks.FUEL_CHANNEL.block().get() : net.minecraft.world.level.block.Blocks.AIR;
                    } else if (y == h - 1 && x == w / 2 && z == d / 2) {
                        block = GZMultiblocks.ENERGY_PORT.block().get();
                    } else if (y == 1 && z == d / 2 && (x == 0 || x == w - 1)) {
                        block = (x == 0 ? GZMultiblocks.FLUID_PORT : GZMultiblocks.ITEM_PORT).block().get();
                    } else {
                        block = GZMultiblocks.LEAD_LINED_CASING.block().get();
                    }
                    level.setBlockAndUpdate(pos, block.defaultBlockState());
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

    /** Eight rods (a rod in every channel of the example), rods fully out, water in: 600 FE/t, water drawn, the core warms. */
    private static void reactorRuns(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        helper.assertTrue(r.channelSlots().size() == 8, "the example's 2x2x2 core has eight channels, " + r.channelSlots());
        fuelAll(r);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        helper.assertTrue(r.insertion() == 0 && Math.abs(r.powerLevel() - 1f) < 1e-4f, "full power with every channel loaded and the rods out");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(r.energy().stored() >= 30 * 8 * FissionReactorBlockEntity.ROD_OUTPUT, "about 600 FE a tick, have " + r.energy().stored());
            helper.assertTrue(r.tank(0).amount() < 16_000, "coolant is used");
            helper.assertTrue(r.heat() > FissionReactorBlockEntity.AMBIENT && r.heat() < FissionReactorBlockEntity.WARN_HEAT, "the core warms, " + r.heat());
            helper.assertTrue(r.status() == MachineStatus.RUNNING, "running, status " + r.status());
            // Half the rods back in halves the power; half the channels empty does too.
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
        fuelAll(r);
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
        fuelAll(r);
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
        fuelAll(r);
        r.tank(0).fillInternal(new FluidStack(GZFluids.HEAVY_WATER.source().get(), 16_000));
        withdrawAll(r);
        helper.assertTrue(r.equilibrium(1f) < FissionReactorBlockEntity.SCRAM_HEAT - 100, "full power on heavy water stays well below SCRAM");
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(!r.scrammed() && r.status() != MachineStatus.SCRAM, "no SCRAM, heat " + r.heat());
            helper.assertTrue(r.heat() > 600 && r.heat() < FissionReactorBlockEntity.SCRAM_HEAT, "the core runs hot but holds, " + r.heat());
            int used = 16_000 - r.tank(0).amount();
            int water = Math.round(300 * r.channelSlots().size() * FissionReactorBlockEntity.WATER_PER_ROD);
            helper.assertTrue(used > 0 && used <= water / FissionReactorBlockEntity.HEAVY_WATER_SAVING + 10, "a fifth of the water rate, used " + used);
            helper.succeed();
        });
    }

    /** The controller exposes nothing; coolant goes in through the Fluid Port, items through the Item Port only. */
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
        fuelAll(r);
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

    /** Power leaves through the Energy Port (low in the back wall) into a machine standing behind it. */
    private static void reactorEnergyPort(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        BlockPos port = cell(r, 'E');
        BlockPos behind = port.relative(r.controllerFacing().getOpposite());
        helper.getLevel().setBlockAndUpdate(behind, GZMachines.ORE_CRUSHER.block().get().defaultBlockState());
        OreCrusherBlockEntity crusher = (OreCrusherBlockEntity) helper.getLevel().getBlockEntity(behind);
        fuelAll(r);
        r.tank(0).fillInternal(new FluidStack(Fluids.WATER, 16_000));
        withdrawAll(r);
        helper.assertTrue(r.isFormed(), "the machine behind it leaves the reactor whole");
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
        fuelAll(r);
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
                float rate = RadiationSources.of(level).rateAt(level, net.minecraft.world.phys.Vec3.atCenterOf(ctrl));
                helper.assertTrue(rate > 0.5f * FissionReactorBlockEntity.CONTAMINATION_PEAK, "the land round it is irradiated: " + rate);
            });
        });
    }

    /** Meltdowns on, critical: SCRAM drops the rods in and the core cools instead of melting down. */
    private static void criticalSaved(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        r.setMeltdownsForTest(true);
        fuelAll(r);
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

    /**
     * A strong radiation source gives a pig near its centre a dose that makes it badly sick within a second (450 rad: stage
     * III); a zombie beside it is immune.
     */
    private static void contamination(GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.world.phys.Vec3 centre = helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 2, 8));
        RadiationSources.of(level).add(new RadiationSource(centre, 10, 2_000_000, 200, level.getGameTime()));
        var pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(8, 2, 9));
        var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(9, 2, 8));
        helper.succeedWhen(() -> {
            var sick = pig.getEffect(GZEffects.RADIATION_SICKNESS);
            helper.assertTrue(sick != null && sick.getAmplifier() >= 2, "the pig is badly sick near the centre");
            helper.assertTrue(!zombie.hasEffect(GZEffects.RADIATION_SICKNESS), "the undead are immune");
            RadiationSources.of(level).removeNear(centre, 1);
        });
    }

    /** Critical with water still flowing: coolant only slows the runaway, it cannot hold the core; it melts down. */
    private static void cooledMeltdown(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(7, 1, 6));
        BlockPos ctrl = r.controllerPos();
        r.setMeltdownsForTest(true);
        fuelAll(r);
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
     * Any box from 3x3x3 to 6x6x6 forms; every Fuel Channel in its core is a rod (so a taller core holds more), and its
     * energy buffer and coolant tank follow the channels (with floors, the tank capped). A 7-wide box does not form.
     */
    private static void reactorSizes(GameTestHelper helper) {
        FissionReactorBlockEntity small = box(helper, new BlockPos(1, 1, 1), 3, 3, 3, (x, y, z) -> true);
        helper.assertTrue(small.isFormed() && small.channelSlots().size() == 1, "a 3x3x3 reactor forms with one channel");
        helper.assertTrue(small.core().width() == 3 && small.core().height() == 3 && small.core().depth() == 3, "its size is found");
        helper.assertTrue(small.energy().capacity() == FissionReactorBlockEntity.MIN_ENERGY
                && small.tank(0).capacity() == FissionReactorBlockEntity.MIN_COOLANT, "the smallest buffers");
        FissionReactorBlockEntity big = box(helper, new BlockPos(6, 1, 1), 6, 6, 6, (x, y, z) -> true);
        helper.assertTrue(big.isFormed() && big.channelSlots().size() == 64, "a 6x6x6 reactor forms with 64 channels, " + big.channelSlots().size());
        helper.assertTrue(big.energy().capacity() == 64 * FissionReactorBlockEntity.ENERGY_PER_CHANNEL
                && big.tank(0).capacity() == FissionReactorBlockEntity.COOLANT_MAX, "buffers for 64 channels (the tank capped)");
        helper.assertTrue(big.thermalMass() == 1f, "a core packed with channels heats at the base rate");
        // Height counts: a 4x6x4 holds four times the rods of a 4x3x4.
        FissionReactorBlockEntity tall = box(helper, new BlockPos(13, 1, 1), 4, 6, 4, (x, y, z) -> true);
        helper.assertTrue(tall.isFormed() && tall.channelSlots().size() == 16, "a 4x6x4 reactor holds sixteen rods, " + tall.channelSlots().size());
        // Part of the core left as coolant: a checkerboard of columns, ten channels; the water slows its heating.
        FissionReactorBlockEntity mixed = box(helper, new BlockPos(1, 1, 8), 5, 4, 5, (x, y, z) -> (x + z) % 2 == 0);
        helper.assertTrue(mixed.isFormed() && mixed.channelSlots().size() == 10, "a checkerboard core: ten channels, " + mixed.channelSlots());
        helper.assertTrue(Math.abs(mixed.thermalMass() - 1.8f) < 1e-4f, "eight water cells to ten channels: 1.8x the mass, " + mixed.thermalMass());
        // Too wide: seven blocks.
        FissionReactorBlockEntity wide = box(helper, new BlockPos(8, 1, 9), 7, 3, 3, (x, y, z) -> true);
        helper.assertTrue(!wide.isFormed() && wide.problem(helper.getLevel()) != null, "a 7-wide box does not form");
        helper.succeed();
    }

    /**
     * Fuel Channels stand anywhere in the core, a rod each; the rest is coolant. Taking a channel out leaves a smaller
     * core; anything else in the core, or glass on an edge, stops it forming and the preview outlines it.
     */
    private static void channelsAnywhere(GameTestHelper helper) {
        FissionReactorBlockEntity r = box(helper, new BlockPos(3, 1, 3), 4, 5, 4, (x, y, z) -> (x + y + z) % 2 == 0);
        helper.assertTrue(r.isFormed() && r.channelSlots().size() == 6, "a 4x5x4 reactor with a 3D checkerboard of six channels forms, "
                + r.channelSlots());
        var level = helper.getLevel();
        BlockPos taken = r.core().cellOf(r.channelSlots().get(0));
        level.setBlockAndUpdate(taken, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        r.revalidate();
        helper.assertTrue(r.isFormed() && r.channelSlots().size() == 5, "a channel taken out leaves coolant: five channels, " + r.channelSlots());
        level.setBlockAndUpdate(taken, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        r.revalidate();                                                        // (stone tells no one; the reactor's recheck finds it)
        helper.assertTrue(!r.isFormed(), "stone in the core un-forms it");
        helper.assertTrue(r.mismatches(level).stream().anyMatch(m -> m.pos().equals(taken) && !m.empty()), "the stone is outlined as wrong");
        level.setBlockAndUpdate(taken, GZMultiblocks.FUEL_CHANNEL.block().get().defaultBlockState());
        r.revalidate();
        helper.assertTrue(r.isFormed() && r.channelSlots().size() == 6, "a channel back in: six again");
        // Glass on an edge is wrong.
        BlockPos edge = r.core().pos(r.core().col0(), 1, 0);
        level.setBlockAndUpdate(edge, GZMultiblocks.REACTOR_GLASS.block().get().defaultBlockState());
        helper.assertTrue(!r.isFormed() && r.mismatches(level).stream().anyMatch(m -> m.pos().equals(edge) && !m.empty()),
                "glass on an edge is outlined as wrong");
        helper.succeed();
    }

    /**
     * One Item, one Fluid and one Energy Port, in the walls: a second port of a kind is outlined as wrong; without one
     * the reactor says which it needs. The controller cannot stand in a corner.
     */
    private static void onePortEach(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        var level = helper.getLevel();
        BlockPos window = r.controllerPos().above(2);                         // the front wall's upper window
        level.setBlockAndUpdate(window, GZMultiblocks.ITEM_PORT.block().get().defaultBlockState());
        helper.assertTrue(!r.isFormed(), "a second Item Port un-forms it");
        BlockPos first = cell(r, 'U');
        helper.assertTrue(r.mismatches(level).stream().anyMatch(m -> !m.empty() && (m.pos().equals(window) || m.pos().equals(first))),
                "one of the two Item Ports is outlined as wrong");
        level.setBlockAndUpdate(window, GZMultiblocks.REACTOR_GLASS.block().get().defaultBlockState());
        r.revalidate();
        helper.assertTrue(r.isFormed(), "one Item Port again: it forms");
        BlockPos fluid = cell(r, 'W');
        level.setBlockAndUpdate(fluid, GZMultiblocks.LEAD_LINED_CASING.block().get().defaultBlockState());
        var problem = r.problem(level);
        helper.assertTrue(!r.isFormed() && problem != null && problem.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals(FissionCore.NEEDS_PORT), "without a Fluid Port it asks for one, " + problem);
        // The controller in a corner: the box is found, but the controller cannot stand there.
        FissionReactorBlockEntity cornered = box(helper, new BlockPos(11, 1, 9), 3, 3, 3, 0, (x, y, z) -> true);
        var scan = FissionCore.scan(level, cornered.controllerPos(), Direction.NORTH);
        helper.assertTrue(!cornered.isFormed() && scan.problem() != null && scan.problem().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents c
                && c.getKey().equals(FissionCore.CORNER), "a controller in a corner is refused, " + scan.problem());
        helper.succeed();
    }

    /**
     * A lone controller's ghost preview shows exactly its plan (4x4x4 at first): casing on the edges, roof and floor, glass
     * in the side walls, one port of each kind and Fuel Channels filling the core. The plan's size changes with the
     * screen's buttons, and building what the ghosts show forms the reactor.
     */
    private static void planPreview(GameTestHelper helper) {
        BlockPos rel = new BlockPos(7, 1, 4);
        helper.setBlock(rel, GZMachines.FISSION_REACTOR.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        FissionReactorBlockEntity r = helper.getBlockEntity(rel, FissionReactorBlockEntity.class);
        var level = helper.getLevel();
        var ghosts = r.mismatches(level);
        java.util.Map<Character, Long> keys = ghosts.stream().collect(java.util.stream.Collectors.groupingBy(m -> m.key(), java.util.stream.Collectors.counting()));
        helper.assertTrue(ghosts.size() == 63 && ghosts.stream().allMatch(m -> m.empty()), "a ghost on every cell of the 4x4x4 plan, " + ghosts.size());
        helper.assertTrue(keys.getOrDefault('C', 0L) == 8 && keys.getOrDefault('U', 0L) == 1 && keys.getOrDefault('W', 0L) == 1
                && keys.getOrDefault('E', 0L) == 1 && keys.getOrDefault('G', 0L) == 14 && keys.getOrDefault('L', 0L) == 38,
                "eight channels, a port of each kind, fourteen windows, the rest casing: " + keys);
        helper.assertTrue(r.previewLayers() == 4, "four layers to page through");
        r.handleButton(FissionReactorBlockEntity.BUTTON_PLAN + 1);            // wider
        r.handleButton(FissionReactorBlockEntity.BUTTON_PLAN + 5);            // deeper
        ghosts = r.mismatches(level);
        helper.assertTrue(ghosts.size() == 5 * 4 * 5 - 1, "the plan grows to 5x4x5, " + ghosts.size());
        for (var m : ghosts) {
            level.setBlockAndUpdate(m.pos(), r.ghostState(m));
        }
        r.revalidate();
        helper.assertTrue(r.isFormed() && r.channelSlots().size() == 3 * 2 * 3, "built as shown, it forms with eighteen channels, " + r.problem(level));
        helper.assertTrue(r.handleButton(FissionReactorBlockEntity.BUTTON_PLAN + 1) == false, "a formed reactor's plan is its own size");
        helper.succeed();
    }

    /**
     * Every loaded rod burns at once (each takes burnup every tick), each rod's lifetime is its own (within 15% of the
     * average), and a rod at the end of its life comes out spent while the others keep burning.
     */
    private static void everyRodBurns(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        fuelAll(r);
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
                helper.assertTrue(r.loadedRods() == slots.size() - 1, "the other seven still burn, " + r.loadedRods());
                helper.succeed();
            });
        });
    }

    /**
     * The menu shows the core a layer at a time: a rod slot on each channel of the layer picked (one rod each), and on a
     * cell still holding a rod after a rebuild. A shift-click loads every channel, whatever layer is shown.
     */
    private static void guiSlots(GameTestHelper helper) {
        FissionReactorBlockEntity r = reactor(helper, new BlockPos(5, 1, 3));
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = (dev.groundzero.content.machine.MachineMenu) r.createMenu(1, player.getInventory(), player);
        var channels = r.channelSlots();
        helper.assertTrue(channels.size() == 8, "eight channels, two layers of four, " + channels);
        for (int layer = 0; layer < FissionCore.GRID; layer++) {
            r.handleButton(FissionReactorBlockEntity.BUTTON_LAYER + layer);
            for (int slot = 0; slot < FissionReactorBlockEntity.CHANNELS; slot++) {
                boolean shown = slot / FissionCore.LAYER == layer && channels.contains(slot);
                helper.assertTrue(menu.slotShown(slot) == shown, "layer " + (layer + 1) + ": slot " + slot + " shown only on a channel of that layer");
            }
        }
        player.getInventory().setItem(0, new ItemStack(GZNuclear.URANIUM_FUEL_ROD.get(), 12));
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
            r.handleButton(FissionReactorBlockEntity.BUTTON_LAYER + slot / FissionCore.LAYER);
            helper.assertTrue(menu.slotShown(slot), "the rod in slot " + slot + " can still be taken out");
        }
        helper.assertTrue(!r.inventory().canPlaceItem(channels.get(0), new ItemStack(GZNuclear.URANIUM_FUEL_ROD.get())), "no rods go in now");
        helper.succeed();
    }

    /** Sixty-four rods in a 6x6x6 reactor: 4 800 FE/t. */
    private static void bigCore(GameTestHelper helper) {
        FissionReactorBlockEntity r = box(helper, new BlockPos(5, 1, 3), 6, 6, 6, (x, y, z) -> true);
        fuelAll(r);
        helper.assertTrue(r.loadedRods() == 64, "sixty-four rods, " + r.loadedRods());
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
