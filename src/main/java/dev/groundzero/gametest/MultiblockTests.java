package dev.groundzero.gametest;

import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.machine.ChemicalPlantBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.resource.GZResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Phase 4 slice 4: multiblock forming in every rotation, un-forming, the Item Port, the spawner and a plant recipe. */
final class MultiblockTests {
    private MultiblockTests() {}

    static void register() {
        GZGameTests.test("plant_forms_in_every_rotation", GZGameTests.FLOOR_16, 20, MultiblockTests::rotations);
        GZGameTests.test("plant_unforms_when_broken", GZGameTests.FLOOR_16, 20, MultiblockTests::unform);
        GZGameTests.test("plant_wrong_block_blocks_forming", GZGameTests.FLOOR_16, 20, MultiblockTests::wrongBlock);
        GZGameTests.test("item_port_reaches_controller", GZGameTests.FLOOR_16, 20, MultiblockTests::port);
        GZGameTests.test("spawner_builds_and_refuses_obstruction", GZGameTests.FLOOR_16, 20, MultiblockTests::spawner);
        GZGameTests.test("plant_makes_sulfuric_acid", GZGameTests.FLOOR_16, 400, MultiblockTests::sulfuric);
    }

    /** Builds the Chemical Plant by hand, controller first, parts placed one by one (their placement re-checks it). */
    private static ChemicalPlantBlockEntity build(GameTestHelper helper, BlockPos rel, Direction facing) {
        helper.setBlock(rel, GZMachines.CHEMICAL_PLANT.block().get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        BlockPos controller = helper.absolutePos(rel);
        MultiblockDefinition def = GZMultiblocks.CHEMICAL_PLANT;
        for (MultiblockDefinition.Cell cell : def.cells(controller, facing)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER) {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
        return helper.getBlockEntity(rel, ChemicalPlantBlockEntity.class);
    }

    private static void assertPartsFormed(GameTestHelper helper, ChemicalPlantBlockEntity plant, boolean formed) {
        for (BlockPos pos : plant.definition().partPositions(plant.controllerPos(), plant.controllerFacing())) {
            var state = helper.getLevel().getBlockState(pos);
            if (!state.hasProperty(MultiblockPartBlock.FORMED)) {
                continue;                                    // the part that was just broken
            }
            helper.assertTrue(state.getValue(MultiblockPartBlock.FORMED) == formed, "part at " + pos + " should have formed=" + formed);
        }
    }

    private static void rotations(GameTestHelper helper) {
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        BlockPos[] spots = {new BlockPos(3, 1, 3), new BlockPos(11, 1, 3), new BlockPos(3, 1, 11), new BlockPos(11, 1, 11)};
        for (int i = 0; i < 4; i++) {
            ChemicalPlantBlockEntity plant = build(helper, spots[i], facings[i]);
            helper.assertTrue(plant.isFormed(), "plant facing " + facings[i] + " should form");
            assertPartsFormed(helper, plant, true);
            // The structure extends to the controller's right (clockwise) and behind it.
            BlockPos behindRight = plant.controllerPos().relative(facings[i].getClockWise()).relative(facings[i].getOpposite());
            helper.assertTrue(helper.getLevel().getBlockState(behindRight).is(GZMultiblocks.ITEM_PORT.block().get()),
                    "the item port sits behind and right of a controller facing " + facings[i]);
        }
        helper.succeed();
    }

    private static void unform(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = build(helper, new BlockPos(4, 1, 4), Direction.NORTH);
        helper.assertTrue(plant.isFormed(), "plant should form");
        BlockPos glass = plant.controllerPos().above();
        helper.getLevel().destroyBlock(glass, true);
        helper.assertTrue(!plant.isFormed(), "breaking a part un-forms the plant");
        assertPartsFormed(helper, plant, false);
        helper.getLevel().setBlockAndUpdate(glass, GZMultiblocks.REACTOR_GLASS.block().get().defaultBlockState());
        helper.assertTrue(plant.isFormed(), "replacing the part forms it again");
        helper.succeed();
    }

    private static void wrongBlock(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = build(helper, new BlockPos(4, 1, 4), Direction.SOUTH);
        BlockPos glass = plant.controllerPos().above();
        helper.getLevel().setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
        helper.assertTrue(!plant.isFormed(), "vanilla glass is not reactor glass");
        var wrong = plant.definition().check(helper.getLevel(), plant.controllerPos(), plant.controllerFacing());
        helper.assertTrue(wrong.size() == 1 && !wrong.get(0).empty() && wrong.get(0).layer() == 1,
                "exactly one wrong (not missing) block on layer 1, got " + wrong);
        helper.succeed();
    }

    private static void port(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = build(helper, new BlockPos(4, 1, 4), Direction.NORTH);
        BlockPos portPos = plant.controllerPos().east().south();
        var level = helper.getLevel();
        var items = level.getCapability(Capabilities.Item.BLOCK, portPos, Direction.EAST);
        var fluids = level.getCapability(Capabilities.Fluid.BLOCK, portPos, Direction.EAST);
        helper.assertTrue(items != null && fluids != null, "a formed item port exposes items and fluids");
        try (var tx = Transaction.openRoot()) {
            int in = 0;
            for (int i = 0; i < items.size(); i++) {
                in += items.insert(i, ItemResource.of(GZResources.SULFUR_DUST.get()), 4 - in, tx);
            }
            helper.assertTrue(in == 4, "the port should accept sulfur dust, took " + in);
            helper.assertTrue(fluids.insert(0, FluidResource.of(Fluids.WATER), 2000, tx) == 2000, "the port fills the plant's tank");
            tx.commit();
        }
        helper.assertTrue(plant.inventory().countItem(GZResources.SULFUR_DUST.get()) == 4, "sulfur dust arrives in the controller");
        helper.assertTrue(plant.tank().amount() == 2000, "water arrives in the controller's tank");
        level.destroyBlock(plant.controllerPos().above(), true);
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, portPos, Direction.EAST) == null, "an unformed port exposes nothing");
        helper.succeed();
    }

