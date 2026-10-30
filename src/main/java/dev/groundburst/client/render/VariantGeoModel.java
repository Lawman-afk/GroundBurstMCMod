package dev.groundburst.client.render;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.base.GeoRenderState;

import net.minecraft.resources.Identifier;

/**
 * A block model whose texture its block entity may choose ({@code AnimatedMachine.textureVariant}: the Warning Beacon's
 * colour), its glowmask following the texture; its own texture otherwise (and in its item).
 */
public class VariantGeoModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    public VariantGeoModel(Identifier id) {
        super(id);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        Identifier variant = state.getGeckolibData(MachineGeoRenderer.TEXTURE);
        return variant != null ? variant : super.getTextureResource(state);
    }
}
