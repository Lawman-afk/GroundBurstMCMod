package dev.groundburst.gametest;

import java.util.List;
import java.util.Optional;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.fluid.ChemFluid;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.machine.ArcFurnaceBlockEntity;
import dev.groundburst.content.machine.CleanRoomBlockEntity;
import dev.groundburst.content.machine.CryogenicCondenserBlockEntity;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.machine.CrystalPullerBlockEntity;
import dev.groundburst.content.machine.IndustrialPressBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.recipe.GBRecipes;
import dev.groundburst.content.recipe.MachineRecipeInput;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import dev.groundburst.content.resource.GBResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** The semiconductor chain (DESIGN.md §8.1): its materials, gases and the recipes that pick them. */
final class SemiconductorTests {
    private SemiconductorTests() {}

    static void register() {
        GBGameTests.test("arc_furnace_makes_silicon", GBGameTests.FLOOR_16, 400, SemiconductorTests::silicon);
        GBGameTests.test("condenser_draws_argon", GBGameTests.FLOOR_16, 400, SemiconductorTests::argon);
        GBGameTests.test("semiconductor_recipes_pick_right", GBGameTests.FLOOR_16, 20, SemiconductorTests::recipes);
        GBGameTests.test("crystal_puller_grows_a_boule", GBGameTests.FLOOR_16, 1_400, SemiconductorTests::boule);
        GBGameTests.test("press_saws_a_boule_into_wafers", GBGameTests.FLOOR_16, 400, SemiconductorTests::wafers);
        GBGameTests.test("clean_room_forms_and_prints_microchips", GBGameTests.FLOOR_16, 800, SemiconductorTests::microchips);
        GBGameTests.test("clean_room_prints_rad_hard_chips", GBGameTests.FLOOR_16, 1_400, SemiconductorTests::radHard);
        GBGameTests.test("separator_makes_hydrogen_through_its_membrane", GBGameTests.FLOOR_16, 400, SemiconductorTests::hydrogen);
    }

    private static void silicon(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GBMachines.ARC_FURNACE.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        ArcFurnaceBlockEntity arc = helper.getBlockEntity(pos, ArcFurnaceBlockEntity.class);
        arc.energy().generate(40_000);
        arc.inventory().setItem(0, new ItemStack(Items.QUARTZ, 2));
        arc.inventory().setItem(1, new ItemStack(Items.COAL, 1));
        helper.succeedWhen(() -> {
            ItemStack out = arc.inventory().getItem(2);
            helper.assertTrue(out.is(GBParts.SILICON.get()) && out.getCount() == 1, "two quartz and a coal give a silicon, have " + out);
            helper.assertTrue(arc.inventory().getItem(0).isEmpty() && arc.inventory().getItem(1).isEmpty(), "the quartz and coal are used");
        });
    }

    private static void argon(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GBMachines.CRYOGENIC_CONDENSER.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        CryogenicCondenserBlockEntity cond = helper.getBlockEntity(pos, CryogenicCondenserBlockEntity.class);
        cond.energy().generate(50_000);
        cond.inventory().setItem(0, new ItemStack(GBParts.ARGON_SIEVE.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(cond.tank(0).fluid().getFluid() == GBFluids.ARGON.source().get() && cond.tank(0).amount() == 250,
                    "the argon sieve gives 250 mB of argon a run, have " + cond.tank(0).amount());
            helper.assertTrue(cond.inventory().getItem(0).is(GBParts.ARGON_SIEVE.get()), "the sieve is not used up");
        });
    }

