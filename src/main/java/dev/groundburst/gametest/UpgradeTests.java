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

/** The upgrade chips (DESIGN.md §7.3): faster for the same power a job, or the same speed on less; one chip, in its slot. */
final class UpgradeTests {
    private static final int OUT = 1, ENERGY = 10_000;

    private UpgradeTests() {}

    static void register() {
        GBGameTests.test("upgrade_chips_speed_and_save_power", GBGameTests.FLOOR_16, 320, UpgradeTests::chips);
        GBGameTests.test("upgrade_slot_takes_one_chip", GBGameTests.FLOOR_16, 20, UpgradeTests::slot);
    }

    private static OreCrusherBlockEntity crusher(GameTestHelper helper, BlockPos pos, UpgradeChipItem.Kind kind) {
        helper.setBlock(pos, GBMachines.ORE_CRUSHER.block().get());
        OreCrusherBlockEntity crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        crusher.energy().generate(ENERGY);
        crusher.inventory().setItem(0, new ItemStack(Items.RAW_IRON));
        if (kind != null) {
            crusher.upgrades().setItem(0, new ItemStack(GBUpgrades.chip(kind, UpgradeChipItem.Tier.QUANTUM).get()));
        }
        return crusher;
    }

    /**
     * Three crushers on raw iron (200 ticks at 20 FE: 4,000 FE a job): one plain, one with a quantum speed chip (40%
     * faster: done in 5/7 the time, on the same power), one with a quantum efficiency chip (as slow, on 40% less).
     */
    private static void chips(GameTestHelper helper) {
        OreCrusherBlockEntity plain = crusher(helper, new BlockPos(3, 1, 3), null);
        OreCrusherBlockEntity speed = crusher(helper, new BlockPos(7, 1, 3), UpgradeChipItem.Kind.SPEED);
        OreCrusherBlockEntity saver = crusher(helper, new BlockPos(11, 1, 3), UpgradeChipItem.Kind.EFFICIENCY);
        long start = helper.getTick();
        long[] done = {-1, -1, -1};
        int[] used = new int[3];
        OreCrusherBlockEntity[] crushers = {plain, speed, saver};
        helper.onEachTick(() -> {
            for (int i = 0; i < 3; i++) {
                if (done[i] < 0 && crushers[i].inventory().getItem(OUT).is(GBResources.IRON_DUST.get())) {
                    done[i] = helper.getTick() - start;
                    used[i] = ENERGY - crushers[i].energy().stored();
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0] > 0 && done[1] > 0 && done[2] > 0, "all three done: " + done[0] + " " + done[1] + " " + done[2]);
            double ratio = done[1] / (double) done[0];
            helper.assertTrue(Math.abs(ratio - 1 / 1.4) < 0.03, "the speed chip's crusher 40% faster: " + done[1] + " ticks to " + done[0]);
            helper.assertTrue(Math.abs(used[1] - used[0]) <= used[0] * 0.03, "on the same power a job: " + used[1] + " FE to " + used[0]);
            helper.assertTrue(Math.abs(done[2] - done[0]) <= 2, "the efficiency chip's as fast: " + done[2] + " ticks to " + done[0]);
            helper.assertTrue(Math.abs(used[2] - used[0] * 0.6) <= used[0] * 0.03, "on 40% less power: " + used[2] + " FE to " + used[0]);
        });
    }

    /**
     * The upgrade slot takes one chip and nothing else; a chip shift-clicked from the player's inventory goes into it
     * (not the machine's own slots); the machine drops its chip when it is broken.
     */
    private static void slot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GBMachines.ORE_CRUSHER.block().get());
        OreCrusherBlockEntity crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        ItemStack chip = new ItemStack(GBUpgrades.chip(UpgradeChipItem.Kind.BALANCED, UpgradeChipItem.Tier.MILITARY).get(), 3);
        helper.assertTrue(crusher.acceptsUpgrades(), "a crusher takes a chip");
        helper.assertTrue(crusher.upgrades().canPlaceItem(0, chip), "the slot takes a chip");
        helper.assertTrue(!crusher.upgrades().canPlaceItem(0, new ItemStack(Items.RAW_IRON)), "and nothing else");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, chip.copy());
        MachineMenu menu = (MachineMenu) crusher.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu != null && menu.upgradeSlot() >= 0, "its menu has the upgrade slot");
        int hotbar0 = menu.upgradeSlot() - 9;                  // the hotbar's first slot: the last nine before it
        menu.quickMoveStack(player, hotbar0);
        ItemStack fitted = crusher.upgrades().getItem(0);
        helper.assertTrue(fitted.is(chip.getItem()) && fitted.getCount() == 1, "one chip shift-clicked into its slot: " + fitted);
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2, "the other two left: " + player.getInventory().getItem(0));
        helper.assertTrue(crusher.inventory().getItem(0).isEmpty(), "none in the crusher's own slots");
        helper.assertTrue(Math.abs(crusher.workSpeed() - 1.15f) < 1e-4 && Math.abs(crusher.workEnergyFactor() - 1.15f * 0.85f) < 1e-4,
                "a military balanced chip: 15% faster on 15% less: " + crusher.workSpeed() + " " + crusher.workEnergyFactor());
        helper.destroyBlock(pos);
        helper.assertItemEntityPresent(chip.getItem(), pos, 2);
        helper.succeed();
    }
}
