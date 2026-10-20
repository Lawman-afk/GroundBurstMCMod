package dev.groundzero.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** A block entity that owns a multiblock: knows its pattern, whether it is formed, and re-checks itself on demand. */
public interface MultiblockController {
    MultiblockDefinition definition();

    BlockPos controllerPos();

    Direction controllerFacing();

    boolean isFormed();

    /** Re-checks the structure and forms or un-forms it. Server side only. */
    void revalidate();

    /** Whether {@code pos} is one of this structure's cells (formed or not). */
    default boolean covers(BlockPos pos) {
        return definition().contains(controllerPos(), controllerFacing(), pos);
    }

    /** Sets the formed flag (and corner cut) on every part block that has one, and points ports at the controller. */
    static void setPartsFormed(Level level, MultiblockController controller, boolean formed) {
        MultiblockDefinition def = controller.definition();
        for (MultiblockDefinition.Cell cell : def.cells(controller.controllerPos(), controller.controllerFacing())) {
            if (cell.key() == MultiblockDefinition.CONTROLLER || cell.key() == '.') {
                continue;
            }
            BlockPos pos = cell.pos();
            var state = level.getBlockState(pos);
            if (state.hasProperty(MultiblockPartBlock.FORMED)) {
                PartCut cut = formed ? def.cutFor(cell, controller.controllerFacing()) : PartCut.NONE;
                var target = state.setValue(MultiblockPartBlock.FORMED, formed).setValue(MultiblockPartBlock.CUT, cut);
                if (target != state) {
                    level.setBlock(pos, target, net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            }
            if (level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                port.setController(formed ? controller.controllerPos() : null);
            }
        }
    }
}
