package dev.groundburst.content.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dragon's Teeth (DESIGN.md §22.6): a concrete truncated pyramid, an anti-tank obstacle set out in rows; as hard to break
 * as Reinforced Concrete and as hard for a blast. Its shape three steps of its slope.
 */
public class DragonsTeethBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4.5, 15), Block.box(2.4, 4.5, 2.4, 13.6, 9, 13.6),
            Block.box(3.8, 9, 3.8, 12.2, 13, 12.2));

    public DragonsTeethBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isPathfindable(BlockState state, net.minecraft.world.level.BlockGetter level_, BlockPos pos_, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