    /**
     * Machines take the first recipe their contents satisfy, so a new recipe must not be satisfied by another's
     * ingredients, nor steal a plant set up for an old one; a recipe with no catalyst runs only with the catalyst slot
     * empty (the separator: hydrogen through its membrane, deuterium without).
     */
    private static void recipes(GameTestHelper helper) {
        ItemStack coal = new ItemStack(Items.COAL, 4);
        ItemStack plastic = new ItemStack(GBChemicals.PLASTIC_SHEET.get(), 4);
        FluidStack water = new FluidStack(Fluids.WATER, 16_000);
        pick(helper, ProcessingKind.CHEMISTRY, List.of(coal), List.of(water, fl(GBFluids.LIQUID_OXYGEN)), "chemistry/synthetic_fuel");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(coal), List.of(water), "chemistry/synthetic_fuel");
        ItemStack membrane = new ItemStack(GBParts.PROTON_EXCHANGE_MEMBRANE.get());
        pick(helper, ProcessingKind.ELECTROLYSIS, List.of(), membrane, List.of(water), "electrolysis/hydrogen");
        pick(helper, ProcessingKind.ELECTROLYSIS, List.of(), ItemStack.EMPTY, List.of(water), "electrolysis/deuterium");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(), List.of(water, fl(GBFluids.HYDROGEN), fl(GBFluids.CHLORINE)), "chemistry/hydrogen_chloride");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(new ItemStack(GBParts.SILICON.get(), 4)), List.of(water, fl(GBFluids.HYDROGEN_CHLORIDE)),
                "chemistry/trichlorosilane");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(), List.of(water, fl(GBFluids.TRICHLOROSILANE), fl(GBFluids.HYDROGEN)), "chemistry/polysilicon");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(plastic, coal), List.of(water, fl(GBFluids.NITRIC_ACID), fl(GBFluids.SYNTHETIC_FUEL)),
                "chemistry/photoresist");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(coal), List.of(water, fl(GBFluids.SYNTHETIC_FUEL)), "chemistry/plastic_sheet");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(plastic, new ItemStack(Items.SLIME_BALL, 4)), List.of(water, fl(GBFluids.SYNTHETIC_FUEL)),
                "chemistry/napalm");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(new ItemStack(GBResources.FLUORITE_DUST.get(), 4)), List.of(water, fl(GBFluids.SULFURIC_ACID)),
                "chemistry/hydrofluoric_acid");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(new ItemStack(Items.CLAY_BALL, 8)), List.of(water, fl(GBFluids.SULFURIC_ACID)), "chemistry/alumina");
        ItemStack quartz = new ItemStack(Items.QUARTZ, 8);
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(quartz, coal), List.of(), "arc_smelting/silicon");
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(quartz, new ItemStack(Items.SAND, 8)), List.of(), "arc_smelting/quartz_crucible");
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(new ItemStack(Items.BONE_MEAL, 6), coal), List.of(), "arc_smelting/red_phosphorus");
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(new ItemStack(Items.CLAY_BALL, 4), new ItemStack(GBParts.GOLD_WIRE.get(), 2)), List.of(),
                "arc_smelting/ceramic_package");
        pick(helper, ProcessingKind.CRUSHING, List.of(new ItemStack(GBResources.FLUORITE.get())), List.of(), "crushing/fluorite_dust");
        ItemStack wafers = new ItemStack(GBParts.SILICON_WAFER.get(), 4), red = new ItemStack(GBChemicals.RED_PHOSPHORUS.get(), 4);
        pick(helper, ProcessingKind.LITHOGRAPHY, List.of(wafers, red, plastic),
                List.of(fl(GBFluids.PHOTORESIST), fl(GBFluids.HYDROFLUORIC_ACID), fl(GBFluids.LIQUID_OXYGEN)), "lithography/microchip");
        ItemStack sapphire = new ItemStack(GBParts.SAPPHIRE_WAFER.get(), 4), ceramic = new ItemStack(GBParts.CERAMIC_PACKAGE.get(), 2);
        pick(helper, ProcessingKind.LITHOGRAPHY, List.of(sapphire, red, ceramic),
                List.of(fl(GBFluids.PHOTORESIST), fl(GBFluids.HYDROFLUORIC_ACID), fl(GBFluids.TRICHLOROSILANE)), "lithography/rad_hard_microchip");
        pick(helper, ProcessingKind.LITHOGRAPHY, List.of(sapphire, new ItemStack(dev.groundburst.content.nuclear.GBNuclear.STABILISED_EXOTIC_MATTER.get()), ceramic),
                List.of(fl(GBFluids.PHOTORESIST), fl(GBFluids.HYDROFLUORIC_ACID), fl(GBFluids.ARGON)), "lithography/quantum_chip");
        // Rad-hard chips are built on sapphire now: silicon wafers in a ceramic package make nothing.
        pick(helper, ProcessingKind.LITHOGRAPHY, List.of(wafers, red, ceramic),
                List.of(fl(GBFluids.PHOTORESIST), fl(GBFluids.HYDROFLUORIC_ACID), fl(GBFluids.TRICHLOROSILANE)), "nothing");
        pick(helper, ProcessingKind.CRYSTAL_PULLING, List.of(new ItemStack(GBParts.POLYSILICON.get(), 4), new ItemStack(GBParts.QUARTZ_CRUCIBLE.get())),
                List.of(fl(GBFluids.ARGON)), "crystal_pulling/silicon_boule");
        pick(helper, ProcessingKind.CRYSTAL_PULLING, List.of(new ItemStack(GBParts.ALUMINA.get(), 4), new ItemStack(GBParts.TUNGSTEN_CRUCIBLE.get())),
                List.of(fl(GBFluids.ARGON)), "crystal_pulling/sapphire_boule");
        ItemStack saw = new ItemStack(GBParts.DIAMOND_WIRE_SAW.get());
        pick(helper, ProcessingKind.PRESSING, List.of(new ItemStack(GBParts.SAPPHIRE_BOULE.get())), saw, List.of(), "pressing/sapphire_wafer");
        pick(helper, ProcessingKind.PRESSING, List.of(new ItemStack(GBParts.SILICON_BOULE.get())), saw, List.of(), "pressing/silicon_wafer");
        pick(helper, ProcessingKind.FABRICATING, List.of(new ItemStack(GBParts.MILITARY_CIRCUIT.get()), new ItemStack(GBParts.QUANTUM_CHIP.get(), 2),
                new ItemStack(GBParts.SUPERCONDUCTING_COIL.get())), ItemStack.EMPTY, List.of(), "fabricating/quantum_circuit");
        helper.succeed();
    }

    /** Four polysilicon and a crucible under argon: a boule in a minute, the crucible used, the argon drawn down. */
    private static void boule(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GBMachines.CRYSTAL_PULLER.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        CrystalPullerBlockEntity puller = helper.getBlockEntity(pos, CrystalPullerBlockEntity.class);
        puller.energy().generate(80_000);
        puller.inventory().setItem(0, new ItemStack(GBParts.POLYSILICON.get(), 4));
        puller.inventory().setItem(1, new ItemStack(GBParts.QUARTZ_CRUCIBLE.get()));
        puller.tank(0).fillInternal(new FluidStack(GBFluids.ARGON.source().get(), 1_500));
        // 160 FE a tick for a minute is more than its buffer: top it up as a cable would.
        for (int t : new int[] {400, 800, 1_100}) {
            helper.runAtTickTime(t, () -> puller.energy().generate(80_000));
        }
        helper.runAtTickTime(600, () -> {
            helper.assertTrue(puller.inventory().getItem(2).isEmpty(), "no boule before the minute is up");
            helper.assertTrue(puller.boneScales().get("crystal")[1] > 0.3f, "the crystal shows, half grown");
        });
        helper.succeedWhen(() -> {
            ItemStack out = puller.inventory().getItem(2);
            helper.assertTrue(out.is(GBParts.SILICON_BOULE.get()) && out.getCount() == 1, "a boule, have " + out);
            helper.assertTrue(puller.inventory().getItem(0).isEmpty() && puller.inventory().getItem(1).isEmpty(),
                    "the polysilicon and the crucible are used");
            helper.assertTrue(puller.tank(0).amount() == 500, "1000 mB of argon used, 500 left; have " + puller.tank(0).amount());
        });
    }

    /** A boule on the press with the diamond wire saw in its die slot: eight wafers, the saw kept. */
    private static void wafers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        TallMachineBlock block = GBMachines.INDUSTRIAL_PRESS.block().get();
        helper.setBlock(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), block.defaultBlockState(), null, ItemStack.EMPTY);
        IndustrialPressBlockEntity press = helper.getBlockEntity(pos, IndustrialPressBlockEntity.class);
        press.energy().generate(20_000);
        press.inventory().setItem(0, new ItemStack(GBParts.SILICON_BOULE.get()));
        press.inventory().setItem(1, new ItemStack(GBParts.DIAMOND_WIRE_SAW.get()));
        helper.succeedWhen(() -> {
            ItemStack out = press.inventory().getItem(2);
            helper.assertTrue(out.is(GBParts.SILICON_WAFER.get()) && out.getCount() == 8, "eight wafers, have " + out);
            helper.assertTrue(press.inventory().getItem(1).is(GBParts.DIAMOND_WIRE_SAW.get()), "the saw is kept");
            helper.assertTrue(!press.hiddenBones().contains("die_saw"), "the saw shows on the press bed");
        });
    }

    /** Builds the Clean Room by hand round its controller (facing north), part by part. */
    private static CleanRoomBlockEntity cleanRoom(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, GBMachines.CLEAN_ROOM.block().get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        MultiblockDefinition def = GBMultiblocks.CLEAN_ROOM;
        for (MultiblockDefinition.Cell cell : def.cells(helper.absolutePos(rel), Direction.NORTH)) {
            if (cell.key() != MultiblockDefinition.CONTROLLER) {
                helper.getLevel().setBlockAndUpdate(cell.pos(), def.legend().get(cell.key()).preview().get());
            }
        }
        return helper.getBlockEntity(rel, CleanRoomBlockEntity.class);
    }

    /** A wafer, phosphorus and a plastic sheet under photoresist and hydrofluoric acid: four microchips in 30 s. */
    private static void microchips(GameTestHelper helper) {
        CleanRoomBlockEntity room = cleanRoom(helper, new BlockPos(3, 1, 3));
        helper.assertTrue(room.isFormed(), "the clean room forms");
        room.energy().generate(160_000);
        helper.runAtTickTime(400, () -> room.energy().generate(160_000));   // 30 s at 320 FE/t is more than its buffer
        room.inventory().setItem(0, new ItemStack(GBParts.SILICON_WAFER.get()));
        room.inventory().setItem(1, new ItemStack(GBChemicals.RED_PHOSPHORUS.get()));
        room.inventory().setItem(2, new ItemStack(GBChemicals.PLASTIC_SHEET.get()));
        room.tank(0).fillInternal(new FluidStack(GBFluids.PHOTORESIST.source().get(), 1_000));
        room.tank(1).fillInternal(new FluidStack(GBFluids.HYDROFLUORIC_ACID.source().get(), 1_000));
        helper.succeedWhen(() -> {
            ItemStack out = room.inventory().getItem(3);
            helper.assertTrue(out.is(GBParts.MICROCHIP.get()) && out.getCount() == 4, "four microchips, have " + out);
            helper.assertTrue(room.tank(0).amount() == 750 && room.tank(1).amount() == 750, "250 mB of each fluid used");
        });
    }

    /** Two sapphire wafers, phosphorus and a ceramic package under photoresist, HF and trichlorosilane: two rad-hard chips. */
    private static void radHard(GameTestHelper helper) {
        CleanRoomBlockEntity room = cleanRoom(helper, new BlockPos(3, 1, 3));
        room.energy().generate(160_000);
        for (int t : new int[] {400, 800, 1_100}) {
            helper.runAtTickTime(t, () -> room.energy().generate(160_000));
        }
        room.inventory().setItem(0, new ItemStack(GBParts.SAPPHIRE_WAFER.get(), 2));
        room.inventory().setItem(1, new ItemStack(GBChemicals.RED_PHOSPHORUS.get()));
        room.inventory().setItem(2, new ItemStack(GBParts.CERAMIC_PACKAGE.get()));
        room.tank(0).fillInternal(new FluidStack(GBFluids.PHOTORESIST.source().get(), 500));
        room.tank(1).fillInternal(new FluidStack(GBFluids.HYDROFLUORIC_ACID.source().get(), 500));
        room.tank(2).fillInternal(new FluidStack(GBFluids.TRICHLOROSILANE.source().get(), 500));
        helper.succeedWhen(() -> {
            ItemStack out = room.inventory().getItem(3);
            helper.assertTrue(out.is(GBParts.RAD_HARD_MICROCHIP.get()) && out.getCount() == 2, "two rad-hard chips, have " + out);
        });
    }

    /**
     * Water through the separator with its proton exchange membrane: hydrogen in ten seconds, the membrane kept; with
     * the membrane taken out it stops (deuterium wants 8000 mB, more than it has).
     */
    private static void hydrogen(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GBMachines.ELECTROLYTIC_SEPARATOR.block().get());
        var sep = helper.getBlockEntity(pos, dev.groundburst.content.machine.ElectrolyticSeparatorBlockEntity.class);
        sep.energy().generate(30_000);
        sep.tank(0).fillInternal(new FluidStack(Fluids.WATER, 2_000));
        sep.inventory().setItem(0, new ItemStack(GBParts.PROTON_EXCHANGE_MEMBRANE.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(sep.tank(1).fluid().getFluid() == GBFluids.HYDROGEN.source().get() && sep.tank(1).amount() >= 1_000,
                    "1000 mB of hydrogen a run, have " + sep.tank(1).amount() + " of " + sep.tank(1).fluid().getFluid());
            helper.assertTrue(sep.inventory().getItem(0).is(GBParts.PROTON_EXCHANGE_MEMBRANE.get()), "the membrane is kept");
        });
    }

    private static FluidStack fl(ChemFluid fluid) {
        return new FluidStack(fluid.source().get(), 8_000);
    }

    private static void pick(GameTestHelper helper, ProcessingKind kind, List<ItemStack> items, List<FluidStack> fluids, String expected) {
        pick(helper, kind, items, ItemStack.EMPTY, fluids, expected);
    }

    private static void pick(GameTestHelper helper, ProcessingKind kind, List<ItemStack> items, ItemStack catalyst, List<FluidStack> fluids,
            String expected) {
        MachineRecipeInput input = new MachineRecipeInput(items, catalyst, fluids);
        Optional<RecipeHolder<ProcessingRecipe>> found = helper.getLevel().recipeAccess().getRecipeFor(GBRecipes.type(kind), input, helper.getLevel());
        String got = found.map(h -> h.id().identifier().toString()).orElse("nothing");
        String want = expected.equals("nothing") ? "nothing" : GroundBurst.id(expected).toString();
        helper.assertTrue(got.equals(want), items + " with " + fluids + " should make " + expected + ", made " + got);
    }
}
