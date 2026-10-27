package dev.groundzero.gametest;

import java.util.List;

import dev.groundzero.content.nuclear.GZEffects;
import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.radiation.Emitters;
import dev.groundzero.content.radiation.GeigerCounterItem;
import dev.groundzero.content.radiation.LeadContainerMenu;
import dev.groundzero.content.radiation.Radiation;
import dev.groundzero.content.radiation.RadiationItems;
import dev.groundzero.content.radiation.RadiationSource;
import dev.groundzero.content.radiation.RadiationSources;
import dev.groundzero.content.radiation.RadiationState;
import dev.groundzero.content.radiation.Shielding;
import dev.groundzero.content.resource.GZResources;
import dev.groundzero.registry.GZDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 12 slice 12a (DESIGN.md §21.8): radiation sources and their decay, shielding by block, radioactive blocks and
 * carried items, the Lead Container, the dose and the sickness it brings, medicine, the Geiger Counter.
 */
final class RadiationTests {
    private RadiationTests() {}

    static void register() {
        GZGameTests.test("radiation_source_halves_and_ends", GZGameTests.FLOOR_16, 20, RadiationTests::decay);
        GZGameTests.test("radiation_shielding_by_block", GZGameTests.FLOOR_16, 20, RadiationTests::shielding);
        GZGameTests.test("radioactive_blocks_dose_nearby", GZGameTests.FLOOR_16, 20, RadiationTests::blocks);
        GZGameTests.test("carried_radioactives_and_the_lead_container", GZGameTests.FLOOR_16, 20, RadiationTests::carried);
        GZGameTests.test("radiation_dose_builds_in_a_source", GZGameTests.FLOOR_16, 80, RadiationTests::builds);
        GZGameTests.test("radiation_sickness_follows_the_dose", GZGameTests.FLOOR_16, 20, RadiationTests::stages);
        GZGameTests.test("radiation_medicine_works", GZGameTests.FLOOR_16, 20, RadiationTests::medicine);
        GZGameTests.test("geiger_counter_charge_alarm_and_emp", GZGameTests.FLOOR_16, 20, RadiationTests::geiger);
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** A source halves every half-life, is under 10 RAD/h when it is said to have lasted, and falls off to its edge. */
    private static void decay(GameTestHelper helper) {
        RadiationSource s = RadiationSource.lasting(Vec3.ZERO, 100, 60_000, 48_000, 1_000, 1);
        helper.assertTrue(Math.abs(s.peakAt(1_000) - 60_000) < 1, "fresh: " + s.peakAt(1_000));
        helper.assertTrue(Math.abs(s.peakAt(1_000 + s.halfLife()) - 30_000) < 30, "halved: " + s.peakAt(1_000 + s.halfLife()));
        float safe = s.peakAt(1_000 + 48_000);
        helper.assertTrue(Math.abs(safe - RadiationSource.SAFE) < 0.5, "under 10 RAD/h when it has lasted: " + safe);
        helper.assertTrue(Math.abs(s.rateAt(1_000, 50) - 15_000) < 1, "a quarter halfway out: " + s.rateAt(1_000, 50));
        helper.assertTrue(s.rateAt(1_000, 100) == 0, "nothing at its edge");
        helper.assertTrue(!s.ended(1_000 + 48_000) && s.ended(1_000 + 3 * 48_000), "ends a while after it is safe");
        helper.succeed();
    }

    /**
     * Open ground reads 1; a one-block stone hut halves it, a lead one stops nearly all of it; two blocks of obsidian a
     * sixteenth. (Rays of 3 blocks, kept inside the plot: past it stand other tests and the barriers.)
     */
    private static void shielding(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        double open = Shielding.ambient(level, helper.absoluteVec(new Vec3(4.5, 2.62, 4.5)), 3);
        helper.assertTrue(Math.abs(open - 1) < 0.01, "open ground: " + open);
        hut(helper, Blocks.STONE);
        double stone = Shielding.ambient(level, helper.absoluteVec(new Vec3(11.5, 3.62, 11.5)), 3);
        helper.assertTrue(stone > 0.4 && stone < 0.6, "a stone hut halves it: " + stone);
        hut(helper, GZResources.LEAD_BLOCK.block().get());
        double lead = Shielding.ambient(level, helper.absoluteVec(new Vec3(11.5, 3.62, 11.5)), 3);
        helper.assertTrue(lead < 0.05, "a lead hut stops nearly all of it: " + lead);
        helper.setBlock(new BlockPos(5, 2, 8), Blocks.OBSIDIAN);
        helper.setBlock(new BlockPos(6, 2, 8), Blocks.OBSIDIAN);
        double through = Shielding.line(level, helper.absoluteVec(new Vec3(3.5, 2.5, 8.5)), helper.absoluteVec(new Vec3(8.5, 2.5, 8.5)), null);
        helper.assertTrue(Math.abs(through - 0.0625) < 0.005, "two blocks of obsidian let a sixteenth through: " + through);
        helper.succeed();
    }

    /** A hut of {@code block} round (11, 2..3, 11): one block thick all round, floor and roof. */
    private static void hut(GameTestHelper helper, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(10, 1, 10, 12, 4, 12)) {
            boolean inside = p.getX() == 11 && p.getZ() == 11 && (p.getY() == 2 || p.getY() == 3);
            helper.setBlock(p, inside ? Blocks.AIR : block);
        }
    }

