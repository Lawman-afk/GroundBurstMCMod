package dev.groundburst.gametest;

import java.util.List;
import java.util.Optional;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.chem.GBChemicals;
import dev.groundburst.content.fluid.ChemFluid;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.machine.ArcFurnaceBlockEntity;
import dev.groundburst.content.machine.CryogenicCondenserBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.TallMachineBlock;
import dev.groundburst.content.part.GBParts;
import dev.groundburst.content.recipe.GBRecipes;
import dev.groundburst.content.recipe.MachineRecipeInput;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import dev.groundburst.content.resource.GBResources;
import net.minecraft.core.BlockPos;
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
     * ingredients, nor steal a plant set up for an old one (hydrogen needs oxygen, or every coal-and-water plant
     * would make hydrogen rather than synthetic fuel).
     */
    private static void recipes(GameTestHelper helper) {
        ItemStack coal = new ItemStack(Items.COAL, 4);
        ItemStack plastic = new ItemStack(GBChemicals.PLASTIC_SHEET.get(), 4);
        FluidStack water = new FluidStack(Fluids.WATER, 16_000);
        pick(helper, ProcessingKind.CHEMISTRY, List.of(coal), List.of(water, fl(GBFluids.LIQUID_OXYGEN)), "chemistry/hydrogen");
        pick(helper, ProcessingKind.CHEMISTRY, List.of(coal), List.of(water), "chemistry/synthetic_fuel");
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
        ItemStack quartz = new ItemStack(Items.QUARTZ, 8);
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(quartz, coal), List.of(), "arc_smelting/silicon");
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(quartz, new ItemStack(Items.SAND, 8)), List.of(), "arc_smelting/quartz_crucible");
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(new ItemStack(Items.BONE_MEAL, 6), coal), List.of(), "arc_smelting/red_phosphorus");
        pick(helper, ProcessingKind.ARC_SMELTING, List.of(new ItemStack(Items.CLAY_BALL, 4), new ItemStack(GBParts.GOLD_WIRE.get(), 2)), List.of(),
                "arc_smelting/ceramic_package");
        pick(helper, ProcessingKind.CRUSHING, List.of(new ItemStack(GBResources.FLUORITE.get())), List.of(), "crushing/fluorite_dust");
        helper.succeed();
    }

    private static FluidStack fl(ChemFluid fluid) {
        return new FluidStack(fluid.source().get(), 8_000);
    }

    private static void pick(GameTestHelper helper, ProcessingKind kind, List<ItemStack> items, List<FluidStack> fluids, String expected) {
        MachineRecipeInput input = new MachineRecipeInput(items, ItemStack.EMPTY, fluids);
        Optional<RecipeHolder<ProcessingRecipe>> found = helper.getLevel().recipeAccess().getRecipeFor(GBRecipes.type(kind), input, helper.getLevel());
        String got = found.map(h -> h.id().identifier().toString()).orElse("nothing");
        helper.assertTrue(got.equals(GroundBurst.id(expected).toString()), items + " with " + fluids + " should make " + expected + ", made " + got);
    }
}
