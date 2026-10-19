package dev.groundzero.gametest;

import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.IndustrialPressBlockEntity;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.machine.SideConfig.Face;
import dev.groundzero.content.machine.SideConfig.ItemMode;
import dev.groundzero.content.machine.TallMachineBlock;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.resource.GZResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Side configuration: per-face item modes, energy switches, auto-eject; plus the press showing its die. */
final class SideConfigTests {
    private SideConfigTests() {}

    static void register() {
        GZGameTests.test("side_config_item_modes", GZGameTests.FLOOR_16, 20, SideConfigTests::itemModes);
        GZGameTests.test("side_config_energy_switch", GZGameTests.FLOOR_16, 20, SideConfigTests::energySwitch);
        GZGameTests.test("side_config_auto_eject", GZGameTests.FLOOR_16, 300, SideConfigTests::autoEject);
        GZGameTests.test("press_shows_loaded_die", GZGameTests.FLOOR_16, 20, SideConfigTests::pressDie);
        GZGameTests.test("generator_faces_input_only", GZGameTests.FLOOR_16, 20, SideConfigTests::generatorModes);
    }

    private static OreCrusherBlockEntity crusher(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, GZMachines.ORE_CRUSHER.block().get());
        return helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
    }

    private static void itemModes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        crusher.changeSides(c -> {
            while (c.items(Face.BOTTOM) != ItemMode.INPUT) {
                c.cycleItems(Face.BOTTOM, true);
            }
            while (c.items(Face.TOP) != ItemMode.NONE) {
                c.cycleItems(Face.TOP, true);
            }
        });
        var bottom = level.getCapability(Capabilities.Item.BLOCK, abs, Direction.DOWN);
        var top = level.getCapability(Capabilities.Item.BLOCK, abs, Direction.UP);
        helper.assertTrue(bottom != null, "an INPUT face still exposes items");
        crusher.inventory().setItem(1, new ItemStack(GZResources.IRON_DUST.get(), 4));
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int taken = 0;
            for (int i = 0; i < bottom.size(); i++) {
                taken += bottom.extract(i, bottom.getResource(i), 64, tx);
            }
            helper.assertTrue(taken == 0, "an INPUT face must not give out products");
        }
        helper.assertTrue(top == null || top.size() == 0 || noInsert(top), "a NONE face must not accept items");
        helper.succeed();
    }

    private static boolean noInsert(net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> handler) {
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            for (int i = 0; i < handler.size(); i++) {
                if (handler.insert(i, net.neoforged.neoforge.transfer.item.ItemResource.of(Items.RAW_IRON), 1, tx) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void energySwitch(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        BlockState state = helper.getBlockState(pos);
        Direction back = Face.BACK.toDirection(state.getValue(dev.groundzero.content.machine.MachineBlock.FACING));
        var level = helper.getLevel();
        helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), back) != null, "energy on by default");
        crusher.changeSides(c -> c.toggleEnergy(Face.BACK));
        helper.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), back) == null, "energy switched off on the back");
        helper.succeed();
    }

    private static void autoEject(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        OreCrusherBlockEntity crusher = crusher(helper, pos);
        Direction facing = helper.getBlockState(pos).getValue(dev.groundzero.content.machine.MachineBlock.FACING);
        BlockPos chestPos = pos.relative(Face.LEFT.toDirection(facing));
        helper.setBlock(chestPos, Blocks.CHEST);
        crusher.changeSides(c -> {
            for (Face f : Face.values()) {
                while (c.items(f) != (f == Face.LEFT ? ItemMode.OUTPUT : ItemMode.NONE)) {
                    c.cycleItems(f, true);
                }
            }
            c.toggleAutoEject();
        });
        crusher.energy().generate(10_000);
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_IRON));
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            int dust = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(GZResources.IRON_DUST.get())) {
                    dust += chest.getItem(i).getCount();
                }
            }
            helper.assertTrue(dust == 2, "auto-eject should move 2 iron dust into the chest, found " + dust);
        });
    }

    private static void pressDie(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GZMachines.INDUSTRIAL_PRESS.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        IndustrialPressBlockEntity press = helper.getBlockEntity(pos, IndustrialPressBlockEntity.class);
        helper.assertTrue(press.hiddenBones().size() == 4, "no die loaded: all dies hidden");
        press.inventory().setItem(press.catalystSlot(), new ItemStack(GZParts.WIRE_DIE.get()));
        helper.assertTrue(!press.hiddenBones().contains("die_wire") && press.hiddenBones().size() == 3, "wire die should show");
        helper.assertTrue(press.getUpdateTag(helper.getLevel().registryAccess()).contains("items"), "the update tag must carry the die");
        helper.succeed();
    }

    private static void generatorModes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GZMachines.COMBUSTION_GENERATOR.block().get());
        var gen = helper.getBlockEntity(pos, dev.groundzero.content.power.CombustionGeneratorBlockEntity.class);
        helper.assertTrue(gen.allowedItemModes().equals(java.util.EnumSet.of(ItemMode.NONE, ItemMode.INPUT)), "generator only takes fuel in");
        for (Face f : Face.values()) {
            helper.assertTrue(gen.sides().items(f) == ItemMode.INPUT, "default generator faces should be Input, " + f + " is " + gen.sides().items(f));
        }
        java.util.Set<ItemMode> seen = java.util.EnumSet.noneOf(ItemMode.class);
        for (int i = 0; i < 4; i++) {
            gen.changeSides(c -> c.cycleItems(Face.TOP, true, gen.allowedItemModes()));
            seen.add(gen.sides().items(Face.TOP));
        }
        helper.assertTrue(seen.equals(java.util.EnumSet.of(ItemMode.NONE, ItemMode.INPUT)), "cycling must skip Output modes, saw " + seen);
        helper.succeed();
    }
}
