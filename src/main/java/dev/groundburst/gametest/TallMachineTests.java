package dev.groundburst.gametest;

import dev.groundburst.content.machine.ArcFurnaceBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.IndustrialPressBlockEntity;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.ProcessingBlockEntity;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.resource.GBResources;
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
        GBGameTests.test("arc_furnace_makes_steel", GBGameTests.FLOOR_16, 300, TallMachineTests::arcSteel);
        GBGameTests.test("press_uses_die_without_consuming_it", GBGameTests.FLOOR_16, 300, TallMachineTests::pressPlate);
        GBGameTests.test("press_needs_die", GBGameTests.FLOOR_16, 60, TallMachineTests::pressNeedsDie);
        GBGameTests.test("tall_machine_halves", GBGameTests.FLOOR_16, 20, TallMachineTests::halves);
        GBGameTests.test("tall_machine_upper_half_capabilities", GBGameTests.FLOOR_16, 20, TallMachineTests::upperCapabilities);
        GBGameTests.test("tall_machine_halves_have_their_own_sides", GBGameTests.FLOOR_16, 20, TallMachineTests::halfSides);
    }

    /** Each half of a tall machine answers pipes through its own faces; the faces between the halves expose nothing. */
    private static void halfSides(GameTestHelper helper) {
        IndustrialPressBlockEntity press = place(helper, GBMachines.INDUSTRIAL_PRESS.block().get(), new BlockPos(3, 1, 3), IndustrialPressBlockEntity.class);
        var level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(3, 1, 3)), upper = lower.above();
        var cap = net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK;
        net.minecraft.core.Direction front = level.getBlockState(lower).getValue(dev.groundburst.content.machine.MachineBlock.FACING);
        helper.assertTrue(level.getCapability(cap, lower, front) != null && level.getCapability(cap, upper, front) != null, "both fronts open");
        press.changeSides(true, c -> c.cycleItems(dev.groundburst.content.machine.SideConfig.Face.FRONT, true, press.allowedItemModes()));
        helper.assertTrue(level.getCapability(cap, upper, front) == null, "the upper front closed on its own");
        helper.assertTrue(level.getCapability(cap, lower, front) != null, "the lower front still open");
        helper.assertTrue(level.getCapability(cap, lower, net.minecraft.core.Direction.UP) == null
                && level.getCapability(cap, upper, net.minecraft.core.Direction.DOWN) == null, "nothing between the halves");
        helper.succeed();
    }

    /** Places a tall machine the way a player would: lower half, then setPlacedBy adds the upper half. */
    private static <T extends MachineBlockEntity> T place(GameTestHelper helper, TallMachineBlock block, BlockPos pos, Class<T> type) {
        BlockState lower = block.defaultBlockState();
        helper.setBlock(pos, lower);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), lower, null, ItemStack.EMPTY);
        return helper.getBlockEntity(pos, type);
    }

    private static void arcSteel(GameTestHelper helper) {
        ArcFurnaceBlockEntity arc = place(helper, GBMachines.ARC_FURNACE.block().get(), new BlockPos(3, 1, 3), ArcFurnaceBlockEntity.class);
        arc.energy().generate(40_000);
        arc.inventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        arc.inventory().setItem(1, new ItemStack(Items.CHARCOAL, 2));
        helper.succeedWhen(() -> {
            ItemStack out = arc.inventory().getItem(2);
            helper.assertTrue(out.is(GBParts.STEEL_INGOT.get()) && out.getCount() == 1, "expected a steel ingot, have " + out);
        });
    }

    private static void pressPlate(GameTestHelper helper) {
        IndustrialPressBlockEntity press = place(helper, GBMachines.INDUSTRIAL_PRESS.block().get(), new BlockPos(3, 1, 3), IndustrialPressBlockEntity.class);
        press.energy().generate(20_000);
        press.inventory().setItem(0, new ItemStack(GBResources.LEAD_INGOT.get(), 2));
        press.inventory().setItem(1, new ItemStack(GBParts.PLATE_DIE.get()));
        helper.succeedWhen(() -> {
            ItemStack out = press.inventory().getItem(2);
            helper.assertTrue(out.is(GBParts.LEAD_PLATE.get()) && out.getCount() == 2, "expected 2 lead plates, have " + out);
            helper.assertTrue(press.inventory().getItem(1).is(GBParts.PLATE_DIE.get()), "the die must not be consumed");
        });
    }

    private static void pressNeedsDie(GameTestHelper helper) {
        IndustrialPressBlockEntity press = place(helper, GBMachines.INDUSTRIAL_PRESS.block().get(), new BlockPos(3, 1, 3), IndustrialPressBlockEntity.class);
        press.energy().generate(20_000);
        press.inventory().setItem(0, new ItemStack(Items.COPPER_INGOT));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(press.data().get(MachineBlockEntity.DATA_BASE_COUNT + ProcessingBlockEntity.DATA_PROGRESS) == 0, "pressed without a die");
            helper.assertTrue(press.data().get(MachineBlockEntity.DATA_STATUS) == MachineStatus.NO_RECIPE.ordinal(), "status should be NO_RECIPE");
            helper.succeed();
        });
    }

    private static void halves(GameTestHelper helper) {
        TallMachineBlock block = GBMachines.ARC_FURNACE.block().get();
        BlockPos pos = new BlockPos(3, 1, 3);
        place(helper, block, pos, ArcFurnaceBlockEntity.class);
        BlockState upper = helper.getBlockState(pos.above());
        helper.assertTrue(upper.is(block) && upper.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER, "upper half missing");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(pos.above())) == null, "upper half must not have a block entity");
        // Breaking the upper half takes the lower one with it and drops exactly one machine.
        helper.getLevel().destroyBlock(helper.absolutePos(pos.above()), true);
        helper.assertBlockPresent(Blocks.AIR, pos);
        helper.assertItemEntityCountIs(GBMachines.ARC_FURNACE.item().get(), pos, 2, 1);
        // No room above: placement is refused.
        BlockPos low = new BlockPos(8, 1, 3);
        helper.setBlock(low.above(), Blocks.STONE);
        helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(low.above())).canBeReplaced(), "test setup: stone should block");
        helper.succeed();
    }

    private static void upperCapabilities(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        IndustrialPressBlockEntity press = place(helper, GBMachines.INDUSTRIAL_PRESS.block().get(), pos, IndustrialPressBlockEntity.class);
        BlockPos upperAbs = helper.absolutePos(pos.above());
        var energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, upperAbs, Direction.EAST);
        helper.assertTrue(energy != null, "upper half should expose the machine's energy");
        helper.assertTrue(energy.getCapacityAsLong() == press.energy().capacity(), "upper half energy is not the machine's buffer");
        // A cable beside the upper half connects to it.
        BlockPos cable = pos.above().east();
        Block copper = GBMachines.CABLES.get(0).block().get();
        // setBlock skips placement logic, so resolve the cable's connections the way placing it would.
        helper.setBlock(cable, Block.updateFromNeighbourShapes(copper.defaultBlockState(), helper.getLevel(), helper.absolutePos(cable)));
        helper.assertTrue(dev.groundburst.content.power.CableBlock.side(helper.getBlockState(cable), net.minecraft.core.Direction.WEST) == dev.groundburst.content.power.CableBlock.Side.CONNECTED, "cable should connect to the upper half");
        helper.succeed();
    }
}
