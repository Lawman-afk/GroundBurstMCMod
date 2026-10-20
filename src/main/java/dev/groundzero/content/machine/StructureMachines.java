package dev.groundzero.content.machine;

import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Shared upkeep for multiblock controllers that are machines (forming, facing, covered faces). */
final class StructureMachines {
    private StructureMachines() {}

    static Direction facing(BlockState state) {
        return state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    static boolean isFormed(BlockState state) {
        return state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED);
    }

    /** Checks the pattern and forms or un-forms the structure (parts and controller) when that changed. */
    static void revalidate(MachineBlockEntity machine, MultiblockController controller) {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide() || machine.isRemoved()) {
            return;
        }
        BlockPos pos = machine.getBlockPos();
        boolean complete = controller.definition().check(level, pos, controller.controllerFacing()).isEmpty();
        if (complete != controller.isFormed()) {
            MultiblockController.setPartsFormed(level, controller, complete);
            level.setBlock(pos, machine.getBlockState().setValue(MultiblockPartBlock.FORMED, complete), Block.UPDATE_ALL);
            machine.setChanged();
        }
    }

    /** Breaking a formed controller un-forms its parts. */
    static void unformOnRemove(MachineBlockEntity machine, MultiblockController controller, BlockState state) {
        Level level = machine.getLevel();
        if (level != null && !level.isClientSide() && isFormed(state)) {
            MultiblockController.setPartsFormed(level, controller, false);
        }
    }
}
