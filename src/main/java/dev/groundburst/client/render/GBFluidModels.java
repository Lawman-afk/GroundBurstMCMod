package dev.groundburst.client.render;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.fluid.ChemFluid;
import dev.groundburst.content.fluid.GBFluids;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** Fluid models: each chemical uses its look's shared animated still/flowing textures, tinted with its colour. */
public final class GBFluidModels {
    private GBFluidModels() {}

    /** Released gas clouds are tinted with their gas's colour. */
    public static void registerTints(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources event) {
        for (ChemFluid f : GBFluids.all()) {
            if (f.cloud() != null) {
                event.register(java.util.List.of(FluidTintSources.constant(f.color())), f.cloud().get());
            }
        }
    }

    public static void register(RegisterFluidModelsEvent event) {
        for (ChemFluid f : GBFluids.all()) {
            String look = f.look().texture();
            event.register(new FluidModel.Unbaked(
                    new Material(GroundBurst.id("block/fluid/" + look + "_still")),
                    new Material(GroundBurst.id("block/fluid/" + look + "_flow")),
                    null,
                    FluidTintSources.constant(f.color())), f.source(), f.flowing());
        }
    }
}
