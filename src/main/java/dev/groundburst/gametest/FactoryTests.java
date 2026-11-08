package dev.groundburst.gametest;

import java.util.Set;
import java.util.TreeSet;

import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.machine.CircuitFabricatorBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.part.GBParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.groundburst.port.transfer.Capabilities;
import dev.groundburst.port.transfer.item.ItemResource;
import dev.groundburst.port.transfer.transaction.Transaction;

/** Machines fed by pipes as a factory feeds them: one slot an ingredient, and a whole advanced-circuit line on its own. */
final class FactoryTests {
    private FactoryTests() {}

    static void register() {
        GBGameTests.test("pipes_keep_a_slot_for_each_ingredient", GBGameTests.FLOOR_16, 300, FactoryTests::slotEach);
        GBGameTests.test("circuit_line_makes_advanced_circuits", GBGameTests.FLOOR_16, 2_400, FactoryTests::circuitLine);
    }

    /**
     * Pipes and hoppers put each ingredient into one input slot: more copper wire than a slot holds leaves the
     * fabricator's other slots for the redstone and plastic (it used to fill all four and wait for ever), and it runs.
     */
    private static void slotEach(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GBMachines.CIRCUIT_FABRICATOR.block().get());
        CircuitFabricatorBlockEntity fab = ((CircuitFabricatorBlockEntity) helper.getBlockEntity(pos));
        var top = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(top != null, "its top takes items");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(top.insert(ItemResource.of(GBParts.COPPER_WIRE.get()), 200, tx) == 64, "one slot of copper wire and no more");
            helper.assertTrue(top.insert(ItemResource.of(Items.REDSTONE), 64, tx) == 64, "redstone has a slot of its own");
            helper.assertTrue(top.insert(ItemResource.of(GBChemicals.PLASTIC_SHEET.get()), 64, tx) == 64, "and plastic");
            helper.assertTrue(top.insert(ItemResource.of(GBParts.COPPER_WIRE.get()), 64, tx) == 0, "still no second slot of wire");
            tx.commit();
        }
        fab.energy().generate(20_000);
        helper.succeedWhen(() -> {
            ItemStack out = fab.inventory().getItem(4);
            helper.assertTrue(out.is(GBParts.BASIC_CIRCUIT.get()) && out.getCount() >= 2, "it makes basic circuits, has " + out);
        });
    }

    /**
     * The advanced-circuit line ({@link CircuitLine}): condenser, puller, press, Clean Room and both fabricators each run
     * on what the pipes bring and the generators' power, and advanced circuits reach the chest in front of it (about four
     * a minute once the microchips flow; the first from the work in hand).
     */
    private static void circuitLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(2, 1, 4));                  // the hall's x 0..11, z -2..7 on the plot
        CircuitLine.build(level, o);
        Set<String> ran = new TreeSet<>();
        helper.onEachTick(() -> CircuitLine.MACHINES.forEach((name, at) -> {
            if (level.getBlockEntity(o.offset(at)) instanceof MachineBlockEntity m && m.status() == MachineStatus.RUNNING) {
                ran.add(name);
            }
        }));
        helper.runAtTickTime(2_350, () -> {
            int made = CircuitLine.count(level, o.offset(CircuitLine.OUTPUT), GBParts.ADVANCED_CIRCUIT.get());
            dev.groundburst.GroundBurst.LOGGER.info("Circuit line test: {} advanced circuits in 117 s; ran: {}", made, ran);
            helper.assertTrue(ran.size() == CircuitLine.MACHINES.size(), "every machine ran, ran: " + ran);
            helper.assertTrue(made >= 4, "advanced circuits in its chest after almost two minutes: " + made);
            helper.succeed();
        });
    }
}
