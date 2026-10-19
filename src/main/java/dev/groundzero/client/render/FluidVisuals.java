package dev.groundzero.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * How a fluid looks outside the world: its still texture and tint, taken from its fluid model, so water, our
 * chemicals and other mods' fluids all show the same way in GUI gauges, tank windows and pipes.
 */
public final class FluidVisuals {
    private FluidVisuals() {}

    public static FluidModel model(Fluid fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
    }

    public static TextureAtlasSprite still(Fluid fluid) {
        return model(fluid).stillMaterial().sprite();
    }

    /** ARGB tint for GUIs (water's default blue rather than a biome's). */
    public static int tint(Fluid fluid) {
        FluidModel m = model(fluid);
        return m.fluidTintSource() == null ? 0xFFFFFFFF : 0xFF000000 | m.fluidTintSource().color(fluid.defaultFluidState());
    }

    /** ARGB tint at a position in the world (water takes the biome's colour). */
    public static int tintInWorld(Fluid fluid, Level level, BlockPos pos) {
        FluidModel m = model(fluid);
        if (m.fluidTintSource() == null) {
            return 0xFFFFFFFF;
        }
        if (level instanceof net.minecraft.client.renderer.block.BlockAndTintGetter tintGetter) {
            return 0xFF000000 | m.fluidTintSource().colorInWorld(fluid.defaultFluidState(), fluid.defaultFluidState().createLegacyBlock(), tintGetter, pos);
        }
        return tint(fluid);
    }
}
