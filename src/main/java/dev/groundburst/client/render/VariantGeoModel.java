package dev.groundburst.client.render;

import dev.groundburst.content.machine.AnimatedMachine;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * A block model whose texture its block entity may choose ({@code AnimatedMachine.textureVariant}: the Warning Beacon's
 * colour), its glowmask following the texture; its own texture otherwise (and in its item). The 1.21.1 backport's, for
 * GeckoLib 4: asked of the block entity itself as it is drawn.
 */
public class VariantGeoModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    public VariantGeoModel(ResourceLocation id) {
        super(id);
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        ResourceLocation variant = animatable instanceof AnimatedMachine machine ? machine.textureVariant() : null;
        return variant != null ? variant : super.getTextureResource(animatable);
    }
}
