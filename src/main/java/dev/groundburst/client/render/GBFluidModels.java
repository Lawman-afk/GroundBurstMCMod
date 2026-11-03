package dev.groundburst.client.render;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.fluid.ChemFluid;
import dev.groundburst.content.fluid.GBFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Fluid looks: each chemical uses its look's shared animated still/flowing textures, tinted with its colour (1.21.1
 * gives a fluid type its client extensions; 26.x gives its fluids a fluid model).
 */
public final class GBFluidModels {
    private GBFluidModels() {}

    /** Released gas clouds are tinted with their gas's colour. */
    public static void registerTints(RegisterColorHandlersEvent.Block event) {
        for (ChemFluid f : GBFluids.all()) {
            if (f.cloud() != null) {
                int color = f.color();
                event.register((state, level, pos, tintIndex) -> color, f.cloud().get());
            }
        }
    }

    public static void register(RegisterClientExtensionsEvent event) {
        for (ChemFluid f : GBFluids.all()) {
            String look = f.look().texture();
            ResourceLocation still = GroundBurst.id("block/fluid/" + look + "_still");
            ResourceLocation flow = GroundBurst.id("block/fluid/" + look + "_flow");
            int color = f.color();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return still;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return flow;
                }

                @Override
                public int getTintColor() {
                    return color;
                }
            }, f.type().get());
        }
    }
}
