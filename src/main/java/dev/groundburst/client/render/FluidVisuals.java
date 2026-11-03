package dev.groundburst.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * How a fluid looks outside the world: its still texture and tint, taken from its fluid type's client extensions, so
 * water, our chemicals and other mods' fluids all show the same way in GUI gauges, tank windows and pipes.
 */
public final class FluidVisuals {
    private FluidVisuals() {}

    public static TextureAtlasSprite still(Fluid fluid) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(IClientFluidTypeExtensions.of(fluid).getStillTexture());
    }

    /** ARGB tint for GUIs (water's default blue rather than a biome's). */
    public static int tint(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    /** ARGB tint at a position in the world (water takes the biome's colour). */
    public static int tintInWorld(Fluid fluid, Level level, BlockPos pos) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor(fluid.defaultFluidState(), level, pos);
    }
}
