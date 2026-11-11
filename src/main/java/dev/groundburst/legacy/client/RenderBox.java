package dev.groundburst.legacy.client;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * A block entity renderer that says how far its drawing reaches (NeoForge 1.21 asks the renderer; Forge 1.20.1 asks the
 * block entity: dev.groundburst.legacy.mixin.LevelRendererMixin asks a renderer of this kind instead).
 */
public interface RenderBox<T extends BlockEntity> {
    AABB getRenderBoundingBox(T blockEntity);
}
