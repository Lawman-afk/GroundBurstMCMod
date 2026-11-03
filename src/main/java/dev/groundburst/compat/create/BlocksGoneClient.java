package dev.groundburst.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** The client's side of {@link BlocksGone}: the contraption's copy forgets the blocks, and is drawn and collided without them. */
public final class BlocksGoneClient {
    private BlocksGoneClient() {}

    public static void apply(BlocksGone gone) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(gone.entity()) instanceof AbstractContraptionEntity entity)) {
            return;
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return;
        }
        for (BlockPos pos : gone.local()) {
            contraption.getBlocks().remove(pos);
            contraption.getActors().removeIf(actor -> actor.getLeft().pos().equals(pos));
            contraption.getInteractors().remove(pos);
        }
        contraption.invalidateColliders();
        contraption.resetClientContraption();                    // drawn afresh from what is left
    }
}
