package dev.groundburst.client.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.base.GeoRenderState;

import dev.groundburst.GroundBurst;
import net.minecraft.resources.Identifier;

/**
 * A block model whose texture its block entity may choose ({@code AnimatedMachine.textureVariant}: the Warning Beacon's
 * colour), its glowmask following the texture, or another model and its texture ({@code AnimatedMachine.modelVariant}:
 * the mirrored Blast Door); its own model and texture otherwise (and in its item).
 */
public class VariantGeoModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
    /** Each other model's geometry and texture, by name. */
    private final Map<String, Identifier[]> others = new ConcurrentHashMap<>();

    public VariantGeoModel(Identifier id) {
        super(id);
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        String model = state.getGeckolibData(MachineGeoRenderer.MODEL);
        return model != null ? other(model)[0] : super.getModelResource(state);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        Identifier variant = state.getGeckolibData(MachineGeoRenderer.TEXTURE);
        if (variant != null) {
            return variant;
        }
        String model = state.getGeckolibData(MachineGeoRenderer.MODEL);
        return model != null ? other(model)[1] : super.getTextureResource(state);
    }

    private Identifier[] other(String name) {
        return this.others.computeIfAbsent(name, n -> {
            Identifier id = GroundBurst.id(n);
            return new Identifier[] {buildFormattedModelPath(id), buildFormattedTexturePath(id)};
        });
    }
}
