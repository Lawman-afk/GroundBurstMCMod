package dev.groundburst.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * The client's side of {@link BlocksChanged}: the contraption's copy forgets the blocks broken out of it and takes the
 * new ones, and is drawn and collided as it is now.
 */
public final class BlocksChangedClient {
    private BlocksChangedClient() {}

    public static void apply(BlocksChanged changed) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(changed.entity()) instanceof AbstractContraptionEntity entity)) {
            return;
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return;
        }
        for (int i = 0; i < changed.local().size() && i < changed.states().size(); i++) {
            BlockPos pos = changed.local().get(i);
            BlockState state = changed.states().get(i);
            if (state.isAir()) {
                contraption.getBlocks().remove(pos);
                contraption.getActors().removeIf(actor -> actor.getLeft().pos().equals(pos));
                contraption.getInteractors().remove(pos);
            } else {
                StructureBlockInfo old = contraption.getBlocks().get(pos);
                contraption.getBlocks().put(pos, new StructureBlockInfo(pos, state, old == null ? null : old.nbt()));
            }
        }
        contraption.invalidateColliders();
        contraption.resetClientContraption();                    // drawn afresh as it is now
    }
}
