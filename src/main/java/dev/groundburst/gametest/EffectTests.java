package dev.groundburst.gametest;

import dev.groundburst.content.effect.EffectSystems;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.effect.Frost;
import dev.groundburst.content.effect.GasAgent;
import dev.groundburst.content.effect.GasGrid;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.ordnance.GBOrdnance;
import dev.groundburst.content.ordnance.charge.ChargeBlock;
import dev.groundburst.content.ordnance.charge.ChargeBlockEntity;
import dev.groundburst.content.ordnance.charge.Charges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Phase 6 slice 6e (DESIGN.md §16.4, §16.7, §21.4): the gas grid, EMP fields and freezing, and what uses them. */
final class EffectTests {
    private EffectTests() {}

    static void register() {
        GBGameTests.test("gas_sinks_into_a_pit", GBGameTests.FLOOR_16, 100, EffectTests::sinks);
        GBGameTests.test("gas_is_kept_out_by_a_wall", GBGameTests.FLOOR_16, 60, EffectTests::walls);
        GBGameTests.test("gas_poisons_what_breathes_it", GBGameTests.FLOOR_16, 40, EffectTests::poisons);
        GBGameTests.test("chemical_warhead_lets_out_its_agent", GBGameTests.FLOOR_16, 20, EffectTests::chemical);
        GBGameTests.test("emp_knocks_out_a_machine_until_it_ends", GBGameTests.FLOOR_16, 60, EffectTests::empMachine);
        GBGameTests.test("emp_makes_electronic_charges_safe", GBGameTests.FLOOR_16, 20, EffectTests::empCharges);
        GBGameTests.test("frost_freezes_water_lava_and_mobs", GBGameTests.FLOOR_16, 20, EffectTests::frost);
        GBGameTests.test("frozen_mob_shatters_when_hit_hard", GBGameTests.FLOOR_16, 20, EffectTests::shatter);
    }

    private static void floor(GameTestHelper helper) {
        Charges.bombScale = 0.3f;
        for (BlockPos p : BlockPos.betweenClosed(1, 1, 1, 15, 1, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    private static GasGrid gas(GameTestHelper helper) {
        return GasGrid.of(helper.getLevel());
    }

    /** Chlorine let out beside a pit runs down into it: the pit's floor ends up with more than the block above it (the wind blows on both alike). */
    private static void sinks(GameTestHelper helper) {
        floor(helper);
        for (int y = 1; y >= 1; y--) {
            for (int x = 3; x <= 4; x++) {
                for (int z = 7; z <= 9; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);    // a pit, one deep, west of the release
                }
            }
        }
        helper.setBlock(new BlockPos(3, 0, 8), Blocks.STONE);
        BlockPos at = helper.absolutePos(new BlockPos(6, 2, 8));
        gas(helper).emit(helper.getLevel(), at, GasAgent.CHLORINE, 50);       // plenty, so a wind blowing away does not starve the pit
        helper.succeedWhen(() -> {
            float pit = gas(helper).concentration(helper.absolutePos(new BlockPos(4, 1, 8)), GasAgent.CHLORINE);
            float above = gas(helper).concentration(helper.absolutePos(new BlockPos(4, 3, 8)), GasAgent.CHLORINE);
            helper.assertTrue(pit > 0.05f && pit > above, "the gas settles in the pit (" + pit + " against " + above + " above)");
        });
    }

    /** A glass box keeps the gas outside it out. */
    private static void walls(GameTestHelper helper) {
        floor(helper);
        for (BlockPos p : BlockPos.betweenClosed(9, 2, 6, 13, 6, 10)) {
            boolean shell = p.getX() == 9 || p.getX() == 13 || p.getY() == 6 || p.getZ() == 6 || p.getZ() == 10;
            helper.setBlock(p, shell ? Blocks.GLASS : Blocks.AIR);
        }
        gas(helper).fill(helper.getLevel(), helper.absoluteVec(new Vec3(5, 2, 8)), 4, GasAgent.CHLORINE, 1);
        BlockPos inside = helper.absolutePos(new BlockPos(11, 2, 8));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(gas(helper).concentration(inside, GasAgent.CHLORINE) == 0, "nothing gets through the glass");
            helper.assertTrue(gas(helper).concentration(helper.absolutePos(new BlockPos(8, 2, 8)), GasAgent.CHLORINE) > 0,
                    "but it is right up against it");
            helper.succeed();
        });
    }