    /** A Block of Natural Uranium two blocks away doses 8 / 2^2 RAD/h; a lead block between stops nearly all of it. */
    private static void blocks(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        helper.setBlock(new BlockPos(4, 1, 4), GZResources.URANIUM_BLOCK.block().get());
        Vec3 eye = helper.absoluteVec(new Vec3(6.5, 1.5, 4.5));
        float rate = Emitters.blocks(level, eye);
        float strength = Emitters.radioactivity(GZResources.URANIUM_BLOCK.block().get().defaultBlockState());
        helper.assertTrue(strength > 0 && Math.abs(rate - strength / 4) < 0.05 * strength, strength + " RAD/h a block away is a quarter of it two away: " + rate);
        helper.setBlock(new BlockPos(5, 1, 4), GZResources.LEAD_BLOCK.block().get());
        float shielded = Emitters.blocks(level, eye);
        helper.assertTrue(shielded < rate * 0.05, "behind lead: " + shielded);
        float far = Emitters.blocks(level, helper.absoluteVec(new Vec3(4.5, 1.5, 12.5)));
        helper.assertTrue(far == 0, "beyond its reach: " + far);
        helper.succeed();
    }

    /** Plutonium in the pocket doses; inside a Lead Container it does not; the container takes only radioactive items. */
    private static void carried(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(GZNuclear.PLUTONIUM_INGOT.get(), 10));
        float loose = Emitters.carried(player);
        float each = Emitters.radioactivity(GZNuclear.PLUTONIUM_INGOT.get());
        helper.assertTrue(each >= 100 && Math.abs(loose - 10 * each) < 0.01, "ten plutonium ingots: " + loose);
        ItemStack container = new ItemStack(RadiationItems.LEAD_CONTAINER.get());
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(GZNuclear.PLUTONIUM_INGOT.get(), 10))));
        player.getInventory().setItem(0, container);
        helper.assertTrue(Emitters.carried(player) == 0, "inside lead: " + Emitters.carried(player));
        helper.assertTrue(LeadContainerMenu.holds(new ItemStack(GZNuclear.SPENT_FUEL_ROD.get())), "it takes a spent fuel rod");
        helper.assertTrue(!LeadContainerMenu.holds(new ItemStack(Items.DIRT)), "but not dirt");
        helper.succeed();
    }

    /** A pig in a strong source takes a dose (100 rad a second here); a zombie beside it takes none. */
    private static void builds(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        Vec3 centre = helper.absoluteVec(new Vec3(8, 2, 8));
        RadiationSources.of(level).add(new RadiationSource(centre, 12, 360_000, 100_000, level.getGameTime()));
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 8));
        var zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(9, 1, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(Radiation.dose(pig) > 50, "the pig's dose: " + Radiation.dose(pig));
            helper.assertTrue(Radiation.dose(zombie) == 0, "the undead take none");
            RadiationSources.of(level).removeNear(centre, 1);
        });
    }

    /** The dose's stages: 450 rad is Radiation Sickness III with weakness and slowness; past 1,000 it does damage. */
    private static void stages(GameTestHelper helper) {
        helper.assertTrue(RadiationState.stage(99) == 0 && RadiationState.stage(100) == 1 && RadiationState.stage(450) == 3
                && RadiationState.stage(1_000) == 5, "stages at 100, 200, 400, 600, 1 000 rad");
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 8));
        Radiation.addDose(pig, 450);
        MobEffectInstance sick = pig.getEffect(GZEffects.RADIATION_SICKNESS);
        helper.assertTrue(sick != null && sick.getAmplifier() == 2, "Radiation Sickness III: " + sick);
        helper.assertTrue(pig.hasEffect(MobEffects.WEAKNESS) && pig.hasEffect(MobEffects.SLOWNESS), "weak and slow");
        float health = pig.getHealth();
        Radiation.addDose(pig, 700);
        helper.assertTrue(pig.getHealth() < health, "a lethal dose does damage: " + pig.getHealth());
        helper.succeed();
    }

    /** Rad-Away draws 5 rad a second out; Potassium Iodide halves the dose rate taken up; the injector takes 400 off. */
    private static void medicine(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(4, 1, 4));
        Radiation.setDose(pig, 300);
        pig.addEffect(new MobEffectInstance(GZEffects.RAD_AWAY, 600));
        Radiation.step(pig, Radiation.state(pig), 0);
        float after = Radiation.dose(pig);
        helper.assertTrue(Math.abs(after - (300 - Radiation.RAD_AWAY_PER_SECOND - Radiation.RECOVERY)) < 0.01, "a second of Rad-Away: " + after);
        Vec3 centre = helper.absoluteVec(new Vec3(12, 2, 12));
        RadiationSources.of(level).add(new RadiationSource(centre, 6, 10_000, 100_000, level.getGameTime()));
        var sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(12, 1, 12));
        float bare = Radiation.rateAt(level, sheep, false);
        sheep.addEffect(new MobEffectInstance(GZEffects.POTASSIUM_IODIDE, 600));
        float iodide = Radiation.rateAt(level, sheep, false);
        RadiationSources.of(level).removeNear(centre, 1);
        helper.assertTrue(bare > 0 && Math.abs(iodide - bare * Radiation.IODINE_UPTAKE) < 0.01, "iodide halves it: " + bare + " -> " + iodide);
        var patient = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 1, 12));
        Radiation.setDose(patient, 450);
        ItemStack injector = new ItemStack(RadiationItems.ANTI_RAD_INJECTOR.get());
        injector.getItem().finishUsingItem(injector, level, patient);
        helper.assertTrue(Math.abs(Radiation.dose(patient) - 50) < 0.01, "the injector takes 400 off: " + Radiation.dose(patient));
        helper.assertTrue(patient.hasEffect(MobEffects.WEAKNESS), "and leaves weakness");
        helper.succeed();
    }

    /** A new counter is charged with its alarm at 100 RAD/h; held, it counts; flat or knocked out by an EMP, it does not. */
    private static void geiger(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        ItemStack counter = new ItemStack(RadiationItems.GEIGER_COUNTER.get());
        helper.assertTrue(GeigerCounterItem.stored(counter) == GeigerCounterItem.CAPACITY, "new counters come charged");
        helper.assertTrue(GeigerCounterItem.alarm(counter) == 100, "their alarm at 100 RAD/h");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, counter);
        helper.assertTrue(GeigerCounterItem.carried(player) == GeigerCounterItem.Carried.HELD, "held, it shows its meter");
        counter.set(GZDataComponents.EMP_UNTIL.get(), level.getGameTime() + 100);
        helper.assertTrue(GeigerCounterItem.carried(player) == GeigerCounterItem.Carried.NONE, "an EMP knocks it out");
        counter.remove(GZDataComponents.EMP_UNTIL.get());
        counter.set(GZDataComponents.ENERGY.get(), 0);
        helper.assertTrue(GeigerCounterItem.carried(player) == GeigerCounterItem.Carried.NONE, "flat, it counts nothing");
        counter.set(GZDataComponents.ENERGY.get(), 500);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(3, counter);
        helper.assertTrue(GeigerCounterItem.carried(player) == GeigerCounterItem.Carried.HOTBAR, "in the hotbar, the readout");
        helper.succeed();
    }
}
