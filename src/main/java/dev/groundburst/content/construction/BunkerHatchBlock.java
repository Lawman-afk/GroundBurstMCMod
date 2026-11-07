package dev.groundburst.content.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
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
 * if that place is free and taken away as it shuts or goes. A ladder is built in down its front, opposite its hinges,
 * under the coaming (above it in a ceiling hatch): no ladder can be hung in the hatch's own block, so one hung on the
 * wall under it runs on up this one, and the hatch climbs as a ladder does, open or shut. Its models:
 * tools/assets/bunker_hatch.py.
 */
public class BunkerHatchBlock extends TrapDoorBlock {
    /**
     * Shapes by facing (2D data value) and half (0 bottom, 1 top), with the ladder's: open, the coaming's ring (the lid's
     * block has the lid); shut, the trapdoor's slab.
     */
    private static final VoxelShape[][] OPEN_SHAPES = new VoxelShape[4][2];
    private static final VoxelShape[][] SHUT_SHAPES = new VoxelShape[4][2];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int top = 0; top < 2; top++) {
                VoxelShape ladder = ladderShape(facing, top == 1);
                VoxelShape slab = top == 1 ? Block.box(0, 13, 0, 16, 16, 16) : Block.box(0, 0, 0, 16, 3, 16);
                OPEN_SHAPES[facing.get2DDataValue()][top] = Shapes.or(openShape(facing, top == 1), ladder);
                SHUT_SHAPES[facing.get2DDataValue()][top] = Shapes.or(slab, ladder);
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

    /**
     * The ladder down its front wall (facing north, its hinges at the south: the north wall), from the block's foot to the
     * coaming's underside in a floor hatch, from the coaming's top up in a ceiling hatch: two pixels deep, as the coaming's
     * ring is wide, so one climbing it passes up through the ring.
     */
    private static VoxelShape ladderShape(Direction facing, boolean top) {
        return BlastDoorBlock.box(facing, false, 2, top ? 0 : 2.5, 0, 14, top ? 13.5 : 16, 2);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape[][] shapes = state.getValue(OPEN) ? OPEN_SHAPES : SHUT_SHAPES;
        return shapes[state.getValue(FACING).get2DDataValue()][state.getValue(HALF) == Half.TOP ? 1 : 0];
    }

    /**
     * A ladder, as its built-in one is, open or shut (a trapdoor climbs only open, over a ladder of its own facing: one on
     * the hinges' side, under the lid standing open).
     */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
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
