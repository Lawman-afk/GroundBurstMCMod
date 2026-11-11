package dev.groundburst.legacy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.groundburst.legacy.client.RenderBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** A block entity is culled by its renderer's box where the renderer gives one (as NeoForge 1.21 culls it). */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getRenderBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB groundburst$box(BlockEntity be) {
        BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
        if (renderer instanceof RenderBox box) {
            return box.getRenderBoundingBox(be);
        }
        return be.getRenderBoundingBox();
    }
}
