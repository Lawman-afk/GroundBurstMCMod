package dev.groundburst.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import dev.groundburst.content.machine.Carried;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The client's side of carried machines (main's DESIGN.md §17.16): a menu or screen opened for a machine a contraption
 * carries names the place it was taken from; this finds the copy the contraption near the player draws for that place.
 */
public final class CarriedClient {
    /** How far round the player to look for the contraption, blocks. */
    private static final double NEAR = 96;

    private CarriedClient() {}

    public static void register() {
        Carried.addClientFinder((level, key) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level != level) {
                return null;
            }
            for (AbstractContraptionEntity entity : level.getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(mc.player.blockPosition()).inflate(NEAR))) {
                Contraption contraption = entity.getContraption();
                if (contraption == null || contraption.anchor == null) {
                    continue;
                }
                BlockPos local = key.subtract(contraption.anchor);
                if (contraption.getBlocks().containsKey(local)) {
                    BlockEntity shown = contraption.getOrCreateClientContraptionLazy().getBlockEntity(local);
                    if (shown != null) {
                        return shown;
                    }
                }
            }
            return null;
        });
    }
}
