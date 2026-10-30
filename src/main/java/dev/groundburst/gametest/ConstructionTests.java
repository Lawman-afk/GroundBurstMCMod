package dev.groundburst.gametest;

import dev.groundburst.content.construction.GBConstruction;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The see-through construction blocks (DESIGN.md §22.2, after the user's look): lattice, grating and grating slabs leave
 * out the faces between them only where the other covers a face whole, so a stack of them shows its outside (drawn from
 * both sides by their models) and never two faces in one plane.
 */
final class ConstructionTests {
    private ConstructionTests() {}

    static void register() {
        GBGameTests.test("see_through_blocks_show_only_their_outside", GBGameTests.FLOOR_16, 20, ConstructionTests::seeThrough);
    }

    private static BlockState slab(SlabType type) {
        return GBConstruction.STEEL_GRATING_SLAB.block().get().defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static void hides(GameTestHelper helper, BlockState a, BlockState b, Direction dir, boolean expected, String what) {
        helper.assertTrue(a.skipRendering(b, dir) == expected, what + ": " + (expected ? "left out" : "drawn") + " toward " + dir);
    }

    private static void seeThrough(GameTestHelper helper) {
        BlockState lattice = GBConstruction.LATTICE_FRAME.block().get().defaultBlockState();
        BlockState grating = GBConstruction.STEEL_GRATING.block().get().defaultBlockState();
        BlockState bottom = slab(SlabType.BOTTOM), top = slab(SlabType.TOP), dbl = slab(SlabType.DOUBLE);
        for (Direction d : Direction.values()) {
            hides(helper, lattice, lattice, d, true, "lattice by lattice");
            hides(helper, lattice, grating, d, true, "lattice by grating");
            hides(helper, grating, lattice, d, true, "grating by lattice");
            hides(helper, grating, grating, d, true, "grating by grating");
            hides(helper, lattice, dbl, d, true, "lattice by a double slab");
            hides(helper, lattice, Blocks.GLASS.defaultBlockState(), d, false, "lattice by glass");
            hides(helper, lattice, Blocks.AIR.defaultBlockState(), d, false, "lattice by air");
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            hides(helper, bottom, bottom, d, true, "bottom slab by bottom slab");
            hides(helper, top, top, d, true, "top slab by top slab");
            hides(helper, bottom, top, d, false, "bottom slab by top slab");
            hides(helper, top, bottom, d, false, "top slab by bottom slab");
            hides(helper, bottom, grating, d, true, "bottom slab by grating (it covers the half)");
            hides(helper, grating, bottom, d, false, "grating by bottom slab (only half covered)");
        }
        hides(helper, bottom, top, Direction.DOWN, true, "bottom slab over a top slab");
        hides(helper, top, bottom, Direction.UP, true, "top slab under a bottom slab");
        hides(helper, bottom, bottom, Direction.DOWN, false, "bottom slab over a bottom slab (a gap between)");
        hides(helper, grating, top, Direction.DOWN, true, "grating over a top slab");
        hides(helper, grating, bottom, Direction.DOWN, false, "grating over a bottom slab");
        helper.succeed();
    }
}
