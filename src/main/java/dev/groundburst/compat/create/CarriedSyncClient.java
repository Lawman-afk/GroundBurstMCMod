package dev.groundburst.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** The client's side of {@link CarriedSync}: the contraption's copy of the launcher, and the one it draws, updated. */
public final class CarriedSyncClient {
    private CarriedSyncClient() {}

    public static void apply(CarriedSync sync) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(sync.entity()) instanceof AbstractContraptionEntity entity)) {
            return;
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return;
        }
        StructureBlockInfo info = contraption.getBlocks().get(sync.local());
        if (info == null) {
            return;
        }
        contraption.getBlocks().put(sync.local(), new StructureBlockInfo(info.pos(), info.state(), sync.tag()));
        BlockEntity shown = contraption.getOrCreateClientContraptionLazy().getBlockEntity(sync.local());
        if (shown != null) {
            shown.handleUpdateTag(sync.tag(), level.registryAccess());
        }
    }
}
