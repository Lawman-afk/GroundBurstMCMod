package dev.groundburst.client.render;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import net.minecraft.resources.ResourceLocation;

/**
 * A block model whose texture its block entity may choose ({@code AnimatedMachine.textureVariant}: the Warning Beacon's
 * colour), its glowmask following the texture; its own texture otherwise (and in its item).
 */
public class VariantGeoModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    public VariantGeoModel(ResourceLocation id) {
        super(id);
    }

    @Override
    public ResourceLocation getTextureResource(GeoRenderState state) {
        ResourceLocation variant = state.getGeckolibData(MachineGeoRenderer.TEXTURE);
        return variant != null ? variant : super.getTextureResource(state);
    }
}
