package dev.groundzero.content.launch;

import dev.groundzero.content.multiblock.SpreadPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Silo Hatch (DESIGN.md §17.10): one item that places a 3x3 lid (see {@link SpreadPartBlock}), the Missile Silo's
 * top over its shaft. On its own it is a solid steel lid. In a formed silo the silo's model draws its two door leaves,
 * lying on rails over it: shut, each block is as tall as the leaves (1.5 blocks, so you can walk on the doors); open
 * ({@link #OPEN}, set by the silo while its doors are away), there is nothing there, and the shaft is open to the sky.
 */
public class SiloHatchBlock extends SpreadPartBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Formed and shut: up to the top of the door leaves lying on it (the formed model's 24 px). */
    private static final VoxelShape SHUT = Block.box(0, 0, 0, 16, 24, 16);

    public SiloHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(FORMED)) {
            return Shapes.block();
        }
        return state.getValue(OPEN) ? Shapes.empty() : SHUT;
    }

    /** Shut, the doors keep the daylight out of the shaft (a formed part is otherwise see-through); open, it pours in. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getValue(FORMED) && state.getValue(OPEN);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return state.getValue(FORMED) && state.getValue(OPEN) ? 0 : 15;
    }
}
