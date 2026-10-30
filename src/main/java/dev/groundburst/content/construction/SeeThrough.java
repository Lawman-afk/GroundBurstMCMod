package dev.groundburst.content.construction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WaterloggedTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The see-through construction blocks: the Lattice Frame, the Steel Grating and its slab (DESIGN.md §22.2; after the
 * user's look). Their models draw each outer face from both sides (an inward copy a hundredth of a pixel inside it),
 * so a stack of them looks like one open cage: through its near side you see its far side and its sides from within.
 * Where two of them meet, the faces between them are left out, as glass leaves them out, so only the outside shows:
 * each leaves out a face of its own that the other's covers whole (a slab beside a full block leaves its half out; the
 * block keeps its whole face, drawn both ways), so no two faces ever lie in one plane facing one way.
 */
public final class SeeThrough {
    private static final float[] FULL = {0, 16}, LOWER = {0, 8}, UPPER = {8, 16};

    private SeeThrough() {}

    /**
     * Where a see-through construction block has a face on its cell's side toward {@code dir}: its span up that side
     * (a side face), the whole of it (a top or bottom face on the cell's boundary), or null (none there: a bottom
     * slab's top, a top slab's bottom, or any other block).
     */
    static float @Nullable [] coverage(BlockState state, Direction dir) {
        if (state.getBlock() instanceof Lattice || state.getBlock() instanceof Grating) {
            return FULL;
        }
        if (state.getBlock() instanceof GratingSlab) {
            SlabType type = state.getValue(SlabBlock.TYPE);
            if (type == SlabType.DOUBLE) {
                return FULL;
            }
            boolean bottom = type == SlabType.BOTTOM;
            return switch (dir) {
                case UP -> bottom ? null : FULL;
                case DOWN -> bottom ? FULL : null;
                default -> bottom ? LOWER : UPPER;
            };
        }
        return null;
    }

    /** Whether {@code state}'s face toward {@code dir} is left out: the see-through block there covers it whole. */
    static boolean hidden(BlockState state, BlockState neighbour, Direction dir) {
        float[] mine = coverage(state, dir), theirs = coverage(neighbour, dir.getOpposite());
        return mine != null && theirs != null && theirs[0] <= mine[0] && theirs[1] >= mine[1];
    }

    /** The Lattice Frame: an open braced steel cube. */
    public static class Lattice extends TransparentBlock {
        public Lattice(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean skipRendering(BlockState state, BlockState neighbour, Direction dir) {
            return hidden(state, neighbour, dir) || super.skipRendering(state, neighbour, dir);
        }
    }

    /** The Steel Grating: an open steel grid, waterloggable. */
    public static class Grating extends WaterloggedTransparentBlock {
        public Grating(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean skipRendering(BlockState state, BlockState neighbour, Direction dir) {
            return hidden(state, neighbour, dir) || super.skipRendering(state, neighbour, dir);
        }
    }

    /** The Steel Grating Slab. */
    public static class GratingSlab extends SlabBlock {
        public GratingSlab(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean skipRendering(BlockState state, BlockState neighbour, Direction dir) {
            return hidden(state, neighbour, dir) || super.skipRendering(state, neighbour, dir);
        }
    }
}
