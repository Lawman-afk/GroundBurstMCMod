package dev.groundburst.heal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One of this mod's blocks that keeps track of more than itself (a cable's network, a pipe's run and its pull sides).
 * Healing puts it back exactly as it was and then tells it so, instead of placing it: placing works a cable's or a
 * pipe's sides out again from whatever round it is back yet, and a pull side whose chest is not back would be lost
 * (DESIGN.md §21.9).
 */
public interface Healable {
    /** Put back by healing as it was, its block entity's data with it. */
    void healed(ServerLevel level, BlockPos pos, BlockState state);

    /** A block beside it that has a block entity, or is another mod's, was put back by healing. */
    default void besideHealed(ServerLevel level, BlockPos pos, BlockState state) {
        healed(level, pos, state);
    }
}
