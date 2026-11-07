package dev.groundburst.content.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Bunker Hatch (DESIGN.md §22.3): a round steel lid in a square coaming, opened and shut as a trapdoor is (by hand,
 * by redstone; its sounds the bunker set's). Shut, it is a trapdoor's slab. Open, its coaming stays round the hole (a
 * thin ring, so the way is clear) and its lid stands where its hinges swing it: up out of the floor, at a floor hatch's
 * hinge side, into the block over it; down into the room under a ceiling hatch. There the lid has a block of its own
 * while it stands open ({@link BunkerHatchLidBlock}: solid, and a click on it shuts the hatch), laid as the hatch opens
 * if that place is free and taken away as it shuts or goes. Its models: tools/assets/bunker_hatch.py.
 */
public class BunkerHatchBlock extends TrapDoorBlock {
    /** Open shapes by facing (2D data value) and half (0 bottom, 1 top): the coaming's ring alone (the lid's block has the lid). */
    private static final VoxelShape[][] OPEN_SHAPES = new VoxelShape[4][2];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int top = 0; top < 2; top++) {
                OPEN_SHAPES[facing.get2DDataValue()][top] = openShape(facing, top == 1);
            }
        }
    }

    public BunkerHatchBlock(Properties properties) {
        super(BunkerSet.get(), properties);
    }

    /**
     * Worked out facing north (its hinges at the south edge), then turned: the coaming two pixels wide round the hole, at
     * the top of the block for a floor hatch, the bottom for a ceiling hatch.
     */
    private static VoxelShape openShape(Direction facing, boolean top) {
        double y0 = top ? 13.5 : 0.5, y1 = top ? 15.5 : 2.5;
        return Shapes.or(BlastDoorBlock.box(facing, false, 0, y0, 0, 16, y1, 2), BlastDoorBlock.box(facing, false, 0, y0, 14, 16, y1, 16),
                BlastDoorBlock.box(facing, false, 0, y0, 2, 2, y1, 14), BlastDoorBlock.box(facing, false, 14, y0, 2, 16, y1, 14));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(OPEN)) {
            return super.getShape(state, level, pos, context);
        }
        return OPEN_SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(HALF) == Half.TOP ? 1 : 0];
    }

    /** Opened or shut (by hand, by redstone, by anything), its lid's block follows it. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BunkerHatchLidBlock.follow(level, pos, state);
    }

    /** Gone, its lid's block goes with it. */
    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level_l, BlockPos pos, BlockState newState_, boolean movedByPiston) { super.onRemove(state, level_l, pos, newState_, movedByPiston); if (!(level_l instanceof net.minecraft.server.level.ServerLevel level) || state.is(newState_.getBlock())) return;
        
        BunkerHatchLidBlock.clear(level, pos, state);
    }
}