    private static void spawner(GameTestHelper helper) {
        var item = GZMultiblocks.CHEMICAL_PLANT_SPAWNER.get();
        BlockPos controller = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.assertTrue(item.spawn(helper.getLevel(), controller, Direction.WEST), "spawner should place on open ground");
        ChemicalPlantBlockEntity plant = (ChemicalPlantBlockEntity) helper.getLevel().getBlockEntity(controller);
        helper.assertTrue(plant != null && plant.isFormed() && plant.controllerFacing() == Direction.WEST, "spawned plant is formed, facing west");
        BlockPos blocked = helper.absolutePos(new BlockPos(10, 1, 4));
        helper.getLevel().setBlockAndUpdate(blocked.above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(!item.spawn(helper.getLevel(), blocked, Direction.NORTH), "spawner refuses when stone is in the way");
        helper.assertTrue(helper.getLevel().getBlockState(blocked).isAir(), "nothing is placed when refused");
        helper.succeed();
    }

    private static void sulfuric(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = build(helper, new BlockPos(4, 1, 4), Direction.NORTH);
        plant.energy().generate(50_000);
        try (var tx = Transaction.openRoot()) {
            plant.tank().insert(0, FluidResource.of(Fluids.WATER), 4_000, tx);
            tx.commit();
        }
        plant.inventory().setItem(0, new ItemStack(GZResources.SULFUR_DUST.get()));
        plant.inventory().setItem(1, new ItemStack(GZParts.EMPTY_CANISTER.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(plant.inventory().countItem(GZChemicals.SULFURIC_ACID_CANISTER.get()) == 1, "expected a sulfuric acid canister");
            helper.assertTrue(plant.tank().amount() == 3_000, "1000 mB of water should be used, tank has " + plant.tank().amount());
        });
    }
}
