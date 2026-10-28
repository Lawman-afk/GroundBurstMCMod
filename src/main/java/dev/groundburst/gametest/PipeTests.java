package dev.groundburst.gametest;

import dev.groundburst.content.pipe.GBPipes;
import dev.groundburst.content.pipe.ItemPipeBlock;
import dev.groundburst.content.pipe.ItemPipeTier;
import dev.groundburst.content.pipe.PipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Item pipes: pulling, tier rates, the weakest pipe setting a run's rate, and never sending items back. */
final class PipeTests {
    private PipeTests() {}

    static void register() {
        GBGameTests.test("item_pipe_pulls_at_its_rate", GBGameTests.FLOOR_16, 60, PipeTests::pullRate);
        GBGameTests.test("item_pipe_weakest_pipe_sets_rate", GBGameTests.FLOOR_16, 60, PipeTests::weakest);
        GBGameTests.test("item_pipe_takes_from_hoppers_and_machines", GBGameTests.FLOOR_16, 20, PipeTests::insertion);
        GBGameTests.test("item_pipe_never_sends_back", GBGameTests.FLOOR_16, 40, PipeTests::noBackflow);
    }

    /** A chest at x=1, pipes from x=2 to x=4, a chest at x=5; the first pipe pulls from the first chest. */
    private static void line(GameTestHelper helper, int y, Block... pipes) {
        helper.setBlock(new BlockPos(1, y, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(2 + pipes.length, y, 2), Blocks.CHEST);
        for (int i = 0; i < pipes.length; i++) {
            helper.setBlock(new BlockPos(2 + i, y, 2), pipes[i]);
        }
        BlockPos first = helper.absolutePos(new BlockPos(2, y, 2));
        BlockState state = helper.getLevel().getBlockState(first);
        helper.assertTrue(PipeBlock.mode(state, Direction.WEST) == PipeBlock.Mode.NORMAL, "the pipe connects to the chest");
        BlockState pulling = state.setValue(PipeBlock.SIDES.get(Direction.WEST), PipeBlock.Mode.PULL);
        helper.getLevel().setBlock(first, pulling, Block.UPDATE_ALL);
        PipeBlock.changed(helper.getLevel(), first, pulling);
    }

    private static Container chest(GameTestHelper helper, BlockPos rel) {
        return (Container) helper.getBlockEntity(rel, net.minecraft.world.level.block.entity.BlockEntity.class);
    }

    private static int count(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            n += c.getItem(i).getCount();
        }
        return n;
    }

    /** Over 40 ticks (four windows) an iron run moves at most 4 x 4 items, a pneumatic run all 64. */
    private static void pullRate(GameTestHelper helper) {
        line(helper, 1, GBPipes.IRON_ITEM_PIPE.block().get(), GBPipes.IRON_ITEM_PIPE.block().get());
        line(helper, 3, GBPipes.PNEUMATIC_ITEM_PIPE.block().get(), GBPipes.PNEUMATIC_ITEM_PIPE.block().get());
        chest(helper, new BlockPos(1, 1, 2)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        chest(helper, new BlockPos(1, 3, 2)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.runAfterDelay(41, () -> {
            int iron = count(chest(helper, new BlockPos(4, 1, 2)));
            int pneumatic = count(chest(helper, new BlockPos(4, 3, 2)));
            int windows = 41 / ItemPipeTier.WINDOW + 1;
            helper.assertTrue(iron > 0 && iron <= windows * ItemPipeTier.IRON.batch(), "iron moves 4 a window, moved " + iron);
            helper.assertTrue(pneumatic == 64, "pneumatic moves the whole stack, moved " + pneumatic);
            helper.assertTrue(count(chest(helper, new BlockPos(1, 1, 2))) == 64 - iron, "nothing is lost or made in the pipe");
            helper.succeed();
        });
    }

    /** A pneumatic run with one iron pipe in it moves at the iron rate. */
    private static void weakest(GameTestHelper helper) {
        line(helper, 1, GBPipes.PNEUMATIC_ITEM_PIPE.block().get(), GBPipes.IRON_ITEM_PIPE.block().get(), GBPipes.PNEUMATIC_ITEM_PIPE.block().get());
        chest(helper, new BlockPos(1, 1, 2)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.runAfterDelay(41, () -> {
            int moved = count(chest(helper, new BlockPos(5, 1, 2)));
            helper.assertTrue(moved > 0 && moved <= 5 * ItemPipeTier.IRON.batch(), "the iron pipe limits the run, moved " + moved);
            helper.succeed();
        });
    }

    /** Anything may insert into a pipe (a hopper, a machine's auto-eject): it goes straight on to the far chest. */
    private static void insertion(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), GBPipes.ITEM_PIPE.block().get());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);
        var pipe = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP);
        helper.assertTrue(pipe != null, "the pipe takes items");
        try (Transaction tx = Transaction.openRoot()) {
            int in = pipe.insert(ItemResource.of(Items.IRON_INGOT), 10, tx);
            helper.assertTrue(in == 10, "ten go in, took " + in);
            tx.commit();
        }
        helper.assertTrue(count(chest(helper, new BlockPos(3, 1, 2))) == 10, "and arrive in the chest");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(pipe.insert(ItemResource.of(Items.IRON_INGOT), 64, tx) == ItemPipeTier.STEEL.batch() - 10,
                    "the window's batch is shared");
        }
        helper.succeed();
    }

    /** A chest that a run pulls from is never refilled by the same run, even when the run also touches it elsewhere. */
    private static void noBackflow(GameTestHelper helper) {
        line(helper, 1, GBPipes.ITEM_PIPE.block().get(), GBPipes.ITEM_PIPE.block().get());
        // A branch up from the first pipe and back over the source chest, joining the run to it a second time.
        helper.setBlock(new BlockPos(2, 2, 2), GBPipes.ITEM_PIPE.block().get());
        helper.setBlock(new BlockPos(1, 2, 2), GBPipes.ITEM_PIPE.block().get());
        chest(helper, new BlockPos(1, 1, 2)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(count(chest(helper, new BlockPos(4, 1, 2))) == 32, "everything reaches the far chest");
            helper.assertTrue(count(chest(helper, new BlockPos(1, 1, 2))) == 0, "nothing goes back into the source");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 1, 2))).getBlock() instanceof ItemPipeBlock, "pipes stay");
            helper.succeed();
        });
    }
}
