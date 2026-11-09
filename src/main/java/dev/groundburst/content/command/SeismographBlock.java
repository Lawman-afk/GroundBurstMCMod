package dev.groundburst.content.command;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Seismograph (DESIGN.md §17.17): a recorder on its cabinet. Right-clicking it opens its record (the drum's paper,
 * what it has recorded, its settings); a comparator reads the last arrival's magnitude for a moment.
 */
public class SeismographBlock extends MachineBlock {
    /** The cabinet and, on it, the recorder under its cover (written facing north, the front to the north). */
    public static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 2, 15, 9, 14), Block.box(1.5, 9, 3.5, 14.5, 15, 13));
    /** Client hook: opens its screen (set by the client). */
    public static Consumer<BlockPos> screenHook = p -> {};

    public SeismographBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties, type, true, SHAPE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            screenHook.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof SeismographBlockEntity s ? s.comparator() : 0;
    }
}
