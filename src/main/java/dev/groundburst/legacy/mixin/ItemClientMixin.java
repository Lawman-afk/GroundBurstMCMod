package dev.groundburst.legacy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.groundburst.legacy.client.RegisterClientExtensionsEvent;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** An item's client extensions (its own renderer) as registered through NeoForge's event, in place of its own. */
@Mixin(value = Item.class, remap = false)
public abstract class ItemClientMixin {
    @Inject(method = "getRenderPropertiesInternal", at = @At("HEAD"), cancellable = true)
    private void groundburst$extensions(CallbackInfoReturnable<Object> cir) {
        IClientItemExtensions ext = RegisterClientExtensionsEvent.item((Item) (Object) this);
        if (ext != null) {
            cir.setReturnValue(ext);
        }
    }
}
