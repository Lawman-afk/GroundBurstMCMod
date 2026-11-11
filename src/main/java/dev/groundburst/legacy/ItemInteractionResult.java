package dev.groundburst.legacy;

import net.minecraft.world.InteractionResult;

/**
 * Minecraft 1.21's answer to using an item on a block (1.20.1 has one use method, answering with an InteractionResult):
 * the mod's blocks keep 1.21's two methods, and the port gives each block a 1.20.1 use that asks them in turn
 * ({@link UseOnBlock}).
 */
public enum ItemInteractionResult {
    SUCCESS,
    CONSUME,
    CONSUME_PARTIAL,
    PASS_TO_DEFAULT_BLOCK_INTERACTION,
    SKIP_DEFAULT_BLOCK_INTERACTION,
    FAIL;

    public boolean consumesAction() {
        return this.result().consumesAction();
    }

    public static ItemInteractionResult sidedSuccess(boolean clientSide) {
        return clientSide ? SUCCESS : CONSUME;
    }

    public InteractionResult result() {
        return switch (this) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case CONSUME -> InteractionResult.CONSUME;
            case CONSUME_PARTIAL -> InteractionResult.CONSUME_PARTIAL;
            case PASS_TO_DEFAULT_BLOCK_INTERACTION, SKIP_DEFAULT_BLOCK_INTERACTION -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
        };
    }
}
