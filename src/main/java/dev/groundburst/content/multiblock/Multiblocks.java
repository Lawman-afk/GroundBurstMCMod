package dev.groundburst.content.multiblock;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Finds the controllers a block change may affect. Part blocks carry no block entity, so a change near a structure
 * asks every controller within one chunk whether the position is one of its cells. Structures reach at most 16 blocks
 * from their controller (the Strategic Assembly Hall 12), so one chunk of margin always reaches it.
 */
public final class Multiblocks {
    private Multiblocks() {}

    public static List<MultiblockController> controllersCovering(Level level, BlockPos pos) {
        List<MultiblockController> out = new ArrayList<>();
        ChunkPos center = ChunkPos.containing(pos);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                if (!level.hasChunk(center.x() + cx, center.z() + cz)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(center.x() + cx, center.z() + cz).getBlockEntities().values()) {
                    if (be instanceof MultiblockController controller && !be.isRemoved() && controller.covers(pos)) {
                        out.add(controller);
                    }
                }
            }
        }
        return out;
    }

    /** A block in or near a structure changed: let every covering controller re-check itself. */
    public static void notifyChanged(Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            for (MultiblockController controller : controllersCovering(level, pos)) {
                controller.revalidate();
            }
        }
    }

    /** The formed controller a part belongs to, if any. */
    public static @Nullable MultiblockController formedControllerOf(Level level, BlockPos part) {
        for (MultiblockController controller : controllersCovering(level, part)) {
            if (controller.isFormed()) {
                return controller;
            }
        }
        return null;
    }
}
