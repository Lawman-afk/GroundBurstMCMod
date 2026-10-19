package dev.groundzero.gametest;

import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.fluid.ChemFluid;
import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.ChemicalPlantBlockEntity;
import dev.groundzero.content.machine.CryogenicCondenserBlockEntity;
import dev.groundzero.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.SideConfig;
import dev.groundzero.content.machine.TallMachineBlock;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.part.GZParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Chemical fluids: pipes, fluid auto-eject, multi-tank recipes, dumping, canisters and buckets, liquids in the world.
 */
final class FluidSystemTests {
    private FluidSystemTests() {}

    static void register() {
        GZGameTests.test("pipe_carries_fluid", GZGameTests.FLOOR_16, 20, FluidSystemTests::pipe);
        GZGameTests.test("pipe_run_limited_by_weakest_tier", GZGameTests.FLOOR_16, 20, FluidSystemTests::pipeTiers);
        GZGameTests.test("condenser_ejects_into_plant", GZGameTests.FLOOR_16, 400, FluidSystemTests::ejectIntoPlant);
        GZGameTests.test("plant_two_fluid_recipe", GZGameTests.FLOOR_16, 400, FluidSystemTests::rocketFuel);
        GZGameTests.test("plant_dump", GZGameTests.FLOOR_16, 40, FluidSystemTests::dump);
        GZGameTests.test("canister_and_bucket_containers", GZGameTests.FLOOR_16, 20, FluidSystemTests::containers);
        GZGameTests.test("liquid_in_world", GZGameTests.FLOOR_16, 20, FluidSystemTests::world);
        GZGameTests.test("plant_covered_faces_closed", GZGameTests.FLOOR_16, 20, FluidSystemTests::coveredFaces);
    }

    private static ChemicalPlantBlockEntity plant(GameTestHelper helper, BlockPos rel) {
        BlockPos controller = helper.absolutePos(rel);
        helper.assertTrue(GZMultiblocks.CHEMICAL_PLANT_SPAWNER.get().spawn(helper.getLevel(), controller, Direction.NORTH), "plant spawns");
        return (ChemicalPlantBlockEntity) helper.getLevel().getBlockEntity(controller);
    }

    private static void insert(ChemicalPlantBlockEntity plant, ChemFluid fluid, int amount) {
        try (var tx = Transaction.openRoot()) {
            int in = plant.fluidHandler().insert(FluidResource.of(fluid.source().get()), amount, tx);
            if (in != amount) {
                throw new IllegalStateException("plant took " + in + " of " + amount + " mB " + fluid.name());
            }
            tx.commit();
        }
    }

