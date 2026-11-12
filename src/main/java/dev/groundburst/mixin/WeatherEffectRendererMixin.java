package dev.groundburst.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.groundburst.client.fx.BlackRainFx;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Black rain (DESIGN.md §21.10): each rain column the game draws takes its colour from {@link BlackRainFx#rainColour}, white
 * as ever where the rain is plain, toward soot where it is black. Snow columns (drawn at a lower alpha) are left alone.
 */
@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectRendererMixin {
    @ModifyExpressionValue(method = "prepareInstances", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
    private int groundburst$blackRain(int white, @Local(argsOnly = true, ordinal = 0) float maxAlpha,
            @Local WeatherEffectRenderer.ColumnInstance column) {
        return maxAlpha >= 1 ? BlackRainFx.rainColour(white, column.x(), column.bottomY(), column.z()) : white;
    }
}
