package dev.groundzero.gametest;

import java.util.List;

import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.machine.DecontaminationShowerBlockEntity;
import dev.groundzero.content.machine.GZMachines;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.OreCrusherBlockEntity;
import dev.groundzero.content.machine.TallMachineBlock;
import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.part.GZParts;
import dev.groundzero.content.power.BatteryChargerBlockEntity;
import dev.groundzero.content.power.BatteryItem;
import dev.groundzero.content.power.CapacitorBankBlockEntity;
import dev.groundzero.content.power.DieselGeneratorBlockEntity;
import dev.groundzero.content.power.RtgBlockEntity;
import dev.groundzero.registry.GZDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Phase 4 slice 6: Diesel Generator, RTG, Capacitor Banks, Battery Charger, Decontamination Shower. */
final class PowerStorageTests {
    private PowerStorageTests() {}

    static void register() {
        GZGameTests.test("diesel_burns_fuel_and_powers_neighbours", GZGameTests.FLOOR_16, 80, PowerStorageTests::diesel);
        GZGameTests.test("rtg_decays_its_slug", GZGameTests.FLOOR_16, 40, PowerStorageTests::rtg);
        GZGameTests.test("capacitor_bank_front_outputs_sides_input", GZGameTests.FLOOR_16, 40, PowerStorageTests::bankFaces);
        GZGameTests.test("capacitor_bank_keeps_charge_when_broken", GZGameTests.FLOOR_16, 20, PowerStorageTests::bankKeepsCharge);
        GZGameTests.test("charger_fills_battery", GZGameTests.FLOOR_16, 80, PowerStorageTests::charger);
        GZGameTests.test("shower_washes_off_poison_and_fire", GZGameTests.FLOOR_16, 60, PowerStorageTests::shower);
    }

