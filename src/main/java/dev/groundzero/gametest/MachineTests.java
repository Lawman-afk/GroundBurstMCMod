package dev.groundzero.gametest;

import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.machine.ProcessingBlockEntity;
import dev.groundzero.content.power.CombustionGeneratorBlockEntity;
import dev.groundzero.content.resource.GZResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Phase 4 checks: crushing, generator -> cable -> machine power flow, redstone control, automation, drops. */
final class MachineTests {
    private static final int OUT_MAIN = 1, OUT_BYPRODUCT = 2;

    private MachineTests() {}

    static void register() {
        GZGameTests.test("crusher_crushes_raw_iron", GZGameTests.FLOOR_16, 300, MachineTests::crusherCrushes);
        GZGameTests.test("crusher_needs_power", GZGameTests.FLOOR_16, 60, MachineTests::crusherNeedsPower);
        GZGameTests.test("generator_powers_crusher_over_cable", GZGameTests.FLOOR_16, 600, MachineTests::generatorCableCrusher);
        GZGameTests.test("cable_connections", GZGameTests.FLOOR_16, 20, MachineTests::cableConnections);
        GZGameTests.test("redstone_control", GZGameTests.FLOOR_16, 400, MachineTests::redstoneControl);
        GZGameTests.test("crusher_hopper_automation", GZGameTests.FLOOR_16, 400, MachineTests::hopperAutomation);
        GZGameTests.test("machine_drops_contents", GZGameTests.FLOOR_16, 20, MachineTests::dropsContents);
    }

    private static OreCrusherBlockEntity crusher(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, GZMachines.ORE_CRUSHER.block().get());
        return helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
    }

    private static int status(MachineBlockEntity machine) {
        return machine.data().get(MachineBlockEntity.DATA_STATUS);
    }

    private static int progress(MachineBlockEntity machine) {
        return machine.data().get(MachineBlockEntity.DATA_BASE_COUNT + ProcessingBlockEntity.DATA_PROGRESS);
    }

    private static void crusherCrushes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        crusher.energy().generate(10_000);
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_IRON));
        helper.succeedWhen(() -> {
            ItemStack out = crusher.inventory().getItem(OUT_MAIN);
            helper.assertTrue(out.is(GZResources.IRON_DUST.get()) && out.getCount() == 2, "expected 2 iron dust, have " + out);
            helper.assertTrue(crusher.inventory().getItem(0).isEmpty(), "input should be consumed");
            helper.assertTrue(crusher.energy().stored() < 10_000, "crushing should use energy");
        });
    }

    private static void crusherNeedsPower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_IRON));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(progress(crusher) == 0, "unpowered crusher made progress");
            helper.assertTrue(status(crusher) == MachineStatus.NO_POWER.ordinal(), "status should be NO_POWER, was " + status(crusher));
            helper.assertTrue(!helper.getBlockState(pos).getValue(MachineBlock.ACTIVE), "unpowered crusher shows active");
            helper.succeed();
        });
    }

    private static void generatorCableCrusher(GameTestHelper helper) {
        BlockPos gen = new BlockPos(2, 1, 4);
        helper.setBlock(gen, GZMachines.COMBUSTION_GENERATOR.block().get());
        for (int x = 3; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), GZMachines.CABLES.get(0).block().get());
        }
        BlockPos crusherPos = new BlockPos(8, 1, 4);
        OreCrusherBlockEntity crusher = crusher(helper, crusherPos);
        CombustionGeneratorBlockEntity generator = helper.getBlockEntity(gen, CombustionGeneratorBlockEntity.class);
        generator.inventory().setItem(0, new ItemStack(Items.COAL, 2));
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_COPPER, 2));
        helper.succeedWhen(() -> {
            ItemStack out = crusher.inventory().getItem(OUT_MAIN);
            helper.assertTrue(out.is(GZResources.COPPER_DUST.get()) && out.getCount() == 4, "expected 4 copper dust, have " + out);
        });
    }

    private static void cableConnections(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 1, 3), b = new BlockPos(4, 1, 3), m = new BlockPos(4, 1, 4);
        helper.setBlock(a, GZMachines.CABLES.get(0).block().get());
        helper.setBlock(b, GZMachines.CABLES.get(1).block().get());
        helper.setBlock(m, GZMachines.ORE_CRUSHER.block().get());
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        BlockState sa = helper.getBlockState(a), sb = helper.getBlockState(b);
        helper.assertTrue(sa.getValue(PipeBlock.EAST), "cable should join the cable to its east");
        helper.assertTrue(sb.getValue(PipeBlock.WEST), "cable should join the cable to its west");
        helper.assertTrue(sb.getValue(PipeBlock.SOUTH), "cable should connect to the machine to its south");
        helper.assertTrue(!sa.getValue(PipeBlock.UP), "cable must not connect to plain stone");
        helper.assertTrue(!sa.getValue(PipeBlock.NORTH), "cable must not connect to air");
        helper.succeed();
    }

    private static void redstoneControl(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        crusher.cycleRedstoneMode();     // IGNORE -> HIGH: runs only with a signal
        crusher.energy().generate(10_000);
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_GOLD));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(progress(crusher) == 0, "crusher ran without a redstone signal");
            helper.assertTrue(status(crusher) == MachineStatus.REDSTONE.ordinal(), "status should be REDSTONE");
            helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
            helper.succeedWhen(() -> {
                ItemStack out = crusher.inventory().getItem(OUT_MAIN);
                helper.assertTrue(out.is(GZResources.GOLD_DUST.get()), "expected gold dust once powered, have " + out);
            });
        });
    }

    private static void hopperAutomation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        crusher.energy().generate(10_000);
        HopperBlockEntity top = helper.getBlockEntity(new BlockPos(3, 3, 3), HopperBlockEntity.class);
        top.setItem(0, new ItemStack(Items.RAW_IRON, 1));
        top.setItem(1, new ItemStack(Items.DIRT, 1));     // not a crusher input: must stay in the hopper
        HopperBlockEntity bottom = helper.getBlockEntity(new BlockPos(3, 1, 3), HopperBlockEntity.class);
        helper.succeedWhen(() -> {
            int dust = 0;
            for (int i = 0; i < bottom.getContainerSize(); i++) {
                if (bottom.getItem(i).is(GZResources.IRON_DUST.get())) {
                    dust += bottom.getItem(i).getCount();
                }
            }
            helper.assertTrue(dust == 2, "bottom hopper should pull 2 iron dust, has " + dust);
            helper.assertTrue(top.getItem(1).is(Items.DIRT), "dirt should not be accepted by the crusher");
            helper.assertTrue(crusher.inventory().getItem(OUT_BYPRODUCT).isEmpty() || crusher.inventory().getItem(OUT_BYPRODUCT).is(GZResources.COBALT_DUST.get()),
                    "unexpected byproduct");
        });
    }

    private static void dropsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_IRON, 5));
        crusher.inventory().setItem(OUT_MAIN, new ItemStack(GZResources.IRON_DUST.get(), 3));
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.assertItemEntityPresent(Items.RAW_IRON, pos, 2);
        helper.assertItemEntityPresent(GZResources.IRON_DUST.get(), pos, 2);
        helper.assertItemEntityPresent(GZMachines.ORE_CRUSHER.item().get(), pos, 2);
        helper.succeed();
    }
}
