package dev.groundburst.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.groundburst.client.fx.BlackRainFx;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Black rain (main's DESIGN.md §21.10), this version's way: 1.21.1 draws its rain in {@code LevelRenderer.renderSnowAndRain},
 * each rain column's four corners white at its alpha; here each takes {@link BlackRainFx#rainColour}, toward soot where the
 * rain is black. Snow is left alone. (Main's mixin of this name wraps 26.x's weather renderer.)
 */
@Mixin(LevelRenderer.class)
abstract class WeatherEffectRendererMixin {
    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer groundburst$blackRain(VertexConsumer consumer, float r, float g, float b, float a, Operation<VertexConsumer> original,
            @Local Biome.Precipitation precipitation, @Local BlockPos.MutableBlockPos column) {
        if (precipitation == Biome.Precipitation.RAIN) {
            int c = BlackRainFx.rainColour(0xFFFFFFFF, column.getX(), column.getY(), column.getZ());
            if (c != 0xFFFFFFFF) {
                return original.call(consumer, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a);
            }
        }
        return original.call(consumer, r, g, b, a);
    }
}