    /** Synthetic fuel burns into FE that flows into a crusher next door; water is refused. */
    private static void diesel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GZMachines.DIESEL_GENERATOR.block().get());
        DieselGeneratorBlockEntity gen = helper.getBlockEntity(pos, DieselGeneratorBlockEntity.class);
        helper.setBlock(pos.east(), GZMachines.ORE_CRUSHER.block().get());
        OreCrusherBlockEntity crusher = helper.getBlockEntity(pos.east(), OreCrusherBlockEntity.class);
        var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP);
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(handler.insert(0, FluidResource.of(Fluids.WATER), 1000, tx) == 0, "water is not fuel");
            helper.assertTrue(handler.insert(0, FluidResource.of(GZFluids.SYNTHETIC_FUEL.source().get()), 1000, tx) == 1000, "synthetic fuel goes in");
            tx.commit();
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(gen.tank(0).amount() < 1000, "fuel is burnt");
            helper.assertTrue(crusher.energy().stored() > 0, "the crusher next door gets power");
            helper.assertTrue(gen.status() == MachineStatus.RUNNING, "running, status " + gen.status());
        });
    }

    /** An enriched uranium ingot goes in, 40 FE/t comes out, and the slug's time runs down. */
    private static void rtg(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GZMachines.RTG.block().get());
        RtgBlockEntity rtg = helper.getBlockEntity(pos, RtgBlockEntity.class);
        rtg.inventory().setItem(0, new ItemStack(GZNuclear.ENRICHED_URANIUM_INGOT.get()));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(rtg.inventory().getItem(0).isEmpty(), "the slug is loaded");
            helper.assertTrue(rtg.energy().stored() >= 19 * RtgBlockEntity.RATE, "about 40 FE a tick, have " + rtg.energy().stored());
            helper.assertTrue(rtg.decayLeft() > 47_000 && rtg.decayLeft() < 48_000, "the slug decays, " + rtg.decayLeft() + " ticks left");
            helper.succeed();
        });
    }

    /** Power goes in on every side but the front; the front only gives it out, pushing into what it faces. */
    private static void bankFaces(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GZMachines.CAPACITOR_BANKS.get(0).block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        CapacitorBankBlockEntity bank = helper.getBlockEntity(pos, CapacitorBankBlockEntity.class);
        var level = helper.getLevel();
        var side = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.EAST);
        var front = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.NORTH);
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(side.insert(500, tx) == 500, "the side takes power");
            helper.assertTrue(side.extract(100, tx) == 0, "the side gives none");
            helper.assertTrue(front.insert(500, tx) == 0, "the front takes none");
            helper.assertTrue(front.extract(100, tx) == 100, "the front gives power");
            tx.commit();
        }
        helper.assertTrue(bank.energy().stored() == 400, "500 in, 100 out, have " + bank.energy().stored());
        // A crusher placed in front of it gets pushed power.
        helper.setBlock(pos.north(), GZMachines.ORE_CRUSHER.block().get());
        OreCrusherBlockEntity crusher = helper.getBlockEntity(pos.north(), OreCrusherBlockEntity.class);
        helper.succeedWhen(() -> helper.assertTrue(crusher.energy().stored() > 0, "the bank pushes power out of its front"));
    }

    /** Broken with power in it, the bank drops an item carrying the charge; placing it restores the charge. */
    private static void bankKeepsCharge(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        Block block = GZMachines.CAPACITOR_BANKS.get(1).block().get();
        helper.setBlock(pos, block);
        CapacitorBankBlockEntity bank = helper.getBlockEntity(pos, CapacitorBankBlockEntity.class);
        bank.energy().generate(123_456);
        BlockState state = helper.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), bank);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), "the bank drops itself, got " + drops);
        ItemStack item = drops.get(0);
        helper.assertTrue(item.getOrDefault(GZDataComponents.ENERGY.get(), 0) == 123_456, "the item carries the charge");
        BlockPos other = new BlockPos(6, 1, 3);
        helper.setBlock(other, block);
        CapacitorBankBlockEntity placed = helper.getBlockEntity(other, CapacitorBankBlockEntity.class);
        placed.applyComponentsFromItemStack(item);
        helper.assertTrue(placed.energy().stored() == 123_456, "placing it restores the charge, have " + placed.energy().stored());
        helper.succeed();
    }

    /** An empty battery charges to full and moves to the output slot. */
    private static void charger(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GZMachines.BATTERY_CHARGER.block().get());
        BatteryChargerBlockEntity charger = helper.getBlockEntity(pos, BatteryChargerBlockEntity.class);
        charger.energy().generate(BatteryChargerBlockEntity.CAPACITY);
        helper.assertTrue(charger.inventory().canPlaceItem(0, new ItemStack(GZParts.BATTERY.get())), "a battery fits the cradle");
        helper.assertFalse(charger.inventory().canPlaceItem(0, new ItemStack(GZParts.STEEL_PLATE.get())), "a plate does not");
        charger.inventory().setItem(0, new ItemStack(GZParts.BATTERY.get()));
        helper.succeedWhen(() -> {
            ItemStack out = charger.inventory().getItem(1);
            helper.assertTrue(out.is(GZParts.BATTERY.get()), "the charged battery moves to the output");
            helper.assertTrue(BatteryItem.stored(out) == BatteryItem.CAPACITY, "it is full, " + BatteryItem.stored(out));
        });
    }

    /** A burning, poisoned pig in the powered, watered booth comes out clean, using water. */
    private static void shower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GZMachines.DECONTAMINATION_SHOWER.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        DecontaminationShowerBlockEntity shower = helper.getBlockEntity(pos, DecontaminationShowerBlockEntity.class);
        shower.energy().generate(10_000);
        shower.tank(0).fillInternal(new FluidStack(Fluids.WATER, 1_000));
        Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, pos);
        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        pig.igniteForSeconds(10);
        helper.succeedWhen(() -> {
            helper.assertFalse(pig.hasEffect(MobEffects.POISON), "the poison is washed off");
            helper.assertFalse(pig.isOnFire(), "the fire is out");
            helper.assertTrue(shower.tank(0).amount() < 1_000, "water is used");
        });
    }
}
