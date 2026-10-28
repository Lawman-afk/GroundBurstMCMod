package dev.groundburst.content.multiblock;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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

    /**
     * What is missing or wrong, for the ghost preview and the controller's status line (either side); empty when the
     * structure is complete. A structure that is not one fixed pattern (the Fission Reactor, any size of box) works out
     * what the player is building.
     */
    default List<MultiblockDefinition.Mismatch> mismatches(Level level) {
        return definition().check(level, controllerPos(), controllerFacing());
    }

    /** The block a ghost shows where a cell is missing. */
    default BlockState ghostState(MultiblockDefinition.Mismatch mismatch) {
        return definition().legend().get(mismatch.key()).preview().get();
    }

    /** How many layers the Holo-Projector steps through (bottom-up, as {@link MultiblockDefinition.Mismatch#layer}). */
    default int previewLayers() {
        return definition().layerCount();
    }

    /** Why the structure will not form beyond missing and wrong blocks (too big, an empty core), or null. */
    default @Nullable Component problem(Level level) {
        return null;
    }

    /**
     * Sets the formed flag (and cut) on every part block that has one, and points ports at the controller. The
     * controller's own cut is set here too (its block entity sets its formed flag after).
     */
    static void setPartsFormed(Level level, MultiblockController controller, boolean formed) {
        MultiblockDefinition def = controller.definition();
        for (MultiblockDefinition.Cell cell : def.cells(controller.controllerPos(), controller.controllerFacing())) {
            if (cell.key() == '.') {
                continue;
            }
            BlockPos pos = cell.pos();
            var state = level.getBlockState(pos);
            if (cell.key() == MultiblockDefinition.CONTROLLER) {
                if (state.hasProperty(MultiblockPartBlock.CUT)) {
                    PartCut cut = formed ? def.cutFor(cell, controller.controllerFacing()) : PartCut.NONE;
                    if (state.getValue(MultiblockPartBlock.CUT) != cut) {
                        level.setBlock(pos, state.setValue(MultiblockPartBlock.CUT, cut), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    }
                }
                continue;
            }
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
