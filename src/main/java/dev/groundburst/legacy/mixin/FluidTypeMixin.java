package dev.groundburst.legacy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.groundburst.legacy.client.RegisterClientExtensionsEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/** A fluid type's client extensions (its textures, tint and fog) as registered through NeoForge's event, in place of its own. */
@Mixin(value = FluidType.class, remap = false)
public abstract class FluidTypeMixin {
    @Inject(method = "getRenderPropertiesInternal", at = @At("HEAD"), cancellable = true)
    private void groundburst$extensions(CallbackInfoReturnable<Object> cir) {
        IClientFluidTypeExtensions ext = RegisterClientExtensionsEvent.fluidType((FluidType) (Object) this);
        if (ext != null) {
            cir.setReturnValue(ext);
        }
    }
}
