package dev.groundburst.content.construction;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ammo Crates (DESIGN.md §22.6): eight olive ammo boxes on a wooden pallet, holding 27 stacks as the Filing Cabinet does;
 * while anyone has them open the top front box's lid stands up, its cartridges showing.
 */
public class AmmoCratesBlock extends FilingCabinetBlock {
    private static final VoxelShape[] SHAPES = Fittings.facing(new double[] {0, 0, 0, 16, 2.3, 16}, new double[] {1, 2.3, 1, 15, 9.1, 15},
            new double[] {1.5, 9.1, 3.4, 14.5, 12.5, 12.6});

    public AmmoCratesBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        super(properties, type);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}
