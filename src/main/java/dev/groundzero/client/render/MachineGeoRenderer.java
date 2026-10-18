package dev.groundzero.client.render;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.GeoBlockRenderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

/** GeckoLib machine renderer whose culling box covers the whole model, including the upper half of tall machines. */
public class MachineGeoRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T, BlockEntityRenderState> {
    private final int height;

    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height) {
        super(context, type);
        this.height = height;
    }

    @Override
    public AABB getRenderBoundingBox(T blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.25, 0, 0.25).expandTowards(0, this.height - 1, 0);
    }
}