    /** A pig standing in thick gas is poisoned and hurt. */
    private static void poisons(GameTestHelper helper) {
        floor(helper);
        var pig = helper.spawn(EntityTypes.PIG, new BlockPos(8, 2, 8));
        pig.setNoAi(true);
        gas(helper).fill(helper.getLevel(), helper.absoluteVec(new Vec3(8, 2, 8)), 3, GasAgent.CHLORINE, 1.5f);
        float before = pig.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(pig.hasEffect(MobEffects.POISON) && pig.getHealth() < before, "poisoned and hurt"));
    }

    /** The chemical warhead set off lets out its agent round it. */
    private static void chemical(GameTestHelper helper) {
        floor(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at, GBOrdnance.CHEMICAL_WARHEAD.block().get().defaultBlockState().setValue(ChargeBlock.FACING, Direction.NORTH));
        ChargeBlockEntity be = ChargeBlock.at(helper.getLevel(), helper.absolutePos(at));
        helper.assertTrue(be != null, "a warhead");
        be.arm(helper.getLevel(), null);
        be.trigger(helper.getLevel(), null);
        helper.succeedWhen(() -> helper.assertTrue(
                gas(helper).concentration(helper.absolutePos(new BlockPos(8, 3, 8)), GasAgent.ANTHRAX) > 0.3f, "a thick cloud of it"));
    }

    private static LaunchSiloBlockEntity silo(GameTestHelper helper) {
        BlockPos rel = new BlockPos(5, 2, 3);
        helper.setBlock(rel, GBLaunch.SILO_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        for (MultiblockDefinition.Cell cell : GBLaunch.SILO.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                helper.getLevel().setBlockAndUpdate(cell.pos(), GBLaunch.SILO.legend().get(cell.key()).preview().get());
            }
        }
        LaunchSiloBlockEntity s = (LaunchSiloBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        s.revalidate();
        return s;
    }

    /** A silo caught in an EMP field loses half its power, cannot launch, and works again once the field has gone. */
    private static void empMachine(GameTestHelper helper) {
        floor(helper);
        LaunchSiloBlockEntity s = silo(helper);
        s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, LaunchTests.missile());
        s.energy().generate(LaunchSiloBlockEntity.ENERGY_CAPACITY);
        int full = s.energy().stored();
        EmpFields.add(helper.getLevel(), Vec3.atCenterOf(s.getBlockPos()), 8, 20, null);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(s.emped() && s.energy().stored() <= full / 2 + 1, "knocked out, half its power gone");
            helper.assertTrue(!s.launch(helper.getLevel(), s.getBlockPos().offset(10, 0, 10), null), "it cannot launch");
            helper.succeedWhen(() -> helper.assertTrue(!s.emped(), "it reboots once the field has gone"));
        });
    }

    /** An EMP makes armed charges with electronic fuzes safe; a landmine (a pressure plate) stays live. */
    private static void empCharges(GameTestHelper helper) {
        floor(helper);
        BlockPos c4 = new BlockPos(6, 2, 8), mine = new BlockPos(10, 2, 8);
        helper.setBlock(c4, GBOrdnance.C4.block().get().defaultBlockState().setValue(ChargeBlock.FACING, Direction.UP));
        helper.setBlock(mine, GBOrdnance.LANDMINE.block().get().defaultBlockState());
        ChargeBlockEntity a = ChargeBlock.at(helper.getLevel(), helper.absolutePos(c4)), b = ChargeBlock.at(helper.getLevel(), helper.absolutePos(mine));
        a.arm(helper.getLevel(), null);
        b.arm(helper.getLevel(), null);
        helper.runAfterDelay(2, () -> {
            EmpFields.add(helper.getLevel(), helper.absoluteVec(new Vec3(8, 2, 8)), 6, 20, null);
            helper.assertTrue(!a.armed(), "the C4 is safe");
            helper.assertTrue(b.armed(), "the landmine is still live");
            helper.succeed();
        });
    }

    /** A burst of cold freezes water to ice, lava to obsidian, and a pig solid (it cannot move). */
    private static void frost(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(new BlockPos(6, 1, 8), Blocks.WATER);
        helper.setBlock(new BlockPos(10, 1, 8), Blocks.LAVA);
        var pig = helper.spawn(EntityTypes.PIG, new BlockPos(8, 2, 10));
        Frost.freeze(helper.getLevel(), helper.absoluteVec(new Vec3(8.5, 2, 8.5)), 5, 100, null);
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(6, 1, 8))).is(Blocks.PACKED_ICE)
                || helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(6, 1, 8))).is(Blocks.ICE), "water frozen");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(10, 1, 8))).is(Blocks.OBSIDIAN), "lava frozen");
        helper.assertTrue(pig.hasEffect(EffectSystems.FROZEN), "the pig is frozen");
        helper.assertTrue(pig.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) <= 1e-6, "and cannot move");
        helper.succeed();
    }

    /** A frozen pig hit hard breaks apart. */
    private static void shatter(GameTestHelper helper) {
        floor(helper);
        var pig = helper.spawn(EntityTypes.PIG, new BlockPos(8, 2, 8));
        Frost.freeze(helper.getLevel(), helper.absoluteVec(new Vec3(8.5, 2, 8.5)), 3, 100, null);
        pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), Frost.SHATTER_DAMAGE);
        helper.succeedWhen(() -> helper.assertTrue(!pig.isAlive(), "shattered"));
    }
}
