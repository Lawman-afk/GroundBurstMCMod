package dev.groundzero.gametest;

import dev.groundzero.content.chem.GZChemicals;
import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.CryogenicCondenserBlockEntity;
import dev.groundzero.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.SideConfig.Face;
import dev.groundzero.content.machine.TallMachineBlock;
import dev.groundzero.content.part.GZParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Phase 4 slice 3: machine tanks (buckets, pipes, per-face fluid switch), Electrolytic Separator, Cryogenic Condenser. */
final class FluidMachineTests {
    private FluidMachineTests() {}

    static void register() {
        GZGameTests.test("tank_drains_bucket_slot", GZGameTests.FLOOR_16, 40, FluidMachineTests::bucketSlot);
        GZGameTests.test("tank_pipe_fill_and_face_switch", GZGameTests.FLOOR_16, 20, FluidMachineTests::pipeFill);
        GZGameTests.test("separator_makes_deuterium", GZGameTests.FLOOR_16, 500, FluidMachineTests::deuterium);
        GZGameTests.test("condenser_membrane_picks_gas", GZGameTests.FLOOR_16, 400, FluidMachineTests::condenser);
    }

    private static ElectrolyticSeparatorBlockEntity separator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, GZMachines.ELECTROLYTIC_SEPARATOR.block().get());
        return helper.getBlockEntity(pos, ElectrolyticSeparatorBlockEntity.class);
    }

    private static void bucketSlot(GameTestHelper helper) {
        ElectrolyticSeparatorBlockEntity sep = separator(helper, new BlockPos(3, 1, 3));
        helper.assertTrue(sep.inventory().canPlaceItem(0, new ItemStack(Items.WATER_BUCKET)), "the fill slot must take a water bucket");
        helper.assertTrue(!sep.inventory().canPlaceItem(0, new ItemStack(Items.STONE)), "the fill slot only takes fluid containers");
        sep.inventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        helper.succeedWhen(() -> {
            helper.assertTrue(sep.tank(0).amount() == 1000, "one bucket should give 1000 mB, have " + sep.tank(0).amount());
            helper.assertTrue(sep.inventory().getItem(1).is(Items.BUCKET), "the emptied bucket comes out of the second slot");
        });
    }

    private static void pipeFill(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        ElectrolyticSeparatorBlockEntity sep = separator(helper, pos);
        var level = helper.getLevel();
        var handler = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "a machine with a tank exposes it to pipes");
        try (var tx = Transaction.openRoot()) {
            int in = handler.insert(0, FluidResource.of(Fluids.WATER), 5000, tx);
            helper.assertTrue(in == 5000, "pipes should fill 5000 mB, filled " + in);
            helper.assertTrue(handler.insert(0, FluidResource.of(Fluids.LAVA), 100, tx) == 0, "the water tank refuses lava");
            helper.assertTrue(handler.extract(0, FluidResource.of(Fluids.WATER), 100, tx) == 0, "pipes cannot drain the tank");
            tx.commit();
        }
        helper.assertTrue(sep.tank(0).amount() == 5000, "tank should hold 5000 mB");
        sep.changeSides(c -> c.toggleFluid(Face.TOP));
        helper.assertTrue(level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP) == null, "fluid switched off on top");
        helper.succeed();
    }

    private static void deuterium(GameTestHelper helper) {
        ElectrolyticSeparatorBlockEntity sep = separator(helper, new BlockPos(3, 1, 3));
        sep.energy().generate(30_000);
        try (var tx = Transaction.openRoot()) {
            sep.tank(0).insert(0, FluidResource.of(Fluids.WATER), 10_000, tx);
            tx.commit();
        }
        sep.inventory().setItem(0, new ItemStack(GZChemicals.EMPTY_GAS_CANISTER.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(sep.tank(0).amount() == 2_000, "8000 mB of water should be used, tank has " + sep.tank(0).amount());
            helper.assertTrue(sep.inventory().getItem(1).is(GZChemicals.DEUTERIUM_CANISTER.get()), "the gas canister is filled from the product tank");
            helper.assertTrue(sep.tank(1).isEmpty(), "filling the canister takes the 1000 mB");
        });
    }

    private static void condenser(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GZMachines.CRYOGENIC_CONDENSER.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        CryogenicCondenserBlockEntity cond = helper.getBlockEntity(pos, CryogenicCondenserBlockEntity.class);
        cond.energy().generate(50_000);
        cond.inventory().setItem(0, new ItemStack(GZParts.NITROGEN_MEMBRANE.get()));
        cond.inventory().setItem(1, new ItemStack(GZParts.EMPTY_CANISTER.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(cond.inventory().getItem(2).is(GZChemicals.LIQUID_NITROGEN_CANISTER.get()), "nitrogen membrane should make LN2");
            helper.assertTrue(cond.inventory().getItem(0).is(GZParts.NITROGEN_MEMBRANE.get()), "the membrane is not consumed");
        });
    }
}
