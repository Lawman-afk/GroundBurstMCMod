package dev.groundzero.gametest;

import dev.groundzero.content.assembly.AssemblyBenchBlockEntity;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.WideMachineBlock;
import dev.groundzero.content.ordnance.GZOrdnance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Assembly Bench's pages (after the in-game look): the page is the bench's own, saved with it; shift-clicks land on
 * the page shown, and pipes feed and empty only that page's slots.
 */
final class BenchPageTests {
    private BenchPageTests() {}

    static void register() {
        GZGameTests.test("bench_page_steers_shift_clicks_and_pipes", GZGameTests.FLOOR_16, 20, BenchPageTests::page);
    }

    private static AssemblyBenchBlockEntity bench(GameTestHelper helper) {
        BlockPos rel = new BlockPos(8, 2, 8);
        Block block = GZAssembly.ASSEMBLY_BENCH.block().get();
        helper.setBlock(rel, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        helper.setBlock(rel.west(), block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH)
                .setValue(WideMachineBlock.PART, WideMachineBlock.Part.SIDE));
        return (AssemblyBenchBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
    }

    private static int insert(AssemblyBenchBlockEntity bench, ItemStack stack) {
        try (var tx = Transaction.openRoot()) {
            int in = bench.portItems().insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return in;
        }
    }

    private static int extract(AssemblyBenchBlockEntity bench, ItemStack stack) {
        try (var tx = Transaction.openRoot()) {
            int out = bench.portItems().extract(ItemResource.of(stack), 1, tx);
            tx.commit();
            return out;
        }
    }

    private static void page(GameTestHelper helper) {
        AssemblyBenchBlockEntity b = bench(helper);
        helper.assertTrue(b.page() == AssemblyBenchBlockEntity.JOB_WARHEAD, "a new bench is on its warhead page");
        ItemStack warhead = new ItemStack(GZOrdnance.HE_WARHEAD.item().get());
        ItemStack core = new ItemStack(GZAssembly.HE_CHARGE.get());
        // Pipes: on the warhead page a core goes in and a warhead finds no slot (its missile slot is on the other page).
        helper.assertTrue(insert(b, warhead.copy()) == 0, "no slot for a piped warhead on the warhead page");
        helper.assertTrue(insert(b, core.copy()) == 1 && !b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_CORE).isEmpty(), "a piped core goes in");
        helper.assertTrue(b.setPage(AssemblyBenchBlockEntity.JOB_MISSILE) && b.page() == AssemblyBenchBlockEntity.JOB_MISSILE, "turned");
        helper.assertTrue(insert(b, warhead.copy()) == 1 && !b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD).isEmpty(),
                "on the missile page a piped warhead goes into the missile's warhead slot");
        helper.assertTrue(insert(b, core.copy()) == 0, "and a core finds no slot");
        // Outputs: only the page's own is emptied.
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_WARHEAD, new ItemStack(GZOrdnance.HEAVY_WARHEAD.item().get()));
        helper.assertTrue(extract(b, new ItemStack(GZOrdnance.HEAVY_WARHEAD.item().get())) == 0, "the warhead page's output stays put");
        // Shift-clicks land on the page shown.
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD, ItemStack.EMPTY);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_WARHEAD, ItemStack.EMPTY);
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        MachineMenu menu = (MachineMenu) b.createMenu(1, player.getInventory(), player);
        int hotbar0 = menu.layout().slots().size() + 27;
        player.getInventory().setItem(0, warhead.copy());
        menu.quickMoveStack(player, hotbar0);
        helper.assertTrue(!b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD).isEmpty()
                && b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD).isEmpty(), "a shift-clicked warhead goes to the missile page's slot");
        b.setPage(AssemblyBenchBlockEntity.JOB_WARHEAD);
        b.inventory().setItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD, ItemStack.EMPTY);
        player.getInventory().setItem(0, warhead.copy());
        menu.quickMoveStack(player, hotbar0);
        helper.assertTrue(!b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_WARHEAD).isEmpty()
                && b.inventory().getItem(AssemblyBenchBlockEntity.SLOT_MISSILE_WARHEAD).isEmpty(), "and on the warhead page to its take-apart slot");
        helper.succeed();
    }
}
