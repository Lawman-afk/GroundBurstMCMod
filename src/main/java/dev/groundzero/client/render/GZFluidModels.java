package dev.groundzero.client.render;

import dev.groundzero.GroundZero;
import dev.groundzero.content.fluid.ChemFluid;
import dev.groundzero.content.fluid.GZFluids;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** Fluid models: each chemical uses its look's shared animated still/flowing textures, tinted with its colour. */
public final class GZFluidModels {
    private GZFluidModels() {}

    /** Released gas clouds are tinted with their gas's colour. */
    public static void registerTints(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources event) {
        for (ChemFluid f : GZFluids.all()) {
            if (f.cloud() != null) {
                event.register(java.util.List.of(FluidTintSources.constant(f.color())), f.cloud().get());
            }
        }
    }

    public static void register(RegisterFluidModelsEvent event) {
        for (ChemFluid f : GZFluids.all()) {
            String look = f.look().texture();
            event.register(new FluidModel.Unbaked(
                    new Material(GroundZero.id("block/fluid/" + look + "_still")),
                    new Material(GroundZero.id("block/fluid/" + look + "_flow")),
                    null,
                    FluidTintSources.constant(f.color())), f.source(), f.flowing());
        }
    }
}
