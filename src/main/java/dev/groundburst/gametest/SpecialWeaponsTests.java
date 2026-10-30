package dev.groundburst.gametest;

import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileItem;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.content.strategic.GBStrategic;
import dev.groundburst.content.strategic.SpecialWeapons;
import dev.groundburst.content.strategic.SpecialWeaponsBlockEntity;
import dev.groundburst.content.strategic.StrategicAssembly;
import dev.groundburst.content.strategic.StrategicAssemblyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The Special Weapons Assembler (DESIGN.md §16.15): the exotic weapons' own assembler, a containment bay that builds each
 * one's ICBM whole from its core and an Intercontinental Booster; the ICBM Assembly Hall no longer takes their cores.
 */
final class SpecialWeaponsTests {
    private SpecialWeaponsTests() {}

    static void register() {
        GBGameTests.test("special_weapons_assembler_builds_an_antimatter_icbm_and_takes_it_apart", GBGameTests.FLOOR_16, 40,
                SpecialWeaponsTests::builds);
        GBGameTests.test("special_weapons_assembler_needs_its_structure_and_parts", GBGameTests.FLOOR_16, 20, SpecialWeaponsTests::needsParts);
        GBGameTests.test("icbm_hall_takes_no_exotic_core", GBGameTests.FLOOR_16, 20, SpecialWeaponsTests::hallRefuses);
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            helper.setBlock(p, Blocks.STONE);
        }
    }

    /** The assembler built round a controller at (8, 1, 1) facing north: 7 wide (x 5-11), 13 deep (z 1-13), 4 high. */
    private static SpecialWeaponsBlockEntity assembler(GameTestHelper helper, boolean build) {
        BlockPos rel = new BlockPos(8, 1, 1);
        helper.setBlock(rel, GBStrategic.SPECIAL_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        if (build) {
            for (MultiblockDefinition.Cell cell : GBStrategic.SPECIAL.cells(helper.absolutePos(rel), Direction.NORTH)) {
                if (cell.key() != MultiblockDefinition.CONTROLLER && cell.key() != '.') {
                    helper.getLevel().setBlockAndUpdate(cell.pos(), GBStrategic.SPECIAL.legend().get(cell.key()).preview().get());
                }
            }
        }
        var special = (SpecialWeaponsBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(special != null, "a Special Weapons Assembler controller");
        special.revalidate();
        helper.assertTrue(special.isFormed() == build,
                "formed: " + special.isFormed() + " " + GBStrategic.SPECIAL.check(helper.getLevel(), special.getBlockPos(), Direction.NORTH));
        return special;
    }

    /**
     * An Antimatter Trap, a booster, GPS and hardened electronics make a hardened, GPS-guided Antimatter ICBM: a minute and
     * a half's work (skipped here), its own model on the cradles; TAKE APART gives every part back.
     */
    private static void builds(GameTestHelper helper) {
        floor(helper);
        SpecialWeaponsBlockEntity special = assembler(helper, true);
        Container inv = special.inventory();
        ItemStack trap = new ItemStack(GBAssembly.ANTIMATTER_TRAP.get());
        helper.assertTrue(inv.canPlaceItem(SpecialWeaponsBlockEntity.SLOT_CORE, trap), "the core slot takes an Antimatter Trap");
        inv.setItem(SpecialWeaponsBlockEntity.SLOT_CORE, trap);
        inv.setItem(SpecialWeaponsBlockEntity.SLOT_BOOSTER, new ItemStack(GBStrategic.INTERCONTINENTAL_BOOSTER.get()));
        inv.setItem(SpecialWeaponsBlockEntity.SLOT_GUIDANCE, new ItemStack(GBAssembly.GPS_GUIDANCE.get()));
        inv.setItem(SpecialWeaponsBlockEntity.SLOT_MODULE, new ItemStack(GBAssembly.HARDENED_ELECTRONICS.get()));
        helper.assertTrue(special.problem() == SpecialWeapons.Problem.NONE, "ready: " + special.problem());
        special.energy().generate(SpecialWeaponsBlockEntity.CAPACITY);
        helper.assertTrue(special.startAssembly(), "it starts");
        helper.assertTrue(!special.startAssembly(), "but not twice");
        special.skipToEndForTest();
        helper.succeedWhen(() -> {
            ItemStack out = inv.getItem(SpecialWeaponsBlockEntity.SLOT_OUTPUT);
            MissileSpec spec = MissileItem.spec(out);
            helper.assertTrue(spec != null && spec.intercontinental() && spec.exotic() && spec.needsComplex() && spec.hardened()
                    && AssemblyStats.kindOf(spec.warhead()) == ChargeKind.ANTIMATTER_WARHEAD, "a hardened Antimatter ICBM: " + spec);
            var look = out.get(DataComponents.CUSTOM_MODEL_DATA);
            helper.assertTrue(look != null && look.strings().getFirst().equals("antimatter_warhead"), "its own model: " + look);
            helper.assertTrue(inv.getItem(SpecialWeaponsBlockEntity.SLOT_CORE).isEmpty() && inv.getItem(SpecialWeaponsBlockEntity.SLOT_BOOSTER).isEmpty(),
                    "the parts used up");
            var shown = special.shownMissile();
            helper.assertTrue(special.laid() == SpecialWeaponsBlockEntity.SECTIONS && shown != null
                    && shown.looks().getLast().equals("antimatter_warhead_part4"), "on the cradles: " + shown);
            helper.assertTrue(special.takeApart(), "taken apart");
            helper.assertTrue(inv.getItem(SpecialWeaponsBlockEntity.SLOT_CORE).is(GBAssembly.ANTIMATTER_TRAP.get())
                    && inv.getItem(SpecialWeaponsBlockEntity.SLOT_BOOSTER).is(GBStrategic.INTERCONTINENTAL_BOOSTER.get())
                    && inv.getItem(SpecialWeaponsBlockEntity.SLOT_GUIDANCE).is(GBAssembly.GPS_GUIDANCE.get())
                    && inv.getItem(SpecialWeaponsBlockEntity.SLOT_MODULE).is(GBAssembly.HARDENED_ELECTRONICS.get()), "every part back");
        });
    }

    /** Unbuilt it will not work; built, it says what is missing, and its slots take only their own parts. */
    private static void needsParts(GameTestHelper helper) {
        floor(helper);
        SpecialWeaponsBlockEntity bare = assembler(helper, false);
        helper.assertTrue(bare.problem() == SpecialWeapons.Problem.NOT_FORMED, "not built: " + bare.problem());
        SpecialWeaponsBlockEntity special = assembler(helper, true);
        Container inv = special.inventory();
        helper.assertTrue(special.problem() == SpecialWeapons.Problem.NO_CORE, "no core: " + special.problem());
        helper.assertTrue(!inv.canPlaceItem(SpecialWeaponsBlockEntity.SLOT_CORE, new ItemStack(GBAssembly.BOOSTED_FISSION_CORE.get())),
                "a Boosted Fission Core is no exotic core");
        for (ItemStack core : SpecialWeapons.cores()) {
            helper.assertTrue(inv.canPlaceItem(SpecialWeaponsBlockEntity.SLOT_CORE, core), "it takes " + core);
        }
        inv.setItem(SpecialWeaponsBlockEntity.SLOT_CORE, new ItemStack(GBAssembly.STORM_CORE.get()));
        helper.assertTrue(special.problem() == SpecialWeapons.Problem.NO_BOOSTER, "no booster: " + special.problem());
        helper.assertTrue(!special.startAssembly(), "nothing to start");
        helper.assertTrue(!inv.canPlaceItem(SpecialWeaponsBlockEntity.SLOT_OUTPUT, LaunchTests.missile()), "only its own missiles in its output");
        helper.succeed();
    }

    /**
     * The hall builds the fusion weapons only: its primary slot takes no exotic core, it will not take an exotic missile
     * apart, and an exotic missile is no missile of its output's.
     */
    private static void hallRefuses(GameTestHelper helper) {
        floor(helper);
        BlockPos rel = new BlockPos(8, 1, 1);
        helper.setBlock(rel, GBStrategic.HALL_CONTROLLER.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var hall = (StrategicAssemblyBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(hall != null, "a hall controller");
        for (ItemStack core : SpecialWeapons.cores()) {
            helper.assertTrue(!hall.inventory().canPlaceItem(StrategicAssemblyBlockEntity.SLOT_PRIMARY, core), "the hall refuses " + core);
            helper.assertTrue(!StrategicAssembly.isPrimary(core), "no primary: " + core);
            ItemStack icbm = SpecialWeapons.assemble(core, ItemStack.EMPTY, ItemStack.EMPTY);
            helper.assertTrue(StrategicAssembly.takeApart(icbm).isEmpty(), "the hall takes no exotic missile apart");
            helper.assertTrue(!hall.inventory().canPlaceItem(StrategicAssemblyBlockEntity.SLOT_OUTPUT, icbm), "nor holds one");
            helper.assertTrue(SpecialWeapons.takeApart(icbm).size() == 4, "the Special Weapons Assembler does");
        }
        helper.succeed();
    }
}
