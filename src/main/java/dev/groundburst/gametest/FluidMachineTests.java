package dev.groundburst.gametest;

import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.machine.CryogenicCondenserBlockEntity;
import dev.groundburst.content.machine.ElectrolyticSeparatorBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.SideConfig.Face;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.part.GBParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        GBGameTests.test("bucket_click_fills_and_sneak_click_draws", GBGameTests.FLOOR_16, 40, FluidMachineTests::bucketClick);
        GBGameTests.test("tank_pipe_fill_and_face_switch", GBGameTests.FLOOR_16, 20, FluidMachineTests::pipeFill);
        GBGameTests.test("separator_makes_deuterium", GBGameTests.FLOOR_16, 500, FluidMachineTests::deuterium);
        GBGameTests.test("condenser_membrane_picks_gas", GBGameTests.FLOOR_16, 400, FluidMachineTests::condenser);
    }

    private static ElectrolyticSeparatorBlockEntity separator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, GBMachines.ELECTROLYTIC_SEPARATOR.block().get());
        return helper.getBlockEntity(pos, ElectrolyticSeparatorBlockEntity.class);
    }

    /** Right-click a machine with a water bucket eight times: 8000 mB in, buckets emptied. Sneak-click draws the product. */
    private static void bucketClick(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 1, 3);
        ElectrolyticSeparatorBlockEntity sep = separator(helper, rel);
        BlockPos pos = helper.absolutePos(rel);
        var level = helper.getLevel();
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for (int i = 0; i < 8; i++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the bucket is emptied, holding " + player.getMainHandItem());
        }
        helper.assertTrue(sep.tank(0).amount() == 8_000, "eight buckets give 8000 mB, have " + sep.tank(0).amount());
        // A plain click with an empty bucket must not draw anything out.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        sep.tank(1).fillInternal(new net.neoforged.neoforge.fluids.FluidStack(GBFluids.DEUTERIUM.source().get(), 1_000));
        ItemStack canister = new ItemStack(GBChemicals.EMPTY_GAS_CANISTER.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, canister);
        level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(sep.tank(1).amount() == 1_000, "a plain click does not draw from the product tank");
        player.setShiftKeyDown(true);
        level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(sep.tank(1).isEmpty(), "sneak-click fills the canister from the product tank");
        helper.assertTrue(player.getInventory().countItem(GBChemicals.DEUTERIUM_CANISTER.get()) == 1, "the player gets a deuterium canister");
        helper.succeed();
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
        helper.succeedWhen(() -> {
            helper.assertTrue(sep.tank(0).amount() == 2_000, "8000 mB of water should be used, tank has " + sep.tank(0).amount());
            helper.assertTrue(sep.tank(1).amount() == 1_000 && sep.tank(1).fluid().getFluid() == GBFluids.DEUTERIUM.source().get(),
                    "1000 mB of deuterium in the product tank");
        });
    }

    private static void condenser(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GBMachines.CRYOGENIC_CONDENSER.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        CryogenicCondenserBlockEntity cond = helper.getBlockEntity(pos, CryogenicCondenserBlockEntity.class);
        cond.energy().generate(50_000);
        cond.inventory().setItem(0, new ItemStack(GBParts.NITROGEN_MEMBRANE.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(cond.tank(0).fluid().getFluid() == GBFluids.LIQUID_NITROGEN.source().get(), "nitrogen membrane should make LN2");
            helper.assertTrue(cond.inventory().getItem(0).is(GBParts.NITROGEN_MEMBRANE.get()), "the membrane is not consumed");
        });
    }
}
