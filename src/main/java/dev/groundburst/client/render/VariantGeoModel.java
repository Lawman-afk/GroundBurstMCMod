package dev.groundburst.client.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.AnimatedMachine;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * A block model whose texture its block entity may choose ({@code AnimatedMachine.textureVariant}: the Warning Beacon's
 * colour), its glowmask following the texture, or another model and its texture ({@code AnimatedMachine.modelVariant}:
 * the mirrored Blast Door); its own model and texture otherwise (and in its item). The 1.21.1 backport's, for GeckoLib 4:
 * asked of the block entity itself as it is drawn.
 */
public class VariantGeoModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    /** Each other model's geometry and texture, by name. */
    private final Map<String, ResourceLocation[]> others = new ConcurrentHashMap<>();

    public VariantGeoModel(ResourceLocation id) {
        super(id);
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        String model = animatable instanceof AnimatedMachine machine ? machine.modelVariant() : null;
        return model != null ? other(model)[0] : super.getModelResource(animatable);
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        ResourceLocation variant = animatable instanceof AnimatedMachine machine ? machine.textureVariant() : null;
        if (variant != null) {
            return variant;
        }
        String model = animatable instanceof AnimatedMachine machine ? machine.modelVariant() : null;
        return model != null ? other(model)[1] : super.getTextureResource(animatable);
    }

    private ResourceLocation[] other(String name) {
        return this.others.computeIfAbsent(name, n -> {
            ResourceLocation id = GroundBurst.id(n);
            return new ResourceLocation[] {buildFormattedModelPath(id), buildFormattedTexturePath(id)};
        });
    }
}