    /** Water poured into the far end of a pipe run arrives in the separator at the other end. */
    private static void pipe(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        ElectrolyticSeparatorBlockEntity sep = helper.getBlockEntity(new BlockPos(3, 1, 3), ElectrolyticSeparatorBlockEntity.class);
        for (int x = 4; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), GZFluids.FLUID_PIPE.block().get());
        }
        var level = helper.getLevel();
        var end = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(new BlockPos(7, 1, 3)), Direction.EAST);
        helper.assertTrue(end != null, "a pipe takes fluid");
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(end.insert(FluidResource.of(Fluids.WATER), 1_500, tx) == 1_500, "the pipe passes 1500 mB on");
            helper.assertTrue(end.extract(FluidResource.of(Fluids.WATER), 100, tx) == 0, "pipes hold nothing to drain");
            tx.commit();
        }
        helper.assertTrue(sep.tank(0).amount() == 1_500, "water reaches the separator, has " + sep.tank(0).amount());
        helper.succeed();
    }

    /** A steel run with one copper pipe in it carries only copper's 1000 mB a tick; pressurized alone carries 16000. */
    private static void pipeTiers(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        helper.setBlock(new BlockPos(4, 1, 3), GZFluids.FLUID_PIPE.block().get());
        helper.setBlock(new BlockPos(5, 1, 3), GZFluids.COPPER_FLUID_PIPE.block().get());
        helper.setBlock(new BlockPos(6, 1, 3), GZFluids.FLUID_PIPE.block().get());
        var level = helper.getLevel();
        var end = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(new BlockPos(6, 1, 3)), Direction.EAST);
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(end.insert(FluidResource.of(Fluids.WATER), 5_000, tx) == 1_000, "a copper pipe in the run caps it at 1000 mB/t");
            tx.commit();
        }
        helper.setBlock(new BlockPos(3, 1, 8), GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        helper.setBlock(new BlockPos(4, 1, 8), GZFluids.PRESSURIZED_FLUID_PIPE.block().get());
        var fast = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(new BlockPos(4, 1, 8)), Direction.EAST);
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(fast.insert(FluidResource.of(Fluids.WATER), 20_000, tx) == 16_000, "pressurized carries 16000 mB/t (the separator holds 16000)");
            tx.commit();
        }
        helper.succeed();
    }

    /** A condenser with auto-eject pours its liquid oxygen down a pipe into the plant's chemical input tank. */
    private static void ejectIntoPlant(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = plant(helper, new BlockPos(8, 1, 8));
        // Controller front faces north: pipe from z=5 to z=7, condenser at z=4.
        for (int z = 5; z <= 7; z++) {
            helper.setBlock(new BlockPos(8, 1, z), GZFluids.FLUID_PIPE.block().get());
        }
        BlockPos condPos = new BlockPos(8, 1, 4);
        TallMachineBlock block = GZMachines.CRYOGENIC_CONDENSER.block().get();
        helper.setBlock(condPos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(condPos), block.defaultBlockState(), null, ItemStack.EMPTY);
        CryogenicCondenserBlockEntity cond = helper.getBlockEntity(condPos, CryogenicCondenserBlockEntity.class);
        cond.energy().generate(50_000);
        cond.inventory().setItem(0, new ItemStack(GZParts.OXYGEN_MEMBRANE.get()));
        cond.changeSides(SideConfig::toggleAutoEject);
        helper.succeedWhen(() -> helper.assertTrue(plant.tanks().stream()
                        .anyMatch(t -> t.isInput() && t.fluid().getFluid() == GZFluids.LIQUID_OXYGEN.source().get() && t.amount() >= 1_000),
                "liquid oxygen should reach a plant input tank"));
    }

    /** Liquid rocket fuel takes two chemical fluids at once, each from its own input tank. */
    private static void rocketFuel(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = plant(helper, new BlockPos(4, 1, 4));
        plant.energy().generate(50_000);
        insert(plant, GZFluids.SYNTHETIC_FUEL, 1_000);
        insert(plant, GZFluids.LIQUID_OXYGEN, 1_000);
        helper.assertTrue(plant.tank(1).fluid().getFluid() != plant.tank(2).fluid().getFluid(), "the two fluids go into different tanks");
        helper.succeedWhen(() -> helper.assertTrue(plant.tank(ChemicalPlantBlockEntity.TANK_PRODUCT).amount() == 1_000
                        && plant.tank(ChemicalPlantBlockEntity.TANK_PRODUCT).fluid().getFluid() == GZFluids.LIQUID_ROCKET_FUEL.source().get(),
                "expected 1000 mB of rocket fuel"));
    }

    private static void dump(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = plant(helper, new BlockPos(4, 1, 4));
        insert(plant, GZFluids.NITRIC_ACID, 3_000);
        try (var tx = Transaction.openRoot()) {
            plant.tank(0).insert(0, FluidResource.of(Fluids.WATER), 5_000, tx);
            tx.commit();
        }
        plant.dumpTank(1);
        plant.dumpTank(0);
        helper.assertTrue(plant.tank(1).isEmpty(), "a chemical tank dumps");
        helper.assertTrue(plant.tank(0).amount() == 5_000, "the water tank cannot be dumped");
        helper.succeed();
    }

    /** Canisters and our buckets fill and pour like vanilla buckets. */
    private static void containers(GameTestHelper helper) {
        ItemStack canister = new ItemStack(GZChemicals.SULFURIC_ACID_CANISTER.get());
        var handler = ItemAccess.forStack(canister).getCapability(Capabilities.Fluid.ITEM);
        helper.assertTrue(handler != null && handler.getAmountAsLong(0) == 1_000
                && handler.getResource(0).getFluid() == GZFluids.SULFURIC_ACID.source().get(), "a filled canister holds 1000 mB of its fluid");
        ItemStack empty = new ItemStack(GZChemicals.EMPTY_GAS_CANISTER.get());
        var gas = ItemAccess.forStack(empty).getCapability(Capabilities.Fluid.ITEM);
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(gas.insert(FluidResource.of(GZFluids.SULFURIC_ACID.source().get()), 1_000, tx) == 0, "gas canisters refuse liquids");
        }
        ItemStack bucket = new ItemStack(GZFluids.NAPALM.bucket().get());
        var b = ItemAccess.forStack(bucket).getCapability(Capabilities.Fluid.ITEM);
        helper.assertTrue(b != null && b.getResource(0).getFluid() == GZFluids.NAPALM.source().get(), "the napalm bucket holds napalm");
        var gasBucket = ItemAccess.forStack(new ItemStack(GZFluids.DEUTERIUM.bucket().get())).getCapability(Capabilities.Fluid.ITEM);
        helper.assertTrue(gasBucket != null && gasBucket.getResource(0).getFluid() == GZFluids.DEUTERIUM.source().get(), "a gas bucket holds its gas");
        helper.assertTrue(new ItemStack(Items.BUCKET).getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(new ItemStack(Items.BUCKET))) != null,
                "vanilla buckets still work");
        helper.succeed();
    }

    /** The controller's faces against its own casing and glass expose nothing; its open faces do. */
    private static void coveredFaces(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = plant(helper, new BlockPos(4, 1, 4));
        var level = helper.getLevel();
        BlockPos c = plant.controllerPos();
        // Facing north, the structure lies east (the controller's side) and south (its back), and above.
        for (Direction covered : new Direction[] {Direction.EAST, Direction.SOUTH, Direction.UP}) {
            helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, c, covered) == null
                    && level.getCapability(Capabilities.Fluid.BLOCK, c, covered) == null
                    && level.getCapability(Capabilities.Item.BLOCK, c, covered) == null, "face " + covered + " is inside the structure");
        }
        for (Direction open : new Direction[] {Direction.NORTH, Direction.WEST, Direction.DOWN}) {
            helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, c, open) != null, "face " + open + " is open");
        }
        helper.succeed();
    }

    /** Liquids can be placed and flow; a source block is our fluid. */
    private static void world(GameTestHelper helper) {
        BlockPos pos = new BlockPos(6, 2, 6);
        helper.setBlock(pos, GZFluids.LIQUID_NITROGEN.block().get().defaultBlockState());
        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos)).getType() == GZFluids.LIQUID_NITROGEN.source().get(),
                "the block is a liquid nitrogen source");
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos.east())).getType()
                == GZFluids.LIQUID_NITROGEN.flowing().get(), "it flows sideways"));
    }
}
