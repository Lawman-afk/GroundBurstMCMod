package dev.groundburst.gametest;

import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.GBUpgrades;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.OreCrusherBlockEntity;
import dev.groundburst.content.machine.UpgradeChipItem;
import dev.groundburst.content.resource.GBResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** The upgrade chips (DESIGN.md §7.3): faster for the same power a job, or the same speed on less; three slots, adding up. */
final class UpgradeTests {
    private static final int OUT = 1, ENERGY = 10_000;

    private UpgradeTests() {}

    static void register() {
        GBGameTests.test("upgrade_chips_speed_and_save_power", GBGameTests.FLOOR_16, 320, UpgradeTests::chips);
        GBGameTests.test("upgrade_slots_take_three_chips", GBGameTests.FLOOR_16, 20, UpgradeTests::slot);
    }

    private static OreCrusherBlockEntity crusher(GameTestHelper helper, BlockPos pos, UpgradeChipItem.Kind kind, int chips) {
        helper.setBlock(pos, GBMachines.ORE_CRUSHER.block().get());
        OreCrusherBlockEntity crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        crusher.energy().generate(ENERGY);
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_IRON));
        for (int i = 0; i < chips; i++) {
            crusher.upgrades().setItem(i, new ItemStack(GBUpgrades.chip(kind, UpgradeChipItem.Tier.QUANTUM).get()));
        }
        return crusher;
    }

    /**
     * Four crushers on raw iron (200 ticks at 20 FE: 4,000 FE a job): one plain, one with a quantum speed chip (40%
     * faster: done in 5/7 the time, on the same power), one with a quantum efficiency chip (as slow, on 20% less), and one
     * with three quantum speed chips (120% faster: their chips add up).
     */
    private static void chips(GameTestHelper helper) {
        OreCrusherBlockEntity plain = crusher(helper, new BlockPos(3, 1, 3), null, 0);
        OreCrusherBlockEntity speed = crusher(helper, new BlockPos(7, 1, 3), UpgradeChipItem.Kind.SPEED, 1);
        OreCrusherBlockEntity saver = crusher(helper, new BlockPos(11, 1, 3), UpgradeChipItem.Kind.EFFICIENCY, 1);
        OreCrusherBlockEntity triple = crusher(helper, new BlockPos(7, 1, 9), UpgradeChipItem.Kind.SPEED, 3);
        long start = helper.getTick();
        long[] done = {-1, -1, -1, -1};
        int[] used = new int[4];
        OreCrusherBlockEntity[] crushers = {plain, speed, saver, triple};
        helper.onEachTick(() -> {
            for (int i = 0; i < 4; i++) {
                if (done[i] < 0 && crushers[i].inventory().getItem(OUT).is(GBResources.IRON_DUST.get())) {
                    done[i] = helper.getTick() - start;
                    used[i] = ENERGY - crushers[i].energy().stored();
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0] > 0 && done[1] > 0 && done[2] > 0 && done[3] > 0,
                    "all four done: " + done[0] + " " + done[1] + " " + done[2] + " " + done[3]);
            double ratio = done[1] / (double) done[0];
            helper.assertTrue(Math.abs(ratio - 1 / 1.4) < 0.03, "the speed chip's crusher 40% faster: " + done[1] + " ticks to " + done[0]);
            helper.assertTrue(Math.abs(used[1] - used[0]) <= used[0] * 0.03, "on the same power a job: " + used[1] + " FE to " + used[0]);
            helper.assertTrue(Math.abs(done[2] - done[0]) <= 2, "the efficiency chip's as fast: " + done[2] + " ticks to " + done[0]);
            helper.assertTrue(Math.abs(used[2] - used[0] * 0.8) <= used[0] * 0.03, "on 20% less power: " + used[2] + " FE to " + used[0]);
            double triple3 = done[3] / (double) done[0];
            helper.assertTrue(Math.abs(triple3 - 1 / 2.2) < 0.03, "three speed chips 120% faster: " + done[3] + " ticks to " + done[0]);
            helper.assertTrue(Math.abs(used[3] - used[0]) <= used[0] * 0.03, "on the same power a job: " + used[3] + " FE to " + used[0]);
        });
    }

    /**
     * The upgrade slots take a chip each and nothing else; chips shift-clicked from the player's inventory go into them
     * one a click (never the machine's own slots) until all three are full; their effects add up; the machine drops its
     * chips when it is broken.
     */
    private static void slot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GBMachines.ORE_CRUSHER.block().get());
        OreCrusherBlockEntity crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        ItemStack chip = new ItemStack(GBUpgrades.chip(UpgradeChipItem.Kind.BALANCED, UpgradeChipItem.Tier.MILITARY).get(), 4);
        helper.assertTrue(crusher.acceptsUpgrades(), "a crusher takes chips");
        helper.assertTrue(crusher.upgrades().getContainerSize() == 3, "in three slots: " + crusher.upgrades().getContainerSize());
        helper.assertTrue(crusher.upgrades().canPlaceItem(2, chip), "the slots take a chip");
        helper.assertTrue(!crusher.upgrades().canPlaceItem(0, new ItemStack(Items.RAW_IRON)), "and nothing else");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, chip.copy());
        MachineMenu menu = (MachineMenu) crusher.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu != null && menu.upgradeSlot() >= 0, "its menu has the upgrade slots");
        int hotbar0 = menu.upgradeSlot() - 9;                  // the hotbar's first slot: the last nine before them
        menu.quickMoveStack(player, hotbar0);
        ItemStack fitted = crusher.upgrades().getItem(0);
        helper.assertTrue(fitted.is(chip.getItem()) && fitted.getCount() == 1, "one chip shift-clicked into the first slot: " + fitted);
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 3, "the other three left: " + player.getInventory().getItem(0));
        helper.assertTrue(crusher.inventory().getItem(0).isEmpty(), "none in the crusher's own slots");
        helper.assertTrue(Math.abs(crusher.workSpeed() - 1.15f) < 1e-4 && Math.abs(crusher.workEnergyFactor() - 1.15f * 0.925f) < 1e-4,
                "a military balanced chip: 15% faster on 7.5% less: " + crusher.workSpeed() + " " + crusher.workEnergyFactor());
        menu.quickMoveStack(player, hotbar0);
        menu.quickMoveStack(player, hotbar0);
        ItemStack none = menu.quickMoveStack(player, hotbar0);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(crusher.upgrades().getItem(i).getCount() == 1, "a chip in slot " + i + ": " + crusher.upgrades().getItem(i));
        }
        helper.assertTrue(none.isEmpty() && player.getInventory().getItem(0).getCount() == 1,
                "the fourth has nowhere to go: " + player.getInventory().getItem(0));
        helper.assertTrue(Math.abs(crusher.workSpeed() - 1.45f) < 1e-4 && Math.abs(crusher.workEnergyFactor() - 1.45f * 0.775f) < 1e-4,
                "three add up: 45% faster on 22.5% less: " + crusher.workSpeed() + " " + crusher.workEnergyFactor());
        helper.destroyBlock(pos);
        helper.assertItemEntityPresent(chip.getItem(), pos, 2);
        helper.succeed();
    }
}
