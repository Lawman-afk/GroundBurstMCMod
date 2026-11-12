package dev.groundburst.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.groundburst.client.fx.BlackRainFx;
import dev.groundburst.content.fallout.GBFallout;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Black rain (main's DESIGN.md §21.10): where it falls, the game's rain splashes are sooty ones. 26.1.2 throws them from
 * {@code WeatherEffectRenderer.tickRainParticles} (main's 26.3 from {@code ClientLevel.tickWeatherEffects}).
 */
@Mixin(WeatherEffectRenderer.class)
abstract class ClientLevelRainMixin {
    @WrapOperation(method = "tickRainParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void groundburst$blackSplash(ClientLevel level, ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
            Operation<Void> original) {
        if (options == ParticleTypes.RAIN && BlackRainFx.darkSplash(BlockPos.containing(x, y, z))) {
            options = GBFallout.BLACK_RAIN_SPLASH.get();
        }
        original.call(level, options, x, y, z, dx, dy, dz);
    }
}
