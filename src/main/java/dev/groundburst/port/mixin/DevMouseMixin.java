package dev.groundburst.port.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.groundburst.port.DevRuns;
import net.minecraft.client.MouseHandler;

/** The dev test runs never grab the mouse ({@link DevRuns}): a click on their window leaves the cursor free. */
@Mixin(MouseHandler.class)
abstract class DevMouseMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void groundburst$keepMouse(CallbackInfo ci) {
        if (DevRuns.KEEP_MOUSE) {
            ci.cancel();
        }
    }
}
