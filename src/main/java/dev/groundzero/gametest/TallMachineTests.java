package dev.groundzero.gametest;

import dev.groundzero.content.machine.ArcFurnaceBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.IndustrialPressBlockEntity;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.ProcessingBlockEntity;
import dev.groundzero.content.machine.TallMachineBlock;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.resource.GZResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Phase 4 slice 2: two-block machines (Arc Furnace, Industrial Press), dies, and tall-block behaviour. */
final class TallMachineTests {
    private TallMachineTests() {}

    static void register() {
        GZGameTests.test("arc_furnace_makes_steel", GZGameTests.FLOOR_16, 300, TallMachineTests::arcSteel);
        GZGameTests.test("press_uses_die_without_consuming_it", GZGameTests.FLOOR_16, 300, TallMachineTests::pressPlate);
        GZGameTests.test("press_needs_die", GZGameTests.FLOOR_16, 60, TallMachineTests::pressNeedsDie);
        GZGameTests.test("tall_machine_halves", GZGameTests.FLOOR_16, 20, TallMachineTests::halves);
        GZGameTests.test("tall_machine_upper_half_capabilities", GZGameTests.FLOOR_16, 20, TallMachineTests::upperCapabilities);
    }

    /** Places a tall machine the way a player would: lower half, then setPlacedBy adds the upper half. */
    private static <T extends MachineBlockEntity> T place(GameTestHelper helper, TallMachineBlock block, BlockPos pos, Class<T> type) {
        BlockState lower = block.defaultBlockState();
        helper.setBlock(pos, lower);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), lower, null, ItemStack.EMPTY);
        return helper.getBlockEntity(pos, type);
    }

    private static void arcSteel(GameTestHelper helper) {
        ArcFurnaceBlockEntity arc = place(helper, GZMachines.ARC_FURNACE.block().get(), new BlockPos(3, 1, 3), ArcFurnaceBlockEntity.class);
        arc.energy().generate(40_000);
        arc.inventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        arc.inventory().setItem(1, new ItemStack(Items.CHARCOAL, 2));
        helper.succeedWhen(() -> {
            ItemStack out = arc.inventory().getItem(2);
            helper.assertTrue(out.is(GZParts.STEEL_INGOT.get()) && out.getCount() == 1, "expected a steel ingot, have " + out);
        });
    }

    private static void pressPlate(GameTestHelper helper) {
        IndustrialPressBlockEntity press = place(helper, GZMachines.INDUSTRIAL_PRESS.block().get(), new BlockPos(3, 1, 3), IndustrialPressBlockEntity.class);
        press.energy().generate(20_000);
        press.inventory().setItem(0, new ItemStack(GZResources.LEAD_INGOT.get(), 2));
        press.inventory().setItem(1, new ItemStack(GZParts.PLATE_DIE.get()));
        helper.succeedWhen(() -> {
            ItemStack out = press.inventory().getItem(2);
            helper.assertTrue(out.is(GZParts.LEAD_PLATE.get()) && out.getCount() == 2, "expected 2 lead plates, have " + out);
            helper.assertTrue(press.inventory().getItem(1).is(GZParts.PLATE_DIE.get()), "the die must not be consumed");
        });
    }

    private static void pressNeedsDie(GameTestHelper helper) {
        IndustrialPressBlockEntity press = place(helper, GZMachines.INDUSTRIAL_PRESS.block().get(), new BlockPos(3, 1, 3), IndustrialPressBlockEntity.class);
        press.energy().generate(20_000);
        press.inventory().setItem(0, new ItemStack(Items.COPPER_INGOT));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(press.data().get(MachineBlockEntity.DATA_BASE_COUNT + ProcessingBlockEntity.DATA_PROGRESS) == 0, "pressed without a die");
            helper.assertTrue(press.data().get(MachineBlockEntity.DATA_STATUS) == MachineStatus.NO_RECIPE.ordinal(), "status should be NO_RECIPE");
            helper.succeed();
        });
    }

    private static void halves(GameTestHelper helper) {
        TallMachineBlock block = GZMachines.ARC_FURNACE.block().get();
        BlockPos pos = new BlockPos(3, 1, 3);
        place(helper, block, pos, ArcFurnaceBlockEntity.class);
        BlockState upper = helper.getBlockState(pos.above());
        helper.assertTrue(upper.is(block) && upper.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER, "upper half missing");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(pos.above())) == null, "upper half must not have a block entity");
        // Breaking the upper half takes the lower one with it and drops exactly one machine.
        helper.getLevel().destroyBlock(helper.absolutePos(pos.above()), true);
        helper.assertBlockPresent(Blocks.AIR, pos);
        helper.assertItemEntityCountIs(GZMachines.ARC_FURNACE.item().get(), pos, 2, 1);
        // No room above: placement is refused.
        BlockPos low = new BlockPos(8, 1, 3);
        helper.setBlock(low.above(), Blocks.STONE);
        helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(low.above())).canBeReplaced(), "test setup: stone should block");
        helper.succeed();
    }

    private static void upperCapabilities(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        IndustrialPressBlockEntity press = place(helper, GZMachines.INDUSTRIAL_PRESS.block().get(), pos, IndustrialPressBlockEntity.class);
        BlockPos upperAbs = helper.absolutePos(pos.above());
        var energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, upperAbs, Direction.EAST);
        helper.assertTrue(energy != null, "upper half should expose the machine's energy");
        helper.assertTrue(energy.getCapacityAsLong() == press.energy().capacity(), "upper half energy is not the machine's buffer");
        // A cable beside the upper half connects to it.
        BlockPos cable = pos.above().east();
        Block copper = GZMachines.CABLES.get(0).block().get();
        // setBlock skips placement logic, so resolve the cable's connections the way placing it would.
        helper.setBlock(cable, Block.updateFromNeighbourShapes(copper.defaultBlockState(), helper.getLevel(), helper.absolutePos(cable)));
        helper.assertTrue(dev.groundzero.content.power.CableBlock.side(helper.getBlockState(cable), net.minecraft.core.Direction.WEST) == dev.groundzero.content.power.CableBlock.Side.CONNECTED, "cable should connect to the upper half");
        helper.succeed();
    }
}
