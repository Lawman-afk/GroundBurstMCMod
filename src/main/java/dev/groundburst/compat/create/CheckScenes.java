package dev.groundburst.compat.create;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The parts of the in-game check (dev.groundburst.compat.CreateCheck) that touch Create itself, kept apart so nothing
 * loads Create's classes unless the check runs (with Create installed).
 */
public final class CheckScenes {
    private CheckScenes() {}

    /** Super glue over a box of blocks, as a player spreads it: everything in it moves together. */
    public static void glue(ServerLevel level, BlockPos from, BlockPos to) {
        level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(from, to)));
    }

    /** The contraption carrying the block whose key (the place it was taken from) is {@code key}, near it. */
    public static @Nullable AbstractContraptionEntity carrying(Level level, BlockPos key) {
        for (AbstractContraptionEntity entity : level.getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(key).inflate(64))) {
            Contraption c = entity.getContraption();
            if (c != null && c.anchor != null && c.getBlocks().containsKey(key.subtract(c.anchor))) {
                return entity;
            }
        }
        return null;
    }

    /** A right-click on the carried block with its key at {@code key}, as Create hands one on (either side). */
    public static boolean click(Player player, BlockPos key) {
        AbstractContraptionEntity entity = carrying(player.level(), key);
        return entity != null && entity.handlePlayerInteraction(player, key.subtract(entity.getContraption().anchor), Direction.UP,
                InteractionHand.MAIN_HAND);
    }
}
